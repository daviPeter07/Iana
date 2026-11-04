package com.davipeter.iana.reports

import com.davipeter.iana.assistant.Detail
import com.davipeter.iana.assistant.PtBr
import com.davipeter.iana.data.calendar.CalendarSource
import com.davipeter.iana.data.news.NewsSource
import java.io.IOException
import java.time.LocalDateTime

/** Relatório pronto: [speech] para ler em voz alta, [lines] para a notificação e a tela. */
data class ComposedReport(val title: String, val speech: String, val lines: List<Detail>)

/**
 * Monta o relatório com código, sem IA. Assim ele roda de madrugada sem gastar crédito,
 * funciona mesmo se a chave estiver inválida, e manchete nenhuma consegue "dar ordens" a ninguém.
 */
class ReportComposer(
    private val news: NewsSource,
    private val calendar: CalendarSource,
) {

    suspend fun compose(report: ReportConfig, now: LocalDateTime): ComposedReport {
        val speech = mutableListOf<String>()
        val lines = mutableListOf<Detail>()

        if (report.includeAgenda) {
            // Relatório da noite fala do dia seguinte.
            val day = if (now.hour >= EVENING_HOUR) now.toLocalDate().plusDays(1) else now.toLocalDate()
            val label = PtBr.onDay(day, now.toLocalDate())
            try {
                val events = calendar.events(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                if (events.isEmpty()) {
                    speech += "Você não tem compromissos $label."
                } else {
                    val spoken = events.take(5).joinToString("; ") { e ->
                        val time = if (e.allDay) "o dia todo" else "às ${PtBr.time(e.start.toLocalTime())}"
                        "$time, ${e.title}"
                    }
                    speech += "Agenda de $label: $spoken."
                    events.forEach { e ->
                        val time = if (e.allDay) "Dia todo" else PtBr.time(e.start.toLocalTime())
                        lines += Detail("$time · ${e.title}", "Agenda")
                    }
                }
            } catch (e: SecurityException) {
                speech += "Não tenho permissão para ler sua agenda."
            }
        }

        val topics = buildList {
            if (report.includeTopNews) add(null)
            addAll(report.newsTopics)
        }
        for (topic in topics) {
            try {
                val headlines = news.headlines(topic, limit = HEADLINES_PER_TOPIC)
                if (headlines.isEmpty()) continue
                val intro = if (topic == null) "Principais notícias" else "Sobre $topic"
                speech += "$intro: " + headlines.joinToString(" ") { it.title.trimEnd('.') + "." }
                headlines.forEach { h -> lines += Detail(h.title, h.source ?: topic ?: "Notícias") }
            } catch (e: IOException) {
                speech += "Não consegui buscar as notícias${topic?.let { " sobre $it" }.orEmpty()}."
            }
        }

        if (speech.isEmpty()) speech += "Seu relatório está vazio."
        return ComposedReport(report.name, speech.joinToString(" "), lines)
    }

    private companion object {
        const val EVENING_HOUR = 18
        const val HEADLINES_PER_TOPIC = 3
    }
}
