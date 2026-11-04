package com.davipeter.iana

import android.app.Application
import com.davipeter.iana.ai.AiAgent
import com.davipeter.iana.ai.AnthropicClient
import com.davipeter.iana.ai.ToolBox
import com.davipeter.iana.ai.UsageGuard
import com.davipeter.iana.assistant.Assistant
import com.davipeter.iana.assistant.HybridAssistant
import com.davipeter.iana.assistant.LockPolicy
import com.davipeter.iana.assistant.Motivation
import com.davipeter.iana.assistant.Responder
import com.davipeter.iana.assistant.RuleBasedBrain
import com.davipeter.iana.data.calendar.AndroidCalendarSource
import com.davipeter.iana.data.news.GoogleNewsSource
import com.davipeter.iana.reports.PrefsReportStore
import com.davipeter.iana.reports.ReportComposer
import com.davipeter.iana.reports.ReportScheduler
import com.davipeter.iana.security.ApiKeyStore
import com.davipeter.iana.security.DeviceLock
import com.davipeter.iana.security.IanaSettings
import com.davipeter.iana.voice.EmbeddedVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IanaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Garante que os relatórios estão agendados (ex.: depois de atualizar o app).
        container.scope.launch { ReportScheduler.syncAll(this@IanaApp) }
    }
}

/** Injeção de dependência feita à mão: um lugar só onde as peças são montadas. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val news = GoogleNewsSource()
    val calendar = AndroidCalendarSource(app.contentResolver)

    /** A voz da IANA (modelo carregado uma vez e compartilhado pelo app e pelo serviço). */
    val embeddedVoice = EmbeddedVoice(app)

    val apiKeys = ApiKeyStore(app)
    val settings = IanaSettings(app)
    private val deviceLock = DeviceLock(app)
    val lock = LockPolicy(isLocked = deviceLock::isLocked, personalDataWhenLocked = { settings.personalDataWhenLocked })

    val reports = PrefsReportStore(app)
    val reportComposer = ReportComposer(news, calendar)

    val usage = UsageGuard(settings.counter)

    /** A IA: chave lida do armazenamento cifrado a cada pedido, nunca guardada em memória fixa. */
    val aiAgent = AiAgent(
        llm = AnthropicClient(apiKey = apiKeys::get),
        tools = ToolBox(news, calendar, reports, reportComposer, lock),
        guard = usage,
        userName = Motivation.NAME,
    )

    /** Quem responde: IA quando ligada e com chave; regras quando não (ou quando a IA falha). */
    val assistant: Responder = HybridAssistant(
        rules = Assistant(RuleBasedBrain(), news, calendar, lock = lock),
        ai = aiAgent,
        aiEnabled = { settings.aiEnabled && apiKeys.has() },
        onReportsChanged = { scope.launch { ReportScheduler.syncAll(app) } },
    )
}
