package com.souravgoswami.framefinder.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.souravgoswami.framefinder.data.IndexedPhotoMetadata
import com.souravgoswami.framefinder.data.PhotoGridItem
import com.souravgoswami.framefinder.media.PhotoFileMetadata
import java.text.DateFormat
import java.util.Date
import kotlin.math.min

@Composable
fun PhotoViewerScreen(
    photos: List<PhotoGridItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onShare: (PhotoGridItem) -> Unit,
    onSave: (PhotoGridItem) -> Unit,
    onOpenGallery: (PhotoGridItem) -> Unit,
    onLoadMetadata: suspend (Long) -> IndexedPhotoMetadata?,
    onLoadFileMetadata: suspend (PhotoGridItem) -> PhotoFileMetadata,
    canMoveToTrash: Boolean,
    onTrash: (PhotoGridItem) -> Unit,
    onDelete: (PhotoGridItem) -> Unit
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var currentPhotoZoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showFileMetadata by remember { mutableStateOf(false) }
    var showSearchAnalysis by remember { mutableStateOf(false) }
    var removalAction by remember { mutableStateOf<ViewerRemovalAction?>(null) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size }
    )
    val photo = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !currentPhotoZoomed
        ) { page ->
            ZoomablePhoto(
                photo = photos[page],
                onTap = { controlsVisible = !controlsVisible },
                onTransform = { controlsVisible = false },
                onZoomChanged = { zoomed -> currentPhotoZoomed = zoomed }
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.58f)).statusBarsPadding().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    photo.displayName,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.62f))
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val details = listOfNotNull(
                    photo.width?.let { width -> photo.height?.let { height -> "$width × $height" } },
                    photo.mimeType,
                    photo.sizeBytes.takeIf { it > 0L }?.let { Formatter.formatShortFileSize(context, it) }
                ).joinToString("  •  ")
                if (details.isNotEmpty()) {
                    Text(details, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${pagerState.currentPage + 1} of ${photos.size}",
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ViewerAction(Icons.Outlined.Share, "Share", { onShare(photo) }, Modifier.weight(1f))
                    ViewerAction(Icons.Outlined.SaveAlt, "Save", { onSave(photo) }, Modifier.weight(1f))
                    ViewerAction(Icons.Outlined.PhotoLibrary, "Gallery", { onOpenGallery(photo) }, Modifier.weight(1f))
                    ViewerAction(Icons.Outlined.Info, "Info", { showInfo = true }, Modifier.weight(1f))
                    ViewerAction(
                        Icons.Outlined.DeleteOutline,
                        if (canMoveToTrash) "Trash" else "Delete",
                        {
                            removalAction = if (canMoveToTrash) {
                                ViewerRemovalAction.TRASH
                            } else {
                                ViewerRemovalAction.DELETE
                            }
                        },
                        Modifier.weight(1f),
                        tint = if (canMoveToTrash) Color.White else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showInfo) {
        PhotoInfoDialog(
            photo = photo,
            onShowFileMetadata = {
                showInfo = false
                showFileMetadata = true
            },
            onShowSearchAnalysis = {
                showInfo = false
                showSearchAnalysis = true
            },
            onDismiss = { showInfo = false }
        )
    }
    if (showFileMetadata) {
        PhotoFileMetadataDialog(
            photo = photo,
            onLoadMetadata = onLoadFileMetadata,
            onDismiss = { showFileMetadata = false }
        )
    }
    if (showSearchAnalysis) {
        IndexedMetadataDialog(
            photo = photo,
            onLoadMetadata = onLoadMetadata,
            onDismiss = { showSearchAnalysis = false }
        )
    }
    removalAction?.let { action ->
        AlertDialog(
            onDismissRequest = { removalAction = null },
            title = {
                Text(
                    if (action == ViewerRemovalAction.TRASH) "Move photo to trash?"
                    else "Delete photo permanently?"
                )
            },
            text = {
                Text(
                    if (action == ViewerRemovalAction.TRASH) {
                        "${photo.displayName} can be restored from your gallery app's trash."
                    } else {
                        "This removes ${photo.displayName} from your device. This action cannot be undone."
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { removalAction = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removalAction = null
                        if (action == ViewerRemovalAction.TRASH) onTrash(photo) else onDelete(photo)
                    }
                ) {
                    Text(
                        if (action == ViewerRemovalAction.TRASH) "Move to trash" else "Delete",
                        color = if (action == ViewerRemovalAction.DELETE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        )
    }
}

private enum class ViewerRemovalAction { TRASH, DELETE }

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ZoomablePhoto(
    photo: PhotoGridItem,
    onTap: () -> Unit,
    onTransform: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember(photo.mediaId) { mutableFloatStateOf(1f) }
    var offset by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
    var viewport by remember(photo.mediaId) { mutableStateOf(IntSize.Zero) }
    var sourceSize by remember(photo.mediaId) {
        mutableStateOf(IntSize(photo.width ?: 0, photo.height ?: 0))
    }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (zoomChange != 1f || panChange != Offset.Zero) currentOnTransform()
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        currentOnZoomChanged(newScale > NORMAL_SCALE_EPSILON)
        offset = if (newScale == 1f) {
            Offset.Zero
        } else {
            constrainPhotoOffset(offset + panChange, newScale, viewport, sourceSize)
        }
    }
    AsyncImage(
        model = Uri.parse(photo.contentUri),
        contentDescription = photo.displayName,
        onSuccess = { state ->
            val drawable = state.result.drawable
            val decodedSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
            if (decodedSize.width > 0 && decodedSize.height > 0 && decodedSize != sourceSize) {
                sourceSize = decodedSize
                offset = constrainPhotoOffset(offset, scale, viewport, decodedSize)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewport = it
                offset = constrainPhotoOffset(offset, scale, viewport, sourceSize)
            }
            .pointerInput(photo.mediaId) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = {
                        currentOnTransform()
                        val newScale = if (scale > 1f) 1f else 2.5f
                        scale = newScale
                        currentOnZoomChanged(newScale > NORMAL_SCALE_EPSILON)
                        offset = Offset.Zero
                    }
                )
            }
            .transformable(
                state = transformState,
                canPan = { scale > 1f },
                lockRotationOnZoomPan = true
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentScale = ContentScale.Fit
    )
}

private const val NORMAL_SCALE_EPSILON = 1.001f

private fun constrainPhotoOffset(
    candidate: Offset,
    scale: Float,
    viewport: IntSize,
    source: IntSize
): Offset {
    if (scale <= 1f || viewport == IntSize.Zero) return Offset.Zero
    val fittedWidth: Float
    val fittedHeight: Float
    if (source.width > 0 && source.height > 0) {
        val fitScale = min(
            viewport.width.toFloat() / source.width,
            viewport.height.toFloat() / source.height
        )
        fittedWidth = source.width * fitScale
        fittedHeight = source.height * fitScale
    } else {
        fittedWidth = viewport.width.toFloat()
        fittedHeight = viewport.height.toFloat()
    }
    val maximumX = ((fittedWidth * scale - viewport.width) / 2f).coerceAtLeast(0f)
    val maximumY = ((fittedHeight * scale - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(
        candidate.x.coerceIn(-maximumX, maximumX),
        candidate.y.coerceIn(-maximumY, maximumY)
    )
}

@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PhotoInfoDialog(
    photo: PhotoGridItem,
    onShowFileMetadata: () -> Unit,
    onShowSearchAnalysis: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo info") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoRow("Name", photo.displayName)
                photo.dateTaken?.let { InfoRow("Taken", formatTimestamp(it)) }
                InfoRow("Added", formatTimestamp(photo.dateAdded))
                if (photo.dateModified > 0L) {
                    InfoRow("Modified", formatTimestamp(photo.dateModified * 1_000L))
                }
                InfoRow(
                    "Dimensions",
                    photo.width?.let { width -> photo.height?.let { height -> "$width × $height" } } ?: "Unknown"
                )
                InfoRow(
                    "File size",
                    photo.sizeBytes.takeIf { it > 0L }?.let { Formatter.formatShortFileSize(context, it) } ?: "Unknown"
                )
                InfoRow("Type", photo.mimeType ?: "Unknown")
                InfoRow(
                    "Storage location",
                    photo.storagePath?.trimEnd('/')?.let { "$it/${photo.displayName}" }
                        ?: "Storage location unavailable"
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShowSearchAnalysis) { Text("Search analysis") }
                TextButton(onClick = onShowFileMetadata) { Text("Metadata") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun PhotoFileMetadataDialog(
    photo: PhotoGridItem,
    onLoadMetadata: suspend (PhotoGridItem) -> PhotoFileMetadata,
    onDismiss: () -> Unit
) {
    var loading by remember(photo.mediaId) { mutableStateOf(true) }
    var metadata by remember(photo.mediaId) { mutableStateOf<PhotoFileMetadata?>(null) }

    LaunchedEffect(photo.mediaId) {
        metadata = onLoadMetadata(photo)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo metadata") },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                metadata == null -> Text("Camera metadata could not be read from this photo.")
                metadata!!.entries.isEmpty() -> Text(metadata!!.message.orEmpty())
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    metadata!!.entries.forEach { entry -> InfoRow(entry.label, entry.value) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun IndexedMetadataDialog(
    photo: PhotoGridItem,
    onLoadMetadata: suspend (Long) -> IndexedPhotoMetadata?,
    onDismiss: () -> Unit
) {
    var loading by remember(photo.mediaId) { mutableStateOf(true) }
    var metadata by remember(photo.mediaId) { mutableStateOf<IndexedPhotoMetadata?>(null) }
    var loadError by remember(photo.mediaId) { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.mediaId) {
        runCatching { onLoadMetadata(photo.mediaId) }
            .onSuccess { metadata = it }
            .onFailure { loadError = it.message ?: "Metadata could not be loaded." }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search analysis") },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadError != null -> Text(loadError.orEmpty())
                metadata == null -> Text("No search analysis is available for this photo.")
                else -> IndexedMetadataContent(metadata!!)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun IndexedMetadataContent(metadata: IndexedPhotoMetadata) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        InfoRow("Media description", metadata.description?.takeIf(String::isNotBlank) ?: "None")

        val textStatus = when {
            metadata.textAnalysisError != null -> "Analysis failed. ${metadata.textAnalysisError}"
            metadata.textAnalyzedAt != null -> "Analyzed ${formatTimestamp(metadata.textAnalyzedAt)}"
            else -> "Not analyzed"
        }
        InfoRow("Text recognition", textStatus)
        metadata.recognizedText?.takeIf(String::isNotBlank)?.let { recognizedText ->
            Text(
                "Recognized text",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SelectionContainer {
                Text(recognizedText, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "AI analysis with selected model",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        val model = metadata.aiModel
        val status = when {
            model.analyzed -> model.analyzedAt?.let { "Analyzed ${formatTimestamp(it)}" } ?: "Analyzed"
            model.error != null -> "Analysis failed. ${model.error}"
            else -> "Not analyzed"
        }
        InfoRow(model.modelName, status)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Text(value, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
}

private fun formatTimestamp(value: Long): String {
    if (value <= 0L) return "Unknown"
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
    }.getOrDefault("Unknown")
}
