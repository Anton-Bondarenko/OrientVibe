package com.orientvibe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class DetectionResult(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int
)

class OnnxObjectDetector(private val context: Context) {
    
    private val tag = "OnnxObjectDetector"
    private var ortSession: OrtSession? = null
    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private val inputImageWidth = 640
    private val inputImageHeight = 640
    private val labels = listOf("control_point")
    
    fun loadModel(modelPath: String): Boolean {
        return try {
            // Copy model from assets to temporary file
            val tempFile = copyAssetToTempFile(modelPath)
            
            // Create ONNX session
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            ortSession = ortEnvironment.createSession(tempFile.absolutePath, sessionOptions)
            
            // Clean up temp file
            tempFile.delete()
            
            Log.d(tag, "ONNX model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error loading ONNX model", e)
            false
        }
    }
    
    private fun copyAssetToTempFile(assetPath: String): File {
        val inputStream = context.assets.open(assetPath)
        val tempFile = File(context.cacheDir, "temp_model.onnx")
        val outputStream = FileOutputStream(tempFile)
        
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        
        return tempFile
    }
    
    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val session = this.ortSession ?: run {
            Log.e(tag, "ONNX session not initialized")
            return emptyList()
        }
        
        return try {
            // Preprocess image
            val inputBuffer = preprocessImage(bitmap)
            
            // Prepare input tensor
            val inputName = session.inputNames?.iterator()?.next()
            val inputShape = longArrayOf(1, 3, inputImageHeight.toLong(), inputImageWidth.toLong())
            val inputTensor = ortEnvironment.createTensor(
                inputShape,
                inputBuffer
            )
            
            // Run inference
            val inputs = mapOf(inputName to inputTensor)
            val outputs = session.run(inputs)
            
            // Get output tensor
            val outputTensor = outputs[0]
            val outputBuffer = outputTensor.floatBuffer
            
            // Postprocess results
            val results = postprocessResults(outputBuffer, bitmap.width, bitmap.height)
            
            // Clean up
            inputTensor.close()
            outputTensor.close()
            outputs.close()
            
            results
        } catch (e: Exception) {
            Log.e(tag, "Error during detection", e)
            emptyList()
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        // Resize bitmap to input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)
        
        // Prepare buffer (NCHW format: batch, channels, height, width)
        val buffer = FloatBuffer.allocate(3 * inputImageHeight * inputImageWidth)
        buffer.order(ByteOrder.nativeOrder())
        
        // Convert bitmap to float array with normalization [0, 1]
        val pixels = IntArray(inputImageWidth * inputImageHeight)
        resizedBitmap.getPixels(pixels, 0, inputImageWidth, 0, 0, inputImageWidth, inputImageHeight)
        
        for (y in 0 until inputImageHeight) {
            for (x in 0 until inputImageWidth) {
                val pixel = pixels[y * inputImageWidth + x]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                
                // NCHW format
                buffer.put(r)  // Channel 0 (Red)
                buffer.put(g)  // Channel 1 (Green)
                buffer.put(b)  // Channel 2 (Blue)
            }
        }
        
        resizedBitmap.recycle()
        buffer.rewind()
        
        return buffer
    }
    
    private fun postprocessResults(
        outputBuffer: FloatBuffer,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectionResult> {
        outputBuffer.rewind()
        
        val results = mutableListOf<DetectionResult>()
        val confidenceThreshold = 0.5f
        
        // YOLOv8 output format: [1, 84, 8400] where 84 = 4 (bbox) + 80 (classes) for COCO
        // For custom model with 1 class: [1, 5, 8400] where 5 = 4 (bbox) + 1 (class)
        
        // Get output shape from session
        val outputShape = ortSession?.outputInfo?.values?.first()?.info?.shape
        val numDetections = outputShape?.get(2)?.toInt() ?: 8400
        val numValues = outputShape?.get(1)?.toInt() ?: 5
        
        // Scale factors for converting back to original image size
        val scaleX = originalWidth.toFloat() / inputImageWidth
        val scaleY = originalHeight.toFloat() / inputImageHeight
        
        for (i in 0 until numDetections) {
            val confidenceIndex = i * numValues + 4
            
            if (confidenceIndex < outputBuffer.remaining()) {
                val confidence = outputBuffer.get(confidenceIndex)
                
                if (confidence > confidenceThreshold) {
                    // YOLO format: center_x, center_y, width, height
                    val centerX = outputBuffer.get(i * numValues)
                    val centerY = outputBuffer.get(i * numValues + 1)
                    val width = outputBuffer.get(i * numValues + 2)
                    val height = outputBuffer.get(i * numValues + 3)
                    
                    // Convert to top-left coordinates and scale to original image
                    val left = (centerX - width / 2) * scaleX
                    val top = (centerY - height / 2) * scaleY
                    val right = (centerX + width / 2) * scaleX
                    val bottom = (centerY + height / 2) * scaleY
                    
                    val boundingBox = RectF(left, top, right, bottom)
                    results.add(DetectionResult(boundingBox, confidence, 0))
                }
            }
        }
        
        // Apply Non-Maximum Suppression (NMS)
        return applyNMS(results, 0.45f)
    }
    
    private fun applyNMS(results: List<DetectionResult>, iouThreshold: Float): List<DetectionResult> {
        if (results.isEmpty()) return results
        
        // Sort by confidence (descending)
        val sorted = results.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()
        
        for (result in sorted) {
            var keep = true
            for (selectedResult in selected) {
                val iou = calculateIoU(result.boundingBox, selectedResult.boundingBox)
                if (iou > iouThreshold) {
                    keep = false
                    break
                }
            }
            if (keep) {
                selected.add(result)
            }
        }
        
        return selected
    }
    
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)
        
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }
        
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea
        
        return intersectionArea / unionArea
    }
    
    fun close() {
        ortSession?.close()
        ortSession = null
        ortEnvironment.close()
    }
}
