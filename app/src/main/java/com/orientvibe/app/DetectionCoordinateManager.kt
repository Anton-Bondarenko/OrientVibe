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
    
    fun transformCoordinates(rotation: Float, centerX: Float, centerY: Float) {
        val radians = rotation * kotlin.math.PI / 180f
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        
        val detections = navigationController.currentDetections
        for ((i, detection) in detections.withIndex()) {
            val box = detection.boundingBox
            
            // Transform each corner of the bounding box around center
            val left = box.left
            val top = box.top
            val right = box.right
            val bottom = box.bottom
            
            // Apply rotation around center (no offset needed for PhotoView rotation)
            val newLeft = (left - centerX) * cos - (top - centerY) * sin + centerX
            val newTop = (left - centerX) * sin + (top - centerY) * cos + centerY
            val newRight = (right - centerX) * cos - (bottom - centerY) * sin + centerX
            val newBottom = (right - centerX) * sin + (bottom - centerY) * cos + centerY
            
            // Normalize bounding box after rotation (ensure left < right and top < bottom)
            val normalizedLeft = kotlin.math.min(newLeft, newRight)
            val normalizedRight = kotlin.math.max(newLeft, newRight)
            val normalizedTop = kotlin.math.min(newTop, newBottom)
            val normalizedBottom = kotlin.math.max(newTop, newBottom)
            
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
