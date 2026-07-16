package com.orientvibe.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.orientvibe.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var galleryManager: GalleryManager
    private val objectDetector = OnnxObjectDetector(this)
    private var modelLoaded = false

    // Navigation controller
    private val navigationController = NavigationController()

    // GPS track controller
    private val gpsTrackController = GpsTrackController()

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
        setupNavigationController()
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
                val drawable = binding.mapImageView.drawable
                if (drawable != null) {
                    val touchedControlPointIndex = navigationController.findTouchedControlPoint(
                        x, y, attacher, drawable, resources.displayMetrics.density
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
                                this,
                                "Направление установлено на КП",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        // Handle map touch for start point
                        navigationController.handleMapTouch(imageX, imageY)
                        if (navigationController.isNavigationReady()) {
                            Toast.makeText(
                                this,
                                "Направление установлено: (${"%.2f".format(imageX)}, ${
                                    "%.2f".format(
                                        imageY
                                    )
                                })",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Старт установлен: (${"%.2f".format(imageX)}, ${"%.2f".format(imageY)})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
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

    private fun setupNavigationController() {
        navigationController.setNavigationStateChangedListener {
            updateOverlay()
            updateInfoMessage()
            updateAzimuth()
            updateGpsTrack()
        }
        navigationController.setControlPointSelectedListener { controlPoint ->
            // Handle control point selection if needed
        }
    }

    private fun updateInfoMessage() {
        binding.infoMessage.text = navigationController.getStatusMessage(compassRotation)
    }

    private fun updateAzimuth() {
        val azimuth = navigationController.calculateAzimuth(compassRotation)
        overlayView?.setAzimuth(azimuth)
    }

    private fun updateGpsTrack() {
        val startPoint = navigationController.startPoint
        if (startPoint != null) {
            gpsTrackController.setStartPoint(startPoint.first, startPoint.second)
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

        // Set GPS track controller
        overlayView?.setGpsTrackController(gpsTrackController)

        // Increase maximum zoom scale for PhotoView
        binding.mapImageView.setMaximumScale(10f)

        // Setup overlay listeners
        setupOverlayListeners()
    }

    private fun setupOverlayListeners() {
        overlayView?.let { overlay ->
            overlay.setAttacher(binding.mapImageView.attacher)
            overlay.setOnCompassRotationListener { rotation ->
                compassRotation = rotation
                gpsTrackController.setCompassRotation(rotation)
                updateAzimuth()
                updateInfoMessage()
            }
            overlay.setOnMapTouchListener { screenX, screenY ->
                handleMapTouchFromOverlay(screenX, screenY)
            }

            // Add touch listener for drag handling
            overlay.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        val attacher = binding.mapImageView.attacher
                        val drawable = binding.mapImageView.drawable
                        if (attacher != null && drawable != null) {
                            val touchedCross = navigationController.findTouchedCross(
                                event.x,
                                event.y,
                                attacher,
                                drawable,
                                resources.displayMetrics.density
                            )
                            if (touchedCross == "start") {
                                isDraggingStart = true
                            } else if (touchedCross == "end") {
                                isDraggingEnd = true
                            }
                        }
                        false
                    }

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

        if (isDraggingStart) {
            // Snap to nearest control point if within distance
            val snappedResult =
                navigationController.findNearestControlPoint(imageX, imageY, drawable)
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
            val snappedResult =
                navigationController.findNearestControlPoint(imageX, imageY, drawable)
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
        val touchedControlPointIndex = navigationController.findTouchedControlPoint(
            screenX, screenY, attacher, drawable, resources.displayMetrics.density
        )
        if (touchedControlPointIndex != null) {
            navigationController.handleControlPointSelection(
                touchedControlPointIndex, imageWidth, imageHeight
            )
            if (navigationController.isNavigationReady()) {
                Toast.makeText(this, "Направление установлено на КП", Toast.LENGTH_SHORT).show()
            }
        } else {
            navigationController.handleMapTouch(imageX, imageY)
            if (navigationController.isNavigationReady()) {
                Toast.makeText(
                    this,
                    "Направление установлено: (${"%.2f".format(imageX)}, ${"%.2f".format(imageY)})",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Старт установлен: (${"%.2f".format(imageX)}, ${"%.2f".format(imageY)})",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                navigationController.reset()
                binding.infoMessage.text = navigationController.getStatusMessage()

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
                        navigationController.setDetections(detections)

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
        // Store original bitmap for overlay drawing
        val originalBitmap = binding.mapImageView.drawable?.toBitmap()

        // Setup overlay for drawing
        setupOverlay()

        // Update overlay with new data
        overlayView?.setBitmap(originalBitmap!!)
        overlayView?.setDetections(detections)
        overlayView?.setNavigationPoints(
            navigationController.startPoint,
            navigationController.endPoint
        )
        overlayView?.setSelectedControlPoints(
            navigationController.startControlPointIndex,
            navigationController.endControlPointIndex
        )
        overlayView?.setCompassRotation(compassRotation)
    }

    private fun updateOverlay() {
        val originalBitmap = binding.mapImageView.drawable?.toBitmap() ?: return
        overlayView?.setBitmap(originalBitmap)
        overlayView?.setDetections(navigationController.currentDetections)
        overlayView?.setNavigationPoints(
            navigationController.startPoint,
            navigationController.endPoint
        )
        overlayView?.setSelectedControlPoints(
            navigationController.startControlPointIndex,
            navigationController.endControlPointIndex
        )
        overlayView?.setCompassRotation(compassRotation)
        overlayView?.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        objectDetector.close()
    }
}
