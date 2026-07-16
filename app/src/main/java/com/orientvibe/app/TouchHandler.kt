package com.orientvibe.app

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.Toast
import com.github.chrisbanes.photoview.PhotoViewAttacher

class TouchHandler(
    private val context: Context,
    private val navigationController: NavigationController,
    private val onCrossDragStart: (String) -> Unit,
    private val onCrossDragMove: (Float, Float) -> Unit,
    private val onCrossDragEnd: () -> Unit
) {
    private var isDraggingStart = false
    private var isDraggingEnd = false
    
    fun handleMapTap(
        screenX: Float,
        screenY: Float,
        attacher: PhotoViewAttacher,
        drawable: Drawable,
        density: Float
    ) {
        val displayRect = attacher.displayRect ?: return
        
        // Convert screen coordinates to image coordinates
        val imageX = (screenX - displayRect.left) / displayRect.width()
        val imageY = (screenY - displayRect.top) / displayRect.height()
        
        // Check if touch is on a control point
        val touchedControlPointIndex = navigationController.findTouchedControlPoint(
            screenX, screenY, attacher, drawable, density
        )
        
        if (touchedControlPointIndex != null) {
            // Handle control point selection
            val imageWidth = drawable.intrinsicWidth.toFloat()
            val imageHeight = drawable.intrinsicHeight.toFloat()
            navigationController.handleControlPointSelection(
                touchedControlPointIndex, imageWidth, imageHeight
            )
            if (navigationController.isNavigationReady()) {
                Toast.makeText(
                    context,
                    "Направление установлено на КП",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // Handle map touch for start point
            navigationController.handleMapTouch(imageX, imageY)
            if (navigationController.isNavigationReady()) {
                Toast.makeText(
                    context,
                    "Направление установлено: (${"%.2f".format(imageX)}, ${"%.2f".format(imageY)})",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Старт установлен: (${"%.2f".format(imageX)}, ${"%.2f".format(imageY)})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    fun handleTouchDown(
        screenX: Float,
        screenY: Float,
        attacher: PhotoViewAttacher,
        drawable: Drawable,
        density: Float
    ) {
        if (navigationController.isRoutePointModificationBlocked) {
            return
        }
        
        val touchedCross = navigationController.findTouchedCross(
            screenX,
            screenY,
            attacher,
            drawable,
            density
        )
        if (touchedCross == "start") {
            isDraggingStart = true
            onCrossDragStart("start")
        } else if (touchedCross == "end") {
            isDraggingEnd = true
            onCrossDragStart("end")
        }
    }
    
    fun handleTouchMove(screenX: Float, screenY: Float) {
        if (isDraggingStart || isDraggingEnd) {
            onCrossDragMove(screenX, screenY)
        }
    }
    
    fun handleTouchUp() {
        isDraggingStart = false
        isDraggingEnd = false
        onCrossDragEnd()
    }
    
    fun handleCrossDrag(
        screenX: Float,
        screenY: Float,
        attacher: PhotoViewAttacher,
        drawable: Drawable
    ) {
        val displayRect = attacher.displayRect ?: return
        
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight
        
        // Convert screen coordinates to image coordinates
        val imageX = (screenX - displayRect.left) / scaleX / imageWidth
        val imageY = (screenY - displayRect.top) / scaleY / imageHeight
        
        if (isDraggingStart) {
            // Snap to nearest control point if within distance
            val snappedResult = navigationController.findNearestControlPoint(imageX, imageY, drawable)
            if (snappedResult != null) {
                navigationController.updateStartPoint(snappedResult.first, snappedResult.second)
            } else {
                navigationController.updateStartPoint(
                    Pair(
                        imageX.coerceIn(0f, 1f),
                        imageY.coerceIn(0f, 1f)
                    )
                )
            }
        } else if (isDraggingEnd) {
            // Snap to nearest control point if within distance
            val snappedResult = navigationController.findNearestControlPoint(imageX, imageY, drawable)
            if (snappedResult != null) {
                navigationController.updateEndPoint(snappedResult.first, snappedResult.second)
            } else {
                navigationController.updateEndPoint(
                    Pair(
                        imageX.coerceIn(0f, 1f),
                        imageY.coerceIn(0f, 1f)
                    )
                )
            }
        }
    }
}
