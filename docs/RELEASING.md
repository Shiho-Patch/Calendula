# Releasing Calendula

Calendula is distributed through a self-hosted F-Droid repository. A release is
built, signed, and published automatically by `.gitea/workflows/release.yaml`
when a **bumped `versionName` reaches `main`** — the pipeline then creates the
matching `vX.Y.Z` tag and Gitea release itself.

## Versioning — the committed version is the source of truth

A release is defined by the `versionName`/`versionCode` committed in
`app/build.gradle.kts`:

- `versionName` = `MAJOR.MINOR.PATCH` (e.g. `2.1.0`)
- `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH` (`2.1.0` → `20100`)

So `MINOR` and `PATCH` each have room for 0–99. The release pipeline reads
`versionName`, pins `versionCode` to the derived value, builds, and — once the
APK is published — creates the tag `v<versionName>` at that commit. The tag is
an **output** of a successful release, not its trigger, so a tag always marks a
fully-shipped version (and a failure before publish leaves no tag, so re-running
the workflow safely retries).

Published version codes so far: `v0.1.0`→100 … `v1.0.0`→10000 … `v2.0.0`→20000.

## Cutting a release

1. **Assemble the release branch.** Create `release/vX.Y.Z` and merge the
   feature/fix branches that make up this release into it. This branch is the
   release candidate — everything below happens on it, before it reaches `main`.
2. Move the `## [Unreleased]` section of `CHANGELOG.md` under a new
   `## [X.Y.Z] — <date>` heading (Keep a Changelog format). The text between
   that heading and the next `## [` becomes both the Gitea release notes and
   the F-Droid per-version changelog.
3. Bump the committed `versionName` (and `versionCode`) in
   `app/build.gradle.kts` to the new version. **This bump is what triggers the
   release** when the branch merges to `main`. Then write the per-version
   "What's New" by hand to
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and commit it.

   **Keep it under 500 characters.** That one file is what both the **official**
   F-Droid repo (which reads it from the tagged source tree) and **Google Play**
   publish, and Play caps "What's New" at 500 characters while F-Droid truncates
   long entries in-client. So it is a short summary — a handful of bullets
   naming the headline changes — not a copy of the CHANGELOG.md section, which
   runs to thousands of characters. `CHANGELOG.md` stays the full account and is
   what the Gitea/Codeberg release notes use.

   Then run
   ```bash
   scripts/sync_changelog_to_fastlane.sh
   ```
   to check it. The script **keeps** a committed file untouched and only warns
   if it is over the limit; it extracts the CHANGELOG.md section as a fallback
   solely when the file is missing, so the self-hosted pipeline always has
   something to publish. The pipeline runs the same script, so forgetting to
   write the file yields a long auto-generated changelog rather than none.

   To check every locale rather than just en-US, run
   ```bash
   scripts/check_changelog_lengths.sh
   ```
   It fails on any file for the current `versionCode` that is missing, empty or
   over the limit, and only warns about older ones (already published; Play
   never re-reads them). CI runs it on every pull request. `--strict` fails on
   the older files too.
4. **Verify the release build on a real device** — the mandatory gate. The
   shipped APK is R8-shrunk/obfuscated, and bugs that only appear there, or
   only on first run, never show up in the debug build or on a device that
   already granted the calendar permission (this is how the v2.7.0 launch
   crash slipped through). Run:
   ```bash
   scripts/verify-release.sh
   ```
   It builds the `releaseTest` variant (same R8 config as `release`, debug-signed
   with a `.releasetest` suffix so it installs alongside the real app) and resets
   it to a first-run state. Then, on the device:
   - launch from a **clean / permission-not-granted** state — the permission
     screen must appear, no crash;
   - grant access — the calendar must load;
   - add both home-screen **widgets** and confirm they render;
   - exercise this release's headline changes.

   Only proceed once all of that passes on-device.
5. **Merge `release/vX.Y.Z` into `main`.** That's it — no manual tagging. The
   merge triggers `release.yaml`, which detects the new version, builds, signs,
   publishes to F-Droid, and creates the `vX.Y.Z` tag + Gitea release. **Hold UI
   releases for on-device review and explicit go-ahead before merging.**

> The `releaseTest` build type exists only for step 4 — it is never published.
> The pipeline always builds and signs the real `release` variant.

## What the pipeline does

CI and release are split so a change is built once on its PR and only does
release work when a merge actually cuts a release:

- **`ci.yaml`** (`.forgejo/workflows/`, on `pull_request`, **Codeberg**) — lint +
  unit tests + a debug assemble (and
  a Trivy scan), once per PR. Docs/metadata-only PRs skip the Android build but
  still report a green `CI` check.
- **`release.yaml`** (on push to `main`, plus `workflow_dispatch`) — a cheap
  `detect` job reads `versionName` and checks whether a tag for it already
  exists. Only when it doesn't does the `release` job run: unit tests on the
  merged commit, build & sign the release APK with the **app key**, copy it into
  the F-Droid repo, generate the per-version changelog, re-sign the index with
  the **repo key**, upload `repo/` + `metadata/`, then create the `vX.Y.Z` tag +
  Gitea release (CHANGELOG section as notes), attach the R8 `mapping.txt`, and
  mirror the release to **Codeberg** with the signed APK + a SHA-256 checksum
  (both best-effort). Ordinary merges with no version bump fall through `detect`
  and do nothing.
- **`play` job** (same workflow, after `release`) — uploads the App Bundle to
  Google Play. Runs last and separately so a Play rejection can't endanger a
  release that already shipped; skips cleanly until Play is configured.

### Codeberg direct-download channel

Alongside F-Droid, each release is mirrored to the Codeberg repo
(`jlmakiola/calendula`) as a plain download for users who don't want F-Droid.
Codeberg **push-mirrors** branches and tags to Gitea, but releases aren't git
objects and don't sync in either direction, so the pipeline creates the release
over the Codeberg API and attaches `calendula_v<version>.apk` + its `.sha256`. It's the
same APK the F-Droid repo serves (same **app key**), so it adds no trust
surface. The step is best-effort: a Codeberg outage never fails an
already-published F-Droid release, and it skips cleanly if `CODEBERG_RELEASE_TOKEN` is
unset. One-time setup: the repo's **Releases** unit must be enabled and a
`CODEBERG_RELEASE_TOKEN` secret (Codeberg access token, `write:repository` scope) added
to Gitea Actions.

### Google Play channel

Play is a third channel alongside F-Droid and the Codeberg download, and it is
the only one that gets a different artifact and a different signature.

**Artifact.** Play takes an **App Bundle** (`bundleRelease`), not the APK. It is
a second output of the same source and the same signing config — never a
repackage of the published APK, which stays untouched so the F-Droid
reproducibility guarantee is unaffected. The bundle is built at the very **end**
of the `release` job, after everything else has shipped, and both it and the
handoff to the `play` job are `continue-on-error` — nothing Play-related may
take down a release that is already published. The handoff uses a patched
`upload-artifact`/`download-artifact` fork pinned to a commit: the official v4
actions read any non-github.com forge as an unsupported GHES instance and refuse
to run on Gitea (go-gitea/gitea#36024). `dependenciesInfo` stays disabled for
the bundle too; Play's "app dependencies" report is optional and re-enabling it
would break reproducibility.

**Signature — read this before assuming an update path exists.** Play App
Signing is mandatory for new apps, and Google generates and holds the app
signing key. The release keystore in CI is registered only as the **upload
key**: Play verifies uploads with it, then re-signs with Google's key before
delivery. Consequences, accepted deliberately:

- A Play install and an F-Droid install have **different signatures** and
  **cannot update each other**. Switching channels requires uninstall +
  reinstall, which loses nothing (all data lives in the system calendar
  provider) but must be stated wherever both channels are advertised.
- Losing the upload key is **recoverable** — request an upload-key reset in the
  Play Console. Losing the app key still is not, for F-Droid.

**Build and signing are not fastlane's job.** Gradle does both, exactly as
before. fastlane appears only as the Play Developer API client (`supply`),
because the store listing already lives in `fastlane/metadata/android/` — the
same tree the official F-Droid repo harvests. One metadata source, two stores.

**What gets uploaded per release:** the AAB, the per-version "What's New"
from `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (the
hand-written summary from step 3, which is why it must stay **under 500
characters**), and the **whole store listing** — title, short and full
description, icon, feature graphic and screenshots for every locale — all in one
Play edit. Nothing about the Play listing is edited in the console any more:
change the files, and the next release pushes them. Unchanged images are
skipped (`sync_image_upload`). Every listing change goes through Play's review,
together with the bundle. To push a listing fix between releases, run
`bundle exec fastlane listing` (add `version_code:<code>` if the track holds
more than one release).

The listing tree is guarded by `scripts/check_store_listing.py`, which CI runs
on every pull request:

| Rule | Play limit |
| --- | --- |
| `title.txt` / `short_description.txt` / `full_description.txt` | 30 / 80 / 4000 chars, all three present in every locale |
| `full_description.txt` | one line per paragraph — Play renders every newline |
| `images/icon.png` | 512×512, 32-bit PNG |
| `images/featureGraphic.png` | 1024×500, JPEG or 24-bit PNG |
| `images/*Screenshots/*` | JPEG or 24-bit PNG (no alpha), sides 320–3840, long edge ≤ 2× short, 2–8 per type |
| locale directories | exactly the ones in `fastlane/store-locales.txt`, which maps every app `values-*` to a Play locale code |

A locale without its own images falls back to en-US in both stores, so graphics
only need to exist there. Adding an app language means adding its line to
`fastlane/store-locales.txt` and its listing directory — CI fails until both
exist. Store text is not on Weblate; it is edited here.

**Track.** Uploads go straight to `production` at a full rollout
(`PLAY_RELEASE_STATUS=completed`). The merge to `main` is already the human
gate — a release only gets there after on-device review — so a second manual
promotion in the Play Console added delay without adding a decision. Set the
`PLAY_TRACK` repo variable (e.g. `internal`) to stage a release instead, or
`PLAY_RELEASE_STATUS=draft` to hold it unpublished.

### Manual re-sign / recovery

A manual `workflow_dispatch` of the release workflow runs a **re-sign-only**
path: `detect` reports it's not a release, so the `release` job skips the APK
build, the version bump, and tag/release creation, and just re-signs the
existing F-Droid index with the configured repo key and re-uploads. Use this
for key rotation or repo recovery without publishing a new app version.

## Two forges, one repo

**Codeberg (`jlmakiola/calendula`) is canonical** — git, issues, PRs, tags and
releases. The self-hosted Gitea instance is build infrastructure: it holds the
signing key, publishes the F-Droid repo, and runs the release pipeline. Codeberg
push-mirrors `main` and tags to Gitea, and a bumped `versionName` arriving there
triggers `release.yaml` exactly as before.

Workflows are separated by **directory**, not by conditionals. Forgejo looks in
`.forgejo/workflows` → `.gitea/workflows` → `.github/workflows` and stops at the
first that exists; Gitea doesn't know `.forgejo/` at all:

| Directory | Runs on | Contains | Secrets |
| --- | --- | --- | --- |
| `.forgejo/workflows/` | Codeberg | `ci.yaml`, `translations.yaml` | **none** |
| `.gitea/workflows/` | Gitea | `release.yaml`, `renovate.yml` | signing key, F-Droid, Play, bot tokens |

The line is drawn at **secrets, not at CI-vs-release**. That's what makes fork
PRs safe: everything a contributor can trigger lives in `.forgejo/` and can
reference no secret. Renovate stays on the Gitea runner *even though it opens
PRs on Codeberg* — it talks to Codeberg's API rather than moving its token onto
the contributor-facing runner.

Two consequences worth remembering:

- **`detect` reads tags from Codeberg**, not from the Gitea instance it runs on.
  Push mirroring is `git push --mirror`, so a tag minted on Gitea is deleted by
  the next sync until the Codeberg tag push propagates back. Asking Gitea inside
  that window would re-cut a shipped release.
- **Any ref that exists only on Gitea gets deleted** by the mirror. That's
  correct under Codeberg-canonical, but don't debug a "vanished" branch without
  remembering it.

## Secrets (Gitea → repo Settings → Actions → Secrets)

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64`, `KEY_PASSWORD`, `KEY_ALIAS` | **App** signing key — signs the APK. Losing it means existing installs can't be updated. |
| `FDROID_KEYSTORE_BASE64` | **F-Droid repo** signing key (`keystore.p12`, base64). Signs the repo index. |
| `FDROID_CONFIG_BASE64` | F-Droid `config.yml` (base64) — repo metadata + keystore passwords. |
| `HETZNER_HOST`, `HETZNER_USER`, `HETZNER_PASS` | Upload target for the F-Droid repo. |
| `GITHUB_TOKEN` | Provided by Gitea Actions; used to create the release + attach assets. |
| `CODEBERG_RELEASE_TOKEN` | Codeberg access token (`write:repository` scope) — creates the mirrored Codeberg release + uploads the APK/checksum. Best-effort; if unset the Codeberg step skips. |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud service-account key (full JSON) with Play Console access — uploads the AAB. If unset, the `play` job skips cleanly. |

### Variables (Gitea → repo Settings → Actions → Variables)

| Variable | Default | Purpose |
| --- | --- | --- |
| `PLAY_TRACK` | `production` | Play track the bundle is uploaded to. Set to `internal`/`alpha`/`beta` to stage instead. |
| `PLAY_RELEASE_STATUS` | `completed` | `completed`, `draft`, `inProgress` or `halted`. |
| `PLAY_DRY_RUN` | `false` | `true` validates the Play edit against the API and discards it — use to rehearse. |

The two keys are independent: the **app key** signs APKs; the **repo key**
signs the index (its fingerprint is what users pin). Neither key nor the
F-Droid `config.yml` is ever uploaded to the server — they live only in CI
secrets and are reconstructed in-runner. If `FDROID_KEYSTORE_BASE64` /
`FDROID_CONFIG_BASE64` are unset the workflow **fails loudly** rather than
minting a new repo key (which would break every user's pinned fingerprint).

## Key custody & recovery

- **Offline backups** of both keys (and passwords) live in a password manager.
  These are the only safe copies — losing them is unrecoverable.
- **App key lost** → no existing install can be updated again; you'd have to
  ship a new app under a new applicationId.
- **Repo key lost or compromised** → rotate it, publish the new fingerprint in
  the README, and have users remove + re-add the repo. To rotate: generate a
  new `keystore.p12` + `config.yml`, set them as the `FDROID_*` secrets, update
  the README fingerprint, and run the manual re-sign dispatch above.

## F-Droid repo

- URL: `https://apps.dev.jeanlucmakiola.de/dev/fdroid/repo`
- Fingerprint (current): `C2C0640402BF458FC0ED957AF0B37AA4C14022E72F89CE90B5965B458CF73425`
- Served from the Hetzner storage box. **nginx serves only `…/fdroid/repo/`** —
  the working dir (key, config, metadata) sits above it and must never be
  web-reachable. After any webserver change, verify `keystore.p12` and
  `config.yml` return 404 while `repo/index-v2.json` returns 200.

## Crash deobfuscation

Each release attaches `mapping-<version>.txt.gz` (the R8 mapping) to its Gitea
release. To deobfuscate a user stacktrace, download the mapping for that
version and run it through `retrace`.
