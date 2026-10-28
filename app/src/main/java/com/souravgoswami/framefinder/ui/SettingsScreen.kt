package com.souravgoswami.framefinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souravgoswami.framefinder.BuildConfig
import com.souravgoswami.framefinder.ai.SearchModel
import com.souravgoswami.framefinder.data.LaunchView
import com.souravgoswami.framefinder.data.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: FrameFinderUiState,
    hasPhotoAccess: Boolean,
    hasFullPhotoAccess: Boolean,
    supportsSystemTrash: Boolean,
    onBack: () -> Unit,
    onRequestPhotoAccess: () -> Unit,
    onIndex: () -> Unit,
    onResume: () -> Unit,
    onAnalyzeAll: () -> Unit,
    onRebuild: () -> Unit,
    onCancelScan: () -> Unit,
    onCheckRestoredFolders: () -> Unit,
    onPause: () -> Unit,
    onSearchModel: (SearchModel) -> Unit,
    onSearchStrictness: (Int) -> Unit,
    onVisualSearch: (Boolean) -> Unit,
    onFilenameSearch: (Boolean) -> Unit,
    onDescriptionSearch: (Boolean) -> Unit,
    onUseSystemTrash: (Boolean) -> Unit,
    onIgnoreFailedPhotos: (Boolean) -> Unit,
    onOpenTrash: () -> Unit,
    onExportAnalysis: () -> Unit,
    onImportAnalysis: () -> Unit,
    onLaunchView: (LaunchView) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onFolderExcluded: (String, Boolean) -> Unit,
    onOcr: (Boolean) -> Unit,
    onAppLock: (Boolean) -> Unit
) {
    var showExcludedFolders by remember { mutableStateOf(false) }
    var foldersNeedingIndexCheck by remember { mutableStateOf(emptySet<String>()) }
    fun updateFolderExclusion(folder: String, excluded: Boolean) {
        onFolderExcluded(folder, excluded)
        foldersNeedingIndexCheck = if (excluded) {
            foldersNeedingIndexCheck - folder
        } else {
            foldersNeedingIndexCheck + folder
        }
    }
    fun closeExcludedFolders() {
        showExcludedFolders = false
        if (foldersNeedingIndexCheck.isNotEmpty()) {
            foldersNeedingIndexCheck = emptySet()
            onCheckRestoredFolders()
        }
    }
    if (showExcludedFolders) {
        AlertDialog(
            onDismissRequest = ::closeExcludedFolders,
            title = { Text("Excluded folders") },
            text = {
                when {
                    state.storageFoldersLoading -> Text("Loading folders...")
                    state.availableStorageFolders.isEmpty() -> Text("No photo folders are available.")
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(state.availableStorageFolders, key = { it }) { folder ->
                            val excluded = folder in state.preferences.excludedFolders
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !state.work.running) {
                                        updateFolderExclusion(folder, !excluded)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = excluded,
                                    onCheckedChange = { updateFolderExclusion(folder, it) },
                                    enabled = !state.work.running
                                )
                                Text(folder, Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = ::closeExcludedFolders) { Text("Done") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Settings",
                modifier = Modifier.offset(x = (-12).dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("AI search model")
        SettingsCard {
            SearchModel.entries.forEachIndexed { index, model ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !state.work.running) { onSearchModel(model) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.preferences.searchModel == model,
                        onClick = { onSearchModel(model) },
                        enabled = !state.work.running
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${model.displayName} · ${model.tier}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${model.sizeMegabytes} MB · ${model.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != SearchModel.entries.lastIndex) Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Both models are included in Frame Finder and work offline. AI results are saved separately for each model. Switching back to a model you already used does not require another analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Excluded folders")
        SettingsCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Folders Frame Finder ignores", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.preferences.excludedFolders.isEmpty()) "No folders excluded"
                        else "${state.preferences.excludedFolders.size} folders excluded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { showExcludedFolders = true },
                    enabled = hasPhotoAccess && !state.work.running
                ) {
                    Text("Manage")
                }
            }
            Text(
                "Excluded photos are hidden without deleting saved analysis. Allowing a folder restores saved results immediately and offers to check for photos added while it was ignored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))

        SectionTitle("Search matching")
        SettingsCard {
            var strictness by remember(state.preferences.searchStrictness) {
                mutableFloatStateOf(state.preferences.searchStrictness.toFloat())
            }
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
            Text(
                "Filters weaker visual matches relative to the best result. Recognized text stays searchable, but visual similarity leads the relevance order. No new analysis is needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))

        SectionTitle("On launch")
        SettingsCard {
            LaunchView.entries.forEach { launchView ->
                val title = when (launchView) {
                    LaunchView.SEARCH -> "Search first"
                    LaunchView.PHOTO_LIBRARY -> "Photo library"
                }
                val description = when (launchView) {
                    LaunchView.SEARCH -> "Show a search prompt without loading the photo grid."
                    LaunchView.PHOTO_LIBRARY -> "Show the newest indexed photos when no search is active."
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onLaunchView(launchView) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.preferences.launchView == launchView,
                        onClick = { onLaunchView(launchView) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionTitle("Index")
        SettingsCard {
            StatusRow("Photo access", when {
                !hasPhotoAccess -> "Not allowed"
                hasFullPhotoAccess -> "All photos"
                else -> "Selected photos"
            })
            StatusRow("Photos in index", if (state.indexStatusLoaded) state.indexedCount.toString() else "Loading…")
            state.allowedPhotoCount?.let { allowedCount ->
                StatusRow("Photos currently allowed", allowedCount.toString())
                val missingCount = (allowedCount - state.indexedCount).coerceAtLeast(0)
                if (missingCount > 0 && !state.work.running) {
                    Text(
                        "$missingCount allowed photos are not indexed. Reanalyze can add them without discarding completed results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            StatusRow(
                "AI analysis with selected model",
                "${state.embeddedCount} of ${state.indexedCount} completed"
            )
            if (state.embeddingErrorCount > 0) {
                StatusRow("AI analysis failures", state.embeddingErrorCount.toString())
            }
            StatusRow(
                "Text recognition",
                "${state.ocrCount} of ${state.indexedCount} checked"
            )
            if (state.ocrErrorCount > 0) {
                StatusRow("Text recognition failures", state.ocrErrorCount.toString())
            }
            if (state.embeddingErrorCount > 0 || state.ocrErrorCount > 0) {
                Text(
                    "Reanalyze retries failed photos without removing completed results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.preferences.ignoreFailedPhotos,
                        onCheckedChange = onIgnoreFailedPhotos
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Ignore failed photos in the future", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Known failures will not create incomplete analysis notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.newPhotosAvailable) StatusRow("New photos", "Ready to index")
            StatusRow(
                "AI model",
                if (state.modelReady) "Included (${state.preferences.searchModel.sizeMegabytes} MB)"
                else "Bundled model unavailable"
            )
            if (state.work.error != null) {
                Text(state.work.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.preferences.scanPaused && !state.work.running) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Scan paused. Everything completed so far remains searchable. Resume skips finished photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            if (!hasPhotoAccess || !hasFullPhotoAccess) {
                UtilityButton(
                    if (hasPhotoAccess) "Choose more photos" else "Allow photo access",
                    onRequestPhotoAccess,
                    Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.modelReady) {
                if (state.preferences.scanPaused) {
                    UtilityButton(
                        "Resume scan",
                        onResume,
                        Modifier.fillMaxWidth(),
                        primary = true,
                        enabled = hasPhotoAccess && !state.work.running
                    )
                    Spacer(Modifier.height(6.dp))
                    UtilityButton(
                        "Cancel scan",
                        onCancelScan,
                        Modifier.fillMaxWidth(),
                        enabled = !state.work.running
                    )
                } else if (state.indexedCount == 0) {
                    UtilityButton(
                        "Analyze now",
                        onAnalyzeAll,
                        Modifier.fillMaxWidth(),
                        primary = true,
                        enabled = hasPhotoAccess && !state.work.running && state.indexStatusLoaded
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Creates the first searchable index for the photos you allowed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    UtilityButton(
                        "Index new photos",
                        onIndex,
                        Modifier.fillMaxWidth(),
                        primary = true,
                        enabled = hasPhotoAccess && !state.work.running
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Checks only photos added since the last completed scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    UtilityButton(
                        if (state.embeddedCount > 0) "Reanalyze" else "Analyze now",
                        onAnalyzeAll,
                        Modifier.fillMaxWidth(),
                        enabled = hasPhotoAccess && !state.work.running
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Checks the full library for changed, failed, or missing analysis. Completed unchanged photos are reused.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    UtilityButton(
                        "Rebuild index",
                        onRebuild,
                        Modifier.fillMaxWidth(),
                        enabled = hasPhotoAccess && !state.work.running
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Removes saved analysis for the selected model and saved text recognition, removes missing photos, then analyzes every currently allowed photo again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.work.running) {
                Spacer(Modifier.height(10.dp))
                WorkProgressCard(state.work, onPause)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Analysis backup")
        SettingsCard {
            Text(
                "Export the local index so OCR and AI analysis can be restored after reinstalling Frame Finder. Original photos are not included.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "The backup contains filenames, storage references, descriptions, recognized text, and image embeddings. It is compressed but not encrypted, so store it somewhere private.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UtilityButton(
                    text = "Export analysis",
                    onClick = onExportAnalysis,
                    modifier = Modifier.weight(1f),
                    primary = true,
                    enabled = state.indexedCount > 0 && !state.work.running && !state.analysisBackupRunning
                )
                UtilityButton(
                    text = "Import analysis",
                    onClick = onImportAnalysis,
                    modifier = Modifier.weight(1f),
                    enabled = !state.work.running && !state.analysisBackupRunning
                )
            }
            if (state.analysisBackupRunning) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.analysisBackupStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            state.analysisBackupError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Import merges the backup with the current index. Reanalyze can validate photos that changed after the backup was created.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Photo removal")
        SettingsCard {
            SettingsSwitch(
                title = "Use system trash",
                description = if (supportsSystemTrash) {
                    "Move photos to recoverable Android trash by default. Permanent deletion remains available from the photo menu."
                } else {
                    "System trash requires Android 11 or newer."
                },
                checked = supportsSystemTrash && state.preferences.useSystemTrash,
                enabled = supportsSystemTrash,
                onCheckedChange = onUseSystemTrash
            )
            if (supportsSystemTrash) {
                Spacer(Modifier.height(8.dp))
                UtilityButton(
                    text = "Manage trash",
                    onClick = onOpenTrash,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasPhotoAccess
                )
                Text(
                    "Frame Finder keeps saved AI and text analysis while a photo is in trash. Restoring it makes the saved index available again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Search sources")
        SettingsCard {
            SettingsSwitch(
                title = "Visual similarity",
                description = "Use the selected AI model to find photos by their visual content.",
                checked = state.preferences.visualSearchEnabled,
                onCheckedChange = onVisualSearch
            )
            Spacer(Modifier.height(10.dp))
            SettingsSwitch(
                title = "Photo filenames",
                description = "Use the filename supplied by Android when scoring search results.",
                checked = state.preferences.filenameSearchEnabled,
                onCheckedChange = onFilenameSearch
            )
            Spacer(Modifier.height(10.dp))
            SettingsSwitch(
                title = "Photo descriptions",
                description = "Use descriptions provided by gallery or camera apps. Frame Finder does not generate captions.",
                checked = state.preferences.descriptionSearchEnabled,
                onCheckedChange = onDescriptionSearch
            )
            Spacer(Modifier.height(10.dp))
            SettingsSwitch(
                title = "Text inside photos",
                description = "Use locally recognized Latin script text when scoring search results.",
                checked = state.preferences.ocrSearchEnabled,
                onCheckedChange = onOcr
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Changes affect search immediately and do not delete saved data. Text recognition is always performed during analysis so it can be used at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("App lock")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Require device unlock", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Protect Frame Finder with the PIN, pattern, or password already used to unlock this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FrameFinderSwitch(
                    checked = state.preferences.appLockEnabled,
                    onCheckedChange = onAppLock
                )
            }
            Text(
                "Frame Finder does not store a separate password. App lock protects access to the app but does not encrypt the photo index.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Display mode")
        SettingsCard {
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.DARK_GRAY -> "Dark Gray Mode"
                    ThemeMode.LIGHT -> "Light Mode"
                    ThemeMode.OLED -> "OLED Black Mode"
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onTheme(mode) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = state.preferences.themeMode == mode, onClick = { onTheme(mode) })
                    Text(label)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Privacy and model")
        SettingsCard {
            Text(
                "Photos, recognized text, metadata, embeddings, and searches stay on this device. Frame Finder has no account, analytics, cloud API, or upload path.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Text recognition uses the bundled Google ML Kit Latin recognizer. Semantic search uses the selected bundled ${state.preferences.searchModel.displayName} encoders through ONNX Runtime. No model download or network connection is needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Frame Finder ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f)
            )
        }
        FrameFinderSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

internal fun strictnessLabel(value: Int): String = when (value) {
    in 0..24 -> "Broad"
    in 25..44 -> "Relaxed"
    in 45..64 -> "Balanced"
    in 65..84 -> "Strict"
    else -> "Very strict"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp), fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
