package com.davipeter.iana.assistant

import java.text.Normalizer

/**
 * Guarda a frase original e uma versão "dobrada" (minúsculas, sem acentos, pontuação virando
 * espaço) com o MESMO tamanho. Assim, quando um parser reconhece um trecho na versão dobrada,
 * dá pra remover exatamente o mesmo trecho da frase original — é como extraímos o título de um
 * evento mantendo acentos e maiúsculas.
 */
internal class TextWorkspace(val original: String) {
    private val folded = CharArray(original.length) { fold(original[it]) }
    private val consumed = BooleanArray(original.length)

    /** Texto dobrado, com os trechos já consumidos trocados por espaço. */
    fun view(): String = String(folded)

    fun consume(range: IntRange) {
        for (i in range) {
            folded[i] = ' '
            consumed[i] = true
        }
    }

    /** O que sobrou da frase original (com acentos), depois de remover os trechos consumidos. */
    fun remainingOriginal(): String =
        buildString(original.length) {
            original.forEachIndexed { i, c -> append(if (consumed[i]) ' ' else c) }
        }

    fun originalAt(range: IntRange): String = original.substring(range.first, range.last + 1)

    companion object {
        fun fold(c: Char): Char = when {
            c == 'º' || c == 'ª' || c == '°' -> ' '
            c.isLetterOrDigit() -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0].lowercaseChar()
            c == ':' || c == '/' -> c
            else -> ' '
        }

        fun fold(text: String): String = buildString(text.length) { text.forEach { append(fold(it)) } }
    }
}
