package de.jeanlucmakiola.calendula.ui.onboarding

/** One screen of the first-launch wizard (#163), in the order they are shown. */
enum class OnboardingStep {
    /** Required: nothing works without the calendar grant. */
    Permission,

    /** Whether Calendula delivers reminder notifications itself. */
    Reminders,

    /** Only when nothing you write to is synced anywhere — offers a backup. */
    Backup,

    /** Which view the app opens on, chosen from a live preview. */
    View,

    /** How the Month view lays itself out, chosen from a live preview. */
    MonthStyle,
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
 * The optional steps are only for installs that went through the grant in-app —
 * an existing user sees nothing new. Before the grant that can only be guessed,
 * so an install that has not answered the reminder step either is treated as
 * fresh, which is the same condition [de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs.armOnboardingWizard]
 * commits to storage a moment later.
 *
 * [backupApplies] is null until the calendar list can be read, i.e. for the
 * whole permission step. The backup step is assumed to apply until proven
 * otherwise, so the flow can only ever get shorter — never sprout a step the
 * counter had not accounted for.
 *
 * The month-style step is *not* conditional on Month being the chosen view:
 * Month is reachable from the drawer whatever opens first, and making the step
 * appear and disappear as the view is picked would move the counter under the
 * user on the step right before it.
 */
fun onboardingPlan(
    hasPermission: Boolean,
    remindersDone: Boolean,
    wizardArmed: Boolean,
    backupDone: Boolean,
    viewDone: Boolean,
    monthStyleDone: Boolean,
    backupApplies: Boolean?,
): OnboardingPlan {
    val fresh = wizardArmed || (!hasPermission && !remindersDone)
    val steps = buildList {
        if (!hasPermission || fresh) add(OnboardingStep.Permission)
        if (!remindersDone || fresh) add(OnboardingStep.Reminders)
        if (fresh && backupApplies != false) add(OnboardingStep.Backup)
        if (fresh) {
            add(OnboardingStep.View)
            add(OnboardingStep.MonthStyle)
        }
    }
    val current = steps.firstOrNull { step ->
        when (step) {
            OnboardingStep.Permission -> !hasPermission
            OnboardingStep.Reminders -> !remindersDone
            OnboardingStep.Backup -> !backupDone
            OnboardingStep.View -> !viewDone
            OnboardingStep.MonthStyle -> !monthStyleDone
        }
    }
    return OnboardingPlan(steps, current)
}
