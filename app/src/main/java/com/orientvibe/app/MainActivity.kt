package com.orientvibe.app

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.orientvibe.app.databinding.ActivityMainBinding
import com.orientvibe.app.overlay.CompassManager

enum class PanelState {
    MAP_LOADING,
    NAVIGATION,
    NAVIGATION_MODE
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var galleryManager: GalleryManager
    private val objectDetector = OnnxObjectDetector(this)
    private var modelLoaded = false

    // Managers
    private val navigationController = NavigationController()
    private val gpsTrackController = GpsTrackController()
    private lateinit var panelStateManager: PanelStateManager
    private lateinit var compassManager: CompassManager
    private val mapRotationManager = MapRotationManager()
    private lateinit var touchHandler: TouchHandler
    private lateinit var navigationModeManager: NavigationModeManager
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var controlPointButtonManager: ControlPointButtonManager
    private val bitmapTransformer = BitmapTransformer()

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

        // Initialize managers
        initializeManagers()

        // Setup UI
        setupButtons()
        setupOverlayUpdate()
        setupNavigationController()
        enableMapInteractions()
    }

    private fun initializeManagers() {
        // Initialize compass manager
        compassManager = CompassManager(this)
        
        // Initialize panel state manager
        panelStateManager = PanelStateManager(
            binding.mapLoadingPanel,
            binding.navigationPanel,
            binding.navigationModePanel,
            binding.leftArrowButton,
            binding.rightArrowButton
        ) { panelState ->
            handlePanelChange(panelState)
        }

        // Initialize touch handler
        touchHandler = TouchHandler(
            this,
            navigationController,
            { onCrossDragStart(it) },
            { x, y -> onCrossDragMove(x, y) },
            { onCrossDragEnd() }
        )

        // Initialize navigation mode manager
        navigationModeManager = NavigationModeManager(
            compassManager,
            navigationController,
            gpsTrackController,
            bitmapTransformer,
            { disableMapInteractions() }
        )

        // Initialize control point button manager
        controlPointButtonManager = ControlPointButtonManager(
            binding.progressCard,
            binding.progressBar,
            binding.progressText,
            binding.mapCard,
            navigationController,
            compassManager
        )

        // Initialize image processor
        imageProcessor = ImageProcessor(
            this,
            objectDetector,
            navigationController,
            mapRotationManager,
            panelStateManager,
            controlPointButtonManager,
            { detections, bitmap -> onImageProcessingComplete(detections, bitmap) }
        )
        imageProcessor.setModelLoaded(modelLoaded)

        // Initialize camera and gallery managers
        cameraManager = CameraManager(this) { uri ->
            imageProcessor.processImage(uri)
        }
        cameraManager.setupLaunchers()

        galleryManager = GalleryManager(this) { uri ->
            imageProcessor.processImage(uri)
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

        // Panel switching buttons
        binding.leftArrowButton.setOnClickListener {
            panelStateManager.handleLeftArrow()
        }

        binding.rightArrowButton.setOnClickListener {
            panelStateManager.handleRightArrow(navigationController.isNavigationReady())
        }

        // Navigation buttons
        binding.setStartPointButton.setOnClickListener {
            navigationController.setStartPointMode()
            updateInfoMessage()
        }

        binding.setEndPointButton.setOnClickListener {
            navigationController.setEndPointMode()
            updateInfoMessage()
        }
    }

    private fun handlePanelChange(panelState: PanelState) {
        when (panelState) {
            PanelState.MAP_LOADING, PanelState.NAVIGATION -> {
                // Exit navigation mode
                navigationModeManager.exitNavigationMode(binding.mapImageView)
                enableMapInteractions()
            }

            PanelState.NAVIGATION_MODE -> {
                // Enter navigation mode
                binding.mapCard.post {
                    val drawable = binding.mapImageView.drawable
                    if (drawable != null) {
                        val imageWidth = drawable.intrinsicWidth.toFloat()
                        val imageHeight = drawable.intrinsicHeight.toFloat()
                        navigationModeManager.enterNavigationMode(binding.mapImageView, imageWidth, imageHeight)
                    }
                }
                updateAzimuth()
                updateInfoMessage()
            }
        }
    }

    private fun enableMapInteractions() {
        // Re-enable the tap listener for map interaction
        binding.mapImageView.setOnViewTapListener { view, x, y ->
            val attacher = binding.mapImageView.attacher
            val displayRect = attacher.displayRect
            val drawable = binding.mapImageView.drawable

            if (displayRect != null && drawable != null) {
                touchHandler.handleMapTap(
                    x, y, attacher, drawable, resources.displayMetrics.density
                )
            }
        }

        // Enable PhotoView zoom
        binding.mapImageView.attacher.setZoomable(true)
    }

    private fun disableMapInteractions() {
        // Disable tap listener to prevent map interaction
        binding.mapImageView.setOnViewTapListener(null)

        // Don't disable PhotoView zoom to preserve scale
        // binding.mapImageView.attacher.setZoomable(false)
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
        navigationController.setNavigationReadyListener {
            // Auto-switch to navigation mode panel when both points are set
            panelStateManager.switchToNavigationModePanel()
        }
    }

    private fun updateInfoMessage() {
        binding.infoMessage.text =
            navigationController.getStatusMessage(compassManager.getCurrentRotation())
    }

    private fun updateAzimuth() {
        val azimuth = navigationController.calculateAzimuth(compassManager.getCurrentRotation())
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
        
        // Set compass manager
        overlayView?.setCompassManager(compassManager)

        // Increase maximum zoom scale for PhotoView
        binding.mapImageView.setMaximumScale(10f)
        binding.mapImageView.setMinimumScale(0.5f)

        // Setup overlay listeners
        setupOverlayListeners()
    }

    private fun setupOverlayListeners() {
        overlayView?.let { overlay ->
            overlay.setAttacher(binding.mapImageView.attacher)
            
            // Set compass rotation listener on compass manager
            compassManager.setOnCompassRotationListener { rotation ->
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
                            touchHandler.handleTouchDown(
                                event.x,
                                event.y,
                                attacher,
                                drawable,
                                resources.displayMetrics.density
                            )
                        }
                        false
                    }

                    android.view.MotionEvent.ACTION_MOVE -> {
                        touchHandler.handleTouchMove(event.x, event.y)
                        false
                    }

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        touchHandler.handleTouchUp()
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun onCrossDragStart(crossType: String) {
        // Cross drag started - handled by touchHandler
    }

    private fun onCrossDragMove(screenX: Float, screenY: Float) {
        val attacher = binding.mapImageView.attacher
        val drawable = binding.mapImageView.drawable
        if (attacher != null && drawable != null) {
            touchHandler.handleCrossDrag(screenX, screenY, attacher, drawable)
        }
    }

    private fun onCrossDragEnd() {
        // Cross drag ended
    }

    private fun handleMapTouchFromOverlay(screenX: Float, screenY: Float) {
        val attacher = binding.mapImageView.attacher
        val drawable = binding.mapImageView.drawable
        if (attacher != null && drawable != null) {
            touchHandler.handleMapTap(
                screenX, screenY, attacher, drawable, resources.displayMetrics.density
            )
        }
    }

    private fun onImageProcessingComplete(detections: List<DetectionResult>, bitmap: Bitmap) {
        // Store original bitmap in transformer
        bitmapTransformer.setOriginalBitmap(bitmap)
        
        // Show original image
        binding.mapImageView.setImageBitmap(bitmap)
        binding.infoMessage.text = navigationController.getStatusMessage()

        // Update UI with progress
        controlPointButtonManager.updateProgress(0, 1, "Создание кнопок...")

        // Create control point buttons
        controlPointButtonManager.createControlPointButtons(
            detections,
            bitmap.width,
            bitmap.height,
            bitmap
        )

        // Set overlay view reference
        overlayView = controlPointButtonManager.getOverlayView()
        
        // Set compass manager in overlay view
        overlayView?.setCompassManager(compassManager)
        
        // Set overlay view in navigation mode manager
        navigationModeManager.setOverlayView(overlayView)

        // Save original overlay coordinates
        overlayView?.saveOriginalOverlayCoordinates()

        // Setup overlay listeners
        setupOverlayListeners()

        // Hide progress
        controlPointButtonManager.hideProgress()

        // Auto-switch to navigation panel after map load
        panelStateManager.switchToNavigationPanel()
    }

    private fun updateOverlay() {
        val originalBitmap = binding.mapImageView.drawable?.toBitmap() ?: return
        controlPointButtonManager.updateOverlay(originalBitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        objectDetector.close()
        mapRotationManager.clear()
    }
}

