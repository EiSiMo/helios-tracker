package com.example.helios_location_finder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val Orange = Color(0xFFFF6D00)
private val OrangeLight = Color(0xFFFFAB40)
private val DarkSurface = Color(0xFF1A1A1A)

private val HeliosDarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    secondary = OrangeLight,
    onSecondary = Color.Black,
    background = Color.Black,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFCF6679),
)

class MainActivity : ComponentActivity() {

    private val foregroundGranted = mutableStateOf(false)
    private val backgroundGranted = mutableStateOf(false)

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        foregroundGranted.value = permissions.values.any { it }
        checkPermissions()
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundGranted.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        val listenTopic = mutableStateOf(Prefs.getListenTopic(this))
        val replyTopic = mutableStateOf(Prefs.getReplyTopic(this))

        setContent {
            MaterialTheme(colorScheme = HeliosDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatusScreen(
                        foregroundGranted = foregroundGranted.value,
                        backgroundGranted = backgroundGranted.value,
                        listenTopic = listenTopic.value,
                        replyTopic = replyTopic.value,
                        onListenTopicChange = { value ->
                            listenTopic.value = value
                            Prefs.setListenTopic(this, value)
                        },
                        onReplyTopicChange = { value ->
                            replyTopic.value = value
                            Prefs.setReplyTopic(this, value)
                        },
                        onRequestForegroundPermission = { requestForegroundPermissions() },
                        onRequestBackgroundPermission = { requestBackgroundPermission() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        foregroundGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        backgroundGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestForegroundPermissions() {
        foregroundPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}

@Composable
fun StatusScreen(
    foregroundGranted: Boolean,
    backgroundGranted: Boolean,
    listenTopic: String,
    replyTopic: String,
    onListenTopicChange: (String) -> Unit,
    onReplyTopicChange: (String) -> Unit,
    onRequestForegroundPermission: () -> Unit,
    onRequestBackgroundPermission: () -> Unit
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Orange,
        unfocusedBorderColor = OrangeLight.copy(alpha = 0.5f),
        focusedLabelColor = Orange,
        cursorColor = Orange,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Helios Tracker",
            style = MaterialTheme.typography.headlineMedium,
            color = Orange
        )

        Spacer(modifier = Modifier.height(32.dp))

        val statusText = when {
            !foregroundGranted -> "Standort-Berechtigung fehlt"
            !backgroundGranted -> "Hintergrund-Standort fehlt"
            else -> "Bereit"
        }
        val isReady = foregroundGranted && backgroundGranted

        Text(
            text = "Status: $statusText",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isReady) Orange else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = listenTopic,
            onValueChange = onListenTopicChange,
            label = { Text("Empfangs-Topic (lauschen)") },
            placeholder = { Text("z.B. mein_geraet_locate") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = replyTopic,
            onValueChange = onReplyTopicChange,
            label = { Text("Antwort-Topic (senden)") },
            placeholder = { Text("z.B. mein_geraet_reply") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Die App lauscht auf ntfy-Nachrichten mit dem Inhalt \"LOCATE\" " +
                   "auf dem Empfangs-Topic und antwortet mit dem Standort auf dem Antwort-Topic.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )

        if (!foregroundGranted) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestForegroundPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("Standort-Berechtigung erteilen", color = Color.Black)
            }
        } else if (!backgroundGranted) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestBackgroundPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("Hintergrund-Standort erlauben", color = Color.Black)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Damit die App auf LOCATE-Anfragen reagieren kann, " +
                       "muss \"Immer erlauben\" gewaehlt werden.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
