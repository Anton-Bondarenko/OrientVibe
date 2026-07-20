package com.orientvibe.app

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.github.chrisbanes.photoview.PhotoViewAttacher
import com.orientvibe.app.overlay.CompassManager
import com.orientvibe.app.overlay.ControlPointRenderer
import com.orientvibe.app.overlay.RouteRenderer
import kotlin.math.pow
import kotlin.math.sqrt

class OverlayView(context: Context) : View(context) {

    private var bitmap: android.graphics.Bitmap? = null
    private var gpsTrackController: GpsTrackController? = null

    private var attacher: PhotoViewAttacher? = null
    private var onCrossTouchListener: ((String) -> Unit)? = null
    private var onMapTouchListener: ((Float, Float) -> Unit)? = null
    private var onTouchListener: ((android.view.View, android.view.MotionEvent) -> Boolean)? = null

    private val controlPointRenderer = ControlPointRenderer()
    private val routeRenderer = RouteRenderer(context)
    private var compassManager: CompassManager? = null
    private var isCoordinatesTransformed: Boolean = false

    fun setCompassManager(manager: CompassManager) {
        compassManager = manager
    }

    fun setScaleFactor(factor: Float) {
        controlPointRenderer.setScaleFactor(factor)
        routeRenderer.setScaleFactor(factor)
        invalidate()
    }

    fun setBitmap(bitmap: android.graphics.Bitmap) {
        this.bitmap = bitmap
        invalidate()
    }

    fun setDetections(detections: List<DetectionResult>) {
        // Only update detections if coordinates are not currently transformed
        if (!isCoordinatesTransformed) {
            controlPointRenderer.setDetections(detections)
        }
        invalidate()
    }

    fun setNavigationPoints(start: Pair<Float, Float>?, end: Pair<Float, Float>?) {
        // Only update navigation points if coordinates are not currently transformed
        if (!isCoordinatesTransformed) {
            routeRenderer.setNavigationPoints(start, end)
        }
        invalidate()
    }

    fun setSelectedControlPoints(startIndex: Int?, endIndex: Int?) {
        controlPointRenderer.setSelectedControlPoints(startIndex, endIndex)
        invalidate()
    }

    fun setCompassRotation(rotation: Float) {
        compassManager?.setRotation(rotation)
        invalidate()
    }

    fun setMapRotation(rotation: Float) {
        // Map rotation is handled by NavigationModeManager
        invalidate()
    }

    fun setAzimuth(azimuth: Float?) {
        // Azimuth is handled by RouteRenderer if needed
        invalidate()
    }

    fun setGpsTrackController(controller: GpsTrackController?) {
        this.gpsTrackController = controller
        invalidate()
    }

    fun setAttacher(attacher: PhotoViewAttacher) {
        this.attacher = attacher
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

    fun saveOriginalOverlayCoordinates() {
        controlPointRenderer.saveOriginalDetections()
        routeRenderer.saveOriginalPoints()
    }

    fun transformOverlayCoordinates(
        rotation: Float,
        centerX: Float,
        centerY: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        controlPointRenderer.transformCoordinates(rotation, centerX, centerY, offsetX, offsetY)
        val bitmap = bitmap ?: return
        routeRenderer.transformCoordinates(
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            rotation,
            offsetX,
            offsetY
        )
        isCoordinatesTransformed = true
    }

    fun restoreOriginalOverlayCoordinates() {
        controlPointRenderer.restoreOriginalCoordinates()
        routeRenderer.restoreOriginalPoints()
        isCoordinatesTransformed = false
    }

    fun clearOriginalOverlayCoordinates() {
        controlPointRenderer.clearOriginalDetections()
        routeRenderer.clearOriginalPoints()
        isCoordinatesTransformed = false
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

        // Draw control points
        controlPointRenderer.draw(canvas, displayRect, scaleX, scaleY)

        // Draw route (line and crosses)
        routeRenderer.draw(canvas, displayRect, imageWidth, imageHeight, scaleX, scaleY)

        // Draw GPS track
        drawGpsTrack(canvas, displayRect, imageWidth, imageHeight, scaleX, scaleY)

        // Draw compass
        compassManager?.drawCompass(canvas, height)
    }


    private fun drawGpsTrack(
        canvas: Canvas,
        displayRect: android.graphics.RectF,
        imageWidth: Float,
        imageHeight: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        val controller = gpsTrackController ?: return
        val trackPoints = controller.getTrackPoints()
        if (trackPoints.isEmpty()) return

        val trackPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            color = android.graphics.Color.BLUE
            isAntiAlias = true
        }

        val path = android.graphics.Path()
        var firstPoint = true

        for (gpsPoint in trackPoints) {
            val imageCoords = controller.gpsToImageCoordinates(gpsPoint, imageWidth, imageHeight)
            if (imageCoords != null) {
                // Convert to screen coordinates
                val screenX = displayRect.left + imageCoords.first * scaleX
                val screenY = displayRect.top + imageCoords.second * scaleY

                if (firstPoint) {
                    path.moveTo(screenX, screenY)
                    firstPoint = false
                } else {
                    path.lineTo(screenX, screenY)
                }
            }
        }

        canvas.drawPath(path, trackPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // First, let the external touch listener handle the event
        if (onTouchListener?.invoke(this, event) == true) {
            return true
        }

        val attacher = attacher ?: return false
        val displayRect = attacher.displayRect ?: return false

        // Let compass manager handle compass touch events
        if (compassManager?.handleTouchEvent(event, height) == true) {
            invalidate()
            return true
        }

        val screenX = event.x
        val screenY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if touch is on crosses
                val touchedCross = findTouchedCross(screenX, screenY, displayRect)
                if (touchedCross != null) {
                    onCrossTouchListener?.invoke(touchedCross)
                    return true
                }

                // Don't handle map touch yet - wait to see if it's a tap or drag
                return false // Pass through to PhotoView
            }

            MotionEvent.ACTION_MOVE -> {
                // Let PhotoView handle dragging
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Only handle as tap if compass is not rotating
                if (!compassManager?.isCompassRotating()!!) {
                    // This was a tap - handle map touch
                    onMapTouchListener?.invoke(screenX, screenY)
                }

                return true
            }

            else -> false
        }
        return false
    }

    private fun findTouchedCross(
        screenX: Float,
        screenY: Float,
        displayRect: android.graphics.RectF
    ): String? {
        val bitmap = bitmap ?: return null
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight
        val crossSize = 30f * resources.displayMetrics.density
        val touchRadius = crossSize + 20f * resources.displayMetrics.density

        // Get route points from route renderer
        val startPoint = routeRenderer.getStartPoint()
        val endPoint = routeRenderer.getEndPoint()

        // Check start point
        if (startPoint != null) {
            val startX = displayRect.left + startPoint.first * imageWidth * scaleX
            val startY = displayRect.top + startPoint.second * imageHeight * scaleY
            val distance = sqrt((screenX - startX).pow(2) + (screenY - startY).pow(2))
            if (distance <= touchRadius) {
                return "start"
            }
        }

        // Check end point
        if (endPoint != null) {
            val endX = displayRect.left + endPoint.first * imageWidth * scaleX
            val endY = displayRect.top + endPoint.second * imageHeight * scaleY
            val distance = sqrt((screenX - endX).pow(2) + (screenY - endY).pow(2))
            if (distance <= touchRadius) {
                return "end"
            }
        }

        return null
    }
}
