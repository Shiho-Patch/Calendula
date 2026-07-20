package de.jeanlucmakiola.calendula.data.appname

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure decision logic behind the app-name toggle (issue #44). The framework seam
 * ([LauncherNameManager.set]/[current], which call `PackageManager`) is covered
 * on-device; these tests pin the read interpretation and the write ordering.
 */
class LauncherNameManagerTest {

    @Test
    fun `enabled calendar alias reads as CALENDAR`() {
        assertThat(launcherNameFor(PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
            .isEqualTo(LauncherName.CALENDAR)
    }

    @Test
    fun `default and disabled calendar alias read as CALENDULA`() {
        assertThat(launcherNameFor(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
            .isEqualTo(LauncherName.CALENDULA)
        assertThat(launcherNameFor(PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
            .isEqualTo(LauncherName.CALENDULA)
    }

    @Test
    fun `write plan enables the target alias before disabling the other`() {
        val toCalendar = aliasWritePlan(LauncherName.CALENDAR)
        assertThat(toCalendar).containsExactly(
            AliasStateChange(LauncherAlias.CALENDAR, enabled = true),
            AliasStateChange(LauncherAlias.DEFAULT, enabled = false),
        ).inOrder()

        val toCalendula = aliasWritePlan(LauncherName.CALENDULA)
        assertThat(toCalendula).containsExactly(
            AliasStateChange(LauncherAlias.DEFAULT, enabled = true),
            AliasStateChange(LauncherAlias.CALENDAR, enabled = false),
        ).inOrder()
    }

    @Test
    fun `write plan never disables both aliases in the same step`() {
        // The first step is always an enable, so the launcher can never observe a
        // zero-entry transient regardless of switch direction.
        for (target in LauncherName.entries) {
            assertThat(aliasWritePlan(target).first().enabled).isTrue()
        }
    }
}
