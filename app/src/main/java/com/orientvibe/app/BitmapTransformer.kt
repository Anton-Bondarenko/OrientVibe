package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.Matrix

class BitmapTransformer {
    private var originalBitmap: Bitmap? = null
    private var transformedBitmap: Bitmap? = null
    private var rotationAngle: Float = 0f

    /**
     * Store the original bitmap
     */
    fun setOriginalBitmap(bitmap: Bitmap) {
        originalBitmap = bitmap
        transformedBitmap = null // Reset transformed bitmap when original changes
        rotationAngle = 0f
    }

    /**
     * Get the original bitmap
     */
    fun getOriginalBitmap(): Bitmap? {
        return originalBitmap
    }

    /**
     * Get the transformed bitmap (if exists) or original
     */
    fun getCurrentBitmap(): Bitmap? {
        return transformedBitmap ?: originalBitmap
    }

    /**
     * Get the current rotation angle
     */
    fun getRotationAngle(): Float {
        return rotationAngle
    }

    /**
     * Calculate the center offset after rotation
     * When bitmap is rotated and aligned to bottom edge, the center shifts
     * @return Pair of (offsetX, offsetY) in pixels
     */
    fun getCenterOffset(): Pair<Float, Float> {
        val original = originalBitmap ?: return 0f to 0f
        val transformed = transformedBitmap ?: return 0f to 0f
        if (rotationAngle == 0f) return 0f to 0f

        val width = original.width.toFloat()
        val height = original.height.toFloat()

        val newWidth = transformed.width.toFloat()
        val newHeight = transformed.height.toFloat()

        // When bitmap is aligned to bottom edge, calculate center offset
        // The bottom corner of the rotated bounding box touches the bottom edge
        // This creates a shift in the center position
        val offsetX = (newWidth - width) / 2f
        val offsetY = (newHeight - height) / 2f

        android.util.Log.d(
            "BitmapTransformer",
            "Center offset: width=$width, height=$height, newWidth=$newWidth, newHeight=$newHeight, offsetX=$offsetX, offsetY=$offsetY"
        )

        return offsetX to offsetY
    }

    /**
     * Calculate the scale factor based on area change after rotation
     * @return Scale factor to compensate for image area change
     */
    fun getScaleFactor(): Float {
        val original = originalBitmap ?: return 1f
        val transformed = transformedBitmap ?: return 1f
        if (rotationAngle == 0f) return 1f

        val originalArea = original.width.toFloat() * original.height.toFloat()
        val transformedArea = transformed.width.toFloat() * transformed.height.toFloat()

        // Scale factor = sqrt(originalArea / transformedArea) to compensate for area change
        val scaleFactor = kotlin.math.sqrt(originalArea / transformedArea).toFloat()

        android.util.Log.d(
            "BitmapTransformer",
            "Scale factor: originalArea=$originalArea, transformedArea=$transformedArea, scaleFactor=$scaleFactor"
        )

        return scaleFactor
    }

    /**
     * Rotate the original bitmap by the specified angle and return the result
     * @param angle Rotation angle in degrees
     * @return Rotated bitmap
     */
    fun rotateBitmap(angle: Float): Bitmap? {
        val original = originalBitmap ?: return null

        val matrix = Matrix()
        matrix.postRotate(angle)

        val rotatedBitmap = Bitmap.createBitmap(
            original,
            0, 0,
            original.width, original.height,
            matrix,
            true
        )

        // Recycle previous transformed bitmap if exists
        transformedBitmap?.recycle()
        transformedBitmap = rotatedBitmap
        rotationAngle = angle

        return rotatedBitmap
    }

    /**
     * Reset to original bitmap (discard transformations)
     */
    fun resetToOriginal() {
        transformedBitmap?.recycle()
        transformedBitmap = null
        rotationAngle = 0f
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        originalBitmap?.recycle()
        transformedBitmap?.recycle()
        originalBitmap = null
        transformedBitmap = null
        rotationAngle = 0f
    }
}
