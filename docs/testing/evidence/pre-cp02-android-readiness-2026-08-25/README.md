# Pre-CP-02 physical-device evidence manifest

Public-candidate note: the six PNG files recorded below are retained only in
the private historical repository and its verified private archive. They are
not distributed in the public tree because redistribution rights for embedded
catalog/product imagery were not affirmatively established. This manifest and
its hashes remain as historically accurate evidence metadata.

Date: 2026-08-25

Device class: Samsung SM-A225F, Android 13 / API 33, 720 x 1600, 300 dpi

Package: `com.gurbakir.mobile.dev.debug`

The device serial is intentionally omitted. The privately retained files are
direct PNG captures from the authorized physical device, not previews or
reconstructed mockups. Each PNG was opened at original resolution and visually
inspected by Codex. No reference APK, unrelated application, notification
content, or device file was captured.

| File | Proven state | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `01-home-launch.png` | Normal development APK launched to rendered Home | 440,501 | `924CFD2E218759F883841EA5F2B125461B5F75255AB8B66201D37B97B2946A29` |
| `02-categories-after-swipe.png` | Categories after a real upward swipe; content position changed | 699,035 | `BAAB59ADC5C17064710F16928B4BFAECBC8585D91312CD62C1521F2DEDFF7951` |
| `03-collection-after-tap.png` | `Cezveler` collection after tapping a visible category card; seven products rendered | 398,372 | `6484BF2274C4D86607939A307720F18CA5265644623EA74779204B42EC38CB7B` |
| `04-search-input.png` | Search field after MCP text input `bakir`; keyboard and matching-result state rendered | 113,068 | `178249C4D8A39DE3CADFDC0C6A4132580FD0FA63EFAD879765CAEFB7D1E1A144` |
| `05-deep-link-empty-state.png` | Explicit owned collection deep link routed into the app and rendered its safe not-found state | 40,036 | `B83A7A02840D4C90F4D9CCB48B9000140ECA5264CBA33BB82A43FB41FCFFE3D2` |
| `06-deep-link-collection.png` | Explicit owned `tencereler` deep link routed into the app and rendered four products | 521,149 | `2625E7E044F076BF6F7CABDC495325B9E178A69430EE71932B19A4347A0C5F0C` |

## Interaction sequence

1. Install/replace and launch the development APK.
2. Inspect Home screenshot and UI hierarchy.
3. Tap Categories, swipe the list, and compare the resulting screenshot.
4. Tap the visible `Cezveler` card and inspect the collection state.
5. Use Android Back, open Search, focus its real input, and type `bakir`.
6. Force-stop the package, verify that its process is absent, relaunch it, and
   visually verify Home after the splash screen.
7. Invoke one nonexistent and one published owned collection URL with an
   explicit package target; inspect the safe empty and populated results.

The generic URL opener followed the phone's browser default because these debug
intent filters use `autoVerify=false`. Deterministic app-route validation therefore
uses the package-targeted ADB form already implemented by
`scripts/Invoke-AndroidVirtualDevice.ps1 -Action DeepLink`.

## Structural and diagnostic evidence

- UIAutomator hierarchy exposed real scrollable, clickable, text-input, Back,
  and five-destination bottom-navigation nodes.
- The current MCP accessibility snapshot completed. On the Search result screen
  it reported 12 interactive elements, 11 unlabeled nodes, and two small target
  bounds. This is a readiness/tooling baseline, not a CP-02 remediation or a
  replacement for the authoritative CP-01 issue matrix.
- Package-filtered crash-buffer inspection returned zero crashes.
- Current-process logcat contained zero fatal-exception or ANR matches. The
  error-priority rows were bounded OEM/renderer diagnostics (wide-gamut EGL
  config fallback, ION ioctl, and missing OEM helper-library/file messages), not
  an application crash.
