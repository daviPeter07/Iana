package com.davipeter.iana.assistant

import com.davipeter.iana.data.calendar.CalendarEvent
import com.davipeter.iana.data.calendar.CalendarSource
import com.davipeter.iana.data.calendar.CreatedEvent
import com.davipeter.iana.data.calendar.NewEvent
import com.davipeter.iana.data.news.Headline
import com.davipeter.iana.data.news.NewsSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AssistantTest {
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0)

    private class FakeCalendar(val events: List<CalendarEvent> = emptyList()) : CalendarSource {
        val created = mutableListOf<NewEvent>()
        var denied = false
        override suspend fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> {
            if (denied) throw SecurityException()
            return events.filter { it.start < to && it.end > from }
        }
        override suspend fun create(event: NewEvent): CreatedEvent {
            created += event
            return CreatedEvent(1, "Pessoal")
        }
    }

    private class FakeNews(val result: List<Headline> = emptyList(), val fail: Boolean = false) : NewsSource {
        var lastTopic: String? = "nunca chamado"
        override suspend fun headlines(topic: String?, limit: Int): List<Headline> {
            lastTopic = topic
            if (fail) throw IOException()
            return result.take(limit)
        }
    }

    private fun assistant(news: NewsSource = FakeNews(), calendar: CalendarSource = FakeCalendar()) =
        Assistant(RuleBasedBrain(), news, calendar) { now }

    @Test fun `le as manchetes`() = runBlocking {
        val news = FakeNews(listOf(Headline("Chuva forte em SP", "G1", null, null), Headline("Dólar cai.", null, null, null)))
        val reply = assistant(news = news).handle("notícias")
        assertEquals("Principais notícias. Chuva forte em SP. Dólar cai.", reply.speech)
        assertEquals(listOf("Chuva forte em SP — G1", "Dólar cai."), reply.details)
        assertEquals(null, news.lastTopic)
    }

    @Test fun `sem internet avisa`() = runBlocking {
        val reply = assistant(news = FakeNews(fail = true)).handle("notícias")
        assertTrue(reply.speech.contains("internet"))
    }

    @Test fun `agenda de amanha`() = runBlocking {
        val tomorrow = LocalDate.of(2026, 11, 2)
        val calendar = FakeCalendar(
            listOf(
                CalendarEvent("Dentista", tomorrow.atTime(9, 0), tomorrow.atTime(10, 0), false, null),
                CalendarEvent("Reunião", tomorrow.atTime(14, 30), tomorrow.atTime(15, 0), false, null),
                CalendarEvent("Hoje", now, now.plusHours(1), false, null),
            ),
        )
        val reply = assistant(calendar = calendar).handle("minha agenda de amanhã")
        assertEquals("Você tem 2 compromissos amanhã: Às 9h, Dentista; Às 14h30, Reunião.", reply.speech)
    }

    @Test fun `agenda vazia`() = runBlocking {
        assertEquals("Você não tem compromissos hoje.", assistant().handle("minha agenda").speech)
    }

    @Test fun `marca evento`() = runBlocking {
        val calendar = FakeCalendar()
        val reply = assistant(calendar = calendar).handle("marca dentista sexta às 10h")
        assertEquals(listOf(NewEvent("Dentista", LocalDate.of(2026, 11, 6), LocalTime.of(10, 0), 60)), calendar.created)
        assertEquals("Pronto. Marquei Dentista na sexta-feira, 6 de novembro às 10h.", reply.speech)
    }

    @Test fun `sem permissao pede permissao`() = runBlocking {
        val calendar = FakeCalendar().apply { denied = true }
        assertEquals(Permission.CALENDAR, assistant(calendar = calendar).handle("minha agenda").needs)
    }

    @Test fun `proximo compromisso`() = runBlocking {
        val tomorrow = LocalDate.of(2026, 11, 2)
        val calendar = FakeCalendar(listOf(CalendarEvent("Dentista", tomorrow.atTime(9, 0), tomorrow.atTime(10, 0), false, null)))
        assertEquals(
            "Seu próximo compromisso é Dentista, amanhã às 9h.",
            assistant(calendar = calendar).handle("qual meu próximo compromisso").speech,
        )
    }
}
