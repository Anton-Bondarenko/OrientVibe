package com.orientvibe.app

import com.github.chrisbanes.photoview.PhotoView
import com.orientvibe.app.overlay.CompassManager

class NavigationModeManager(
    private val compassManager: CompassManager,
    private val navigationController: NavigationController,
    private val gpsTrackController: GpsTrackController,
    private val bitmapTransformer: BitmapTransformer,
    private val onNavigationModeEntered: () -> Unit
) {
    var rotationAngle: Float = 0f
        private set
    private var originalStartPoint: Pair<Float, Float>? = null
    private var originalEndPoint: Pair<Float, Float>? = null
    private var originalDetections: List<DetectionResult>? = null
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

        // Save original detections
        originalDetections = navigationController.currentDetections.map { detection ->
            DetectionResult(
                android.graphics.RectF(detection.boundingBox),
                detection.confidence,
                detection.classId
            )
        }

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
            android.util.Log.e(
                "NavigationModeManager",
                "Failed to calculate route angle, using default 0 degrees"
            )
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
        gpsTrackController.setCompassRotation(compassRotation)

        // Transform overlay coordinates to match rotation with center offset
        val (offsetX, offsetY) = bitmapTransformer.getCenterOffset()
        android.util.Log.d("NavigationModeManager", "Center offset: $offsetX, $offsetY")

        // Get scale factor based on width and height change
        val scaleFactor = bitmapTransformer.getScaleFactor()
        android.util.Log.d("NavigationModeManager", "Scale factor: $scaleFactor")

        // Save original overlay coordinates
        overlayView?.saveOriginalOverlayCoordinates()

        // Transform overlay coordinates
        overlayView?.transformOverlayCoordinates(
            rotationAngle,
            imageWidth / 2f,
            imageHeight / 2f,
            offsetX,
            offsetY,
            imageWidth,
            imageHeight,
            scaleFactor
        )

        // Apply scale factor to overlay elements
        overlayView?.setScaleFactor(scaleFactor)

        // Update navigationController with transformed coordinates
        updateNavigationControllerWithTransformedCoordinates()

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

        // Restore original overlay coordinates
        overlayView?.restoreOriginalOverlayCoordinates()

        // Restore original coordinates in navigationController
        if (originalDetections != null) {
            navigationController.setDetections(originalDetections!!)
        }
        if (originalStartPoint != null && originalEndPoint != null) {
            navigationController.setStartPoint(
                originalStartPoint!!.first,
                originalStartPoint!!.second
            )
            navigationController.setEndPoint(originalEndPoint!!.first, originalEndPoint!!.second)
        }

        // Reset scale factor
        overlayView?.setScaleFactor(1f)

        // Clear original overlay coordinates
        overlayView?.clearOriginalOverlayCoordinates()

        // Clear saved originals
        originalDetections = null
        originalStartPoint = null
        originalEndPoint = null

        // Reset rotation angle
        rotationAngle = 0f

        android.util.Log.d("NavigationModeManager", "Navigation mode exited")
    }

    private fun updateNavigationControllerWithTransformedCoordinates() {
        // Update detections in navigationController with transformed coordinates
        val transformedDetections = overlayView?.getTransformedDetections()
        if (transformedDetections != null) {
            navigationController.setDetections(transformedDetections)
        }

        // Update route points in navigationController with transformed coordinates
        val transformedStartPoint = overlayView?.getTransformedStartPoint()
        val transformedEndPoint = overlayView?.getTransformedEndPoint()
        if (transformedStartPoint != null && transformedEndPoint != null) {
            navigationController.setStartPoint(transformedStartPoint.first, transformedStartPoint.second)
            navigationController.setEndPoint(transformedEndPoint.first, transformedEndPoint.second)
        }
    }
}
