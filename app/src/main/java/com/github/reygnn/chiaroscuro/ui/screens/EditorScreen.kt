package com.github.reygnn.chiaroscuro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.chiaroscuro.ui.components.BottomControls
import com.github.reygnn.chiaroscuro.ui.components.ImageCanvas
import com.github.reygnn.chiaroscuro.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Editor") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        floatingActionButton = {
            if (state.sourceBitmap != null) {
                FloatingActionButton(
                    onClick = { viewModel.applyQuickAction() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Filled.FlashOn, contentDescription = "Schnell-Aktion") }
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
                        text = "Kein Bild geladen\nTippe auf 📂 Laden",
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
                onSaveTransparent = { saveLauncher.launch("bild_bearbeitet.png") },
                onAmoledThreshold = viewModel::setAmoledThreshold,
                onToggleWarmMode = viewModel::toggleAmoledWarmMode,
                onAnalyzeAmoled = viewModel::analyzeAmoled,
                onApplyAmoled = viewModel::applyAmoledCorrection,
                onClearAmoled = viewModel::clearAmoledAnalysis
            )
        }
    }
}