package de.jeanlucmakiola.calendula.ui.onboarding

/** One screen of the first-launch wizard (#163), in the order they are shown. */
enum class OnboardingStep {
    /** Required: nothing works without the calendar grant. */
    Permission,

    /**
     * Only when every calendar on the device is switched off, so the app would
     * open on a blank grid: lists them and offers to switch them back on (#287).
     */
    HiddenCalendars,

    /**
     * Only when the device has nothing to put an event in — no calendar at all,
     * or none left showing after the step above. The app can make a local one,
     * or send you to whatever would do the syncing (#287).
     */
    Calendars,

    /** Whether Calendula delivers reminder notifications itself. */
    Reminders,

    /** Only when nothing you write to is synced anywhere — offers a backup. */
    Backup,

    /** Which view the app opens on, chosen from a live preview. */
    View,

    /** How the Month view lays itself out, chosen from a live preview. */
    MonthStyle,

    /** Only on an install that has calendars the device is hiding (#75). */
    Visibility,

    /** The closing screen: everything is answered and the app is ready. */
    Done,
}

/**
 * Which steps this install owes and where it has got to. [steps] is the whole
 * flow — completed steps included — so the counter doesn't renumber as steps
 * are finished; [current] is null once nothing is left and the app opens.
 */
data class OnboardingPlan(
    val steps: List<OnboardingStep>,
    val current: OnboardingStep?,
) {
    /** 1-based position of [current] in [steps], or 0 when the flow is done. */
    val index: Int get() = steps.indexOf(current) + 1

    val total: Int get() = steps.size

    /** A one-step flow is not a wizard: there is nothing to count down. */
    val showsProgress: Boolean get() = total > 1

    /** The step before [current], or null at the start (and once finished). */
    val previous: OnboardingStep? get() = steps.getOrNull(index - 2).takeIf { current != null }

    /**
     * Whether the wizard can step back. Everything is re-askable except the
     * calendar grant, which belongs to the system — once given there is nothing
     * for a back press to return to.
     */
    val canGoBack: Boolean get() = previous != null && previous != OnboardingStep.Permission
}

/**
 * Work out the flow from what is stored.
 *
 * The optional steps belong to installs still working through their first run.
 * Before the grant that can only be guessed, so one that has not answered the
 * reminder step either counts as fresh — the same condition
 * [de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs.armOnboardingWizard]
 * commits to storage a moment later, and clears again on the closing screen so
 * a later re-grant is only the permission step.
 *
 * [backupApplies] is null until the calendar list can be read, i.e. for the
 * whole permission step; the backup step is assumed to apply until proven
 * otherwise, so the flow can only ever get shorter — never sprout a step the
 * counter had not accounted for.
 *
 * [calendarsApplies] carries the same contract for the calendar step, with one
 * wrinkle: for the backup step an empty list means "could not read", but here
 * an empty list *is* the condition. The two are indistinguishable at this
 * layer and both want the step, so it turns false only once a calendar the app
 * could actually show has arrived.
 *
 * [hiddenApplies] is the step in front of it (#287): the device has calendars
 * but every one of them is switched off. It comes first because switching one
 * back on is the lighter remedy — and where it is declined, the calendar step
 * behind it still offers to make one, the same offer a device with no calendars
 * at all gets.
 *
 * The month-style step is *not* conditional on Month being the chosen view:
 * Month is reachable from the drawer whatever opens first, and a step that came
 * and went as the view is picked would move the counter under the user on the
 * step right before it.
 *
 * [visibilityArmed] carries the one-time notice that the device is hiding some
 * calendars (#75) — the only optional step an *existing* install can be given,
 * which is why it is not gated on `fresh`, and on its own it earns no closing
 * screen. It stays a pure announcement: the all-hidden *state* is
 * [hiddenApplies]'s business, up at the front of the flow where something can
 * still be done about it.
 */
fun onboardingPlan(
    hasPermission: Boolean,
    remindersDone: Boolean,
    wizardArmed: Boolean,
    backupDone: Boolean,
    viewDone: Boolean,
    monthStyleDone: Boolean,
    backupApplies: Boolean?,
    hiddenDone: Boolean = false,
    hiddenApplies: Boolean? = false,
    calendarsDone: Boolean = false,
    calendarsApplies: Boolean? = null,
    visibilityArmed: Boolean = false,
    visibilityDone: Boolean = false,
    doneShown: Boolean = false,
): OnboardingPlan {
    val fresh = wizardArmed || (!hasPermission && !remindersDone)
    val steps = buildList {
        if (!hasPermission || fresh) add(OnboardingStep.Permission)
        if (fresh && hiddenApplies != false) add(OnboardingStep.HiddenCalendars)
        if (fresh && calendarsApplies != false) add(OnboardingStep.Calendars)
        if (!remindersDone || fresh) add(OnboardingStep.Reminders)
        if (fresh && backupApplies != false) add(OnboardingStep.Backup)
        if (fresh) {
            add(OnboardingStep.View)
            add(OnboardingStep.MonthStyle)
        }
        if (visibilityArmed) add(OnboardingStep.Visibility)
        if (fresh) add(OnboardingStep.Done)
    }
    val current = steps.firstOrNull { step ->
        when (step) {
            OnboardingStep.Permission -> !hasPermission
            OnboardingStep.HiddenCalendars -> !hiddenDone
            OnboardingStep.Calendars -> !calendarsDone
            OnboardingStep.Reminders -> !remindersDone
            OnboardingStep.Backup -> !backupDone
            OnboardingStep.View -> !viewDone
            OnboardingStep.MonthStyle -> !monthStyleDone
            OnboardingStep.Visibility -> !visibilityDone
            OnboardingStep.Done -> !doneShown
        }
    }
    return OnboardingPlan(steps, current)
}
