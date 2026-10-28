package com.souravgoswami.framefinder.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.souravgoswami.framefinder.data.PhotoGridItem
import com.souravgoswami.framefinder.data.TrashedPhoto
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun TrashScreen(
    photos: List<TrashedPhoto>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (List<PhotoGridItem>) -> Unit,
    onDeletePermanently: (List<PhotoGridItem>) -> Unit
) {
    val selected = remember { mutableStateMapOf<Long, PhotoGridItem>() }
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    LaunchedEffect(photos) {
        val availableIds = photos.mapTo(HashSet()) { it.photo.mediaId }
        selected.keys.toList().filterNot(availableIds::contains).forEach(selected::remove)
    }
    BackHandler {
        if (selected.isNotEmpty()) selected.clear() else onBack()
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Trash",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (photos.isNotEmpty() && !loading) {
                TextButton(
                    onClick = {
                        if (selected.size == photos.size) selected.clear()
                        else photos.forEach { selected[it.photo.mediaId] = it.photo }
                    }
                ) {
                    Text(if (selected.size == photos.size) "Clear" else "Select all")
                }
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh trash")
            }
        }
        Text(
            "Android hides these photos from normal galleries until they are restored or permanently removed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (selected.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${selected.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UtilityButton(
                            text = "Restore",
                            onClick = {
                                val chosen = selected.values.toList()
                                selected.clear()
                                onRestore(chosen)
                            },
                            modifier = Modifier.weight(1f),
                            primary = true
                        )
                        UtilityButton(
                            text = "Delete permanently",
                            onClick = { confirmPermanentDelete = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        when {
            loading && photos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && photos.isEmpty() -> EmptyState(
                title = "Could not open trash",
                body = error,
                action = "Try again",
                onAction = onRefresh,
                modifier = Modifier.fillMaxSize()
            )
            photos.isEmpty() -> EmptyState(
                title = "Trash is empty",
                body = "Photos moved to Android trash will appear here.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(photos, key = { it.photo.mediaId }) { item ->
                    TrashPhotoTile(
                        item = item,
                        selected = item.photo.mediaId in selected,
                        onClick = {
                            if (item.photo.mediaId in selected) selected.remove(item.photo.mediaId)
                            else selected[item.photo.mediaId] = item.photo
                        }
                    )
                }
            }
        }
    }

    if (confirmPermanentDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmPermanentDelete = false },
            title = { Text("Delete $count photos permanently?") },
            text = {
                Text("These files will be permanently deleted from your device. This cannot be undone.")
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentDelete = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = selected.values.toList()
                        confirmPermanentDelete = false
                        selected.clear()
                        onDeletePermanently(chosen)
                    }
                ) {
                    Text("Delete permanently", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun TrashPhotoTile(item: TrashedPhoto, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.aspectRatio(0.82f).clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = Uri.parse(item.photo.contentUri),
                contentDescription = item.photo.displayName,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop
            )
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(27.dp)
                )
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.62f)).padding(6.dp)
            ) {
                Text(
                    item.photo.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    expiryLabel(item.expiresAt),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun expiryLabel(expiresAt: Long?): String {
    expiresAt ?: return "Expiry set by Android"
    val remaining = expiresAt - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(remaining.coerceAtLeast(0L))
    return when {
        remaining <= 0L -> "Scheduled for removal"
        days == 0L -> "Deletes today"
        days == 1L -> "Deletes in 1 day"
        days < 14L -> "Deletes in $days days"
        else -> "Deletes ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiresAt))}"
    }
}
