package de.jeanlucmakiola.calendula.widget

/**
 * The size step a widget draws itself at — a **user setting**, not something
 * derived from the widget's measured size (#103, #51).
 *
 * Both Glance widgets used to bucket their live size (`SizeMode.Exact` +
 * `LocalSize.current`) into a tier and scale from that. The size a launcher
 * reports is not the width the widget is actually drawn into, which broke the
 * month grid outright: its seven columns were sized from that number, came out
 * too wide, and only the first four fitted. Nothing here reads a measured size
 * any more — the user picks a step, every metric follows from it, and both
 * widgets declare `SizeMode.Single`.
 *
 * [SMALL] is the default and reproduces the widgets' original constants, so an
 * existing widget looks as it did until its owner turns the size up.
 */
enum class WidgetSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
