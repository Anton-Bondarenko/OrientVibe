package com.orientvibe.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.github.chrisbanes.photoview.PhotoViewAttacher
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class OverlayView(context: Context) : View(context) {
    
    private var bitmap: android.graphics.Bitmap? = null
    private var currentDetections: List<DetectionResult> = emptyList()
    private var startPoint: Pair<Float, Float>? = null
    private var endPoint: Pair<Float, Float>? = null
    private var startControlPointIndex: Int? = null
    private var endControlPointIndex: Int? = null
    private var compassRotation: Float = 0f
    private var azimuth: Float? = null
    
    private var attacher: PhotoViewAttacher? = null
    private var onCompassRotationListener: ((Float) -> Unit)? = null
    private var onCrossTouchListener: ((String) -> Unit)? = null
    private var onMapTouchListener: ((Float, Float) -> Unit)? = null
    private var onTouchListener: ((android.view.View, android.view.MotionEvent) -> Boolean)? = null
    
    private var isRotatingCompass = false
    private var compassTouchAngle = 0f
    private var compassStartRotation = 0f
    
    // Gesture detection
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isDragging = false
    
    fun setBitmap(bitmap: android.graphics.Bitmap) {
        this.bitmap = bitmap
        invalidate()
    }
    
    fun setDetections(detections: List<DetectionResult>) {
        this.currentDetections = detections
        invalidate()
    }
    
    fun setNavigationPoints(start: Pair<Float, Float>?, end: Pair<Float, Float>?) {
        this.startPoint = start
        this.endPoint = end
        invalidate()
    }
    
    fun setSelectedControlPoints(startIndex: Int?, endIndex: Int?) {
        this.startControlPointIndex = startIndex
        this.endControlPointIndex = endIndex
        invalidate()
    }
    
    fun setCompassRotation(rotation: Float) {
        this.compassRotation = rotation
        invalidate()
    }

    fun setAzimuth(azimuth: Float?) {
        this.azimuth = azimuth
        invalidate()
    }
    
    fun setAttacher(attacher: PhotoViewAttacher) {
        this.attacher = attacher
    }
    
    fun setOnCompassRotationListener(listener: (Float) -> Unit) {
        this.onCompassRotationListener = listener
    }
    
    fun setOnCrossTouchListener(listener: (String) -> Unit) {
        this.onCrossTouchListener = listener
    }
    
    fun setOnMapTouchListener(listener: (Float, Float) -> Unit) {
        this.onMapTouchListener = listener
    }
    
    fun setOnTouchListener(listener: (android.view.View, android.view.MotionEvent) -> Boolean) {
        this.onTouchListener = listener
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val bitmap = bitmap ?: return
        val attacher = attacher ?: return
        val displayRect = attacher.displayRect ?: return
        
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        
        // Calculate scale factors
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight
        
        // Draw control point circles
        drawControlPoints(canvas, displayRect, scaleX, scaleY)
        
        // Draw navigation line
        drawNavigationLine(canvas, displayRect, imageWidth, imageHeight, scaleX, scaleY)
        
        // Draw crosses
        drawCrosses(canvas, displayRect, imageWidth, imageHeight, scaleX, scaleY)

        // Draw compass
        drawCompass(canvas, displayRect)
    }
    
    private fun drawControlPoints(canvas: Canvas, displayRect: android.graphics.RectF, scaleX: Float, scaleY: Float) {
        for ((index, detection) in currentDetections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2
                val radius = min(box.right - box.left, box.bottom - box.top) / 2 * 0.8f
                
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    color = Color.RED
                }
                
                // Check if this is a selected control point
                val isSelected = index == startControlPointIndex || index == endControlPointIndex
                if (isSelected) {
                    paint.strokeWidth = 8f
                    paint.color = Color.YELLOW
                }
                
                // Convert to screen coordinates
                val screenCenterX = displayRect.left + centerX * scaleX
                val screenCenterY = displayRect.top + centerY * scaleY
                val screenRadius = radius * scaleX
                
                canvas.drawCircle(screenCenterX, screenCenterY, screenRadius, paint)
            }
        }
    }
    
    private fun drawNavigationLine(canvas: Canvas, displayRect: android.graphics.RectF, imageWidth: Float, imageHeight: Float, scaleX: Float, scaleY: Float) {
        if (startPoint == null || endPoint == null) {
            return
        }
        
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
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
        val arrowLength = (40f / 3f) * resources.displayMetrics.density
        val arrowAngle = (kotlin.math.PI / 6).toFloat() // 30 degrees
        
        val arrowPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
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
    
    private fun drawCrosses(canvas: Canvas, displayRect: android.graphics.RectF, imageWidth: Float, imageHeight: Float, scaleX: Float, scaleY: Float) {
        // Draw start point cross (red)
        if (startPoint != null) {
            val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
            val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
            val crossSize = 30f * resources.displayMetrics.density
            
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.RED
                isAntiAlias = true
            }
            
            // Draw X cross
            canvas.drawLine(startX - crossSize, startY - crossSize, startX + crossSize, startY + crossSize, paint)
            canvas.drawLine(startX + crossSize, startY - crossSize, startX - crossSize, startY + crossSize, paint)
        }
        
        // Draw end point cross (green)
        if (endPoint != null) {
            val endX = displayRect.left + endPoint!!.first * imageWidth * scaleX
            val endY = displayRect.top + endPoint!!.second * imageHeight * scaleY
            val crossSize = 30f * resources.displayMetrics.density
            
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.GREEN
                isAntiAlias = true
            }
            
            // Draw X cross
            canvas.drawLine(endX - crossSize, endY - crossSize, endX + crossSize, endY + crossSize, paint)
            canvas.drawLine(endX + crossSize, endY - crossSize, endX - crossSize, endY + crossSize, paint)

            // Draw azimuth text
            if (azimuth != null) {
                val textPaint = Paint().apply {
                    color = Color.GREEN
                    textSize = 18f * resources.displayMetrics.density
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.FILL
                }

                val backgroundPaint = Paint().apply {
                    color = Color.BLACK
                    alpha = 180
                    style = Paint.Style.FILL
                }

                val azimuthText = "Азимут: ${"%.1f".format(azimuth)}°"
                val textWidth = textPaint.measureText(azimuthText)
                val textHeight = textPaint.textSize
                val padding = 8f * resources.displayMetrics.density

                val textX = endX
                val textY = endY + crossSize + padding + textHeight

                // Draw background rectangle
                val bgLeft = textX - textWidth / 2 - padding
                val bgRight = textX + textWidth / 2 + padding
                val bgTop = textY - textHeight - padding
                val bgBottom = textY + padding

                canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, backgroundPaint)
                canvas.drawText(azimuthText, textX, textY, textPaint)
            }
        }
    }
    
    private fun drawCompass(canvas: Canvas, displayRect: android.graphics.RectF) {
        val compassX = displayRect.left + 60f * resources.displayMetrics.density
        val compassY = displayRect.top + displayRect.height() / 3
        val compassSize = 40f * resources.displayMetrics.density
        
        val compassPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.BLUE
            isAntiAlias = true
        }
        
        // Draw arrow pointing north (rotated by compassRotation)
        val arrowAngle = compassRotation * kotlin.math.PI.toFloat() / 180f
        val arrowLength = compassSize * 1.6f
        
        // Arrow shaft (no arrow head)
        val arrowEndX = compassX + arrowLength * sin(arrowAngle)
        val arrowEndY = compassY - arrowLength * cos(arrowAngle)
        canvas.drawLine(compassX, compassY, arrowEndX, arrowEndY, compassPaint)
        
        // Draw "N" label - rotates with the arrow
        canvas.save()
        canvas.rotate(compassRotation, compassX, compassY)
        val textPaint = Paint().apply {
            color = Color.BLUE
            textSize = 16f * resources.displayMetrics.density
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("N", compassX + 10f * resources.displayMetrics.density, compassY - compassSize - 10f * resources.displayMetrics.density, textPaint)
        canvas.restore()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // First, let the external touch listener handle the event
        if (onTouchListener?.invoke(this, event) == true) {
            return true
        }
        
        val attacher = attacher ?: return false
        val displayRect = attacher.displayRect ?: return false
        
        val screenX = event.x
        val screenY = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = screenX
                touchDownY = screenY
                isDragging = false
                
                // Check if touch is on compass line end (tip)
                val compassX = displayRect.left + 60f * resources.displayMetrics.density
                val compassY = displayRect.top + displayRect.height() / 3
                val compassSize = 40f * resources.displayMetrics.density
                val arrowAngle = compassRotation * kotlin.math.PI.toFloat() / 180f
                val arrowLength = compassSize * 1.6f
                
                // Calculate line end position (tip)
                val arrowTipX = compassX + arrowLength * sin(arrowAngle)
                val arrowTipY = compassY - arrowLength * cos(arrowAngle)
                
                // Check if touch is near line end
                val touchRadius = 30f * resources.displayMetrics.density
                val distanceToTip = sqrt((screenX - arrowTipX).pow(2) + (screenY - arrowTipY).pow(2))
                if (distanceToTip <= touchRadius) {
                    isRotatingCompass = true
                    compassTouchAngle = atan2(screenY - compassY, screenX - compassX)
                    compassStartRotation = compassRotation
                    return true // Consume event
                }
                
                // Check if touch is on crosses
                val touchedCross = findTouchedCross(screenX, screenY, displayRect)
                if (touchedCross != null) {
                    onCrossTouchListener?.invoke(touchedCross)
                    return true // Consume event
                }
                
                // Don't handle map touch yet - wait to see if it's a tap or drag
                return false // Pass through to PhotoView
            }
            MotionEvent.ACTION_MOVE -> {
                // Check if this is a drag gesture (significant movement)
                val dx = screenX - touchDownX
                val dy = screenY - touchDownY
                val dragThreshold = 10f * resources.displayMetrics.density
                
                if (sqrt(dx * dx + dy * dy) > dragThreshold) {
                    isDragging = true
                }
                
                if (isRotatingCompass) {
                    val compassX = displayRect.left + 60f * resources.displayMetrics.density
                    val compassY = displayRect.top + displayRect.height() / 3
                    val currentAngle = atan2(screenY - compassY, screenX - compassX)
                    val angleDelta = (currentAngle - compassTouchAngle) * 180f / kotlin.math.PI.toFloat()
                    compassRotation = (compassStartRotation + angleDelta) % 360
                    onCompassRotationListener?.invoke(compassRotation)
                    invalidate()
                    return true // Consume event
                }
                
                // If dragging, let PhotoView handle it
                if (isDragging) {
                    return false // Pass through to PhotoView
                }
                
                return false // Pass through to PhotoView
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Only handle as tap if there was no significant dragging
                if (!isDragging && !isRotatingCompass) {
                    // This was a tap - handle map touch
                    onMapTouchListener?.invoke(screenX, screenY)
                }
                
                isRotatingCompass = false
                isDragging = false
                return true // Consume event
            }
            else -> false
        }
        return false
    }
    
    private fun findTouchedCross(screenX: Float, screenY: Float, displayRect: android.graphics.RectF): String? {
        val bitmap = bitmap ?: return null
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight
        val crossSize = 30f * resources.displayMetrics.density
        val touchRadius = crossSize + 20f * resources.displayMetrics.density
        
        // Check start point
        if (startPoint != null) {
            val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
            val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
            val distance = sqrt((screenX - startX).pow(2) + (screenY - startY).pow(2))
            if (distance <= touchRadius) {
                return "start"
            }
        }
        
        // Check end point
        if (endPoint != null) {
            val endX = displayRect.left + endPoint!!.first * imageWidth * scaleX
            val endY = displayRect.top + endPoint!!.second * imageHeight * scaleY
            val distance = sqrt((screenX - endX).pow(2) + (screenY - endY).pow(2))
            if (distance <= touchRadius) {
                return "end"
            }
        }
        
        return null
    }
}
