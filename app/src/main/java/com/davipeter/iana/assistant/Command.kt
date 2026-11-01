package com.davipeter.iana.assistant

import java.time.LocalDate
import java.time.LocalTime

/** O que o usuário pediu, já interpretado. Quem interpreta é um [Brain]. */
sealed interface Command {
    /** Manchetes; [topic] nulo = principais notícias do dia. */
    data class News(val topic: String?) : Command

    /** Compromissos a partir de [start], durante [days] dias. */
    data class Agenda(val start: LocalDate, val days: Int) : Command

    data object NextEvent : Command

    /** [time] nulo = evento de dia inteiro. */
    data class CreateEvent(
        val title: String,
        val date: LocalDate,
        val time: LocalTime?,
        val durationMinutes: Int,
    ) : Command

    /** Pediu pra marcar algo, mas não disse quando. */
    data class CreateEventMissingDate(val title: String) : Command

    data object TellTime : Command
    data object TellDate : Command
    data object Help : Command
    data object Greeting : Command
    data class Unknown(val text: String) : Command
}
