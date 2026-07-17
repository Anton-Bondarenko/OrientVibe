package com.orientvibe.app

import com.github.chrisbanes.photoview.PhotoView

class NavigationModeManager(
    private val compassManager: CompassManager,
    private val detectionCoordinateManager: DetectionCoordinateManager,
    private val navigationController: NavigationController,
    private val gpsTrackController: GpsTrackController,
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
            android.util.Log.e("NavigationModeManager", "Failed to calculate route angle, using default 90 degrees")
            rotationAngle = 0f
        }
        
        // Temporarily disable image transformations to preserve manual scale
        // Reset current scale first
        val attacher = mapImageView.attacher
//        attacher.scale = 1f
        
        // Rotate PhotoView by calculated angle
        attacher.setRotationTo(rotationAngle)
        
        // Set scale range to allow 2x
        attacher.minimumScale = 0.5f
        attacher.maximumScale = 10f
        
        // Rotate compass to match map rotation + current compass angle
        val compassRotation = rotationAngle + originalCompassRotation
        compassManager.setRotation(compassRotation)
        overlayView?.setCompassRotation(compassRotation)
//        gpsTrackController.setCompassRotation(compassRotation)
        
        // Transform detection coordinates to match rotation
        detectionCoordinateManager.transformCoordinates(
            rotationAngle,
            imageWidth / 2f,
            imageHeight / 2f
        )
        
        // Transform route points to match rotation
        transformRoutePoints(imageWidth, imageHeight)
        
        onNavigationModeEntered()
        android.util.Log.d("NavigationModeManager", "Navigation mode entered")
    }
    
    fun exitNavigationMode(mapImageView: PhotoView) {
        android.util.Log.d("NavigationModeManager", "Exiting navigation mode")
        
        // Unblock route point modifications
        navigationController.setRoutePointModificationBlocked(false)
        
        // Reset PhotoView rotation
        val attacher = mapImageView.attacher
        attacher.setRotationTo(0f)
        
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
    
    private fun transformRoutePoints(imageWidth: Float, imageHeight: Float) {
        val startPoint = originalStartPoint
        val endPoint = originalEndPoint
        
        if (startPoint != null && endPoint != null) {
            val centerX = imageWidth / 2.0
            val centerY = imageHeight / 2.0
            
            val radians = rotationAngle * kotlin.math.PI / 180.0
            val cos = kotlin.math.cos(radians)
            val sin = kotlin.math.sin(radians)
            
            // Transform start point (from normalized to pixel, rotate, back to normalized)
            val startPixelX = startPoint.first * imageWidth
            val startPixelY = startPoint.second * imageHeight
            val newStartPixelX = (startPixelX - centerX) * cos - (startPixelY - centerY) * sin + centerX
            val newStartPixelY = (startPixelX - centerX) * sin + (startPixelY - centerY) * cos + centerY
            val newStartX = (newStartPixelX / imageWidth).toFloat()
            val newStartY = (newStartPixelY / imageHeight).toFloat()
            
            // Transform end point (from normalized to pixel, rotate, back to normalized)
            val endPixelX = endPoint.first * imageWidth
            val endPixelY = endPoint.second * imageHeight
            val newEndPixelX = (endPixelX - centerX) * cos - (endPixelY - centerY) * sin + centerX
            val newEndPixelY = (endPixelX - centerX) * sin + (endPixelY - centerY) * cos + centerY
            val newEndX = (newEndPixelX / imageWidth).toFloat()
            val newEndY = (newEndPixelY / imageHeight).toFloat()
            
            // Update navigation controller with transformed points
            navigationController.setStartPoint(newStartX, newStartY)
            navigationController.setEndPoint(newEndX, newEndY)
        }
    }
}
