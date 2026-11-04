package com.davipeter.iana.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Resposta HTTP crua. */
data class HttpResponse(val code: Int, val body: String)

/** Faz o POST. Injetável para os testes. */
fun interface HttpTransport {
    fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
}

/**
 * Cliente da Messages API da Anthropic, sem SDK (o SDK oficial é para a JVM/servidor e traria
 * dependências pesadas para o app).
 *
 * Segurança:
 * - a chave vem de [apiKey] a cada chamada, direto do armazenamento cifrado, e nunca é guardada
 *   em campo, nunca vai para log e nunca aparece em mensagens de erro;
 * - só aceita endereço https (dá pra trocar [baseUrl] por um proxy seu no futuro);
 * - system prompt e ferramentas vão com cache de prompt, o que deixa cada pedido mais barato.
 */
class AnthropicClient(
    private val apiKey: () -> String?,
    private val model: String = DEFAULT_MODEL,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val transport: HttpTransport = UrlConnectionTransport(),
) : LlmClient {

    init {
        require(baseUrl.startsWith("https://")) { "A API só pode ser chamada por https" }
    }

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw LlmException(LlmException.Kind.AUTH, "Nenhuma chave configurada")
        val headers = mapOf(
            "x-api-key" to key,
            "anthropic-version" to API_VERSION,
            "content-type" to "application/json",
        )
        val response = try {
            transport.post("${baseUrl.trimEnd('/')}/v1/messages", headers, encode(request))
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "Falha de rede ao falar com a API")
        }
        if (response.code !in 200..299) throw errorFor(response)
        decode(response.body)
    }

    fun encode(request: LlmRequest): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put(
                "system",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", request.system)
                            putJsonObject("cache_control") { put("type", "ephemeral") }
                        },
                    )
                },
            )
            put(
                "tools",
                buildJsonArray {
                    request.tools.forEachIndexed { i, tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("input_schema", tool.inputSchema)
                                // O cache vale para tudo até o ponto marcado: marcar a última ferramenta cobre todas.
                                if (i == request.tools.lastIndex) putJsonObject("cache_control") { put("type", "ephemeral") }
                            },
                        )
                    }
                },
            )
            put("messages", buildJsonArray { request.messages.forEach { add(encodeMessage(it)) } })
        }
        return body.toString()
    }

    private fun encodeMessage(message: Message): JsonObject = buildJsonObject {
        put("role", if (message.role == Role.USER) "user" else "assistant")
        put(
            "content",
            buildJsonArray {
                message.content.forEach { block ->
                    add(
                        when (block) {
                            is Block.Text -> buildJsonObject {
                                put("type", "text")
                                put("text", block.text)
                            }
                            is Block.ToolUse -> buildJsonObject {
                                put("type", "tool_use")
                                put("id", block.id)
                                put("name", block.name)
                                put("input", block.input)
                            }
                            is Block.ToolResult -> buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", block.toolUseId)
                                put("content", block.content)
                                if (block.isError) put("is_error", true)
                            }
                        },
                    )
                }
            },
        )
    }

    fun decode(body: String): LlmResponse {
        try {
            val root = Json.parseToJsonElement(body).jsonObject
            val blocks = root["content"]!!.jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "text" -> Block.Text(obj["text"]!!.jsonPrimitive.content)
                    "tool_use" -> Block.ToolUse(
                        id = obj["id"]!!.jsonPrimitive.content,
                        name = obj["name"]!!.jsonPrimitive.content,
                        input = obj["input"] as? JsonObject ?: JsonObject(emptyMap()),
                    )
                    // Outros tipos (ex.: "thinking") não interessam ao app.
                    else -> null
                }
            }
            return LlmResponse(blocks, root["stop_reason"]?.jsonPrimitive?.contentOrNull)
        } catch (e: Exception) {
            if (e is LlmException) throw e
            throw LlmException(LlmException.Kind.BAD_RESPONSE, "Resposta inesperada da API")
        }
    }

    private fun errorFor(response: HttpResponse): LlmException {
        // A mensagem de erro da API não contém segredos, mas o app só usa o tipo dela.
        val errorType = runCatching {
            Json.parseToJsonElement(response.body).jsonObject["error"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val errorMessage = runCatching {
            Json.parseToJsonElement(response.body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        val kind = when {
            response.code == 401 || response.code == 403 -> LlmException.Kind.AUTH
            response.code == 429 || response.code == 529 -> LlmException.Kind.RATE_LIMIT
            response.code >= 500 -> LlmException.Kind.SERVER
            errorMessage.contains("credit", ignoreCase = true) -> LlmException.Kind.NO_CREDIT
            else -> LlmException.Kind.BAD_REQUEST
        }
        return LlmException(kind, "API respondeu HTTP ${response.code} (${errorType ?: "sem tipo"})")
    }

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        private const val API_VERSION = "2023-06-01"
    }
}

/** POST com HttpURLConnection, com timeouts. Não registra corpo nem cabeçalhos em log. */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.instanceFollowRedirects = false // não seguir redirecionamento com a chave no cabeçalho
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return HttpResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }
}
