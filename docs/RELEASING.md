# Releasing Calendula

Calendula is distributed through a self-hosted F-Droid repository. Every
release is built, signed, and published automatically by
`.gitea/workflows/release.yaml` when a version tag is pushed.

## Versioning — the git tag is the single source of truth

A release is defined by its tag, `vMAJOR.MINOR.PATCH` (e.g. `v2.1.0`). At
release time the workflow derives both Gradle fields from the tag:

- `versionName` = the tag without the leading `v` (`2.1.0`)
- `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH` (`2.1.0` → `20100`)

So `MINOR` and `PATCH` each have room for 0–99. The values committed in
`app/build.gradle.kts` are only the dev/local default — CI overwrites them
from the tag. Keep the committed `versionCode`/`versionName` matching the
**latest released tag** so local builds are sanely versioned; the published
value always comes from the tag.

Published version codes so far: `v0.1.0`→100 … `v1.0.0`→10000 … `v2.0.0`→20000.

## Cutting a release

1. **Assemble the release branch.** Create `release/vX.Y.Z` and merge the
   feature/fix branches that make up this release into it. This branch is the
   release candidate — everything below happens on it, before it reaches `main`.
2. Move the `## [Unreleased]` section of `CHANGELOG.md` under a new
   `## [X.Y.Z] — <date>` heading (Keep a Changelog format). The text between
   that heading and the next `## [` becomes both the Gitea release notes and
   the F-Droid per-version changelog.
3. Bump the committed `versionCode`/`versionName` in `app/build.gradle.kts` to
   match the new version (keeps local builds tidy; CI overwrites from the tag).
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
5. Merge `release/vX.Y.Z` into `main`, then tag and push:
   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
6. The push triggers the release workflow. **Hold UI releases for on-device
   review and explicit go-ahead before tagging.**

> The `releaseTest` build type exists only for step 4 — it is never published.
> The pipeline always builds and signs the real `release` variant from the tag.

## What the pipeline does

`release.yaml` has three jobs:

- **ci** — unit tests + a debug assemble (sanity).
- **build-and-deploy** — derives the version, builds & signs the release APK
  with the app key, copies it into the F-Droid repo, generates the per-version
  changelog, re-signs the F-Droid index with the **repo key**, uploads
  `repo/` + `metadata/` to the box, and attaches the R8 `mapping.txt` to the
  Gitea release (best-effort).
- **gitea-release** — creates/updates the Gitea release carrying the tag's
  CHANGELOG section as notes. Gated on `ci` only (not the deploy) so notes
  publish even if the F-Droid upload hiccups.

### Manual re-sign / recovery

A manual `workflow_dispatch` of the release workflow **from a branch** (not a
tag) runs a **re-sign-only** path: it skips the APK build and just re-signs
the existing F-Droid index with the configured repo key and re-uploads. Use
this for key rotation or repo recovery without publishing a new app version.

## Secrets (Gitea → repo Settings → Actions → Secrets)

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64`, `KEY_PASSWORD`, `KEY_ALIAS` | **App** signing key — signs the APK. Losing it means existing installs can't be updated. |
| `FDROID_KEYSTORE_BASE64` | **F-Droid repo** signing key (`keystore.p12`, base64). Signs the repo index. |
| `FDROID_CONFIG_BASE64` | F-Droid `config.yml` (base64) — repo metadata + keystore passwords. |
| `HETZNER_HOST`, `HETZNER_USER`, `HETZNER_PASS` | Upload target for the F-Droid repo. |
| `GITHUB_TOKEN` | Provided by Gitea Actions; used to create the release + attach assets. |

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
