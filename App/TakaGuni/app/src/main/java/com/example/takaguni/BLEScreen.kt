
package com.example.takaguni

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEScreen(navController: NavController, tts: TextToSpeech, vibrator: Vibrator) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(checkPermissions(context)) }
    var receivedImage by remember { mutableStateOf<Bitmap?>(null) }
    var detectedText by remember { mutableStateOf("No text detected yet...") }
    val scope = rememberCoroutineScope()
    var bluetoothSocket by remember { mutableStateOf<BluetoothSocket?>(null) }
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    var connectedThread by remember { mutableStateOf<ConnectedThread?>(null) }

    // Handler for updating UI with received images
    val handler = remember {
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == 1) { // Image received
                receivedImage = msg.obj as Bitmap
                tts.speak(
                    if (MainActivity.language == "bn") "ছবি গৃহীত হয়েছে" else "Image received",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(longArrayOf(0, 200), -1)
                }
                scope.launch(Dispatchers.IO) {
                    processImage(receivedImage!!, tts, vibrator, MainActivity.language) { text ->
                        detectedText = text
                    }
                }
            }
            true
        }
    }

    // BroadcastReceiver for Bluetooth device discovery
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_FOUND == intent.action && hasPermissions) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    ) {
                        Log.e("Bluetooth", "Missing permissions for device discovery")
                        return
                    }
                    try {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device?.name == "ESP32_CAM_BT" && bluetoothSocket == null) {
                            tts.speak(
                                if (MainActivity.language == "bn") "ইএসপি৩২ ক্যাম পাওয়া গেছে" else "Found ESP32 CAM",
                                TextToSpeech.QUEUE_FLUSH, null, null
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                vibrator.vibrate(longArrayOf(0, 200), -1)
                            }
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                                bluetoothAdapter?.cancelDiscovery()
                            }
                            scope.launch(Dispatchers.IO) {
                                try {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        val socket = device.createRfcommSocketToServiceRecord(
                                            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                                        )
                                        socket.connect()
                                        bluetoothSocket = socket
                                        connectedThread = ConnectedThread(socket, handler).apply { start() }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Connected to ESP32 CAM", Toast.LENGTH_SHORT).show()
                                            tts.speak(
                                                if (MainActivity.language == "bn") "ইএসপি৩২ ক্যামের সাথে সংযুক্ত" else "Connected to ESP32 CAM",
                                                TextToSpeech.QUEUE_FLUSH, null, null
                                            )
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Bluetooth connect permission denied", Toast.LENGTH_SHORT).show()
                                            tts.speak(
                                                if (MainActivity.language == "bn") "ব্লুটুথ সংযোগ অনুমতি প্রত্যাখ্যাত" else "Bluetooth connect permission denied",
                                                TextToSpeech.QUEUE_FLUSH, null, null
                                            )
                                        }
                                    }
                                } catch (e: IOException) {
                                    Log.e("Bluetooth", "Connection failed", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Connection failed", Toast.LENGTH_SHORT).show()
                                        tts.speak(
                                            if (MainActivity.language == "bn") "সংযোগ ব্যর্থ" else "Connection failed",
                                            TextToSpeech.QUEUE_FLUSH, null, null
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            vibrator.vibrate(longArrayOf(0, 500), -1)
                                        }
                                    }
                                } catch (e: SecurityException) {
                                    Log.e("Bluetooth", "Security exception during connection", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Permission denied for Bluetooth", Toast.LENGTH_SHORT).show()
                                        tts.speak(
                                            if (MainActivity.language == "bn") "ব্লুটুথের জন্য অনুমতি প্রত্যাখ্যাত" else "Permission denied for Bluetooth",
                                            TextToSpeech.QUEUE_FLUSH, null, null
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e("Bluetooth", "Security exception in BroadcastReceiver", e)
                        tts.speak(
                            if (MainActivity.language == "bn") "ব্লুটুথ ডিভাইস স্ক্যান করার অনুমতি প্রত্যাখ্যাত" else "Permission denied for device scan",
                            TextToSpeech.QUEUE_FLUSH, null, null
                        )
                    }
                }
            }
        }
    }

    // Register receiver
    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        onDispose {
            if (hasPermissions && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.cancelDiscovery()
            }
            bluetoothSocket?.close()
            connectedThread?.cancel()
        }
    }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.all { it.value }
        if (hasPermissions) {
            tts.speak(
                if (MainActivity.language == "bn") "ব্লুটুথ অনুমতি প্রদান করা হয়েছে" else "Bluetooth permissions granted",
                TextToSpeech.QUEUE_FLUSH, null, null
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(longArrayOf(0, 200), -1)
            }
            if (bluetoothAdapter?.isEnabled == true) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.startDiscovery()
                } else {
                    tts.speak(
                        if (MainActivity.language == "bn") "ব্লুটুথ স্ক্যান অনুমতি প্রয়োজন" else "Bluetooth scan permission required",
                        TextToSpeech.QUEUE_FLUSH, null, null
                    )
                }
            } else {
                tts.speak(
                    if (MainActivity.language == "bn") "ব্লুটুথ সক্রিয় করুন" else "Please enable Bluetooth",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
            }
        } else {
            tts.speak(
                if (MainActivity.language == "bn") "ব্লুটুথ অনুমতি প্রয়োজন" else "Bluetooth permissions required",
                TextToSpeech.QUEUE_FLUSH, null, null
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(longArrayOf(0, 500), -1)
            }
        }
    }

    // Handle volume up to read text and volume down to navigate back
    DisposableEffect(Unit) {
        val listener = object : android.view.View.OnKeyListener {
            override fun onKey(v: android.view.View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (event?.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (detectedText.isNotBlank()) {
                                tts.speak(detectedText, TextToSpeech.QUEUE_FLUSH, null, null)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    vibrator.vibrate(longArrayOf(0, 200), -1)
                                }
                            } else {
                                tts.speak(
                                    if (MainActivity.language == "bn") "কোন টেক্সট পাওয়া যায়নি" else "No text found to read",
                                    TextToSpeech.QUEUE_FLUSH, null, null
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    vibrator.vibrate(longArrayOf(0, 500), -1)
                                }
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            tts.speak(
                                if (MainActivity.language == "bn") "হোম স্ক্রিনে ফিরে যাচ্ছি" else "Going back to homescreen",
                                TextToSpeech.QUEUE_FLUSH, null, null
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                vibrator.vibrate(longArrayOf(0, 200), -1)
                            }
                            navController.navigateUp()
                            return true
                        }
                    }
                }
                return false
            }
        }
        val rootView = (context as? androidx.activity.ComponentActivity)?.window?.decorView
        rootView?.setOnKeyListener(listener)
        onDispose {
            rootView?.setOnKeyListener(null)
        }
    }

    // Start Bluetooth discovery if permissions are granted
    LaunchedEffect(hasPermissions) {
        if (hasPermissions && bluetoothAdapter?.isEnabled == true) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.startDiscovery()
            } else {
                tts.speak(
                    if (MainActivity.language == "bn") "ব্লুটুথ স্ক্যান অনুমতি প্রয়োজন" else "Bluetooth scan permission required",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
            }
        } else if (hasPermissions) {
            tts.speak(
                if (MainActivity.language == "bn") "ব্লুটুথ সক্রিয় করুন" else "Please enable Bluetooth",
                TextToSpeech.QUEUE_FLUSH, null, null
            )
        }
    }

    // Notify if no image received
    LaunchedEffect(receivedImage) {
        if (receivedImage == null) {
            tts.speak(
                if (MainActivity.language == "bn") "এখনো কোন ছবি গৃহীত হয়নি" else "No image yet received",
                TextToSpeech.QUEUE_FLUSH, null, null
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(longArrayOf(0, 500), -1)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (MainActivity.language == "bn") "ব্লুটুথ স্ক্রিন" else "Bluetooth Screen") }) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (hasPermissions) {
                    if (receivedImage != null) {
                        // Show the received image inside a card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Image(
                                bitmap = receivedImage!!.asImageBitmap(),
                                contentDescription = if (MainActivity.language == "bn") "ব্লুটুথ থেকে গৃহীত ছবি" else "Image received from Bluetooth",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            )
                        }

                        // Show detected text in styled box
                        Text(
                            text = detectedText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .background(Color(0xFFF5F5F5))
                                .padding(16.dp),
                            color = Color.Black
                        )

                        // Buttons row: Read text + Fetch new image
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    if (detectedText.isNotBlank()) {
                                        tts.speak(detectedText, TextToSpeech.QUEUE_FLUSH, null, null)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            vibrator.vibrate(longArrayOf(0, 200), -1)
                                        }
                                    } else {
                                        tts.speak(
                                            if (MainActivity.language == "bn") "কোন টেক্সট পাওয়া যায়নি" else "No text found to read",
                                            TextToSpeech.QUEUE_FLUSH, null, null
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            vibrator.vibrate(longArrayOf(0, 500), -1)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(if (MainActivity.language == "bn") "টেক্সট পড়ুন" else "Read Text")
                            }

                            Button(
                                onClick = {
                                    bluetoothSocket?.close()
                                    connectedThread?.cancel()
                                    bluetoothSocket = null
                                    connectedThread = null
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                                        bluetoothAdapter?.startDiscovery()
                                    } else {
                                        tts.speak(
                                            if (MainActivity.language == "bn") "ব্লুটুথ স্ক্যান অনুমতি প্রয়োজন" else "Bluetooth scan permission required",
                                            TextToSpeech.QUEUE_FLUSH, null, null
                                        )
                                    }
                                },
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(if (MainActivity.language == "bn") "নতুন ছবি আনুন" else "Fetch New Image")
                            }
                        }
                    } else {
                        // Show progress while waiting
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Text(
                            text = if (MainActivity.language == "bn") "ইএসপি৩২ ক্যামের জন্য অপেক্ষা করা হচ্ছে..." else "Waiting for ESP32 CAM...",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = if (MainActivity.language == "bn") "ব্লুটুথ স্ক্যান করতে অনুমতি দিন" else "Please grant Bluetooth permissions to scan",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                } else {
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH,
                                        Manifest.permission.BLUETOOTH_ADMIN
                                    )
                                }
                            )
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(if (MainActivity.language == "bn") "অনুমতি দিন" else "Grant Permission")
                    }
                }
            }
        }
    }
}

private fun checkPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }
}

private fun processImage(bitmap: Bitmap, tts: TextToSpeech, vibrator: Vibrator, language: String, onTextDetected: (String) -> Unit) {
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(inputImage)
        .addOnSuccessListener { visionText: Text ->
            val text = visionText.text
            onTextDetected(text)
            if (text.isNotBlank()) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(longArrayOf(0, 200), -1)
                }
            } else {
                tts.speak(
                    if (language == "bn") "কোন টেক্সট পাওয়া যায়নি" else "No text found to read",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(longArrayOf(0, 500), -1)
                }
            }
        }
        .addOnFailureListener { e ->
            tts.speak(
                if (language == "bn") "টেক্সট সনাক্তকরণ ব্যর্থ" else "Text recognition failed",
                TextToSpeech.QUEUE_FLUSH, null, null
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(longArrayOf(0, 500), -1)
            }
            Log.e("CurrencyReader", "OCR failed", e)
        }
}

class ConnectedThread(
    private val socket: BluetoothSocket,
    private val handler: Handler
) : Thread() {
    private val inputStream: InputStream? = try {
        socket.inputStream
    } catch (e: IOException) {
        Log.e("Bluetooth", "Error creating input stream", e)
        null
    }
    private var isRunning = true

    override fun run() {
        val buffer = ByteArray(512) // Match ESP32-CAM chunk size
        var imageSize = 0
        var totalBytesReceived = 0
        var isReceivingImage = false
        val outputStream = ByteArrayOutputStream()

        while (isRunning && inputStream != null) {
            try {
                if (!isReceivingImage) {
                    // Read 4-byte image size (little-endian)
                    val sizeBytes = ByteArray(4)
                    var bytesRead = 0
                    while (bytesRead < 4) {
                        val read = inputStream.read(sizeBytes, bytesRead, 4 - bytesRead)
                        if (read == -1) break
                        bytesRead += read
                    }
                    if (bytesRead == 4) {
                        imageSize = (sizeBytes[0].toInt() and 0xFF) or
                                ((sizeBytes[1].toInt() and 0xFF) shl 8) or
                                ((sizeBytes[2].toInt() and 0xFF) shl 16) or
                                ((sizeBytes[3].toInt() and 0xFF) shl 24)
                        if (imageSize > 0) {
                            isReceivingImage = true
                            totalBytesReceived = 0
                            outputStream.reset()
                            Log.d("Bluetooth", "Starting image reception, size: $imageSize")
                        }
                    }
                } else {
                    // Read image data
                    val numBytes = inputStream.read(buffer)
                    if (numBytes == -1) break
                    outputStream.write(buffer, 0, numBytes)
                    totalBytesReceived += numBytes
                    if (totalBytesReceived >= imageSize) {
                        // Image fully received
                        val imageBytes = outputStream.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            handler.obtainMessage(1, bitmap).sendToTarget()
                            Log.d("Bluetooth", "Image received successfully")
                        } else {
                            Log.e("Bluetooth", "Failed to decode image")
                        }
                        isReceivingImage = false
                        imageSize = 0
                        totalBytesReceived = 0
                        outputStream.reset()
                    }
                }
            } catch (e: IOException) {
                Log.e("Bluetooth", "Input stream disconnected", e)
                break
            }
        }
    }

    fun cancel() {
        isRunning = false
        try {
            socket.close()
        } catch (e: IOException) {
            Log.e("Bluetooth", "Could not close socket", e)
        }
    }
}
