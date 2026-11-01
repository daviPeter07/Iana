package com.davipeter.iana.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.davipeter.iana.ui.theme.IanaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IanaTheme {
                HomeScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Saiu do app: para de ouvir e de falar (ouvir em segundo plano é o ponto 4 do roadmap).
        viewModel.stop()
    }
}
