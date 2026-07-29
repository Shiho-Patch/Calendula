source "https://rubygems.org"

# fastlane is used ONLY to upload the release bundle to Google Play
# (see fastlane/Fastfile). It is not part of the build or the signing path, so
# it never runs on a PR — only in release.yaml's `play` job.
#
# Pinned exactly; Renovate's bundler manager keeps it bumped. No Gemfile.lock is
# committed on purpose: this resolves an uploader's transitive deps, not the
# app's, and none of it affects the reproducible release build.
gem "fastlane", "2.237.0"
