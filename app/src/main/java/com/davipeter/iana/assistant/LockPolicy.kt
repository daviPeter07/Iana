package com.davipeter.iana.assistant

/**
 * O que a IANA pode fazer com o celular bloqueado.
 *
 * A palavra "IANA" funciona com a tela travada, então qualquer pessoa perto do celular pode
 * falar com ela (e o Vosk também acorda com "Ana"). Por isso:
 * - alterar alguma coisa (criar evento, mexer em relatório) exige o celular desbloqueado, sempre;
 * - ouvir agenda e relatórios com a tela travada é uma escolha sua (desligado por padrão).
 * Notícias, hora e data continuam livres.
 */
class LockPolicy(
    private val isLocked: () -> Boolean,
    private val personalDataWhenLocked: () -> Boolean,
) {
    fun canReadPersonal(): Boolean = !isLocked() || personalDataWhenLocked()

    fun canChange(): Boolean = !isLocked()

    companion object {
        /** Sem restrição (testes e o comportamento antigo). */
        val OPEN = LockPolicy(isLocked = { false }, personalDataWhenLocked = { true })

        const val LOCKED_READ = "Desbloqueie o celular para eu falar da sua agenda."
        const val LOCKED_CHANGE = "Desbloqueie o celular para eu fazer alterações."
    }
}
