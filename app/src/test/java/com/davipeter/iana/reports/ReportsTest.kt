package com.davipeter.iana.reports

import com.davipeter.iana.ai.FakeCalendar
import com.davipeter.iana.ai.FakeNews
import com.davipeter.iana.data.calendar.CalendarEvent
import com.davipeter.iana.data.news.Headline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ReportsTest {
    private val weekdays = DayOfWeek.entries.take(5).toSet()
    private val morning = ReportConfig("m1", "Resumo da manhã", LocalTime.of(7, 0), weekdays, true, true, listOf("tecnologia"))

    @Test
    fun `codec ida e volta`() {
        val back = ReportCodec.decode(ReportCodec.encode(listOf(morning)))
        assertEquals(listOf(morning), back)
    }

    @Test
    fun `codec ignora lixo sem quebrar`() {
        assertEquals(emptyList<ReportConfig>(), ReportCodec.decode("{não é json"))
        assertEquals(emptyList<ReportConfig>(), ReportCodec.decode(null))
        val mixed = """[{"id":"x"},{"id":"ok","name":"N","time":"07:00","days":["MONDAY"],"agenda":true}]"""
        assertEquals(listOf("ok"), ReportCodec.decode(mixed).map { it.id })
    }

    @Test
    fun `proxima execucao`() {
        // Domingo 10h: próximo dia útil é segunda 7h.
        assertEquals(LocalDateTime.of(2026, 11, 2, 7, 0), ReportSchedule.nextRun(morning, LocalDateTime.of(2026, 11, 1, 10, 0)))
        // Segunda 6h59: ainda hoje.
        assertEquals(LocalDateTime.of(2026, 11, 2, 7, 0), ReportSchedule.nextRun(morning, LocalDateTime.of(2026, 11, 2, 6, 59)))
        // Segunda 7h em ponto: já passou, vai para terça.
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 0), ReportSchedule.nextRun(morning, LocalDateTime.of(2026, 11, 2, 7, 0)))
        // Sexta 8h: pula o fim de semana.
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 0), ReportSchedule.nextRun(morning, LocalDateTime.of(2026, 11, 6, 8, 0)))
        // Só domingo, sendo domingo depois do horário: semana que vem.
        val sunday = morning.copy(days = setOf(DayOfWeek.SUNDAY))
        assertEquals(LocalDateTime.of(2026, 11, 8, 7, 0), ReportSchedule.nextRun(sunday, LocalDateTime.of(2026, 11, 1, 10, 0)))
    }

    @Test
    fun `relatorio junta agenda e noticias`() = runBlocking {
        val now = LocalDateTime.of(2026, 11, 2, 7, 0)
        val calendar = FakeCalendar(listOf(CalendarEvent("Daily", now.withHour(9), now.withHour(10), false, "Trabalho")))
        val news = FakeNews(listOf(Headline("Manchete", "G1", null, null)))
        val composed = ReportComposer(news, calendar).compose(morning, now)
        assertEquals("Resumo da manhã", composed.title)
        assertTrue(composed.speech, composed.speech.contains("às 9h, Daily"))
        assertTrue(composed.speech.contains("Principais notícias: Manchete."))
        assertTrue(composed.speech.contains("Sobre tecnologia: Manchete."))
        assertEquals(listOf(null, "tecnologia"), news.topics)
    }

    @Test
    fun `relatorio da noite fala de amanha e sobrevive sem permissao`() = runBlocking {
        val now = LocalDateTime.of(2026, 11, 2, 20, 0)
        val tomorrowEvent = CalendarEvent("Dentista", now.plusDays(1).withHour(10), now.plusDays(1).withHour(11), false, null)
        val evening = morning.copy(includeTopNews = false, newsTopics = emptyList())
        val composed = ReportComposer(FakeNews(), FakeCalendar(listOf(tomorrowEvent))).compose(evening, now)
        assertTrue(composed.speech, composed.speech.contains("amanhã") && composed.speech.contains("Dentista"))

        val denied = FakeCalendar().apply { this.denied = true }
        val noPermission = ReportComposer(FakeNews(), denied).compose(evening, now)
        assertEquals("Não tenho permissão para ler sua agenda.", noPermission.speech)
    }
}
