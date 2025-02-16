package com.ncorti.kotlin.template.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import androidx.core.content.ContextCompat

class SkewableRectangleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var videoSurface: Surface? = null
    private val handlePaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
    private var burntPixelLogo: Bitmap? = null
    private var isInEditMode = false
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            isInEditMode = !isInEditMode
            invalidate()
            return true
        }
    })
    private val handleRadius = 40f
    private val cornerHandles = mutableListOf<PointF>()
    private var selectedHandleIndex = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var viewWidth = 0
    private var viewHeight = 0

    init {
        ContextCompat.getDrawable(context, R.drawable.burntpixel)?.let {
            burntPixelLogo = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                it.setBounds(0, 0, bitmap.width, bitmap.height)
                it.draw(Canvas(bitmap))
            }
        }
    }

    fun setVideoSurface(surface: Surface?) {
        Log.d("SkewableRectangleView", "setVideoSurface called")
        videoSurface = surface
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        updateCornerHandles()
    }

    private fun updateCornerHandles() {
        cornerHandles.apply {
            clear()
            add(PointF(0f, 0f)) // Top-left
            add(PointF(viewWidth.toFloat(), 0f)) // Top-right
            add(PointF(viewWidth.toFloat(), viewHeight.toFloat())) // Bottom-right
            add(PointF(0f, viewHeight.toFloat())) // Bottom-left
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        applySkewTransformation(canvas)
        if (isInEditMode && burntPixelLogo != null) drawStretchedLogo(canvas)
        drawCornerHandles(canvas)
    }

    private fun applySkewTransformation(canvas: Canvas) {
        val matrix = Matrix()
        val src = floatArrayOf(0f, 0f, viewWidth.toFloat(), 0f, viewWidth.toFloat(), viewHeight.toFloat(), 0f, viewHeight.toFloat())
        val dst = floatArrayOf(cornerHandles[0].x, cornerHandles[0].y, cornerHandles[1].x, cornerHandles[1].y, cornerHandles[2].x, cornerHandles[2].y, cornerHandles[3].x, cornerHandles[3].y)
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        canvas.concat(matrix)
    }

    private fun drawCornerHandles(canvas: Canvas) {
        if (isInEditMode) cornerHandles.forEach { canvas.drawCircle(it.x, it.y, handleRadius, handlePaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInEditMode) {
                    selectedHandleIndex = findSelectedHandle(event.x, event.y)
                    if (selectedHandleIndex != -1) {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isInEditMode && selectedHandleIndex != -1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    cornerHandles[selectedHandleIndex].apply { x += dx; y += dy }
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isInEditMode && selectedHandleIndex != -1) {
                    selectedHandleIndex = -1
                    return true
                }
            }
        }
        return true
    }

    private fun findSelectedHandle(x: Float, y: Float): Int {
        cornerHandles.forEachIndexed { index, handle ->
            if (x in handle.x - handleRadius..handle.x + handleRadius &&
                y in handle.y - handleRadius..handle.y + handleRadius) {
                return index
            }
        }
        return -1
    }

    private fun drawStretchedLogo(canvas: Canvas) {
        burntPixelLogo?.let { logo ->
            val matrix = Matrix()
            matrix.postScale(viewWidth.toFloat() / logo.width, viewHeight.toFloat() / logo.height)
            canvas.drawBitmap(logo, matrix, null)
        }
    }
}