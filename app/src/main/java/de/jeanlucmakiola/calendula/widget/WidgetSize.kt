package de.jeanlucmakiola.calendula.widget

/**
 * The size step the **agenda** widget draws its text and rows at — a user
 * setting, not something derived from the widget's measured size (#51).
 *
 * The agenda widget used to bucket its live size (`SizeMode.Exact` +
 * `LocalSize.current`) into a tier and scale from that. The size a launcher
 * reports is not the size the widget is drawn into, so the tier it picked could
 * disagree with what was on screen; a size the user sets is predictable and is
 * what the #51 thread actually asked for.
 *
 * The month widget deliberately has no size setting: its grid divides whatever
 * width it is given by seven and always has, which is the behaviour to keep
 * (#103).
 *
 * [SMALL] is the default and reproduces the widget's original constants, so an
 * existing widget looks as it did until its owner turns the size up.
 */
enum class WidgetSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
