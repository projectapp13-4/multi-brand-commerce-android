# Android Virtual-Device Testing Environment

Status: **SECONDARY COVERAGE SURFACE — firmware-blocked; physical-device-first workflow proven separately**

Date inspected: 2026-08-21

The connected Samsung physical device is now the primary day-to-day target. Its
validated build/install/vision/interaction/test loop is recorded in
`ANDROID-PHYSICAL-DEVICE-READINESS-HANDOFF-2026-08-25.md`. This document retains
the separate API 36 emulator setup and its firmware gate; physical-device proof
does not relabel the AVD rows below.

## Selected architecture

The secondary emulator path is:

`Codex -> project-local Android MCP or PowerShell driver -> ADB -> official Android Emulator -> Gur Bakir application`

Android Studio is not required to remain open. When another API level or an emulator-specific configuration is needed, the intended target is a single headless official Android Emulator instance. ADB supplies the rendered PNG screenshot, UI hierarchy, input, lifecycle, intent, and diagnostic channels. A windowed launch remains available only for exceptional debugging.

## Pinned components

| Component | Selection |
| --- | --- |
| Android Emulator | 37.1.11, build 15917651 |
| Platform Tools / ADB | 37.0.1 |
| Command-line Tools | 23.0 |
| Android MCP | `@us-all/android-mcp` 1.14.4 |
| MCP upstream | `us-all/android-mcp-server`, tag `v1.14.4`, commit `7637060d7c2d38b5f22ea4a93fd3177d87dd498c` |
| MCP transport | local stdio |
| MCP write policy | device writes enabled; arbitrary shell tool disabled |
| AVD | `GurBakir_API36` / `medium_phone` |
| System image | Android 16 / API 36, Google Play, x86_64, revision 7 |
| Display | 720 x 1600 px, 320 dpi, portrait |
| Runtime resources | 2 vCPU, 2560 MB RAM, 6 GB data partition, audio/cameras disabled |
| Normal graphics | host GPU |
| Normal presentation | headless (`-no-window`); rendered screen remains available through ADB PNG capture |

The project uses compile/target SDK 36 and min SDK 23. The Google Play image was retained because browser/OAuth, Firebase, Checkout Kit, and Play-services-dependent behavior are in project scope. ARM64 images cannot run in QEMU2 on this x86_64 host, and an older API 30 x86 image did not avoid the acceleration requirement.

## Integration and security review

The MCP package is installed in `.codex/android-mcp` with an exact dependency and npm lockfile. `.codex/config.toml` registers it as a project-local stdio server and restricts its exposed categories to device, UI, application, logcat, emulator, and debug tools. `ANDROID_MCP_ALLOW_WRITE=true` permits the requested device interaction; `ANDROID_MCP_ALLOW_SHELL=false` keeps its separate arbitrary-shell surface disabled.

Upstream and installed distribution inspection found argument-array process execution, input validation, device-path checks, separate write/shell gates, and error redaction. No telemetry or unexpected outbound service was found in the selected server distribution. Its npm production dependency audit again reported zero known vulnerabilities on 2026-08-25. The current MCP handshake exposed 76 Android tools across the configured categories, including device discovery, emulator lifecycle, install/launch/stop, screenshot, hierarchy/accessibility, tap/swipe/text/key input, deep links, logcat, and crash diagnostics.

This Codex desktop task must be reopened or the application restarted after adding the project MCP configuration so the host discovers the new server. The PowerShell driver works without that restart.

## Reusable workflow

From the repository root:

```powershell
# Verify the host gate before allocating emulator RAM.
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Preflight

# Build once, then perform one clean headless boot after the host gate is fixed.
.\gradlew.bat :app:assembleDevelopmentDebug --no-configuration-cache
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Start -ColdBoot -WipeData

# Install, launch, capture actual rendered evidence, and inspect UI semantics.
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Install
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Launch
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Screenshot
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Hierarchy

# Interact and capture the resulting rendered state.
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Tap -X 360 -Y 1200
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Swipe -X 360 -Y 1250 -EndX 360 -EndY 450
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Text -TextValue 'example'
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Screenshot

# Lifecycle, routes, and diagnostics.
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action RestartApp
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action DeepLink -Uri 'https://gurbakir.com/collections/example'
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Logcat
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Crashes
pwsh -File scripts/Invoke-AndroidVirtualDevice.ps1 -Action Stop
```

Use `-WipeData` only for the first accelerated boot or to reset this test-only AVD; omit it during normal iteration so emulator state and fast-boot snapshots can be reused. `Start -Windowed` is available when an emulator window is specifically needed. `Screenshot` writes a device-rendered PNG and `Hierarchy` writes UIAutomator XML under `build/android-virtual-device`; these ignored artifacts can be inspected directly by Codex vision and source tools. The script targets the explicit ADB serial `emulator-5554` and confirms that it belongs to `GurBakir_API36` before acting.

## Current blocker and exact release gate

The AMD Ryzen 3 3200G supports virtualization, but the live Windows host reports:

- `VirtualizationFirmwareEnabled=False`
- `HypervisorPresent=False`
- `systeminfo`: virtualization disabled in firmware
- `emulator-check.exe accel`: exit 6, Android Emulator hypervisor unavailable

Official x86/x86_64 emulator attempts with software acceleration reached only `emulator-5554 offline`; the QEMU threads then remained suspended. Host-GPU, software-GPU, headless, windowed, API 36 x86_64, API 30 x86, and an older official Emulator build were tested without obtaining a booted Android device. The ARM64 image was rejected because the x86_64 host requires a matching system-image architecture. These alternatives were removed after validation; the intended API 36 AVD and current SDK caches were preserved.

The remaining gate is outside the running non-elevated Windows session:

1. Enable **SVM Mode** in the Gigabyte A320M-S2H firmware.
2. From an elevated Windows session, enable **Windows Hypervisor Platform** if it is not already enabled.
3. Reboot Windows.
4. Require `C:\src\android-sdk\emulator\emulator-check.exe accel` to exit 0 before running `Start`.

Firmware setup and the Windows hypervisor feature cannot be changed safely from this live non-elevated task, and both require a restart. The driver deliberately fails early while this gate is closed instead of launching a known-unusable `-accel off` process.

## Resource observations

Android Studio and its Java processes initially consumed about 3.2 GB, leaving less than 1 GB free. Closing the IDE restored roughly 3.7 GB. Gradle and Kotlin daemons can together consume about 2.6 GB during compilation; after the successful build/tests they were explicitly stopped and the workstation returned to about 3.5 GB free. No Android Studio, emulator, QEMU, ADB, Gradle, or Kotlin daemon was left running.

The script requires at least 2.5 GB free before allocating the AVD and starts only one 2560 MB, 2-vCPU instance. Host GPU, disabled audio/cameras, 720 x 1600 rendering, headless operation, and retained SDK/Gradle caches minimize steady-state overhead. The current KSP task has a Gradle configuration-cache serialization warning, so the verified build/test commands use `--no-configuration-cache`; this avoids the incompatible cache path without altering application behavior.

## Evidence status

| Capability | Result |
| --- | --- |
| Official SDK, Emulator, platform tools present | PASS |
| Purpose-built local MCP installed, pinned, audited, handshaken | PASS |
| Single low-overhead API 36 Google Play AVD configured | PASS |
| Android Studio-free headless control workflow prepared | PASS |
| Project APK build | PASS — `assembleDevelopmentDebug`; 20,176,991-byte APK, SHA-256 `9E9C4E67E4F0892E5F1DA14D85E5C0215DB86538EB8D34D6C2B17B1684FB228E` |
| Foundation/account/checkout/storefront/firebase/app JVM tests | PASS — 244 tests, 0 failures, 0 errors, 2 skipped |
| Spotless formatting/static format gate | PASS |
| Accelerated virtual device boot / online ADB identity | EXTERNALLY BLOCKED by firmware SVM and Windows hypervisor state |
| APK install, launch, stop, relaunch | NOT RUN — requires online AVD |
| Actual rendered application screenshot inspected | NOT RUN — requires online AVD |
| Hierarchy, tap, swipe, text, navigation, before/after image | NOT RUN — requires online AVD |
| Application logcat/crash/deep-link evidence | NOT RUN — requires online AVD |
| Android instrumentation tests on AVD | NOT RUN — requires online AVD |

The setup must not be reported as full end-to-end success until the blocked rows are exercised and actual application screenshots are inspected. Emulator evidence will not be relabeled as Samsung/OEM or physical-device evidence; StrongBox, OEM background restrictions, true thermal/battery/performance behavior, and manufacturer-specific browser/payment behavior remain physical-device-only checks.
