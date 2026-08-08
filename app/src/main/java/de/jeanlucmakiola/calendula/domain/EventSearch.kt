package de.jeanlucmakiola.calendula.domain

import kotlin.time.Instant

/** An event with the free text the search may have matched it on. */
data class SearchCandidate(
    val event: EventInstance,
    val description: String? = null,
)

/** A matched run inside one of a hit's texts, for highlighting. */
data class MatchSpan(val start: Int, val end: Int)

/** The part of a description a token matched, elided to what fits a row. */
data class DescriptionSnippet(
    val text: String,
    val spans: List<MatchSpan>,
)

/**
 * One ranked search result: the event, plus where the query matched so the row
 * can show why it is in the list.
 */
data class SearchHit(
    val event: EventInstance,
    val titleSpans: List<MatchSpan> = emptyList(),
    val locationSpans: List<MatchSpan> = emptyList(),
    val descriptionSnippet: DescriptionSnippet? = null,
)

/**
 * Turning a typed query into ranked hits.
 *
 * Matching is token-wise: the query splits on whitespace and an event has to
 * satisfy *every* token, each in any of title / location / description. Case is
 * folded through Kotlin's [String.indexOf] rather than SQL's `LIKE`, which folds
 * ASCII only — "ärzte" has to find "Ärzte".
 *
 * Ranking answers the complaint behind #80's follow-up: a description hit used
 * to outrank a title hit purely by being sooner. A token scores by the field it
 * matched ([FIELD_TITLE] > [FIELD_LOCATION] > [FIELD_DESCRIPTION]) and by how it
 * sat in that field (whole-field start > word start > mid-word), and an event
 * ranks by the *weakest* field the query needed — one token findable only in the
 * description drags the whole event below the title matches, however soon it is.
 * Equal scores are separated by how much of the title the match covers, so an
 * exact title beats one that merely begins the same way, and only then by date.
 */
object EventSearchRanker {

    private const val FIELD_TITLE = 3
    private const val FIELD_LOCATION = 2
    private const val FIELD_DESCRIPTION = 1

    private const val KIND_MID_WORD = 1
    private const val KIND_WORD_START = 2
    private const val KIND_FIELD_START = 3

    /** Characters of description kept around the match in a row's snippet. */
    private const val SNIPPET_LENGTH = 96

    /** Characters of lead-in shown before the match, when there is room. */
    private const val SNIPPET_LEAD = 24

    private val WHITESPACE = Regex("\\s+")

    /**
     * The query as match tokens: whitespace-separated, blanks dropped. Shared
     * with the data layer so its SQL pre-filter tokenises identically.
     */
    fun tokenize(query: String): List<String> =
        query.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    /**
     * Rank [candidates] against [query], dropping any that don't carry every
     * token. Ties fall back to the previous ordering — soonest upcoming (and
     * ongoing) first, then the most recent past — measured against [now].
     */
    fun rank(
        candidates: List<SearchCandidate>,
        query: String,
        now: Instant,
    ): List<SearchHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val scored = candidates.mapNotNull { candidate -> score(candidate, tokens) }
        return scored
            .sortedWith(
                compareByDescending<Scored> { it.weakest }
                    .thenByDescending { it.total }
                    .thenByDescending { it.titleCoverage }
                    .thenBy { if (it.hit.event.end >= now) 0 else 1 }
                    .thenComparator { a, b ->
                        val aStart = a.hit.event.start
                        val bStart = b.hit.event.start
                        // Same side of now by the key above: upcoming reads
                        // forwards, the past backwards from today.
                        if (a.hit.event.end >= now) {
                            aStart.compareTo(bStart)
                        } else {
                            bStart.compareTo(aStart)
                        }
                    },
            )
            .map { it.hit }
    }

    private fun score(candidate: SearchCandidate, tokens: List<String>): Scored? {
        val title = candidate.event.title
        val location = candidate.event.location?.takeIf { it.isNotBlank() }
        // Collapsed once so a multi-line description matches and snippets the
        // same way it will be drawn.
        val description = candidate.description
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        var weakest = Int.MAX_VALUE
        var total = 0
        for (token in tokens) {
            val best = bestScore(token, title, location, description) ?: return null
            weakest = minOf(weakest, best)
            total += best
        }

        val descriptionSpans = description?.let { spansIn(it, tokens) }.orEmpty()
        val titleSpans = spansIn(title, tokens)
        return Scored(
            hit = SearchHit(
                event = candidate.event,
                titleSpans = titleSpans,
                locationSpans = location?.let { spansIn(it, tokens) }.orEmpty(),
                descriptionSnippet = description
                    ?.takeIf { descriptionSpans.isNotEmpty() }
                    ?.let { snippet(it, descriptionSpans) },
            ),
            weakest = weakest,
            total = total,
            titleCoverage = coverage(title, titleSpans),
        )
    }

    /**
     * How much of [text] the match accounts for — 1.0 when the query *is* the
     * title. What separates an exact hit from one that merely starts the same
     * way: "test" and "test2" score identically otherwise, and the shorter title
     * is the closer answer whichever of the two happens to be sooner.
     */
    private fun coverage(text: String, spans: List<MatchSpan>): Double {
        if (text.isEmpty() || spans.isEmpty()) return 0.0
        return spans.sumOf { it.end - it.start }.toDouble() / text.length
    }

    /** The best a single token does across the fields, or null if it is absent. */
    private fun bestScore(
        token: String,
        title: String,
        location: String?,
        description: String?,
    ): Int? {
        val scores = listOfNotNull(
            kindIn(title, token)?.let { FIELD_TITLE * 10 + it },
            location?.let { kindIn(it, token) }?.let { FIELD_LOCATION * 10 + it },
            description?.let { kindIn(it, token) }?.let { FIELD_DESCRIPTION * 10 + it },
        )
        return scores.maxOrNull()
    }

    /**
     * How well [token] sits in [text]: at its very start, at the start of a word,
     * or inside one. Mid-word still counts — German compounds mean "termin" has
     * to keep finding "Zahnarzttermin" — it just ranks last.
     */
    private fun kindIn(text: String, token: String): Int? {
        var best: Int? = null
        var index = text.indexOf(token, startIndex = 0, ignoreCase = true)
        while (index >= 0) {
            val kind = when {
                index == 0 -> KIND_FIELD_START
                !text[index - 1].isLetterOrDigit() -> KIND_WORD_START
                else -> KIND_MID_WORD
            }
            if (kind == KIND_FIELD_START) return KIND_FIELD_START
            if (best == null || kind > best) best = kind
            index = text.indexOf(token, startIndex = index + 1, ignoreCase = true)
        }
        return best
    }

    /** Every occurrence of every token in [text], merged where they overlap. */
    private fun spansIn(text: String, tokens: List<String>): List<MatchSpan> {
        val raw = mutableListOf<MatchSpan>()
        for (token in tokens) {
            var index = text.indexOf(token, startIndex = 0, ignoreCase = true)
            while (index >= 0) {
                raw += MatchSpan(index, index + token.length)
                index = text.indexOf(token, startIndex = index + 1, ignoreCase = true)
            }
        }
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedBy { it.start }
        val merged = mutableListOf(sorted.first())
        for (span in sorted.drop(1)) {
            val last = merged.last()
            if (span.start <= last.end) {
                merged[merged.lastIndex] = MatchSpan(last.start, maxOf(last.end, span.end))
            } else {
                merged += span
            }
        }
        return merged
    }

    /**
     * A window of [description] around its first match, elided at both ends, with
     * the spans rebased onto the window. Anything that falls outside is dropped —
     * the row shows one excerpt, not the whole note.
     */
    private fun snippet(description: String, spans: List<MatchSpan>): DescriptionSnippet {
        if (description.length <= SNIPPET_LENGTH) {
            return DescriptionSnippet(description, spans)
        }
        val first = spans.first().start
        val start = (first - SNIPPET_LEAD).coerceIn(0, (description.length - SNIPPET_LENGTH))
        val end = (start + SNIPPET_LENGTH).coerceAtMost(description.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < description.length) "…" else ""
        val text = prefix + description.substring(start, end) + suffix
        val shift = prefix.length - start
        val rebased = spans
            .filter { it.start >= start && it.end <= end }
            .map { MatchSpan(it.start + shift, it.end + shift) }
        return DescriptionSnippet(text, rebased)
    }

    private data class Scored(
        val hit: SearchHit,
        val weakest: Int,
        val total: Int,
        val titleCoverage: Double,
    )
}
