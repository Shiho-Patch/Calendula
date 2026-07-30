package de.jeanlucmakiola.calendula.widget

/**
 * The size step the **agenda** widget draws its text and rows at — a user
 * setting, not the measured size (#51), which a launcher reports inaccurately.
 * The month widget keeps sizing itself from the width it is given (#103).
 *
 * [SMALL] is the default and reproduces the widget's original constants.
 */
enum class WidgetSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
