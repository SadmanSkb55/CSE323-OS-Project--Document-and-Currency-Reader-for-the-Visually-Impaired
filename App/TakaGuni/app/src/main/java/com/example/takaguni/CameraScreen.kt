package com.example.takaguni

import android.Manifest
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.material.icons.filled.PlayArrow

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraScreen(navController: NavController, tts: TextToSpeech, vibrator: android.os.Vibrator) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            tts.speak(if (MainActivity.language == "bn") "ক্যামেরা অনুমতি প্রদান করা হয়েছে" else "Camera permission granted", TextToSpeech.QUEUE_FLUSH, null, null)
            vibrator.vibrate(longArrayOf(0, 200), -1)
        } else {
            tts.speak(if (MainActivity.language == "bn") "ক্যামেরা অনুমতি প্রয়োজন" else "Camera permission required", TextToSpeech.QUEUE_FLUSH, null, null)
            vibrator.vibrate(longArrayOf(0, 500), -1)
        }
    }

    // Handle volume down to navigate back
    DisposableEffect(Unit) {
        val listener = object : android.view.View.OnKeyListener {
            override fun onKey(v: android.view.View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (event?.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    tts.speak(if (MainActivity.language == "bn") "মূল স্ক্রিনে ফিরে যাওয়া হয়েছে" else "Returned to main screen", TextToSpeech.QUEUE_FLUSH, null, null)
                    vibrator.vibrate(longArrayOf(0, 200), -1)
                    navController.navigateUp()
                    return true
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

    if (hasCameraPermission) {
        CameraContent(navController, tts, vibrator)
    } else {
        NoPermissionScreen(
            onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            tts = tts,
            vibrator = vibrator
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalGetImage::class, ExperimentalMaterial3Api::class)
@Composable
private fun CameraContent(navController: NavController, tts: TextToSpeech, vibrator: android.os.Vibrator) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context).apply { cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA } }
    var detectedText by remember { mutableStateOf("No text detected yet...") }
    val scope = rememberCoroutineScope()

    fun onTextUpdated(updatedText: String) {
        detectedText = updatedText
        scope.launch {
            delay((MainActivity.ocrDelay * 1000).toLong())
            if (updatedText.isNotBlank()) {
                tts.speak(updatedText, TextToSpeech.QUEUE_FLUSH, null, null)
                vibrator.vibrate(longArrayOf(0, 200), -1)
            } else {
                tts.speak(
                    if (MainActivity.language == "bn") "কোন টেক্সট পাওয়া যায়নি" else "No text found to read",
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
                vibrator.vibrate(longArrayOf(0, 500), -1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(if (MainActivity.language == "bn") "মোবাইল ক্যামেরা" else "Mobile Camera") }) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.BottomCenter
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_START
                    }.also { previewView ->
                        startTextRecognition(
                            context = ctx,
                            cameraController = cameraController,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            onDetectedTextUpdated = ::onTextUpdated
                        )
                    }
                }
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                text = detectedText
            )
        }
    }
}

@Composable
private fun NoPermissionScreen(onRequestPermission: () -> Unit, tts: TextToSpeech, vibrator: android.os.Vibrator) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Text(
                textAlign = TextAlign.Center,
                text = if (MainActivity.language == "bn") "টেক্সট স্ক্যান করতে ক্যামেরা অনুমতি দিন" else "Please grant the camera permission to scan text"
            )
            Button(onClick = onRequestPermission) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                    contentDescription = "Camera"
                )
                Text(if (MainActivity.language == "bn") "অনুমতি দিন" else "Grant permission")
            }
        }
    }
}

private fun startTextRecognition(
    context: Context,
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onDetectedTextUpdated: (String) -> Unit
) {
    cameraController.imageAnalysisTargetSize = CameraController.OutputSize(AspectRatio.RATIO_16_9)
    cameraController.setImageAnalysisAnalyzer(
        ContextCompat.getMainExecutor(context),
        TextRecognitionAnalyzer(onDetectedTextUpdated = onDetectedTextUpdated)
    )
    cameraController.setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
    cameraController.bindToLifecycle(lifecycleOwner)
    previewView.controller = cameraController
}

class TextRecognitionAnalyzer(
    private val onDetectedTextUpdated: (String) -> Unit
) : ImageAnalysis.Analyzer {
    companion object {
        const val THROTTLE_TIMEOUT_MS = 1_000L
    }

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        scope.launch {
            val mediaImage = imageProxy.image ?: run { imageProxy.close(); return@launch }
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            suspendCoroutine { continuation ->
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText: Text ->
                        val detectedText = visionText.text
                        if (detectedText.isNotBlank()) {
                            onDetectedTextUpdated(detectedText)
                        }
                    }
                    .addOnCompleteListener {
                        continuation.resume(Unit)
                    }
            }

            delay(THROTTLE_TIMEOUT_MS)
        }.invokeOnCompletion { exception ->
            exception?.printStackTrace()
            imageProxy.close()
        }
    }
}