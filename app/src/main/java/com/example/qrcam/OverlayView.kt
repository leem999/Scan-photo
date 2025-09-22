
package com.example.qrcam
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
): View(context, attrs) {

    data class Box(val leftN: Float, val topN: Float, val rightN: Float, val bottomN: Float, val label: String, val tag: String)

    private val boxes = mutableListOf<Box>()
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.CYAN; pathEffect = DashPathEffect(floatArrayOf(14f,10f),0f) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; setShadowLayer(6f,0f,0f,Color.BLACK); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80,0,0,0); style = Paint.Style.FILL }

    fun setBoxes(list: List<Box>) { synchronized(boxes) { boxes.clear(); boxes.addAll(list) }; postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val local = synchronized(boxes) { boxes.toList() }
        for (b in local) {
            val l = b.leftN*w; val t = b.topN*h; val r = b.rightN*w; val bt = b.bottomN*h
            val rect = RectF(l,t,r,bt)
            canvas.drawRect(rect, rectPaint)
            val label = "${b.tag}:${b.label}"
            val tw = textPaint.measureText(label) + 24f
            val th = textPaint.textSize + 16f
            val bg = RectF(l, t - th, l + tw, t)
            canvas.drawRoundRect(bg, 12f, 12f, fillPaint)
            canvas.drawText(label, bg.left + 12f, bg.bottom - 8f, textPaint)
        }
    }
}
