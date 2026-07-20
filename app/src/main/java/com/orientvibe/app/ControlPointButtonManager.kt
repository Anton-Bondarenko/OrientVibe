package com.orientvibe.app

import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.orientvibe.app.overlay.CompassManager

class ControlPointButtonManager(
    private val progressCard: android.view.View,
    private val progressBar: ProgressBar,
    private val progressText: TextView,
    private val mapCard: FrameLayout,
    private val navigationController: NavigationController,
    private val compassManager: CompassManager
) {
    private var overlayView: OverlayView? = null
    
    fun createControlPointButtons(
        detections: List<DetectionResult>,
        imageWidth: Int,
        imageHeight: Int,
        bitmap: Bitmap
    ) {
        // Store original bitmap for overlay drawing
        val originalBitmap = bitmap

        // Setup overlay for drawing
        setupOverlay()

        // Update overlay with new data
        overlayView?.setBitmap(originalBitmap)
        overlayView?.setDetections(detections)
        overlayView?.setNavigationPoints(
            navigationController.startPoint,
            navigationController.endPoint
        )
        overlayView?.setSelectedControlPoints(
            navigationController.startControlPointIndex,
            navigationController.endControlPointIndex
        )
        // Only update compass rotation if not manually rotated
        if (!compassManager.isManuallyRotated()) {
            overlayView?.setCompassRotation(compassManager.getCurrentRotation())
        }
    }
    
    fun updateOverlay(bitmap: Bitmap) {
        overlayView?.setBitmap(bitmap)
        overlayView?.setDetections(navigationController.currentDetections)
        overlayView?.setNavigationPoints(
            navigationController.startPoint,
            navigationController.endPoint
        )
        overlayView?.setSelectedControlPoints(
            navigationController.startControlPointIndex,
            navigationController.endControlPointIndex
        )
        // Only update compass rotation if not manually rotated
        if (!compassManager.isManuallyRotated()) {
            overlayView?.setCompassRotation(compassManager.getCurrentRotation())
        }
        overlayView?.invalidate()
    }
    
    fun showProgress(message: String) {
        progressCard.visibility = android.view.View.VISIBLE
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.isIndeterminate = true
        progressText.visibility = android.view.View.VISIBLE
        progressText.text = message
    }
    
    fun updateProgress(current: Int, total: Int, message: String) {
        progressBar.isIndeterminate = false
        progressBar.max = total
        progressBar.progress = current
        progressText.text = "$message ($current/$total)"
    }
    
    fun hideProgress() {
        progressCard.visibility = android.view.View.GONE
    }
    
    fun setOverlayView(overlay: OverlayView?) {
        overlayView = overlay
    }
    
    fun getOverlayView(): OverlayView? = overlayView
    
    private fun setupOverlay() {
        // Create and add overlay view programmatically
        val frameLayout = mapCard.getChildAt(0) as FrameLayout

        // Remove existing overlay if any
        if (frameLayout.childCount > 1) {
            frameLayout.removeViewAt(1)
        }

        overlayView = OverlayView(mapCard.context)
        overlayView?.id = android.view.View.generateViewId()
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Allow overlay to receive touch events for cross dragging
        overlayView?.isClickable = true
        frameLayout.addView(overlayView, layoutParams)
    }
}
