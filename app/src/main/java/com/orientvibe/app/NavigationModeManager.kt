package com.orientvibe.app

import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.abs

class NavigationModeManager(
    private val compassManager: CompassManager,
    private val detectionCoordinateManager: DetectionCoordinateManager,
    private val navigationController: NavigationController,
    private val gpsTrackController: GpsTrackController,
    private val bitmapTransformer: BitmapTransformer,
    private val onNavigationModeEntered: () -> Unit
) {
    var rotationAngle: Float = 0f
        private set
    private var originalStartPoint: Pair<Float, Float>? = null
    private var originalEndPoint: Pair<Float, Float>? = null
    private var originalCompassRotation: Float = 0f
    private var wasManuallyRotated: Boolean = false
    private var overlayView: OverlayView? = null
    
    fun setOverlayView(overlay: OverlayView?) {
        this.overlayView = overlay
    }
    
    fun enterNavigationMode(mapImageView: PhotoView, imageWidth: Float, imageHeight: Float) {
        android.util.Log.d("NavigationModeManager", "Entering navigation mode")
        
        // Block route point modifications
        navigationController.setRoutePointModificationBlocked(true)
        
        // Save original route points
        originalStartPoint = navigationController.startPoint
        originalEndPoint = navigationController.endPoint
        
        // Save original compass rotation
        originalCompassRotation = compassManager.getCurrentRotation()
        wasManuallyRotated = compassManager.isManuallyRotated()
        
        // Clear manual rotation flag to allow automatic rotation in navigation mode
        compassManager.clearManualRotation()
        
        // Calculate route angle (angle from north to route, clockwise)
        val routeAngle = navigationController.calculateRouteAngle()
        
        if (routeAngle != null) {
            // Calculate rotation angle: 360 - route angle
            rotationAngle = -routeAngle
        } else {
            android.util.Log.e("NavigationModeManager", "Failed to calculate route angle, using default 0 degrees")
            rotationAngle = 0f
        }
        
        // Rotate bitmap using BitmapTransformer
        val rotatedBitmap = bitmapTransformer.rotateBitmap(rotationAngle)
        if (rotatedBitmap != null) {
            mapImageView.setImageBitmap(rotatedBitmap)
            android.util.Log.d("NavigationModeManager", "Bitmap rotated to $rotationAngle degrees")
        }
        
        // Rotate compass to match map rotation + current compass angle
        val compassRotation = rotationAngle + originalCompassRotation
        compassManager.setRotation(compassRotation)
        overlayView?.setCompassRotation(compassRotation)
//        gpsTrackController.setCompassRotation(compassRotation)
        
        // Transform detection coordinates to match rotation with center offset
        val (offsetX, offsetY) = bitmapTransformer.getCenterOffset()
        android.util.Log.d("NavigationModeManager", "Center offset: $offsetX, $offsetY")
        
        detectionCoordinateManager.transformCoordinates(
            rotationAngle,
            imageWidth / 2f,
            imageHeight / 2f,
            1f, // No scale adjustment needed when rotating bitmap directly
            offsetX,
            offsetY
        )
        
        // Transform route points to match rotation with center offset
        transformRoutePoints(imageWidth, imageHeight, 1f, offsetX, offsetY)
        
        onNavigationModeEntered()
        android.util.Log.d("NavigationModeManager", "Navigation mode entered")
    }
    
    fun exitNavigationMode(mapImageView: PhotoView) {
        android.util.Log.d("NavigationModeManager", "Exiting navigation mode")
        
        // Unblock route point modifications
        navigationController.setRoutePointModificationBlocked(false)
        
        // Reset to original bitmap
        bitmapTransformer.resetToOriginal()
        val originalBitmap = bitmapTransformer.getCurrentBitmap()
        if (originalBitmap != null) {
            mapImageView.setImageBitmap(originalBitmap)
            android.util.Log.d("NavigationModeManager", "Restored original bitmap")
        }
        
        // Restore original compass rotation
        compassManager.setRotation(originalCompassRotation)
        overlayView?.setCompassRotation(originalCompassRotation)
        gpsTrackController.setCompassRotation(originalCompassRotation)
        
        // Restore manual rotation flag if it was set before navigation mode
        if (wasManuallyRotated) {
            compassManager.setManualRotation(originalCompassRotation)
        }
        
        // Restore original detection coordinates
        detectionCoordinateManager.restoreOriginalCoordinates()
        
        // Restore original route points
        if (originalStartPoint != null && originalEndPoint != null) {
            navigationController.setStartPoint(originalStartPoint!!.first, originalStartPoint!!.second)
            navigationController.setEndPoint(originalEndPoint!!.first, originalEndPoint!!.second)
            originalStartPoint = null
            originalEndPoint = null
        }
        
        // Reset rotation angle
        rotationAngle = 0f
        
        android.util.Log.d("NavigationModeManager", "Navigation mode exited")
    }
    
    private fun transformRoutePoints(imageWidth: Float, imageHeight: Float, scaleAdjustment: Float, offsetX: Float, offsetY: Float) {
        val startPoint = originalStartPoint
        val endPoint = originalEndPoint
        
        android.util.Log.d("NavigationModeManager", "transformRoutePoints - rotationAngle: $rotationAngle, offset: ($offsetX, $offsetY)")
        
        if (startPoint != null && endPoint != null) {
            android.util.Log.d("NavigationModeManager", "Original start point: ${startPoint.first}, ${startPoint.second}")
            android.util.Log.d("NavigationModeManager", "Original end point: ${endPoint.first}, ${endPoint.second}")
            
            val centerX = imageWidth / 2.0
            val centerY = imageHeight / 2.0
            android.util.Log.d("NavigationModeManager", "Center: $centerX, $centerY")
            
            val radians = rotationAngle * kotlin.math.PI / 180.0
            val cos = kotlin.math.cos(radians)
            val sin = kotlin.math.sin(radians)
            android.util.Log.d("NavigationModeManager", "Rotation - radians: $radians, cos: $cos, sin: $sin")
            
            // Transform start point (from normalized to pixel, rotate, back to normalized)
            val startPixelX = startPoint.first * imageWidth
            val startPixelY = startPoint.second * imageHeight
            android.util.Log.d("NavigationModeManager", "Start pixel coords: $startPixelX, $startPixelY")
            
            // Apply rotation around center
            val newStartPixelX = (startPixelX - centerX) * cos - (startPixelY - centerY) * sin + centerX
            val newStartPixelY = (startPixelX - centerX) * sin + (startPixelY - centerY) * cos + centerY
            android.util.Log.d("NavigationModeManager", "Rotated start pixel coords: $newStartPixelX, $newStartPixelY")
            
            // Apply center offset for bottom-edge alignment
            val offsetStartPixelX = newStartPixelX + offsetX
            val offsetStartPixelY = newStartPixelY + offsetY
            android.util.Log.d("NavigationModeManager", "Offset start pixel coords: $offsetStartPixelX, $offsetStartPixelY")
            
            val newStartX = (offsetStartPixelX / imageWidth).toFloat()
            val newStartY = (offsetStartPixelY / imageHeight).toFloat()
            android.util.Log.d("NavigationModeManager", "New start normalized coords: $newStartX, $newStartY")
            
            // Transform end point (from normalized to pixel, rotate, back to normalized)
            val endPixelX = endPoint.first * imageWidth
            val endPixelY = endPoint.second * imageHeight
            android.util.Log.d("NavigationModeManager", "End pixel coords: $endPixelX, $endPixelY")
            
            // Apply rotation around center
            val newEndPixelX = (endPixelX - centerX) * cos - (endPixelY - centerY) * sin + centerX
            val newEndPixelY = (endPixelX - centerX) * sin + (endPixelY - centerY) * cos + centerY
            android.util.Log.d("NavigationModeManager", "Rotated end pixel coords: $newEndPixelX, $newEndPixelY")
            
            // Apply center offset
            val offsetEndPixelX = newEndPixelX + offsetX
            val offsetEndPixelY = newEndPixelY + offsetY
            android.util.Log.d("NavigationModeManager", "Offset end pixel coords: $offsetEndPixelX, $offsetEndPixelY")
            
            val newEndX = (offsetEndPixelX / imageWidth).toFloat()
            val newEndY = (offsetEndPixelY / imageHeight).toFloat()
            android.util.Log.d("NavigationModeManager", "New end normalized coords: $newEndX, $newEndY")
            
            // Update navigation controller with transformed points
            navigationController.setStartPoint(newStartX, newStartY)
            navigationController.setEndPoint(newEndX, newEndY)
        }
    }
}
