# Tooling and Codex Infrastructure

Date: 2026-07-19
Scope: revised Phase 1 native-Android preparation; no live-service configuration or production scaffold

## Outcome

Tooling was reconsidered only after the platform decision. Android Studio was updated, Gitleaks and the official Firebase CLI were installed and execution-tested, and two focused Codex security skills were installed for the next turn. Android libraries, formatters, analyzers, test frameworks, and code generators remain project-pinned Phase 2 dependencies rather than global tools.

No Shopify/Firebase account connector, MCP server, code graph, database, AI framework, or background service was added. Native Codex Browser/shell/file capabilities already provide the required official-document and local-repository access; live services remain intentionally unauthorized.

## Workstation changes

| Tool | Final state | Why |
|---|---|---|
| Android Studio | Updated to 2026.1.2 build `AI-261.25134.95.2612.15822958` | First-class Compose/Gradle/device/profiling/testing environment |
| Gitleaks | Installed and verified at `8.30.1` | Deterministic local and CI credential scanning |
| Firebase CLI | Installed from official npm `firebase-tools`, verified at `15.24.0` | Owned-project setup/emulators/deploy tooling when separately authorized |
| WinGet Firebase package | Rejected after runtime test; stale user-scope record remains | Its executable failed on missing `semver`; elevated uninstall could not remove the user-scope record, and the verified npm command is first on PATH |
| JDK/Android SDK | Kept | JDK 17 and current SDK packages already satisfy the native foundation inventory |
| Flutter/Dart | Kept as unrelated inventory | Old prototype validation only; zero architectural weight |

The Firebase CLI was not logged in and no Firebase project was created, initialized, read, or mutated. `where firebase` resolves only the working npm command; WinGet may still display the rejected user-scope package record. The Android Studio update was completed through the official WinGet publisher package after the normal upgrade route required the publisher installer.

## Codex structure

| Mechanism | Decision | Evidence / reason |
|---|---|---|
| Root `AGENTS.md` | Updated | Durable native decision, evidence boundaries, iOS/multi-brand interpretation, and verification gates |
| Private evidence-navigation skill | Retired from the public tree | Its useful results are consolidated into the small neutral model under `docs/reference-model` |
| New project-local Android skill | Defer | Before a scaffold, it would duplicate ADR/instructions without a proven repeatable scriptable workflow |
| Curated `security-best-practices` | Installed | Auth, token storage, Firebase, checkout, backend and release changes cross trust boundaries |
| Curated `security-threat-model` | Installed | Threat modeling is a required early Phase 2 gate |
| In-app Browser | Retained and used | Fresh primary-source platform/Shopify/Firebase/upstream research |
| Computer Use | Retained; scoped inspection only | Used to inspect a stalled publisher install; no UI action was needed after the installer completed |
| MCP / account connector | No addition | No missing structured external access justifies credentials or background infrastructure in Phase 1 |
| Subagents | Not used | Governing instructions did not authorize delegation |

Installed personal skills become discoverable to Codex on the next turn. No existing skill was removed or overwritten.

## MCP and integration assessment

The active structured-tool inventory and local Codex configuration show the bundled Browser/Computer Use and a `node_repl` MCP runtime. `codex mcp list` remains inaccessible from this WindowsApps shell (`Access denied`), so the reassessment used the session's actual callable inventory and local MCP headings rather than bypassing security controls.

Rejected for now:

- Shopify/Firebase MCP: live project/store access is not authorized and official docs/CLI/schema tooling cover Phase 2 preparation.
- Sourcegraph/Neo4j/code graph: the future source tree is intentionally empty.
- Sentry connector/skill: Crashlytics is selected provisionally and no observability account/project exists.
- Playwright/browser-test skill: it does not test native Compose UI or Checkout Kit lifecycle.
- Figma/design MCP additions: no approved design source or current design task.

## Project-pinned tool plan

| Category | Phase 2 choice |
|---|---|
| Build/package management | Gradle wrapper + version catalog + dependency verification |
| Format/lint/static analysis | Spotless/ktlint, detekt, Android Lint |
| GraphQL/code generation | Apollo Kotlin Gradle plugin with separately versioned Storefront and Customer Account schemas |
| Android/Firebase/Shopify | Official AndroidX/Hilt/Firebase BoM/Checkout Kit dependencies |
| Unit/contract/UI tests | JUnit, coroutine/Flow tests, Apollo/MockWebServer or fakes, Compose UI and instrumentation |
| Device/emulator | Physical device first; emulator only if acceleration is reliable |
| Security/dependencies | Gitleaks, Gradle dependency verification, dependency update/review policy, focused threat model |
| Accessibility | Compose semantics tests plus physical-device TalkBack checks |
| Performance | Android Studio profilers, Macrobenchmark, Baseline Profiles |
| Screenshot regression | Select after representative brand UI and deterministic CI renderer exist |

No global Gradle, Apollo CLI, detekt, ktlint, Android library, or project SDK dependency was installed. Those must move with the repository and be reproducible in CI.

## Validation notes

- The Skill Creator validator initially lacked `PyYAML`; `PyYAML 6.0.3` was installed only into an isolated temporary validator dependency directory, then the existing repo skill passed.
- The normal curated skill catalog was refreshed from `openai/skills`; only the two security skills had clear project value.
- Official Codex manual cache was refreshed and already current. Persistent rules stay in `AGENTS.md`, recurring workflows in skills, and external structured access in MCP only when justified.

## References

- [Android Studio](https://developer.android.com/studio)
- [Firebase CLI](https://firebase.google.com/docs/cli)
- [Gitleaks](https://github.com/gitleaks/gitleaks)
- [Repository instructions with AGENTS.md](https://developers.openai.com/codex/guides/agents-md)
- [Agent Skills](https://developers.openai.com/codex/skills)
- [Model Context Protocol](https://developers.openai.com/codex/mcp)
