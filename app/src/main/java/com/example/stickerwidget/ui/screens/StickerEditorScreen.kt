package com.example.stickerwidget.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickerwidget.segmentation.StickerProcessor
import com.example.stickerwidget.ui.theme.AccentCyan
import com.example.stickerwidget.ui.theme.AccentPink
import com.example.stickerwidget.ui.theme.AccentYellow
import com.example.stickerwidget.ui.theme.StickerBorderColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EditorStyle {
    POLAROID,
    STICKER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerEditorScreen(
    initialBitmap: Bitmap,
    onBack: () -> Unit,
    onSaveAndMakeWidget: (Bitmap, Float, Long, Boolean, String, String, String) -> Unit,
    onSaveOnly: (Bitmap, Float, Long, Boolean, String, String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Mode State
    var selectedStyle by remember { mutableStateOf(EditorStyle.POLAROID) }

    // Common State
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var originalBitmap by remember { mutableStateOf(initialBitmap) }
    var cutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderedStickerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showWallpaperPreview by remember { mutableStateOf(false) }
    var hasShadow by remember { mutableStateOf(true) }

    // Polaroid-specific controls
    val todayDateStr = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
    }
    var polaroidCaption by remember { mutableStateOf("") }
    var polaroidPaperColor by remember { mutableStateOf(Color.White) }
    var polaroidHasTape by remember { mutableStateOf(true) }

    // Die-Cut Sticker-specific controls
    var cutoutClarity by remember { mutableStateOf(StickerProcessor.CutoutClarity.BALANCED) }
    var borderWidth by remember { mutableFloatStateOf(16f) }
    var borderColor by remember { mutableStateOf(Color.White) }
    var stickerCaptionText by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("") }

    // Re-render whenever options change
    LaunchedEffect(
        selectedStyle,
        originalBitmap,
        cutoutBitmap,
        polaroidCaption,
        polaroidPaperColor,
        polaroidHasTape,
        borderWidth,
        borderColor,
        hasShadow,
        stickerCaptionText,
        selectedEmoji
    ) {
        withContext(Dispatchers.Default) {
            if (selectedStyle == EditorStyle.POLAROID) {
                // Render Polaroid Frame
                val result = StickerProcessor.createPolaroidEffect(
                    photoBitmap = originalBitmap,
                    captionText = polaroidCaption,
                    paperColor = polaroidPaperColor.toArgb(),
                    textColor = if (polaroidPaperColor == Color(0xFF212121)) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#212121"),
                    hasShadow = hasShadow,
                    hasTape = polaroidHasTape
                )
                renderedStickerBitmap = result
            } else {
                // Render Die-Cut Sticker
                val target = cutoutBitmap ?: originalBitmap
                val result = StickerProcessor.createStickerEffect(
                    cutoutBitmap = target,
                    borderWidth = borderWidth,
                    borderColor = borderColor.toArgb(),
                    hasShadow = hasShadow,
                    captionText = stickerCaptionText,
                    emojiBadge = selectedEmoji
                )
                renderedStickerBitmap = result
            }
        }
    }

    // AI Cutout trigger with Sub-pixel Bilinear Matting
    fun performAiCutout(clarity: StickerProcessor.CutoutClarity = cutoutClarity) {
        coroutineScope.launch {
            isProcessing = true
            processingMessage = "AI generating ultra-clear sub-pixel cutout..."
            try {
                val cutout = StickerProcessor.removeBackground(originalBitmap, clarity)
                cutoutBitmap = cutout
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sticker & Frame Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showWallpaperPreview = !showWallpaperPreview }) {
                        Icon(
                            imageVector = if (showWallpaperPreview) Icons.Default.Wallpaper else Icons.Default.GridOn,
                            contentDescription = "Toggle Preview Background",
                            tint = if (showWallpaperPreview) AccentCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val targetBitmap = renderedStickerBitmap ?: originalBitmap
                            val caption = if (selectedStyle == EditorStyle.POLAROID) polaroidCaption else stickerCaptionText
                            val styleStr = if (selectedStyle == EditorStyle.POLAROID) "POLAROID" else "STICKER"
                            onSaveOnly(
                                targetBitmap,
                                borderWidth,
                                borderColor.value.toLong(),
                                hasShadow,
                                caption,
                                selectedEmoji,
                                styleStr
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }

                    Button(
                        onClick = {
                            val targetBitmap = renderedStickerBitmap ?: originalBitmap
                            val caption = if (selectedStyle == EditorStyle.POLAROID) polaroidCaption else stickerCaptionText
                            val styleStr = if (selectedStyle == EditorStyle.POLAROID) "POLAROID" else "STICKER"
                            onSaveAndMakeWidget(
                                targetBitmap,
                                borderWidth,
                                borderColor.value.toLong(),
                                hasShadow,
                                caption,
                                selectedEmoji,
                                styleStr
                            )
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Make Widget", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Style Selector (Polaroid Frame vs Die-Cut Sticker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedStyle == EditorStyle.POLAROID,
                    onClick = { selectedStyle = EditorStyle.POLAROID },
                    label = { Text("📷 Polaroid Frame", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentPink,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                FilterChip(
                    selected = selectedStyle == EditorStyle.STICKER,
                    onClick = { selectedStyle = EditorStyle.STICKER },
                    label = { Text("✂️ Die-Cut Sticker", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentPink,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (showWallpaperPreview) Color(0xFF192A56)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showWallpaperPreview) {
                    Text(
                        "Home Screen Preview",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    )
                }

                when {
                    isProcessing -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = AccentPink)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                processingMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    renderedStickerBitmap != null -> {
                        Image(
                            bitmap = renderedStickerBitmap!!.asImageBitmap(),
                            contentDescription = "Created Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    else -> {
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = "Original Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Controls for POLAROID FRAME
            if (selectedStyle == EditorStyle.POLAROID) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Polaroid Name & Caption",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = polaroidCaption,
                        onValueChange = { if (it.length <= 30) polaroidCaption = it },
                        label = { Text("Write name or small caption") },
                        placeholder = { Text("e.g. Best Days, Vacation '26, Alex & Emma") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick suggestion chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { polaroidCaption = todayDateStr },
                            label = { Text("📅 $todayDateStr", fontSize = 12.sp) }
                        )
                        SuggestionChip(
                            onClick = { polaroidCaption = "✨ Happy Memories" },
                            label = { Text("✨ Happy Memories", fontSize = 12.sp) }
                        )
                        SuggestionChip(
                            onClick = { polaroidCaption = "❤️ Best Friends" },
                            label = { Text("❤️ Best Friends", fontSize = 12.sp) }
                        )
                        SuggestionChip(
                            onClick = { polaroidCaption = "🌴 Vacation Mode" },
                            label = { Text("🌴 Vacation Mode", fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Paper Color Selector
                    Text("Paper Frame Tint", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val paperOptions = listOf(
                        "Classic White" to Color.White,
                        "Vintage Cream" to Color(0xFFFDFBF7),
                        "Pastel Pink" to Color(0xFFFFF0F5),
                        "Retro Noir" to Color(0xFF212121)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paperOptions.forEach { (name, color) ->
                            FilterChip(
                                selected = polaroidPaperColor == color,
                                onClick = { polaroidPaperColor = color },
                                label = { Text(name, fontSize = 13.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tape & Shadow toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Top Washi Tape", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Adds decorative yellow tape at top", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = polaroidHasTape,
                            onCheckedChange = { polaroidHasTape = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentPink)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Photo Depth Shadow", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Soft realistic shadow on wallpaper", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = hasShadow,
                            onCheckedChange = { hasShadow = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentPink)
                        )
                    }
                }
            } else {
                // Controls for DIE-CUT STICKER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    if (cutoutBitmap == null && !isProcessing) {
                        Button(
                            onClick = { performAiCutout() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Create Sticker (AI Remove Background)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (cutoutBitmap != null) {
                        // Clarity & Precision tuning chips
                        Text("Cutout Edge Clarity", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "🎯 Sharp (Crisp Edges)" to StickerProcessor.CutoutClarity.SHARP,
                                "✨ Balanced" to StickerProcessor.CutoutClarity.BALANCED,
                                "🌿 Detailed (Fine Hair)" to StickerProcessor.CutoutClarity.DETAILED
                            ).forEach { (label, clarity) ->
                                FilterChip(
                                    selected = cutoutClarity == clarity,
                                    onClick = {
                                        cutoutClarity = clarity
                                        performAiCutout(clarity)
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Border Width Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sticker Border Thickness", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${borderWidth.toInt()} px", color = AccentPink, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = borderWidth,
                        onValueChange = { borderWidth = it },
                        valueRange = 0f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPink,
                            activeTrackColor = AccentPink
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Border Color Palette
                    Text("Border Color", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StickerBorderColors.forEach { color ->
                            val isSelected = borderColor == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) AccentPink else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { borderColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Drop Shadow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("3D Die-Cut Shadow", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Realistic sticker depth", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = hasShadow,
                            onCheckedChange = { hasShadow = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentPink)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Caption badge
                    OutlinedTextField(
                        value = stickerCaptionText,
                        onValueChange = { if (it.length <= 20) stickerCaptionText = it },
                        label = { Text("Sticker Text Badge (Optional)") },
                        placeholder = { Text("e.g. Mood, Besties, Star") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Emoji Badges
                    Text("Sticker Emoji Badge", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val emojiOptions = listOf("None", "⭐", "❤️", "🔥", "😎", "✨", "🚀", "👑", "🐾")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojiOptions.forEach { emoji ->
                            val actualEmoji = if (emoji == "None") "" else emoji
                            FilterChip(
                                selected = selectedEmoji == actualEmoji,
                                onClick = { selectedEmoji = actualEmoji },
                                label = { Text(emoji, fontSize = if (emoji == "None") 12.sp else 16.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
