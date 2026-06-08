package de.jeanlucmakiola.calendula.data.calendar

import android.database.Cursor

/**
 * Read-only view over a single row's columns by index. Lets the mappers work
 * on pure-Kotlin test fixtures (MapColumnReader) on the JVM, while the
 * production path adapts an Android Cursor row via CursorColumnReader.
 */
internal interface ColumnReader {
    fun getLong(index: Int): Long
    fun getString(index: Int): String?
    fun getInt(index: Int): Int
    fun isNull(index: Int): Boolean
}

internal class CursorColumnReader(private val cursor: Cursor) : ColumnReader {
    override fun getLong(index: Int): Long = cursor.getLong(index)
    override fun getString(index: Int): String? = cursor.getString(index)
    override fun getInt(index: Int): Int = cursor.getInt(index)
    override fun isNull(index: Int): Boolean = cursor.isNull(index)
}
