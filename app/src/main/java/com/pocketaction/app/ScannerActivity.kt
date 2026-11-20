package com.pocketaction.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var finished = false
    private var mode: String = MODE_QR
    private val qrScanner by lazy { BarcodeScanning.getClient() }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_QR
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_SHORT).show(); finish(); return
        }
        buildScannerUi()
        startCamera()
    }

    private fun buildScannerUi() {
        window.statusBarColor = Color.BLACK
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(24)) }
        val title = TextView(this).apply {
            text = if (mode == MODE_QR) "Scan QR Code" else "Camera Text Scan"
            textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        overlay.addView(title)
        status = TextView(this).apply {
            text = if (mode == MODE_QR) "Place a QR code inside the camera view." else "Point the camera at clear, readable text."
            textSize = 13f; setTextColor(Color.WHITE); setPadding(0, dp(6), 0, 0)
        }
        overlay.addView(status)
        val close = TextView(this).apply {
            text = "Close"; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12)); setBackgroundColor(0x66000000)
            setOnClickListener { finish() }
        }
        val bottom = FrameLayout(this).apply { addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }
        overlay.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy -> analyzeFrame(proxy) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (error: Exception) {
                status.text = "Unable to start camera. Please try again."
                android.util.Log.e("PocketScanner", "Camera start failed", error)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    @ExperimentalGetImage
    private fun analyzeFrame(proxy: androidx.camera.core.ImageProxy) {
        if (finished || !busy.compareAndSet(false, true)) { proxy.close(); return }
        val mediaImage = proxy.image
        if (mediaImage == null) { busy.set(false); proxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        if (mode == MODE_QR) {
            qrScanner.process(image)
                .addOnSuccessListener { codes ->
                    val value = codes.firstNotNullOfOrNull { it.rawValue }
                    if (!value.isNullOrBlank()) finishWithResult(value)
                }
                .addOnFailureListener { android.util.Log.w("PocketScanner", "QR analysis failed", it) }
                .addOnCompleteListener { proxy.close(); busy.set(false) }
        } else {
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    if (result.text.isNotBlank() && result.text.length >= 4) finishWithResult(result.text)
                }
                .addOnFailureListener { android.util.Log.w("PocketScanner", "Text analysis failed", it) }
                .addOnCompleteListener { proxy.close(); busy.set(false) }
        }
    }

    private fun finishWithResult(value: String) {
        if (finished) return
        finished = true
        runOnUiThread {
            setResult(RESULT_OK, intent.putExtra(EXTRA_RESULT, value))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { qrScanner.close() } catch (_: Exception) { }
        try { textRecognizer.close() } catch (_: Exception) { }
        try { executor.shutdown() } catch (_: Exception) { }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_MODE = "scanner_mode"
        const val EXTRA_RESULT = "scanner_result"
        const val MODE_QR = "qr"
        const val MODE_TEXT = "text"
    }
}
