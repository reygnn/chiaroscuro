package com.github.reygnn.chiaroscuro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.reygnn.chiaroscuro.ui.screens.EditorScreen
import com.github.reygnn.chiaroscuro.ui.screens.PreferencesScreen
import com.github.reygnn.chiaroscuro.ui.theme.ImageEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageEditorTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "editor") {
                    composable("editor") {
                        EditorScreen(onOpenPreferences = { navController.navigate("preferences") })
                    }
                    composable("preferences") {
                        PreferencesScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}