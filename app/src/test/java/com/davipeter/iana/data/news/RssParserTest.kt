package com.davipeter.iana.data.news

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RssParserTest {
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Principais notícias</title>
          <item>
            <title>Chuva forte atinge São Paulo - g1</title>
            <link>https://news.google.com/a</link>
            <pubDate>Sun, 01 Nov 2026 12:00:00 GMT</pubDate>
            <source url="https://g1.globo.com">g1</source>
          </item>
          <item><title>Sem fonte</title></item>
        </channel></rss>
    """.trimIndent()

    @Test fun `le itens e tira o nome do jornal do titulo`() {
        val items = RssParser.parse(feed.byteInputStream())
        assertEquals(2, items.size)
        assertEquals(
            Headline("Chuva forte atinge São Paulo", "g1", "https://news.google.com/a", Instant.parse("2026-11-01T12:00:00Z")),
            items[0],
        )
        assertEquals(Headline("Sem fonte", null, null, null), items[1])
    }

    @Test fun `monta a url com assunto`() {
        assertEquals(
            "https://news.google.com/rss/search?q=elei%C3%A7%C3%B5es+2026&hl=pt-BR&gl=BR&ceid=BR:pt-419",
            GoogleNewsSource.urlFor("eleições 2026"),
        )
    }
}
