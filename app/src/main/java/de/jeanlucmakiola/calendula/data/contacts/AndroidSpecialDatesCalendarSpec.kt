package de.jeanlucmakiola.calendula.data.contacts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.CalendarColorPalette
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the localized calendar names/templates and per-type colours the
 * mirror creates its calendars with. The colours are picked from the shared
 * palette so managed calendars look native alongside user calendars.
 */
@Singleton
class AndroidSpecialDatesCalendarSpec @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpecialDatesCalendarSpec {

    override fun displayName(type: SpecialDateType): String = context.getString(
        when (type) {
            SpecialDateType.Birthday -> R.string.special_dates_calendar_birthday
            SpecialDateType.Anniversary -> R.string.special_dates_calendar_anniversary
            SpecialDateType.Custom -> R.string.special_dates_calendar_custom
        },
    )

    override fun defaultTitleTemplate(type: SpecialDateType): String = context.getString(
        when (type) {
            SpecialDateType.Birthday -> R.string.special_dates_default_title_birthday
            SpecialDateType.Anniversary -> R.string.special_dates_default_title_anniversary
            SpecialDateType.Custom -> R.string.special_dates_default_title_custom
        },
    )

    override fun color(type: SpecialDateType): Int = when (type) {
        SpecialDateType.Birthday -> CalendarColorPalette.Purple
        SpecialDateType.Anniversary -> CalendarColorPalette.Red
        SpecialDateType.Custom -> CalendarColorPalette.Blue
    }
}
