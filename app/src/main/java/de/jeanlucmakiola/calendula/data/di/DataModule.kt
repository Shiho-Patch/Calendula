package de.jeanlucmakiola.calendula.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.jeanlucmakiola.calendula.data.calendar.AndroidCalendarDataSource
import de.jeanlucmakiola.calendula.data.calendar.CalendarDataSource
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import de.jeanlucmakiola.calendula.data.contacts.AndroidContactSpecialDatesDataSource
import de.jeanlucmakiola.calendula.data.contacts.AndroidSpecialDatesCalendarSpec
import de.jeanlucmakiola.calendula.data.contacts.ContactSpecialDatesDataSource
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesCalendarSpec
import de.jeanlucmakiola.calendula.data.reminders.ProviderReminderInstanceSource
import de.jeanlucmakiola.calendula.data.reminders.ReminderInstanceSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

private val Context.calendulaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calendula_prefs",
)

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {

    @Binds
    @Singleton
    abstract fun bindCalendarDataSource(
        impl: AndroidCalendarDataSource,
    ): CalendarDataSource

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl,
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindReminderInstanceSource(
        impl: ProviderReminderInstanceSource,
    ): ReminderInstanceSource

    @Binds
    @Singleton
    abstract fun bindContactSpecialDatesDataSource(
        impl: AndroidContactSpecialDatesDataSource,
    ): ContactSpecialDatesDataSource

    @Binds
    @Singleton
    abstract fun bindSpecialDatesCalendarSpec(
        impl: AndroidSpecialDatesCalendarSpec,
    ): SpecialDatesCalendarSpec
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvideModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.calendulaDataStore

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
