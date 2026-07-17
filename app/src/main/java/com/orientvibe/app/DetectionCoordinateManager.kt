package com.orientvibe.app

class DetectionCoordinateManager(
    private val navigationController: NavigationController
) {
    private var originalDetections: List<DetectionResult> = emptyList()
    private var overlayView: OverlayView? = null
    
    fun setOverlayView(overlay: OverlayView?) {
        overlayView = overlay
    }
    
    fun saveOriginalDetections() {
        originalDetections = navigationController.currentDetections.mapIndexed { index, detection ->
            DetectionResult(
                android.graphics.RectF(detection.boundingBox),
                detection.confidence,
                detection.classId
            )
        }
    }
    
    fun transformCoordinates(rotation: Float, centerX: Float, centerY: Float, scaleAdjustment: Float, offsetX: Float = 0f, offsetY: Float = 0f) {
        val radians = rotation * kotlin.math.PI / 180f
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        
        android.util.Log.d("DetectionCoordinateManager", "transformCoordinates - rotation: $rotation, offset: ($offsetX, $offsetY)")
        
        val detections = navigationController.currentDetections
        for ((i, detection) in detections.withIndex()) {
            val box = detection.boundingBox
            android.util.Log.d("DetectionCoordinateManager", "Detection $i original box: ${box.left}, ${box.top}, ${box.right}, ${box.bottom}")
            
            // Transform each corner of the bounding box around center
            val left = box.left
            val top = box.top
            val right = box.right
            val bottom = box.bottom
            
            // Apply rotation around center
            val newLeft = (left - centerX) * cos - (top - centerY) * sin + centerX
            val newTop = (left - centerX) * sin + (top - centerY) * cos + centerY
            val newRight = (right - centerX) * cos - (bottom - centerY) * sin + centerX
            val newBottom = (right - centerX) * sin + (bottom - centerY) * cos + centerY
            
            // Apply center offset for bottom-edge alignment
            val offsetLeft = newLeft + offsetX
            val offsetTop = newTop + offsetY
            val offsetRight = newRight + offsetX
            val offsetBottom = newBottom + offsetY
            
            // Normalize bounding box after rotation (ensure left < right and top < bottom)
            val normalizedLeft = kotlin.math.min(offsetLeft, offsetRight)
            val normalizedRight = kotlin.math.max(offsetLeft, offsetRight)
            val normalizedTop = kotlin.math.min(offsetTop, offsetBottom)
            val normalizedBottom = kotlin.math.max(offsetTop, offsetBottom)
            
            android.util.Log.d("DetectionCoordinateManager", "Detection $i rotated box: $normalizedLeft, $normalizedTop, $normalizedRight, $normalizedBottom")
            
            // Create new detection with updated bounding box
            val updatedDetection = DetectionResult(
                android.graphics.RectF(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom),
                detection.confidence,
                detection.classId
            )
            
            navigationController.updateDetection(i, updatedDetection)
        }
        
        overlayView?.invalidate()
    }
    
    fun restoreOriginalCoordinates() {
        val currentDetections = navigationController.currentDetections
        for ((i, detection) in currentDetections.withIndex()) {
            if (i < originalDetections.size) {
                val updatedDetection = DetectionResult(
                    android.graphics.RectF(originalDetections[i].boundingBox),
                    detection.confidence,
                    detection.classId
                )
                
                navigationController.updateDetection(i, updatedDetection)
            }
        }
        
        overlayView?.invalidate()
    }
    
    fun clear() {
        originalDetections = emptyList()
    }
}
