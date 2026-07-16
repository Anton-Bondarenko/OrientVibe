package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.orientvibe.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

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

    // Drag state
    private var isDraggingStart = false
    private var isDraggingEnd = false

    // Compass state
    private var compassRotation = 0f

    // Overlay view
    private var overlayView: OverlayView? = null

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
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            // Get the attacher to convert screen coordinates to image coordinates
            val attacher = binding.mapImageView.attacher
            val displayRect = attacher.displayRect

            if (displayRect != null) {
                // Convert screen coordinates to image coordinates
                val imageX = (x - displayRect.left) / displayRect.width()
                val imageY = (y - displayRect.top) / displayRect.height()

                // Check if touch is on a control point
                val touchedControlPointIndex = findTouchedControlPoint(x, y)

                if (touchedControlPointIndex != null) {
                    // Handle control point selection
                    handleControlPointSelection(touchedControlPointIndex)
                } else {
                    // Handle map touch for start point
                    handleMapTouch(imageX, imageY)
                }
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

        overlayView = OverlayView(this)
        overlayView?.id = android.view.View.generateViewId()
        val layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Allow overlay to receive touch events for cross dragging
        overlayView?.isClickable = true
        frameLayout.addView(overlayView, layoutParams)

        // Setup overlay listeners
        setupOverlayListeners()
    }

    private fun setupOverlayListeners() {
        overlayView?.let { overlay ->
            overlay.setAttacher(binding.mapImageView.attacher)
            overlay.setOnCompassRotationListener { rotation ->
                compassRotation = rotation
            }
            overlay.setOnCrossTouchListener { crossType ->
                if (crossType == "start") {
                    isDraggingStart = true
                } else if (crossType == "end") {
                    isDraggingEnd = true
                }
            }
            overlay.setOnMapTouchListener { screenX, screenY ->
                handleMapTouchFromOverlay(screenX, screenY)
            }

            // Add touch listener for drag handling
            overlay.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (isDraggingStart || isDraggingEnd) {
                            handleCrossDrag(event.x, event.y)
                            return@setOnTouchListener true
                        }
                        false
                    }

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        isDraggingStart = false
                        isDraggingEnd = false
                        return@setOnTouchListener true
                    }

                    else -> false
                }
            }
        }
    }

    private fun handleCrossDrag(screenX: Float, screenY: Float) {
        val attacher = binding.mapImageView.attacher
        val displayRect = attacher.displayRect ?: return

        val drawable = binding.mapImageView.drawable ?: return
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight

        // Convert screen coordinates to image coordinates
        val imageX = (screenX - displayRect.left) / scaleX / imageWidth
        val imageY = (screenY - displayRect.top) / scaleY / imageHeight

        if (isDraggingStart && startPoint != null) {
            // Snap to nearest control point if within distance
            val snappedResult = findNearestControlPoint(imageX, imageY)
            if (snappedResult != null) {
                startPoint = snappedResult.first
                startControlPointIndex = snappedResult.second
            } else {
                startPoint = Pair(imageX.coerceIn(0f, 1f), imageY.coerceIn(0f, 1f))
                startControlPointIndex = null
            }
            updateOverlay()
        } else if (isDraggingEnd && endPoint != null) {
            // Snap to nearest control point if within distance
            val snappedResult = findNearestControlPoint(imageX, imageY)
            if (snappedResult != null) {
                endPoint = snappedResult.first
                endControlPointIndex = snappedResult.second
            } else {
                endPoint = Pair(imageX.coerceIn(0f, 1f), imageY.coerceIn(0f, 1f))
                endControlPointIndex = null
            }
            updateOverlay()
        }
    }

    private fun handleMapTouchFromOverlay(screenX: Float, screenY: Float) {
        val attacher = binding.mapImageView.attacher
        val displayRect = attacher.displayRect ?: return

        val drawable = binding.mapImageView.drawable ?: return
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight

        // Convert screen coordinates to image coordinates
        val imageX = (screenX - displayRect.left) / scaleX / imageWidth
        val imageY = (screenY - displayRect.top) / scaleY / imageHeight

        // Find touched control point
        val touchedControlPointIndex = findTouchedControlPoint(screenX, screenY)
        if (touchedControlPointIndex != null) {
            // Handle control point selection
            handleControlPointSelection(touchedControlPointIndex)
        } else {
            // Handle map touch for start point
            handleMapTouch(imageX, imageY)
        }
    }

    private fun findTouchedControlPoint(screenX: Float, screenY: Float): Int? {
        val attacher = binding.mapImageView.attacher
        val displayRect = attacher.displayRect ?: return null

        // Get image dimensions
        val drawable = binding.mapImageView.drawable ?: return null
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        // Calculate scale factors
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight

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

                if (screenX >= screenLeft - margin && screenX <= screenRight + margin &&
                    screenY >= screenTop - margin && screenY <= screenBottom + margin
                ) {
                    return index
                }
            }
        }
        return null
    }

    private fun findTouchedCross(screenX: Float, screenY: Float): String? {
        // Returns "start", "end", or null
        val attacher = binding.mapImageView.attacher
        val displayRect = attacher.displayRect ?: return null

        // Get image dimensions
        val drawable = binding.mapImageView.drawable ?: return null
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        // Calculate scale factors
        val scaleX = displayRect.width() / imageWidth
        val scaleY = displayRect.height() / imageHeight

        val crossSize = 30f * resources.displayMetrics.density
        val touchRadius = crossSize + 20f * resources.displayMetrics.density

        // Check start point cross
        if (startPoint != null) {
            val startX = displayRect.left + startPoint!!.first * imageWidth * scaleX
            val startY = displayRect.top + startPoint!!.second * imageHeight * scaleY
            val distance = kotlin.math.sqrt((screenX - startX).pow(2) + (screenY - startY).pow(2))
            if (distance <= touchRadius) {
                return "start"
            }
        }

        // Check end point cross
        if (endPoint != null) {
            val endX = displayRect.left + endPoint!!.first * imageWidth * scaleX
            val endY = displayRect.top + endPoint!!.second * imageHeight * scaleY
            val distance = kotlin.math.sqrt((screenX - endX).pow(2) + (screenY - endY).pow(2))
            if (distance <= touchRadius) {
                return "end"
            }
        }

        return null
    }

    private fun findNearestControlPoint(
        imageX: Float,
        imageY: Float
    ): Pair<Pair<Float, Float>, Int>? {
        // Returns the center of the nearest control point and its index if within snap distance
        val snapDistance = 0.05f // 5% of image dimensions
        var nearestPoint: Pair<Pair<Float, Float>, Int>? = null
        var minDistance = Float.MAX_VALUE

        // Get image dimensions to normalize CP coordinates
        val drawable = binding.mapImageView.drawable ?: return null
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        for ((index, detection) in currentDetections.withIndex()) {
            if (detection.classId == 0) { // Only control points
                val box = detection.boundingBox
                val centerX = (box.left + box.right) / 2
                val centerY = (box.top + box.bottom) / 2

                // Normalize CP coordinates to 0-1 range
                val normalizedCenterX = centerX / imageWidth
                val normalizedCenterY = centerY / imageHeight

                val distance = kotlin.math.sqrt(
                    (imageX - normalizedCenterX).pow(2) + (imageY - normalizedCenterY).pow(2)
                )

                if (distance < minDistance && distance <= snapDistance) {
                    minDistance = distance
                    nearestPoint = Pair(Pair(normalizedCenterX, normalizedCenterY), index)
                }
            }
        }

        return nearestPoint
    }

    private fun handleControlPointSelection(controlPointIndex: Int) {
        val controlPoint = currentDetections[controlPointIndex]
        selectedControlPoint = controlPoint

        // Get center coordinates of the control point
        val box = controlPoint.boundingBox
        val centerX = (box.left + box.right) / 2
        val centerY = (box.top + box.bottom) / 2

        if (isSettingStart) {
            startPoint = Pair(
                centerX / binding.mapImageView.drawable.intrinsicWidth.toFloat(),
                centerY / binding.mapImageView.drawable.intrinsicHeight.toFloat()
            )
            startControlPointIndex = controlPointIndex
            isSettingStart = false
            binding.infoMessage.text = "выберите направление"
            Toast.makeText(this, "Старт установлен на КП", Toast.LENGTH_SHORT).show()
            redrawImageWithHighlights()
        } else {
            endPoint = Pair(
                centerX / binding.mapImageView.drawable.intrinsicWidth.toFloat(),
                centerY / binding.mapImageView.drawable.intrinsicHeight.toFloat()
            )
            endControlPointIndex = controlPointIndex
            binding.infoMessage.text = "навигация готова"
            Toast.makeText(this, "Направление установлено на КП", Toast.LENGTH_SHORT).show()
            redrawImageWithHighlights()
        }
    }

    private fun handleMapTouch(x: Float, y: Float) {
        if (isSettingStart) {
            startPoint = Pair(x, y)
            isSettingStart = false
            binding.infoMessage.text = "выберите направление"
            Toast.makeText(
                this,
                "Старт установлен: (${"%.2f".format(x)}, ${"%.2f".format(y)})",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            endPoint = Pair(x, y)
            binding.infoMessage.text = "навигация готова"
            Toast.makeText(
                this,
                "Направление установлено: (${"%.2f".format(x)}, ${"%.2f".format(y)})",
                Toast.LENGTH_SHORT
            ).show()
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
                            override fun onProgressUpdate(
                                current: Int,
                                total: Int,
                                message: String
                            ) {
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
                            Toast.makeText(
                                this@MainActivity,
                                "Ошибка: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
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

    private fun createControlPointButtons(
        detections: List<DetectionResult>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        // Store detection info for drawing
        currentDetections = detections

        // Store original bitmap for overlay drawing
        originalBitmap = binding.mapImageView.drawable?.toBitmap()

        // Setup overlay for drawing
        setupOverlay()

        // Update overlay with new data
        overlayView?.setBitmap(originalBitmap!!)
        overlayView?.setDetections(detections)
        overlayView?.setNavigationPoints(startPoint, endPoint)
        overlayView?.setSelectedControlPoints(startControlPointIndex, endControlPointIndex)
        overlayView?.setCompassRotation(compassRotation)
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
        overlayView?.setBitmap(originalBitmap ?: return)
        overlayView?.setDetections(currentDetections)
        overlayView?.setNavigationPoints(startPoint, endPoint)
        overlayView?.setSelectedControlPoints(startControlPointIndex, endControlPointIndex)
        overlayView?.setCompassRotation(compassRotation)
        overlayView?.invalidate()
    }

    private fun drawNavigationLine(
        bitmap: Bitmap,
        start: Pair<Float, Float>,
        end: Pair<Float, Float>
    ): Bitmap {
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
