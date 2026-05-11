package com.github.reygnn.chiaroscuro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.chiaroscuro.R
import com.github.reygnn.chiaroscuro.model.ExportMessage
import com.github.reygnn.chiaroscuro.ui.components.CommandsPanel
import com.github.reygnn.chiaroscuro.ui.components.EditorFab
import com.github.reygnn.chiaroscuro.ui.components.ImageCanvas
import com.github.reygnn.chiaroscuro.viewmodel.EditorViewModel

@Composable
fun EditorScreen(
    onOpenPreferences: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val state          by viewModel.state.collectAsStateWithLifecycle()
    val sourceBitmap   by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val analysisBitmap by viewModel.analysisBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var commandsOpen by remember { mutableStateOf(false) }

    val defaultExportFilename = stringResource(R.string.export_default_filename)

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.loadImage(context, it) } }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> uri?.let { viewModel.saveTransparent(context, it) } }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let { msg ->
            val text = when (msg) {
                is ExportMessage.Saved          -> context.getString(R.string.msg_saved)
                is ExportMessage.AmoledApplied  -> context.getString(R.string.msg_amoled_applied)
                is ExportMessage.Error          -> context.getString(R.string.msg_error, msg.throwableMessage.orEmpty())
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.clearExportMessage()
        }
    }

    LaunchedEffect(state.proposedFilename) {
        state.proposedFilename?.let { name -> saveLauncher.launch(name) }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ImageCanvas(
                state          = state,
                sourceBitmap   = sourceBitmap,
                analysisBitmap = analysisBitmap,
                onZoomChange   = { sc, off -> viewModel.updateZoom(sc, off) },
                onDoubleTap    = { viewModel.resetZoom() },
                onCanvasSize   = { viewModel.updateCanvasSize(it) },
                modifier       = Modifier.fillMaxSize(),
            )

            if (state.isLoading || state.isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (sourceBitmap == null && !state.isLoading) {
                Text(
                    text = stringResource(R.string.editor_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (commandsOpen) {
                CommandsPanel(
                    state              = state,
                    onLoadImage        = { openLauncher.launch(arrayOf("image/*")) },
                    onOpenPreferences  = { commandsOpen = false; onOpenPreferences() },
                    onRectWidthChange  = viewModel::setRectWidth,
                    onRectHeightChange = viewModel::setRectHeight,
                    onAmoledThreshold  = viewModel::setAmoledThreshold,
                    onToggleWarmMode   = viewModel::toggleAmoledWarmMode,
                    onClose            = { commandsOpen = false },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }

            EditorFab(
                hasImage          = sourceBitmap != null,
                rectVisible       = state.rectVisible,
                showAmoledOverlay = state.showAmoledOverlay,
                onQuickAction     = { viewModel.applyQuickAction() },
                onToggleRect      = viewModel::toggleRect,
                onAnalyzeOrApply  = {
                    if (state.showAmoledOverlay) viewModel.applyAmoledCorrection()
                    else viewModel.analyzeAmoled()
                },
                onSave            = { saveLauncher.launch(defaultExportFilename) },
                onOpenCommandsOrCancel = {
                    if (state.showAmoledOverlay) viewModel.clearAmoledAnalysis()
                    else commandsOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}
