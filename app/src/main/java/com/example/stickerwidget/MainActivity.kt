package com.example.stickerwidget

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.stickerwidget.data.StickerItem
import com.example.stickerwidget.data.StickerRepository
import com.example.stickerwidget.ui.screens.HomeScreen
import com.example.stickerwidget.ui.screens.StickerEditorScreen
import com.example.stickerwidget.ui.theme.StickerWidgetTheme
import com.example.stickerwidget.widget.StickerWidgetProvider
import com.example.stickerwidget.widget.WidgetPinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var repository: StickerRepository
    private var tempCameraUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = StickerRepository(applicationContext)

        setContent {
            StickerWidgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var stickers by remember { mutableStateOf(repository.getAllStickers()) }
                    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    var isInEditor by remember { mutableStateOf(false) }

                    // Function to refresh sticker list
                    fun refreshStickers() {
                        stickers = repository.getAllStickers()
                    }

                    // Gallery Picker Launcher
                    val photoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? ->
                        uri?.let {
                            val bitmap = decodeBitmapFromUri(it)
                            if (bitmap != null) {
                                activeBitmap = bitmap
                                isInEditor = true
                            } else {
                                Toast.makeText(this, "Failed to load selected image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // Camera TakePicture Launcher
                    val takePictureLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.TakePicture()
                    ) { success: Boolean ->
                        if (success && tempCameraUri != null) {
                            val bitmap = decodeBitmapFromUri(tempCameraUri!!)
                            if (bitmap != null) {
                                activeBitmap = bitmap
                                isInEditor = true
                            }
                        }
                    }

                    // Camera Permission Launcher
                    val cameraPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted: Boolean ->
                        if (isGranted) {
                            launchCamera(takePictureLauncher)
                        } else {
                            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (isInEditor && activeBitmap != null) {
                        StickerEditorScreen(
                            initialBitmap = activeBitmap!!,
                            onBack = {
                                isInEditor = false
                                activeBitmap = null
                            },
                            onSaveAndMakeWidget = { bitmap, borderWidth, borderColor, hasShadow, caption, emoji, style ->
                                lifecycleScope.launch {
                                    val item = repository.saveStickerBitmap(
                                        bitmap = bitmap,
                                        borderWidth = borderWidth,
                                        borderColor = borderColor,
                                        hasShadow = hasShadow,
                                        captionText = caption,
                                        emojiSticker = emoji,
                                        style = style
                                    )
                                    refreshStickers()

                                    // Pin widget immediately to Android Home Screen!
                                    val pinned = WidgetPinHelper.pinStickerAsWidget(
                                        context = this@MainActivity,
                                        stickerId = item.id,
                                        stickerBitmap = bitmap
                                    )
                                    if (pinned) {
                                        Toast.makeText(this@MainActivity, R.string.widget_pinned_success, Toast.LENGTH_SHORT).show()
                                    }

                                    // Refresh active widgets
                                    StickerWidgetProvider.updateAllWidgets(this@MainActivity)

                                    isInEditor = false
                                    activeBitmap = null
                                }
                            },
                            onSaveOnly = { bitmap, borderWidth, borderColor, hasShadow, caption, emoji, style ->
                                lifecycleScope.launch {
                                    repository.saveStickerBitmap(
                                        bitmap = bitmap,
                                        borderWidth = borderWidth,
                                        borderColor = borderColor,
                                        hasShadow = hasShadow,
                                        captionText = caption,
                                        emojiSticker = emoji,
                                        style = style
                                    )
                                    refreshStickers()
                                    Toast.makeText(this@MainActivity, "Sticker saved to gallery!", Toast.LENGTH_SHORT).show()
                                    isInEditor = false
                                    activeBitmap = null
                                }
                            }
                        )
                    } else {
                        HomeScreen(
                            stickers = stickers,
                            onTakePhotoClick = {
                                val hasCamPermission = ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasCamPermission) {
                                    launchCamera(takePictureLauncher)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onPickPhotoClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onStickerClick = { item ->
                                val bitmap = repository.loadBitmap(item.filePath)
                                if (bitmap != null) {
                                    activeBitmap = bitmap
                                    isInEditor = true
                                }
                            },
                            onPinWidgetClick = { item ->
                                val bitmap = repository.loadBitmap(item.filePath)
                                WidgetPinHelper.pinStickerAsWidget(
                                    context = this,
                                    stickerId = item.id,
                                    stickerBitmap = bitmap
                                )
                            },
                            onDeleteStickerClick = { item ->
                                lifecycleScope.launch {
                                    repository.deleteSticker(item.id)
                                    refreshStickers()
                                    StickerWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun launchCamera(takePictureLauncher: androidx.activity.result.ActivityResultLauncher<Uri>) {
        try {
            val photosDir = File(cacheDir, "photos").apply { if (!exists()) mkdirs() }
            val photoFile = File(photosDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
