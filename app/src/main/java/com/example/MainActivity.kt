package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameViewModel.startFeed(GameFeedRepository(java.io.File(filesDir, "games-cache.json")))
        
        // Supports borderless edge-to-edge drawing
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    // MainNbaScreen handles insets securely
                    MainNbaScreen(viewModel = gameViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening a document picker or returning to the activity must not silently replace
        // the chart's captured game. Only its explicit Refresh action updates that session.
        if (gameViewModel.visualizations.session.value == null) gameViewModel.refreshGames()
    }
}
