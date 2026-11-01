package com.davipeter.iana.assistant

import java.time.LocalDateTime

/**
 * Frases motivacionais para o Davi. A frase muda a cada 3 horas e é a mesma no app e no widget
 * naquele período. Pra trocar ou adicionar frases, edite a lista [PHRASES].
 */
object Motivation {
    const val NAME = "Davi"

    val PHRASES = listOf(
        "Um passo de cada vez, $NAME. Hoje conta.",
        "Disciplina vence motivação, $NAME. Começa pelo pequeno.",
        "$NAME, o que você construir hoje te sustenta amanhã.",
        "Foco no que depende de você, $NAME.",
        "Feito é melhor que perfeito, $NAME. Entrega e melhora depois.",
        "$NAME, constância é o seu superpoder.",
        "Respira, organiza e executa, $NAME.",
        "Grandes coisas começam com uma tarefa terminada, $NAME.",
        "$NAME, você já resolveu coisas mais difíceis que essa.",
        "Hoje é um bom dia pra avançar, $NAME.",
        "Menos promessa, mais progresso, $NAME.",
        "$NAME, quem faz um pouco todo dia chega longe.",
        "Cuida do processo, $NAME. O resultado vem.",
        "A SyncForge cresce com cada decisão sua, $NAME.",
        "$NAME, termina o que começou e comemora.",
        "Calma e consistência, $NAME. Você está no caminho.",
        "Errou? Ajusta e segue, $NAME.",
        "$NAME, seu futuro agradece o esforço de agora.",
        "Prioriza o importante, $NAME. O resto pode esperar.",
        "Descansar também é produtividade, $NAME.",
        "$NAME, coragem pra começar, paciência pra continuar.",
        "Uma hora de foco vale um dia de distração, $NAME.",
        "$NAME, ideias boas viram reais quando você executa.",
        "Confia no que você já construiu, $NAME.",
    )

    /** Frase do período atual (troca a cada 3 horas). */
    fun phraseFor(now: LocalDateTime): String {
        val slot = now.dayOfYear * 8 + now.hour / 3
        return PHRASES[slot % PHRASES.size]
    }
}
