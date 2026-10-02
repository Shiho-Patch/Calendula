package de.jeanlucmakiola.calendula.ui.detail

/** An http(s) URL found in free text, spanning [start] until [end] (exclusive). */
internal data class WebLink(val start: Int, val end: Int, val url: String)

private val webLinkRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

/**
 * Find the http(s) URLs in [text]. Trailing prose punctuation is left out of
 * each match, and the scheme is lower-cased so intent filters match it.
 */
internal fun findWebLinks(text: String): List<WebLink> = webLinkRegex.findAll(text).mapNotNull { match ->
    val raw = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'')
    val schemeEnd = raw.indexOf(':')
    if (raw.length <= schemeEnd + 3) return@mapNotNull null
    val url = raw.substring(0, schemeEnd).lowercase() + raw.substring(schemeEnd)
    WebLink(match.range.first, match.range.first + raw.length, url)
}.toList()
