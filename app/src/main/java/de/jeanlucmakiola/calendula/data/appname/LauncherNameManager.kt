package de.jeanlucmakiola.calendula.data.appname

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The launcher label the app shows for itself (issue #44). */
enum class LauncherName { CALENDULA, CALENDAR }

/** The two launcher aliases declared in the manifest. */
enum class LauncherAlias { DEFAULT, CALENDAR }

/** One component-enable change in a [aliasWritePlan]. */
data class AliasStateChange(val alias: LauncherAlias, val enabled: Boolean)

/**
 * Interpret the `CalendarNameAlias` component-enabled state as a [LauncherName].
 * Only [PackageManager.COMPONENT_ENABLED_STATE_ENABLED] means the "Calendar"
 * name is active; `DEFAULT` (never toggled) and `DISABLED` both resolve to the
 * manifest default, "Calendula".
 */
fun launcherNameFor(calendarAliasState: Int): LauncherName =
    if (calendarAliasState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
        LauncherName.CALENDAR
    } else {
        LauncherName.CALENDULA
    }

/**
 * The ordered enable/disable steps to switch the launcher name to [target].
 * Always **enables the target alias first, then disables the other** — the two
 * `setComponentEnabledSetting` calls are not atomic, and this ordering means the
 * only transient the launcher can observe is a harmless two-entry state, never a
 * zero-entry one (which would briefly drop the app from the launcher).
 */
fun aliasWritePlan(target: LauncherName): List<AliasStateChange> = when (target) {
    LauncherName.CALENDAR -> listOf(
        AliasStateChange(LauncherAlias.CALENDAR, enabled = true),
        AliasStateChange(LauncherAlias.DEFAULT, enabled = false),
    )
    LauncherName.CALENDULA -> listOf(
        AliasStateChange(LauncherAlias.DEFAULT, enabled = true),
        AliasStateChange(LauncherAlias.CALENDAR, enabled = false),
    )
}

/**
 * Switches the app's launcher label between "Calendula" and "Calendar" by
 * enabling/disabling the two `<activity-alias>` components (issue #44). The
 * component-enabled state is the single source of truth — there is no persisted
 * preference — so [current] reads it straight from [PackageManager] and the two
 * always agree.
 *
 * The decision logic lives in the pure [launcherNameFor] / [aliasWritePlan]
 * functions above (JVM-tested); this class is only the thin framework seam.
 */
@Singleton
class LauncherNameManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageManager: PackageManager get() = context.packageManager

    // The class portion is namespace-qualified (suffix-free), the package portion
    // is the applicationId (which carries the .debug / .releasetest suffix). The
    // manifest's ".CalendarNameAlias" resolves its class against the namespace, so
    // the real component is <namespace>.CalendarNameAlias registered under the
    // suffixed applicationId. Do NOT use ComponentName(context, ".CalendarNameAlias")
    // — its leading-dot form prepends the applicationId to the class too, producing
    // "<appId>.CalendarNameAlias" and failing on every debug / releaseTest build.
    private fun component(alias: LauncherAlias): ComponentName {
        val simpleName = when (alias) {
            LauncherAlias.DEFAULT -> "DefaultNameAlias"
            LauncherAlias.CALENDAR -> "CalendarNameAlias"
        }
        return ComponentName(context.packageName, "$NAMESPACE.$simpleName")
    }

    /** The launcher name currently in effect. */
    fun current(): LauncherName =
        launcherNameFor(packageManager.getComponentEnabledSetting(component(LauncherAlias.CALENDAR)))

    /** Switch the launcher name to [name] (no-op cost if already active). */
    fun set(name: LauncherName) {
        for (change in aliasWritePlan(name)) {
            val state = if (change.enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                component(change.alias),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private companion object {
        /**
         * The app's namespace (R-class package) — NOT `applicationId`, which
         * carries the build-type suffix. Kept in sync with `namespace` in
         * `app/build.gradle.kts`.
         */
        const val NAMESPACE = "de.jeanlucmakiola.calendula"
    }
}
