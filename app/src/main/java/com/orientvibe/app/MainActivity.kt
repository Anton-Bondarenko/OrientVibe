package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.github.chrisbanes.photoview.PhotoViewAttacher
import com.orientvibe.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var galleryManager: GalleryManager
    private val objectDetector = OnnxObjectDetector(this)
    private var modelLoaded = false
    
    // Navigation state
    private var currentDetections: List<DetectionResult> = emptyList()
    private var startPoint: Pair<Float, Float>? = null
    private var selectedControlPoint: DetectionResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Hide action bar
        supportActionBar?.hide()
        
        // Hide system bars using modern API
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        // Load ONNX model
        modelLoaded = objectDetector.loadModel("yolov8n.onnx")
        if (!modelLoaded) {
            Toast.makeText(this, "Не удалось загрузить модель", Toast.LENGTH_LONG).show()
        }

        setupManagers()
        setupButtons()
        setupMapTouchHandler()
    }

    private fun setupManagers() {
        cameraManager = CameraManager(this) { uri ->
            processImage(uri)
        }
        cameraManager.setupLaunchers()

        galleryManager = GalleryManager(this) { uri ->
            processImage(uri)
        }
        galleryManager.setupLaunchers()
    }

    private fun setupButtons() {
        binding.cameraButton.setOnClickListener {
            cameraManager.requestCamera()
        }

        binding.galleryButton.setOnClickListener {
            galleryManager.requestGallery()
        }
    }
    
    private fun setupMapTouchHandler() {
        // Use PhotoView's built-in tap listener
        binding.mapImageView.setOnViewTapListener { view, x, y ->
            // Get the attacher to convert screen coordinates to image coordinates
            val attacher = binding.mapImageView.attacher
            val displayRect = attacher.displayRect
            
            if (displayRect != null) {
                // Convert screen coordinates to image coordinates
                val imageX = (x - displayRect.left) / displayRect.width()
                val imageY = (y - displayRect.top) / displayRect.height()
                
                // Check if touch is on a control point
                val touchedControlPoint = findTouchedControlPoint(x, y)
                
                if (touchedControlPoint != null) {
                    // Handle control point selection
                    handleControlPointSelection(touchedControlPoint)
                } else {
                    // Handle map touch for start point
                    handleMapTouch(imageX, imageY)
                }
            }
        }
    }
    
    private fun findTouchedControlPoint(screenX: Float, screenY: Float): DetectionResult? {
        val attacher = binding.mapImageView.attacher
        val displayRect = attacher.displayRect ?: return null
        
        for (detection in currentDetections) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2
                
                // Convert image coordinates to screen coordinates
                val screenCenterX = displayRect.left + centerX * displayRect.width()
                val screenCenterY = displayRect.top + centerY * displayRect.height()
                
                // Check if touch is within 30dp of the center
                val touchRadius = 30 * resources.displayMetrics.density
                val dx = screenX - screenCenterX
                val dy = screenY - screenCenterY
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance <= touchRadius) {
                    return detection
                }
            }
        }
        return null
    }
    
    private fun handleControlPointSelection(controlPoint: DetectionResult) {
        selectedControlPoint = controlPoint
        binding.infoMessage.text = "Выбран КП"
        Toast.makeText(this, "Выбран контрольный пункт", Toast.LENGTH_SHORT).show()
    }
    
    private fun handleMapTouch(x: Float, y: Float) {
        startPoint = Pair(x, y)
        binding.infoMessage.text = "Старт установлен"
        Toast.makeText(this, "Старт установлен: (${"%.2f".format(x)}, ${"%.2f".format(y)})", Toast.LENGTH_SHORT).show()
    }

    private fun processImage(uri: Uri) {
        if (!modelLoaded) {
            Toast.makeText(this, "Модель не загружена", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Load bitmap from URI
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Show original image first
                binding.mapImageView.setImageBitmap(bitmap)
                
                // Reset navigation state
                currentDetections = emptyList()
                startPoint = null
                selectedControlPoint = null
                binding.infoMessage.text = "выберите старт"

                // Detect control points in background
                lifecycleScope.launch {
                    try {
                        // Show progress bar on main thread first
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.progressCard.visibility = android.view.View.VISIBLE
                            binding.progressBar.visibility = android.view.View.VISIBLE
                            binding.progressBar.isIndeterminate = true
                            binding.progressText.visibility = android.view.View.VISIBLE
                            binding.progressText.text = "Подготовка..."
                        }

                        // Set progress listener
                        objectDetector.setProgressListener(object : DetectionProgressListener {
                            override fun onProgressUpdate(current: Int, total: Int, message: String) {
                                runOnUiThread {
                                    binding.progressBar.isIndeterminate = false
                                    binding.progressBar.max = total
                                    binding.progressBar.progress = current
                                    binding.progressText.text = "$message ($current/$total)"
                                }
                            }
                        })

                        // Run detection on background thread
                        val detections = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            objectDetector.detect(bitmap)
                        }
                        
                        // Store detections
                        currentDetections = detections
                        
                        // Update UI with progress
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.progressText.text = "Создание кнопок..."
                        }
                        
                        // Create control point buttons in background thread
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            createControlPointButtons(detections, bitmap.width, bitmap.height)
                            binding.progressCard.visibility = android.view.View.GONE
                        }
                    } catch (e: Exception) {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.progressCard.visibility = android.view.View.GONE
                            Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createControlPointButtons(detections: List<DetectionResult>, imageWidth: Int, imageHeight: Int) {
        // Store detection info for drawing
        currentDetections = detections
        
        // Draw circles directly on the current bitmap
        val currentBitmap = binding.mapImageView.drawable?.toBitmap()
        if (currentBitmap != null) {
            val annotatedBitmap = drawControlPointCircles(currentBitmap, detections)
            binding.mapImageView.setImageBitmap(annotatedBitmap)
        }
    }
    
    private fun drawControlPointCircles(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.RED
        }
        
        for (detection in detections) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2
                val radius = kotlin.math.min(box.right - box.left, box.bottom - box.top) / 2 * 0.8f
                
                // Draw circle directly on the bitmap
                canvas.drawCircle(centerX, centerY, radius, paint)
            }
        }
        
        return mutableBitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        objectDetector.close()
    }
}
