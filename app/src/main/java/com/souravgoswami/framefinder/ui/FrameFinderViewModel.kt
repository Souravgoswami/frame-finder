package com.souravgoswami.framefinder.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.souravgoswami.framefinder.ai.SearchModel
import com.souravgoswami.framefinder.appContainer
import com.souravgoswami.framefinder.data.AppPreferences
import com.souravgoswami.framefinder.data.DEFAULT_SEARCH_STRICTNESS
import com.souravgoswami.framefinder.data.ExcludedFolders
import com.souravgoswami.framefinder.data.GalleryGrouping
import com.souravgoswami.framefinder.data.GalleryItem
import com.souravgoswami.framefinder.data.IndexedPhotoMetadata
import com.souravgoswami.framefinder.data.LaunchView
import com.souravgoswami.framefinder.data.PhotoGridItem
import com.souravgoswami.framefinder.data.PhotoLayout
import com.souravgoswami.framefinder.data.ResultSort
import com.souravgoswami.framefinder.data.SearchResult
import com.souravgoswami.framefinder.data.ThemeMode
import com.souravgoswami.framefinder.data.ThumbnailDensity
import com.souravgoswami.framefinder.data.TrashedPhoto
import com.souravgoswami.framefinder.media.MediaStoreScanner
import com.souravgoswami.framefinder.work.WorkContract
import com.souravgoswami.framefinder.work.WorkScheduler
import com.souravgoswami.framefinder.work.WorkContract.KEY_ESTIMATED_REMAINING_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.text.NumberFormat

data class BackgroundWorkState(
    val running: Boolean = false,
    val phase: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val estimatedRemainingMillis: Long? = null,
    val error: String? = null
)

data class FrameFinderUiState(
    val results: List<SearchResult> = emptyList(),
    val query: String = "",
    val sort: ResultSort = ResultSort.RELEVANCE,
    val indexedCount: Int = 0,
    val indexStatusLoaded: Boolean = false,
    val embeddedCount: Int = 0,
    val embeddingErrorCount: Int = 0,
    val missingEmbeddingCount: Int = 0,
    val ocrCount: Int = 0,
    val ocrErrorCount: Int = 0,
    val missingOcrCount: Int = 0,
    val modelReady: Boolean = false,
    val searching: Boolean = false,
    val contentRefreshing: Boolean = false,
    val newPhotosAvailable: Boolean = false,
    val availableStorageFolders: List<String> = emptyList(),
    val allowedPhotoCount: Int? = null,
    val storageFoldersLoading: Boolean = false,
    val trashedPhotos: List<TrashedPhoto> = emptyList(),
    val trashLoading: Boolean = false,
    val trashError: String? = null,
    val analysisBackupRunning: Boolean = false,
    val analysisBackupStatus: String? = null,
    val analysisBackupError: String? = null,
    val work: BackgroundWorkState = BackgroundWorkState(),
    val preferences: AppPreferences = AppPreferences(
        initialModelSelected = false,
        themeMode = ThemeMode.SYSTEM,
        ocrSearchEnabled = true,
        visualSearchEnabled = true,
        filenameSearchEnabled = true,
        descriptionSearchEnabled = true,
        excludedFolders = emptySet(),
        storagePathsIndexed = false,
        searchModel = SearchModel.MOBILECLIP_S2,
        searchStrictness = DEFAULT_SEARCH_STRICTNESS,
        resultSort = ResultSort.RELEVANCE,
        launchView = LaunchView.SEARCH,
        photoLayout = PhotoLayout.GRID,
        thumbnailDensity = ThumbnailDensity.COMFORTABLE,
        galleryGrouping = GalleryGrouping.DAY,
        useSystemTrash = true,
        ignoreFailedPhotos = false,
        appLockEnabled = false,
        scanPaused = false,
        scanAnalyzeAllPending = false,
        scanForceRebuildPending = false,
        lastIndexedPhotoTimestamp = 0L
    )
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FrameFinderViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer()
    private val repository = container.repository
    private val workManager = WorkManager.getInstance(application)
    private val mediaStoreScanner = MediaStoreScanner(application)
    private val initialPreferences = container.preferences.read()
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(initialPreferences.resultSort)
    private val ocrSearchEnabled = MutableStateFlow(initialPreferences.ocrSearchEnabled)
    private val visualSearchEnabled = MutableStateFlow(initialPreferences.visualSearchEnabled)
    private val filenameSearchEnabled = MutableStateFlow(initialPreferences.filenameSearchEnabled)
    private val descriptionSearchEnabled = MutableStateFlow(initialPreferences.descriptionSearchEnabled)
    private val selectedModel = MutableStateFlow(initialPreferences.searchModel)
    private val ignoreFailedPhotos = MutableStateFlow(initialPreferences.ignoreFailedPhotos)
    private val searchStrictness = MutableStateFlow(initialPreferences.searchStrictness)
    private val galleryGrouping = MutableStateFlow(initialPreferences.galleryGrouping)
    private val galleryRevision = MutableStateFlow(0L)
    private val searchRevision = MutableStateFlow(0L)
    private val _state = MutableStateFlow(
        FrameFinderUiState(
            sort = initialPreferences.resultSort,
            modelReady = container.modelManager.isReady(initialPreferences.searchModel),
            preferences = initialPreferences
        )
    )
    val state: StateFlow<FrameFinderUiState> = _state.asStateFlow()
    val gallery: kotlinx.coroutines.flow.Flow<PagingData<GalleryItem>> = combine(
        galleryRevision,
        sort,
        galleryGrouping
    ) { _, currentSort, grouping -> GalleryInputs(currentSort, grouping) }
        .flatMapLatest { repository.pagedPhotos(it.sort, it.grouping) }
        .cachedIn(viewModelScope)

    private var indexWork: List<WorkInfo> = emptyList()
    private var lastCompletedIndexWork: UUID? = null
    private var newPhotoCheckJob: Job? = null
    private var contentRefreshJob: Job? = null
    private val indexObserver = Observer<List<WorkInfo>> { indexWork = it.orEmpty(); updateWork() }
    private val indexLiveData = workManager.getWorkInfosForUniqueWorkLiveData(WorkContract.INDEX_WORK)

    init {
        indexLiveData.observeForever(indexObserver)

        viewModelScope.launch {
            repository.syncExcludedFolders(initialPreferences.excludedFolders)
            galleryRevision.value++
            searchRevision.value++
        }

        viewModelScope.launch {
            val baseSearchInputs = combine(
                query.debounce(SEARCH_DEBOUNCE_MILLIS),
                sort,
                ocrSearchEnabled,
                selectedModel
            ) { currentQuery, currentSort, includeOcr, model ->
                SearchInputs(currentQuery, currentSort, includeOcr, model, DEFAULT_SEARCH_STRICTNESS)
            }
            val searchInputs = combine(baseSearchInputs, searchStrictness) { inputs, strictness ->
                inputs.copy(searchStrictness = strictness)
            }
            val searchSources = combine(
                visualSearchEnabled,
                filenameSearchEnabled,
                descriptionSearchEnabled
            ) { includeVisual, includeFilename, includeDescription ->
                SearchSources(includeVisual, includeFilename, includeDescription)
            }
            combine(searchInputs, searchSources, searchRevision, query) { inputs, sources, _, liveQuery ->
                LiveSearchInputs(
                    inputs = inputs.copy(
                        includeVisual = sources.includeVisual,
                        includeFilename = sources.includeFilename,
                        includeDescription = sources.includeDescription
                    ),
                    liveQuery = liveQuery
                )
            }.collectLatest { request ->
                if (request.inputs.query != request.liveQuery) return@collectLatest
                val inputs = request.inputs
                _state.value = _state.value.copy(
                    sort = inputs.sort,
                    searching = inputs.query.isNotBlank()
                )
                val searched = repository.search(
                    inputs.query,
                    inputs.sort,
                    inputs.includeVisual,
                    inputs.includeFilename,
                    inputs.includeDescription,
                    inputs.includeOcr,
                    inputs.model,
                    inputs.searchStrictness
                )
                val results = removeMissingPhotos(searched)
                _state.value = _state.value.copy(
                    results = results,
                    searching = false,
                    contentRefreshing = false
                )
            }
        }
        viewModelScope.launch {
            val aiCounts = selectedModel.flatMapLatest { model ->
                combine(
                    repository.embeddedCount(model),
                    repository.embeddingErrorCount(model),
                    repository.missingEmbeddingCount(model, ignoreFailures = false)
                ) { embedded, errors, missing ->
                    Triple(embedded, errors, missing)
                }
            }
            val textCounts = combine(
                repository.ocrCount,
                repository.ocrErrorCount,
                repository.missingOcrCount(ignoreFailures = false)
            ) { completed, errors, missing ->
                Triple(completed, errors, missing)
            }
            combine(
                repository.indexedCount,
                aiCounts,
                textCounts,
                ignoreFailedPhotos
            ) { total, ai, text, ignoreFailures ->
                IndexCounts(
                    total = total,
                    embedded = ai.first,
                    embeddingErrors = ai.second,
                    missingEmbeddings = actionableMissingCount(ai.third, ai.second, ignoreFailures),
                    ocr = text.first,
                    ocrErrors = text.second,
                    missingOcr = actionableMissingCount(text.third, text.second, ignoreFailures)
                )
            }.collect { counts ->
                    _state.value = _state.value.copy(
                        indexedCount = counts.total,
                        indexStatusLoaded = true,
                        embeddedCount = counts.embedded,
                        embeddingErrorCount = counts.embeddingErrors,
                        missingEmbeddingCount = counts.missingEmbeddings,
                        ocrCount = counts.ocr,
                        ocrErrorCount = counts.ocrErrors,
                        missingOcrCount = counts.missingOcr
                    )
                }
        }
        viewModelScope.launch {
            container.preferences.values.collect { preferences ->
                ocrSearchEnabled.value = preferences.ocrSearchEnabled
                visualSearchEnabled.value = preferences.visualSearchEnabled
                filenameSearchEnabled.value = preferences.filenameSearchEnabled
                descriptionSearchEnabled.value = preferences.descriptionSearchEnabled
                selectedModel.value = preferences.searchModel
                ignoreFailedPhotos.value = preferences.ignoreFailedPhotos
                searchStrictness.value = preferences.searchStrictness
                sort.value = preferences.resultSort
                galleryGrouping.value = preferences.galleryGrouping
                _state.value = _state.value.copy(
                    sort = preferences.resultSort,
                    preferences = preferences,
                    modelReady = container.modelManager.isReady(preferences.searchModel)
                )
            }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value, searching = value.isNotBlank())
        query.value = value
    }

    fun setSort(value: ResultSort) {
        _state.value = _state.value.copy(sort = value)
        sort.value = value
        container.preferences.setResultSort(value)
    }

    fun refreshContent() {
        if (
            _state.value.contentRefreshing ||
            _state.value.work.running ||
            !_state.value.indexStatusLoaded ||
            _state.value.indexedCount == 0
        ) return
        _state.value = _state.value.copy(contentRefreshing = true)
        contentRefreshJob?.cancel()
        contentRefreshJob = viewModelScope.launch {
            try {
                val preferences = container.preferences.read()
                val scanToken = System.currentTimeMillis()
                val discovered = withContext(Dispatchers.IO) {
                    mediaStoreScanner.discover(
                        scanToken = scanToken,
                        excludedFolders = preferences.excludedFolders
                    )
                }
                repository.syncPhotoIndex(discovered, scanToken)
                galleryRevision.value++
                searchRevision.value++
                refreshNewPhotoStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshNewPhotoStatus()
            } finally {
                _state.value = _state.value.copy(contentRefreshing = false)
            }
        }
    }

    fun prepareAndIndex(analyzeAll: Boolean = false, forceRebuild: Boolean = false) {
        val model = selectedModel.value
        val preferences = container.preferences.read()
        val shouldForceRebuild = if (preferences.scanPaused) {
            preferences.scanForceRebuildPending
        } else {
            forceRebuild || preferences.scanForceRebuildPending
        }
        val shouldAnalyzeAll = shouldForceRebuild || analyzeAll || _state.value.indexedCount == 0 ||
            preferences.scanAnalyzeAllPending ||
            (preferences.scanPaused && preferences.lastIndexedPhotoTimestamp <= 0L)
        newPhotoCheckJob?.cancel()
        _state.value = _state.value.copy(newPhotosAvailable = false)
        container.preferences.setScanStarted(shouldAnalyzeAll, shouldForceRebuild)
        WorkScheduler.index(getApplication(), shouldAnalyzeAll, shouldForceRebuild, model)
    }

    fun refreshNewPhotoStatus() {
        if (!_state.value.indexStatusLoaded || _state.value.indexedCount == 0 || _state.value.work.running) return
        newPhotoCheckJob?.cancel()
        newPhotoCheckJob = viewModelScope.launch {
            var indexedThrough = container.preferences.read().lastIndexedPhotoTimestamp
            if (indexedThrough <= 0L) {
                indexedThrough = repository.newestIndexedSortTime()
                if (indexedThrough > 0L) container.preferences.setLastIndexedPhotoTimestamp(indexedThrough)
            }
            val hasNewPhotos = withContext(Dispatchers.IO) {
                runCatching {
                    mediaStoreScanner.hasPhotoNewerThan(
                        indexedThrough,
                        container.preferences.read().excludedFolders
                    )
                }.getOrDefault(false)
            }
            if (!_state.value.work.running) {
                _state.value = _state.value.copy(newPhotosAvailable = hasNewPhotos)
            }
        }
    }

    fun clearNewPhotoNotice() {
        newPhotoCheckJob?.cancel()
        _state.value = _state.value.copy(newPhotosAvailable = false)
    }

    suspend fun indexedPhotoMetadata(mediaId: Long): IndexedPhotoMetadata? =
        repository.indexedMetadata(mediaId, selectedModel.value)

    fun pauseWork() {
        if (!_state.value.work.running || _state.value.preferences.scanPaused) return
        container.preferences.setScanPaused()
        _state.value = _state.value.copy(
            preferences = _state.value.preferences.copy(scanPaused = true),
            work = _state.value.work.copy(
                phase = WorkContract.PHASE_PAUSING,
                estimatedRemainingMillis = null
            )
        )
    }

    fun cancelPausedWork() {
        WorkScheduler.cancel(getApplication())
        container.preferences.setScanStopped()
        _state.value = _state.value.copy(
            preferences = _state.value.preferences.copy(
                scanPaused = false,
                scanAnalyzeAllPending = false,
                scanForceRebuildPending = false
            ),
            work = BackgroundWorkState()
        )
    }

    fun setTheme(value: ThemeMode) = container.preferences.setThemeMode(value)
    fun setOcrSearchEnabled(value: Boolean) = container.preferences.setOcrSearchEnabled(value)
    fun setVisualSearchEnabled(value: Boolean) = container.preferences.setVisualSearchEnabled(value)
    fun setFilenameSearchEnabled(value: Boolean) = container.preferences.setFilenameSearchEnabled(value)
    fun setDescriptionSearchEnabled(value: Boolean) = container.preferences.setDescriptionSearchEnabled(value)
    fun refreshStorageFolders() {
        _state.value = _state.value.copy(storageFoldersLoading = true)
        viewModelScope.launch {
            val folderState = withContext(Dispatchers.IO) {
                runCatching {
                    mediaStoreScanner.storageFolders() to mediaStoreScanner.photoCount(
                        container.preferences.read().excludedFolders
                    )
                }.getOrNull()
            }
            val folders = (folderState?.first.orEmpty() + container.preferences.read().excludedFolders)
                .mapNotNull(ExcludedFolders::normalize)
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            _state.value = _state.value.copy(
                availableStorageFolders = folders,
                allowedPhotoCount = folderState?.second,
                storageFoldersLoading = false
            )
        }
    }

    fun setFolderExcluded(folder: String, excluded: Boolean) {
        if (_state.value.work.running) return
        val normalized = ExcludedFolders.normalize(folder) ?: return
        val current = container.preferences.read().excludedFolders
        val updated = if (excluded) current + normalized else current - normalized
        container.preferences.setExcludedFolders(updated)
        _state.value = _state.value.copy(
            preferences = _state.value.preferences.copy(excludedFolders = updated),
            availableStorageFolders = (_state.value.availableStorageFolders + normalized)
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        )
        viewModelScope.launch {
            repository.syncExcludedFolders(updated)
            galleryRevision.value++
            searchRevision.value++
            refreshStorageFolders()
            refreshNewPhotoStatus()
        }
    }
    fun setSearchStrictness(value: Int) = container.preferences.setSearchStrictness(value)
    fun setLaunchView(value: LaunchView) = container.preferences.setLaunchView(value)
    fun setPhotoLayout(value: PhotoLayout) = container.preferences.setPhotoLayout(value)
    fun setThumbnailDensity(value: ThumbnailDensity) = container.preferences.setThumbnailDensity(value)
    fun setUseSystemTrash(value: Boolean) = container.preferences.setUseSystemTrash(value)
    fun setIgnoreFailedPhotos(value: Boolean) {
        ignoreFailedPhotos.value = value
        _state.value = _state.value.copy(
            preferences = _state.value.preferences.copy(ignoreFailedPhotos = value)
        )
        container.preferences.setIgnoreFailedPhotos(value)
    }
    fun setGalleryGrouping(value: GalleryGrouping) {
        container.preferences.setGalleryGrouping(value)
        galleryGrouping.value = value
        if (
            value == GalleryGrouping.DAY || value == GalleryGrouping.MONTH
        ) {
            val currentSort = sort.value
            if (currentSort != ResultSort.NEWEST && currentSort != ResultSort.OLDEST) {
                setSort(ResultSort.NEWEST)
            }
        }
    }
    fun setAppLockEnabled(value: Boolean) = container.preferences.setAppLockEnabled(value)
    fun removePhotoFromIndex(mediaId: Long) {
        viewModelScope.launch {
            repository.removeFromIndex(mediaId)
            galleryRevision.value++
            searchRevision.value++
        }
    }
    fun removePhotosFromIndex(mediaIds: Collection<Long>) {
        val uniqueIds = mediaIds.distinct()
        if (uniqueIds.isEmpty()) return
        viewModelScope.launch {
            repository.removeFromIndex(uniqueIds)
            galleryRevision.value++
            searchRevision.value++
        }
    }
    fun setPhotosTrashed(mediaIds: Collection<Long>, trashed: Boolean) {
        val uniqueIds = mediaIds.distinct()
        if (uniqueIds.isEmpty()) return
        viewModelScope.launch {
            repository.setTrashed(uniqueIds, trashed)
            galleryRevision.value++
            searchRevision.value++
        }
    }
    fun refreshTrash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _state.value = _state.value.copy(
                trashedPhotos = emptyList(),
                trashLoading = false,
                trashError = null
            )
            return
        }
        _state.value = _state.value.copy(trashLoading = true, trashError = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { mediaStoreScanner.trashedPhotos() } }
            _state.value = result.fold(
                onSuccess = { photos ->
                    _state.value.copy(trashedPhotos = photos, trashLoading = false, trashError = null)
                },
                onFailure = { error ->
                    _state.value.copy(
                        trashLoading = false,
                        trashError = error.message ?: "The system trash could not be opened."
                    )
                }
            )
        }
    }
    fun exportAnalysis(uri: Uri) {
        if (_state.value.analysisBackupRunning || _state.value.work.running) return
        val model = _state.value.preferences.searchModel
        _state.value = _state.value.copy(
            analysisBackupRunning = true,
            analysisBackupStatus = "Exporting analysis data...",
            analysisBackupError = null
        )
        viewModelScope.launch {
            runCatching { container.analysisBackupManager.exportTo(uri, model) }
                .onSuccess { summary ->
                    _state.value = _state.value.copy(
                        analysisBackupRunning = false,
                        analysisBackupStatus = "Exported ${formatCount(summary.photos)} photos and " +
                            "${formatCount(summary.embeddings)} AI results.",
                        analysisBackupError = null
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        analysisBackupRunning = false,
                        analysisBackupStatus = null,
                        analysisBackupError = error.message ?: "Analysis data could not be exported."
                    )
                }
        }
    }

    fun importAnalysis(uri: Uri) {
        if (_state.value.analysisBackupRunning || _state.value.work.running) return
        _state.value = _state.value.copy(
            analysisBackupRunning = true,
            analysisBackupStatus = "Importing analysis data...",
            analysisBackupError = null
        )
        viewModelScope.launch {
            runCatching { container.analysisBackupManager.importFrom(uri) }
                .onSuccess { summary ->
                    summary.selectedModel?.let(::setSearchModel)
                    repository.syncExcludedFolders(container.preferences.read().excludedFolders)
                    galleryRevision.value++
                    searchRevision.value++
                    val recoveryNote = if (summary.missingEmbeddings > 0) {
                        " The backup ended before ${formatCount(summary.missingEmbeddings)} expected AI " +
                            "results. Complete analysis can restore them."
                    } else {
                        ""
                    }
                    val modelNote = summary.selectedModel?.let { model ->
                        " ${model.displayName} was selected from the backup."
                    }.orEmpty()
                    _state.value = _state.value.copy(
                        analysisBackupRunning = false,
                        analysisBackupStatus = "Imported ${formatCount(summary.photos)} photos and " +
                            "${formatCount(summary.embeddings)} AI results.$modelNote$recoveryNote",
                        analysisBackupError = null
                    )
                    refreshNewPhotoStatus()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        analysisBackupRunning = false,
                        analysisBackupStatus = null,
                        analysisBackupError = error.message ?: "Analysis data could not be imported."
                    )
                }
        }
    }
    fun setSearchModel(value: SearchModel) {
        if (value == selectedModel.value || _state.value.work.running) return
        container.preferences.setSearchModel(value)
        selectedModel.value = value
        _state.value = _state.value.copy(
            preferences = _state.value.preferences.copy(searchModel = value),
            modelReady = container.modelManager.isReady(value),
            embeddedCount = 0,
            embeddingErrorCount = 0,
            missingEmbeddingCount = _state.value.indexedCount
        )
        viewModelScope.launch { container.embedder.reset() }
    }

    fun completeInitialModelSelection(value: SearchModel, launchView: LaunchView) {
        if (_state.value.preferences.initialModelSelected || _state.value.work.running) return
        val modelChanged = value != selectedModel.value
        container.preferences.completeInitialModelSelection(value, launchView)
        selectedModel.value = value
        _state.value = _state.value.copy(
            preferences = _state.value.preferences.copy(
                initialModelSelected = true,
                searchModel = value,
                launchView = launchView
            ),
            modelReady = container.modelManager.isReady(value),
            embeddedCount = 0,
            embeddingErrorCount = 0,
            missingEmbeddingCount = _state.value.indexedCount
        )
        if (modelChanged) viewModelScope.launch { container.embedder.reset() }
    }

    private suspend fun removeMissingPhotos(results: List<SearchResult>): List<SearchResult> {
        if (results.isEmpty()) return results
        val candidateIds = results.map { it.photo.mediaId }
        val existingIds = try {
            withContext(Dispatchers.IO) { mediaStoreScanner.existingMediaIds(candidateIds) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return results
        }
        if (existingIds.size == candidateIds.size) return results
        val staleIds = candidateIds.filterNot(existingIds::contains)
        repository.removeMissingFromIndex(staleIds)
        galleryRevision.value++
        return results.filter { it.photo.mediaId in existingIds }
    }

    override fun onCleared() {
        indexLiveData.removeObserver(indexObserver)
        super.onCleared()
    }

    private fun updateWork() {
        val latest = listOfNotNull(indexWork.lastOrNull())
        val active = latest.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }
        val failed = latest.firstOrNull { it.state == WorkInfo.State.FAILED }
        val completed = latest.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
        if (completed != null && completed.id != lastCompletedIndexWork) {
            lastCompletedIndexWork = completed.id
            _state.value = _state.value.copy(newPhotosAvailable = false)
            galleryRevision.value++
            searchRevision.value++
        }
        val workState = when {
            active != null -> {
                val pauseRequested = container.preferences.read().scanPaused
                BackgroundWorkState(
                    running = true,
                    phase = if (pauseRequested) {
                        WorkContract.PHASE_PAUSING
                    } else {
                        active.progress.getString(WorkContract.KEY_PHASE)
                            ?: if (active.state == WorkInfo.State.ENQUEUED) "Waiting to start" else "Preparing"
                    },
                    done = active.progress.getInt(WorkContract.KEY_DONE, 0),
                    total = active.progress.getInt(WorkContract.KEY_TOTAL, 0),
                    estimatedRemainingMillis = if (pauseRequested) null else active.progress
                        .getLong(KEY_ESTIMATED_REMAINING_MS, -1L)
                        .takeIf { it >= 0L }
                )
            }
            failed != null -> BackgroundWorkState(error = failed.outputData.getString(WorkContract.KEY_ERROR) ?: "Background work failed")
            else -> BackgroundWorkState()
        }
        _state.value = _state.value.copy(
            work = workState,
            modelReady = container.modelManager.isReady(selectedModel.value)
        )
    }

    private data class SearchInputs(
        val query: String,
        val sort: ResultSort,
        val includeOcr: Boolean,
        val model: SearchModel,
        val searchStrictness: Int,
        val includeVisual: Boolean = true,
        val includeFilename: Boolean = true,
        val includeDescription: Boolean = true
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 750L
    }

    private data class SearchSources(
        val includeVisual: Boolean,
        val includeFilename: Boolean,
        val includeDescription: Boolean
    )

    private data class LiveSearchInputs(
        val inputs: SearchInputs,
        val liveQuery: String
    )

    private data class IndexCounts(
        val total: Int,
        val embedded: Int,
        val embeddingErrors: Int,
        val missingEmbeddings: Int,
        val ocr: Int,
        val ocrErrors: Int,
        val missingOcr: Int
    )

    private data class GalleryInputs(
        val sort: ResultSort,
        val grouping: GalleryGrouping
    )

    private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)
}

internal fun actionableMissingCount(
    missingCount: Int,
    failedCount: Int,
    ignoreFailures: Boolean
): Int = if (ignoreFailures) {
    (missingCount - failedCount).coerceAtLeast(0)
} else {
    missingCount.coerceAtLeast(0)
}
