package com.davipeter.iana

import android.app.Application
import com.davipeter.iana.assistant.Assistant
import com.davipeter.iana.assistant.RuleBasedBrain
import com.davipeter.iana.data.calendar.AndroidCalendarSource
import com.davipeter.iana.data.news.GoogleNewsSource
import com.davipeter.iana.voice.EmbeddedVoice

class IanaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Injeção de dependência feita à mão: um lugar só onde as peças são montadas. */
class AppContainer(app: Application) {
    val news = GoogleNewsSource()
    val calendar = AndroidCalendarSource(app.contentResolver)

    /** A voz da IANA (modelo carregado uma vez e compartilhado pelo app e pelo serviço). */
    val embeddedVoice = EmbeddedVoice(app)

    // Para trocar regras por IA no futuro, basta outra implementação de Brain aqui.
    val assistant = Assistant(RuleBasedBrain(), news, calendar)
}
