package com.example.stickerwidget.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickerwidget.data.StickerItem
import com.example.stickerwidget.ui.theme.AccentCyan
import com.example.stickerwidget.ui.theme.AccentPink
import com.example.stickerwidget.ui.theme.AccentYellow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    stickers: List<StickerItem>,
    onTakePhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit,
    onStickerClick: (StickerItem) -> Unit,
    onPinWidgetClick: (StickerItem) -> Unit,
    onDeleteStickerClick: (StickerItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "StickerWidget",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentPink.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "AI CUTOUT",
                                color = AccentPink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Action Hero Cards (Take Photo & Choose from Gallery)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera Button
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clickable { onTakePhotoClick() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Take Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Gallery Button
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clickable { onPickPhotoClick() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Choose Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "My Sticker Widgets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stickers.size} stickers",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (stickers.isEmpty()) {
                // Empty state illustration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = AccentPink
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Stickers Yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Click a photo with your camera or pick one from gallery. Our AI will cut out the subject into a sticker and you can pin it directly to your Home Screen as a widget!",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(stickers, key = { it.id }) { item ->
                        StickerCard(
                            item = item,
                            onClick = { onStickerClick(item) },
                            onPinWidget = { onPinWidgetClick(item) },
                            onDelete = { onDeleteStickerClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StickerCard(
    item: StickerItem,
    onClick: () -> Unit,
    onPinWidget: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Sticker Preview with subtle checkerboard-like border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                val file = remember(item.filePath) { File(item.filePath) }
                if (file.exists()) {
                    val bitmap = remember(item.filePath) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Sticker",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            if (item.captionText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (item.style == "POLAROID") "📷 ${item.captionText}" else item.captionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions row: Pin to Home & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPinWidget,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentPink.copy(alpha = 0.15f),
                        contentColor = AccentPink
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "Pin Widget",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
