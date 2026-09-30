# Documentation map

Where to look for what:

| Document | What it is |
|---|---|
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | How to contribute: issue-first workflow, which branch to target, translations, the rules a change is reviewed against |
| [`BUILDING.md`](BUILDING.md) | Building from source: submodule, JDK/SDK requirements, Gradle tasks, what CI runs |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Orientation tour: principles, layers, navigation, recurring-write / conflict / reminder pipelines, testing |
| [`RELEASING.md`](RELEASING.md) | Release process: versioning, the merge-driven pipeline, the two-forge split, secrets, key custody |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Release history (Keep a Changelog, SemVer) |
| [Issues](https://codeberg.org/jlmakiola/calendula/issues) + [milestones](https://codeberg.org/jlmakiola/calendula/milestones) | The roadmap: what's planned, in progress and shipped. A milestone maps to its `release/vX.Y.Z` branch |
| [`../.planning/PROJECT.md`](../.planning/PROJECT.md) | What the project is: core value, stack + version pins, constraints, naming, forge/release infrastructure |
| [`design/`](design/) | Per-feature design notes kept for features whose provider behaviour is worth recording |
| [`../fastlane/metadata/android/`](../fastlane/metadata/android/) | Store metadata (single source of truth) for every app language, mapped in `store-locales.txt`: descriptions, title, graphics. Harvested directly by the official F-Droid repo, pushed to Google Play with every release, validated by [`../scripts/check_store_listing.py`](../scripts/check_store_listing.py); transformed into the self-hosted repo layout at release time by [`../scripts/fastlane_to_fdroid_localized.sh`](../scripts/fastlane_to_fdroid_localized.sh) |
| [`../fdroid-metadata/`](../fdroid-metadata/) | App-level F-Droid control file (`*.yml`: Categories, License, links) for the self-hosted repo's `fdroid update` |
| [`fdroid-official/`](fdroid-official/) | Recipe and notes for publishing to the official F-Droid repo (reproducible build + developer-signed binary) |

Planning lives in the issue tracker and its milestones, not in this repository.
`.planning/PROJECT.md` describes the project itself.
`ARCHITECTURE.md` is updated with the code and is the place to record a lesson
learned about the calendar provider.
