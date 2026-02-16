package com.example.helios_location_finder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocationWorker"
        private const val NTFY_BASE_URL = "https://ntfy.sh/"
        private const val LOCATION_TIMEOUT_MS = 30_000L
    }

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        Log.d(TAG, "LocationWorker started")

        val replyTopic = Prefs.getReplyTopic(applicationContext)
        if (replyTopic.isBlank()) {
            Log.e(TAG, "No reply topic configured")
            return Result.failure()
        }
        val replyUrl = NTFY_BASE_URL + replyTopic

        if (!hasLocationPermission()) {
            Log.e(TAG, "No location permission")
            sendMessage(replyUrl, "Error: Location permission not granted")
            return Result.failure()
        }

        val locationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        try {
            // Strategy 1: getCurrentLocation
            val cancellationSource = CancellationTokenSource()
            var location: Location? = try {
                withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    locationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cancellationSource.token
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "getCurrentLocation failed: ${e.message}")
                null
            } finally {
                cancellationSource.cancel()
            }

            // Strategy 2: getLastLocation as fallback
            if (location == null) {
                Log.d(TAG, "getCurrentLocation returned null, trying getLastLocation")
                location = try {
                    locationClient.lastLocation.await()
                } catch (e: Exception) {
                    Log.w(TAG, "getLastLocation failed: ${e.message}")
                    null
                }
            }

            // Strategy 3: requestLocationUpdates as last resort
            if (location == null) {
                Log.d(TAG, "getLastLocation returned null, trying requestLocationUpdates")
                location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    requestSingleUpdate(locationClient)
                }
            }

            if (location == null) {
                Log.w(TAG, "All location strategies failed")
                sendMessage(replyUrl, "Error: Could not determine location")
                return Result.retry()
            }

            val battery = getBatteryLevel()
            val payload = "Lat: ${location.latitude}, Lon: ${location.longitude}, Battery: $battery%"

            Log.d(TAG, "Sending location: $payload")
            sendMessage(replyUrl, payload)

            return Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
            sendMessage(replyUrl, "Error: SecurityException - ${e.message}")
            return Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            sendMessage(replyUrl, "Error: ${e.message}")
            return Result.retry()
        }
    }

    private suspend fun requestSingleUpdate(
        locationClient: com.google.android.gms.location.FusedLocationProviderClient
    ): Location = suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L
        ).setMaxUpdates(1).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                locationClient.removeLocationUpdates(this)
                val loc = result.lastLocation
                if (loc != null && cont.isActive) {
                    cont.resume(loc)
                }
            }
        }

        locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        cont.invokeOnCancellation {
            locationClient.removeLocationUpdates(callback)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getBatteryLevel(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = applicationContext.registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun sendMessage(url: String, text: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .post(text.toRequestBody("text/plain".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "POST response: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
        }
    }
}
