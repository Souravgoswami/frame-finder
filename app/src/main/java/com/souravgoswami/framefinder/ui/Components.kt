package com.souravgoswami.framefinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.souravgoswami.framefinder.work.formatEstimatedRemaining
import com.souravgoswami.framefinder.work.formatScanProgress
import com.souravgoswami.framefinder.work.WorkContract
import java.text.NumberFormat

@Composable
fun FrameFinderSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            uncheckedThumbColor = colors.onSurfaceVariant,
            uncheckedTrackColor = colors.surfaceVariant,
            uncheckedBorderColor = colors.outline,
            disabledCheckedThumbColor = colors.onSurface.copy(alpha = 0.45f),
            disabledCheckedTrackColor = colors.primary.copy(alpha = 0.28f),
            disabledCheckedBorderColor = colors.primary.copy(alpha = 0.18f),
            disabledUncheckedThumbColor = colors.onSurface.copy(alpha = 0.28f),
            disabledUncheckedTrackColor = colors.surfaceVariant.copy(alpha = 0.35f),
            disabledUncheckedBorderColor = colors.outline.copy(alpha = 0.35f)
        )
    )
}

@Composable
fun UtilityButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    if (primary) {
        Button(onClick = onClick, modifier = modifier.height(44.dp), enabled = enabled) { Text(text) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            enabled = enabled,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text(text) }
    }
}

@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Frame Finder is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use your phone screen lock to view your photos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            UtilityButton("Unlock", onUnlock, primary = true)
        }
    }
}

@Composable
fun WorkProgressCard(work: BackgroundWorkState, onPause: () -> Unit, modifier: Modifier = Modifier) {
    val pausing = work.phase == WorkContract.PHASE_PAUSING
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(work.phase, fontWeight = FontWeight.SemiBold)
                    if (pausing) {
                        Text(
                            "Finishing active photo work before stopping",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (work.total > 0) {
                        Text(
                            formatScanProgress(work.done, work.total).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    work.estimatedRemainingMillis?.takeUnless { pausing }?.let { remaining ->
                        Text(
                            formatEstimatedRemaining(remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                UtilityButton(if (pausing) "Pausing" else "Pause", onPause, enabled = !pausing)
            }
            Spacer(Modifier.height(10.dp))
            if (work.total > 0) {
                LinearProgressIndicator(
                    progress = { work.done.toFloat() / work.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun PausedWorkCard(
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    newPhotosAvailable: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Scan paused", fontWeight = FontWeight.SemiBold)
                Text(
                    if (newPhotosAvailable) {
                        "Photos completed so far remain searchable. New photos will be included when you resume."
                    } else {
                        "Photos completed so far remain searchable."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UtilityButton("Resume", onResume, primary = true)
                TextButton(onClick = onCancel) { Text("Cancel scan") }
            }
        }
    }
}

@Composable
fun NewPhotosCard(onIndex: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("New photos found", fontWeight = FontWeight.SemiBold)
                Text(
                    "Analyze them when you are ready. Frame Finder will not start in the background by itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UtilityButton("Index new photos", onIndex, primary = true)
        }
    }
}

@Composable
fun IncompleteAnalysisCard(
    modelName: String,
    missingAiCount: Int,
    missingTextCount: Int,
    hasFailedPhotos: Boolean,
    ignoreFailedPhotos: Boolean,
    onIgnoreFailedPhotos: (Boolean) -> Unit,
    onAnalyzeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aiCount = missingAiCount.coerceAtLeast(0)
    val textCount = missingTextCount.coerceAtLeast(0)
    val title = when {
        aiCount > 0 && textCount > 0 -> "Photo analysis is incomplete"
        textCount > 0 -> "Text recognition is incomplete"
        else -> "AI analysis is incomplete"
    }
    val details = buildList {
        if (aiCount > 0) {
            add("${photoCount(aiCount)} need AI analysis with $modelName.")
        }
        if (textCount > 0) {
            add("${photoCount(textCount)} need text recognition.")
        }
    }.joinToString(" ")
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    ) {
        Column {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Completed work will be reused.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UtilityButton(
                    "Complete",
                    onAnalyzeAll,
                    primary = true
                )
            }
            if (hasFailedPhotos) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = ignoreFailedPhotos,
                        onCheckedChange = onIgnoreFailedPhotos
                    )
                    Text(
                        "Ignore failed photos in the future",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun photoCount(count: Int): String {
    val formatted = NumberFormat.getIntegerInstance().format(count)
    return "$formatted ${if (count == 1) "photo" else "photos"}"
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    alternativeAction: String? = null,
    onAlternativeAction: (() -> Unit)? = null,
    alternativeBody: String? = null,
    busy: Boolean = false,
    statusMessage: String? = null,
    errorMessage: String? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 34.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("▧⌕", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (busy) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            UtilityButton(action, onAction, primary = true, enabled = actionEnabled)
        }
        if (secondaryAction != null && onSecondaryAction != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondaryAction) { Text(secondaryAction) }
        }
        if (alternativeAction != null && onAlternativeAction != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Or",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            UtilityButton(
                alternativeAction,
                onAlternativeAction,
                enabled = actionEnabled
            )
            alternativeBody?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
