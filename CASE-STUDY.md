# Java asynchronous retry: make scheduled failures observable

Status: **IN_PROGRESS**. Independent open-source maintenance exercise by Ivan Matiushkin, using Codex; not client work or an upstream-approved fix.

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

Supports a bounded Java async/API integration diagnosis and fix, with a reproduction test and handover. Does not satisfy mandatory commercial years, client references, Spring expertise, distributed-systems ownership or production reliability requirements. Implementation, commands and observed results will be recorded after verification.
