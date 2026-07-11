package com.orientvibe.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CameraManager(
    private val activity: AppCompatActivity,
    private val onImageSelected: (Uri) -> Unit
) {

    private lateinit var cameraLauncher: ActivityResultLauncher<Void?>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    fun setupLaunchers() {
        cameraLauncher = activity.registerForActivityResult(
            CameraContract()
        ) { uri ->
            uri?.let {
                onImageSelected(it)
                Toast.makeText(activity, "Фото сохранено", Toast.LENGTH_SHORT).show()
            }
        }

        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(activity, "Разрешение на камеру отклонено", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun requestCamera() {
        when {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }

            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        cameraLauncher.launch(null)
    }
}
