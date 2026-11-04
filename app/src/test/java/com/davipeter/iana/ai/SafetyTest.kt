package com.davipeter.iana.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SafetyTest {

    @Test
    fun `sim curto confirma`() {
        listOf("sim", "Sim!", "pode sim", "Confirma.", "IANA, sim", "confirmo", "pode marcar", "isso mesmo").forEach {
            assertEquals(it, Answer.YES, Confirmation.parse(it))
        }
    }

    @Test
    fun `nao e variacoes cancelam`() {
        listOf("não", "nao", "cancela", "deixa pra lá", "esquece", "melhor não").forEach {
            assertEquals(it, Answer.NO, Confirmation.parse(it))
        }
    }

    @Test
    fun `frase longa ou com condicao nao conta como sim`() {
        // Palavras soltas comuns em conversa de fundo/TV não confirmam nada.
        listOf("sim, mas muda pra 16h", "pode marcar para amanhã", "simulação", "", "talvez", "ok", "claro", "pode", "certo").forEach {
            assertEquals(it, Answer.OTHER, Confirmation.parse(it))
        }
    }

    @Test
    fun `sanitize tira controle, invisiveis e corta`() {
        assertEquals("abc def", Sanitize.line("a​bc\u0000 \n\tdef", 50))
        assertEquals("abcd…", Sanitize.line("abcdefghij", 5))
    }

    @Test
    fun `fala sem markdown, sem emoji e sem bloco de codigo`() {
        val raw = "# Título\n- **item** um\n```rm -rf```\nfim 🚀"
        assertEquals("Título item um fim", Sanitize.speech(raw))
    }

    @Test
    fun `teto diario conta por dia`() {
        var day = LocalDate.of(2026, 11, 1)
        val guard = UsageGuard(InMemoryCounterStore(), dailyLimit = 2) { day }
        assertTrue(guard.tryConsume())
        assertTrue(guard.tryConsume())
        assertFalse(guard.tryConsume())
        assertEquals(2, guard.usedToday())
        day = day.plusDays(1)
        assertEquals(0, guard.usedToday())
        assertTrue(guard.tryConsume())
    }

    @Test
    fun `formato da chave`() {
        assertTrue(ApiKeyFormat.isPlausible("sk-ant-api03-" + "a".repeat(40)))
        assertFalse(ApiKeyFormat.isPlausible("sk-proj-" + "a".repeat(40)))
        assertFalse(ApiKeyFormat.isPlausible("sk-ant-curta"))
        assertFalse(ApiKeyFormat.isPlausible("sk-ant-api03-" + "a".repeat(40) + " espaço"))
        assertEquals("sk-ant-api03-abc", ApiKeyFormat.normalize("  sk-ant-api03-abc\n"))
        assertEquals("sk-ant-…wxyz", ApiKeyFormat.masked("sk-ant-api03-qualquercoisawxyz"))
    }
}
