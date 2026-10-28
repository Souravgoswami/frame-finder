package com.souravgoswami.framefinder

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.souravgoswami.framefinder.data.LaunchView
import com.souravgoswami.framefinder.data.PhotoGridItem
import com.souravgoswami.framefinder.media.MediaStoreScanner
import com.souravgoswami.framefinder.media.PhotoMetadataReader
import com.souravgoswami.framefinder.ui.AppLockScreen
import com.souravgoswami.framefinder.ui.HomeScreen
import com.souravgoswami.framefinder.ui.InitialModelSelectionScreen
import com.souravgoswami.framefinder.ui.PhotoViewerScreen
import com.souravgoswami.framefinder.ui.FrameFinderViewModel
import com.souravgoswami.framefinder.ui.SettingsScreen
import com.souravgoswami.framefinder.ui.TrashScreen
import com.souravgoswami.framefinder.ui.theme.FrameFinderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat

class MainActivity : ComponentActivity() {
    private val viewModel: FrameFinderViewModel by viewModels()
    private val photoMetadataReader by lazy { PhotoMetadataReader(this) }
    private var credentialPromptActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowScreenCapture()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { FrameFinderApp() }
    }

    override fun onResume() {
        super.onResume()
        allowScreenCapture()
    }

    private fun allowScreenCapture() {
        // Frame Finder's app lock controls entry to the app. Once it is open, users must be
        // able to capture screenshots for sharing and troubleshooting.
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Composable
    private fun FrameFinderApp() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val gallery = if (state.preferences.launchView == LaunchView.PHOTO_LIBRARY) {
            viewModel.gallery.collectAsLazyPagingItems()
        } else {
            null
        }
        val galleryGridState = rememberLazyGridState()
        val searchGridState = rememberLazyGridState()
        val galleryListState = rememberLazyListState()
        val searchListState = rememberLazyListState()
        var destination by remember { mutableStateOf<Destination>(Destination.Home) }
        var permissionRevision by remember { mutableIntStateOf(0) }
        var mediaRevision by remember { mutableIntStateOf(0) }
        var pendingAnalyzeAll by remember { mutableStateOf(false) }
        var pendingForceRebuild by remember { mutableStateOf(false) }
        var pendingIndex by remember { mutableStateOf(false) }
        var pendingNotificationIndex by remember { mutableStateOf(false) }
        var pendingNotificationAnalyzeAll by remember { mutableStateOf(false) }
        var pendingNotificationForceRebuild by remember { mutableStateOf(false) }
        var scanCheckRequest by remember { mutableStateOf<IndexRequest?>(null) }
        var scanWarningRequest by remember { mutableStateOf<ScanWarningRequest?>(null) }
        var analysisConfirmationRequest by remember { mutableStateOf<IndexRequest?>(null) }
        var largeLibraryWarningAccepted by remember { mutableStateOf(false) }
        var credentialAction by remember { mutableStateOf<CredentialAction?>(null) }
        var appUnlocked by remember { mutableStateOf(!state.preferences.appLockEnabled) }
        var unlockPromptAttempted by remember { mutableStateOf(false) }
        var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
        var pendingSave by remember { mutableStateOf<PhotoGridItem?>(null) }
        var pendingAnalysisImport by remember { mutableStateOf<Uri?>(null) }
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        val credentialLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val action = credentialAction
            credentialPromptActive = false
            credentialAction = null
            if (result.resultCode == Activity.RESULT_OK) {
                when (action) {
                    CredentialAction.UNLOCK -> appUnlocked = true
                    CredentialAction.ENABLE -> {
                        viewModel.setAppLockEnabled(true)
                        appUnlocked = true
                    }
                    CredentialAction.DISABLE -> {
                        viewModel.setAppLockEnabled(false)
                        appUnlocked = true
                    }
                    null -> Unit
                }
            }
        }
        val analysisExportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            uri?.let(viewModel::exportAnalysis)
        }
        val analysisImportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) pendingAnalysisImport = uri
        }
        val legacySavePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val photo = pendingSave
            pendingSave = null
            if (granted && photo != null) {
                savePhotoCopy(photo)
            } else if (photo != null) {
                Toast.makeText(
                    this@MainActivity,
                    "Storage permission is required to save this photo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        fun requestCredential(action: CredentialAction) {
            val keyguard = getSystemService(KeyguardManager::class.java)
            val title = when (action) {
                CredentialAction.UNLOCK -> "Unlock Frame Finder"
                CredentialAction.ENABLE -> "Enable Frame Finder app lock"
                CredentialAction.DISABLE -> "Disable Frame Finder app lock"
            }
            val prompt = keyguard.createConfirmDeviceCredentialIntent(
                title,
                "Confirm your phone screen lock to continue."
            )
            if (prompt == null) {
                credentialPromptActive = false
                if (action == CredentialAction.UNLOCK || action == CredentialAction.DISABLE) {
                    viewModel.setAppLockEnabled(false)
                    appUnlocked = true
                    Toast.makeText(
                        this@MainActivity,
                        "App lock was turned off because this phone has no screen lock.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Set a PIN, pattern, or password on this phone before enabling app lock.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            credentialPromptActive = true
            credentialAction = action
            runCatching { credentialLauncher.launch(prompt) }
                .onFailure {
                    credentialPromptActive = false
                    credentialAction = null
                    Toast.makeText(
                        this@MainActivity,
                        "The phone screen lock could not be opened.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        DisposableEffect(lifecycleOwner, state.preferences.appLockEnabled) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionRevision++
                    mediaRevision++
                } else if (
                    event == Lifecycle.Event.ON_STOP &&
                    state.preferences.appLockEnabled &&
                    !credentialPromptActive
                ) {
                    appUnlocked = false
                    unlockPromptAttempted = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        DisposableEffect(Unit) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    mediaRevision++
                }
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            onDispose { contentResolver.unregisterContentObserver(observer) }
        }
        @Suppress("UNUSED_VARIABLE") val revision = permissionRevision
        val access = photoAccess()

        LaunchedEffect(state.preferences.appLockEnabled, appUnlocked, unlockPromptAttempted) {
            if (state.preferences.appLockEnabled && !appUnlocked && !unlockPromptAttempted) {
                unlockPromptAttempted = true
                requestCredential(CredentialAction.UNLOCK)
            }
        }

        LaunchedEffect(
            state.query,
            state.sort,
            state.preferences.searchStrictness,
            state.preferences.visualSearchEnabled,
            state.preferences.filenameSearchEnabled,
            state.preferences.descriptionSearchEnabled,
            state.preferences.ocrSearchEnabled,
            state.preferences.searchModel,
            state.preferences.photoLayout,
            state.preferences.thumbnailDensity,
            state.preferences.galleryGrouping
        ) {
            searchGridState.scrollToItem(0)
            searchListState.scrollToItem(0)
        }
        LaunchedEffect(
            state.preferences.launchView,
            state.preferences.photoLayout,
            state.preferences.thumbnailDensity,
            state.preferences.galleryGrouping,
            state.sort
        ) {
            galleryGridState.scrollToItem(0)
            galleryListState.scrollToItem(0)
        }

        LaunchedEffect(
            mediaRevision,
            access.hasAny,
            state.indexStatusLoaded,
            state.indexedCount,
            state.work.running
        ) {
            if (access.hasAny && state.indexStatusLoaded && state.indexedCount > 0 && !state.work.running) {
                viewModel.refreshNewPhotoStatus()
            }
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val startIndex = pendingNotificationIndex
            val analyzeAll = pendingNotificationAnalyzeAll
            val forceRebuild = pendingNotificationForceRebuild
            pendingNotificationIndex = false
            pendingNotificationAnalyzeAll = false
            pendingNotificationForceRebuild = false
            if (!granted) {
                Toast.makeText(
                    this@MainActivity,
                    "Notifications are off; progress will remain visible inside Frame Finder.",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (startIndex) viewModel.prepareAndIndex(analyzeAll, forceRebuild)
        }
        val photoPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionRevision++
            if (pendingIndex && photoAccess().hasAny) {
                val request = IndexRequest(
                    analyzeAll = pendingAnalyzeAll,
                    forceRebuild = pendingForceRebuild,
                    timeWarningConfirmed = state.preferences.scanPaused
                )
                if ((request.analyzeAll || request.forceRebuild) &&
                    state.indexedCount > 0 && !state.preferences.scanPaused
                ) {
                    analysisConfirmationRequest = request
                } else {
                    scanCheckRequest = request
                }
            }
            pendingAnalyzeAll = false
            pendingForceRebuild = false
            pendingIndex = false
        }
        fun requestPhotos(
            analyzeAll: Boolean = false,
            forceRebuild: Boolean = false,
            startIndex: Boolean = false
        ) {
            pendingAnalyzeAll = analyzeAll
            pendingForceRebuild = forceRebuild
            pendingIndex = startIndex
            photoPermissionLauncher.launch(photoPermissions())
        }
        fun startCheckedIndex(request: IndexRequest) {
            if (shouldRequestNotificationPermission()) {
                pendingNotificationIndex = true
                pendingNotificationAnalyzeAll = request.analyzeAll
                pendingNotificationForceRebuild = request.forceRebuild
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.prepareAndIndex(request.analyzeAll, request.forceRebuild)
            }
        }
        fun index(analyzeAll: Boolean = false, forceRebuild: Boolean = false) {
            when {
                !photoAccess().hasAny -> requestPhotos(analyzeAll, forceRebuild, startIndex = true)
                (analyzeAll || forceRebuild) && state.indexedCount > 0 && !state.preferences.scanPaused -> {
                    analysisConfirmationRequest = IndexRequest(
                        analyzeAll = true,
                        forceRebuild = forceRebuild
                    )
                }
                else -> scanCheckRequest = IndexRequest(
                    analyzeAll = analyzeAll || forceRebuild,
                    forceRebuild = forceRebuild,
                    timeWarningConfirmed = state.preferences.scanPaused
                )
            }
        }
        LaunchedEffect(scanCheckRequest) {
            val request = scanCheckRequest ?: return@LaunchedEffect
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val scanner = MediaStoreScanner(this@MainActivity)
                    val timestamp = state.preferences.lastIndexedPhotoTimestamp
                    if (request.analyzeAll || state.indexedCount == 0 || timestamp <= 0L) {
                        scanner.photoCount(state.preferences.excludedFolders)
                    } else {
                        scanner.photoCountNewerThan(timestamp, state.preferences.excludedFolders)
                    }
                }.getOrNull()
            }
            scanCheckRequest = null
            if (count == null) {
                Toast.makeText(
                    this@MainActivity,
                    "The photo library could not be checked.",
                    Toast.LENGTH_LONG
                ).show()
            } else if (!request.analyzeAll && state.indexedCount > 0 && count == 0) {
                viewModel.clearNewPhotoNotice()
                Toast.makeText(this@MainActivity, "No new photos found.", Toast.LENGTH_SHORT).show()
            } else if (
                count >= LARGE_LIBRARY_WARNING_THRESHOLD &&
                !request.timeWarningConfirmed &&
                !largeLibraryWarningAccepted
            ) {
                scanWarningRequest = ScanWarningRequest(request, count)
            } else {
                startCheckedIndex(request)
            }
        }

        val finishRemovals: (List<PhotoGridItem>, Int, Boolean, PhotoRemoval) -> Unit =
            { removed, failed, cancelled, removal ->
            val uniqueRemoved = removed.distinctBy(PhotoGridItem::mediaId)
            val removedIds = uniqueRemoved.map(PhotoGridItem::mediaId).toSet()
            if (removedIds.isNotEmpty()) {
                if (removal == PhotoRemoval.TRASH) {
                    viewModel.setPhotosTrashed(removedIds, true)
                } else {
                    viewModel.removePhotosFromIndex(removedIds)
                }
            }
            val viewer = destination as? Destination.Viewer
            if (viewer != null && removedIds.isNotEmpty()) {
                val removedIndex = viewer.photos.indexOfFirst { it.mediaId in removedIds }
                val remaining = viewer.photos.filterNot { it.mediaId in removedIds }
                destination = if (remaining.isEmpty()) {
                    Destination.Home
                } else {
                    Destination.Viewer(
                        remaining,
                        removedIndex.coerceAtLeast(0).coerceAtMost(remaining.lastIndex)
                    )
                }
            }
            if (removedIds.isNotEmpty()) mediaRevision++
            if (destination == Destination.Trash) viewModel.refreshTrash()
            val message = when (removal) {
                PhotoRemoval.TRASH -> when {
                    cancelled -> "Move to trash cancelled."
                    uniqueRemoved.isEmpty() -> "The selected photos could not be moved to trash."
                    failed > 0 -> "${uniqueRemoved.size} photos moved to trash. $failed could not be moved."
                    uniqueRemoved.size == 1 -> "Photo moved to trash."
                    else -> "${uniqueRemoved.size} photos moved to trash."
                }
                PhotoRemoval.DELETE -> when {
                    cancelled && uniqueRemoved.isEmpty() -> "Deletion cancelled."
                    cancelled -> "${uniqueRemoved.size} photos were deleted before deletion was cancelled."
                    uniqueRemoved.isEmpty() -> "The selected photos could not be deleted."
                    failed > 0 -> "${uniqueRemoved.size} photos deleted. $failed could not be deleted."
                    uniqueRemoved.size == 1 -> "Photo deleted permanently."
                    else -> "${uniqueRemoved.size} photos deleted permanently."
                }
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        val finishRestores: (List<PhotoGridItem>, Boolean) -> Unit = { restored, cancelled ->
            if (restored.isNotEmpty()) {
                viewModel.setPhotosTrashed(restored.map(PhotoGridItem::mediaId), false)
                mediaRevision++
            }
            viewModel.refreshTrash()
            val message = when {
                cancelled -> "Restore cancelled."
                restored.size == 1 -> "Photo restored."
                else -> "${restored.size} photos restored."
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        val deleteDirectly: (PhotoGridItem) -> Boolean = { photo ->
            contentResolver.delete(Uri.parse(photo.contentUri), null, null) > 0
        }
        lateinit var continueLegacyDeletion: (List<PhotoGridItem>, List<PhotoGridItem>, Int) -> Unit
        val deleteApprovalLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val request = pendingDelete
            pendingDelete = null
            if (request != null) {
                when (request.mode) {
                    DeleteApprovalMode.SYSTEM_DELETES -> {
                        if (result.resultCode == Activity.RESULT_OK) {
                            finishRemovals(request.photos, request.failed, false, PhotoRemoval.DELETE)
                        } else {
                            finishRemovals(request.deleted, request.failed, true, PhotoRemoval.DELETE)
                        }
                    }
                    DeleteApprovalMode.SYSTEM_TRASHES -> {
                        if (result.resultCode == Activity.RESULT_OK) {
                            finishRemovals(request.photos, request.failed, false, PhotoRemoval.TRASH)
                        } else {
                            finishRemovals(emptyList(), request.failed, true, PhotoRemoval.TRASH)
                        }
                    }
                    DeleteApprovalMode.SYSTEM_RESTORES -> {
                        if (result.resultCode == Activity.RESULT_OK) {
                            finishRestores(request.photos, false)
                        } else {
                            finishRestores(emptyList(), true)
                        }
                    }
                    DeleteApprovalMode.RETRY_DELETE -> {
                        if (result.resultCode == Activity.RESULT_OK) {
                            val photo = request.photos.first()
                            val deleted = runCatching { deleteDirectly(photo) }.getOrDefault(false)
                            continueLegacyDeletion(
                                request.remaining,
                                if (deleted) request.deleted + photo else request.deleted,
                                request.failed + if (deleted) 0 else 1
                            )
                        } else {
                            finishRemovals(request.deleted, request.failed, true, PhotoRemoval.DELETE)
                        }
                    }
                    DeleteApprovalMode.DIRECT_AFTER_PERMISSION -> Unit
                }
            }
        }
        val legacyWritePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val request = pendingDelete
            pendingDelete = null
            if (granted && request != null) {
                continueLegacyDeletion(request.photos, request.deleted, request.failed)
            } else if (request != null) {
                Toast.makeText(
                    this@MainActivity,
                    "Storage permission is required to delete these photos.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val launchDeleteApproval: (PendingDelete, android.content.IntentSender) -> Unit = { request, sender ->
            pendingDelete = request
            runCatching {
                deleteApprovalLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }.onFailure {
                pendingDelete = null
                when (request.mode) {
                    DeleteApprovalMode.RETRY_DELETE -> continueLegacyDeletion(
                        request.remaining,
                        request.deleted,
                        request.failed + request.photos.size
                    )
                    DeleteApprovalMode.SYSTEM_RESTORES -> finishRestores(emptyList(), true)
                    else -> finishRemovals(
                        request.deleted,
                        request.failed + request.photos.size,
                        false,
                        if (request.mode == DeleteApprovalMode.SYSTEM_TRASHES) {
                            PhotoRemoval.TRASH
                        } else {
                            PhotoRemoval.DELETE
                        }
                    )
                }
            }
        }
        continueLegacyDeletion = continueDeletion@ { remaining, alreadyDeleted, alreadyFailed ->
            val deleted = alreadyDeleted.toMutableList()
            var failed = alreadyFailed
            var nextIndex = 0
            while (nextIndex < remaining.size) {
                val photo = remaining[nextIndex]
                nextIndex++
                try {
                    if (deleteDirectly(photo)) deleted += photo else failed++
                } catch (security: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && security is RecoverableSecurityException) {
                        launchDeleteApproval(
                            PendingDelete(
                                photos = listOf(photo),
                                mode = DeleteApprovalMode.RETRY_DELETE,
                                remaining = remaining.drop(nextIndex),
                                deleted = deleted.toList(),
                                failed = failed
                            ),
                            security.userAction.actionIntent.intentSender
                        )
                        return@continueDeletion
                    }
                    failed++
                } catch (_: Exception) {
                    failed++
                }
            }
            finishRemovals(deleted, failed, false, PhotoRemoval.DELETE)
        }
        fun deletePhotos(selected: List<PhotoGridItem>) {
            val photos = selected.distinctBy(PhotoGridItem::mediaId)
            if (photos.isEmpty()) return
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val uris = photos.map { Uri.parse(it.contentUri) }
                    runCatching { MediaStore.createDeleteRequest(contentResolver, uris).intentSender }
                        .onSuccess {
                            launchDeleteApproval(
                                PendingDelete(photos, DeleteApprovalMode.SYSTEM_DELETES),
                                it
                            )
                        }
                        .onFailure {
                            Toast.makeText(
                                this@MainActivity,
                                "The selected photos could not be prepared for deletion.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    pendingDelete = PendingDelete(photos, DeleteApprovalMode.DIRECT_AFTER_PERMISSION)
                    legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> continueLegacyDeletion(photos, emptyList(), 0)
            }
        }
        fun trashPhotos(selected: List<PhotoGridItem>) {
            val photos = selected.distinctBy(PhotoGridItem::mediaId)
            if (photos.isEmpty()) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                deletePhotos(photos)
                return
            }
            val uris = photos.map { Uri.parse(it.contentUri) }
            runCatching { MediaStore.createTrashRequest(contentResolver, uris, true).intentSender }
                .onSuccess {
                    launchDeleteApproval(
                        PendingDelete(photos, DeleteApprovalMode.SYSTEM_TRASHES),
                        it
                    )
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "The selected photos could not be prepared for trash.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
        fun restorePhotos(selected: List<PhotoGridItem>) {
            val photos = selected.distinctBy(PhotoGridItem::mediaId)
            if (photos.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            val uris = photos.map { Uri.parse(it.contentUri) }
            runCatching { MediaStore.createTrashRequest(contentResolver, uris, false).intentSender }
                .onSuccess {
                    launchDeleteApproval(
                        PendingDelete(photos, DeleteApprovalMode.SYSTEM_RESTORES),
                        it
                    )
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "The selected photos could not be prepared for restore.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
        fun deletePhoto(photo: PhotoGridItem) = deletePhotos(listOf(photo))
        fun trashPhoto(photo: PhotoGridItem) = trashPhotos(listOf(photo))
        fun savePhoto(photo: PhotoGridItem) {
            if (
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                pendingSave = photo
                legacySavePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                savePhotoCopy(photo)
            }
        }
        FrameFinderTheme(state.preferences.themeMode) {
            Surface {
                if (state.preferences.appLockEnabled && !appUnlocked) {
                    AppLockScreen { requestCredential(CredentialAction.UNLOCK) }
                } else if (!state.preferences.initialModelSelected) {
                    InitialModelSelectionScreen(
                        initialModel = state.preferences.searchModel,
                        initialLaunchView = state.preferences.launchView,
                        onContinue = viewModel::completeInitialModelSelection
                    )
                } else when (val current = destination) {
                    Destination.Home -> HomeScreen(
                        state = state,
                        gallery = gallery,
                        galleryGridState = galleryGridState,
                        searchGridState = searchGridState,
                        galleryListState = galleryListState,
                        searchListState = searchListState,
                        hasPhotoAccess = access.hasAny,
                        onRequestPhotoAccess = { requestPhotos() },
                        onQueryChange = viewModel::setQuery,
                        onSortChange = viewModel::setSort,
                        onPhotoLayout = viewModel::setPhotoLayout,
                        onThumbnailDensity = viewModel::setThumbnailDensity,
                        onGalleryGrouping = viewModel::setGalleryGrouping,
                        onSearchStrictness = viewModel::setSearchStrictness,
                        onVisualSearch = viewModel::setVisualSearchEnabled,
                        onFilenameSearch = viewModel::setFilenameSearchEnabled,
                        onDescriptionSearch = viewModel::setDescriptionSearchEnabled,
                        onIgnoreFailedPhotos = viewModel::setIgnoreFailedPhotos,
                        onOcr = viewModel::setOcrSearchEnabled,
                        onOpenSettings = {
                            viewModel.refreshStorageFolders()
                            destination = Destination.Settings
                        },
                        onOpenPhotos = { photos, initialIndex ->
                            if (photos.isNotEmpty()) destination = Destination.Viewer(photos, initialIndex)
                        },
                        onSharePhotos = ::sharePhotos,
                        canMoveToTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            state.preferences.useSystemTrash,
                        onTrashPhotos = ::trashPhotos,
                        onDeletePhotos = ::deletePhotos,
                        onPrepareAndIndex = {
                            index(
                                analyzeAll = state.preferences.scanPaused &&
                                    state.preferences.scanAnalyzeAllPending,
                                forceRebuild = state.preferences.scanPaused &&
                                    state.preferences.scanForceRebuildPending
                            )
                        },
                        onImportAnalysis = {
                            analysisImportLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream")
                            )
                        },
                        onAnalyzeAll = { index(analyzeAll = true) },
                        onPauseWork = viewModel::pauseWork,
                        onCancelScan = viewModel::cancelPausedWork,
                        onRefreshContent = viewModel::refreshContent
                    )
                    Destination.Settings -> SettingsScreen(
                        state = state,
                        hasPhotoAccess = access.hasAny,
                        hasFullPhotoAccess = access.hasFull,
                        supportsSystemTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        onBack = { destination = Destination.Home },
                        onRequestPhotoAccess = { requestPhotos() },
                        onIndex = { index() },
                        onResume = {
                            index(
                                analyzeAll = state.preferences.scanAnalyzeAllPending,
                                forceRebuild = state.preferences.scanForceRebuildPending
                            )
                        },
                        onAnalyzeAll = { index(analyzeAll = true) },
                        onRebuild = { index(analyzeAll = true, forceRebuild = true) },
                        onCancelScan = viewModel::cancelPausedWork,
                        onCheckRestoredFolders = {
                            analysisConfirmationRequest = IndexRequest(analyzeAll = true)
                        },
                        onPause = viewModel::pauseWork,
                        onSearchModel = viewModel::setSearchModel,
                        onSearchStrictness = viewModel::setSearchStrictness,
                        onVisualSearch = viewModel::setVisualSearchEnabled,
                        onFilenameSearch = viewModel::setFilenameSearchEnabled,
                        onDescriptionSearch = viewModel::setDescriptionSearchEnabled,
                        onUseSystemTrash = viewModel::setUseSystemTrash,
                        onIgnoreFailedPhotos = viewModel::setIgnoreFailedPhotos,
                        onOpenTrash = {
                            viewModel.refreshTrash()
                            destination = Destination.Trash
                        },
                        onExportAnalysis = {
                            analysisExportLauncher.launch("Frame Finder analysis ${java.time.LocalDate.now()}.ffbackup")
                        },
                        onImportAnalysis = {
                            analysisImportLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream")
                            )
                        },
                        onLaunchView = viewModel::setLaunchView,
                        onTheme = viewModel::setTheme,
                        onFolderExcluded = viewModel::setFolderExcluded,
                        onOcr = viewModel::setOcrSearchEnabled,
                        onAppLock = { enabled ->
                            requestCredential(if (enabled) CredentialAction.ENABLE else CredentialAction.DISABLE)
                        }
                    )
                    is Destination.Viewer -> PhotoViewerScreen(
                        photos = current.photos,
                        initialIndex = current.initialIndex,
                        onBack = { destination = Destination.Home },
                        onShare = ::sharePhoto,
                        onSave = ::savePhoto,
                        onOpenGallery = ::openInGallery,
                        onLoadMetadata = viewModel::indexedPhotoMetadata,
                        onLoadFileMetadata = photoMetadataReader::read,
                        canMoveToTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            state.preferences.useSystemTrash,
                        onTrash = ::trashPhoto,
                        onDelete = ::deletePhoto
                    )
                    Destination.Trash -> TrashScreen(
                        photos = state.trashedPhotos,
                        loading = state.trashLoading,
                        error = state.trashError,
                        onBack = { destination = Destination.Settings },
                        onRefresh = viewModel::refreshTrash,
                        onRestore = ::restorePhotos,
                        onDeletePermanently = ::deletePhotos
                    )
                }
            }
            analysisConfirmationRequest
                ?.takeIf { !state.preferences.appLockEnabled || appUnlocked }
                ?.let { request ->
                val hasSavedAnalysis = state.embeddedCount > 0
                val missingAiCount = state.missingEmbeddingCount
                val missingTextCount = state.missingOcrCount
                val hasIncompleteAnalysis = missingAiCount > 0 || missingTextCount > 0
                val incompleteSummary = buildList {
                    if (missingAiCount > 0) {
                        add(
                            "${formatPhotoCount(missingAiCount)} need AI analysis with " +
                                "${state.preferences.searchModel.displayName}."
                        )
                    }
                    if (missingTextCount > 0) {
                        add("${formatPhotoCount(missingTextCount)} need text recognition.")
                    }
                }.joinToString(" ")
                AlertDialog(
                    onDismissRequest = { analysisConfirmationRequest = null },
                    title = {
                        Text(
                            when {
                                request.forceRebuild -> "Rebuild index?"
                                hasIncompleteAnalysis -> "Complete photo analysis?"
                                hasSavedAnalysis -> "Reanalyze photos?"
                                else -> "Analyze photos?"
                            }
                        )
                    },
                    text = {
                        Text(
                            if (request.forceRebuild) {
                                "Rebuilding removes saved analysis for the selected model and saved text recognition for currently allowed photos. " +
                                    "It removes index entries for photos that are no longer available, then analyzes every allowed photo again. " +
                                    "Search results may be incomplete until it finishes. This can take a long time and use significant battery. " +
                                    "Keep your phone connected to a charger when possible."
                            } else if (hasIncompleteAnalysis) {
                                "$incompleteSummary Frame Finder checks the full library to find missing work, " +
                                    "but completed unchanged photos are reused. This can still take time and use " +
                                    "significant battery. Keep your phone connected to a charger when possible."
                            } else if (hasSavedAnalysis) {
                                "Analysis already exists for ${NumberFormat.getIntegerInstance().format(state.embeddedCount)} photos with ${state.preferences.searchModel.displayName}. " +
                                    "Reanalyzing checks the full library for changed, failed, or missing work. " +
                                    "Completed unchanged photos are reused, but this can still take time and use significant battery. " +
                                    "Keep your phone connected to a charger when possible."
                            } else {
                                "This checks the full library and analyzes photos with ${state.preferences.searchModel.displayName}. " +
                                    "It can take a long time and use significant battery. Keep your phone connected to a charger when possible."
                            }
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { analysisConfirmationRequest = null }) { Text("Cancel") }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                analysisConfirmationRequest = null
                                scanCheckRequest = request.copy(timeWarningConfirmed = true)
                            }
                        ) {
                            Text(
                                when {
                                    request.forceRebuild -> "Rebuild"
                                    hasIncompleteAnalysis -> "Complete"
                                    hasSavedAnalysis -> "Reanalyze"
                                    else -> "Analyze"
                                }
                            )
                        }
                    }
                )
            }
            scanWarningRequest
                ?.takeIf { !state.preferences.appLockEnabled || appUnlocked }
                ?.let { warning ->
                AlertDialog(
                    onDismissRequest = { scanWarningRequest = null },
                    title = { Text("Large photo library") },
                    text = {
                        Text(
                            "Frame Finder found ${NumberFormat.getIntegerInstance().format(warning.photoCount)} photos. " +
                                "Indexing may take a long time and use significant battery. Keep your phone " +
                                "connected to a charger when possible."
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { scanWarningRequest = null }) { Text("Cancel") }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scanWarningRequest = null
                                largeLibraryWarningAccepted = true
                                startCheckedIndex(warning.request)
                            }
                        ) { Text(if (warning.request.forceRebuild) "Start rebuilding" else "Start indexing") }
                    }
                )
            }
            pendingAnalysisImport
                ?.takeIf { !state.preferences.appLockEnabled || appUnlocked }
                ?.let { uri ->
                AlertDialog(
                    onDismissRequest = { pendingAnalysisImport = null },
                    title = { Text("Import analysis data?") },
                    text = {
                        Text(
                            "This merges the backup with the current local index. Matching entries are replaced, " +
                                "but original photos are never changed. Keep Frame Finder open until import finishes."
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingAnalysisImport = null }) { Text("Cancel") }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingAnalysisImport = null
                                viewModel.importAnalysis(uri)
                            }
                        ) { Text("Import") }
                    }
                )
            }
        }
        BackHandler(enabled = state.preferences.appLockEnabled && !appUnlocked) { finish() }
        BackHandler(
            enabled = appUnlocked && destination != Destination.Home && destination != Destination.Trash
        ) { destination = Destination.Home }
    }

    private fun sharePhoto(photo: PhotoGridItem) {
        sharePhotos(listOf(photo))
    }

    private fun sharePhotos(selected: List<PhotoGridItem>) {
        val photos = selected.distinctBy(PhotoGridItem::mediaId)
        if (photos.isEmpty()) return
        val uris = ArrayList(photos.map { Uri.parse(it.contentUri) })
        val multiple = photos.size > 1
        val intent = Intent(if (multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = sharedMimeType(photos)
            if (multiple) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            } else {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
            clipData = ClipData.newUri(
                contentResolver,
                if (multiple) "${photos.size} selected photos" else photos.first().displayName,
                uris.first()
            ).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(
            intent,
            if (multiple) "Share ${photos.size} photos" else "Share photo",
            if (multiple) {
                "No app is available to share these photos."
            } else {
                "No app is available to share this photo."
            }
        )
    }

    private fun sharedMimeType(photos: List<PhotoGridItem>): String {
        val types = photos.mapNotNull(PhotoGridItem::mimeType).distinct()
        if (types.size == 1) return types.first()
        val categories = types.map { it.substringBefore('/') }.distinct()
        return if (categories.size == 1 && categories.first().isNotBlank()) {
            "${categories.first()}/*"
        } else {
            "image/*"
        }
    }

    private fun savePhotoCopy(photo: PhotoGridItem) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { copyPhotoToDownloads(photo) }
            }
            val message = result.fold(
                onSuccess = { "Saved to Downloads/Frame Finder." },
                onFailure = { error ->
                    Log.e(LOG_TAG, "Could not save ${photo.displayName}", error)
                    "The photo could not be saved."
                }
            )
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    @Throws(IOException::class)
    private fun copyPhotoToDownloads(photo: PhotoGridItem): Uri {
        val source = Uri.parse(photo.contentUri)
        val displayName = safeCopyName(photo.displayName)
        val mimeType = photo.mimeType ?: contentResolver.getType(source) ?: "image/*"
        val now = System.currentTimeMillis()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyPhotoWithMediaStore(source, displayName, mimeType, now)
        } else {
            copyPhotoToLegacyDownloads(source, displayName, mimeType, now)
        }
    }

    @Throws(IOException::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyPhotoWithMediaStore(
        source: Uri,
        requestedName: String,
        mimeType: String,
        now: Long
    ): Uri {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$SAVED_PHOTO_DIRECTORY/"
        val displayName = uniqueMediaStoreName(collection, relativePath, requestedName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore did not create the saved photo")
        try {
            copyPhotoBytes(source, target)
            val published = contentResolver.update(
                target,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            if (published <= 0) throw IOException("MediaStore did not publish the saved photo")
            // Android already gives a newly inserted item fresh added and modified times.
            // DATE_TAKEN is provider dependent, so failure to override it must not discard
            // an otherwise valid copy.
            runCatching {
                contentResolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Images.Media.DATE_TAKEN, now) },
                    null,
                    null
                )
            }.onFailure { error ->
                Log.w(LOG_TAG, "Saved photo but could not update its taken time", error)
            }
            return target
        } catch (error: Exception) {
            runCatching { contentResolver.delete(target, null, null) }
            throw error
        }
    }

    @Suppress("DEPRECATION")
    @Throws(IOException::class)
    private fun copyPhotoToLegacyDownloads(
        source: Uri,
        requestedName: String,
        mimeType: String,
        now: Long
    ): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SAVED_PHOTO_DIRECTORY
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the save directory")
        }
        val targetFile = uniqueLegacyFile(directory, requestedName)
        try {
            contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(targetFile).use(input::copyTo)
            } ?: throw IOException("Could not open the source photo")
            targetFile.setLastModified(now)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, targetFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_ADDED, now / 1_000L)
                put(MediaStore.Images.Media.DATE_MODIFIED, now / 1_000L)
                put(MediaStore.Images.Media.DATE_TAKEN, now)
            }
            return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore did not register the saved photo")
        } catch (error: Exception) {
            runCatching { targetFile.delete() }
            throw error
        }
    }

    @Throws(IOException::class)
    private fun copyPhotoBytes(source: Uri, target: Uri) {
        contentResolver.openInputStream(source)?.use { input ->
            contentResolver.openOutputStream(target, "w")?.use(input::copyTo)
                ?: throw IOException("Could not open the saved photo")
        } ?: throw IOException("Could not open the source photo")
    }

    private fun uniqueMediaStoreName(collection: Uri, relativePath: String, requestedName: String): String {
        return uniqueCopyName(requestedName) { candidate ->
            runCatching {
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(relativePath, candidate),
                    null
                )?.use { it.moveToFirst() } == true
            }.getOrDefault(false)
        }
    }

    private fun uniqueLegacyFile(directory: File, requestedName: String): File {
        val name = uniqueCopyName(requestedName) { File(directory, it).exists() }
        return File(directory, name)
    }

    private fun uniqueCopyName(requestedName: String, exists: (String) -> Boolean): String {
        if (!exists(requestedName)) return requestedName
        val dot = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
        val base = requestedName.substring(0, dot)
        val extension = requestedName.substring(dot)
        var number = 1
        while (true) {
            val candidate = "$base ($number)$extension"
            if (!exists(candidate)) return candidate
            number++
        }
    }

    private fun safeCopyName(displayName: String): String {
        return displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace('\u0000', '_')
            .trim()
            .takeUnless { it.isEmpty() || it == "." || it == ".." }
            ?: "Frame Finder photo"
    }

    private fun openInGallery(photo: PhotoGridItem) {
        val uri = Uri.parse(photo.contentUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, photo.mimeType ?: "image/*")
            clipData = ClipData.newUri(contentResolver, photo.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(intent, "Open photo", "No gallery app is available to open this photo.")
    }

    private fun launchChooser(intent: Intent, title: String, failureMessage: String) {
        try {
            startActivity(Intent.createChooser(intent, title))
        } catch (_: Exception) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun photoAccess(): PhotoAccess {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val full = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
                val selected = hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                PhotoAccess(hasAny = full || selected, hasFull = full)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val full = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
                PhotoAccess(full, full)
            }
            else -> {
                val full = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                PhotoAccess(full, full)
            }
        }
    }

    private fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun shouldRequestNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun formatPhotoCount(count: Int): String {
        val formatted = NumberFormat.getIntegerInstance().format(count)
        return "$formatted ${if (count == 1) "photo" else "photos"}"
    }

    private data class PhotoAccess(val hasAny: Boolean, val hasFull: Boolean)
    private data class IndexRequest(
        val analyzeAll: Boolean,
        val forceRebuild: Boolean = false,
        val timeWarningConfirmed: Boolean = false
    )
    private data class ScanWarningRequest(val request: IndexRequest, val photoCount: Int)
    private data class PendingDelete(
        val photos: List<PhotoGridItem>,
        val mode: DeleteApprovalMode,
        val remaining: List<PhotoGridItem> = emptyList(),
        val deleted: List<PhotoGridItem> = emptyList(),
        val failed: Int = 0
    )

    private enum class CredentialAction { UNLOCK, ENABLE, DISABLE }
    private enum class PhotoRemoval { TRASH, DELETE }
    private enum class DeleteApprovalMode {
        SYSTEM_DELETES,
        SYSTEM_TRASHES,
        SYSTEM_RESTORES,
        RETRY_DELETE,
        DIRECT_AFTER_PERMISSION
    }

    private sealed interface Destination {
        data object Home : Destination
        data object Settings : Destination
        data object Trash : Destination
        data class Viewer(val photos: List<PhotoGridItem>, val initialIndex: Int) : Destination
    }

    private companion object {
        const val LOG_TAG = "FrameFinder"
        const val LARGE_LIBRARY_WARNING_THRESHOLD = 1_000
        const val SAVED_PHOTO_DIRECTORY = "Frame Finder"
    }
}
