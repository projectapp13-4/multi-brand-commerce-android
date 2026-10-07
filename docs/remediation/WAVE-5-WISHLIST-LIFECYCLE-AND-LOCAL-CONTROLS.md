# Wave 5: Wishlist lifetime and local controls

Checkpoint: reviewed local implementation and evidence, 2026-10-07. This addresses existing findings P-A3 and P-A4 on accepted Wave 4 main `6162bc8e498119be59d1772a7df0e23d9b23a298`. Protected Wave 5 head/main acceptance and the final v3 source remain pending. The program table records subsequent protected acceptance; this document preserves the evidence at its checkpoint.

## Resulting shared behavior

Product hydration no longer holds the local membership lock. Room membership stays observable, removable and clearable while provider reads wait. A separate hydration lock serializes rounds; a repository-wide four-permit boundary remains held until each physical read exits, including a callback that ignores cancellation. Exact product identity, ordered membership, timestamp, generation and market checks reject obsolete results. Pending rows contain local identity and actions without presenting cached commerce state as current truth.

The destination owns fresh product rounds through actual visible intervals. Constructor bootstrap is adopted by the first resume; later resumes start fresh work, while hidden membership updates remain local. Presentation ownership and cancellation reject obsolete publication. Paired lifecycle/disposal handling also releases a destination disposed before its first resume. Local storage failures preserve truthful state and retry rather than claiming removal or clear succeeded.

Pending, unavailable and configuration-error rows retain localized Remove/Clear controls. Clear remains a native confirmation with distinct Cancel and Confirm outcomes. The list viewport applies Scaffold padding structurally, so scrolling a control into view cannot place it beneath the title bar. Brand spacing is applied separately and once.

These changes live in `:mobile-core`. Room version 2, migration and protected application/storage identities remain unchanged. Independent Product Detail/cart/provider truth remains authoritative. No concrete brand branch, runtime merchant switch, production fixture or provider-selection change is introduced; Synthetic remains offline and Trial development-only.

## Permanent behavioral checks

Seven new JVM classes contain 23 checks for external identity, physical hydration concurrency, stale-round rejection, lifecycle hook ownership, local responsiveness, presentation ownership and successful reentry. The affected inherited group contains 34 checks, including Account Deletion controller/ViewModel, Product Detail, Wishlist product actions, repository and ViewModel.

Seven new Android classes contain 27 cases for destination freshness/lifetime, local controls, rendered EN/TR composition at font scale 2, pre-resume disposal and actual Room hydration/observation. The affected inherited group contains 25 navigation, Account Deletion, Room/migration and Wishlist cases. Rendered observation is a separate eight-case invocation with passive geometry/PNG capture enabled only by explicit instrumentation arguments. Its timing does not replace ordinary native execution.

## Current local evidence

The current complete implementation vector contains 709 files and hashes to `38b482bb5731d6284d21d4b32c591fc5f5f6c9e2cdb4cee807444952c58987c4`. Source before formatting, after formatting before JVM execution and after the focused run matched exactly.

The fresh focused run passed formatting, core Detekt/debug Lint, unfiltered core JVM execution and AndroidTest assembly. Seventy XML suites contain 567 passes with zero failures, errors or skips: 562 distinct class/display pairs and five existing duplicate display pairs, each represented twice. Exact new 23 and inherited 34 identities passed. Compilation reuse is recorded separately from forced JVM execution. Nine static reports agree on zero findings.

The freshly assembled self-targeted core test APK hashes to `b3fd1cf0ba40d3eee70a74f2f033a92530087ba9eb85b6f4b8b1aec8bcf94371` and contains 26,755,208 bytes. The ordinary API 30 invocation passed exactly 27 new plus 25 inherited cases, with zero failures/skips. Root external source/device guards bracketed the legacy transport wrapper; the actual installed APK digest was captured after this invocation, before the next install. A before-normal installed digest was not captured and is not claimed.

The separate guarded API 30 observation passed all eight cases. Its installed self-target/hash, complete source vector, owned process epoch and six device setting values/presences were checked. All 20 checkpoint pairs were retained, comprising 16 required and four diagnostic pairs; all 40 files passed independent integrity checks and all 20 PNGs were inspected firsthand. EN/TR Clear lies fully below the title bar; the original tap opens the dialog. Targeted configuration/removed/pending/retry copy and local controls are visible. Some immediate captures contain transition translucency, and unrelated content can be scrolled/clipped. Geometry and pixels were acquired separately. No dispatch-receiver trace, TalkBack speech, production Activity shell, provider-runtime or atomic timing proof follows.

The ordinary API 23 invocation also passed exactly 27 new plus 25 inherited cases, with zero failures/skips. Its guarded runner checked the complete source vector, owned emulator process epoch, six setting values/presences and self-targeted manifest. Whole installed APK bytes were captured before and after execution and matched the fresh archived APK. The original receipt contains an empty emitted object plus one actual capture object for each installed readback; the unique actual objects and four complete APK copies were independently verified without rewriting the receipt. API 23 lacks device `sha256sum`, so these are read-only binary copies hashed on the host.

Projection-only validation and registry/configuration regressions passed (81/81 and 19/19). All five registry/projection inputs and the complete implementation vector remained unchanged. Independent review found no blocking source issue.

The proportional broad run completed successfully in 30m 47s with 1,330 actionable tasks: 250 executed, eight from cache and 1,072 up-to-date. All eleven registered JVM scopes contributed 166 retained XML suites containing 1,030 elements: 1,025 passes and five existing opt-in Storefront skips. Five scopes freshly executed 143 elements; six reused 887, including the separately fresh focused core result above. The exact new 23 and inherited 34 core identities and all five existing duplicate display pairs remained intact. This is proportional verification, not the required final fresh all-eleven lane.

The run requested 21 assemblies, nine Detekt scopes and four affected debug Lint scopes. Static analysis/report tasks reused matching inputs and report zero findings; aggregate Lint executed. Three projection validators, 15 BuildConfig tasks and five R8 tasks were forced and executed. Configuration comparison retains 45 unchanged inputs and the historical core managed-device image change from `aosp-atd` to `aosp`; the Wave 5 AndroidTest manifest source rebind is separate. Public-readiness and portability checks also passed (23/23 and 46 checks).

All 12 selected shipping APK/AARs and five R8 mappings passed source/compiled-fixture isolation checks. The core AndroidTest positive control contains 52 catalogued fixture/support definitions, including the required subset of 34; extra definitions are not additional executed cases. Account and Storefront have four and six owned compiled positives. Checkout has no owned AndroidTest Kotlin fixture and its generated APK supplies integrity/support evidence only. The selected archive, metadata, test-control and static payloads were retained as 104 exact copies and independently reviewed against originals, source brackets and tool/receipt pins. Root accepted this scoped proportional evidence without promoting it to protected CI or final acceptance.

A redacted scan of the complete nonignored current-source snapshot covered 823 files, with zero findings and unchanged inputs. Ignored private/provider/build files, Git history and compiled artifacts are outside that scan. Protected head/main CI remains pending. Fresh all-eleven JVM/all-nine Lint integration, all-thirteen final-source reassessment and the existing-key-signed v3 artifact remain separate program obligations.

## Preserved earlier evidence

Original compiled regressions produced 13 audited-target failures, six selected-guard failures and ten healthy passes; selected-guard failures are not additional findings. Green1 passed JVM checks but failed static analysis. Green2 passed scoped static/JVM checks; its first native sequence still had three failures. A pre-resume failure was test-owned destroyed-entry teardown, corrected by retaining the actual entry store while alive. The two localized Cancel failures exposed Clear scrolling beneath the title bar; unchanged actions/assertions passed after the structural viewport fix.

The older retained lifetime APK differs from its declared whole-file digest by one unindexed padding byte. Indexed payloads match, but cause, mutation time and signature validity remain unknown; that historical copy is not accepted or replaced. Diagnostic setup formatting, zero-case method selection, partial failing captures and a scoped-storage missing-folder preflight failure are retained with their actual boundaries. A read-only privileged existence check resolved the latter without a daemon/configuration change. Current fresh APK copies and installed digest checks agree.

No Play upload, track/tester change, key/reset action, real customer/order/payment mutation, provider production write or public production-readiness claim occurred.
