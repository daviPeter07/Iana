package com.davipeter.iana.assistant

import com.davipeter.iana.ai.AiAgent
import com.davipeter.iana.ai.FakeCalendar
import com.davipeter.iana.ai.FakeNews
import com.davipeter.iana.ai.InMemoryCounterStore
import com.davipeter.iana.ai.LlmClient
import com.davipeter.iana.ai.LlmException
import com.davipeter.iana.ai.ScriptedLlm
import com.davipeter.iana.ai.ToolBox
import com.davipeter.iana.ai.UsageGuard
import com.davipeter.iana.ai.text
import com.davipeter.iana.ai.tool
import com.davipeter.iana.reports.InMemoryReportStore
import com.davipeter.iana.reports.ReportComposer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HybridAssistantTest {
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0)
    private val news = FakeNews()
    private val calendar = FakeCalendar()
    private var locked = false
    private val lock = LockPolicy(isLocked = { locked }, personalDataWhenLocked = { false })
    private val rules = Assistant(RuleBasedBrain(), news, calendar, clock = { now }, lock = lock)

    private fun hybrid(llm: LlmClient, enabled: Boolean = true, onReports: () -> Unit = {}) = HybridAssistant(
        rules = rules,
        ai = AiAgent(
            llm,
            ToolBox(news, calendar, InMemoryReportStore(), ReportComposer(news, calendar), lock, newId = { "r1" }),
            UsageGuard(InMemoryCounterStore()) { now.toLocalDate() },
            userName = "Davi",
            clock = { now },
        ),
        aiEnabled = { enabled },
        onReportsChanged = onReports,
    )

    private fun failing(kind: LlmException.Kind) = LlmClient { throw LlmException(kind, "falhou") }

    @Test
    fun `ia desligada usa as regras`() = runBlocking {
        val llm = ScriptedLlm()
        val reply = hybrid(llm, enabled = false).handle("que horas são?")
        assertEquals("Agora são 10h.", reply.speech)
        assertTrue(llm.requests.isEmpty())
    }

    @Test
    fun `chave recusada avisa e cai no modo basico`() = runBlocking {
        val reply = hybrid(failing(LlmException.Kind.AUTH)).handle("que horas são?")
        assertTrue(reply.speech.startsWith("A chave da IA foi recusada."))
        assertTrue(reply.speech.endsWith("Agora são 10h."))
    }

    @Test
    fun `sem internet cai no modo basico sem sermao`() = runBlocking {
        val reply = hybrid(failing(LlmException.Kind.NETWORK)).handle("que horas são?")
        assertEquals("Agora são 10h.", reply.speech)
        assertEquals("Modo básico", reply.details.last().text)
    }

    @Test
    fun `relatorio confirmado avisa para reagendar`() = runBlocking {
        var synced = 0
        val llm = ScriptedLlm(tool("salvar_relatorio", """{"nome":"Manhã","hora":"07:00","dias":["seg"],"agenda":true}"""))
        val assistant = hybrid(llm, onReports = { synced++ })
        assistant.handle("cria um relatório segunda às 7h")
        assertEquals(0, synced)
        assistant.handle("sim")
        assertEquals(1, synced)
    }

    @Test
    fun `modo basico tambem respeita o bloqueio`() = runBlocking {
        locked = true
        val assistant = hybrid(ScriptedLlm(), enabled = false)
        assertEquals(LockPolicy.LOCKED_READ, assistant.handle("minha agenda de hoje").speech)
        assertEquals(LockPolicy.LOCKED_CHANGE, assistant.handle("marca dentista amanhã às 10h").speech)
        assertTrue(calendar.created.isEmpty())
        // Hora e notícias continuam livres.
        assertEquals("Agora são 10h.", assistant.handle("que horas são?").speech)
    }

    @Test
    fun `resposta da ia passa direto`() = runBlocking {
        assertEquals("Oi, Davi!", hybrid(ScriptedLlm(text("Oi, Davi!"))).handle("oi").speech)
    }
}
