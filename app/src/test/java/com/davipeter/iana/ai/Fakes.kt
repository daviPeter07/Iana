package com.davipeter.iana.ai

import com.davipeter.iana.data.calendar.CalendarEvent
import com.davipeter.iana.data.calendar.CalendarSource
import com.davipeter.iana.data.calendar.CreatedEvent
import com.davipeter.iana.data.calendar.NewEvent
import com.davipeter.iana.data.news.Headline
import com.davipeter.iana.data.news.NewsSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime

class FakeNews(var headlines: List<Headline> = listOf(Headline("Manchete um", "Fonte A", null, null))) : NewsSource {
    val topics = mutableListOf<String?>()
    override suspend fun headlines(topic: String?, limit: Int): List<Headline> {
        topics += topic
        return headlines.take(limit)
    }
}

class FakeCalendar(var events: List<CalendarEvent> = emptyList()) : CalendarSource {
    val created = mutableListOf<NewEvent>()
    var denied = false
    override suspend fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> {
        if (denied) throw SecurityException()
        return events.filter { it.start < to && it.end > from }
    }
    override suspend fun create(event: NewEvent): CreatedEvent {
        if (denied) throw SecurityException()
        created += event
        return CreatedEvent(created.size.toLong(), "Pessoal")
    }
}

/** Modelo de mentira: devolve as respostas na ordem e guarda os pedidos. */
class ScriptedLlm(vararg responses: LlmResponse) : LlmClient {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<LlmRequest>()
    var fallback: LlmResponse? = null

    override suspend fun complete(request: LlmRequest): LlmResponse {
        requests += request
        return queue.removeFirstOrNull() ?: fallback ?: error("Sem mais respostas roteirizadas")
    }
}

fun text(t: String) = LlmResponse(listOf(Block.Text(t)), "end_turn")

fun tool(name: String, input: String = "{}", id: String = "t1") =
    LlmResponse(listOf(Block.ToolUse(id, name, json(input))), "tool_use")

fun tools(vararg uses: Block.ToolUse) = LlmResponse(uses.toList(), "tool_use")

fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

/** Todo o texto que foi mandado ao modelo num pedido (para conferir o que vazou ou não). */
fun LlmRequest.allText(): String = messages.joinToString("\n") { m ->
    m.content.joinToString("\n") { b ->
        when (b) {
            is Block.Text -> b.text
            is Block.ToolUse -> b.input.toString()
            is Block.ToolResult -> b.content
        }
    }
}
