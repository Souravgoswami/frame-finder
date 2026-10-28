package com.souravgoswami.framefinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souravgoswami.framefinder.ai.SearchModel
import com.souravgoswami.framefinder.data.LaunchView

@Composable
fun InitialModelSelectionScreen(
    initialModel: SearchModel,
    initialLaunchView: LaunchView,
    onContinue: (SearchModel, LaunchView) -> Unit
) {
    var selectedModel by remember(initialModel) { mutableStateOf(initialModel) }
    var selectedLaunchView by remember(initialLaunchView) { mutableStateOf(initialLaunchView) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            "Frame Finder",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "First setup",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(36.dp))
        Text(
            "Choose your AI search model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose before indexing so your first scan uses the model you want. You can change models later, but photos need separate AI analysis for each model.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))

        SearchModel.entries.forEach { model ->
            ModelChoiceCard(
                model = model,
                selected = selectedModel == model,
                onClick = { selectedModel = model }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Choose what to show on launch",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedLaunchView = launchView }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedLaunchView == launchView,
                    onClick = { selectedLaunchView = launchView }
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

        Spacer(Modifier.height(12.dp))
        Text(
            "Both models are included and work offline. Next, you can choose photo access and start indexing when you are ready.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onContinue(selectedModel, selectedLaunchView) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ModelChoiceCard(
    model: SearchModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${model.tier}, ${model.sizeMegabytes} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
