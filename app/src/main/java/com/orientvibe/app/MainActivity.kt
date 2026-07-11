package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orientvibe.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var galleryManager: GalleryManager
    private val objectDetector = OnnxObjectDetector(this)
    private var modelLoaded = false

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

                // Detect control points in background
                lifecycleScope.launch {
                    Toast.makeText(
                        this@MainActivity,
                        "Анализ карты...",
                        Toast.LENGTH_SHORT
                    ).show()

                    val detections = objectDetector.detect(bitmap)
                    val annotatedBitmap = drawDetections(bitmap, detections)

                    // Update UI with annotated image
                    binding.mapImageView.setImageBitmap(annotatedBitmap)

                    Toast.makeText(
                        this@MainActivity,
                        "Найдено ${detections.size} контрольных пунктов",
                        Toast.LENGTH_SHORT
                    ).show()
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
        
        val paint = Paint().apply {
            color = Color.GREEN
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
        
        for (detection in detections) {
            // Draw bounding box
            canvas.drawRect(detection.boundingBox, paint)
            
            // Draw confidence score
            val confidenceText = String.format("%.2f", detection.confidence)
            val textWidth = textPaint.measureText(confidenceText)
            val textHeight = textPaint.textSize
            
            canvas.drawRect(
                detection.boundingBox.left,
                detection.boundingBox.top - textHeight - 10f,
                detection.boundingBox.left + textWidth + 10f,
                detection.boundingBox.top,
                textBackgroundPaint
            )
            
            canvas.drawText(
                confidenceText,
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
