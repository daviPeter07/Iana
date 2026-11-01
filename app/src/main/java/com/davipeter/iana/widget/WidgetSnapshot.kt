package com.davipeter.iana.widget

import com.davipeter.iana.assistant.PtBr
import com.davipeter.iana.data.calendar.CalendarSource
import com.davipeter.iana.data.news.NewsSource
import java.io.IOException
import java.time.LocalDateTime

/** O que o widget mostra. Fica salvo no aparelho, porque o widget não pode buscar dados sozinho. */
data class WidgetSnapshot(
    val headlines: List<String> = emptyList(),
    /** Ex.: "Amanhã 9h · Dentista". Nulo = nada nos próximos 7 dias. */
    val nextEvent: String? = null,
    val calendarAllowed: Boolean = true,
    val updatedAt: LocalDateTime? = null,
)

object WidgetSnapshots {
    const val HEADLINES = 3

    /**
     * Monta um snapshot novo. Se a internet falhar, mantém as manchetes de [previous]
     * (é melhor mostrar notícias de meia hora atrás do que um widget vazio).
     */
    suspend fun build(
        news: NewsSource,
        calendar: CalendarSource,
        now: LocalDateTime,
        previous: WidgetSnapshot,
    ): WidgetSnapshot {
        val headlines = try {
            news.headlines(topic = null, limit = HEADLINES).map { it.title }
        } catch (e: IOException) {
            previous.headlines
        }

        var calendarAllowed = true
        val nextEvent = try {
            val today = now.toLocalDate()
            calendar.events(now, now.plusDays(7))
                .firstOrNull { e -> if (e.allDay) !e.start.toLocalDate().isBefore(today) else e.end.isAfter(now) }
                ?.let { e ->
                    val day = PtBr.shortDay(e.start.toLocalDate(), today)
                    val time = when {
                        e.allDay -> ""
                        e.start <= now -> " agora"
                        else -> " " + PtBr.time(e.start.toLocalTime())
                    }
                    "$day$time · ${e.title}"
                }
        } catch (e: SecurityException) {
            calendarAllowed = false
            null
        }

        return WidgetSnapshot(headlines, nextEvent, calendarAllowed, now)
    }
}
