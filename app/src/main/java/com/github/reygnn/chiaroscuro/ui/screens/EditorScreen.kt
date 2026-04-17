package com.github.reygnn.chiaroscuro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.chiaroscuro.ui.components.AppIcons
import com.github.reygnn.chiaroscuro.ui.components.BottomControls
import com.github.reygnn.chiaroscuro.ui.components.ImageCanvas
import com.github.reygnn.chiaroscuro.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onOpenPreferences: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val state          by viewModel.state.collectAsStateWithLifecycle()
    val sourceBitmap   by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val analysisBitmap by viewModel.analysisBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadImage(context, it) } }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> uri?.let { viewModel.saveTransparent(context, it) } }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearExportMessage()
        }
    }

    LaunchedEffect(state.proposedFilename) {
        state.proposedFilename?.let { name -> saveLauncher.launch(name) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chiaroscuro") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(AppIcons.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Preferences") },
                            onClick = { menuExpanded = false; onOpenPreferences() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (sourceBitmap != null) {
                FloatingActionButton(
                    onClick = { viewModel.applyQuickAction() },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(AppIcons.FlashOn, contentDescription = "Quick Action")
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.weight(1f)) {
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
                        text = "No image loaded\nTap 📂 Load to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            BottomControls(
                state              = state,
                onRectWidthChange  = viewModel::setRectWidth,
                onRectHeightChange = viewModel::setRectHeight,
                onToggleRect       = viewModel::toggleRect,
                onLoadImage        = { openLauncher.launch(arrayOf("image/*")) },
                onSaveTransparent  = { saveLauncher.launch("edited_image.png") },
                onAmoledThreshold  = viewModel::setAmoledThreshold,
                onToggleWarmMode   = viewModel::toggleAmoledWarmMode,
                onAnalyzeAmoled    = viewModel::analyzeAmoled,
                onApplyAmoled      = viewModel::applyAmoledCorrection,
                onClearAmoled      = viewModel::clearAmoledAnalysis,
            )
        }
    }
}