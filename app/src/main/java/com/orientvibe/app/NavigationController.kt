package com.orientvibe.app

import android.graphics.drawable.Drawable
import com.github.chrisbanes.photoview.PhotoViewAttacher
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class NavigationController {
    // Navigation state
    var currentDetections: List<DetectionResult> = emptyList()
        private set
    var startPoint: Pair<Float, Float>? = null
        private set
    var endPoint: Pair<Float, Float>? = null
        private set
    var selectedControlPoint: DetectionResult? = null
        private set
    var startControlPointIndex: Int? = null
        private set
    var endControlPointIndex: Int? = null
        private set
    var isSettingStart = true // true = setting start, false = setting end
        private set

    // Listeners
    private var onNavigationStateChangedListener: (() -> Unit)? = null
    private var onControlPointSelectedListener: ((DetectionResult) -> Unit)? = null

    fun setDetections(detections: List<DetectionResult>) {
        this.currentDetections = detections
    }

    fun setNavigationStateChangedListener(listener: () -> Unit) {
        this.onNavigationStateChangedListener = listener
    }

    fun setControlPointSelectedListener(listener: (DetectionResult) -> Unit) {
        this.onControlPointSelectedListener = listener
    }

    fun reset() {
        startPoint = null
        endPoint = null
        selectedControlPoint = null
        startControlPointIndex = null
        endControlPointIndex = null
        isSettingStart = true
        onNavigationStateChangedListener?.invoke()
    }

    fun handleControlPointSelection(
        controlPointIndex: Int,
        imageWidth: Float,
        imageHeight: Float
    ) {
        val controlPoint = currentDetections[controlPointIndex]
        selectedControlPoint = controlPoint

        // Get center coordinates of the control point
        val box = controlPoint.boundingBox
        val centerX = (box.left + box.right) / 2
        val centerY = (box.top + box.bottom) / 2

        if (isSettingStart) {
            startPoint = Pair(centerX / imageWidth, centerY / imageHeight)
            startControlPointIndex = controlPointIndex
            isSettingStart = false
        } else {
            endPoint = Pair(centerX / imageWidth, centerY / imageHeight)
            endControlPointIndex = controlPointIndex
        }

        onControlPointSelectedListener?.invoke(controlPoint)
        onNavigationStateChangedListener?.invoke()
    }

    fun handleMapTouch(x: Float, y: Float) {
        if (isSettingStart) {
            startPoint = Pair(x, y)
            startControlPointIndex = null
            isSettingStart = false
        } else {
            endPoint = Pair(x, y)
            endControlPointIndex = null
        }
        onNavigationStateChangedListener?.invoke()
    }

    fun findTouchedControlPoint(
        screenX: Float,
        screenY: Float,
        attacher: PhotoViewAttacher,
        drawable: Drawable,
        density: Float
    ): Int? {
        val displayRect = attacher.displayRect ?: return null

        // Get image dimensions
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        // Calculate scale factors
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight

        for ((index, detection) in currentDetections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2

                // Convert bounding box to screen coordinates using scale factors
                val screenLeft = displayRect.left + box.left * scaleX
                val screenRight = displayRect.left + box.right * scaleX
                val screenTop = displayRect.top + box.top * scaleY
                val screenBottom = displayRect.top + box.bottom * scaleY

                // Check if touch is within the bounding box (with some margin)
                val margin = 20 * density

                if (screenX >= screenLeft - margin && screenX <= screenRight + margin &&
                    screenY >= screenTop - margin && screenY <= screenBottom + margin
                ) {
                    return index
                }
            }
        }
        return null
    }

    fun findTouchedCross(
        screenX: Float,
        screenY: Float,
        attacher: PhotoViewAttacher,
        drawable: Drawable,
        density: Float
    ): String? {
        // Returns "start", "end", or null
        val displayRect = attacher.displayRect ?: return null

        // Get image dimensions
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        // Calculate scale factors
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight

        val crossSize = 30f * density
        val touchRadius = crossSize + 20f * density

        // Check start point cross
        if (startPoint != null) {
            val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
            val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
            val distance = sqrt((screenX - startX).pow(2) + (screenY - startY).pow(2))
            if (distance <= touchRadius) {
                return "start"
            }
        }

        // Check end point cross
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

    fun findNearestControlPoint(
        imageX: Float,
        imageY: Float,
        drawable: Drawable
    ): Pair<Pair<Float, Float>, Int>? {
        // Returns the center of the nearest control point and its index if within snap distance
        val snapDistance = 0.05f // 5% of image dimensions
        var nearestPoint: Pair<Pair<Float, Float>, Int>? = null
        var minDistance = Float.MAX_VALUE

        // Get image dimensions to normalize CP coordinates
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        for ((index, detection) in currentDetections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2

                // Normalize CP coordinates to 0-1 range
                val normalizedCenterX = centerX / imageWidth
                val normalizedCenterY = centerY / imageHeight

                val distance = sqrt(
                    (imageX - normalizedCenterX).pow(2) + (imageY - normalizedCenterY).pow(2)
                )

                if (distance < minDistance && distance <= snapDistance) {
                    minDistance = distance
                    nearestPoint = Pair(Pair(normalizedCenterX, normalizedCenterY), index)
                }
            }
        }

        return nearestPoint
    }

    fun updateStartPoint(point: Pair<Float, Float>, controlPointIndex: Int? = null) {
        startPoint = point
        startControlPointIndex = controlPointIndex
        onNavigationStateChangedListener?.invoke()
    }

    fun updateEndPoint(point: Pair<Float, Float>, controlPointIndex: Int? = null) {
        endPoint = point
        endControlPointIndex = controlPointIndex
        onNavigationStateChangedListener?.invoke()
    }

    fun isNavigationReady(): Boolean {
        return startPoint != null && endPoint != null
    }

    fun getStatusMessage(compassRotation: Float = 0f): String {
        return when {
            startPoint == null -> "выберите старт"
            endPoint == null -> "выберите направление"
            else -> {
                val azimuth = calculateAzimuth(compassRotation)
                if (azimuth != null) {
                    "Азимут: ${"%.1f".format(azimuth)}°"
                } else {
                    "навигация готова"
                }
            }
        }
    }

    fun calculateAzimuth(compassRotation: Float): Float? {
        if (startPoint == null || endPoint == null) return null

        val dx = endPoint!!.first - startPoint!!.first
        val dy = startPoint!!.second - endPoint!!.second // Inverted because y grows downward

        // Calculate route angle in radians (0 = north, clockwise)
        val routeAngle = atan2(dx, dy)

        // Convert compass rotation to radians (0 = north, clockwise)
        val compassAngle = compassRotation * PI / 180f

        // Calculate azimuth (angle from north to route, clockwise)
        var azimuth = (routeAngle - compassAngle) * 180f / PI

        // Normalize to 0-360 range
        while (azimuth < 0) azimuth += 360f
        while (azimuth >= 360f) azimuth -= 360f

        return azimuth
    }

    companion object {
        private const val PI = 3.141592653589793f
    }
}
