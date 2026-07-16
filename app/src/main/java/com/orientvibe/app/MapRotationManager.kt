package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.Matrix

class MapRotationManager {
    private var originalBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    
    fun setOriginalBitmap(bitmap: Bitmap) {
        originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
    
    fun getOriginalBitmap(): Bitmap? = originalBitmap
    
    fun getRotatedBitmap(): Bitmap? = rotatedBitmap
    
    fun createRotatedBitmap(rotation: Float): Bitmap? {
        val original = originalBitmap ?: return null
        
        val matrix = Matrix()
        matrix.postRotate(rotation, original.width / 2f, original.height / 2f)
        
        rotatedBitmap = Bitmap.createBitmap(
            original,
            0, 0,
            original.width,
            original.height,
            matrix,
            true
        )
        
        return rotatedBitmap
    }
    
    fun restoreOriginalBitmap(): Bitmap? {
        rotatedBitmap = null
        return originalBitmap
    }
    
    fun clear() {
        originalBitmap?.recycle()
        rotatedBitmap?.recycle()
        originalBitmap = null
        rotatedBitmap = null
    }
}
