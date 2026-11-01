package com.davipeter.iana.assistant

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal data class DateTimeMatch(
    val date: LocalDate?,
    val time: LocalTime?,
    val durationMinutes: Int?,
)

/**
 * Entende datas e horas faladas em português: "amanhã", "depois de amanhã", "na sexta",
 * "dia 15", "15/11", "3 de dezembro", "às 15h", "15:30", "às 3 da tarde", "meio-dia",
 * "por 2 horas". Cada trecho reconhecido é consumido do [TextWorkspace], para que o que sobrar
 * vire o título do evento.
 */
internal object DateTimeParser {

    private val WEEKDAYS = mapOf(
        "segunda" to DayOfWeek.MONDAY,
        "terca" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY,
        "sexta" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
    )

    private val MONTHS = listOf(
        "janeiro", "fevereiro", "marco", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    private const val DATE_PREP = """(?:(?:para|pra|em|n[oa]|d[oa]|de)\s+)?"""

    private val RELATIVE = Regex("""\b(?:(?:para|pra|de)\s+)?(depois\s+de\s+amanha|amanha|hoje)\b""")
    private val NAMED_DATE = Regex(
        """\b$DATE_PREP(?:dia\s+)?(\d{1,2})\s+de\s+(${MONTHS.joinToString("|")})(?:\s+de\s+(\d{4}))?\b""",
    )
    private val NUMERIC_DATE = Regex("""\b$DATE_PREP(?:dia\s+)?(\d{1,2})/(\d{1,2})(?:/(\d{4}|\d{2}))?\b""")
    private val DAY_ONLY = Regex("""\b$DATE_PREP\bdia\s+(\d{1,2})\b""")
    private val WEEKDAY = Regex(
        """\b(?:(?:para|pra|n[ao]|nest[ae]|ness[ae]|est[ae]|ess[ae]|proxim[ao])\s+)*""" +
            """(segunda|terca|quarta|quinta|sexta|sabado|domingo)(?:\s+feira)?(\s+que\s+vem)?\b""",
    )

    private val DURATION = Regex(
        """\b(?:por|durante|com\s+duracao\s+de)\s+(?:(\d{1,3})\s*(h|horas?|min|minutos?)\b|(uma\s+hora\s+e\s+meia|uma\s+hora|meia\s+hora))""",
    )

    private val NOON_MIDNIGHT = Regex("""\b(?:(?:ao|as|a|pra|para\s+o|para)\s+)?(meio\s+dia|meia\s+noite)(\s+e\s+meia)?\b""")

    private val TIME = Regex(
        """\b(?:(as|a|pras|pra|para\s+as|para)\s+)?(\d{1,2})(?:\s*(:|h\b|h(?=\d)|horas?\b)(?:\s*(\d{2}))?)?""" +
            """(?:\s+e\s+(meia|\d{1,2})(?:\s+minutos?)?)?(?:\s+(?:da|de)\s+(manha|tarde|noite|madrugada))?\b""",
    )

    fun extract(ws: TextWorkspace, now: LocalDateTime): DateTimeMatch {
        val today = now.toLocalDate()
        val duration = extractDuration(ws)
        val date = extractDate(ws, today)
        val time = extractTime(ws)
        return DateTimeMatch(date, time, duration)
    }

    /** Só a data (usado para perguntas como "minha agenda de amanhã"). */
    fun extractDate(ws: TextWorkspace, today: LocalDate): LocalDate? {
        RELATIVE.find(ws.view())?.let { m ->
            ws.consume(m.range)
            return when (m.groupValues[1].replace(Regex("\\s+"), " ")) {
                "hoje" -> today
                "amanha" -> today.plusDays(1)
                else -> today.plusDays(2)
            }
        }
        NAMED_DATE.find(ws.view())?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS.indexOf(m.groupValues[2]) + 1
            val year = m.groupValues[3].toIntOrNull()
            resolveDayMonth(day, month, year, today)?.let { ws.consume(m.range); return it }
        }
        NUMERIC_DATE.find(ws.view())?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            resolveDayMonth(day, month, year, today)?.let { ws.consume(m.range); return it }
        }
        DAY_ONLY.find(ws.view())?.let { m ->
            resolveDayOnly(m.groupValues[1].toInt(), today)?.let { ws.consume(m.range); return it }
        }
        WEEKDAY.find(ws.view())?.let { m ->
            val target = WEEKDAYS.getValue(m.groupValues[1])
            val forceNextWeek = m.groupValues[2].isNotBlank() || m.value.contains("proxim")
            var delta = (target.value - today.dayOfWeek.value + 7) % 7
            if (delta == 0 && forceNextWeek) delta = 7
            ws.consume(m.range)
            return today.plusDays(delta.toLong())
        }
        return null
    }

    private fun extractDuration(ws: TextWorkspace): Int? {
        val m = DURATION.find(ws.view()) ?: return null
        val minutes = when {
            m.groupValues[3].isNotEmpty() -> when {
                m.groupValues[3].startsWith("meia") -> 30
                m.groupValues[3].contains("meia") -> 90
                else -> 60
            }
            m.groupValues[2].startsWith("h") -> m.groupValues[1].toInt() * 60
            else -> m.groupValues[1].toInt()
        }
        if (minutes <= 0) return null
        ws.consume(m.range)
        return minutes
    }

    private fun extractTime(ws: TextWorkspace): LocalTime? {
        NOON_MIDNIGHT.find(ws.view())?.let { m ->
            val hour = if (m.groupValues[1].startsWith("meio")) 12 else 0
            val minute = if (m.groupValues[2].isNotBlank()) 30 else 0
            ws.consume(m.range)
            return LocalTime.of(hour, minute)
        }
        for (m in TIME.findAll(ws.view())) {
            val prep = m.groupValues[1]
            val unit = m.groupValues[3]
            val period = m.groupValues[6]
            // Sem "às", sem "h"/":" e sem "da tarde" um número solto não é hora ("2 pessoas").
            if (prep.isEmpty() && unit.isEmpty() && period.isEmpty()) continue

            var hour = m.groupValues[2].toInt()
            val extra = m.groupValues[5]
            val minute = when {
                m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt()
                extra == "meia" -> 30
                extra.isNotEmpty() -> extra.toInt()
                else -> 0
            }
            when (period) {
                "tarde", "noite" -> if (hour in 1..11) hour += 12 else if (hour == 12 && period == "noite") hour = 0
                "manha" -> if (hour == 12) hour = 0
                "" -> if (hour in 1..6) hour += 12 // "às 3" quase sempre é 15h
            }
            if (hour !in 0..23 || minute !in 0..59) continue
            ws.consume(m.range)
            return LocalTime.of(hour, minute)
        }
        return null
    }

    private fun resolveDayMonth(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        val candidate = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
    }

    private fun resolveDayOnly(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var month = today.withDayOfMonth(1)
        if (day < today.dayOfMonth) month = month.plusMonths(1)
        repeat(3) {
            if (day <= month.lengthOfMonth()) return month.withDayOfMonth(day)
            month = month.plusMonths(1)
        }
        return null
    }
}
