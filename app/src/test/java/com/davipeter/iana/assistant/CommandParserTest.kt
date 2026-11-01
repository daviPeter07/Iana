package com.davipeter.iana.assistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CommandParserTest {
    // Domingo, 1 de novembro de 2026, 10h.
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0)
    private fun parse(text: String) = CommandParser.parse(text, now)
    private fun date(m: Int, d: Int, y: Int = 2026) = LocalDate.of(y, m, d)

    @Test fun `noticias sem assunto`() {
        assertEquals(Command.News(null), parse("IANA, quais as notícias de hoje?"))
        assertEquals(Command.News(null), parse("me dá as últimas notícias"))
        assertEquals(Command.News(null), parse("manchetes"))
    }

    @Test fun `noticias com assunto mantem acentos`() {
        assertEquals(Command.News("futebol"), parse("notícias sobre futebol"))
        assertEquals(Command.News("Flamengo"), parse("notícias do Flamengo"))
        assertEquals(Command.News("eleições"), parse("notícias sobre eleições de hoje"))
    }

    @Test fun `agenda por dia`() {
        assertEquals(Command.Agenda(date(11, 1), 1), parse("qual minha agenda"))
        assertEquals(Command.Agenda(date(11, 2), 1), parse("agenda de amanhã"))
        assertEquals(Command.Agenda(date(11, 2), 1), parse("o que eu tenho amanhã?"))
        assertEquals(Command.Agenda(date(11, 6), 1), parse("tenho algo na sexta?"))
        assertEquals(Command.Agenda(date(11, 15), 1), parse("compromissos do dia 15"))
    }

    @Test fun `agenda da semana`() {
        assertEquals(Command.Agenda(date(11, 1), 7), parse("minha agenda da semana"))
        assertEquals(Command.Agenda(date(11, 2), 7), parse("compromissos da semana que vem"))
    }

    @Test fun `proximo compromisso`() {
        assertEquals(Command.NextEvent, parse("qual meu próximo compromisso?"))
    }

    @Test fun `criar evento com data e hora`() {
        assertEquals(
            Command.CreateEvent("Reunião com João", date(11, 2), LocalTime.of(15, 0), 60),
            parse("marca reunião com João amanhã às 15h"),
        )
        assertEquals(
            Command.CreateEvent("Dentista", date(11, 6), LocalTime.of(10, 30), 60),
            parse("Iana, agenda dentista na sexta às 10:30"),
        )
        assertEquals(
            Command.CreateEvent("Ligar pro contador", date(11, 15), LocalTime.of(15, 0), 60),
            parse("me lembra de ligar pro contador dia 15 às 3 da tarde"),
        )
        assertEquals(
            Command.CreateEvent("Almoço com a Ana", date(12, 3), LocalTime.of(12, 0), 90),
            parse("marca almoço com a Ana 3 de dezembro ao meio-dia por uma hora e meia"),
        )
    }

    @Test fun `criar evento so com hora usa hoje ou amanha`() {
        assertEquals(Command.CreateEvent("Academia", date(11, 1), LocalTime.of(18, 0), 60), parse("marca academia às 18h"))
        assertEquals(Command.CreateEvent("Café", date(11, 2), LocalTime.of(8, 0), 60), parse("marca café às 8h"))
    }

    @Test fun `criar evento de dia inteiro`() {
        assertEquals(Command.CreateEvent("Aniversário da Maria", date(11, 20), null, 60), parse("marca aniversário da Maria 20/11"))
    }

    @Test fun `data passada vai pro ano seguinte`() {
        assertEquals(Command.CreateEvent("Viagem", date(10, 15, 2027), null, 60), parse("marca viagem 15/10"))
    }

    @Test fun `criar evento sem data pergunta quando`() {
        assertEquals(Command.CreateEventMissingDate("Reunião de equipe"), parse("marca uma reunião de equipe"))
    }

    @Test fun `outros comandos`() {
        assertEquals(Command.TellTime, parse("que horas são?"))
        assertEquals(Command.TellDate, parse("que dia é hoje?"))
        assertEquals(Command.Help, parse("o que você sabe fazer?"))
        assertEquals(Command.Greeting, parse("bom dia IANA"))
        assertEquals(Command.Unknown("toca uma música"), parse("toca uma música"))
    }
}
