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

/**
 * Reverse of [escapeText]: turn `\n`/`\N` into newlines and unescape `\\`, `\;`,
 * `\,`. A backslash before any other character is dropped, keeping the
 * character (lenient — foreign files escape liberally).
 */
fun unescapeText(value: String): String = buildString(value.length) {
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            when (val next = value[i + 1]) {
                'n', 'N' -> append('\n')
                else -> append(next) // \\, \;, \, and any other escaped char
            }
            i += 2
        } else {
            append(c)
            i++
        }
    }
}

/**
 * Reverse of [foldLine] across a whole document: split into physical lines on
 * CRLF/LF/CR, then re-join any line that begins with a single space or tab onto
 * the previous one (RFC 5545 unfolding). Returns the logical content lines.
 */
fun unfoldLines(text: String): List<String> {
    val out = mutableListOf<String>()
    for (physical in text.split("\r\n", "\n", "\r")) {
        if (physical.isEmpty()) continue
        val isContinuation = physical[0] == ' ' || physical[0] == '\t'
        if (isContinuation && out.isNotEmpty()) {
            out[out.lastIndex] = out.last() + physical.substring(1)
        } else {
            out.add(physical)
        }
    }
    return out
}

/**
 * One unfolded content line split into its property name, parameters and value:
 * `SUMMARY;LANGUAGE=en:Lunch` → name `SUMMARY`, params `{LANGUAGE=en}`, value
 * `Lunch`. The value is everything after the first colon that isn't inside a
 * quoted parameter; param keys are upper-cased, quoted param values unquoted.
 * Returns null for a line with no colon.
 */
data class IcsContentLine(val name: String, val params: Map<String, String>, val value: String)

fun parseContentLine(line: String): IcsContentLine? {
    var inQuote = false
    var colon = -1
    for (i in line.indices) {
        when (line[i]) {
            '"' -> inQuote = !inQuote
            ':' -> if (!inQuote) { colon = i; break }
        }
    }
    if (colon < 0) return null
    val head = splitUnquoted(line.substring(0, colon), ';')
    val name = head.firstOrNull()?.trim()?.uppercase().orEmpty()
    if (name.isEmpty()) return null
    val params = buildMap {
        for (part in head.drop(1)) {
            val eq = part.indexOf('=')
            if (eq > 0) {
                put(
                    part.substring(0, eq).trim().uppercase(),
                    part.substring(eq + 1).trim().removeSurrounding("\""),
                )
            }
        }
    }
    return IcsContentLine(name, params, line.substring(colon + 1))
}

/** Split on [delimiter] except where it falls inside a double-quoted run. */
private fun splitUnquoted(text: String, delimiter: Char): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var inQuote = false
    for (c in text) {
        when {
            c == '"' -> { inQuote = !inQuote; current.append(c) }
            c == delimiter && !inQuote -> { parts.add(current.toString()); current.clear() }
            else -> current.append(c)
        }
    }
    parts.add(current.toString())
    return parts
}
