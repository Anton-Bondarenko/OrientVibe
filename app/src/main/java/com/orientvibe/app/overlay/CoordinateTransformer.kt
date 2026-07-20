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
     * Only transforms the center point, keeping sides parallel to axes
     * @param box Bounding box to transform
     * @param rotation Rotation angle in degrees
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param offsetX Offset X after rotation
     * @param offsetY Offset Y after rotation
     * @param scaleFactor Scale factor to apply to width and height
     * @return Transformed bounding box (normalized)
     */
    fun transformBoundingBox(
        box: RectF,
        rotation: Float,
        centerX: Float,
        centerY: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        scaleFactor: Float = 1f
    ): RectF {
        // Calculate center of bounding box
        val boxCenterX = (box.left + box.right) / 2f
        val boxCenterY = (box.top + box.bottom) / 2f

        // Transform only the center point
        val (transformedCenterX, transformedCenterY) = transformPoint(
            boxCenterX,
            boxCenterY,
            rotation,
            centerX,
            centerY,
            offsetX,
            offsetY
        )

        // Scale width and height by scaleFactor
        val width = (box.right - box.left) * scaleFactor
        val height = (box.bottom - box.top) * scaleFactor

        // Create new bounding box with transformed center and scaled size
        return RectF(
            transformedCenterX - width / 2f,
            transformedCenterY - height / 2f,
            transformedCenterX + width / 2f,
            transformedCenterY + height / 2f
        )
    }
}
