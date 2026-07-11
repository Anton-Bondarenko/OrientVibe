package com.orientvibe.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

data class ControlPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val number: Int? = null
)

class MapAnalyzer {
    
    private val tag = "MapAnalyzer"
    
    suspend fun analyzeMap(bitmap: Bitmap): Pair<List<ControlPoint>, Bitmap> =
        withContext(Dispatchers.Default) {
            try {
                // Convert bitmap to OpenCV Mat
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)
                
                // Detect red circles
                val circles = detectRedCircles(mat)
                
                // Detect lines between circles
                val lines = detectLines(mat)
                
                // Create overlay image
                val overlayMat = mat.clone()
                drawDetectedElements(overlayMat, circles, lines)
                
                // Convert back to bitmap
                val resultBitmap = Bitmap.createBitmap(
                    mat.cols(),
                    mat.rows(),
                    Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(overlayMat, resultBitmap)
                
                // Convert circles to ControlPoint objects
                val controlPoints = mutableListOf<ControlPoint>()
                for (i in 0 until circles.cols()) {
                    val circleData = circles[0, i]
                    controlPoints.add(
                        ControlPoint(
                            x = circleData[0].toFloat(),
                            y = circleData[1].toFloat(),
                            radius = circleData[2].toFloat()
                        )
                    )
                }
                
                Log.d(tag, "Found ${controlPoints.size} control points")
                
                Pair(controlPoints, resultBitmap)
            } catch (e: Exception) {
                Log.e(tag, "Error analyzing map", e)
                Pair(emptyList(), bitmap)
            }
        }
    
    private fun detectRedCircles(mat: Mat): Mat {
        // Convert to HSV color space
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
        
        // Define red color range (red wraps around in HSV)
        // Color #8B535C (RGB: 139, 83, 92) in HSV: H~350°, S~40%, V~55%
        val lowerRed1 = Scalar(0.0, 30.0, 30.0)
        val upperRed1 = Scalar(10.0, 80.0, 80.0)
        val lowerRed2 = Scalar(170.0, 30.0, 30.0)
        val upperRed2 = Scalar(180.0, 80.0, 80.0)
        
        // Create masks for both red ranges
        val mask1 = Mat()
        val mask2 = Mat()
        Core.inRange(hsvMat, lowerRed1, upperRed1, mask1)
        Core.inRange(hsvMat, lowerRed2, upperRed2, mask2)
        
        // Combine masks
        val redMask = Mat()
        Core.add(mask1, mask2, redMask)
        
        // Apply morphological operations to reduce noise
        // Kernel size adjusted for ~6px line thickness
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(7.0, 7.0)
        )
        Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_CLOSE, kernel)
        
        // Detect circles using Hough Circle Transform
        // Circles are ~85px diameter (42-43px radius)
        val circles = Mat()
        Imgproc.HoughCircles(
            redMask,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.0, // dp
            80.0, // minDist (minimum distance between circle centers)
            50.0, // param1 (higher threshold for Canny)
            30.0, // param2 (accumulator threshold)
            35, // minRadius (85px diameter / 2 - margin)
            50  // maxRadius (85px diameter / 2 + margin)
        )
        
        // Cleanup
        hsvMat.release()
        mask1.release()
        mask2.release()
        redMask.release()
        kernel.release()
        
        return circles
    }
    
    private fun detectLines(mat: Mat): Mat {
        // Convert to grayscale
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // Apply Canny edge detection
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0, 3, false)
        
        // Detect lines using Hough Line Transform
        val lines = Mat()
        Imgproc.HoughLinesP(
            edges,
            lines,
            1.0, // rho
            Math.PI / 180.0, // theta
            80, // threshold
            50.0, // minLineLength
            10.0 // maxLineGap
        )
        
        // Cleanup
        grayMat.release()
        edges.release()
        
        return lines
    }
    
    private fun drawDetectedElements(mat: Mat, circles: Mat, lines: Mat) {
        // Draw detected circles with bright color
        for (i in 0 until circles.cols()) {
            val circle = circles[0, i]
            val center = Point(circle[0], circle[1])
            val radius = circle[2]
            
            // Draw bright green circle
            Imgproc.circle(mat, center, radius.toInt(), Scalar(0.0, 255.0, 0.0), 3)
            Imgproc.circle(mat, center, 3, Scalar(0.0, 255.0, 0.0), -1)
        }
        
        // Draw detected lines
        for (i in 0 until lines.rows()) {
            val line = lines[i, 0]
            val pt1 = Point(line[0], line[1])
            val pt2 = Point(line[2], line[3])
            
            // Draw bright blue line
            Imgproc.line(mat, pt1, pt2, Scalar(255.0, 0.0, 0.0), 2)
        }
    }
}
