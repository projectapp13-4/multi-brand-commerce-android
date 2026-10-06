# Shared commerce remediation program

Status: implementation in progress, 2026-10-06 Europe/Istanbul. The owner request explicitly authorizes implementation, permanent regression tests, normal protected PR merges and final v3 artifact preparation signed with the replacement upload key. It excludes Play uploads, real customer or commerce mutations, provider production changes, key/reset changes and version 4. The two dated audits remain historical evidence under ignored `out/audit`; this plan does not establish defect closure or release acceptance.

## Purpose and global invariants

Correct the 13 verified client defects in shared ownership boundaries so another enrolled merchant inherits the same behavior without editing shared business logic. Keep the native application-module topology, provider-neutral core, Synthetic offline/nonproduction and Trial development-only. Provider-returned commerce state remains authoritative; successful envelopes, achieved intent, exact identity and callback finality are distinct contracts.

Start source is protected main `1f3e3c9f532731ff637da98756c78886b623adca`, freshly matched to local, fetched and live main on this task. The active ruleset requires strict `validate`, `instrumentation` and `minimum-sdk-instrumentation`, normal merge commits and resolved review threads, with no bypass actors. Existing exact-main run `37245807156` passed; this is baseline evidence, not repair proof. Initial affected account/checkout/storefront/core unit suites passed before tracked edits.

For each wave: latest protected main → isolated branch → permanent behavioral regression/meaningful old-source RED → minimal implementation → focused and affected-suite GREEN → appropriate static/Lint/Android/release checks → independent review → focused PR → exact-head required CI → normal merge → exact merged-main CI. Do not stack dependent waves on an unmerged base. Root coordinates builds/merges and durable receipts; independent agents may design/review orthogonal boundaries concurrently without changing dependent source.

Test-only controlled external boundaries may use synthetic IDs/stores/HTTP/SDK callbacks. Real coordinators/repositories/ViewModels/parser/transport/Compose are exercised when material. No real customer data, orders or payments. Passing characterizations of bad behavior do not close findings. Do not add production test switches or weaken old healthy controls to obtain green output.

## Protected wave acceptance

| Wave | Protected PR / merged main | Exact merged-main verification | Status |
|---|---|---|---|
| 1 — SP-01 | [PR #39](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/39), `12ead74b7045147ad237ef46b7ae2a71f9a7ad54` | [Run 37389377378](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37389377378): validate, API 30, API 23 and native-16k passed | Accepted for the wave; final-source reassessment remains required. |
| 2A — C-07/C-01/C-02 | [PR #40](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/40), `1f814e0abc5993401bd4b6f92f53b773dd764893` | [Run 37399603158](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37399603158): validate, API 30, API 23 and native-16k passed | Accepted for the wave; final-source reassessment remains required. |
| 2B — C-05/C-04 | [PR #41](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/41), `5da4f83e9637f8dcbc418bc844e526b66e3be7e2` | [Run 37408725779](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37408725779): validate, API 30, API 23 and native-16k passed | Accepted for the wave; final-source reassessment remains required. |
| 3 — SP-02/C-03/C-06 | [PR #42](https://github.com/projectapp13-4/multi-brand-commerce-android/pull/42), `e6c1bdf335775be076ec590329c79fa385238672` | [Run 37426884953](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/37426884953): validate, API 30, API 23 and native-16k passed | Accepted for the wave; final-source reassessment remains required. The initial PR test-target setup failure and preserved two-class correction remain historical evidence. |
| 4 — P-A1/P-A2 | Pending protected PR on accepted Wave 3 main `e6c1bdf335775be076ec590329c79fa385238672` | Pending | Shared implementation and local verification complete: fresh core 531, current accessibility 65 and affected Android 15 pass. Broader suites, static/minified/projection checks and bounded actual TalkBack review pass with execution and observation limits recorded in the Wave 4 handoff. Exact-head and merged-main protected acceptance remain required. |
| 5 — P-A3/P-A4 | Pending accepted Wave 4 main | Pending | Not implemented. |

The Wave 1, Wave 2A, Wave 2B and Wave 3 handoffs preserve their pre-merge checkpoints. This table records later protected acceptance without rewriting that historical evidence. Wave acceptance does not select the final v3 source or authorize an upload.

## Wave 1: exact identity isolation — SP-01

Owners: `:account` session lease/replacement truth, `:storefront` exact buyer mapping and cart ownership protocol, `:mobile-core` session/cart bridge and Account presentation. Determine current customer identity from a supported authenticated provider contract, before assigning/verifying an existing associated cart. Preserve exact returned customer identity; a Boolean never proves same-customer ownership. Persistent ownership format changes must retain supported old payloads and quarantine unverified association without discarding customer work.

Permanent checks: connected retained-A identity failure and failed logout before/after clear receipt → valid B exchange; no B mutation/checkout of A cart; same-A refresh/continuity; anonymous attach; unresolved/rejected/ambiguous buyer transition; restart of old/new protected ownership; session/logout ordering against an in-flight mutation. Gate new sign-in/replacement while prior durable removal remains unresolved. Keep retry/logout available independently of display-name presence.

## Wave 2A: transport and action outcome authority — C-07/C-01/C-02

Owners: `:storefront` actual GraphQL HTTP engine/parser taxonomy; `:mobile-core` mutation reconciliation. Prevent repeat submission of uncertain sent mutation bodies, including transport recovery and HTTP follow-ups, while preserving safe read recovery. Verify the exact pinned Apollo/OkHttp contract; no dependency upgrade merely to change behavior.

Preserve merchandise/input user errors distinctly from proven whole-cart missing/expired/completed state. Preserve/refetch valid cart and unrelated lines. Empty/Expired is not action completion. Enforce create/add/update/remove intent postconditions on direct success as well as ambiguity rereads. Return honest exact/corrected/rejected outcomes without treating every warning as failure or fabricating provider state.

Permanent checks: warmed lost-response and HTTP 408/503 follow-up mutation request counts; safe read retry; actual generated parser error with nonnull cart data and warnings; create/add/update/remove unchanged/corrected/exact results; achieved intent with a warning succeeds; true missing/expiry/completion and expiry-between-restore/mutation controls. No live duplicate commit is required.

## Wave 2B: mutation owner and feedback — C-05/C-04

Owner: `:mobile-core` repository progress and Product action attribution. Cancellation releases stale presentation, preserves durable cart and exposes truthful recoverable ambiguity without claiming success/replaying. Retained Cart regains controls and recovery in place. Product outcome belongs to the initiating variant/action, and changed selection clears obsolete feedback.

Permanent checks: actual Product ViewModel owner cleared while actual Cart owner remains; rendered Cart progress/control/retry state; refresh recovery; A add pending → select B with success/failure/cancel and rapid actions; the submitted variant remains A. Coordinate with Wave 2A's outcome contract rather than duplicating mutation semantics.

## Wave 3: protected state and checkout lifetime — SP-02/C-03/C-06

Owners: `:account` typed secure faults through private resolver/gateways, `:mobile-core` private/cart/checkout recovery, `:checkout` SDK event/finality/disposal contract. Uncertain durable storage cannot authorize private/cart presentation or claim logout success. Preserve cancellation and clear busy ownership with bounded retry. Known checkout completion and exact launched-cart cleanup remain facts even if later refresh fails.

Treat callback cancellation and recoverable failure as one lifetime protocol. Disposed owners reject late callbacks safely and cannot regain ownership. Preserve the exact pinned Checkout Kit 3.5.4 recovery/finality through fallback, retry exhaustion/fatal failure and cancellation; do not make all Failed terminal or all failures nonterminal. Completion processes once for the launched session/cart.

Permanent checks: real secure-store read/write/clear/malformed/refused-cleanup/restart uncertainty; private stale state suppression; checkout prepare retry and completion followed by secure refresh failure; real adapter flow/controller cancellation plus late/duplicate/wrong-owner callbacks; actual SDK recoverable exception/default policy → application callback flow → recovered completion; fatal/exhausted/canceled recovery controls. Add Android/minified lifetime checks that can be safely exercised without orders/payments.

## Wave 4: accessibility recovery and purpose — P-A1/P-A2

Owner: `:mobile-core` shared Address form and Search History settings. Scroll first invalid lazy field into composition, await attachment/layout and focus before consuming recovery; preserve visible/accessible error. Provide localized purpose/role/state/action coherently for Make Default and Search History, without duplicate accessibility actions.

Permanent Compose checks: original offscreen font-scale 1/2 RED fixtures become maintained healthy regressions; visible/offscreen/IME validation recovery; merged purpose/role/state/action enabled/disabled and focus order. Actual TalkBack verification where practical; semantics is not spoken output. No general UI redesign.

## Wave 5: wishlist local management and freshness — P-A3/P-A4

Owner: `:mobile-core` actual membership Flow/repository/ViewModel/destination. Remote hydration runs outside the local membership lock. Generation/order/partition/coalescing/cancel guarantees prevent late-result resurrection. Maintain bounded concurrency. Ordinary visible entry deliberately refreshes successful cached products, without a made-up TTL or recomposition-driven overfetch; local management remains usable while rehydration continues.

Permanent checks: actual membership Flow + repository + ViewModel clear/remove immediately while remote work waits, then late release; stable order/four-request bound/partitions; canceled partial cache and repeated refresh generation; successful cached snapshot→provider change/missing→ordinary reentry updates status; coalesced entry and explicit recovery. Product Detail/cart truth remains independently live.

## Review focus and shared interfaces

| Coupled boundary | Required review / regression |
|---|---|
| Wave1 session lease → Wave2 mutations → Wave3 secure faults | No non-reentrant lock cycle, stale customer lease, fallback to anonymous on uncertainty or authorization gained through refresh. Coupled failed-logout/restart/mutation control. |
| Wave1 exact ownership → persistent payload compatibility | Current token/provider identity domain must be proved, not assumed from OIDC subject. Old payloads readable/quarantined; exact ID redacted and encrypted. |
| Wave2A HTTP recovery → mutation reread/postconditions → Wave2B cancellation | Exactly one uncertain sent mutation; safe reads still recover. Cancellation neither commits success nor destroys/replays customer work. |
| Wave3 SDK callback finality → adapter disposal → controller cleanup | Pinned SDK retry exhaustion/cancel/fallback-completion fact determines finality. Lost consumer cannot throw; known completion survives later refresh failure. |
| Wave5 mutex release → membership Flow/generation → visible-entry freshness | Immediate remove/clear, no late resurrection or out-of-order publish, bounded/coalesced reads and no arbitrary TTL. |

Each wave's tests and production change share the same owning modules above. Accessibility is independent of identity contracts but follows protected merges. Final integration includes both anonymous/Synthetic and enabled-account application projections; no concrete brand/package/domain conditional is acceptable in shared behavior. Design rulings and exact test/red/green/CI receipts are maintained under the ignored task ledger and sanitized wave handoffs.

## Final verification and release handoff

After all six merges, establish identical clean local, fetched and live final main. Run all 13 permanent regressions plus the full registered JVM/static/Lint/build lanes, required API 30/API 23 and established native-16k CI, release/minified checks, topology/projections and appropriate emulator/physical tests. Record each PR/merge/run and all meaningful skips. Reassess all 13 on exact final source; no finding closes solely from inspection or adjacent CI.

Actual owned/synthetic hosted auth/cart/checkout and TalkBack checks run where safely possible; OTP/security challenges remain human-only. If the actual old distributed code 2 artifact with a supported upgrade signature remains unavailable, installed-upgrade proof stays UNKNOWN. No recreated debug/reference binary substitutes for it.

Keep package `com.gurbakir.mobile`, versionCode 3, versionName 0.3.0, minSdk 23 and targetSdk 36. Only after source acceptance, confirm the replacement upload certificate read-only and build a fresh production AAB through the existing private helper. Preserve secured key/password custody; verify replacement upload certificate (not Play App Signing identity), package/version/minification/native/bundletool/no test leakage and record secure path/size/hash/source/toolchain/certificate fingerprints. Do not reuse the old AAB, create a key, alter the reset, upload or change Play tracks/testers. Owner schedule: 2026-10-06 22:35 UTC = 2026-10-07 01:35 Istanbul; activation/readback is distinct from source readiness.

Final sanitized handoff uses exactly READY FOR CLOSED ALPHA UPLOAD TASK or NOT READY with specific blockers; it includes the 13-row closure matrix, Git/PR/CI, multi-brand projection, AAB receipt, genuine remaining UNKNOWNs and prohibited-action counts. App Links ticket 70706239/association and public P3-16 remain separate. Do not invent a tag convention. No final candidate is attributed to an unmerged branch.
