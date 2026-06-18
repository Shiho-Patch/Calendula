package de.jeanlucmakiola.calendula.domain.ics

/**
 * Low-level RFC 5545 text mechanics, kept separate from [IcsWriter] so the
 * escaping and folding rules can be tested in isolation. Pure Kotlin — no
 * Android, no time handling.
 */

/** iCalendar mandates CRLF line breaks, not the platform separator. */
const val ICS_CRLF: String = "\r\n"

/** RFC 5545 §3.1: content lines SHOULD be folded at 75 octets (excluding the break). */
private const val MAX_OCTETS = 75

/**
 * Escape a TEXT value (SUMMARY, DESCRIPTION, LOCATION, …) per RFC 5545 §3.3.11:
 * backslash, semicolon and comma are escaped, newlines become the literal `\n`.
 * Backslash is handled first so it doesn't double-escape the others' markers.
 */
fun escapeText(value: String): String = buildString(value.length) {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            ';' -> append("\\;")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            '\r' -> Unit // CR is dropped; a lone CRLF in source text folds to one \n
            else -> append(ch)
        }
    }
}

/**
 * Fold a single content line to ≤75 octets per physical line, inserting
 * `CRLF + space` between segments (the space is part of the 75-octet budget of
 * the continuation line, so its content caps at 74). Folding counts UTF-8
 * octets, never splitting a multi-byte character across a boundary.
 */
fun foldLine(line: String): String {
    if (line.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return line
    val out = StringBuilder()
    var octetsThisLine = 0
    var first = true
    var i = 0
    while (i < line.length) {
        val cp = line.codePointAt(i)
        val width = Character.charCount(cp)
        val piece = line.substring(i, i + width)
        val pieceOctets = piece.toByteArray(Charsets.UTF_8).size
        // Continuation lines spend one octet on the leading space.
        val budget = if (first) MAX_OCTETS else MAX_OCTETS - 1
        if (octetsThisLine + pieceOctets > budget) {
            out.append(ICS_CRLF).append(' ')
            octetsThisLine = 0
            first = false
        }
        out.append(piece)
        octetsThisLine += pieceOctets
        i += width
    }
    return out.toString()
}
