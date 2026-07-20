package com.orientvibe.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.orientvibe.app.DetectionResult
import kotlin.math.min

class ControlPointRenderer {
    private var detections: List<DetectionResult> = emptyList()
    private var originalDetections: List<DetectionResult> = emptyList()
    private var startControlPointIndex: Int? = null
    private var endControlPointIndex: Int? = null
    private var scaleFactor: Float = 1f

    fun setDetections(detections: List<DetectionResult>) {
        this.detections = detections
    }

    fun setSelectedControlPoints(startIndex: Int?, endIndex: Int?) {
        this.startControlPointIndex = startIndex
        this.endControlPointIndex = endIndex
    }

    fun setScaleFactor(factor: Float) {
        this.scaleFactor = factor
    }

    fun saveOriginalDetections() {
        originalDetections = detections.map { detection ->
            DetectionResult(
                RectF(detection.boundingBox),
                detection.confidence,
                detection.classId
            )
        }
    }

    fun transformCoordinates(
        rotation: Float,
        centerX: Float,
        centerY: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        scaleFactor: Float = 1f
    ) {
        detections = detections.mapIndexed { index, detection ->
            val transformedBox = CoordinateTransformer.transformBoundingBox(
                detection.boundingBox,
                rotation,
                centerX,
                centerY,
                offsetX,
                offsetY,
                scaleFactor
            )
            DetectionResult(transformedBox, detection.confidence, detection.classId)
        }
    }

    fun restoreOriginalCoordinates() {
        detections = originalDetections.map { detection ->
            DetectionResult(
                RectF(detection.boundingBox),
                detection.confidence,
                detection.classId
            )
        }
    }

    fun clearOriginalDetections() {
        originalDetections = emptyList()
    }

    fun getDetections(): List<DetectionResult> = detections

    fun draw(
        canvas: Canvas,
        displayRect: RectF,
        scaleX: Float,
        scaleY: Float
    ) {
        for ((index, detection) in detections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2
                val radius = min(box.right - box.left, box.bottom - box.top) / 2 * 0.8f

                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * scaleFactor
                    color = Color.RED
                }

                // Check if this is a selected control point
                val isSelected = index == startControlPointIndex || index == endControlPointIndex
                if (isSelected) {
                    paint.strokeWidth = 8f * scaleFactor
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
}
