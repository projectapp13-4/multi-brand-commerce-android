# Wave 2B: cancellation and Product feedback ownership

Checkpoint: local verification complete; protected acceptance pending, 2026-10-06.
This document covers C-05 (MEDIUM) and C-04 (LOW). It does not select the final
v3 source, establish release readiness or claim a Play upload. The program's
protected acceptance table records later merges without rewriting this checkpoint.

## Input and shared ownership

Input is accepted Wave 2A main
`1f814e0abc5993401bd4b6f92f53b773dd764893`, merged through
[PR #40](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/40).
Its exact-main [run 37399603158](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37399603158)
passed validate, API 30, API 23 and native-16k before this wave started.
Local, fetched and live main matched; implementation uses the isolated
`codex/remediation-mutation-lifecycle` branch.

`:mobile-core` owns both repairs. Repository progress belongs to the operation
holding its existing mutex; Product feedback belongs to the initiating immutable
purchase intent and selection revision. Another enrolled merchant inherits these
rules by composing the same core. No application/package/domain/merchant branch,
duplicated application implementation, provider change, dependency upgrade or
persistent-format change is needed. Synthetic remains offline conformance and
Trial remains development-only; their package projections are verified separately.

## Invariants and implementation

- Cancellation releases only its owner's presentation progress before the
  repository mutex is released. A canceled waiter cannot clear another action.
- Cleanup rethrows the same cancellation exception and performs no remote call,
  persistence clear, replay or detached work. It preserves the protected handle.
- An anonymous snapshot freshly verified by this operation can show retryable uncertainty.
  Associated or unconfirmed ownership hides private cart content until leased
  restore verifies it. Existing restricted recovery retains one retry action.
- Explicit Cart Retry rereads provider truth. Cancellation certifies neither the
  mutation's success nor its absence. The existing inline retry has a test tag;
  its behavior and localized text are preserved.
- Product claims a submission before launching, captures the full intent and
  invalidates its attribution on effective selection changes or reload. Returning
  A to B to A cannot revive an old outcome. Completed, Adjusted, Failed and
  Restricted all use the same attribution check.
- The owned finalizer releases Product progress independently of whether feedback
  still belongs to the current selection. Reload retains the pending indicator;
  ordinary option selection remains available and rapid taps coalesce.

Product still submits quantity 1; this wave adds no quantity selector. Wave 2A's
direct/ambiguous postconditions, typed adjustments, truthful provider state and
one-shot mutation transport remain the mutation authority. Wave 1's exact
customer lease, retained-session guard and protected identity formats remain intact.

## Permanent old-behavior regressions

The original and independent audit diagnostics remain preserved under ignored
`out/audit`. Maintained tests now exercise actual production ownership:

- `CartCancellationOwnershipTest`: canceled mutation/read/discard, restricted and
  customer presentation, queued-caller and successor ownership, plus exact
  cancellation propagation and canceled reconciliation controls.
- `CartCancellationConnectedTest`: production Apollo factory, generated cart
  parser, customer lease, coordinator, protected store and real Product/retained
  Cart ViewModels. Complete held Add is sent once; clearing the actual Product
  store preserves the handle and explicit Cart Retry observes provider quantity.
- `ProductCartAttributionTest`: all typed outcomes, A to B to A, settled feedback,
  rapid taps, reload, cancellation and healthy same-selection/media controls;
  joined repository controls preserve completed and adjusted cart truth.
- `CartCancellationRecoveryTest` and `ProductCartAttributionScreenTest`: actual
  ViewModels, separate owner stores, real repository and production screens.
  Rendered retry recovers the retained Cart; stale localized Product confirmation
  and adjustment must not attach to B or revive after A to B to A.
- The inherited `CartMutationOutcomeTest` cancellation characterization now
  requires released progress and explicit uncertainty, preserving its one-Add,
  protected-handle and bounded reread controls.

Before fixes, all 645 existing implementation files matched accepted Wave 2A
byte-for-byte. First JVM RED: 43 cases, 23 assertion failures, 20 passes, no errors
or skips. One assertion was an authenticated fixture setup problem: its warm
restore omitted the required buyer-identity response. It does not count as defect
RED; the other 22 failures reached behavioral assertions. Raw evidence is retained.

After correcting that fixture and two later assertion boundaries, the unchanged
production Cart rerun had 9 cases, 7 intended failures and 2 healthy passes, no
errors or skips. Actual API 36 old-source screens had 8 cases: 5 intended failures
and 3 healthy passes. Both canceled Cart cases retained a spinner; three Product
cases displayed obsolete Completed/Adjusted feedback. The immutable RED APK SHA-256
is `9D4E7673E52E7F56E3EA68F4265F77BE4D7A538E8C5CD6FF20B24C110A2DCB71`.
No Back harness modification, skipped test or weakened assertion was used.

## Current verification boundary

Focused GREEN 2 passed: 569 affected JVM cases, 564 passes, five existing live
opt-in skips and no failures or errors. Fresh core execution has 333 cases,
including ownership 9, connected 5, attribution 20, mutation outcomes 28 and the
inherited exact-customer ownership 25. Unchanged account/checkout/storefront
suites retain their earlier valid timestamps and UP-TO-DATE task receipts;
they are not represented as newly executed.

Spotless, core detekt and the rebuilt Android test APK passed. The full API 36
Cart/Product run passed 42/42 in 111.151 seconds: 34 inherited controls and eight
new cancellation/attribution cases. The immutable APK SHA-256 is
`B50D1557F70293A4383548CA2D9E50CA1B889729D52BD891FB8F8410595FD137`.
The original system Back/focus test passed unchanged.

Broader validation passed in 13 minutes 25 seconds: all static checks, four Lint
lanes, eleven JVM tasks, seventeen registered shared/application builds and four
shared Android test APKs, including all five R8-minified application variants.
The complete archived JVM inventory is 734 cases, 729 passes, five existing live
opt-in skips, no failures or errors. Its task receipt distinguishes 169 executed,
four cached and 1,157 UP-TO-DATE tasks from the 1,330-task graph.

Packaged Synthetic checks passed 63/63, Trial 25/25, onboarding All 255/255,
registry/projection Validate passed with missing ignored configuration explicitly
UNCONFIGURED, portability passed 46/46 and precommit public readiness 23/23.
Synthetic remains physically Firebase-free/offline; Trial remains development-only.

The frozen 653-file implementation inventory SHA-256 is
`e182228a4130c029b87fa6fb5eea888eaed0e250674ee5afe1ee1efead51f7a4`.
Independent review matched every source file and the rebuilt APK, recounted the
focused/broader XML and all 42 terminal device results, reviewed the projections
and handoff, and found no further actionable issue. Normal protected PR/main
acceptance remains pending at this checkpoint. Failed attempts and fixture
corrections are retained; they are not represented as passing verification.

Independent review identified an initial-fix gap before acceptance: a previously
anonymous cart could become pending customer association inside a leased restore
before the repository received a resolution. Cancellation must not republish that
old anonymous ownership. Connected pending-rebind regressions and typed
per-operation observation corrected this before acceptance. Every new
reconciliation read invalidates the prior observation until its own fresh result;
cleanup must also preserve a newly observed provider snapshot rather than rewind
to an older quantity.

Three additional connected cases reproduced this first-fix gap: pending buyer
rebind and a fallback read with a newly acquired customer lease both remained
ACTIVE instead of hiding unresolved ownership; canceled prepared checkout
restored quantity 2 instead of retaining the freshly observed quantity 3.
The actual five-case run had exactly these three assertion failures, two healthy
passes and no errors or skips. Its raw XML and source inventory are preserved.

The first corrected run passed all five connected cases, including the three
review gaps. Its 569-case affected-module inventory contained one failure, no
errors and five existing live opt-in skips. The failure was an exception-identity
fixture: completing a Deferred exceptionally introduced the pinned coroutine
library's [stacktrace-recovery copy](https://github.com/Kotlin/kotlinx.coroutines/blob/1.11.0/kotlinx-coroutines-core/jvm/src/internal/StackTraceRecovery.kt).
The fixture now throws directly at the actual CartOperations boundary while
retaining the exact-object assertion. Four static-analysis readability and file
name findings were corrected with equivalent guard simplification and a fixture rename;
no suppression, baseline or threshold change is used. This failed run remains
preserved and was superseded by the passing second run.

Controlled loopback and inert SDK/provider boundaries contain synthetic data only.
These tests establish client ownership and rendered recovery, not live Shopify
commit incidence, hosted authentication/payment behavior, or a distributed code 2
installed upgrade. Protected storage and checkout lifetime remain Wave 3 findings;
accessibility and wishlist remain later waves. Final reassessment of all thirteen
findings must use the eventual accepted final main.
