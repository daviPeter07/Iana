package com.davipeter.iana.data.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class CalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val calendarName: String?,
)

data class NewEvent(
    val title: String,
    val date: LocalDate,
    /** Nulo = dia inteiro. */
    val time: LocalTime?,
    val durationMinutes: Int,
)

data class CreatedEvent(val id: Long, val calendarName: String)

/** Não existe nenhuma agenda no celular em que dê pra gravar (ex.: nenhuma conta Google). */
class NoWritableCalendarException : Exception("Nenhuma agenda gravável encontrada")

/**
 * Acesso à agenda. Lança [SecurityException] se faltar permissão de agenda.
 */
interface CalendarSource {
    /** Eventos que acontecem (total ou parcialmente) entre [from] e [to], em ordem de início. */
    suspend fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarEvent>

    suspend fun create(event: NewEvent): CreatedEvent
}
