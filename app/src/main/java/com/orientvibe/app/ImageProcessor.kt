package com.orientvibe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageProcessor(
    private val context: Context,
    private val objectDetector: OnnxObjectDetector,
    private val navigationController: NavigationController,
    private val mapRotationManager: MapRotationManager,
    private val panelStateManager: PanelStateManager,
    private val controlPointButtonManager: ControlPointButtonManager,
    private val onProcessingComplete: (List<DetectionResult>, Bitmap) -> Unit
) {
    private var modelLoaded = false
    
    fun setModelLoaded(loaded: Boolean) {
        modelLoaded = loaded
    }
    
    fun processImage(uri: Uri) {
        if (!modelLoaded) {
            Toast.makeText(context, "Модель не загружена", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Load bitmap from URI
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Save original bitmap for rotation
                mapRotationManager.setOriginalBitmap(bitmap)
                
                // Reset navigation state
                navigationController.reset()
                
                // Detect control points in background
                detectControlPoints(bitmap)
            } else {
                Toast.makeText(context, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun detectControlPoints(bitmap: Bitmap) {
        // Use lifecycle scope from context (assuming context is Activity)
        if (context is androidx.lifecycle.LifecycleOwner) {
            context.lifecycleScope.launch {
                try {
                    // Show progress bar on main thread first
                    withContext(Dispatchers.Main) {
                        controlPointButtonManager.showProgress("Подготовка...")
                    }

                    // Set progress listener
                    objectDetector.setProgressListener(object : DetectionProgressListener {
                        override fun onProgressUpdate(
                            current: Int,
                            total: Int,
                            message: String
                        ) {
                            if (context is androidx.lifecycle.LifecycleOwner) {
                                context.lifecycleScope.launch(Dispatchers.Main) {
                                    controlPointButtonManager.updateProgress(current, total, message)
                                }
                            }
                        }
                    })

                    // Run detection on background thread
                    val detections = withContext(Dispatchers.Default) {
                        objectDetector.detect(bitmap)
                    }

                    // Store detections
                    navigationController.setDetections(detections)

                    // Notify completion
                    onProcessingComplete(detections, bitmap)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        controlPointButtonManager.hideProgress()
                        Toast.makeText(
                            context,
                            "Ошибка: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
