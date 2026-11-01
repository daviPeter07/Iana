package com.davipeter.iana.assistant

import com.davipeter.iana.data.calendar.CalendarEvent
import com.davipeter.iana.data.calendar.CalendarSource
import com.davipeter.iana.data.calendar.NewEvent
import com.davipeter.iana.data.calendar.NoWritableCalendarException
import com.davipeter.iana.data.news.NewsSource
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

enum class Permission { CALENDAR }

/** Uma linha extra mostrada na tela; [note] é a informação secundária (fonte, agenda...). */
data class Detail(val text: String, val note: String? = null)

/**
 * Resposta da assistente.
 * @param speech o que ela fala em voz alta.
 * @param details linhas extras mostradas na tela (manchetes com a fonte, eventos...).
 * @param needs permissão que faltou; a tela pede e repete o comando.
 * @param changedCalendar o comando criou um evento (o widget precisa ser atualizado).
 */
data class Reply(
    val speech: String,
    val details: List<Detail> = emptyList(),
    val needs: Permission? = null,
    val changedCalendar: Boolean = false,
)

/**
 * O roteador: recebe a frase, pede ao [Brain] para interpretar e executa o comando.
 * Não depende de Android, então dá pra testar na JVM.
 */
class Assistant(
    private val brain: Brain,
    private val news: NewsSource,
    private val calendar: CalendarSource,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) {

    suspend fun handle(text: String): Reply {
        val now = clock()
        val command = brain.interpret(text, now)
        return try {
            execute(command, now)
        } catch (e: SecurityException) {
            Reply("Preciso de permissão para acessar sua agenda.", needs = Permission.CALENDAR)
        } catch (e: NoWritableCalendarException) {
            Reply("Não encontrei nenhuma agenda no celular em que eu possa gravar. Adicione uma conta Google ao aparelho.")
        } catch (e: IOException) {
            Reply("Não consegui acessar a internet agora. Tente de novo em instantes.")
        }
    }

    private suspend fun execute(command: Command, now: LocalDateTime): Reply {
        val today = now.toLocalDate()
        return when (command) {
            is Command.News -> news(command.topic)
            is Command.Agenda -> agenda(command, today)
            Command.NextEvent -> nextEvent(now)
            is Command.CreateEvent -> create(command, today)
            is Command.CreateEventMissingDate -> Reply(
                "Para quando é ${command.title}? Diga, por exemplo: marca ${command.title.lowercase()} amanhã às 15h.",
            )
            Command.TellTime -> Reply("Agora são ${PtBr.time(now.toLocalTime())}.")
            Command.TellDate -> Reply("Hoje é ${PtBr.fullDate(today, LocalDate.MIN)}.")
            Command.Help -> Reply(
                "Eu posso ler as notícias, dizer sua agenda e marcar compromissos.",
                details = listOf(
                    Detail("Notícias", "ou “notícias sobre futebol”"),
                    Detail("Minha agenda de amanhã", "ou “agenda da semana”"),
                    Detail("Qual meu próximo compromisso?"),
                    Detail("Marca dentista sexta às 10h"),
                    Detail("Que horas são?"),
                ),
            )
            Command.Greeting -> Reply("${greeting(now)}! Em que posso ajudar?")
            is Command.Unknown ->
                if (command.text.isBlank()) {
                    Reply("Não ouvi nada. Pode repetir?")
                } else {
                    Reply(
                        "Ainda não sei fazer isso. Tente: notícias, minha agenda de amanhã, ou marca reunião amanhã às 15h.",
                    )
                }
        }
    }

    private suspend fun news(topic: String?): Reply {
        val headlines = news.headlines(topic, limit = 5)
        if (headlines.isEmpty()) {
            return Reply(if (topic == null) "Não encontrei notícias agora." else "Não encontrei notícias sobre $topic agora.")
        }
        val intro = if (topic == null) "Principais notícias." else "Notícias sobre $topic."
        val speech = intro + " " + headlines.joinToString(" ") { it.title.trimEnd('.') + "." }
        val details = headlines.map { h -> Detail(h.title, h.source) }
        return Reply(speech, details)
    }

    private suspend fun agenda(command: Command.Agenda, today: LocalDate): Reply {
        val from = command.start.atStartOfDay()
        val to = command.start.plusDays(command.days.toLong()).atStartOfDay()
        val events = calendar.events(from, to)
        val label = when {
            command.days == 1 -> PtBr.onDay(command.start, today)
            command.start == today -> "nos próximos 7 dias"
            else -> "na próxima semana"
        }
        if (events.isEmpty()) return Reply("Você não tem compromissos $label.")

        val multiDay = command.days > 1
        val spoken = events.take(MAX_SPOKEN_EVENTS).joinToString("; ") { describe(it, today, multiDay) }
        val more = events.size - MAX_SPOKEN_EVENTS
        val tail = if (more > 0) "; e mais ${PtBr.plural(more, "compromisso", "compromissos")}." else "."
        return Reply(
            "Você tem ${PtBr.plural(events.size, "compromisso", "compromissos")} $label: $spoken$tail",
            details = events.map { Detail(describe(it, today, multiDay), it.calendarName) },
        )
    }

    private suspend fun nextEvent(now: LocalDateTime): Reply {
        val today = now.toLocalDate()
        val upcoming = calendar.events(now, now.plusDays(30)).firstOrNull { e ->
            if (e.allDay) !e.start.toLocalDate().isBefore(today) else e.end.isAfter(now)
        } ?: return Reply("Você não tem nenhum compromisso nos próximos 30 dias.")

        val speech = when {
            !upcoming.allDay && upcoming.start <= now ->
                "Agora você está em ${upcoming.title}, até as ${PtBr.time(upcoming.end.toLocalTime())}."
            upcoming.allDay ->
                "Seu próximo compromisso é ${upcoming.title}, ${PtBr.onDay(upcoming.start.toLocalDate(), today)}, o dia todo."
            else ->
                "Seu próximo compromisso é ${upcoming.title}, ${PtBr.onDay(upcoming.start.toLocalDate(), today)} " +
                    "às ${PtBr.time(upcoming.start.toLocalTime())}."
        }
        return Reply(speech)
    }

    private suspend fun create(command: Command.CreateEvent, today: LocalDate): Reply {
        val created = calendar.create(NewEvent(command.title, command.date, command.time, command.durationMinutes))
        val whenText = PtBr.onDay(command.date, today) +
            (command.time?.let { " às ${PtBr.time(it)}" } ?: ", o dia todo")
        return Reply(
            "Pronto. Marquei ${command.title} $whenText.",
            details = listOf(Detail(command.title, "Agenda ${created.calendarName}")),
            changedCalendar = true,
        )
    }

    private fun describe(e: CalendarEvent, today: LocalDate, withDay: Boolean): String {
        val day = if (withDay) PtBr.onDay(e.start.toLocalDate(), today) + " " else ""
        val time = if (e.allDay) "o dia todo" else "às ${PtBr.time(e.start.toLocalTime())}"
        return "$day$time, ${e.title}".replaceFirstChar { it.uppercase() }
    }

    private fun greeting(now: LocalDateTime): String = when (now.hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }

    private companion object {
        const val MAX_SPOKEN_EVENTS = 6
    }
}
