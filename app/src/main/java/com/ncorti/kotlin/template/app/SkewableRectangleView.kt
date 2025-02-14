package com.ncorti.kotlin.template.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

class SkewableRectangleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        init {
            System.loadLibrary("opencv_java4")
            Log.d("SkewableRectangleView", "OpenCV library loaded")
        }
    }

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private var isInEditMode = false
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            isInEditMode = !isInEditMode
            invalidate()
            return true
        }
    })
    private val handleRadius = 20f
    private val cornerHandles = mutableListOf<PointF>()
    private var selectedHandleIndex = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var transformedBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    init {
        // Load the image from resources
        originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.burntpixel)
        Log.d("SkewableRectangleView", "Image loaded: ${originalBitmap != null}")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        updateCornerHandles()
        updateImage()
    }

    private fun updateCornerHandles() {
        cornerHandles.clear()
        Log.d("SkewableRectangleView", "updateCornerHandles() called. Width: $viewWidth, Height: $viewHeight")
        cornerHandles.add(PointF(0f, 0f)) // Top-left
        cornerHandles.add(PointF(viewWidth.toFloat(), 0f)) // Top-right
        cornerHandles.add(PointF(viewWidth.toFloat(), viewHeight.toFloat())) // Bottom-right
        cornerHandles.add(PointF(0f, viewHeight.toFloat())) // Bottom-left
    }

    override fun onDraw(canvas: Canvas) {
        Log.d("SkewableRectangleView", "onDraw() called")
        super.onDraw(canvas)
        Log.d("SkewableRectangleView", "transformedBitmap is null: ${transformedBitmap == null}")
        if (transformedBitmap != null) {
            canvas.drawBitmap(transformedBitmap!!, 0f, 0f, null)
        }
        // Draw handles
        drawCornerHandles(canvas)
    }

    private fun updateImage() {
        Log.d("SkewableRectangleView", "updateImage() called")
        if (originalBitmap == null) {
            Log.e("SkewableRectangleView", "originalBitmap is null")
            return
        }

        // Convert cornerHandles to OpenCV Mat
        val srcPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(originalBitmap!!.width.toDouble(), 0.0),
            Point(originalBitmap!!.width.toDouble(), originalBitmap!!.height.toDouble()),
            Point(0.0, originalBitmap!!.height.toDouble())
        )
        val dstPoints = MatOfPoint2f(
            Point(cornerHandles[0].x.toDouble(), cornerHandles[0].y.toDouble()),
            Point(cornerHandles[1].x.toDouble(), cornerHandles[1].y.toDouble()),
            Point(cornerHandles[2].x.toDouble(), cornerHandles[2].y.toDouble()),
            Point(cornerHandles[3].x.toDouble(), cornerHandles[3].y.toDouble())
        )

        // Calculate the perspective transform
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

        // Apply the transform
        val srcMat = Mat()
        Utils.bitmapToMat(originalBitmap, srcMat)
        val dstMat = Mat()
        Imgproc.warpPerspective(srcMat, dstMat, perspectiveTransform, org.opencv.core.Size(viewWidth.toDouble(), viewHeight.toDouble()))

        // Convert back to Bitmap
        transformedBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dstMat, transformedBitmap)
        Log.d("SkewableRectangleView", "Image transformed successfully")
        invalidate()
    }

    private fun drawCornerHandles(canvas: Canvas) {
        cornerHandles.forEach { handle ->
            canvas.drawCircle(handle.x, handle.y, handleRadius, handlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                selectedHandleIndex = getSelectedHandleIndex(event.x, event.y)
                if (selectedHandleIndex != -1) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                return selectedHandleIndex != -1
            }

            MotionEvent.ACTION_MOVE -> {
                if (selectedHandleIndex != -1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    cornerHandles[selectedHandleIndex] = PointF(cornerHandles[selectedHandleIndex].x + dx, cornerHandles[selectedHandleIndex].y + dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateImage()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                selectedHandleIndex = -1
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getSelectedHandleIndex(x: Float, y: Float): Int {
        cornerHandles.forEachIndexed { index, handle ->
            val distance = calculateDistance(x, y, handle.x, handle.y)
            if (distance <= handleRadius) {
                return index
            }
        }
        return -1
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt(((x1 - x2) * (x1 - x2)) + ((y1 - y2) * (y1 - y2)))
    }
}