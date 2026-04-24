# Namespace claim

The Maven Central Portal namespace `dev.jcputney` is verified via DNS TXT
record on `jcputney.dev`. Same pattern as sibling `mjml-java` (see
`~/git/mjml-java/pom.xml`).

Status (check before Plan 6 / first publish attempt):
- Portal URL: https://central.sonatype.com/account
- DNS record: TXT `jcputney.dev` -> `sonatype-<token>`

Phase 1 does NOT publish. This doc records the claim so Plan 3 (Phase 3)
can skip the namespace-verification step and go straight to
`central-publishing-maven-plugin` wiring.

CI note (CI-01): GitHub Actions matrix uses `fail-fast: false` so one
leg's failure does not mask the others. Branch-protection "required
checks" configuration is a human-operated step after CI lands in Plan 6
— documented but not enforced by the build.
