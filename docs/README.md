# Documentation map

Where to look for what:

| Document | What it is |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Orientation tour: principles, layers, navigation, recurring-write / conflict / reminder pipelines, testing |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Release history (Keep a Changelog, SemVer) |
| [`../.planning/ROADMAP.md`](../.planning/ROADMAP.md) | Living roadmap: shipped milestones, current scope, idea backlog |
| [`../.planning/PROJECT.md`](../.planning/PROJECT.md) | What the project is, stack, naming, infrastructure |
| [`../.planning/REQUIREMENTS.md`](../.planning/REQUIREMENTS.md) | Requirement checklist per milestone |
| [`../.planning/STATE.md`](../.planning/STATE.md) | Snapshot of where development currently stands |
| [`superpowers/specs/`](superpowers/specs/) | The original design spec (2026-06-08) — historical record, not updated |
| [`superpowers/plans/`](superpowers/plans/) | Per-milestone implementation plans with task checklists — historical record of how each slice was built, including provider lessons learned |
| [`../fastlane/metadata/android/`](../fastlane/metadata/android/) | Store metadata (single source of truth): descriptions, title, icon, screenshots (DE + EN). Harvested directly by the official F-Droid repo; transformed into the self-hosted repo layout at release time by [`../scripts/fastlane_to_fdroid_localized.sh`](../scripts/fastlane_to_fdroid_localized.sh) |
| [`../fdroid-metadata/`](../fdroid-metadata/) | App-level F-Droid control file (`*.yml`: Categories, License, links) for the self-hosted repo's `fdroid update` |
| [`fdroid-official/`](fdroid-official/) | Draft recipe + notes for publishing to the **official** F-Droid repo (reproducible build + developer-signed binary) |

Conventions: plans and specs under `superpowers/` are point-in-time
artifacts of the agentic workflow that built each milestone — they get
status updates but are never rewritten. The `.planning/` files are living
documents and should stay current.
