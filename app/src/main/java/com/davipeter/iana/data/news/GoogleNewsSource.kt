package com.davipeter.iana.data.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Notícias do Google Notícias via RSS: grátis e sem chave de API.
 * [open] é injetável para os testes não precisarem de internet.
 */
class GoogleNewsSource(
    private val open: (String) -> InputStream = ::httpGet,
) : NewsSource {

    override suspend fun headlines(topic: String?, limit: Int): List<Headline> = withContext(Dispatchers.IO) {
        open(urlFor(topic)).use { RssParser.parse(it) }.take(limit)
    }

    companion object {
        private const val LOCALE = "hl=pt-BR&gl=BR&ceid=BR:pt-419"

        fun urlFor(topic: String?): String =
            if (topic.isNullOrBlank()) {
                "https://news.google.com/rss?$LOCALE"
            } else {
                "https://news.google.com/rss/search?q=${URLEncoder.encode(topic, "UTF-8")}&$LOCALE"
            }

        private fun httpGet(url: String): InputStream {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Iana/0.1 (Android)")
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Google Notícias respondeu HTTP $code")
            }
            return conn.inputStream
        }
    }
}
