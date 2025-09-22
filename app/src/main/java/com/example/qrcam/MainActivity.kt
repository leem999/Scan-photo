
package com.example.qrcam
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.qrcam.BarcodeAnalyzer.DetectionResult
import com.example.qrcam.OverlayView.Box
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var overlay: OverlayView
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var shooting = false
    private var lastShotAt = 0L

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        overlay = findViewById(R.id.overlay)
        requestCamera()
    }

    private fun requestCamera() {
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (ok) startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(previewView.display.rotation).build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(previewView.display.rotation).build()
            val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { res -> runOnUiThread { handleDetection(res) } })
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analyzer)
                statusText.text = "相机已开启，开始识别..."
            } catch (e: Exception) {
                statusText.text = "相机启动失败：${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleDetection(res: DetectionResult) {
        val boxes = mutableListOf<Box>()
        res.allDetected.forEach { d -> boxes.add(Box(d.leftN, d.topN, d.rightN, d.bottomN, d.raw, "DET")) }
        res.corners?.let { c ->
            boxes.add(Box(c.tl.leftN, c.tl.topN, c.tl.rightN, c.tl.bottomN, c.tl.raw, "TL"))
            boxes.add(Box(c.tr.leftN, c.tr.topN, c.tr.rightN, c.tr.bottomN, c.tr.raw, "TR"))
            boxes.add(Box(c.bl.leftN, c.bl.topN, c.bl.rightN, c.bl.bottomN, c.bl.raw, "BL"))
            boxes.add(Box(c.br.leftN, c.br.topN, c.br.rightN, c.br.bottomN, c.br.raw, "BR"))
        }
        overlay.setBoxes(boxes)

        if (!res.ok) { statusText.text = "识别中：${res.reason}"; return }
        statusText.text = "条件满足，自动拍照（学籍号：${res.fullId}）"

        val now = System.currentTimeMillis()
        if (shooting || now - lastShotAt < 1500) return
        shooting = true
        takePhoto(res.fullId ?: "student")
    }

    private fun takePhoto(studentId: String) {
        val capture = imageCapture ?: return
        val name = "${studentId}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StudentShots")
        }
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
        capture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) { shooting = false; statusText.text = "拍照失败：${exc.message}" }
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { shooting = false; lastShotAt = System.currentTimeMillis(); statusText.text = "已保存：相册/Pictures/StudentShots/$name" }
        })
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}
