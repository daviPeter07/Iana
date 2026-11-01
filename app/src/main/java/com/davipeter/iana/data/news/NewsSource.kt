package com.davipeter.iana.data.news

import java.time.Instant

data class Headline(
    val title: String,
    val source: String?,
    val link: String?,
    val published: Instant?,
)

interface NewsSource {
    /** [topic] nulo = principais notícias. Lança [java.io.IOException] se não houver conexão. */
    suspend fun headlines(topic: String?, limit: Int): List<Headline>
}
