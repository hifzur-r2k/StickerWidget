package com.example.stickerwidget.data

import java.util.UUID

data class StickerItem(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val originalFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val borderWidth: Float = 16f,
    val borderColor: Long = 0xFFFFFFFF,
    val hasShadow: Boolean = true,
    val captionText: String = "",
    val emojiSticker: String = "",
    val style: String = "STICKER", // "STICKER" or "POLAROID"
    val associatedWidgetIds: MutableList<Int> = mutableListOf()
)
