package com.davipeter.iana.ui

import android.content.Intent
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
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            IanaTheme {
                HomeScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_LISTEN) {
            viewModel.requestAutoListen()
            intent.action = null // não repetir ao girar a tela
        }
    }

    override fun onStop() {
        super.onStop()
        // Saiu do app: para a conversa da tela. (A escuta da palavra "IANA" é um serviço à parte e continua.)
        viewModel.stop()
    }

    companion object {
        /** Usado pelo botão de microfone do widget. */
        const val ACTION_LISTEN = "com.davipeter.iana.LISTEN"
    }
}
