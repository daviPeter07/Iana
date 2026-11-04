package com.davipeter.iana.ai

import kotlinx.serialization.json.JsonObject
import java.io.IOException

/** Um pedaço de mensagem na conversa com o modelo. */
sealed interface Block {
    data class Text(val text: String) : Block

    /** O modelo pediu para usar uma ferramenta. */
    data class ToolUse(val id: String, val name: String, val input: JsonObject) : Block

    /** O app devolve o resultado da ferramenta [toolUseId]. */
    data class ToolResult(val toolUseId: String, val content: String, val isError: Boolean = false) : Block
}

enum class Role { USER, ASSISTANT }

data class Message(val role: Role, val content: List<Block>) {
    companion object {
        fun user(text: String) = Message(Role.USER, listOf(Block.Text(text)))
        fun assistant(text: String) = Message(Role.ASSISTANT, listOf(Block.Text(text)))
    }
}

/** Uma ferramenta que o modelo pode chamar. [inputSchema] é um JSON Schema. */
data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject)

data class LlmRequest(
    val system: String,
    val messages: List<Message>,
    val tools: List<ToolSpec>,
    val maxTokens: Int,
)

data class LlmResponse(val content: List<Block>, val stopReason: String?) {
    val toolUses: List<Block.ToolUse> get() = content.filterIsInstance<Block.ToolUse>()
    val text: String get() = content.filterIsInstance<Block.Text>().joinToString("\n") { it.text }.trim()
}

/** Quem conversa com o modelo. Fica atrás de uma interface para os testes não usarem a internet. */
fun interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmResponse
}

/**
 * Falha ao falar com o modelo. A mensagem nunca carrega a chave nem o texto da conversa,
 * só o tipo do problema, para poder aparecer em tela e em log sem vazar nada.
 */
class LlmException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind {
        /** Chave inválida, revogada ou sem permissão (401/403). */
        AUTH,

        /** Sem crédito na conta da API. */
        NO_CREDIT,

        /** Muitas requisições (429) ou serviço sobrecarregado (529). */
        RATE_LIMIT,

        /** Erro do lado da API (5xx). */
        SERVER,

        /** Sem internet, timeout, DNS. */
        NETWORK,

        /** Resposta que o app não entendeu. */
        BAD_RESPONSE,

        /** Pedido mal formado (400) — bug do app. */
        BAD_REQUEST,

        /** O teto diário de pedidos à IA (definido no app) foi atingido. */
        DAILY_LIMIT,
    }
}
