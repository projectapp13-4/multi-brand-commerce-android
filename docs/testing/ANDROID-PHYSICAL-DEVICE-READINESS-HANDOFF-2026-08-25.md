# Pre-CP-02 Android development and physical-device readiness handoff

Status: **READY — PHYSICAL-DEVICE-FIRST LOOP PROVEN; CP-02 NOT STARTED**

Date: 2026-08-25

Checkpoint boundary: CP-01 remains complete and paused; this is tooling and
validation evidence only.

## Outcome

Codex can independently run the intended source-to-device loop without Android
Studio: inspect, build, install/replace, launch, capture the rendered device UI,
visually inspect it, navigate, tap, swipe, enter text, inspect structure/logs,
run device tests, and repeat. The only manual action in this task was the
phone's one-time USB-debugging RSA approval after ADB initially reported
`unauthorized`; normal iterations after authorization were autonomous.

No production Kotlin, Compose, navigation, feature, branding, or UI behavior was
edited. CP-02 design work has not begun.

## Workstation and resource baseline

| Area | Observed state / decision |
| --- | --- |
| Host | Windows 10 Pro 10.0.19045; AMD Ryzen 3 3200G, 4 logical processors |
| Memory | 8,204,736 KB visible RAM; full local gate is memory-heavy, so Gradle/device work remains serialized |
| Storage | Healthy 1 TB SATA HDD; repository, SDK, caches, and AVD data were not moved |
| Virtual memory | Page file remained enabled at 8,192 MB initial / 12,288 MB maximum |
| IDE/emulator | Android Studio is not required or running for the primary loop; the emulator remains secondary |
| Toolchain | Microsoft OpenJDK 17.0.19, Gradle 9.4.1, AGP 9.2.1, Kotlin 2.3.10, compile/target SDK 36, min SDK 23 |
| Android CLI | Platform Tools / ADB 37.0.1; Android command-line tools available under `C:\src\android-sdk` |

The full deterministic gate temporarily drove the Gradle daemon to roughly
2.0 GB working set and the Kotlin daemon to roughly 1.1 GB, leaving about
0.5 GB free. Stopping Gradle restored about 3.6 GB free. This supports a single
authoritative build/test operation, not concurrent Gradle jobs or an emulator
beside a full build. The warm incremental development APK assembly completed in
19.53 seconds; no `clean` or cache deletion was used.

## Gradle, JVM, and Kotlin decisions

`gradle.properties` now uses evidence-bounded settings for this host:

- Gradle heap 2,048 MB and metaspace cap 512 MB;
- Kotlin daemon heap 1,024 MB and metaspace cap 512 MB, instead of inheriting the
  previous 3,072 MB Gradle heap as a second large JVM;
- at most two workers, parallel execution disabled, build cache retained;
- Gradle daemon retained for incremental iteration with a 15-minute idle timeout;
- configuration cache disabled because this repository's KSP task previously
  failed to serialize its state and Gradle discarded that cache;
- heap dump on OOM retained as bounded diagnostic evidence.

`gradle/gradle-daemon-jvm.properties` pins compatible Java 17, matching the
installed local JDK and AGP 9.2 baseline. The official `updateDaemonJvm` task was
attempted once but could not provision a toolchain because this build declares no
toolchain download repository; the equivalent minimal Gradle property was then
recorded directly and `gradlew --version` confirmed it. No retry loop or JDK
download was introduced.

Deliberately unchanged: page file, Defender/firewall, dependency verification,
Gradle caches, incremental compilation defaults, worker count of two, disabled
parallelism, Android SDK location, repository location, and all production build
dependencies. Gradle 10 deprecation warnings remain future maintenance debt;
they do not fail the current Gradle 9.4.1 gate.

## Android control architecture

Primary path:

`Codex -> project-local Android MCP / bounded ADB -> authorized physical Samsung -> development app`

The live project MCP is `@us-all/android-mcp` 1.14.4 (MIT), pinned by exact npm
dependency and lockfile from upstream `us-all/android-mcp-server` tag `v1.14.4`,
audited commit `7637060d7c2d38b5f22ea4a93fd3177d87dd498c`. Its current production dependency
audit reports zero known vulnerabilities. The project configuration exposes 76
tools across device, UI, apps, logcat, emulator, debug, and meta categories.
Device writes required for installation and interaction are enabled; its
arbitrary-shell category remains disabled. ADB is the bounded native fallback.

The configuration is intentionally workstation-local: `.codex/config.toml`
records this dedicated machine's Node, repository, and SDK paths, while the AVD
driver has the documented `C:\src\android-sdk` fallback. These are the only
machine-specific absolute tracked paths found in this unit, are not secrets, and
must be updated if the repository or SDK is moved. A Codex restart/new task is
required after changing MCP configuration; the server is already discovered and
usable in the current setup.

## Physical-device proof

| Capability | Current evidence |
| --- | --- |
| Identity | Exactly one authorized/online Samsung SM-A225F, Android 13 / API 33, arm64-v8a, 720 x 1600, density 300 |
| Package | `com.gurbakir.mobile.dev.debug`, version 0.1.0 (1), target 36 / min 23 |
| APK | `app-development-debug.apk`, 20,176,991 bytes, SHA-256 `9E9C4E67E4F0892E5F1DA14D85E5C0215DB86538EB8D34D6C2B17B1684FB228E` |
| Install/update | MCP streamed replacement install returned `Success`; repeated after instrumentation to restore the normal app artifact |
| Launch | Launcher invocation rendered splash and then Home |
| Stop/relaunch | Force-stop succeeded, `pidof` confirmed no process, and relaunch returned to Home |
| SEE | Six direct 720 x 1600 PNGs were opened at original resolution and visually inspected |
| Structure | UIAutomator hierarchy and a completed accessibility snapshot were inspected |
| Interaction | Bottom navigation, category-card tap, upward swipe, collection navigation, Search field focus, `bakir` text input, keyboard, Android Back, and explicit deep links worked |
| State comparison | Home, scrolled Categories, populated collection, Search/result loading, deep-link empty, and deep-link populated states are preserved in the evidence manifest |
| Diagnostics | Package crash query: 0; current-process fatal/ANR matches: 0; nonfatal OEM/renderer diagnostics classified |

The six screenshots are retained only in the private historical repository and
its verified private archive because redistribution rights for their visible
catalog/product imagery were not affirmatively established. The public tree
retains their historical manifest metadata and hashes in
`docs/testing/evidence/pre-cp02-android-readiness-2026-08-25/README.md`.

The generic URL opener selected the device browser because the debug build's
owned intent filters use `autoVerify=false`. The deterministic package-targeted
ADB invocation routed both owned URLs into `MainActivity`: an unknown collection
rendered a safe not-found state, and `tencereler` rendered four products. This is
a tested workflow distinction, not a deep-link blocker.

## Developer Options and restoration

No Developer Option, global setting, per-app compatibility override, permission,
OEM control, or unrelated device state was changed. Post-test reads confirmed:

- USB debugging on, wireless debugging off, stay-awake-while-powered value 7;
- Don't keep activities off and background process limit `null` (Standard);
- window, transition, and animator scales 1.0; font scale 1.0;
- pointer location off and OEM unlock not allowed.

No root, bootloader, modem, media, contacts, location, message, unrelated app,
or reference APK action occurred. The device serial is omitted from this handoff.

## Build and test evidence

The serialized deterministic gate passed in 13 minutes 58 seconds:

```text
spotlessCheck
detekt
lint
:foundation:testDebugUnitTest
:account:testDebugUnitTest
:checkout:testDebugUnitTest
:storefront:testDebugUnitTest
:firebase:testDebugUnitTest
:app:testDevelopmentDebugUnitTest
:app:assembleDevelopmentDebug
```

Result: `BUILD SUCCESSFUL`; 309 actionable tasks, 42 executed and 267 up-to-date.
The JUnit XML set contains 244 tests, zero failures/errors, and two existing
conditional skips. APK assembly and dependency verification passed. Existing
non-failing warnings remain in `LegalSupportModel.kt` (future annotation target)
and `WishlistViewModelTest.kt` (coroutine API opt-in); this readiness task did not
expand into product/test refactoring.

Physical instrumentation ended with one complete authoritative
`:app:connectedDevelopmentDebugAndroidTest` run: **83 / 83 PASS**, zero skips and
zero failures, `BUILD SUCCESSFUL` in 1 minute 59 seconds. An earlier full run had
one transient `assertIsDisplayed` failure in
`HomeScreenTest.collectionAndFeaturedProductCardsOpenFunctionalDestinations`
(82 / 83); the exact failed test immediately passed alone, and the subsequent
complete run passed all 83. The transient is disclosed as device/Compose runner
variance; no test or production code was changed to conceal it.

## Skills and workflow choices

- Retained `mobile-ui-ux-designer` as the primary CP-02 design/evaluation skill.
- Retained `edge-to-edge` as the narrow Android/Compose inset companion.
- Used `skill-installer` to inspect the current curated list; no additional
  native Android physical-device readiness skill materially improved this loop.
- Evaluated the current Superpowers repository but did not install it: its
  mandatory worktree/subagent-heavy workflow conflicts with this repository's
  single-worktree checkpoint policy and the host's serialized 8 GB/HDD strategy.
- Used official OpenAI documentation for project-scoped skills/MCP configuration.
- Loaded Browser and Computer Use guidance as requested, then kept both as
  fallbacks because the purpose-built MCP/ADB path provided safer, lower-overhead
  phone control and direct screenshots.

No new skill, hook, permanent service, browser dependency, account connection,
or overlapping methodology framework was added.

## Remaining limitations and checkpoint boundary

- The primary phone proves Android 13 / API 33 on one 720 x 1600 Samsung form
  factor. Android 16/API 36 runtime behavior, another density/size, tablet/fold,
  and non-Samsung OEM behavior remain later matrix coverage.
- The official API 36 AVD remains a secondary coverage surface blocked by AMD
  SVM/Windows Hypervisor firmware state. Its isolated handoff remains in
  `ANDROID-VIRTUAL-DEVICE-ENVIRONMENT.md`; the physical-device success does not
  relabel that blocker.
- Full gates are RAM- and HDD-intensive. Keep Android Studio/emulators closed,
  serialize Gradle, preserve caches, avoid routine `clean`, and stop daemons after
  a heavy acceptance run. Warm incremental APK assembly is the normal loop.
- The Search-screen accessibility snapshot exposed unlabeled UIAutomator nodes
  and two small bounds. It is preserved as a tool baseline for the existing
  product-quality backlog; CP-02/CP-05 must decide/remediate under their own
  contracts. No broad UI edit was started here.

At the handoff boundary, the normal development APK is installed, all project
processes are stopped, no temporary device override remains, and the coherent
readiness unit is committed with a clean worktree. No remote exists, so nothing
was pushed.

## Exact resume instruction

Send the explicit Checkpoint 2 task and state: **“Resume Checkpoint 2 from
`docs/testing/ANDROID-PHYSICAL-DEVICE-READINESS-HANDOFF-2026-08-25.md`; use the
physical-device-first MCP/ADB loop, preserve CP-01 evidence, and do not advance
beyond the next owner checkpoint.”**

Until that instruction arrives, do not begin CP-02 analysis or implementation.
