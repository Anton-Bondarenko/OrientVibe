package com.orientvibe.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class RouteRenderer(private val context: Context) {
    private var startPoint: Pair<Float, Float>? = null
    private var endPoint: Pair<Float, Float>? = null
    private var originalStartPoint: Pair<Float, Float>? = null
    private var originalEndPoint: Pair<Float, Float>? = null
    private var scaleFactor: Float = 1f

    fun setNavigationPoints(start: Pair<Float, Float>?, end: Pair<Float, Float>?) {
        this.startPoint = start
        this.endPoint = end
    }

    fun setScaleFactor(factor: Float) {
        this.scaleFactor = factor
    }

    fun getStartPoint(): Pair<Float, Float>? = startPoint

    fun getEndPoint(): Pair<Float, Float>? = endPoint

    fun saveOriginalPoints() {
        originalStartPoint = startPoint?.let { it.first to it.second }
        originalEndPoint = endPoint?.let { it.first to it.second }
    }

    fun transformCoordinates(
        originalImageWidth: Float,
        originalImageHeight: Float,
        transformedImageWidth: Float,
        transformedImageHeight: Float,
        rotation: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        val centerX = originalImageWidth / 2f
        val centerY = originalImageHeight / 2f

        android.util.Log.d("RouteRenderer", "transformCoordinates - rotation: $rotation, offset: ($offsetX, $offsetY)")
        android.util.Log.d("RouteRenderer", "originalImageWidth: $originalImageWidth, originalImageHeight: $originalImageHeight")
        android.util.Log.d("RouteRenderer", "transformedImageWidth: $transformedImageWidth, transformedImageHeight: $transformedImageHeight")
        android.util.Log.d("RouteRenderer", "centerX: $centerX, centerY: $centerY")

        android.util.Log.d("RouteRenderer", "originalStartPoint: $originalStartPoint")
        android.util.Log.d("RouteRenderer", "originalEndPoint: $originalEndPoint")

        startPoint = originalStartPoint?.let { (x, y) ->
            val pixelX = x * originalImageWidth
            val pixelY = y * originalImageHeight
            android.util.Log.d("RouteRenderer", "Start point normalized: ($x, $y), pixel (original): ($pixelX, $pixelY)")
            val (transformedX, transformedY) = CoordinateTransformer.transformPoint(
                pixelX,
                pixelY,
                rotation,
                centerX,
                centerY,
                offsetX,
                offsetY
            )
            android.util.Log.d("RouteRenderer", "Start point transformed pixel: ($transformedX, $transformedY)")
            val normalizedX = transformedX / transformedImageWidth
            val normalizedY = transformedY / transformedImageHeight
            android.util.Log.d("RouteRenderer", "Start point transformed normalized: ($normalizedX, $normalizedY)")
            normalizedX to normalizedY
        }

        endPoint = originalEndPoint?.let { (x, y) ->
            val pixelX = x * originalImageWidth
            val pixelY = y * originalImageHeight
            android.util.Log.d("RouteRenderer", "End point normalized: ($x, $y), pixel (original): ($pixelX, $pixelY)")
            val (transformedX, transformedY) = CoordinateTransformer.transformPoint(
                pixelX,
                pixelY,
                rotation,
                centerX,
                centerY,
                offsetX,
                offsetY
            )
            android.util.Log.d("RouteRenderer", "End point transformed pixel: ($transformedX, $transformedY)")
            val normalizedX = transformedX / transformedImageWidth
            val normalizedY = transformedY / transformedImageHeight
            android.util.Log.d("RouteRenderer", "End point transformed normalized: ($normalizedX, $normalizedY)")
            normalizedX to normalizedY
        }

        android.util.Log.d("RouteRenderer", "Final startPoint: $startPoint")
        android.util.Log.d("RouteRenderer", "Final endPoint: $endPoint")
    }

    fun restoreOriginalPoints() {
        startPoint = originalStartPoint?.let { it.first to it.second }
        endPoint = originalEndPoint?.let { it.first to it.second }
    }

    fun clearOriginalPoints() {
        originalStartPoint = null
        originalEndPoint = null
    }

    fun draw(
        canvas: Canvas,
        displayRect: RectF,
        imageWidth: Float,
        imageHeight: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        drawNavigationLine(canvas, displayRect, imageWidth, imageHeight, scaleX, scaleY)
        drawCrosses(canvas, displayRect, imageWidth, imageHeight, scaleX, scaleY)
    }

    private fun drawNavigationLine(
        canvas: Canvas,
        displayRect: RectF,
        imageWidth: Float,
        imageHeight: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        if (startPoint == null || endPoint == null) {
            return
        }

        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f * scaleFactor
            color = Color.GREEN
            isAntiAlias = true
        }

        val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
        val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
        val endX = displayRect.left + endPoint!!.first * imageWidth * scaleX
        val endY = displayRect.top + endPoint!!.second * imageHeight * scaleY

        canvas.drawLine(startX, startY, endX, endY, paint)

        // Draw direction arrow in the middle
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2
        val angle = atan2(endY - startY, endX - startX)
        val arrowLength = (40f / 3f) * context.resources.displayMetrics.density * scaleFactor
        val arrowAngle = (kotlin.math.PI / 6).toFloat() // 30 degrees

        val arrowPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f * scaleFactor
            color = Color.GREEN
            isAntiAlias = true
        }

        // Arrow head lines
        val arrowX1 = midX - arrowLength * cos(angle - arrowAngle)
        val arrowY1 = midY - arrowLength * sin(angle - arrowAngle)
        val arrowX2 = midX - arrowLength * cos(angle + arrowAngle)
        val arrowY2 = midY - arrowLength * sin(angle + arrowAngle)

        canvas.drawLine(midX, midY, arrowX1, arrowY1, arrowPaint)
        canvas.drawLine(midX, midY, arrowX2, arrowY2, arrowPaint)
    }

    private fun drawCrosses(
        canvas: Canvas,
        displayRect: RectF,
        imageWidth: Float,
        imageHeight: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        // Draw start point cross (red)
        if (startPoint != null) {
            val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
            val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
            val crossSize = 30f * context.resources.displayMetrics.density * scaleFactor

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f * scaleFactor
                color = Color.RED
                isAntiAlias = true
            }

            // Draw X cross
            canvas.drawLine(
                startX - crossSize,
                startY - crossSize,
                startX + crossSize,
                startY + crossSize,
                paint
            )
            canvas.drawLine(
                startX + crossSize,
                startY - crossSize,
                startX - crossSize,
                startY + crossSize,
                paint
            )
        }

        // Draw end point cross (green)
        if (endPoint != null) {
            val endX = displayRect.left + endPoint!!.first * imageWidth * scaleX
            val endY = displayRect.top + endPoint!!.second * imageHeight * scaleY
            val crossSize = 30f * context.resources.displayMetrics.density * scaleFactor

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f * scaleFactor
                color = Color.GREEN
                isAntiAlias = true
            }

            // Draw X cross
            canvas.drawLine(
                endX - crossSize,
                endY - crossSize,
                endX + crossSize,
                endY + crossSize,
                paint
            )
            canvas.drawLine(
                endX + crossSize,
                endY - crossSize,
                endX - crossSize,
                endY + crossSize,
                paint
            )
        }
    }
}
