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
    private var endPoint: Pair<Float, Float>? = null
    private var selectedControlPoint: DetectionResult? = null
    private var startControlPointIndex: Int? = null
    private var endControlPointIndex: Int? = null
    private var isSettingStart = true // true = setting start, false = setting end
    private var originalBitmap: Bitmap? = null

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
        setupOverlayUpdate()
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
            android.util.Log.d("TouchDebug", "Tap detected at screen: ($x, $y)")
            
            // Get the attacher to convert screen coordinates to image coordinates
            val attacher = binding.mapImageView.attacher
            val displayRect = attacher.displayRect
            
            if (displayRect != null) {
                // Convert screen coordinates to image coordinates
                val imageX = (x - displayRect.left) / displayRect.width()
                val imageY = (y - displayRect.top) / displayRect.height()
                android.util.Log.d("TouchDebug", "Image coordinates: ($imageX, $imageY)")
                android.util.Log.d("TouchDebug", "Display rect: left=${displayRect.left}, top=${displayRect.top}, width=${displayRect.width()}, height=${displayRect.height()}")
                
                // Check if touch is on a control point
                val touchedControlPointIndex = findTouchedControlPoint(x, y)
                android.util.Log.d("TouchDebug", "Touched CP index: $touchedControlPointIndex")
                
                if (touchedControlPointIndex != null) {
                    // Handle control point selection
                    android.util.Log.d("TouchDebug", "Handling CP selection for index: $touchedControlPointIndex")
                    handleControlPointSelection(touchedControlPointIndex)
                } else {
                    // Handle map touch for start point
                    android.util.Log.d("TouchDebug", "No CP touched, handling map touch")
                    handleMapTouch(imageX, imageY)
                }
            } else {
                android.util.Log.d("TouchDebug", "Display rect is null")
            }
        }
    }
    
    private fun setupOverlayUpdate() {
        // Update overlay when PhotoView scale changes
        binding.mapImageView.setOnMatrixChangeListener { rect ->
            updateOverlay()
        }
    }
    
    private fun setupOverlay() {
        // Create and add overlay view programmatically
        val frameLayout = binding.mapCard.getChildAt(0) as android.widget.FrameLayout
        
        // Remove existing overlay if any
        if (frameLayout.childCount > 1) {
            frameLayout.removeViewAt(1)
        }
        
        val overlayView = OverlayView(this)
        overlayView.id = android.view.View.generateViewId()
        val layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Make sure overlay doesn't intercept touches
        overlayView.isClickable = false
        overlayView.isFocusable = false
        frameLayout.addView(overlayView, layoutParams)
    }
    
    private fun findTouchedControlPoint(screenX: Float, screenY: Float): Int? {
        val attacher = binding.mapImageView.attacher
        val displayRect = attacher.displayRect ?: return null
        
        // Get image dimensions
        val drawable = binding.mapImageView.drawable ?: return null
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        
        android.util.Log.d("TouchDebug", "Checking for CP touch. Total detections: ${currentDetections.size}")
        android.util.Log.d("TouchDebug", "Image size: ${imageWidth}x${imageHeight}")
        
        // Calculate scale factors
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight
        android.util.Log.d("TouchDebug", "Scale factors: scaleX=$scaleX, scaleY=$scaleY")
        
        for ((index, detection) in currentDetections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2
                
                // Convert bounding box to screen coordinates using scale factors
                val screenLeft = displayRect.left + box.left * scaleX
                val screenRight = displayRect.left + box.right * scaleX
                val screenTop = displayRect.top + box.top * scaleY
                val screenBottom = displayRect.top + box.bottom * scaleY
                
                // Check if touch is within the bounding box (with some margin)
                val margin = 20 * resources.displayMetrics.density
                android.util.Log.d("TouchDebug", "CP $index: box=($screenLeft, $screenTop, $screenRight, $screenBottom), touch=($screenX, $screenY), margin=$margin")
                
                if (screenX >= screenLeft - margin && screenX <= screenRight + margin &&
                    screenY >= screenTop - margin && screenY <= screenBottom + margin) {
                    android.util.Log.d("TouchDebug", "CP $index matched!")
                    return index
                }
            }
        }
        android.util.Log.d("TouchDebug", "No CP matched")
        return null
    }
    
    private fun handleControlPointSelection(controlPointIndex: Int) {
        android.util.Log.d("TouchDebug", "handleControlPointSelection called with index: $controlPointIndex")
        
        val controlPoint = currentDetections[controlPointIndex]
        selectedControlPoint = controlPoint
        
        // Get center coordinates of the control point
        val box = controlPoint.boundingBox
        val centerX = (box.left + box.right) / 2
        val centerY = (box.top + box.bottom) / 2
        
        android.util.Log.d("TouchDebug", "CP center: ($centerX, $centerY)")
        android.util.Log.d("TouchDebug", "isSettingStart: $isSettingStart")
        
        if (isSettingStart) {
            startPoint = Pair(centerX / binding.mapImageView.drawable.intrinsicWidth.toFloat(), 
                             centerY / binding.mapImageView.drawable.intrinsicHeight.toFloat())
            startControlPointIndex = controlPointIndex
            isSettingStart = false
            binding.infoMessage.text = "выберите направление"
            Toast.makeText(this, "Старт установлен на КП", Toast.LENGTH_SHORT).show()
            android.util.Log.d("TouchDebug", "Start point set to CP $controlPointIndex")
            redrawImageWithHighlights()
        } else {
            endPoint = Pair(centerX / binding.mapImageView.drawable.intrinsicWidth.toFloat(), 
                           centerY / binding.mapImageView.drawable.intrinsicHeight.toFloat())
            endControlPointIndex = controlPointIndex
            binding.infoMessage.text = "навигация готова"
            Toast.makeText(this, "Направление установлено на КП", Toast.LENGTH_SHORT).show()
            android.util.Log.d("TouchDebug", "End point set to CP $controlPointIndex")
            redrawImageWithHighlights()
        }
    }
    
    private fun handleMapTouch(x: Float, y: Float) {
        if (isSettingStart) {
            startPoint = Pair(x, y)
            isSettingStart = false
            binding.infoMessage.text = "выберите направление"
            Toast.makeText(this, "Старт установлен: (${"%.2f".format(x)}, ${"%.2f".format(y)})", Toast.LENGTH_SHORT).show()
        } else {
            endPoint = Pair(x, y)
            binding.infoMessage.text = "навигация готова"
            Toast.makeText(this, "Направление установлено: (${"%.2f".format(x)}, ${"%.2f".format(y)})", Toast.LENGTH_SHORT).show()
            redrawImageWithHighlights()
        }
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
                endPoint = null
                selectedControlPoint = null
                startControlPointIndex = null
                endControlPointIndex = null
                isSettingStart = true
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
        
        // Store original bitmap for overlay drawing
        originalBitmap = binding.mapImageView.drawable?.toBitmap()
        
        // Setup overlay for drawing
        setupOverlay()
    }
    
    private fun drawControlPointCircles(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        return drawControlPointCircles(bitmap, detections, null, null)
    }
    
    private fun drawControlPointCircles(
        bitmap: Bitmap, 
        detections: List<DetectionResult>,
        startCPIndex: Int? = null,
        endCPIndex: Int? = null
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        for ((index, detection) in detections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2
                val radius = kotlin.math.min(box.right - box.left, box.bottom - box.top) / 2 * 0.8f
                
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    color = Color.RED
                }
                
                // Check if this is a selected control point
                if (index == startCPIndex || index == endCPIndex) {
                    paint.strokeWidth = 8f // Thicker line for selected points
                    paint.color = Color.YELLOW // Different color for selected points
                    canvas.drawCircle(centerX, centerY, radius * 1.2f, paint) // Larger circle
                } else {
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
            }
        }
        
        return mutableBitmap
    }
    
    private fun redrawImageWithHighlights() {
        updateOverlay()
    }
    
    private fun updateOverlay() {
        // Find the overlay view in the layout
        val frameLayout = binding.mapCard.getChildAt(0) as android.widget.FrameLayout
        val overlayView = frameLayout.getChildAt(1) // Second child is the overlay
        if (overlayView != null) {
            overlayView.invalidate()
        }
    }
    
    private inner class OverlayView(context: android.content.Context) : android.view.View(context) {
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            
            android.util.Log.d("OverlayDebug", "onDraw called")
            
            if (originalBitmap == null) {
                android.util.Log.d("OverlayDebug", "originalBitmap is null")
                return
            }
            
            val bitmap = originalBitmap!!
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()
            
            android.util.Log.d("OverlayDebug", "Image size: ${bitmap.width}x${bitmap.height}")
            android.util.Log.d("OverlayDebug", "startCPIndex: $startControlPointIndex, endCPIndex: $endControlPointIndex")
            
            // Get PhotoView display rect to scale coordinates
            val attacher = binding.mapImageView.attacher
            val displayRect = attacher.displayRect ?: return
            
            android.util.Log.d("OverlayDebug", "Display rect: ${displayRect.width()}x${displayRect.height()}")
            
            // Calculate scale factors
            val scaleX = displayRect.width() / imageWidth
            val scaleY = displayRect.height() / imageHeight
            
            android.util.Log.d("OverlayDebug", "Scale factors: scaleX=$scaleX, scaleY=$scaleY")
            
            // Draw control point circles
            var cpCount = 0
            for ((index, detection) in currentDetections.withIndex()) {
                if (detection.classId == 0) { // Only control points
                    cpCount++
                    val box = detection.boundingBox
                    val centerX = (box.left + box.right) / 2
                    val centerY = (box.top + box.bottom) / 2
                    val radius = kotlin.math.min(box.right - box.left, box.bottom - box.top) / 2 * 0.8f
                    
                    val paint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 4f
                        color = android.graphics.Color.RED
                    }
                    
                    // Check if this is a selected control point
                    val isSelected = index == startControlPointIndex || index == endControlPointIndex
                    if (isSelected) {
                        paint.strokeWidth = 8f
                        paint.color = android.graphics.Color.YELLOW
                        android.util.Log.d("OverlayDebug", "CP $index is SELECTED (highlighted)")
                    }
                    
                    // Convert to screen coordinates
                    val screenCenterX = displayRect.left + centerX * scaleX
                    val screenCenterY = displayRect.top + centerY * scaleY
                    val screenRadius = radius * scaleX
                    
                    canvas.drawCircle(screenCenterX, screenCenterY, screenRadius, paint)
                }
            }
            
            android.util.Log.d("OverlayDebug", "Drew $cpCount control points")
            
            // Draw navigation line if both points are set
            if (startPoint != null && endPoint != null) {
                android.util.Log.d("OverlayDebug", "Drawing navigation line")
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 6f
                    color = android.graphics.Color.GREEN
                    isAntiAlias = true
                }
                
                val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
                val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
                val endX = displayRect.left + endPoint!!.first * imageWidth * scaleX
                val endY = displayRect.top + endPoint!!.second * imageHeight * scaleY
                
                canvas.drawLine(startX, startY, endX, endY, paint)
            } else {
                android.util.Log.d("OverlayDebug", "Not drawing navigation line - startPoint=$startPoint, endPoint=$endPoint")
            }
        }
        
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            // Pass touch events through to PhotoView
            return false
        }
    }
    
    private fun drawNavigationLine(bitmap: Bitmap, start: Pair<Float, Float>, end: Pair<Float, Float>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        
        val startX = start.first * imageWidth
        val startY = start.second * imageHeight
        val endX = end.first * imageWidth
        val endY = end.second * imageHeight
        
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.GREEN
            isAntiAlias = true
        }
        
        canvas.drawLine(startX, startY, endX, endY, paint)
        
        return mutableBitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        objectDetector.close()
    }
}
