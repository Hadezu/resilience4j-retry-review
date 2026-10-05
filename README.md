# Java retry failure handling — independent proof

[Read the case study](CASE-STUDY.md) · [Verification](docs/verification-20261005.md) · [Upstream README](README.adoc)

A scoped change to **Resilience4j**, a Java fault-tolerance library: when a scheduled retry throws before returning a completion stage, the original caller future now completes with that error instead of remaining pending.

This repository is an attributed maintenance exercise by **Ivan Matiushkin**, developed with Codex. Resilience4j and its existing functionality belong to the upstream contributors. Apache-2.0 attribution and source history are retained. This work is not an upstream release, client project or production deployment.

## Inspect quickly

- One production file changed: `resilience4j-retry/src/main/java/io/github/resilience4j/retry/Retry.java`.
- Seven deterministic tests cover scheduled exceptions/errors, result-triggered retries, initial-throw compatibility, async recovery/exhaustion and rejected scheduling.
- The CI workflow checks the unchanged baseline, requires three regressions to fail there, then tests/builds the patched modules.
- Initial synchronous failures still propagate. This patch does not blindly retry a potentially unsafe operation.

```sh
# JDK 21; dependencies download from Gradle/Maven repositories
bash gradlew :resilience4j-retry:test --tests '*ScheduledSupplierFailureTest' --no-daemon
bash gradlew :resilience4j-core:test :resilience4j-retry:test :resilience4j-retry:assemble --no-daemon
```

Windows users can use `gradlew.bat`; the author's local Gradle socket limitation is documented in verification. No private credentials, service account or paid infrastructure is required to run the project.

**Status: VERIFIED_LOCAL (focused direct JUnit runner); native Gradle CI pending.** Do not describe this as public CI-verified until the linked verification record confirms it.

This demonstrates one trace/reproduce/fix/test/handover workflow in an existing Java codebase. It does not establish years of Java employment, Spring application delivery, client references or production-scale reliability.

[Portfolio](https://work.matiushkin.com/en) · [GitHub](https://github.com/Hadezu)
