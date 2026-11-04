package com.davipeter.iana.assistant

import com.davipeter.iana.ai.AiAgent
import com.davipeter.iana.ai.LlmException
import kotlinx.coroutines.CancellationException

/** Quem responde aos pedidos (o app e o serviço da palavra "IANA" só conhecem isto). */
fun interface Responder {
    suspend fun handle(text: String): Reply
}

/**
 * Usa a IA quando ela está ligada e disponível; senão, as regras de sempre.
 *
 * A IANA nunca fica muda: sem internet, sem crédito, com a chave errada ou depois do teto diário,
 * ela continua entendendo os comandos básicos. Quando o problema exige uma ação sua (chave
 * recusada, sem crédito, teto atingido), ela avisa em voz alta antes de responder.
 */
class HybridAssistant(
    private val rules: Assistant,
    private val ai: AiAgent,
    private val aiEnabled: () -> Boolean,
    private val onReportsChanged: () -> Unit = {},
) : Responder {

    override suspend fun handle(text: String): Reply {
        val reply = if (!aiEnabled()) rules.handle(text) else try {
            ai.handle(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmException) {
            fallback(text, e.kind)
        } catch (e: Exception) {
            // Qualquer outro erro (bug, provedor de agenda instável...) não pode derrubar o app
            // nem o serviço da palavra "IANA".
            fallback(text, LlmException.Kind.BAD_RESPONSE)
        }
        if (reply.changedReports) onReportsChanged()
        return reply
    }

    private suspend fun fallback(text: String, kind: LlmException.Kind): Reply {
        val basic = rules.handle(text, allowChanges = false)
        val warning = when (kind) {
            LlmException.Kind.AUTH -> "A chave da IA foi recusada. Confira nas configurações."
            LlmException.Kind.NO_CREDIT -> "A conta da IA está sem crédito."
            LlmException.Kind.DAILY_LIMIT -> "Cheguei no limite diário de pedidos à IA."
            else -> null
        }
        val note = Detail("Modo básico", warning ?: "IA indisponível agora")
        return basic.copy(
            speech = if (warning != null) "$warning Por enquanto, no modo básico: ${basic.speech}" else basic.speech,
            details = basic.details + note,
        )
    }
}
