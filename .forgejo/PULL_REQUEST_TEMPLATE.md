<!--
Thanks for contributing to Calendula!

Please skim CONTRIBUTING.md if you haven't:
https://codeberg.org/jlmakiola/calendula/src/branch/main/CONTRIBUTING.md

Two things it's easy to get wrong:
  • Features need a discussed issue first. An undiscussed feature PR may be
    closed unmerged even when the code is good.
  • Target the release branch for your issue's milestone (milestone 2.18.0 →
    release/v2.18.0), not main. If you targeted main, just say so below and it
    will be retargeted.
-->

### What this changes


### Why

<!-- Closes #123: link the issue this implements or fixes. -->


### How it was tested

<!--
Which of these ran green, and anything you exercised by hand. On-device notes
are especially useful for UI changes.

  ./gradlew lint test assembleDebug
  python3 scripts/check_translations.py
-->


### Checklist

- [ ] There's an issue for this, and (for a feature) it got a go-ahead
- [ ] Targeting the release branch for that issue's milestone (or `main`, noted above)
- [ ] `./gradlew lint test assembleDebug` passes locally
- [ ] No `values-*/strings.xml` touched (Weblate owns those; new English strings in `values/` are fine)
- [ ] `CHANGELOG.md` updated under `## [Unreleased]`, if the change is user-visible
- [ ] No planning or design documents committed
