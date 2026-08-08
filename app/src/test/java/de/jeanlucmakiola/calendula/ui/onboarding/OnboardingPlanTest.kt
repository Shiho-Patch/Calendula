package de.jeanlucmakiola.calendula.ui.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The wizard's step plan (#163). Two invariants carry the whole feature: an
 * existing install must never be re-onboarded, and the counter must not
 * renumber under the user as steps are completed.
 */
class OnboardingPlanTest {

    private fun plan(
        hasPermission: Boolean = false,
        remindersDone: Boolean = false,
        wizardArmed: Boolean = false,
        backupDone: Boolean = false,
        viewDone: Boolean = false,
        monthStyleDone: Boolean = false,
        backupApplies: Boolean? = null,
    ) = onboardingPlan(
        hasPermission = hasPermission,
        remindersDone = remindersDone,
        wizardArmed = wizardArmed,
        backupDone = backupDone,
        viewDone = viewDone,
        monthStyleDone = monthStyleDone,
        backupApplies = backupApplies,
    )

    @Test
    fun `fresh install starts on the permission step`() {
        val fresh = plan()
        assertThat(fresh.current).isEqualTo(OnboardingStep.Permission)
        assertThat(fresh.index).isEqualTo(1)
        assertThat(fresh.showsProgress).isTrue()
    }

    @Test
    fun `granting the permission does not renumber the steps behind it`() {
        // The plan keeps completed steps, so the reminder step stays step 2 of
        // the same flow rather than becoming step 1 of a shorter one.
        val granted = plan(hasPermission = true, wizardArmed = true, backupApplies = true)
        assertThat(granted.steps).containsExactly(
            OnboardingStep.Permission,
            OnboardingStep.Reminders,
            OnboardingStep.Backup,
            OnboardingStep.View,
            OnboardingStep.MonthStyle,
        ).inOrder()
        assertThat(granted.current).isEqualTo(OnboardingStep.Reminders)
        assertThat(granted.index).isEqualTo(2)
    }

    @Test
    fun `a synced calendar drops the backup step`() {
        val synced = plan(
            hasPermission = true,
            wizardArmed = true,
            remindersDone = true,
            backupApplies = false,
        )
        assertThat(synced.steps).doesNotContain(OnboardingStep.Backup)
        assertThat(synced.current).isEqualTo(OnboardingStep.View)
        assertThat(synced.total).isEqualTo(4)
    }

    @Test
    fun `the backup step is assumed until the calendars can be read`() {
        // Before the grant the answer is unknowable, so the flow is planned at
        // its longest — it may shrink afterwards, never grow.
        assertThat(plan(backupApplies = null).steps).contains(OnboardingStep.Backup)
    }

    @Test
    fun `an existing install is not re-onboarded`() {
        val existing = plan(hasPermission = true, remindersDone = true)
        assertThat(existing.steps).isEmpty()
        assertThat(existing.current).isNull()
    }

    @Test
    fun `an existing install owing only the reminder step gets no counter`() {
        val existing = plan(hasPermission = true)
        assertThat(existing.steps).containsExactly(OnboardingStep.Reminders)
        assertThat(existing.current).isEqualTo(OnboardingStep.Reminders)
        assertThat(existing.showsProgress).isFalse()
    }

    @Test
    fun `re-granting the permission on an onboarded install skips the extra steps`() {
        // Revoked and granted again: the reminder step is already answered, so
        // this is not a fresh install and only the permission is owed.
        val revoked = plan(remindersDone = true)
        assertThat(revoked.steps).containsExactly(OnboardingStep.Permission)
        assertThat(revoked.showsProgress).isFalse()
    }

    @Test
    fun `answering every step ends the flow`() {
        val done = plan(
            hasPermission = true,
            remindersDone = true,
            wizardArmed = true,
            backupDone = true,
            viewDone = true,
            monthStyleDone = true,
            backupApplies = true,
        )
        assertThat(done.current).isNull()
        assertThat(done.index).isEqualTo(0)
    }

    @Test
    fun `the month style is asked whatever view was chosen`() {
        // Month is reachable from the drawer whatever opens first, and making
        // the step conditional would move the counter on the step before it.
        val afterView = plan(
            hasPermission = true,
            wizardArmed = true,
            remindersDone = true,
            backupDone = true,
            viewDone = true,
            backupApplies = true,
        )
        assertThat(afterView.current).isEqualTo(OnboardingStep.MonthStyle)
    }

    @Test
    fun `back steps to the previous answered step`() {
        val onView = plan(
            hasPermission = true,
            wizardArmed = true,
            remindersDone = true,
            backupDone = true,
            backupApplies = true,
        )
        assertThat(onView.current).isEqualTo(OnboardingStep.View)
        assertThat(onView.previous).isEqualTo(OnboardingStep.Backup)
        assertThat(onView.canGoBack).isTrue()
    }

    @Test
    fun `back is refused where the previous step is the system grant`() {
        // The permission is the system's to give; there is nothing to return to.
        val onReminders = plan(hasPermission = true, wizardArmed = true, backupApplies = true)
        assertThat(onReminders.previous).isEqualTo(OnboardingStep.Permission)
        assertThat(onReminders.canGoBack).isFalse()
    }

    @Test
    fun `back is refused on the first step and once the flow is done`() {
        assertThat(plan().canGoBack).isFalse()
        val done = plan(
            hasPermission = true,
            remindersDone = true,
            wizardArmed = true,
            backupDone = true,
            viewDone = true,
            monthStyleDone = true,
            backupApplies = true,
        )
        assertThat(done.previous).isNull()
        assertThat(done.canGoBack).isFalse()
    }
}
