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
   release** when the branch merges to `main`. Then run
   ```bash
   scripts/sync_changelog_to_fastlane.sh
   ```
   and commit the generated
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. This is what
   makes the **official** F-Droid repo show this version's changelog (it reads
   the changelog from the tagged source tree). The self-hosted pipeline
   regenerates it regardless, so forgetting only affects the official listing.
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

- **`ci.yaml`** (on `pull_request`) — lint + unit tests + a debug assemble (and
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

### Codeberg direct-download channel

Alongside F-Droid, each release is mirrored to the Codeberg repo
(`jlmakiola/calendula`) as a plain download for users who don't want F-Droid.
Gitea already **push-mirrors** branches and tags to Codeberg, but releases
aren't git objects and don't sync, so the pipeline creates the release over the
Codeberg API and attaches `calendula_v<version>.apk` + its `.sha256`. It's the
same APK the F-Droid repo serves (same **app key**), so it adds no trust
surface. The step is best-effort: a Codeberg outage never fails an
already-published F-Droid release, and it skips cleanly if `CODEBERG_TOKEN` is
unset. One-time setup: the repo's **Releases** unit must be enabled and a
`CODEBERG_TOKEN` secret (Codeberg access token, `write:repository` scope) added
to Gitea Actions.

### Manual re-sign / recovery

A manual `workflow_dispatch` of the release workflow runs a **re-sign-only**
path: `detect` reports it's not a release, so the `release` job skips the APK
build, the version bump, and tag/release creation, and just re-signs the
existing F-Droid index with the configured repo key and re-uploads. Use this
for key rotation or repo recovery without publishing a new app version.

## Secrets (Gitea → repo Settings → Actions → Secrets)

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64`, `KEY_PASSWORD`, `KEY_ALIAS` | **App** signing key — signs the APK. Losing it means existing installs can't be updated. |
| `FDROID_KEYSTORE_BASE64` | **F-Droid repo** signing key (`keystore.p12`, base64). Signs the repo index. |
| `FDROID_CONFIG_BASE64` | F-Droid `config.yml` (base64) — repo metadata + keystore passwords. |
| `HETZNER_HOST`, `HETZNER_USER`, `HETZNER_PASS` | Upload target for the F-Droid repo. |
| `GITHUB_TOKEN` | Provided by Gitea Actions; used to create the release + attach assets. |
| `CODEBERG_TOKEN` | Codeberg access token (`write:repository` scope) — creates the mirrored Codeberg release + uploads the APK/checksum. Best-effort; if unset the Codeberg step skips. |

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
