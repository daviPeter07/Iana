package com.davipeter.iana.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MotivationTest {
    @Test fun `toda frase cita o Davi`() {
        Motivation.PHRASES.forEach { assertTrue(it, it.contains("Davi")) }
    }

    @Test fun `mesma frase dentro do bloco de 3 horas e outra no bloco seguinte`() {
        val nine = LocalDateTime.of(2026, 11, 1, 9, 0)
        assertEquals(Motivation.phraseFor(nine), Motivation.phraseFor(nine.withHour(11).withMinute(59)))
        assertNotEquals(Motivation.phraseFor(nine), Motivation.phraseFor(nine.withHour(12)))
    }
}
