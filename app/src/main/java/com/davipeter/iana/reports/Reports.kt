package com.davipeter.iana.reports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Um relatório que a IANA monta sozinha num horário fixo (ex.: "resumo da manhã").
 * @param days dias da semana em que roda; nunca vazio.
 * @param newsTopics assuntos extras de notícia (até [MAX_TOPICS]).
 */
data class ReportConfig(
    val id: String,
    val name: String,
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val includeAgenda: Boolean,
    val includeTopNews: Boolean,
    val newsTopics: List<String>,
) {
    companion object {
        const val MAX_REPORTS = 10
        const val MAX_TOPICS = 3
        const val MAX_NAME = 40
        const val MAX_TOPIC = 40
    }
}

/** Onde os relatórios ficam guardados (no celular). */
interface ReportStore {
    suspend fun list(): List<ReportConfig>
    suspend fun save(report: ReportConfig)
    suspend fun delete(id: String)
}

class InMemoryReportStore(initial: List<ReportConfig> = emptyList()) : ReportStore {
    private val items = initial.associateBy { it.id }.toMutableMap()
    override suspend fun list() = items.values.sortedBy { it.time }
    override suspend fun save(report: ReportConfig) {
        items[report.id] = report
    }
    override suspend fun delete(id: String) {
        items.remove(id)
    }
}

/** Converte a lista de relatórios de/para JSON. Ignora entradas corrompidas em vez de quebrar o app. */
object ReportCodec {
    fun encode(reports: List<ReportConfig>): String = buildJsonArray {
        reports.forEach { r ->
            add(
                buildJsonObject {
                    put("id", r.id)
                    put("name", r.name)
                    put("time", r.time.toString())
                    put("days", buildJsonArray { r.days.sorted().forEach { add(JsonPrimitive(it.name)) } })
                    put("agenda", r.includeAgenda)
                    put("topNews", r.includeTopNews)
                    put("topics", buildJsonArray { r.newsTopics.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
    }.toString()

    fun decode(json: String?): List<ReportConfig> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element -> runCatching { decodeOne(element.jsonObject) }.getOrNull() }
    }

    private fun decodeOne(o: JsonObject): ReportConfig? {
        fun str(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
        fun bool(key: String) = (o[key] as? JsonPrimitive)?.booleanOrNull ?: false
        fun list(key: String) = (o[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val days = list("days").mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
        if (days.isEmpty()) return null
        return ReportConfig(
            id = str("id") ?: return null,
            name = str("name") ?: return null,
            time = LocalTime.parse(str("time") ?: return null),
            days = days,
            includeAgenda = bool("agenda"),
            includeTopNews = bool("topNews"),
            newsTopics = list("topics"),
        )
    }
}

object ReportSchedule {
    /** Próxima vez (estritamente depois de [after]) em que o relatório deve rodar. */
    fun nextRun(report: ReportConfig, after: LocalDateTime): LocalDateTime {
        require(report.days.isNotEmpty())
        for (offset in 0L..7L) {
            val day = after.toLocalDate().plusDays(offset)
            val candidate = day.atTime(report.time)
            if (day.dayOfWeek in report.days && candidate.isAfter(after)) return candidate
        }
        error("Sempre há um dia em 8 dias")
    }
}
