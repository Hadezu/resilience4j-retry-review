# Verification — 2026-10-05

## Scope and baseline

Upstream: https://github.com/resilience4j/resilience4j

Exact baseline: `7e3ab5252ed380b596e25240f19376a4435570b8` (Apache-2.0).

Only the scheduled retry exception boundary is changed. This is not validation of every upstream module or of a live external integration.

## Local environment

Windows x64; portable Eclipse Temurin JDK `21.0.12.1+1`, downloaded from Adoptium and archive SHA-256 checked against its published package metadata. Upstream wrapper: Gradle 9.4.1.

Native Gradle baseline attempts stopped before executing tests with `Unable to establish loopback connection` / `Invalid argument: connect` in Java's Windows socket implementation. IPv4, selector and no-daemon attempts did not resolve it. This is an environment blocker, not a passing baseline or an application test failure.

Independent local path: compiled all upstream core/retry production sources with `javac`, SLF4J 1.7.30 and JSR305 3.0.2, plus the new test using JUnit Platform Console Standalone 1.12.2. This is a focused secondary runner, not a substitute for the upstream Gradle build. Compiler emitted an existing deprecation-annotation warning in CheckedFunctionUtils and unchecked-operation notes.

- Before patch: 7 tests executed, 4 passed, 3 failed because the original caller future remained pending.
- After patch: 7 tests executed, 7 passed, 0 failed.
- No network request, artificial sleep, client data or real side effect in these tests.

The first restricted local compile also reported an archive-access exception. A fresh baseline compile using the original `Retry.java` from the pinned commit, in a separate output directory and outside that filesystem restriction, then succeeded and again produced exactly 4 passing / 3 failing cases. The patched compile/run also succeeded. Hosted CI subsequently independently reproduced the baseline and patched results using project-native tooling.

## Hosted verification

**SUCCESS — VERIFIED_CI_PUBLIC.** [Run 37363332252](https://github.com/Hadezu/resilience4j-retry-review/actions/runs/37363332252), tested commit `89d57091e52216fdcc7ef34ebae7c401ea34e30b`, completed `2026-10-05T19:28:42Z`.

Linux GitHub-hosted runner, Temurin 21, project wrapper Gradle 9.4.1 and upstream dependency declarations. Workflow `.github/workflows/proof.yml` performs unchanged upstream core/retry tests and retry assembly, checks the three exact regression failures on baseline, then runs patched core/retry tests and retry assembly. No deploy, upstream contact, release publishing or paid AI calls.

Downloaded and parsed native JUnit XML from the successful run's `java-proof-test-results` artifact:

| Scope | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Unchanged upstream core | 250 | 0 | 0 | 0 |
| Unchanged upstream retry | 137 | 0 | 0 | 0 |
| Patched core | 250 | 0 | 0 | 0 |
| Patched retry | 144 | 0 | 0 | 0 |

Baseline total: **387**. Patched total: **394**, including the seven new tests. Both assembly steps passed. The deliberate red stage confirms the expected three distinct pending-future failures; upstream's automatic test retries are accounted for. XML artifact retention is 14 days; this checked-in count summary and linked CI logs record the observation. Subsequent documentation-only commits preserve the tested production, test and workflow bytes; they do not imply a new whole-application test run.

Initial run [37362774584](https://github.com/Hadezu/resilience4j-retry-review/actions/runs/37362774584) passed the unchanged upstream core/retry tests and assembly, then failed the proof workflow's own count assertion: upstream's Gradle test-retry plugin repeats the three expected failing tests, yielding 16 invocations / 12 failed invocations across 7 distinct cases. The corrected workflow checks the 7 distinct cases, the exact three failed names and their pending-future messages, with no skipped/error cases. It does not accept an arbitrary build failure as a reproduction.

The original upstream Build workflow now runs only in `resilience4j/resilience4j`; its release workflow already had that guard. Dependabot configuration remains unchanged.

## Limits

No full multi-module suite, physical deployment, throughput measurement, cancellation race guarantee or external-write rollback/idempotency proof. A caller receiving an exception does not imply a remote write never happened. No client experience or upstream acceptance claimed.
