# Wave 3: protected-state recovery and checkout lifetime

Checkpoint: implementation and local verification complete;
final independent review and protected acceptance pending, 2026-10-06.
This wave addresses SP-02, C-03 and C-06 (all MEDIUM). It does not select the
final v3 source, establish release readiness or claim a Play upload. Later
protected acceptance belongs in the remediation program's acceptance table.

## Input and shared ownership

Input is accepted Wave 2B main
`5da4f83e9637f8dcbc418bc844e526b66e3be7e2`, merged through
[PR #41](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/41).
Its exact-main [run 37408725779](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37408725779)
passed validate, API 30, API 23 and native-16k before this wave started.
Local, fetched and live main matched. Work uses the isolated
`codex/remediation-protected-state-checkout-lifetime` branch.

`:account` owns typed protected-session resolver results; `:storefront` owns
the neutral cart-restriction reason; `:mobile-core` owns private/cart recovery,
checkout state and actual destination actions; `:checkout` owns provider
callback finality and presentation disposal. Another enrolled merchant inherits
these contracts through the same modules. Application identity, provider
selection, protected-store formats, Room schema and production application
composition are unchanged. Three existing application debug evidence hosts receive
only the required shared action/event/failure contract adaptations. Synthetic remains
offline conformance; Trial remains development-only.

## Recovery invariants

- Catch the exact customer-session storage exception at the resolver/confirmed
  clear boundaries. Cancellation and programmer failures retain their contracts.
  Explicit leased identity does not reacquire the session coordinator.
- Failed read/write/clear receipts cannot authorize private state or certify
  durable sign-out. Storage failures have a dedicated category and bounded Retry.
  Loaded names, pages, addresses, drafts and notices are suppressed for that
  category; ordinary conflict, validation and pagination behavior is preserved.
- Address storage recovery retains the private immutable New/Edit target and
  rechecks authorization before enabling form mutations. A later connection
  failure cannot turn Edit recovery into a new local form.
- Customer-storage uncertainty produces a hidden typed cart restriction while
  preserving the exact durable handle. Ordinary ownership restrictions and
  verified anonymous cart-record persistence failures remain distinct.
- The pinned Checkout Kit recovery hook selects recovery; error taxonomy alone
  cannot establish finality. Delegate the original policy, capture the exact
  presentation/failure generation and settle through the main message queue.
  No matching selected hook means final failure, including exhaustion.
- Presentation disposal silences callbacks before disposing the SDK handle.
  Late/duplicate callbacks cannot throw or regain ownership. Synchronous terminal
  delivery before handle adoption remains buffered for the one consumer.
- Accepted completion precedes cleanup and refresh. Matching terminal state is
  nonbusy before a new private read; cancellation cannot reverse accepted truth.
  Unconfirmed cleanup retains the launched cart ID for Retry, preserving a
  replacement cart and performing no SDK relaunch or merchandise mutation.
- Cleanup Retry has its own actual destination action and remains available when
  later private storage fails. It can interrupt obsolete terminal follow-up work.
  Each attempt has a unique owner; an older terminal finalizer cannot release a
  newer Retry for the same captured cart. Matching attempt ownership is released
  even when lock acquisition is canceled.

## Permanent old-source execution

All 653 accepted implementation files were compared before RED. Only the
test manifest changed to register two private process services; production edits
before RED were zero. Immutable source/test inventory contained 667 files,
SHA-256 `656d563aef46d3414a4c779f19e26ec6236216d2e551ff9b3cb413ec356d1c02`.

Actual selected JVM execution: **230 cases, 185 intended assertion failures,
45 healthy passes, zero errors or skips**. The six maintained classes exercise
all ten Apollo gateway operations, terminal cleanup refusal, actual private
controllers/ViewModels, customer/cart coordination and the adapter/controller
completion protocol. Independent review classified every failure; other cached
XML files are excluded from these totals.

Actual API 36 execution: **12 cases, three intended checkout UI assertion
failures and nine healthy passes, with no setup failures**. The real
CartDestination Retry retained CLEANUP_REQUIRED twice; held terminal refresh
retained IN_PROGRESS once. Ordinary matching completion, six Android Keystore
commit-refusal/malformed-state controls and two fresh-reader process controls passed.
Immutable RED APK SHA-256:
`47EC5301C9DA2082B9E9A30E2BFC05D4BF2F6C81B4F052C956027182D0162CA4`.

The real stores distinguish refusal before apply from apply followed by a failed
receipt. Distinct reader processes share the test UID and verify encrypted
session/exact or pending cart ownership before checked clear, then a never-opened
reader verifies absence. This proves these controlled boundaries; it does not
prove writer process killing, OEM power-loss durability, distributed code2
upgrades or hosted payment behavior.

Two formatting failures and narrowly corrected test setup issues were retained
before execution. No setup/compile issue was counted as behavioral RED. The
independent review corrected token-wrapper expected identity, direct cancellation
throwing and omitted GraphQL variables without weakening assertions.

## Review-gap characterization

Independent review of the first implementation found two bounded gaps. A lost
add response followed by a fresh customer-store failure hid the cart correctly,
but ambiguity reconciliation overwrote SECURE_STORAGE with AMBIGUOUS_MUTATION.
An older checkout terminal finalizer reused the captured cart object as its
attempt owner and could release a newer live Retry for that same cart.

The first frozen candidate ran 119 account, 159 Storefront and 522 core JVM
cases. Core had one intended storage-category assertion failure; the other 521
passed. Storefront retained five existing live opt-in skips. Checkout test and
Android test compilation exposed two fixture/API setup problems; they were
preserved and corrected, with no behavioral RED claim for those failures.

With only those test corrections and the new cleanup overlap regression, actual
Checkout/core execution ran **543 cases: 541 passes, two intended assertions,
zero errors or skips**. Both failures occurred after connected guard assertions:
one sent mutation and hidden exact handle for the cart case; held actual terminal
read, joined old owner, and held actual checked Retry clear for the cleanup case.
Checkout's 20 cases passed. Independent review verified both failures and the
unchanged production-source inventory before the narrow corrections.

## Local GREEN and remaining protected acceptance

The corrected frozen candidate passed the affected JVM inventory: **821 cases,
816 passes, five existing live opt-in skips, zero failures or errors**. Checkout's
20 and core's 523 cases executed fresh; account's 119 and Storefront's 159 remained
up-to-date from the first focused run with unchanged relevant inputs. The nine
selected classes contain 252 passing cases with no skips; 203 ran fresh and 49
account cases retain that unchanged-input provenance. Both additional review-gap
regressions are unchanged from their executed RED and now pass. Spotless, all four
affected detekt tasks and all four shared test APK builds passed.

Actual API 36 execution passed **19 Wave 3 controls and 67 affected screen cases**,
including the shared storage-recovery case: 86 unique cases with no errors,
skips or setup failures. These include the real
SDK assembly and main-message-queue controls, actual ViewModel-store disposal,
destination cleanup Retry, private-store/process controls and private/cart/product
screens. Both runs used immutable APK SHA-256
`09C611C632A8CC9365A703F1DB8C4A14B7FAF3AF45D738742E2889D9B0B2195F`.
Independent review verified raw results and source/APK binding. This does not
claim a live SDK dialog, hosted payment, writer kill or distributed upgrade.

The first broader lane stopped at a missing SecureStorage branch in an existing
application debug proof controller. This is a contract-compilation issue, not
behavioral RED. A concurrently run onboarding self-test temporarily changed the
real registry/projections and restored every implementation byte; that broad lane
is supporting evidence only. The final broader lane must run with those inputs
held stable and fresh configuration-dependent generation and R8 execution.

That stable repeat reached the corrected debug variants, then the Windows
Temurin JVM crashed in its VM thread during allocation garbage collection.
The native crash, daemon log, partial results and unchanged source inventory
are preserved. No assertion failure or out-of-memory cause was established.
The next repeat keeps the pinned wrapper/JDK and full checks while limiting
workers/processor count and JIT tier through ignored local runner options;
success must not be described as a diagnosed or permanently fixed JVM crash.

The stable conservative repeat passed in **25m 25s**: 1,330 tasks, 172 executed,
six from cache and 1,152 up-to-date. Its task-scoped JVM inventory contains
**986 cases, 981 passes, five existing live opt-in skips and zero failures/errors**.
Production-debug's 43 and Trial's nine cases executed fresh in this repeat;
the other 934 retain matching-input up-to-date provenance, including the 86
development/staging cases executed in the preceding stable attempt. Spotless,
all nine detekt tasks, four affected Lint tasks (zero issues), the eleven JVM
tasks, seventeen application/core builds and four shared test APK builds passed.
Three projection validators, fifteen BuildConfig tasks and all five application
R8 tasks were forced to execute fresh with registry/projection inputs held stable.
The implementation inventory stayed at 674 files with SHA-256
`6f0083ea5271f83c910ad74504901c91558f27a3b1145d21c5c8a388156ca2c3`.
The conservative runner is an observed successful local lane, not a diagnosis
or permanent repair of the preceding native JVM crash.

Synthetic debug/release package validation passed all **63 checks**, including
offline composition and physical Firebase exclusion. Trial debug/release passed
all **25 checks**. Repository portability passed all **46 dirty-worktree checks**;
normal public-readiness inspection passed all **23 dirty-worktree checks**.
The separately retained onboarding self-test passed all **255 checks** and
registry validation passed with the intentional UNCONFIGURED default projections.
Its inputs remain unchanged by the later three debug-host adaptations.
Clean-worktree variants follow the documentation-bearing commit.

Final independent review and normal protected PR/main CI remain required. No real
customer/provider/order/payment, Play or signing operation occurred in this wave.
