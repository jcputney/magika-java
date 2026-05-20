# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0] - 2026-05-20

### Added

- MIME-focused `detectBytes`, `detectPath`, and `detectStream` APIs returning
  `DetectedContentType`.
- Allowlist verification APIs via `ExpectedContentTypes`, `VerificationResult`, and
  `VerificationReason`.
- Optional `dev.jcputney:magika-java-tika` artifact with an embedded-ONNX Apache Tika
  `Detector` adapter and service-loader registration.
- GitHub CodeQL static analysis workflow (`.github/workflows/codeql.yml`) running on
  push, pull request, and weekly cron with the `security-and-quality` query suite.
- OWASP Dependency-Check SCA scan (`.github/workflows/dependency-check.yml`) running
  on push to `main`, weekly cron, and `workflow_dispatch`. Uses the
  `dependency-check/Dependency-Check_Action` Docker wrapper (pre-warmed NVD dataset),
  scans only the consumer-facing runtime closure assembled via
  `dependency:copy-dependencies`, and fails the build on CVSS ≥ 7. SARIF uploaded to
  the GitHub Security tab.
- PR-time dependency review via `actions/dependency-review-action`
  (`.github/workflows/dependency-review.yml`) — fast GHSA-backed check on every pull
  request, complements the deeper scheduled OWASP scan.
- Dependabot auto-merge workflow (`.github/workflows/dependabot-auto-merge.yml`) that
  queues patch and minor Dependabot bumps for `--auto --squash` once required checks
  pass.
- Release workflow now attaches per-module CycloneDX SBOMs (JSON + XML) to the GitHub
  Release and generates Sigstore-backed `actions/attest-build-provenance` attestations
  for the published JARs plus `actions/attest-sbom` attestations linking each SBOM to
  its JAR.

### Changed

- Project converted to a Maven reactor while preserving the core artifact coordinates
  `dev.jcputney:magika-java`.
- Runtime dependency bumps: `jackson-databind` 2.21.2 → 2.21.3, `slf4j-api` 2.0.17 →
  2.0.18, `onnxruntime` 1.25.0 → 1.26.0.

## [0.3.0] - 2026-04-27

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
  appear here by design — those versions had no external consumers.

[Unreleased]: https://github.com/jcputney/magika-java/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/jcputney/magika-java/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/jcputney/magika-java/releases/tag/v0.3.0
