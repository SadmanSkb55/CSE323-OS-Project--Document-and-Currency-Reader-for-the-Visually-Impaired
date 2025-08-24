package com.example.takaguni

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.*
import java.util.*

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var vibrator: Vibrator
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    companion object {
        var language by mutableStateOf("en") // Shared language state
        var ocrDelay by mutableStateOf(5) // Shared OCR delay (seconds)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setContent {
            CurrencyReaderApp(tts, vibrator)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            updateTtsLanguage()
            tts.speak(if (language == "bn") "অ্যাপ শুরু হয়েছে" else "App started", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            tts.speak(if (language == "bn") "টেক্সট টু স্পিচ শুরু করতে ব্যর্থ" else "Failed to initialize text-to-speech", TextToSpeech.QUEUE_FLUSH, null, null)
            vibrator.vibrate(longArrayOf(0, 500), -1)
        }
    }

    private fun updateTtsLanguage() {
        val locale = if (language == "bn") Locale("bn", "BD") else Locale.US
        tts.language = locale
    }

    private fun checkPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.VIBRATE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.VIBRATE
            )
        }
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                tts.speak(if (language == "bn") "মোবাইল ক্যামেরা শুরু হয়েছে" else "Mobile camera started", TextToSpeech.QUEUE_FLUSH, null, null)
                vibrator.vibrate(longArrayOf(0, 200), -1)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                tts.speak(if (language == "bn") "ব্লুটুথ স্ক্রিন শুরু হয়েছে" else "Bluetooth screen started", TextToSpeech.QUEUE_FLUSH, null, null)
                vibrator.vibrate(longArrayOf(0, 200), -1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        scope.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CurrencyReaderApp(tts: TextToSpeech, vibrator: Vibrator) {
        val navController = rememberNavController()
        NavHost(navController, startDestination = "main") {
            composable("main") { MainScreen(navController, tts, vibrator) }
            composable("settings") { SettingsScreen(navController, tts, vibrator) }
            composable("camera") { CameraScreen(navController, tts, vibrator) }
            composable("ble") { BLEScreen(navController, tts, vibrator) }
        }
    }

    @Composable
    fun MainScreen(navController: NavController, tts: TextToSpeech, vibrator: Vibrator) {
        val context = LocalContext.current
        var hasPermissions by remember { mutableStateOf(checkPermissions()) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            hasPermissions = permissions.all { it.value }
            if (hasPermissions) {
                tts.speak(
                    if (language == "bn") "সমস্ত অনুমতি প্রদান করা হয়েছে" else "All permissions granted",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
                vibrator.vibrate(longArrayOf(0, 200), -1)
            } else {
                tts.speak(
                    if (language == "bn") "অনুমতি প্রয়োজন" else "Permissions required",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
                vibrator.vibrate(longArrayOf(0, 500), -1)
            }
        }

        LaunchedEffect(Unit) {
            if (!hasPermissions) {
                permissionLauncher.launch(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.VIBRATE,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.VIBRATE
                        )
                    }
                )
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        tts.speak(if (language == "bn") "ব্লুটুথ স্ক্রিন শুরু হয়েছে" else "Bluetooth screen started", TextToSpeech.QUEUE_FLUSH, null, null)
                        vibrator.vibrate(longArrayOf(0, 200), -1)
                        navController.navigate("ble")
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (language == "bn") "ব্লুটুথ থেকে পড়ুন" else "Read from Bluetooth")
                }
                Button(
                    onClick = {
                        tts.speak(if (language == "bn") "মোবাইল ক্যামেরা শুরু হয়েছে" else "Mobile camera started", TextToSpeech.QUEUE_FLUSH, null, null)
                        vibrator.vibrate(longArrayOf(0, 200), -1)
                        navController.navigate("camera")
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (language == "bn") "মোবাইল ক্যামেরা থেকে পড়ুন" else "Read from Mobile Camera")
                }
                Button(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (language == "bn") "সেটিংস" else "Settings")
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(navController: NavController, tts: TextToSpeech, vibrator: Vibrator) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (language == "bn") "ভাষা নির্বাচন করুন" else "Select Language",
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = {
                        language = "en"
                        updateTtsLanguage()
                        tts.speak("English selected", TextToSpeech.QUEUE_FLUSH, null, null)
                        vibrator.vibrate(longArrayOf(0, 200), -1)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("English")
                }
                Button(
                    onClick = {
                        language = "bn"
                        updateTtsLanguage()
                        tts.speak("Bangla Nirbachito hoyeche", TextToSpeech.QUEUE_FLUSH, null, null)
                        vibrator.vibrate(longArrayOf(0, 200), -1)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("বাংলা")
                }
                Text(
                    text = if (language == "bn") "ওসিআর বিলম্ব নির্বাচন করুন" else "Select OCR Delay",
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = {
                        ocrDelay = 5
                        tts.speak(if (language == "bn") "৫ সেকেন্ড বিলম্ব নির্বাচিত" else "5 seconds delay selected", TextToSpeech.QUEUE_FLUSH, null, null)
                        vibrator.vibrate(longArrayOf(0, 200), -1)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("5 Seconds")
                }
                Button(
                    onClick = {
                        ocrDelay = 10
                        tts.speak(if (language == "bn") "১০ সেকেন্ড বিলম্ব নির্বাচিত" else "10 seconds delay selected", TextToSpeech.QUEUE_FLUSH, null, null)
                        vibrator.vibrate(longArrayOf(0, 200), -1)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("10 Seconds")
                }
                Button(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (language == "bn") "ফিরে যান" else "Back")
                }
            }
        }
    }
}

/*-----------Experimental----------------------------*/
/*
data class CurrencyFeedback(
    val vibrations: List<Long>,
    val beeps: List<Long>
)

private fun getCurrencyFeedback(currencyValue: Int, tts: TextToSpeech): CurrencyFeedback? {
    return when (currencyValue) {
        5 -> CurrencyFeedback(
            vibrations = listOf(100),
            beeps = listOf(100, 100, 100, 100, 100)
        )
        10 -> CurrencyFeedback(
            vibrations = listOf(100, 150, 100),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        20 -> CurrencyFeedback(
            vibrations = listOf(100, 150, 100, 150, 100),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        50 -> CurrencyFeedback(
            vibrations = listOf(100, 150, 100, 150, 100, 150, 100),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        100 -> CurrencyFeedback(
            vibrations = listOf(300),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        200 -> CurrencyFeedback(
            vibrations = listOf(300, 150, 300),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        500 -> CurrencyFeedback(
            vibrations = listOf(300, 150, 300, 150, 300),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        1000 -> CurrencyFeedback(
            vibrations = listOf(300, 150, 300, 150, 300, 150, 300),
            beeps = listOf(100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100, 300, 100, 100, 100, 100, 100)
        )
        else -> null
    }
}

private suspend fun triggerHapticFeedback(vibrations: List<Long>, vibrator: Vibrator) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val timings = vibrations.toLongArray()
        val amplitudes = IntArray(vibrations.size) { VibrationEffect.DEFAULT_AMPLITUDE }
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    } else {
        vibrator.vibrate(vibrations.toLongArray(), -1)
    }
    delay(vibrations.sum() + (vibrations.size - 1) * 150L)
}

private suspend fun triggerSonificationFeedback(beeps: List<Long>, tts: TextToSpeech) {
    for (beep in beeps) {
        tts.playSilentUtterance(beep, TextToSpeech.QUEUE_ADD, null)
        delay(beep + 100L)
    }
}
 */