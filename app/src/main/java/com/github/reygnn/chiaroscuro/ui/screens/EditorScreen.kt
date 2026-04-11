package com.github.reygnn.chiaroscuro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.chiaroscuro.ui.components.BottomControls
import com.github.reygnn.chiaroscuro.ui.components.ImageCanvas
import com.github.reygnn.chiaroscuro.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onOpenPreferences: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    // SAF: Load image
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadImage(context, it) } }

    // SAF: Save – uses proposedFilename if set
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> uri?.let { viewModel.saveTransparent(context, it) } }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearExportMessage()
        }
    }

    // Auto-open save dialog when FAB sets a proposed filename
    LaunchedEffect(state.proposedFilename) {
        state.proposedFilename?.let { name ->
            saveLauncher.launch(name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chiaroscuro") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Preferences") },
                            onClick = { menuExpanded = false; onOpenPreferences() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.sourceBitmap != null) {
                FloatingActionButton(
                    onClick = { viewModel.applyQuickAction() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.FlashOn, contentDescription = "Quick Action")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ImageCanvas(
                    state = state,
                    onDragRect = { delta -> viewModel.moveRect(delta) },
                    modifier = Modifier.fillMaxSize()
                )
                if (state.isLoading || state.isAnalyzing) CircularProgressIndicator()
                if (state.sourceBitmap == null && !state.isLoading) {
                    Text(
                        text = "No image loaded\nTap 📂 Load to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            BottomControls(
                state = state,
                onRectWidthChange = viewModel::setRectWidth,
                onRectHeightChange = viewModel::setRectHeight,
                onToggleRect = viewModel::toggleRect,
                onLoadImage = { openLauncher.launch(arrayOf("image/*")) },
                onSaveTransparent = { saveLauncher.launch("edited_image.png") },
                onAmoledThreshold = viewModel::setAmoledThreshold,
                onToggleWarmMode = viewModel::toggleAmoledWarmMode,
                onAnalyzeAmoled = viewModel::analyzeAmoled,
                onApplyAmoled = viewModel::applyAmoledCorrection,
                onClearAmoled = viewModel::clearAmoledAnalysis
            )
        }
    }
}