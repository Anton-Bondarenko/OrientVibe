package com.orientvibe.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CompassManager(private val context: Context) {
    private var compassRotation = 0f
    private var previousCompassRotation = 0f
    private var isManuallyRotated = false

    // Touch handling state
    private var isRotatingCompass = false
    private var compassTouchAngle = 0f
    private var compassStartRotation = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isDragging = false

    // Callbacks
    private var onCompassRotationListener: ((Float) -> Unit)? = null

    fun getCurrentRotation(): Float = compassRotation

    fun getPreviousRotation(): Float = previousCompassRotation

    fun isManuallyRotated(): Boolean = isManuallyRotated

    fun setRotation(rotation: Float) {
        compassRotation = rotation
    }

    fun setManualRotation(rotation: Float) {
        compassRotation = rotation
        isManuallyRotated = true
    }

    fun clearManualRotation() {
        isManuallyRotated = false
    }

    fun saveCurrentAsPrevious() {
        previousCompassRotation = compassRotation
    }

    fun restorePrevious() {
        compassRotation = previousCompassRotation
    }

    fun reset() {
        compassRotation = 0f
        previousCompassRotation = 0f
        isManuallyRotated = false
    }

    fun setOnCompassRotationListener(listener: (Float) -> Unit) {
        onCompassRotationListener = listener
    }

    fun drawCompass(canvas: Canvas, viewHeight: Int) {
        val density = context.resources.displayMetrics.density
        val compassX = 90f * density
        val compassY = (viewHeight / 3).toFloat()
        val compassSize = 40f * density

        val compassPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.BLUE
            isAntiAlias = true
        }

        // Draw arrow pointing north (rotated by compassRotation)
        val arrowAngle = compassRotation * kotlin.math.PI.toFloat() / 180f
        val arrowLength = compassSize * 2.6f

        // Arrow shaft (no arrow head)
        val arrowEndX = compassX + arrowLength * sin(arrowAngle)
        val arrowEndY = compassY - arrowLength * cos(arrowAngle)
        canvas.drawLine(compassX, compassY, arrowEndX, arrowEndY, compassPaint)

        // Draw two parallel reference lines above compass (don't rotate)
        val referenceLinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.BLUE
            isAntiAlias = true
        }
        val referenceLineY = compassY - compassSize * 2.5f
        val lineLength = 20f * density
        val gap = 4f

        // Left vertical line
        canvas.drawLine(
            compassX - gap,
            referenceLineY - lineLength / 2,
            compassX - gap,
            referenceLineY + lineLength / 2,
            referenceLinePaint
        )

        // Right vertical line
        canvas.drawLine(
            compassX + gap,
            referenceLineY - lineLength / 2,
            compassX + gap,
            referenceLineY + lineLength / 2,
            referenceLinePaint
        )

        // Draw "N" label - rotates with the arrow
        canvas.save()
        canvas.rotate(compassRotation, compassX, compassY)
        val textPaint = Paint().apply {
            color = Color.BLUE
            textSize = 16f * density
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "N",
            compassX + 10f * density,
            compassY - compassSize - 10f * density,
            textPaint
        )
        canvas.restore()
    }

    fun handleTouchEvent(event: MotionEvent, viewHeight: Int): Boolean {
        val density = context.resources.displayMetrics.density
        val screenX = event.x
        val screenY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isRotatingCompass = false
                touchDownX = screenX
                touchDownY = screenY
                isDragging = false

                // Check if touch is on compass line end (tip)
                val compassX = 60f * density
                val compassY = (viewHeight / 3).toFloat()
                val compassSize = 40f * density
                val arrowAngle = compassRotation * kotlin.math.PI.toFloat() / 180f
                val arrowLength = compassSize * 3.2f

                // Calculate line end position (tip)
                val arrowTipX = compassX + arrowLength * sin(arrowAngle)
                val arrowTipY = compassY - arrowLength * cos(arrowAngle)

                // Check if touch is near line end
                val touchRadius = 60f * density
                val distanceToTip =
                    sqrt((screenX - arrowTipX).pow(2) + (screenY - arrowTipY).pow(2))
                if (distanceToTip <= touchRadius) {
                    isRotatingCompass = true
                    compassTouchAngle = atan2(screenY - compassY, screenX - compassX)
                    compassStartRotation = compassRotation
                    setManualRotation(compassRotation)
                    return true
                }

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                // Check if this is a drag gesture (significant movement)
                val dx = screenX - touchDownX
                val dy = screenY - touchDownY
                val dragThreshold = 10f * density

                if (sqrt(dx * dx + dy * dy) > dragThreshold) {
                    isDragging = true
                }

                if (isRotatingCompass) {
                    val compassX = 60f * density
                    val compassY = (viewHeight / 3).toFloat()
                    val currentAngle = atan2(screenY - compassY, screenX - compassX)
                    val angleDelta =
                        (currentAngle - compassTouchAngle) * 180f / kotlin.math.PI.toFloat()
                    compassRotation = (compassStartRotation + angleDelta) % 360
                    onCompassRotationListener?.invoke(compassRotation)
                    return true
                }

                // If dragging, let PhotoView handle it
                if (isDragging) {
                    return false
                }

                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isRotatingCompass) {
                    clearManualRotation()
                }

                isRotatingCompass = false
                isDragging = false
                return true
            }

            else -> false
        }
        return false
    }

    fun isCompassRotating(): Boolean = isRotatingCompass
}
