
package com.example.qrcam
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlin.math.max

class BarcodeAnalyzer(private val onResult: (DetectionResult) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()

    data class DetectedCode(val raw: String, val leftN: Float, val topN: Float, val rightN: Float, val bottomN: Float, val cx: Float, val cy: Float)
    data class Corners(val tl: DetectedCode, val tr: DetectedCode, val bl: DetectedCode, val br: DetectedCode)
    data class DetectionResult(val ok: Boolean, val reason: String, val fullId: String? = null, val corners: Corners? = null, val allDetected: List<DetectedCode> = emptyList())

    override fun analyze(imageProxy: ImageProxy) {
        val img = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)
        val W = image.width.toFloat(); val H = image.height.toFloat()
        scanner.process(image).addOnSuccessListener { codes ->
            val items = codes.mapNotNull { b -> map(b, W, H) }
            onResult(evaluate(items))
        }.addOnFailureListener { e ->
            onResult(DetectionResult(false, "识别失败：${e.message}"))
        }.addOnCompleteListener { imageProxy.close() }
    }

    private fun map(b: com.google.mlkit.vision.barcode.Barcode, W: Float, H: Float): DetectedCode? {
        val raw = b.rawValue?.trim() ?: return null
        val bb = b.boundingBox ?: return null
        val lN = (bb.left / max(1f, W)).coerceIn(0f,1f)
        val tN = (bb.top / max(1f, H)).coerceIn(0f,1f)
        val rN = (bb.right / max(1f, W)).coerceIn(0f,1f)
        val bN = (bb.bottom / max(1f, H)).coerceIn(0f,1f)
        val cx = ((bb.left + bb.right)/2f / max(1f,W)).coerceIn(0f,1f)
        val cy = ((bb.top + bb.bottom)/2f / max(1f,H)).coerceIn(0f,1f)
        return DetectedCode(raw, lN, tN, rN, bN, cx, cy)
    }

    private fun isFullId(s: String) = s.length >= 6 && s.all { it.isLetterOrDigit() }

    private fun evaluate(list: List<DetectedCode>): DetectionResult {
        if (list.size < 4) return DetectionResult(false, "二维码不足4个", allDetected = list)
        val four = list.map { it to score(it) }.sortedByDescending { it.second }.take(4).map { it.first }
        fun cx(o: DetectedCode)=o.cx; fun cy(o: DetectedCode)=o.cy
        val tl = four.minBy { cx(it)+cy(it) }
        val tr = four.minBy { cy(it)+(1-cx(it)) }
        val bl = four.minBy { (1-cy(it))+cx(it) }
        val br = four.minBy { (1-cy(it))+(1-cx(it)) }
        val fullId = tr.raw
        if (!isFullId(fullId)) return DetectionResult(false, "右上角学籍号格式不符", allDetected = list)
        val last3 = fullId.takeLast(3)
        if (!last3.all { it.isDigit() }) return DetectionResult(false, "学籍号后三位必须是数字", allDetected = list)
        val singles = listOf(tl.raw, bl.raw, br.raw)
        if (!singles.all { it.length==1 && it[0].isDigit() }) return DetectionResult(false, "三个小码必须各为1位数字", allDetected = list)
        fun count(s:String)= s.groupingBy{it}.eachCount()
        val needC = count(last3); val gotC = count(singles.joinToString(""))
        val match = needC.all{ (k,v)-> gotC[k]==v }
        if (!match) return DetectionResult(false, "三个小码与后三位不匹配（应为 ${last3.toCharArray().joinToString("+")} 的任意顺序）", allDetected = list)
        return DetectionResult(true, "条件满足", fullId, Corners(tl,tr,bl,br), allDetected = list)
    }
    private fun score(c:DetectedCode):Float{ val dx=c.cx-0.5f; val dy=c.cy-0.5f; return dx*dx+dy*dy }
}
