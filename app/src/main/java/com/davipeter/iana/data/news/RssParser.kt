package com.davipeter.iana.data.news

import org.w3c.dom.Element
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** Lê um feed RSS 2.0 (como o do Google Notícias) e devolve as manchetes. */
object RssParser {

    fun parse(input: InputStream): List<Headline> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // Bloqueia DOCTYPE/entidades externas (XXE). Nem todo parser aceita a flag; aí seguimos sem ela.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(input)
        val items = doc.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { i -> toHeadline(items.item(i) as Element) }
    }

    private fun toHeadline(item: Element): Headline? {
        val rawTitle = item.childText("title") ?: return null
        val source = item.childText("source")
        // O Google Notícias manda "Manchete - Nome do Jornal"; tiramos o sufixo.
        val title = if (source != null && rawTitle.endsWith(" - $source")) {
            rawTitle.removeSuffix(" - $source")
        } else {
            rawTitle
        }
        val published = item.childText("pubDate")?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
        }
        return Headline(title.trim(), source?.trim(), item.childText("link"), published)
    }

    private fun Element.childText(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() }
    }
}
