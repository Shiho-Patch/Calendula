package de.jeanlucmakiola.calendula.data.calendar

/**
 * Test-only ColumnReader. Backed by a Map<Int, Any?>; any missing index is
 * treated as null. Numeric getters coerce via toLong/toInt; non-numeric values
 * yield zero (matching Android Cursor behavior for type-mismatched reads).
 */
internal class MapColumnReader(values: Map<Int, Any?>) : ColumnReader {

    private val data: Map<Int, Any?> = values

    constructor(vararg pairs: Pair<Int, Any?>) : this(pairs.toMap())

    override fun getLong(index: Int): Long = when (val v = data[index]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }

    override fun getString(index: Int): String? = data[index]?.toString()

    override fun getInt(index: Int): Int = when (val v = data[index]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }

    override fun isNull(index: Int): Boolean = data[index] == null
}
