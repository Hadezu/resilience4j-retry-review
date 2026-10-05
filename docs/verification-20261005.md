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

The first restricted local compile also reported an archive-access exception; the patched compile/run succeeded outside that filesystem restriction. Hosted CI must independently reproduce baseline and patched results using project-native tooling before VERIFIED_CI_PUBLIC.

## Hosted verification

Pending. Workflow `.github/workflows/proof.yml` performs upstream core/retry baseline tests/build, checks the three exact regression failures on baseline and runs patched core/retry tests/build. No deploy, upstream contact, release publishing or paid AI calls.

## Limits

No full multi-module suite, physical deployment, throughput measurement, cancellation race guarantee or external-write rollback/idempotency proof. A caller receiving an exception does not imply a remote write never happened. No client experience or upstream acceptance claimed.
