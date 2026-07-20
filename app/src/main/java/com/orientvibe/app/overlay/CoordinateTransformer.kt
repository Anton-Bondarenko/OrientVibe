package com.orientvibe.app.overlay

import android.graphics.RectF

object CoordinateTransformer {
    
    /**
     * Transform a single point around a center with rotation and offset
     * @param x X coordinate
     * @param y Y coordinate
     * @param rotation Rotation angle in degrees
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param offsetX Offset X after rotation
     * @param offsetY Offset Y after rotation
     * @return Pair of transformed (x, y) coordinates
     */
    fun transformPoint(
        x: Float,
        y: Float,
        rotation: Float,
        centerX: Float,
        centerY: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ): Pair<Float, Float> {
        val radians = rotation * kotlin.math.PI / 180f
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        
        // Apply rotation around center
        val rotatedX = (x - centerX) * cos - (y - centerY) * sin + centerX
        val rotatedY = (x - centerX) * sin + (y - centerY) * cos + centerY
        
        // Apply center offset
        val transformedX = rotatedX + offsetX
        val transformedY = rotatedY + offsetY
        
        return transformedX to transformedY
    }
    
    /**
     * Transform a bounding box around a center with rotation and offset
     * @param box Bounding box to transform
     * @param rotation Rotation angle in degrees
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param offsetX Offset X after rotation
     * @param offsetY Offset Y after rotation
     * @return Transformed bounding box (normalized)
     */
    fun transformBoundingBox(
        box: RectF,
        rotation: Float,
        centerX: Float,
        centerY: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ): RectF {
        // Transform each corner of the bounding box
        val (newLeft, newTop) = transformPoint(box.left, box.top, rotation, centerX, centerY, offsetX, offsetY)
        val (newRight, newBottom) = transformPoint(box.right, box.bottom, rotation, centerX, centerY, offsetX, offsetY)
        
        // Normalize bounding box (ensure left < right and top < bottom)
        val normalizedLeft = kotlin.math.min(newLeft, newRight)
        val normalizedRight = kotlin.math.max(newLeft, newRight)
        val normalizedTop = kotlin.math.min(newTop, newBottom)
        val normalizedBottom = kotlin.math.max(newTop, newBottom)
        
        return RectF(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)
    }
}
