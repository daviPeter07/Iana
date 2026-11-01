package com.davipeter.iana.assistant

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Formatação de datas e horas do jeito que a gente fala em português. */
object PtBr {
    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to "segunda-feira",
        DayOfWeek.TUESDAY to "terça-feira",
        DayOfWeek.WEDNESDAY to "quarta-feira",
        DayOfWeek.THURSDAY to "quinta-feira",
        DayOfWeek.FRIDAY to "sexta-feira",
        DayOfWeek.SATURDAY to "sábado",
        DayOfWeek.SUNDAY to "domingo",
    )

    private val MONTHS = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    fun weekday(date: LocalDate): String = WEEKDAYS.getValue(date.dayOfWeek)

    fun month(date: LocalDate): String = MONTHS[date.monthValue - 1]

    /** "15h" ou "15h30". */
    fun time(t: LocalTime): String = if (t.minute == 0) "${t.hour}h" else "${t.hour}h%02d".format(t.minute)

    /** "sexta-feira, 7 de novembro" (com o ano só se for outro ano). */
    fun fullDate(date: LocalDate, today: LocalDate): String {
        val year = if (date.year != today.year) " de ${date.year}" else ""
        return "${weekday(date)}, ${date.dayOfMonth} de ${month(date)}$year"
    }

    /** "hoje", "amanhã", "na sexta-feira, 7 de novembro", "no sábado, 8 de novembro". */
    fun onDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "hoje"
        today.plusDays(1) -> "amanhã"
        else -> {
            val prep = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) "no" else "na"
            "$prep ${fullDate(date, today)}"
        }
    }

    /** Versão curta para o widget: "Hoje", "Amanhã", "Sex 7/11". */
    fun shortDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        else -> weekday(date).take(3).replaceFirstChar { it.uppercase() } + " ${date.dayOfMonth}/${date.monthValue}"
    }

    /** "1 compromisso", "3 compromissos". */
    fun plural(n: Int, singular: String, plural: String): String = "$n ${if (n == 1) singular else plural}"
}
