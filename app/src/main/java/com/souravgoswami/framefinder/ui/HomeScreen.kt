package com.souravgoswami.framefinder.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.souravgoswami.framefinder.data.LaunchView
import com.souravgoswami.framefinder.data.GalleryGrouping
import com.souravgoswami.framefinder.data.GalleryItem
import com.souravgoswami.framefinder.data.PhotoGridItem
import com.souravgoswami.framefinder.data.PhotoLayout
import com.souravgoswami.framefinder.data.ResultSort
import com.souravgoswami.framefinder.data.SearchMatchReason
import com.souravgoswami.framefinder.data.ThumbnailDensity
import com.souravgoswami.framefinder.data.groupedGalleryItems
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    state: FrameFinderUiState,
    gallery: LazyPagingItems<GalleryItem>?,
    galleryGridState: LazyGridState,
    searchGridState: LazyGridState,
    galleryListState: LazyListState,
    searchListState: LazyListState,
    hasPhotoAccess: Boolean,
    onRequestPhotoAccess: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (ResultSort) -> Unit,
    onPhotoLayout: (PhotoLayout) -> Unit,
    onThumbnailDensity: (ThumbnailDensity) -> Unit,
    onGalleryGrouping: (GalleryGrouping) -> Unit,
    onSearchStrictness: (Int) -> Unit,
    onVisualSearch: (Boolean) -> Unit,
    onFilenameSearch: (Boolean) -> Unit,
    onDescriptionSearch: (Boolean) -> Unit,
    onIgnoreFailedPhotos: (Boolean) -> Unit,
    onOcr: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhotos: (List<PhotoGridItem>, Int) -> Unit,
    onSharePhotos: (List<PhotoGridItem>) -> Unit,
    canMoveToTrash: Boolean,
    onTrashPhotos: (List<PhotoGridItem>) -> Unit,
    onDeletePhotos: (List<PhotoGridItem>) -> Unit,
    onPrepareAndIndex: () -> Unit,
    onImportAnalysis: () -> Unit,
    onAnalyzeAll: () -> Unit,
    onPauseWork: () -> Unit,
    onCancelScan: () -> Unit,
    onRefreshContent: () -> Unit
) {
    val selectedPhotos = remember { mutableStateMapOf<Long, PhotoGridItem>() }
    var batchRemovalAction by remember { mutableStateOf<PhotoRemovalAction?>(null) }
    val selectionMode = selectedPhotos.isNotEmpty()
    val selectedPhotoIds = selectedPhotos.keys.toSet()
    val toggleSelection: (PhotoGridItem) -> Unit = { photo ->
        if (selectedPhotos.containsKey(photo.mediaId)) selectedPhotos.remove(photo.mediaId)
        else selectedPhotos[photo.mediaId] = photo
    }
    val searchItems = remember(state.results, state.preferences.galleryGrouping) {
        groupedGalleryItems(
            state.results.map { it.photo },
            state.preferences.galleryGrouping
        )
    }
    val searchPhotos = remember(searchItems) {
        searchItems.mapNotNull { (it as? GalleryItem.Photo)?.photo }
    }
    val matchReasons = remember(state.results) {
        state.results.associate { it.photo.mediaId to it.matchReasons }
    }
    val thumbnailSize = thumbnailSize(state.preferences.thumbnailDensity)
    val incompleteCounts = incompleteAnalysisCounts(
        missingEmbeddingCount = state.missingEmbeddingCount,
        embeddingErrorCount = state.embeddingErrorCount,
        missingOcrCount = state.missingOcrCount,
        ocrErrorCount = state.ocrErrorCount
    )
    val knownFailureCount = incompleteCounts.knownFailureCount
    val missingAiCount = incompleteCounts.missingAiCount
    val missingTextCount = incompleteCounts.missingTextCount
    val postScanNotificationsAvailable = state.indexStatusLoaded &&
        state.indexedCount > 0 &&
        !state.work.running &&
        !state.preferences.scanPaused
    val hasNewPhotosNotification = postScanNotificationsAvailable && state.newPhotosAvailable
    val hasIncompleteAnalysisNotification = postScanNotificationsAvailable &&
        (missingAiCount > 0 || missingTextCount > 0)
    val notificationCount = listOf(
        hasNewPhotosNotification,
        hasIncompleteAnalysisNotification
    ).count { it }
    var showNotifications by remember { mutableStateOf(false) }
    LaunchedEffect(state.query, state.preferences.launchView) {
        selectedPhotos.clear()
    }
    BackHandler(enabled = selectionMode) { selectedPhotos.clear() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Frame Finder", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Find a memory, not a filename",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgedBox(
                    badge = {
                        if (notificationCount > 0) {
                            Badge { Text(notificationCount.toString()) }
                        }
                    }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = if (notificationCount > 0) {
                                    "$notificationCount notifications"
                                } else {
                                    "Notifications"
                                }
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasPhotoAccess && state.indexedCount > 0 && !selectionMode,
            singleLine = true,
            placeholder = { Text("Try “receipt total” or “a cat sleeping”") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                    SearchOptionsMenu(
                        searchStrictness = state.preferences.searchStrictness,
                        visualSearchEnabled = state.preferences.visualSearchEnabled,
                        filenameSearchEnabled = state.preferences.filenameSearchEnabled,
                        descriptionSearchEnabled = state.preferences.descriptionSearchEnabled,
                        ocrEnabled = state.preferences.ocrSearchEnabled,
                        onSearchStrictness = onSearchStrictness,
                        onVisualSearch = onVisualSearch,
                        onFilenameSearch = onFilenameSearch,
                        onDescriptionSearch = onDescriptionSearch,
                        onOcr = onOcr
                    )
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
        ActiveSearchSources(
            visualEnabled = state.preferences.visualSearchEnabled,
            filenameEnabled = state.preferences.filenameSearchEnabled,
            descriptionEnabled = state.preferences.descriptionSearchEnabled,
            ocrEnabled = state.preferences.ocrSearchEnabled
        )

        if (state.work.running) {
            Spacer(Modifier.height(12.dp))
            WorkProgressCard(state.work, onPauseWork)
        } else if (state.preferences.scanPaused && state.indexedCount > 0) {
            Spacer(Modifier.height(12.dp))
            PausedWorkCard(
                onResume = onPrepareAndIndex,
                onCancel = onCancelScan,
                newPhotosAvailable = state.newPhotosAvailable
            )
        } else if (state.work.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                state.work.error,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        when {
            !hasPhotoAccess -> EmptyState(
                title = "Choose the photos Frame Finder can search",
                body = "Frame Finder reads only the photos you allow. Android 14 also lets you grant access to selected photos.",
                action = "Allow photo access",
                onAction = onRequestPhotoAccess,
                modifier = Modifier.fillMaxSize()
            )
            !state.indexStatusLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.modelReady && !state.work.running -> EmptyState(
                title = "Bundled AI model unavailable",
                body = "Frame Finder could not open ${state.preferences.searchModel.displayName} from this installation. Reinstall the complete APK and try again.",
                action = "Try again",
                onAction = onPrepareAndIndex,
                modifier = Modifier.fillMaxSize()
            )
            state.indexedCount == 0 && !state.work.running -> EmptyState(
                title = if (state.preferences.scanPaused) "Scan is paused" else "No photos indexed yet",
                body = if (state.preferences.scanPaused) {
                    "Resume scanning to make photos searchable. Nothing already stored will be discarded."
                } else {
                    "Build a local search index. Frame Finder recognizes visible text and analyzes small thumbnails while keeping original photos untouched."
                },
                action = if (state.preferences.scanPaused) "Resume scan" else "Index photos",
                onAction = onPrepareAndIndex,
                actionEnabled = !state.analysisBackupRunning,
                secondaryAction = if (state.preferences.scanPaused) "Cancel scan" else null,
                onSecondaryAction = if (state.preferences.scanPaused) onCancelScan else null,
                alternativeAction = if (state.preferences.scanPaused) null else "Import analysis",
                onAlternativeAction = if (state.preferences.scanPaused) null else onImportAnalysis,
                alternativeBody = if (state.preferences.scanPaused) {
                    null
                } else {
                    "Restore OCR and AI analysis from a Frame Finder backup. Original photos are not included, so the matching photos must already be available on this device."
                },
                busy = state.analysisBackupRunning,
                statusMessage = state.analysisBackupStatus,
                errorMessage = state.analysisBackupError,
                modifier = Modifier.fillMaxSize()
            )
            else -> {
                val visibleCount = state.indexedCount
                if (state.query.isBlank() && state.preferences.launchView == LaunchView.SEARCH) {
                    EmptyState(
                        title = "What would you like to find?",
                        body = "$visibleCount indexed photos are ready. Search for an object, scene, date, or any enabled text source.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    if (selectionMode) {
                        SelectionBar(
                            selectedCount = selectedPhotos.size,
                            onCancel = selectedPhotos::clear,
                            onShare = {
                                val photos = selectedPhotos.values.toList()
                                selectedPhotos.clear()
                                onSharePhotos(photos)
                            },
                            canMoveToTrash = canMoveToTrash,
                            onTrash = { batchRemovalAction = PhotoRemovalAction.TRASH },
                            onDelete = { batchRemovalAction = PhotoRemovalAction.DELETE }
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (state.query.isBlank()) "$visibleCount photos" else "${state.results.size} matches",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SortMenu(
                                selected = state.sort,
                                searching = state.query.isNotBlank(),
                                grouping = state.preferences.galleryGrouping,
                                onSelected = onSortChange
                            )
                            GalleryDisplayMenu(
                                layout = state.preferences.photoLayout,
                                density = state.preferences.thumbnailDensity,
                                grouping = state.preferences.galleryGrouping,
                                onLayoutSelected = onPhotoLayout,
                                onDensitySelected = onThumbnailDensity,
                                onGroupingSelected = onGalleryGrouping
                            )
                        }
                    }
                    val galleryRefreshing = state.query.isBlank() && gallery != null &&
                        gallery.loadState.refresh is LoadState.Loading && gallery.itemCount > 0
                    TouchPullToRefreshBox(
                        isRefreshing = galleryRefreshing || state.contentRefreshing,
                        onRefresh = {
                            if (!selectionMode) {
                                if (state.query.isBlank()) gallery?.refresh()
                                onRefreshContent()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                    if (state.query.isBlank() && gallery == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (
                        state.query.isBlank() && gallery != null &&
                        gallery.loadState.refresh is LoadState.Loading && gallery.itemCount == 0
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (
                        state.query.isBlank() && gallery != null &&
                        gallery.loadState.refresh is LoadState.Error
                    ) {
                        EmptyState(
                            title = "Could not load the photo index",
                            body = (gallery.loadState.refresh as LoadState.Error).error.message
                                ?: "The local photo index could not be read.",
                            action = "Try again",
                            onAction = gallery::retry,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (state.query.isBlank() && gallery != null) {
                        when (state.preferences.photoLayout) {
                            PhotoLayout.GRID -> PagedPhotoGrid(
                                gallery,
                                galleryGridState,
                                selectedPhotoIds,
                                selectionMode,
                                thumbnailSize,
                                toggleSelection,
                                onOpenPhotos
                            )
                            PhotoLayout.LIST -> PagedPhotoList(
                                gallery,
                                galleryListState,
                                selectedPhotoIds,
                                selectionMode,
                                toggleSelection,
                                onOpenPhotos
                            )
                        }
                    } else if (state.searching && !state.contentRefreshing) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (state.results.isEmpty()) {
                        EmptyState(
                            title = "No close matches",
                            body = "Try a shorter query, a month and year, a different visual detail, or enable another search source.",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        when (state.preferences.photoLayout) {
                            PhotoLayout.GRID -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(thumbnailSize),
                                state = searchGridState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                itemsIndexed(
                                    items = searchItems,
                                    key = { _, item -> galleryItemKey(item) },
                                    span = { _, item ->
                                        if (item is GalleryItem.Header) GridItemSpan(maxLineSpan)
                                        else GridItemSpan(1)
                                    }
                                ) { _, item ->
                                    when (item) {
                                        is GalleryItem.Header -> GalleryHeader(item.label)
                                        is GalleryItem.Photo -> PhotoTile(
                                            photo = item.photo,
                                            matchReasons = matchReasons[item.photo.mediaId].orEmpty(),
                                            selected = item.photo.mediaId in selectedPhotoIds,
                                            onClick = {
                                                if (selectionMode) toggleSelection(item.photo)
                                                else openSearchPhoto(searchPhotos, item.photo, onOpenPhotos)
                                            },
                                            onLongClick = { toggleSelection(item.photo) }
                                        )
                                    }
                                }
                            }
                            PhotoLayout.LIST -> LazyColumn(
                                state = searchListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listItemsIndexed(
                                    searchItems,
                                    key = { _, item -> galleryItemKey(item) }
                                ) { _, item ->
                                    when (item) {
                                        is GalleryItem.Header -> GalleryHeader(item.label)
                                        is GalleryItem.Photo -> PhotoListRow(
                                            photo = item.photo,
                                            matchReasons = matchReasons[item.photo.mediaId].orEmpty(),
                                            selected = item.photo.mediaId in selectedPhotoIds,
                                            onClick = {
                                                if (selectionMode) toggleSelection(item.photo)
                                                else openSearchPhoto(searchPhotos, item.photo, onOpenPhotos)
                                            },
                                            onLongClick = { toggleSelection(item.photo) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Notifications") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (notificationCount == 0) {
                        Text(
                            "You are all caught up.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasNewPhotosNotification) {
                        NewPhotosCard(
                            onIndex = {
                                showNotifications = false
                                onPrepareAndIndex()
                            }
                        )
                    }
                    if (hasIncompleteAnalysisNotification) {
                        IncompleteAnalysisCard(
                            modelName = state.preferences.searchModel.displayName,
                            missingAiCount = missingAiCount,
                            missingTextCount = missingTextCount,
                            hasFailedPhotos = knownFailureCount > 0,
                            ignoreFailedPhotos = state.preferences.ignoreFailedPhotos,
                            onIgnoreFailedPhotos = onIgnoreFailedPhotos,
                            onAnalyzeAll = {
                                showNotifications = false
                                onAnalyzeAll()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifications = false }) { Text("Close") }
            }
        )
    }

    batchRemovalAction?.let { removalAction ->
        val count = selectedPhotos.size
        val selectionLabel = if (count == 1) "selected photo" else "$count selected photos"
        AlertDialog(
            onDismissRequest = { batchRemovalAction = null },
            title = {
                Text(
                    if (removalAction == PhotoRemovalAction.TRASH) "Move $selectionLabel to trash?"
                    else "Delete $selectionLabel permanently?"
                )
            },
            text = {
                Text(
                    if (removalAction == PhotoRemovalAction.TRASH) {
                        if (count == 1) {
                            "The photo will leave Frame Finder and can be restored from your gallery app's trash."
                        } else {
                            "The photos will leave Frame Finder and can be restored from your gallery app's trash."
                        }
                    } else if (count == 1) {
                        "This file will be permanently deleted from your device. This cannot be undone."
                    } else {
                        "These files will be permanently deleted from your device in one batch. " +
                            "This cannot be undone. Check your selection carefully before continuing."
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { batchRemovalAction = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val photos = selectedPhotos.values.toList()
                        batchRemovalAction = null
                        selectedPhotos.clear()
                        if (removalAction == PhotoRemovalAction.TRASH) onTrashPhotos(photos)
                        else onDeletePhotos(photos)
                    }
                ) {
                    Text(
                        when {
                            removalAction == PhotoRemovalAction.TRASH -> "Move to trash"
                            count == 1 -> "Delete photo"
                            else -> "Delete $count photos"
                        },
                        color = if (removalAction == PhotoRemovalAction.DELETE) {
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

internal data class IncompleteAnalysisCounts(
    val missingAiCount: Int,
    val missingTextCount: Int,
    val knownFailureCount: Int
)

internal fun incompleteAnalysisCounts(
    missingEmbeddingCount: Int,
    embeddingErrorCount: Int,
    missingOcrCount: Int,
    ocrErrorCount: Int
): IncompleteAnalysisCounts {
    return IncompleteAnalysisCounts(
        missingAiCount = missingEmbeddingCount.coerceAtLeast(0),
        missingTextCount = missingOcrCount.coerceAtLeast(0),
        knownFailureCount = (embeddingErrorCount + ocrErrorCount).coerceAtLeast(0)
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TouchPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    var touchInputActive by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(touchInputActive, isRefreshing) {
        if (!touchInputActive && !isRefreshing) pullState.animateToHidden()
    }
    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> {
                                touchInputActive = event.changes.any { it.type == PointerType.Touch }
                            }
                            PointerEventType.Scroll -> touchInputActive = false
                        }
                    }
                }
            }
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullState,
                enabled = touchInputActive,
                onRefresh = {
                    touchInputActive = false
                    onRefresh()
                }
            )
    ) {
        content()
        if (touchInputActive || isRefreshing) {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    canMoveToTrash: Boolean,
    onTrash: () -> Unit,
    onDelete: () -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "Share selected photos")
            }
            if (canMoveToTrash) {
                IconButton(onClick = onTrash) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Move selected photos to trash")
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More selection actions")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                moreExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete selected photos",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSearchSources(
    visualEnabled: Boolean,
    filenameEnabled: Boolean,
    descriptionEnabled: Boolean,
    ocrEnabled: Boolean
) {
    val sources = buildList {
        if (visualEnabled) add("Visual")
        if (ocrEnabled) add("OCR")
        if (filenameEnabled) add("Filename")
        if (descriptionEnabled) add("Description")
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Searching",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        sources.forEach { source ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    source,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SearchOptionsMenu(
    searchStrictness: Int,
    visualSearchEnabled: Boolean,
    filenameSearchEnabled: Boolean,
    descriptionSearchEnabled: Boolean,
    ocrEnabled: Boolean,
    onSearchStrictness: (Int) -> Unit,
    onVisualSearch: (Boolean) -> Unit,
    onFilenameSearch: (Boolean) -> Unit,
    onDescriptionSearch: (Boolean) -> Unit,
    onOcr: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var strictness by remember(searchStrictness) { mutableFloatStateOf(searchStrictness.toFloat()) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Search options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(300.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Search strictness", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(
                        strictnessLabel(strictness.roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = strictness,
                    onValueChange = { strictness = it },
                    onValueChangeFinished = { onSearchStrictness(strictness.roundToInt()) },
                    enabled = visualSearchEnabled,
                    valueRange = 0f..100f,
                    steps = 19
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "More results",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Closer matches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                SearchOptionSwitch(
                    title = "Search photo contents visually",
                    checked = visualSearchEnabled,
                    onCheckedChange = onVisualSearch
                )
                SearchOptionSwitch(
                    title = "Search photo filenames",
                    checked = filenameSearchEnabled,
                    onCheckedChange = onFilenameSearch
                )
                SearchOptionSwitch(
                    title = "Search photo descriptions",
                    checked = descriptionSearchEnabled,
                    onCheckedChange = onDescriptionSearch
                )
                SearchOptionSwitch(
                    title = "Search text inside photos",
                    checked = ocrEnabled,
                    onCheckedChange = onOcr
                )
            }
        }
    }
}

@Composable
private fun SearchOptionSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        FrameFinderSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SortMenu(
    selected: ResultSort,
    searching: Boolean,
    grouping: GalleryGrouping,
    onSelected: (ResultSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateGrouping = grouping == GalleryGrouping.DAY || grouping == GalleryGrouping.MONTH
    val effectiveSelection = when {
        !searching && dateGrouping && selected != ResultSort.NEWEST && selected != ResultSort.OLDEST -> ResultSort.NEWEST
        !searching && selected == ResultSort.RELEVANCE -> ResultSort.NEWEST
        else -> selected
    }
    val options = when {
        searching -> ResultSort.entries
        dateGrouping -> listOf(ResultSort.NEWEST, ResultSort.OLDEST)
        else -> ResultSort.entries.filterNot { it == ResultSort.RELEVANCE }
    }

    Box {
        Surface(
            modifier = Modifier.clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(start = 11.dp, end = 5.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sort by ${sortLabel(effectiveSelection)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sortLabel(option),
                            fontWeight = if (option == effectiveSelection) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun GalleryDisplayMenu(
    layout: PhotoLayout,
    density: ThumbnailDensity,
    grouping: GalleryGrouping,
    onLayoutSelected: (PhotoLayout) -> Unit,
    onDensitySelected: (ThumbnailDensity) -> Unit,
    onGroupingSelected: (GalleryGrouping) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    if (layout == PhotoLayout.GRID) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = "Photo layout and grouping"
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(250.dp)
        ) {
            Text(
                "Layout",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            PhotoDisplayMenuItem(
                label = "Grid",
                selected = layout == PhotoLayout.GRID,
                onClick = {
                    expanded = false
                    onLayoutSelected(PhotoLayout.GRID)
                }
            )
            PhotoDisplayMenuItem(
                label = "List",
                selected = layout == PhotoLayout.LIST,
                onClick = {
                    expanded = false
                    onLayoutSelected(PhotoLayout.LIST)
                }
            )
            if (layout == PhotoLayout.GRID) {
                Text(
                    "Thumbnail size",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                ThumbnailDensity.entries.forEach { option ->
                    PhotoDisplayMenuItem(
                        label = densityLabel(option),
                        selected = density == option,
                        onClick = {
                            expanded = false
                            onDensitySelected(option)
                        }
                    )
                }
            }
            Text(
                "Group photos",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            GalleryGrouping.entries.forEach { option ->
                PhotoDisplayMenuItem(
                    label = groupingLabel(option),
                    selected = grouping == option,
                    onClick = {
                        expanded = false
                        onGroupingSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun PhotoDisplayMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        },
        onClick = onClick
    )
}

private fun groupingLabel(grouping: GalleryGrouping): String = when (grouping) {
    GalleryGrouping.NONE -> "No grouping"
    GalleryGrouping.DAY -> "Day"
    GalleryGrouping.MONTH -> "Month"
    GalleryGrouping.FOLDER -> "Storage folder"
}

private fun densityLabel(density: ThumbnailDensity): String = when (density) {
    ThumbnailDensity.COMPACT -> "Compact"
    ThumbnailDensity.COMFORTABLE -> "Comfortable"
    ThumbnailDensity.LARGE -> "Large"
}

private fun thumbnailSize(density: ThumbnailDensity): Dp = when (density) {
    ThumbnailDensity.COMPACT -> 82.dp
    ThumbnailDensity.COMFORTABLE -> 108.dp
    ThumbnailDensity.LARGE -> 150.dp
}

private fun sortLabel(sort: ResultSort): String = when (sort) {
    ResultSort.RELEVANCE -> "Relevance"
    ResultSort.NEWEST -> "Newest"
    ResultSort.OLDEST -> "Oldest"
    ResultSort.NAME -> "Name"
    ResultSort.LARGEST -> "Largest file"
    ResultSort.SMALLEST -> "Smallest file"
}

@Composable
private fun PagedPhotoGrid(
    gallery: LazyPagingItems<GalleryItem>,
    gridState: LazyGridState,
    selectedPhotoIds: Set<Long>,
    selectionMode: Boolean,
    thumbnailSize: Dp,
    onToggleSelection: (PhotoGridItem) -> Unit,
    onOpenPhotos: (List<PhotoGridItem>, Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(thumbnailSize),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(
            count = gallery.itemCount,
            key = gallery.itemKey(::galleryItemKey),
            span = { index ->
                if (gallery.peek(index) is GalleryItem.Header) GridItemSpan(maxLineSpan)
                else GridItemSpan(1)
            }
        ) { index ->
            when (val item = gallery[index]) {
                is GalleryItem.Header -> GalleryHeader(item.label)
                is GalleryItem.Photo -> PhotoTile(
                    photo = item.photo,
                    selected = item.photo.mediaId in selectedPhotoIds,
                    onClick = {
                        if (selectionMode) onToggleSelection(item.photo)
                        else openPagedPhoto(gallery, item.photo, onOpenPhotos)
                    },
                    onLongClick = { onToggleSelection(item.photo) }
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun PagedPhotoList(
    gallery: LazyPagingItems<GalleryItem>,
    listState: LazyListState,
    selectedPhotoIds: Set<Long>,
    selectionMode: Boolean,
    onToggleSelection: (PhotoGridItem) -> Unit,
    onOpenPhotos: (List<PhotoGridItem>, Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            count = gallery.itemCount,
            key = gallery.itemKey(::galleryItemKey)
        ) { index ->
            when (val item = gallery[index]) {
                is GalleryItem.Header -> GalleryHeader(item.label)
                is GalleryItem.Photo -> PhotoListRow(
                    photo = item.photo,
                    selected = item.photo.mediaId in selectedPhotoIds,
                    onClick = {
                        if (selectionMode) onToggleSelection(item.photo)
                        else openPagedPhoto(gallery, item.photo, onOpenPhotos)
                    },
                    onLongClick = { onToggleSelection(item.photo) }
                )
                null -> Unit
            }
        }
    }
}

private fun galleryItemKey(item: GalleryItem): Any = when (item) {
    is GalleryItem.Header -> "header:${item.key}"
    is GalleryItem.Photo -> item.photo.mediaId
}

private fun openPagedPhoto(
    gallery: LazyPagingItems<GalleryItem>,
    photo: PhotoGridItem,
    onOpenPhotos: (List<PhotoGridItem>, Int) -> Unit
) {
    val loadedPhotos = gallery.itemSnapshotList.items.mapNotNull { (it as? GalleryItem.Photo)?.photo }
    val loadedIndex = loadedPhotos.indexOfFirst { it.mediaId == photo.mediaId }
    if (loadedIndex >= 0) onOpenPhotos(loadedPhotos, loadedIndex)
}

private fun openSearchPhoto(
    photos: List<PhotoGridItem>,
    photo: PhotoGridItem,
    onOpenPhotos: (List<PhotoGridItem>, Int) -> Unit
) {
    val index = photos.indexOfFirst { it.mediaId == photo.mediaId }
    if (index >= 0) onOpenPhotos(photos, index)
}

@Composable
private fun GalleryHeader(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 3.dp, start = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PhotoListRow(
    photo: PhotoGridItem,
    matchReasons: Set<SearchMatchReason> = emptySet(),
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = Uri.parse(photo.contentUri),
                contentDescription = photo.displayName,
                modifier = Modifier.size(82.dp).clip(RoundedCornerShape(5.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    photo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatPhotoDate(photo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (matchReasons.isNotEmpty()) {
                    Text(
                        matchReasonSummary(matchReasons),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                photo.storagePath?.let { path ->
                    Text(
                        path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PhotoTile(
    photo: PhotoGridItem,
    matchReasons: Set<SearchMatchReason> = emptySet(),
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.aspectRatio(1f).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = Uri.parse(photo.contentUri),
                contentDescription = photo.displayName,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            if (matchReasons.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    contentColor = Color.White
                ) {
                    Text(
                        matchReasonSummary(matchReasons, maximumLabels = 1),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    formatPhotoDate(photo),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(27.dp)
                )
            }
        }
    }
}

private fun matchReasonSummary(
    reasons: Set<SearchMatchReason>,
    maximumLabels: Int = SearchMatchReason.entries.size
): String {
    val ordered = SearchMatchReason.entries.filter(reasons::contains)
    val visible = ordered.take(maximumLabels).joinToString(", ") { it.label }
    val remaining = ordered.size - maximumLabels
    return if (remaining > 0) "$visible +$remaining" else visible
}

private enum class PhotoRemovalAction { TRASH, DELETE }

private fun formatPhotoDate(photo: PhotoGridItem): String = runCatching {
    DATE_FORMAT.format(Instant.ofEpochMilli(photo.dateTaken ?: photo.dateAdded).atZone(ZoneId.systemDefault()))
}.getOrDefault(photo.displayName)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")
