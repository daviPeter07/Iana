package com.davipeter.iana.assistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DateTimeParserTest {
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0) // domingo

    private fun extract(text: String) = DateTimeParser.extract(TextWorkspace(text), now)

    @Test fun `horas em varios formatos`() {
        assertEquals(LocalTime.of(15, 0), extract("às 15h").time)
        assertEquals(LocalTime.of(15, 30), extract("às 15h30").time)
        assertEquals(LocalTime.of(15, 30), extract("15:30").time)
        assertEquals(LocalTime.of(15, 30), extract("às 15 horas e 30").time)
        assertEquals(LocalTime.of(20, 0), extract("às 8 da noite").time)
        assertEquals(LocalTime.of(9, 30), extract("às 9 e meia da manhã").time)
        assertEquals(LocalTime.of(15, 0), extract("às 3").time)
        assertEquals(LocalTime.of(0, 0), extract("à meia-noite").time)
    }

    @Test fun `numero solto nao vira hora`() {
        assertEquals(null, extract("reunião com 5 pessoas").time)
    }

    @Test fun `datas`() {
        assertEquals(LocalDate.of(2026, 11, 3), extract("depois de amanhã").date)
        assertEquals(LocalDate.of(2026, 11, 8), extract("no domingo que vem").date)
        assertEquals(LocalDate.of(2026, 11, 1), extract("domingo").date)
        assertEquals(LocalDate.of(2026, 11, 4), extract("quarta-feira").date)
        assertEquals(LocalDate.of(2027, 1, 5), extract("5/1/2027").date)
        assertEquals(LocalDate.of(2026, 12, 25), extract("25 de dezembro").date)
        assertEquals(LocalDate.of(2026, 12, 1), extract("1º de dezembro").date)
    }

    @Test fun `duracao`() {
        assertEquals(120, extract("por 2 horas").durationMinutes)
        assertEquals(30, extract("por meia hora").durationMinutes)
        assertEquals(45, extract("durante 45 minutos").durationMinutes)
    }
}
