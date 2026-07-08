package com.orientvibe.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import java.io.File

class CameraContract : ActivityResultContract<Void?, Uri?>() {
    
    private lateinit var photoUri: Uri

    override fun createIntent(context: Context, input: Void?): Intent {
        // Create a file to save the photo
        val photoFile = File(context.cacheDir, "map_photo_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
        )
        
        return Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == android.app.Activity.RESULT_OK) photoUri else null
    }
}
