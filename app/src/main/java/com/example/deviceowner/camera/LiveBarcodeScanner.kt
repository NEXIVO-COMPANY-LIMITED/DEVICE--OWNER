package com.example.deviceowner.camera

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.Barcode
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveBarcodeScanner(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (barcode: String) -> Unit,
    isEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        BarcodeScannerContent(
            modifier = modifier,
            context = context,
            lifecycleOwner = lifecycleOwner,
            onBarcodeDetected = onBarcodeDetected,
            isEnabled = isEnabled
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera permission required",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun BarcodeScannerContent(
    modifier: Modifier,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onBarcodeDetected: (barcode: String) -> Unit,
    isEnabled: Boolean
) {
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Build preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Build image analysis for barcode scanning
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcodes ->
                            if (isEnabled) {
                                barcodes.forEach { barcode ->
                                    val rawValue = barcode.rawValue as? String
                                    if (rawValue != null) {
                                        // Run callback on main thread to avoid UI crashes
                                        try {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                try {
                                                    onBarcodeDetected(rawValue)
                                                } catch (e: Exception) {
                                                    Log.e("LiveBarcodeScanner", "Error in barcode callback", e)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("LiveBarcodeScanner", "Error launching coroutine", e)
                                        }
                                    }
                                }
                            }
                        })
                    }

                // Select BACK camera (like Google Lens)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind all previous use cases and bind new ones
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d("LiveBarcodeScanner", "Back camera initialized successfully")
            } catch (e: Exception) {
                Log.e("LiveBarcodeScanner", "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                // Only unbind if the camera provider future is already done
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                Log.e("LiveBarcodeScanner", "Failed to unbind camera", e)
            }
            try {
                cameraExecutor.shutdown()
            } catch (e: Exception) {
                Log.e("LiveBarcodeScanner", "Failed to shutdown executor", e)
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}
