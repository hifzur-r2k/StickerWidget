package com.example.stickerwidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class StickerRepository(private val context: Context) {

    private val gson = Gson()
    private val stickersDir: File by lazy {
        File(context.filesDir, "stickers").apply {
            if (!exists()) mkdirs()
        }
    }
    private val metaFile: File by lazy {
        File(context.filesDir, "stickers_metadata.json")
    }
    private val widgetPrefs by lazy {
        context.getSharedPreferences("widget_associations", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getAllStickers(): List<StickerItem> {
        if (!metaFile.exists()) return emptyList()
        return try {
            val json = metaFile.readText()
            val type = object : TypeToken<List<StickerItem>>() {}.type
            gson.fromJson<List<StickerItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun saveStickerMetadata(stickers: List<StickerItem>) {
        try {
            val json = gson.toJson(stickers)
            metaFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveStickerBitmap(
        bitmap: Bitmap,
        borderWidth: Float,
        borderColor: Long,
        hasShadow: Boolean,
        captionText: String,
        emojiSticker: String,
        style: String = "STICKER"
    ): StickerItem = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fileName = "sticker_${id}.png"
        val file = File(stickersDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val item = StickerItem(
            id = id,
            filePath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
            borderWidth = borderWidth,
            borderColor = borderColor,
            hasShadow = hasShadow,
            captionText = captionText,
            emojiSticker = emojiSticker,
            style = style
        )

        val currentList = getAllStickers().toMutableList()
        currentList.add(0, item) // add newest on top
        saveStickerMetadata(currentList)

        item
    }

    suspend fun deleteSticker(id: String) = withContext(Dispatchers.IO) {
        val currentList = getAllStickers().toMutableList()
        val item = currentList.find { it.id == id }
        if (item != null) {
            val file = File(item.filePath)
            if (file.exists()) file.delete()
            currentList.remove(item)
            saveStickerMetadata(currentList)
        }
    }

    fun getStickerById(id: String): StickerItem? {
        return getAllStickers().find { it.id == id }
    }

    fun loadBitmap(filePath: String): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun setWidgetStickerId(widgetId: Int, stickerId: String) {
        widgetPrefs.edit().putString("widget_$widgetId", stickerId).apply()
        // Also update associatedWidgetIds
        val stickers = getAllStickers().toMutableList()
        val index = stickers.indexOfFirst { it.id == stickerId }
        if (index != -1) {
            val item = stickers[index]
            if (!item.associatedWidgetIds.contains(widgetId)) {
                item.associatedWidgetIds.add(widgetId)
                saveStickerMetadata(stickers)
            }
        }
    }

    fun getStickerForWidget(widgetId: Int): StickerItem? {
        val stickerId = widgetPrefs.getString("widget_$widgetId", null) ?: return null
        return getStickerById(stickerId)
    }

    fun removeWidgetAssociation(widgetId: Int) {
        widgetPrefs.edit().remove("widget_$widgetId").apply()
    }
}
