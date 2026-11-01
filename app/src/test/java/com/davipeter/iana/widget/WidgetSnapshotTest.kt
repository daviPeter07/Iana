package com.davipeter.iana.widget

import com.davipeter.iana.data.calendar.CalendarEvent
import com.davipeter.iana.data.calendar.CalendarSource
import com.davipeter.iana.data.calendar.CreatedEvent
import com.davipeter.iana.data.calendar.NewEvent
import com.davipeter.iana.data.news.Headline
import com.davipeter.iana.data.news.NewsSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetSnapshotTest {
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0) // domingo

    private fun news(vararg titles: String, fail: Boolean = false) = object : NewsSource {
        override suspend fun headlines(topic: String?, limit: Int): List<Headline> {
            if (fail) throw IOException()
            return titles.map { Headline(it, null, null, null) }.take(limit)
        }
    }

    private fun calendar(vararg events: CalendarEvent, denied: Boolean = false) = object : CalendarSource {
        override suspend fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> {
            if (denied) throw SecurityException()
            return events.filter { it.start < to && it.end > from }
        }
        override suspend fun create(event: NewEvent) = CreatedEvent(1, "x")
    }

    private fun build(news: NewsSource, calendar: CalendarSource, previous: WidgetSnapshot = WidgetSnapshot()) =
        runBlocking { WidgetSnapshots.build(news, calendar, now, previous) }

    @Test fun `monta manchetes e proximo compromisso`() {
        val tue = LocalDate.of(2026, 11, 3)
        val snap = build(
            news("A", "B", "C", "D"),
            calendar(CalendarEvent("Dentista", tue.atTime(9, 30), tue.atTime(10, 30), false, null)),
        )
        assertEquals(listOf("A", "B", "C"), snap.headlines)
        assertEquals("Ter 3/11 9h30 · Dentista", snap.nextEvent)
        assertEquals(now, snap.updatedAt)
    }

    @Test fun `evento acontecendo agora`() {
        val snap = build(news(), calendar(CalendarEvent("Reunião", now.minusMinutes(30), now.plusMinutes(30), false, null)))
        assertEquals("Hoje agora · Reunião", snap.nextEvent)
    }

    @Test fun `sem internet mantem manchetes anteriores`() {
        val snap = build(news(fail = true), calendar(), previous = WidgetSnapshot(headlines = listOf("velha")))
        assertEquals(listOf("velha"), snap.headlines)
    }

    @Test fun `sem permissao de agenda`() {
        assertEquals(false, build(news(), calendar(denied = true)).calendarAllowed)
    }
}
