package com.davipeter.iana.assistant

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Interpreta uma frase. Hoje são regras; depois dá pra ter uma implementação com IA. */
fun interface Brain {
    suspend fun interpret(text: String, now: LocalDateTime): Command
}

/** [Brain] por regras e palavras-chave, sem internet e sem IA. */
class RuleBasedBrain : Brain {
    override suspend fun interpret(text: String, now: LocalDateTime): Command = CommandParser.parse(text, now)
}

object CommandParser {

    /** "IANA" no começo da frase. O reconhecimento às vezes escreve "Yana", "Hiana" ou "Ianna". */
    private val WAKE_WORD = Regex("""^\s*(?:(?:ok|ei|hey|ola|oi)\s+)?(?:iana|yana|hiana|ianna|iannah)\b""")

    private val CREATE = Regex(
        """^\s*(?:por\s+favor\s+)?(?:me\s+)?""" +
            """(marca|marque|marcar|agenda|agende|agendar|cria|crie|criar|adiciona|adicione|adicionar|""" +
            """coloca|coloque|colocar|bota|bote|lembra|lembre|lembrar)\b""" +
            """(?:\s+(?:me|pra\s+mim|para\s+mim))?(?:\s+(?:na|em)\s+(?:minha\s+)?agenda)?""" +
            """(?:\s+(?:um|uma)\s+(?:evento|compromisso|lembrete)(?:\s+(?:chamad[oa]|de|para|pra))?)?(?:\s+(?:de|que))?\b""",
    )

    /** Depois de "agenda", estas palavras indicam pergunta ("agenda de amanhã"), não criação. */
    private val AGENDA_QUERY_FOLLOWERS = Regex(
        """^(?:de|do|da|pra|para|hoje|amanha|depois|desta|dessa|nesta|nessa|esta|essa|semana|proxim\w*|n[ao])(?:\s|$)|^$""",
    )

    private val TELL_TIME = Regex("""\bque\s+horas?\b|\bhoras?\s+sao\b""")
    private val TELL_DATE = Regex("""\bque\s+dia\s+(?:e|eh)\s+hoje\b|\bdata\s+de\s+hoje\b|\bque\s+dia\s+e\s+hoje\b""")
    private val NEWS = Regex("""\b(?:noticias?|manchetes?|novidades)\b""")
    private val NEWS_TOPIC = Regex("""\b(?:noticias?|manchetes?|novidades)\s+(?:de|do|da|dos|das|sobre|em|no|na)\s+(.+)$""")
    private val NEWS_TOPIC_NOISE = setOf(
        "hoje", "agora", "dia", "do dia", "de hoje", "ultimas", "recentes", "principais", "mais recentes", "por favor",
        "pra mim", "para mim", "o dia",
    )
    private val NEXT_EVENT = Regex("""\bproxim[oa]\s+(?:compromisso|evento|reuniao|agendamento)\b""")
    private val AGENDA = Regex(
        """\b(?:agenda|compromissos?|eventos?|reunioes|reuniao|o\s+que\s+(?:eu\s+)?tenho|tenho\s+(?:algo|alguma\s+coisa|o\s+que))\b""",
    )
    private val WEEK = Regex("""\bsemana\b""")
    private val NEXT_WEEK = Regex("""\bproxima\s+semana\b|\bsemana\s+que\s+vem\b""")
    private val HELP = Regex("""\b(?:ajuda|comandos|o\s+que\s+voce\s+(?:faz|sabe|pode))\b""")
    private val GREETING = Regex("""^\s*(?:oi|ola|e\s+ai|bom\s+dia|boa\s+tarde|boa\s+noite|tudo\s+bem)\b""")

    private val TITLE_EDGE_WORDS = setOf(
        "de", "do", "da", "para", "pra", "as", "a", "o", "os", "no", "na", "em", "um", "uma", "e", "que",
        "evento", "compromisso", "lembrete", "chamado", "chamada", "por", "favor",
    )

    fun parse(text: String, now: LocalDateTime): Command {
        val ws = TextWorkspace(text)
        WAKE_WORD.find(ws.view())?.let { ws.consume(it.range) }
        val v = ws.view()
        if (v.isBlank()) return Command.Unknown("")

        CREATE.find(v)?.let { m ->
            val verb = m.groups[1]!!
            val afterVerb = v.substring(verb.range.last + 1).trimStart()
            val isAgendaQuestion = verb.value == "agenda" && AGENDA_QUERY_FOLLOWERS.containsMatchIn(afterVerb)
            if (!isAgendaQuestion) return parseCreate(ws, m.range, now)
        }

        if (TELL_TIME.containsMatchIn(v)) return Command.TellTime
        if (TELL_DATE.containsMatchIn(v)) return Command.TellDate

        if (NEWS.containsMatchIn(v)) return Command.News(newsTopic(ws))

        if (NEXT_EVENT.containsMatchIn(v)) return Command.NextEvent
        if (AGENDA.containsMatchIn(v)) {
            val today = now.toLocalDate()
            if (NEXT_WEEK.containsMatchIn(v)) {
                return Command.Agenda(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), 7)
            }
            if (WEEK.containsMatchIn(v)) return Command.Agenda(today, 7)
            return Command.Agenda(DateTimeParser.extractDate(ws, today) ?: today, 1)
        }

        if (HELP.containsMatchIn(v)) return Command.Help
        if (GREETING.containsMatchIn(v) && v.trim().split(Regex("\\s+")).size <= 4) return Command.Greeting

        return Command.Unknown(text.trim())
    }

    private fun parseCreate(ws: TextWorkspace, trigger: IntRange, now: LocalDateTime): Command {
        ws.consume(trigger)
        val dt = DateTimeParser.extract(ws, now)
        val title = cleanTitle(ws.remainingOriginal())
        if (dt.date == null && dt.time == null) return Command.CreateEventMissingDate(title)

        val date = dt.date ?: if (dt.time!! > now.toLocalTime()) now.toLocalDate() else now.toLocalDate().plusDays(1)
        return Command.CreateEvent(title, date, dt.time, dt.durationMinutes ?: 60)
    }

    private fun newsTopic(ws: TextWorkspace): String? {
        val m = NEWS_TOPIC.find(ws.view()) ?: return null
        val group = m.groups[1]!!
        val folded = TextWorkspace.fold(ws.originalAt(group.range)).trim().replace(Regex("\\s+"), " ")
        if (folded.isEmpty() || folded in NEWS_TOPIC_NOISE) return null
        val topic = ws.originalAt(group.range).trim().trimEnd('.', '!', '?', ',')
        // Tira um "de hoje" / "hoje" no fim: "futebol de hoje" -> "futebol".
        return topic.replace(Regex("""(?i)\s+(de\s+)?(hoje|agora)$"""), "").ifBlank { null }
    }

    /** Remove conectores soltos nas pontas: "de reunião com João às" -> "Reunião com João". */
    internal fun cleanTitle(raw: String): String {
        val words = raw.split(Regex("\\s+")).map { it.trim(',', '.', ';', ':', '!', '?', '-') }.filter { it.isNotEmpty() }
            .toMutableList()
        fun isEdge(w: String) = TextWorkspace.fold(w).trim() in TITLE_EDGE_WORDS
        while (words.isNotEmpty() && isEdge(words.first())) words.removeAt(0)
        while (words.isNotEmpty() && isEdge(words.last())) words.removeAt(words.lastIndex)
        val title = words.joinToString(" ")
        return if (title.isEmpty()) "Compromisso" else title.replaceFirstChar { it.uppercase() }
    }
}
