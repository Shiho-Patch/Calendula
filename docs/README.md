# Documentation map

Where to look for what:

| Document | What it is |
|---|---|
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | How to contribute: issue-first workflow, which branch to target, translations, the rules a change is reviewed against |
| [`BUILDING.md`](BUILDING.md) | Building from source: submodule, JDK/SDK requirements, Gradle tasks, what CI runs |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Orientation tour: principles, layers, navigation, recurring-write / conflict / reminder pipelines, testing |
| [`RELEASING.md`](RELEASING.md) | Release process: versioning, the merge-driven pipeline, the two-forge split, secrets, key custody |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Release history (Keep a Changelog, SemVer) |
| [Issues](https://codeberg.org/jlmakiola/calendula/issues) + [milestones](https://codeberg.org/jlmakiola/calendula/milestones) | **The roadmap.** What's planned, in progress, and shipped — a milestone maps to its `release/vX.Y.Z` branch |
| [`../.planning/PROJECT.md`](../.planning/PROJECT.md) | What the project is, stack, naming, infrastructure |
| [`../.planning/REQUIREMENTS.md`](../.planning/REQUIREMENTS.md) | Historical V1/V2 requirement checklist, plus the stack/platform constraints |
| [`design/`](design/) | Per-feature design notes kept for features whose provider behaviour is worth recording |
| [`../fastlane/metadata/android/`](../fastlane/metadata/android/) | Store metadata (single source of truth): descriptions, title, icon, screenshots (DE + EN). Harvested directly by the official F-Droid repo; transformed into the self-hosted repo layout at release time by [`../scripts/fastlane_to_fdroid_localized.sh`](../scripts/fastlane_to_fdroid_localized.sh) |
| [`../fdroid-metadata/`](../fdroid-metadata/) | App-level F-Droid control file (`*.yml`: Categories, License, links) for the self-hosted repo's `fdroid update` |
| [`fdroid-official/`](fdroid-official/) | Recipe + notes for publishing to the **official** F-Droid repo (reproducible build + developer-signed binary) |

Conventions: planning lives in the **issue tracker**, not in this repository. The
`.planning/` files that predated it (a roadmap and a development-state snapshot)
are gone — issues and milestones say the same thing without going stale.
`ARCHITECTURE.md` is the authoritative orientation tour: it is updated with the
code, and is the right place for a lesson learned about the calendar provider.
