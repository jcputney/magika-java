# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - YYYY-MM-DD

### Added

- First artifact published to Maven Central as `dev.jcputney:magika-java:0.3.0`.
- GPG-signed jar / sources / javadoc artifacts (REL-10).
- CycloneDX SBOM attached to the published bundle in JSON + XML format, schema 1.6 (REL-13).
- Sonatype Central Portal publishing pipeline via `central-publishing-maven-plugin` 0.10.0 (REL-11).
- `maven-release-plugin` 3.3.1 tag + version-bump flow, triggered manually via the `release.yml`
  GitHub Actions workflow (`workflow_dispatch`) gated by a 3-OS verify matrix (REL-12).
- Snapshot publishing to GitHub Packages on every push to `main`, GPG-signed for signature
  continuity with the Central artifacts.
- `README.md` Maven Central badge + Maven/Gradle install snippet (REL-16).

### Changed

- GitHub Actions workflow versions bumped to `actions/checkout@v6` and `actions/setup-java@v5`
  to match the release pipeline and align with the Node 24 runner runtime.

### Notes

- This is the first artifact published to Maven Central. Pre-v0.3 versions (v0.1, v0.2)
  shipped as local-install only and are captured in git tag annotations (`v0.1`, `v0.2`)
  and in the `.planning/milestones/` archives. No `## [0.1.0]` / `## [0.2.0]` sections
  appear here by design — see `README.md` `## Breaking changes in v0.2` for the v0.1→v0.2
  source-break that landed in pre-Central history.

[Unreleased]: https://github.com/jcputney/magika-java/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/jcputney/magika-java/releases/tag/v0.3.0
