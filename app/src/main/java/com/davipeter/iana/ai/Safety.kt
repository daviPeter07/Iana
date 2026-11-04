package com.davipeter.iana.ai

import java.time.LocalDate

/** Limpeza de texto que vem de fora (manchetes, títulos de evento) ou que vai ser falado. */
object Sanitize {
    private val CONTROL = Regex("""[\p{Cc}\p{Cf}&&[^\n]]""")
    private val SPACES = Regex("""\s+""")

    /** Uma linha: sem caracteres de controle/invisíveis, sem quebras, cortada em [max]. */
    fun line(text: String, max: Int): String {
        val clean = text.replace(CONTROL, "").replace(SPACES, " ").trim()
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }

    /**
     * Resposta para ser falada: tira marcação de markdown e emojis que a voz leria em voz alta,
     * e corta respostas longas demais (proteção contra respostas que não acabam nunca).
     */
    fun speech(text: String, max: Int = 900): String {
        val noMarkdown = text
            .replace(Regex("""```.*?```""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""[*_#`>|]"""), "")
            .replace(Regex("""^\s*[-•]\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}]"""), "")
        return line(noMarkdown, max)
    }
}

/** Resposta do usuário a "Confirma?". Decidida por código, nunca pelo modelo. */
enum class Answer { YES, NO, OTHER }

object Confirmation {
    // Só formas explícitas. "ok", "claro", "certo" e "pode" sozinhos aparecem demais em conversa
    // de fundo e na TV, e a rodada de confirmação da palavra "IANA" não sabe quem está falando.
    private val YES = setOf(
        "sim", "sim pode", "sim confirma", "sim confirmo", "sim por favor", "pode sim", "confirma", "confirmo",
        "confirmado", "isso mesmo", "pode marcar", "pode criar", "pode apagar", "pode salvar", "pode alterar",
    )
    private val NO = Regex("""\b(nao|cancela|cancelar|cancele|deixa|esquece|esqueca|pare|negativo|melhor nao)\b""")
    // "para" fica de fora de propósito: em português também é preposição ("para amanhã").
    private val WAKE = Regex("""^(?:(?:ok|ei|hey|oi)\s+)?(?:iana|yana|hiana|ianna)\s+""")

    fun parse(text: String): Answer {
        val folded = fold(text).replace(WAKE, "").trim()
        if (folded.isEmpty()) return Answer.OTHER
        if (NO.containsMatchIn(folded)) return Answer.NO
        // Só aceita um "sim" curto e limpo. "Sim, mas muda pra 16h" é um pedido novo, não uma confirmação.
        return if (folded in YES) Answer.YES else Answer.OTHER
    }

    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

/** Guarda quantos pedidos à IA foram feitos no dia. */
interface CounterStore {
    fun read(): Pair<LocalDate, Int>?
    fun write(day: LocalDate, count: Int)
}

class InMemoryCounterStore : CounterStore {
    private var value: Pair<LocalDate, Int>? = null
    override fun read() = value
    override fun write(day: LocalDate, count: Int) {
        value = day to count
    }
}

/**
 * Teto diário de chamadas à API. Protege a conta contra laço infinito, bug, ou a palavra
 * "IANA" disparando sem parar por causa de barulho (TV falando "Ana", por exemplo).
 * Cada ida e volta ao modelo conta, inclusive as do uso de ferramentas.
 */
class UsageGuard(
    private val store: CounterStore,
    private val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    private val today: () -> LocalDate = LocalDate::now,
) {
    @Synchronized
    fun tryConsume(): Boolean {
        val day = today()
        val used = store.read()?.takeIf { it.first == day }?.second ?: 0
        if (used >= dailyLimit) return false
        store.write(day, used + 1)
        return true
    }

    @Synchronized
    fun usedToday(): Int = store.read()?.takeIf { it.first == today() }?.second ?: 0

    companion object {
        const val DEFAULT_DAILY_LIMIT = 200
    }
}

/** Checagem de formato da chave antes de salvar (não prova que ela vale; isso só a API diz). */
object ApiKeyFormat {
    private val PATTERN = Regex("""^sk-ant-[A-Za-z0-9_\-]{20,300}$""")

    fun normalize(raw: String): String = raw.trim()

    fun isPlausible(key: String): Boolean = PATTERN.matches(key)

    /** Para mostrar na tela sem expor a chave: "sk-ant-…a1b2". */
    fun masked(key: String): String = "sk-ant-…" + key.takeLast(4)
}
