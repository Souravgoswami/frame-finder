package com.souravgoswami.framefinder.data

import android.content.Context
import android.content.SharedPreferences
import com.souravgoswami.framefinder.ai.SearchModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ThemeMode { SYSTEM, DARK_GRAY, LIGHT, OLED }
enum class LaunchView { SEARCH, PHOTO_LIBRARY }

data class AppPreferences(
    val initialModelSelected: Boolean,
    val themeMode: ThemeMode,
    val ocrSearchEnabled: Boolean,
    val visualSearchEnabled: Boolean,
    val filenameSearchEnabled: Boolean,
    val descriptionSearchEnabled: Boolean,
    val excludedFolders: Set<String>,
    val storagePathsIndexed: Boolean,
    val searchModel: SearchModel,
    val searchStrictness: Int,
    val resultSort: ResultSort,
    val launchView: LaunchView,
    val photoLayout: PhotoLayout,
    val thumbnailDensity: ThumbnailDensity,
    val galleryGrouping: GalleryGrouping,
    val useSystemTrash: Boolean,
    val ignoreFailedPhotos: Boolean,
    val appLockEnabled: Boolean,
    val scanPaused: Boolean,
    val scanAnalyzeAllPending: Boolean,
    val scanForceRebuildPending: Boolean,
    val lastIndexedPhotoTimestamp: Long
)

class FrameFinderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("framefinder", Context.MODE_PRIVATE)

    init {
        val existingInstallation = preferences.all.isNotEmpty()
        val migration = preferences.edit().remove(LEGACY_KEY_SHOW_DOT_ITEMS)
        if (!preferences.contains(KEY_SEARCH_OCR)) {
            migration.putBoolean(
                KEY_SEARCH_OCR,
                preferences.getBoolean(LEGACY_KEY_OCR, true)
            )
        }
        migration.remove(LEGACY_KEY_OCR).apply()
        if (!preferences.contains(KEY_INITIAL_MODEL_SELECTED)) {
            // Existing installations already made an implicit model choice. Only a genuinely
            // empty preference store should enter the first run model selection screen.
            preferences.edit()
                .putBoolean(KEY_INITIAL_MODEL_SELECTED, existingInstallation)
                .apply()
        }
    }

    val values: Flow<AppPreferences> = callbackFlow {
        fun publish() { trySend(read()) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> publish() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        publish()
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun read(): AppPreferences = AppPreferences(
        initialModelSelected = preferences.getBoolean(KEY_INITIAL_MODEL_SELECTED, false),
        themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        ocrSearchEnabled = preferences.getBoolean(KEY_SEARCH_OCR, true),
        visualSearchEnabled = preferences.getBoolean(KEY_SEARCH_VISUAL, true),
        filenameSearchEnabled = preferences.getBoolean(KEY_SEARCH_FILENAMES, true),
        descriptionSearchEnabled = preferences.getBoolean(KEY_SEARCH_DESCRIPTIONS, true),
        excludedFolders = preferences.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet())
            .orEmpty()
            .mapNotNull(ExcludedFolders::normalize)
            .toSet(),
        storagePathsIndexed = preferences.getBoolean(KEY_STORAGE_PATHS_INDEXED, false),
        searchModel = SearchModel.fromId(preferences.getString(KEY_SEARCH_MODEL, null)),
        searchStrictness = preferences.getInt(KEY_SEARCH_STRICTNESS, DEFAULT_SEARCH_STRICTNESS)
            .coerceIn(SEARCH_STRICTNESS_MIN, SEARCH_STRICTNESS_MAX),
        resultSort = runCatching {
            ResultSort.valueOf(preferences.getString(KEY_RESULT_SORT, ResultSort.RELEVANCE.name)!!)
        }.getOrDefault(ResultSort.RELEVANCE),
        launchView = runCatching {
            LaunchView.valueOf(preferences.getString(KEY_LAUNCH_VIEW, LaunchView.SEARCH.name)!!)
        }.getOrDefault(LaunchView.SEARCH),
        photoLayout = runCatching {
            PhotoLayout.valueOf(preferences.getString(KEY_PHOTO_LAYOUT, PhotoLayout.GRID.name)!!)
        }.getOrDefault(PhotoLayout.GRID),
        thumbnailDensity = runCatching {
            ThumbnailDensity.valueOf(
                preferences.getString(KEY_THUMBNAIL_DENSITY, ThumbnailDensity.COMFORTABLE.name)!!
            )
        }.getOrDefault(ThumbnailDensity.COMFORTABLE),
        galleryGrouping = runCatching {
            GalleryGrouping.valueOf(preferences.getString(KEY_GALLERY_GROUPING, GalleryGrouping.DAY.name)!!)
        }.getOrDefault(GalleryGrouping.DAY),
        useSystemTrash = preferences.getBoolean(KEY_USE_SYSTEM_TRASH, true),
        ignoreFailedPhotos = preferences.getBoolean(KEY_IGNORE_FAILED_PHOTOS, false),
        appLockEnabled = preferences.getBoolean(KEY_APP_LOCK, false),
        scanPaused = preferences.getBoolean(KEY_SCAN_PAUSED, false),
        scanAnalyzeAllPending = preferences.getBoolean(KEY_SCAN_ANALYZE_ALL_PENDING, false),
        scanForceRebuildPending = preferences.getBoolean(KEY_SCAN_FORCE_REBUILD_PENDING, false),
        lastIndexedPhotoTimestamp = preferences.getLong(KEY_LAST_INDEXED_PHOTO_TIMESTAMP, 0L)
    )

    fun setThemeMode(value: ThemeMode) {
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun setOcrSearchEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SEARCH_OCR, value).apply()
    }

    fun setVisualSearchEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SEARCH_VISUAL, value).apply()
    }

    fun setFilenameSearchEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SEARCH_FILENAMES, value).apply()
    }

    fun setDescriptionSearchEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SEARCH_DESCRIPTIONS, value).apply()
    }

    fun setExcludedFolders(value: Set<String>) {
        val normalized = value.mapNotNull(ExcludedFolders::normalize).toSet()
        preferences.edit().putStringSet(KEY_EXCLUDED_FOLDERS, normalized).apply()
    }

    fun setSearchModel(value: SearchModel) {
        preferences.edit().putString(KEY_SEARCH_MODEL, value.id).apply()
    }

    fun completeInitialModelSelection(value: SearchModel, launchView: LaunchView) {
        preferences.edit()
            .putString(KEY_SEARCH_MODEL, value.id)
            .putString(KEY_LAUNCH_VIEW, launchView.name)
            .putBoolean(KEY_INITIAL_MODEL_SELECTED, true)
            .apply()
    }

    fun setSearchStrictness(value: Int) {
        preferences.edit()
            .putInt(KEY_SEARCH_STRICTNESS, value.coerceIn(SEARCH_STRICTNESS_MIN, SEARCH_STRICTNESS_MAX))
            .apply()
    }

    fun setResultSort(value: ResultSort) {
        preferences.edit().putString(KEY_RESULT_SORT, value.name).apply()
    }

    fun setLaunchView(value: LaunchView) {
        preferences.edit().putString(KEY_LAUNCH_VIEW, value.name).apply()
    }

    fun setPhotoLayout(value: PhotoLayout) {
        preferences.edit().putString(KEY_PHOTO_LAYOUT, value.name).apply()
    }

    fun setThumbnailDensity(value: ThumbnailDensity) {
        preferences.edit().putString(KEY_THUMBNAIL_DENSITY, value.name).apply()
    }

    fun setGalleryGrouping(value: GalleryGrouping) {
        preferences.edit().putString(KEY_GALLERY_GROUPING, value.name).apply()
    }

    fun setUseSystemTrash(value: Boolean) {
        preferences.edit().putBoolean(KEY_USE_SYSTEM_TRASH, value).apply()
    }

    fun setIgnoreFailedPhotos(value: Boolean) {
        preferences.edit().putBoolean(KEY_IGNORE_FAILED_PHOTOS, value).apply()
    }

    fun setAppLockEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_APP_LOCK, value).apply()
    }

    fun setScanStarted(analyzeAll: Boolean, forceRebuild: Boolean = false) {
        preferences.edit()
            .putBoolean(KEY_SCAN_PAUSED, false)
            .putBoolean(KEY_SCAN_ANALYZE_ALL_PENDING, analyzeAll)
            .putBoolean(KEY_SCAN_FORCE_REBUILD_PENDING, forceRebuild)
            .apply()
    }

    fun setScanPaused() {
        preferences.edit().putBoolean(KEY_SCAN_PAUSED, true).apply()
    }

    fun setForceRebuildPrepared() {
        preferences.edit().putBoolean(KEY_SCAN_FORCE_REBUILD_PENDING, false).apply()
    }

    fun setScanCompleted(lastIndexedPhotoTimestamp: Long? = null) {
        val editor = preferences.edit()
            .putBoolean(KEY_SCAN_PAUSED, false)
            .putBoolean(KEY_SCAN_ANALYZE_ALL_PENDING, false)
            .putBoolean(KEY_SCAN_FORCE_REBUILD_PENDING, false)
        lastIndexedPhotoTimestamp?.let {
            editor.putLong(KEY_LAST_INDEXED_PHOTO_TIMESTAMP, it.coerceAtLeast(0L))
        }
        editor.apply()
    }

    fun setScanStopped() {
        preferences.edit()
            .putBoolean(KEY_SCAN_PAUSED, false)
            .putBoolean(KEY_SCAN_ANALYZE_ALL_PENDING, false)
            .putBoolean(KEY_SCAN_FORCE_REBUILD_PENDING, false)
            .apply()
    }

    fun setLastIndexedPhotoTimestamp(value: Long) {
        preferences.edit().putLong(KEY_LAST_INDEXED_PHOTO_TIMESTAMP, value.coerceAtLeast(0L)).apply()
    }

    fun setStoragePathsIndexed() {
        preferences.edit().putBoolean(KEY_STORAGE_PATHS_INDEXED, true).apply()
    }

    private companion object {
        const val KEY_INITIAL_MODEL_SELECTED = "initial_model_selected_v1"
        const val KEY_THEME = "theme"
        const val KEY_SEARCH_OCR = "search_ocr"
        const val LEGACY_KEY_OCR = "ocr_enabled"
        const val KEY_SEARCH_VISUAL = "search_visual"
        const val KEY_SEARCH_FILENAMES = "search_filenames"
        const val KEY_SEARCH_DESCRIPTIONS = "search_descriptions"
        const val LEGACY_KEY_SHOW_DOT_ITEMS = "show_dot_items"
        const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
        const val KEY_STORAGE_PATHS_INDEXED = "storage_paths_indexed_v1"
        const val KEY_SEARCH_MODEL = "search_model"
        const val KEY_SEARCH_STRICTNESS = "search_strictness"
        const val KEY_RESULT_SORT = "result_sort"
        const val KEY_LAUNCH_VIEW = "launch_view"
        const val KEY_PHOTO_LAYOUT = "photo_layout"
        const val KEY_THUMBNAIL_DENSITY = "thumbnail_density"
        const val KEY_GALLERY_GROUPING = "gallery_grouping"
        const val KEY_USE_SYSTEM_TRASH = "use_system_trash"
        const val KEY_IGNORE_FAILED_PHOTOS = "ignore_failed_photos"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_SCAN_PAUSED = "scan_paused"
        const val KEY_SCAN_ANALYZE_ALL_PENDING = "scan_rebuild_pending"
        const val KEY_SCAN_FORCE_REBUILD_PENDING = "scan_force_rebuild_pending"
        const val KEY_LAST_INDEXED_PHOTO_TIMESTAMP = "last_indexed_photo_timestamp"
    }
}
