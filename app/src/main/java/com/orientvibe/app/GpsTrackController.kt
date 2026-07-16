package com.orientvibe.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class GpsTrackController {
    // Track data
    private var trackPoints: List<GpsPoint> = emptyList()
    private var startPoint: Pair<Float, Float>? = null // Image coordinates (0-1)
    private var compassRotation: Float = 0f

    // Scale: 1:10000 means 1 cm on map = 10000 cm = 100 m in reality
    private val mapScale = 10000f

    // Earth radius in meters
    private val earthRadius = 6371000.0

    fun setTrackPoints(points: List<GpsPoint>) {
        this.trackPoints = points
    }

    fun setStartPoint(imageX: Float, imageY: Float) {
        this.startPoint = Pair(imageX, imageY)
    }

    fun setCompassRotation(rotation: Float) {
        this.compassRotation = rotation
    }

    fun getTrackPoints(): List<GpsPoint> = trackPoints

    fun getStartPoint(): Pair<Float, Float>? = startPoint

    fun getCompassRotation(): Float = compassRotation

    /**
     * Converts GPS coordinates to image coordinates relative to start point
     * @param gpsPoint GPS point to convert
     * @param imageWidth Image width in pixels
     * @param imageHeight Image height in pixels
     * @return Pair of image coordinates (x, y) in pixels, or null if start point not set
     */
    fun gpsToImageCoordinates(
        gpsPoint: GpsPoint,
        imageWidth: Float,
        imageHeight: Float
    ): Pair<Float, Float>? {
        val start = startPoint ?: return null

        if (trackPoints.isEmpty()) return null

        // Get first track point as reference
        val referencePoint = trackPoints[0]

        // Calculate distance and bearing from reference point
        val (distance, bearing) = calculateDistanceAndBearing(
            referencePoint.latitude, referencePoint.longitude,
            gpsPoint.latitude, gpsPoint.longitude
        )

        // Convert distance to image pixels based on scale
        // Scale 1:10000 means 100 meters = 1 cm on map
        // Assuming image DPI of 96, 1 cm = 37.8 pixels
        val pixelsPerMeter = 37.8f / (mapScale / 100f)
        val distanceInPixels = distance * pixelsPerMeter

        // Calculate bearing relative to compass rotation
        // Compass rotation: 0 = north, clockwise
        // Bearing: 0 = north, clockwise
        // We need to rotate the bearing by the compass rotation
        val adjustedBearing = bearing - compassRotation

        // Convert to image coordinates (relative to start point)
        // x = distance * sin(bearing), y = -distance * cos(bearing) (negative because y grows downward)
        val deltaX = distanceInPixels * sin(adjustedBearing * PI / 180f)
        val deltaY = -distanceInPixels * cos(adjustedBearing * PI / 180f)

        // Convert to normalized coordinates (0-1) then to pixels
        val startX = start.first * imageWidth
        val startY = start.second * imageHeight

        val imageX = startX + deltaX
        val imageY = startY + deltaY

        return Pair(imageX.toFloat(), imageY.toFloat())
    }

    /**
     * Calculates distance in meters and bearing in degrees between two GPS points
     */
    private fun calculateDistanceAndBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Pair<Double, Double> {
        val lat1Rad = lat1 * PI / 180.0
        val lat2Rad = lat2 * PI / 180.0
        val deltaLat = (lat2 - lat1) * PI / 180.0
        val deltaLon = (lon2 - lon1) * PI / 180.0

        // Haversine formula for distance
        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = earthRadius * c

        // Bearing formula
        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        val bearing = atan2(y, x) * 180.0 / PI

        return Pair(distance, bearing)
    }

    /**
     * Loads GPX file and extracts track points
     * @param gpxContent GPX file content as string
     * @return List of GPS points
     */
    fun loadGpxFile(gpxContent: String): List<GpsPoint> {
        val points = mutableListOf<GpsPoint>()
        
        // Simple GPX parser - extracts trkpt elements
        val trkptRegex = """<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)">""".toRegex()
        val timeRegex = """<time>([^<]+)</time>""".toRegex()
        
        val trkptMatches = trkptRegex.findAll(gpxContent)
        
        for (match in trkptMatches) {
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lon = match.groupValues[2].toDoubleOrNull() ?: continue
            
            // Try to find time element within the trkpt
            val trkptStart = match.range.first
            val trkptEnd = gpxContent.indexOf("</trkpt>", trkptStart)
            if (trkptEnd != -1) {
                val trkptContent = gpxContent.substring(trkptStart, trkptEnd)
                val timeMatch = timeRegex.find(trkptContent)
                val timestamp = timeMatch?.groupValues?.get(1)?.let { parseGpxTime(it) }
                
                points.add(GpsPoint(lat, lon, timestamp ?: System.currentTimeMillis()))
            } else {
                points.add(GpsPoint(lat, lon))
            }
        }
        
        return points
    }

    /**
     * Parses GPX time format to timestamp
     */
    private fun parseGpxTime(timeString: String): Long {
        // GPX time format: 2024-01-01T12:00:00Z
        return try {
            val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            isoFormat.parse(timeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Clears all track data
     */
    fun clear() {
        trackPoints = emptyList()
        startPoint = null
        compassRotation = 0f
    }

    companion object {
        private const val PI = 3.141592653589793
    }
}
