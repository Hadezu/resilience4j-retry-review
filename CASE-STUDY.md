# Java asynchronous retry: make scheduled failures observable

Status: **VERIFIED_CI_PUBLIC** — [CI run 37363332252](https://github.com/Hadezu/resilience4j-retry-review/actions/runs/37363332252), code commit `89d57091e52216fdcc7ef34ebae7c401ea34e30b`. Independent open-source maintenance exercise by Ivan Matiushkin, using Codex; not client work or an upstream-approved fix.

## Upstream and scope

- [Resilience4j](https://github.com/resilience4j/resilience4j), Apache-2.0; original license and attribution retained.
- Baseline: `7e3ab5252ed380b596e25240f19376a4435570b8`.
- Java 21 / Gradle / JUnit / Mockito. This is a mature fault-tolerance library, **not a Spring Boot application**. No Spring deployment experience is claimed.
- Existing path: `Retry.executeCompletionStage` -> `decorateCompletionStage` -> `AsyncRetryBlock.run` -> completion callback -> scheduled retry.

## Problem to reproduce

An asynchronous attempt can fail and schedule another attempt. If that later invocation throws synchronously before returning its CompletionStage, the scheduler captures the exception but the caller's original future can remain incomplete. The same risk exists for retries triggered by a rejected result. A caller can then wait indefinitely even though the underlying operation has stopped.

The upstream test explicitly expects the *initial* synchronous supplier exception to propagate. Preserve that behavior. For a scheduled invocation, make the already-returned future complete exceptionally with the original failure; do not assume another retry is safe.

## Acceptance cases

- A scheduled supplier exception after an exceptional stage terminates the caller future.
- The same holds after a result-based retry and for a scheduled Error.
- Initial synchronous exceptions still propagate as upstream specifies.
- Normal asynchronous failure/recovery and retry exhaustion remain unchanged.
- Scheduler rejection is observable; no forever-pending result.
- Tests use controlled task execution, not timing-sensitive sleeps.

## Non-goals

No new retry policy, cancellation semantics, idempotency guarantee, HTTP service, database, production deployment, upstream issue/PR or paid-client claim. No claim that completion proves an external write did not happen. A buyer's side-effect and retry policy must be reviewed separately.

## Buyer relevance

Supports a bounded Java async/API integration diagnosis and fix, with a reproduction test and handover. Does not satisfy mandatory commercial years, client references, Spring expertise, distributed-systems ownership or production reliability requirements.

## Implementation and review

`Retry.java`: both retry scheduling branches now use a small shared `scheduleRetry` helper. It catches failures escaping a scheduled invocation and completes the original caller promise exceptionally. The initial invocation still calls `run()` directly, preserving upstream's tested immediate-throw behavior. Existing exceptions delivered *through* a CompletionStage still follow upstream's retry policy.

`ScheduledSupplierFailureTest.java`: seven deterministic tests use a controlled executor. It models the real executor's capture of an escaped exception into its own task future, independently from the future returned to the caller. Three regressions fail before the patch; four compatibility/control cases pass before and after. No wall-clock sleeps or external dependencies are involved in the test scenarios.

`.github/workflows/proof.yml`: validates the pinned baseline, the exact red regression cases and patched core/retry tests/assembly. The workflow cannot deploy or write repository contents. README and this case study provide the buyer entry point; the dated verification note distinguishes native and secondary-runner results.

The behavior change is **14 added / 2 replaced lines** in one production file, plus a modification notice in its existing license header. No dependency or public API change. Original source files, upstream README, license and copyright remain in place. The upstream build/publish workflow is restricted to its original repository; this proof uses only its scoped, read-only verification workflow.

## Reproduce

With JDK 21:

```sh
bash gradlew :resilience4j-retry:test --tests '*ScheduledSupplierFailureTest' --no-daemon
bash gradlew :resilience4j-core:test :resilience4j-retry:test :resilience4j-retry:assemble --no-daemon
git diff 7e3ab5252ed380b596e25240f19376a4435570b8 -- resilience4j-retry/src/main/java/io/github/resilience4j/retry/Retry.java
```

The CI job creates an unchanged baseline worktree, tests it, then copies only the new test into it and requires exactly the three pending-future failures. See [verification](docs/verification-20261005.md) for actual results and environment limits.

Observed native Gradle results: baseline core 250 + retry 137 = **387 passing tests**; patched core 250 + retry 144 = **394 passing tests**, no failures/errors/skips, and retry assembly passed. The seven-case local secondary runner also passes. Other upstream modules were not included in this claim.

## Proof matrix

| Buyer need | Exact proof | Safe proof line | Supports | Does not prove | First paid task | Verify next |
|---|---|---|---|---|---|---|
| Diagnose a Java async integration that never finishes after a retry | [Production diff](https://github.com/Hadezu/resilience4j-retry-review/compare/7e3ab5252ed380b596e25240f19376a4435570b8...623a90ee53212ffffbae5f907c1cce9c2c6fd827), regression tests and scoped CI | In an independent Resilience4j maintenance exercise, I reproduced a scheduled-retry failure that left callers waiting and added a small fix with regression tests. | Evidence of tracing and changing this Java async path with tests; a preferred practical sample requirement | Commercial Java years, client work, Spring delivery, security ownership, upstream approval or production reliability | Reproduce and fix one agreed async/API error path, with tests and handover | Client JDK/library version, exception contract, side effects, idempotency, cancellation/timeouts, metrics and acceptance cases |

Use this proof only for a matching request. It is not generic evidence for every Java/Spring role. Codex assisted the work; the inspectable change and tests are the evidence, not a claim of unassisted breadth. No years of commercial experience are inferred.

## Deliberate limitations

- Initial synchronous exceptions still escape immediately; scheduled synchronous exceptions complete the returned future exceptionally. This preserves the initial upstream contract rather than redesigning the API.
- No extra retry or rollback is attempted after a scheduled synchronous failure. Its external side effects may be unknown.
- The patch does not introduce new retry metrics/events for this terminal synchronous boundary. Monitoring semantics require separate scope if needed.
- Cancellation and races with in-flight external operations, Spring configuration, other adapters and the full multi-module suite are outside this case.
