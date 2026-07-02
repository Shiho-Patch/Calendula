package de.jeanlucmakiola.calendula.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.domain.contacts.ContactSpecialDate
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.domain.contacts.parseContactEventDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the dated `Event` rows (birthdays, anniversaries, custom dates) of the
 * device's contacts. Read-only and offline — the one-way source for the
 * special-dates mirror. Requires `READ_CONTACTS`; returns an empty list when
 * the permission is absent so the sync can degrade to a stalled state rather
 * than crash.
 */
interface ContactSpecialDatesDataSource {
    fun hasPermission(): Boolean

    /** All usable contact special-dates, deduplicated per contact and type. */
    fun readSpecialDates(): List<ContactSpecialDate>
}

/** Whether `READ_CONTACTS` is granted — the gate for every contacts read. */
fun Context.hasContactsPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

/** Map a `ContactsContract` event `TYPE` to our calendar bucket. */
internal fun specialDateTypeForRawEventType(type: Int): SpecialDateType = when (type) {
    Event.TYPE_BIRTHDAY -> SpecialDateType.Birthday
    Event.TYPE_ANNIVERSARY -> SpecialDateType.Anniversary
    else -> SpecialDateType.Custom
}

@Singleton
class AndroidContactSpecialDatesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactSpecialDatesDataSource {

    override fun hasPermission(): Boolean = context.hasContactsPermission()

    override fun readSpecialDates(): List<ContactSpecialDate> {
        if (!hasPermission()) return emptyList()
        val resolver = context.contentResolver
        // A contact can carry the same date more than once (multiple raw
        // contacts under one aggregate); keep the first per (contact, type).
        val seen = HashSet<Pair<String, SpecialDateType>>()
        val result = ArrayList<ContactSpecialDate>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                PROJECTION,
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(Event.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                val idxDate = c.getColumnIndexOrThrow(Event.START_DATE)
                val idxType = c.getColumnIndexOrThrow(Event.TYPE)
                val idxLabel = c.getColumnIndexOrThrow(Event.LABEL)
                val idxLookup = c.getColumnIndexOrThrow(ContactsContract.Data.LOOKUP_KEY)
                val idxName = c.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
                while (c.moveToNext()) {
                    val lookup = c.getString(idxLookup)?.takeIf { it.isNotEmpty() } ?: continue
                    val parts = parseContactEventDate(c.getString(idxDate)) ?: continue
                    val type = specialDateTypeForRawEventType(c.getInt(idxType))
                    if (!seen.add(lookup to type)) continue
                    result += ContactSpecialDate(
                        lookupKey = lookup,
                        displayName = c.getString(idxName)?.trim().orEmpty(),
                        type = type,
                        month = parts.month,
                        day = parts.day,
                        year = parts.year,
                        label = c.getString(idxLabel)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Reading contact special-dates failed", it) }
        return result
    }

    private companion object {
        const val TAG = "ContactSpecialDates"

        val PROJECTION = arrayOf(
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            Event.START_DATE,
            Event.TYPE,
            Event.LABEL,
        )
    }
}
