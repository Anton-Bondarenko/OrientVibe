package com.orientvibe.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class GalleryManager(
    private val activity: AppCompatActivity,
    private val onImageSelected: (Uri) -> Unit
) {

    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    fun setupLaunchers() {
        galleryLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                onImageSelected(it)
                Toast.makeText(activity, "Карта загружена", Toast.LENGTH_SHORT).show()
            }
        }

        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                launchGallery()
            } else {
                Toast.makeText(activity, "Разрешение на галерею отклонено", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun requestGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchGallery()
            }

            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }

    private fun launchGallery() {
        galleryLauncher.launch("image/*")
    }
}
