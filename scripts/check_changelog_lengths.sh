#!/usr/bin/env bash
# Length guard for the per-version "What's New" files under
# fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt.
#
# Google Play rejects an upload whose "What's New" exceeds 500 characters, and
# F-Droid truncates a long entry in-client. Play reads only the version being
# uploaded, so the file for the CURRENT versionCode is release-blocking in every
# locale it exists in — that is a hard failure here. Older files are already
# published and Play never looks at them again; they are reported as warnings so
# a legacy overrun (the pipeline used to auto-generate these from CHANGELOG.md)
# does not turn every PR red.
#
#   scripts/check_changelog_lengths.sh            current version fails, rest warn
#   scripts/check_changelog_lengths.sh --strict   every file must fit
#
# The companion scripts/sync_changelog_to_fastlane.sh checks only en-US for the
# current version, and only as a side effect of ensuring the file exists.
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

# wc -m counts bytes, not characters, under a C locale — force UTF-8 so "•" and
# "—" count as the single characters Play sees.
if locale -a 2>/dev/null | grep -qix 'C.utf8\|C.UTF-8'; then
  export LC_ALL=C.UTF-8
fi

LIMIT=500
STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
[ -n "$VERSION" ] || { echo "No versionName in app/build.gradle.kts" >&2; exit 1; }
MAJOR=${VERSION%%.*}; rest=${VERSION#*.}; MINOR=${rest%%.*}; PATCH=${rest##*.}
VERSION_CODE=$(( ${MAJOR:-0} * 10000 + ${MINOR:-0} * 100 + ${PATCH:-0} ))

fail=0
warned=0
current=0

shopt -s nullglob
for f in fastlane/metadata/android/*/changelogs/*.txt; do
  locale_dir=$(basename "$(dirname "$(dirname "$f")")")
  code=$(basename "$f" .txt)
  chars=$(wc -m < "$f" | tr -d ' ')

  if [ "$code" = "$VERSION_CODE" ]; then
    current=$((current + 1))
    if [ ! -s "$f" ]; then
      echo "ERROR: $f is empty." >&2
      fail=1
    elif [ "$chars" -gt "$LIMIT" ]; then
      echo "ERROR: $f is $chars chars (limit $LIMIT) — Play would reject this release." >&2
      fail=1
    else
      printf 'OK: %-6s %s — %s chars\n' "$locale_dir" "$code" "$chars"
    fi
  elif [ "$chars" -gt "$LIMIT" ]; then
    if [ "$STRICT" -eq 1 ]; then
      echo "ERROR: $f is $chars chars (limit $LIMIT)." >&2
      fail=1
    else
      echo "warning: $f is $chars chars (limit $LIMIT) — already published, left as is." >&2
      warned=$((warned + 1))
    fi
  fi
done

if [ "$current" -eq 0 ]; then
  echo "ERROR: no changelog for version $VERSION (code $VERSION_CODE)." >&2
  echo "       Write fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt" >&2
  echo "       before releasing — see docs/RELEASING.md step 3." >&2
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo >&2
  echo "Fix the changelog(s) above before releasing." >&2
  exit 1
fi
[ "$warned" -gt 0 ] && echo "$warned older changelog(s) over the limit — warnings only."
echo "All release-blocking changelogs fit in $LIMIT characters."
