package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    
    // Zoom variables
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var scaleFactor = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var posX = 0f
    private var posY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load ONNX model
        modelLoaded = objectDetector.loadModel("yolov8n.onnx")
        if (!modelLoaded) {
            Toast.makeText(this, "Не удалось загрузить модель", Toast.LENGTH_LONG).show()
        }

        setupManagers()
        setupButtons()
        setupZoomGestures()
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
    
    private fun setupZoomGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                // Limit scale between 1x and 10x
                scaleFactor = scaleFactor.coerceIn(1.0f, 10.0f)
                updateImageTransform()
                return true
            }
        })
        
        binding.mapImageView.setOnTouchListener { view, event ->
            scaleGestureDetector?.onTouchEvent(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleFactor > 1.0f) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        posX += dx
                        posY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        updateImageTransform()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Reset position when zoom is at 1x
                    if (scaleFactor == 1.0f) {
                        posX = 0f
                        posY = 0f
                        updateImageTransform()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateImageTransform() {
        binding.mapImageView.scaleX = scaleFactor
        binding.mapImageView.scaleY = scaleFactor
        binding.mapImageView.translationX = posX
        binding.mapImageView.translationY = posY
    }
    
    private fun resetZoom() {
        scaleFactor = 1.0f
        posX = 0f
        posY = 0f
        updateImageTransform()
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
                // Reset zoom for new image
                resetZoom()
                
                // Show original image first
                binding.mapImageView.setImageBitmap(bitmap)

                // Detect control points in background
                lifecycleScope.launch {
                    try {
                        // Show progress bar on main thread first
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.progressContainer.visibility = android.view.View.VISIBLE
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
                        
                        // Update UI with progress
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.progressText.text = "Отрисовка результатов..."
                        }
                        
                        // Draw detections in background thread
                        val annotatedBitmap = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            drawDetections(bitmap, detections)
                        }

                        // Update UI with annotated image
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.mapImageView.setImageBitmap(annotatedBitmap)
                            binding.progressContainer.visibility = android.view.View.GONE

                            Toast.makeText(
                                this@MainActivity,
                                "Найдено ${detections.size} контрольных пунктов",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.progressContainer.visibility = android.view.View.GONE
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

    private fun drawDetections(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        // Paint for control points (class 0) - green
        val controlPointPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        
        // Paint for numbers (class 1) - red
        val numberPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            style = Paint.Style.FILL
        }
        
        val textBackgroundPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        
        // Separate detections by class for numbering
        val controlPointDetections = detections.filter { it.classId == 0 }
        val numberDetections = detections.filter { it.classId == 1 }
        
        var cpCounter = 1
        var numCounter = 1
        
        // Draw all detections sorted by class
        for (detection in detections) {
            // Select paint based on class
            val paint = when (detection.classId) {
                0 -> controlPointPaint  // control_point - green
                1 -> numberPaint        // number - red
                else -> controlPointPaint // default
            }
            
            // Draw bounding box
            canvas.drawRect(detection.boundingBox, paint)
            
            // Draw number and confidence
            val number = when (detection.classId) {
                0 -> "КП$cpCounter"
                1 -> "№$numCounter"
                else -> ""
            }
            
            if (detection.classId == 0) cpCounter++
            if (detection.classId == 1) numCounter++
            
            val labelText = "$number (${String.format("%.2f", detection.confidence)})"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize
            
            canvas.drawRect(
                detection.boundingBox.left,
                detection.boundingBox.top - textHeight - 10f,
                detection.boundingBox.left + textWidth + 10f,
                detection.boundingBox.top,
                textBackgroundPaint
            )
            
            canvas.drawText(
                labelText,
                detection.boundingBox.left + 5f,
                detection.boundingBox.top - 5f,
                textPaint
            )
        }
        
        return mutableBitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        objectDetector.close()
    }
}
