package de.jeanlucmakiola.calendula.ui.licences

/**
 * The third-party code Calendula ships, and what each licence obliges us to say.
 *
 * This screen is a **licence obligation, not an about-page nicety**:
 * **Apache-2.0 §4(a)** requires the licence to travel with the work and **§4(d)**
 * propagates any `NOTICE` file. What discharges that is what the recipient of the
 * APK is shown — a notice only visible to someone reading the repository does not
 * count. Nearly everything below is Apache-2.0.
 *
 * Hand-maintained on purpose. Generators read POM metadata, and POM metadata is
 * routinely wrong — the licence name is often not an SPDX id and the licence URL
 * frequently 404s, which produces confidently incomplete output. A short honest
 * list beats a long generated one that omits the entry that mattered.
 *
 * **When adding a dependency, add it here.** There is no automated check that can
 * tell you that you forgot. Test-only dependencies (JUnit, Truth, Turbine) are not
 * shipped in the APK and stay off the list.
 */
data class Attribution(
    val name: String,
    val copyright: String,
    val licence: Licence,
    /** Where the source can actually be obtained; what each row links to. */
    val sourceUrl: String,
)

enum class Licence(val spdxId: String, val url: String) {
    APACHE_2("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    MIT("MIT", "https://opensource.org/license/mit"),
}

object OpenSourceLicences {

    val ALL: List<Attribution> = listOf(
        Attribution(
            name = "AndroidX, Jetpack Compose, Glance, WorkManager, DataStore, DocumentFile",
            copyright = "© The Android Open Source Project",
            licence = Licence.APACHE_2,
            sourceUrl = "https://cs.android.com/androidx/platform/frameworks/support",
        ),
        Attribution(
            name = "Kotlin, kotlinx.coroutines, kotlinx.datetime",
            copyright = "© JetBrains s.r.o. and Kotlin Programming Language contributors",
            licence = Licence.APACHE_2,
            sourceUrl = "https://github.com/JetBrains/kotlin",
        ),
        Attribution(
            name = "Dagger and Hilt",
            copyright = "© Google LLC and The Dagger Authors",
            licence = Licence.APACHE_2,
            sourceUrl = "https://github.com/google/dagger",
        ),
        Attribution(
            name = "Material Design icons",
            copyright = "© Google LLC",
            licence = Licence.APACHE_2,
            sourceUrl = "https://github.com/google/material-design-icons",
        ),
        Attribution(
            name = "floret-kit",
            copyright = "© Jean-Luc Makiola",
            licence = Licence.MIT,
            sourceUrl = "https://codeberg.org/jlmakiola/floret-kit",
        ),
    )
}
