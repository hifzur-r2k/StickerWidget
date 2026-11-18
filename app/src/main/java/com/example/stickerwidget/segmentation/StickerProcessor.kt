package com.example.stickerwidget.segmentation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StickerProcessor {

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    enum class CutoutClarity(val lowThreshold: Float, val highThreshold: Float) {
        SHARP(0.48f, 0.76f),       // Crisp silhouette, removes any fuzzy halos
        BALANCED(0.38f, 0.70f),    // Natural smooth hair & edges with anti-aliasing
        DETAILED(0.26f, 0.62f)     // Preserves fine strands and subtle edge details
    }

    /**
     * Extracts foreground subject from the bitmap using ML Kit Selfie Segmentation
     * with Bilinear Sub-Pixel Interpolation and Smoothstep Edge Matting for ultra-clear cutouts.
     */
    suspend fun removeBackground(
        inputBitmap: Bitmap,
        clarity: CutoutClarity = CutoutClarity.BALANCED
    ): Bitmap = withContext(Dispatchers.Default) {
        val scaledBitmap = scaleDownIfNeeded(inputBitmap, maxDimension = 1080)
        val image = InputImage.fromBitmap(scaledBitmap, 0)
        val mask = segmenter.process(image).await()

        val maskWidth = mask.width
        val maskHeight = mask.height
        val maskBuffer: ByteBuffer = mask.buffer
        maskBuffer.rewind()

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        // 1. Build a high-precision grayscale confidence bitmap from ML Kit buffer
        val rawMaskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val rawMaskPixels = IntArray(maskWidth * maskHeight)

        for (i in 0 until maskWidth * maskHeight) {
            val conf = maskBuffer.getFloat(i * 4).coerceIn(0f, 1f)
            val alphaByte = (conf * 255f).toInt()
            rawMaskPixels[i] = Color.argb(alphaByte, alphaByte, alphaByte, alphaByte)
        }
        rawMaskBitmap.setPixels(rawMaskPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        // 2. High-Quality Bilinear Upsampling of the mask to match the image dimensions
        // Eliminates blocky stair-stepping artifacts from 256x256 to full resolution
        val upscaledMaskBitmap = Bitmap.createScaledBitmap(rawMaskBitmap, width, height, true)
        val smoothedMaskPixels = IntArray(width * height)
        upscaledMaskBitmap.getPixels(smoothedMaskPixels, 0, width, 0, 0, width, height)

        rawMaskBitmap.recycle()
        upscaledMaskBitmap.recycle()

        // 3. Smoothstep S-curve matting for razor-sharp, natural edges
        val originalPixels = IntArray(width * height)
        scaledBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height)

        val outputPixels = IntArray(width * height)
        val lowEdge = clarity.lowThreshold
        val highEdge = clarity.highThreshold

        for (i in 0 until width * height) {
            val originalColor = originalPixels[i]
            val maskAlphaFloat = (Color.alpha(smoothedMaskPixels[i])) / 255f

            // Hermite Smoothstep S-curve
            val factor = smoothstep(lowEdge, highEdge, maskAlphaFloat)

            if (factor > 0.015f) {
                val origA = Color.alpha(originalColor) / 255f
                val finalAlpha = (origA * factor * 255f).toInt().coerceIn(0, 255)

                outputPixels[i] = Color.argb(
                    finalAlpha,
                    Color.red(originalColor),
                    Color.green(originalColor),
                    Color.blue(originalColor)
                )
            } else {
                outputPixels[i] = Color.TRANSPARENT
            }
        }

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
        cropTransparent(outputBitmap)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Builds a die-cut sticker with outline border, drop shadow, and optional text/emoji overlays
     */
    fun createStickerEffect(
        cutoutBitmap: Bitmap,
        borderWidth: Float = 18f,
        borderColor: Int = Color.WHITE,
        hasShadow: Boolean = true,
        captionText: String = "",
        emojiBadge: String = ""
    ): Bitmap {
        val padding = (borderWidth * 2.5f + (if (hasShadow) 24f else 8f)).toInt()
        val width = cutoutBitmap.width + padding * 2
        val height = cutoutBitmap.height + padding * 2

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val left = padding.toFloat()
        val top = padding.toFloat()

        // 1. Draw Drop Shadow with soft realistic falloff
        if (hasShadow) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(85, 20, 20, 25)
                maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
            }
            val alphaMask = cutoutBitmap.extractAlpha()
            canvas.drawBitmap(alphaMask, left + 8f, top + 12f, shadowPaint)
        }

        // 2. Draw Die-cut White / Colored Border with smooth multi-pass sub-pixel dilation
        if (borderWidth > 0) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                isDither = true
                isFilterBitmap = true
            }
            val alphaMask = cutoutBitmap.extractAlpha()

            // Continuous radial dilation passes to prevent concentric gaps or scalloping
            val numAngles = 36
            var step = 1.0f
            while (step <= borderWidth) {
                val r = step
                for (i in 0 until numAngles) {
                    val angle = (2.0 * Math.PI * i / numAngles)
                    val dx = (r * cos(angle)).toFloat()
                    val dy = (r * sin(angle)).toFloat()
                    canvas.drawBitmap(alphaMask, left + dx, top + dy, borderPaint)
                }
                step += 2.0f
            }

            // High-density boundary pass for razor-sharp contour
            val boundaryAngles = 48
            for (i in 0 until boundaryAngles) {
                val angle = (2.0 * Math.PI * i / boundaryAngles)
                val dx = (borderWidth * cos(angle)).toFloat()
                val dy = (borderWidth * sin(angle)).toFloat()
                canvas.drawBitmap(alphaMask, left + dx, top + dy, borderPaint)
            }
        }

        // 3. Draw original Cutout Subject
        val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.drawBitmap(cutoutBitmap, left, top, subjectPaint)

        // 4. Draw Caption Text if present
        if (captionText.isNotBlank()) {
            drawCaptionBadge(canvas, captionText, width, height)
        }

        // 5. Draw Emoji Badge if present
        if (emojiBadge.isNotBlank()) {
            drawEmojiBadge(canvas, emojiBadge, width, height)
        }

        return cropTransparent(resultBitmap)
    }

    /**
     * Builds an authentic Polaroid frame around a photo with bottom chin for custom handwritten caption/name
     */
    fun createPolaroidEffect(
        photoBitmap: Bitmap,
        captionText: String = "",
        paperColor: Int = Color.WHITE,
        textColor: Int = Color.parseColor("#212121"),
        hasShadow: Boolean = true,
        hasTape: Boolean = true
    ): Bitmap {
        val scaled = scaleDownIfNeeded(photoBitmap, maxDimension = 900)
        val photoW = scaled.width
        val photoH = scaled.height

        val marginSide = max(24, (photoW * 0.08f).toInt())
        val marginTop = max(24, (photoW * 0.08f).toInt())
        val marginBottom = max(90, (photoW * 0.28f).toInt()) // Taller chin for signature/caption

        val cardW = photoW + marginSide * 2
        val cardH = photoH + marginTop + marginBottom

        val shadowPad = if (hasShadow) 36 else 12
        val canvasW = cardW + shadowPad * 2
        val canvasH = cardH + shadowPad * 2

        val resultBitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val cardLeft = shadowPad.toFloat()
        val cardTop = shadowPad.toFloat()
        val cardRight = cardLeft + cardW
        val cardBottom = cardTop + cardH
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // 1. Realistic Drop Shadow
        if (hasShadow) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(85, 0, 0, 0)
                maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
            }
            val shadowRect = RectF(cardLeft + 6f, cardTop + 14f, cardRight + 6f, cardBottom + 14f)
            canvas.drawRoundRect(shadowRect, 14f, 14f, shadowPaint)
        }

        // 2. Draw Polaroid Photo Paper (Glossy/Matte Paper with subtle corner rounding)
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 12f, 12f, paperPaint)

        // 3. Subtle Paper Rim Edge
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(25, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, 12f, 12f, edgePaint)

        // 4. Draw Photo inside frame
        val photoLeft = cardLeft + marginSide
        val photoTop = cardTop + marginTop
        canvas.drawBitmap(scaled, photoLeft, photoTop, Paint(Paint.FILTER_BITMAP_FLAG))

        // Subtle inner shadow / border for the photo recess
        val photoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRect(
            RectF(photoLeft, photoTop, photoLeft + photoW, photoTop + photoH),
            photoBorderPaint
        )

        // 5. Draw Handwritten / Casual Caption on the Bottom Chin
        if (captionText.isNotBlank()) {
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = max(32f, photoW * 0.065f)
                textAlign = Paint.Align.CENTER
                // Use casual handwriting or fallback to bold sans/serif
                typeface = Typeface.create("casual", Typeface.BOLD)
                isAntiAlias = true
            }

            // Center vertically within the bottom chin area
            val chinCenterY = cardTop + marginTop + photoH + (marginBottom / 2f)
            val fontMetrics = captionPaint.fontMetrics
            val baseline = chinCenterY - (fontMetrics.ascent + fontMetrics.descent) / 2f

            canvas.drawText(captionText, cardRect.centerX(), baseline, captionPaint)
        }

        // 6. Cute decorative Washi Tape at the top center
        if (hasTape) {
            val tapeW = cardW * 0.32f
            val tapeH = max(24f, marginTop * 0.95f)
            val tapeLeft = cardRect.centerX() - tapeW / 2f
            val tapeTop = cardTop - tapeH * 0.45f
            val tapeRect = RectF(tapeLeft, tapeTop, tapeLeft + tapeW, tapeTop + tapeH)

            // Semi-transparent pastel washi tape
            val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(205, 255, 238, 88) // Warm pastel washi tape
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(tapeRect, 4f, 4f, tapePaint)

            val tapeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(35, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(tapeRect, 4f, 4f, tapeBorderPaint)
        }

        return cropTransparent(resultBitmap)
    }

    private fun drawCaptionBadge(canvas: Canvas, text: String, canvasWidth: Int, canvasHeight: Int) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val bgPaddingHoriz = 24f
        val bgPaddingVert = 12f
        val badgeWidth = textBounds.width() + bgPaddingHoriz * 2
        val badgeHeight = textBounds.height() + bgPaddingVert * 2

        val badgeLeft = (canvasWidth - badgeWidth) / 2f
        val badgeTop = canvasHeight - badgeHeight - 20f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E91E63") // Vibrant pill background
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        // Draw pill background & white outline
        canvas.drawRoundRect(badgeRect, 28f, 28f, bgPaint)
        canvas.drawRoundRect(badgeRect, 28f, 28f, borderPaint)

        // Draw text centered
        val fontMetrics = textPaint.fontMetrics
        val textY = badgeRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2
        canvas.drawText(text, badgeRect.centerX(), textY, textPaint)
    }

    private fun drawEmojiBadge(canvas: Canvas, emoji: String, canvasWidth: Int, canvasHeight: Int) {
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            textAlign = Paint.Align.CENTER
        }
        val x = canvasWidth - 60f
        val y = 80f
        canvas.drawText(emoji, x, y, emojiPaint)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun cropTransparent(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (pixels[y * width + x] ushr 24) and 0xFF
                if (alpha > 10) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return bitmap // Completely empty or no cutout found
        }

        val cropWidth = maxX - minX + 1
        val cropHeight = maxY - minY + 1

        return Bitmap.createBitmap(bitmap, minX, minY, cropWidth, cropHeight)
    }
}
