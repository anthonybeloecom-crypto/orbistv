package app.orbistv.data

/** Parseur M3U / M3U8 étendu (#EXTINF avec tvg-logo, group-title…). */
object M3uParser {
    data class Entry(val name: String, val logo: String?, val group: String, val url: String)

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var extGrp: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    // Le nom est après la virgule qui suit le dernier attribut entre guillemets.
                    val lastQuote = line.lastIndexOf('"')
                    val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    name = title.ifEmpty { attrs["tvg-name"] ?: "Sans nom" }
                    logo = attrs["tvg-logo"]?.ifBlank { null }
                    group = attrs["group-title"]?.ifBlank { null }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGrp = line.substringAfter(':').trim().ifBlank { null }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    out += Entry(
                        name = name ?: line.substringAfterLast('/'),
                        logo = logo,
                        group = group ?: extGrp ?: "Sans catégorie",
                        url = line,
                    )
                    name = null; logo = null; group = null; extGrp = null
                }
            }
        }
        return out
    }
}
