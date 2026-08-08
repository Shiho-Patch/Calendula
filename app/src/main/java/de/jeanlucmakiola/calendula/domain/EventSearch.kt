package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
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
    /** Already over, so the row can say so. Finished-today counts. */
    val isPast: Boolean = false,
)

/** One month's worth of hits, as the results list draws them under a header. */
data class SearchMonth(
    val year: Int,
    /** 1–12, so the UI can build the month's first day for a localized label. */
    val monthNumber: Int,
    val hits: List<SearchHit>,
)

/**
 * A finished search: a calendar of hits, plus the ones that only turned up
 * inside a description and would otherwise clutter it.
 */
data class SearchResults(
    val months: List<SearchMonth>,
    val inDescriptions: List<SearchHit>,
) {
    val isEmpty: Boolean get() = months.isEmpty() && inDescriptions.isEmpty()

    /** Every hit, whichever section it sits in — for select-all and delete. */
    val allHits: List<SearchHit> get() = months.flatMap { it.hits } + inDescriptions
}

/**
 * Turning a typed query into the results list.
 *
 * Matching is token-wise: the query splits on whitespace and an event has to
 * satisfy *every* token, each in any of title / location / description. Case is
 * folded through Kotlin's [String.indexOf] rather than SQL's `LIKE`, which folds
 * ASCII only — "ärzte" has to find "Ärzte".
 *
 * Results are a calendar rather than a ranking: hits are grouped by the month
 * they fall in, the current month first, then the months ahead, then the months
 * behind counting backwards. Inside a month what is still to come reads
 * forwards and what has passed follows it, so an event tomorrow sits above one
 * from yesterday. The day is the boundary, not the moment — an event that ended
 * at 19:00 is still today's business and keeps its place in today's order.
 *
 * An event the query only reached through its description is held back in
 * [SearchResults.inDescriptions]: those are the matches that made search
 * confusing when a stray word in a holiday's notes landed among events actually
 * named for the query.
 */
object EventSearch {

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
     * Search [candidates] for [query], dropping any that don't carry every token.
     * [now] marks an individual event as over; [todayStart] is where an event
     * stops being current, so everything still on today's page keeps its place
     * among the upcoming ones. [zone] resolves each event to a calendar month —
     * via [spanFirstDay], so an all-day event doesn't slip into the month before
     * it west of UTC (#82).
     */
    fun search(
        candidates: List<SearchCandidate>,
        query: String,
        now: Instant,
        todayStart: Instant,
        zone: TimeZone,
    ): SearchResults {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return SearchResults(emptyList(), emptyList())

        val matched = candidates.mapNotNull { candidate -> match(candidate, tokens, now) }
        val order = compareBy<Matched> { if (it.event.end >= todayStart) 0 else 1 }
            .thenComparator { a, b ->
                // What is left reads forwards; what is behind us reads backwards
                // from today, nearest first.
                if (a.event.end >= todayStart) {
                    a.event.start.compareTo(b.event.start)
                } else {
                    b.event.start.compareTo(a.event.start)
                }
            }

        val (inDescriptions, dated) = matched.sortedWith(order).partition { it.descriptionOnly }
        return SearchResults(
            months = groupByMonth(dated, zone, todayStart),
            inDescriptions = inDescriptions.map { it.hit },
        )
    }

    /**
     * The dated hits as months: the one we are in, then forwards, then backwards.
     * Each month keeps the order it was handed, so a month holding both sides of
     * today lists what is left before what has passed.
     */
    private fun groupByMonth(
        dated: List<Matched>,
        zone: TimeZone,
        todayStart: Instant,
    ): List<SearchMonth> {
        val currentKey = monthKey(todayStart.toLocalDateTime(zone).date)
        return dated
            .groupBy { monthKey(it.event.spanFirstDay(zone)) }
            .entries
            .sortedWith(
                compareBy<Map.Entry<Int, List<Matched>>> {
                    when {
                        it.key == currentKey -> 0
                        it.key > currentKey -> 1
                        else -> 2
                    }
                }.thenComparator { a, b ->
                    if (a.key > currentKey) a.key.compareTo(b.key) else b.key.compareTo(a.key)
                },
            )
            .map { (key, entries) ->
                SearchMonth(
                    year = key / 12,
                    monthNumber = key % 12 + 1,
                    hits = entries.map { it.hit },
                )
            }
    }

    /** Year and month as one comparable number. */
    private fun monthKey(date: LocalDate): Int = date.year * 12 + (date.month.number - 1)

    private fun match(
        candidate: SearchCandidate,
        tokens: List<String>,
        now: Instant,
    ): Matched? {
        val title = candidate.event.title
        val location = candidate.event.location?.takeIf { it.isNotBlank() }
        // Collapsed once so a multi-line description matches and snippets the
        // same way it will be drawn.
        val description = candidate.description
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        // Mid-word counts as a match — German compounds mean "termin" has to keep
        // finding "Zahnarzttermin".
        val present = { token: String ->
            title.contains(token, ignoreCase = true) ||
                location?.contains(token, ignoreCase = true) == true ||
                description?.contains(token, ignoreCase = true) == true
        }
        if (!tokens.all(present)) return null

        val descriptionSpans = description?.let { spansIn(it, tokens) }.orEmpty()
        val titleSpans = spansIn(title, tokens)
        val locationSpans = location?.let { spansIn(it, tokens) }.orEmpty()
        return Matched(
            hit = SearchHit(
                event = candidate.event,
                titleSpans = titleSpans,
                locationSpans = locationSpans,
                descriptionSnippet = description
                    ?.takeIf { descriptionSpans.isNotEmpty() }
                    ?.let { snippet(it, descriptionSpans) },
                isPast = candidate.event.end < now,
            ),
            // Nothing in the name or the place the query could have caught on —
            // the only reason this event is here is buried in its notes.
            descriptionOnly = titleSpans.isEmpty() && locationSpans.isEmpty(),
        )
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

    private data class Matched(val hit: SearchHit, val descriptionOnly: Boolean) {
        val event: EventInstance get() = hit.event
    }
}
