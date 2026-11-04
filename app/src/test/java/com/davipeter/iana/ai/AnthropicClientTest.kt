package com.davipeter.iana.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AnthropicClientTest {
    private val key = "sk-ant-api03-SEGREDO-que-nao-pode-vazar-1234567890"

    private class Recorder(var response: HttpResponse = HttpResponse(200, OK)) : HttpTransport {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null
        var throwIo = false
        override fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
            if (throwIo) throw IOException("timeout")
            this.url = url
            this.headers = headers
            this.body = body
            return response
        }
    }

    private val request = LlmRequest(
        system = "sistema",
        messages = listOf(
            Message.user("oi"),
            Message(Role.ASSISTANT, listOf(Block.ToolUse("t1", "buscar_noticias", json("{}")))),
            Message(Role.USER, listOf(Block.ToolResult("t1", "resultado", isError = true))),
        ),
        tools = listOf(
            ToolSpec("a", "A", json("""{"type":"object"}""")),
            ToolSpec("b", "B", json("""{"type":"object"}""")),
        ),
        maxTokens = 300,
    )

    @Test
    fun `monta o pedido com cabecalhos, cache e blocos de ferramenta`() = runBlocking {
        val http = Recorder()
        AnthropicClient({ key }, model = "claude-haiku-5-5", transport = http).complete(request)

        assertEquals("https://api.anthropic.com/v1/messages", http.url)
        assertEquals(key, http.headers["x-api-key"])
        assertEquals("2023-06-01", http.headers["anthropic-version"])

        val body = Json.parseToJsonElement(http.body!!).jsonObject
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("300", body["max_tokens"]!!.jsonPrimitive.content)
        assertNotNull(body["system"]!!.jsonArray[0].jsonObject["cache_control"])
        val tools = body["tools"]!!.jsonArray
        assertNull(tools[0].jsonObject["cache_control"])
        assertNotNull(tools[1].jsonObject["cache_control"])
        val result = body["messages"]!!.jsonArray[2].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("t1", result["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("true", result["is_error"]!!.jsonPrimitive.content)
        // A chave vai só no cabeçalho, nunca no corpo.
        assertFalse(http.body!!.contains(key))
    }

    @Test
    fun `le texto e pedidos de ferramenta`() = runBlocking {
        val http = Recorder(
            HttpResponse(
                200,
                """{"content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"Vou ver."},
                   {"type":"tool_use","id":"tu_1","name":"ler_agenda","input":{"dias":1}}],"stop_reason":"tool_use"}""",
            ),
        )
        val response = AnthropicClient({ key }, transport = http).complete(request)
        assertEquals("Vou ver.", response.text)
        assertEquals("ler_agenda", response.toolUses.single().name)
        assertEquals("1", response.toolUses.single().input["dias"]!!.jsonPrimitive.content)
        assertEquals("tool_use", response.stopReason)
    }

    @Test
    fun `erros viram tipos e nunca carregam a chave`() {
        val cases = mapOf(
            HttpResponse(401, """{"error":{"type":"authentication_error","message":"invalid x-api-key"}}""") to LlmException.Kind.AUTH,
            HttpResponse(429, """{"error":{"type":"rate_limit_error","message":"slow down"}}""") to LlmException.Kind.RATE_LIMIT,
            HttpResponse(529, "overloaded") to LlmException.Kind.RATE_LIMIT,
            HttpResponse(500, "boom") to LlmException.Kind.SERVER,
            HttpResponse(400, """{"error":{"type":"invalid_request_error","message":"Your credit balance is too low"}}""") to
                LlmException.Kind.NO_CREDIT,
            HttpResponse(400, """{"error":{"type":"invalid_request_error","message":"bad"}}""") to LlmException.Kind.BAD_REQUEST,
            HttpResponse(200, "não é json") to LlmException.Kind.BAD_RESPONSE,
        )
        cases.forEach { (http, kind) ->
            try {
                runBlocking { AnthropicClient({ key }, transport = Recorder(http)).complete(request) }
                fail("esperava erro para $http")
            } catch (e: LlmException) {
                assertEquals("caso $http", kind, e.kind)
                assertFalse(e.message!!.contains(key))
            }
        }
    }

    @Test
    fun `sem chave nem tenta chamar`() = runBlocking {
        val http = Recorder()
        try {
            AnthropicClient({ null }, transport = http).complete(request)
            fail()
        } catch (e: LlmException) {
            assertEquals(LlmException.Kind.AUTH, e.kind)
        }
        assertNull(http.url)
    }

    @Test
    fun `falha de rede vira NETWORK`() = runBlocking {
        try {
            AnthropicClient({ key }, transport = Recorder().apply { throwIo = true }).complete(request)
            fail()
        } catch (e: LlmException) {
            assertEquals(LlmException.Kind.NETWORK, e.kind)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recusa endereco sem https`() {
        AnthropicClient({ key }, baseUrl = "http://api.exemplo.com")
    }

    @Test
    fun `proxy proprio por https funciona`() = runBlocking {
        val http = Recorder()
        AnthropicClient({ key }, baseUrl = "https://meu-proxy.exemplo.com/", transport = http).complete(request)
        assertTrue(http.url == "https://meu-proxy.exemplo.com/v1/messages")
    }

    private companion object {
        const val OK = """{"content":[{"type":"text","text":"oi"}],"stop_reason":"end_turn"}"""
    }
}
