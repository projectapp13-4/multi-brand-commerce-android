# Public Repository Migration Handoff

Status: **COMPLETE — this public repository is the forward-development authority**

Status date: 2026-09-13

## Result and authority boundary

This repository was created with fresh Git history from an approved sanitized
export. The owner-controlled predecessor remains private, retains its original
repository identity and Git refs, and is archived as historical evidence. It
received no migration-status commit and is not a development upstream.

The migration changed repository framing, portable tooling metadata,
documentation authority, and the Gradle root project name. It did not rename,
genericize, or behaviorally alter the concrete Gürbakır application. Multi-Brand
work remains unfinished, and Gürbakır `:app` remains the first real brand
application alongside the non-production `:synthetic` conformance application.

## Immutable baselines

| Evidence | Value |
|---|---|
| Verified historical source commit | `44202d31da8820a1f1766cb767bc0a1619d9c07e` |
| Approved sanitized content tree | `abe3cedde4169b514b07b56017f752b8dcb55968` |
| Fresh parentless root commit | `ba67e602d3cda2bb65326e8a0a5e9d156013245e` |
| Authority-cutover `main` commit | `b8ecf5d084578a79c07d941b1a0be3c5648948c9` |
| Historical remote heads at cutover | `24` |
| Historical remote tag refs at cutover | `0` |
| Historical heads/tags snapshot SHA-256 | `6207ce6a1feb874464bb97dc93f7efb724706bd5be4330dde601f70867fb8235` |

The root commit and authority-cutover commit resolve to the same approved
content tree. The intermediate bootstrap-proof commits deliberately introduced
and then fully reverted one invalid root-name value; no invalid content reached
`main`.

## Private evidence preservation

The private forensic/reference archive passed file-count, integrity, and restore
checks. Its SHA-256 is
`713529251bb2eba8b2293aa8075efa6538fc63271fd384feb56b4ea1017bed36`.
Its owner-controlled storage location is intentionally omitted from this public
document.

The six historical testing screenshots were excluded from the public tree
because redistribution rights for their embedded catalog/product imagery were
not affirmatively established. A separate private seven-file archive (six PNGs
plus their manifest) passed a blob-for-blob restore check. Its SHA-256 is
`66dec6c5f57a8f2e1fd57266769fdd4a1e78da93d19433136e05085282f6a011`.
The original files also remain in the archived private historical repository.
No runtime/application asset was changed or removed.

## Publication rights

The Gürbakır owner authorized public repository distribution of the concrete
application name/copy, launcher and UI icons, and Gürbakır-owned brand assets on
2026-09-13. Gürbakır marks and designated brand assets remain outside the
Apache-2.0 grant. Licensed Material icon vectors retain their upstream
Apache-2.0 disposition, and the project-created synthetic launcher is included
under the repository license. The path-level record is
[`ASSET-LICENSES.md`](../ASSET-LICENSES.md), with trademark and dependency
boundaries in [`TRADEMARKS.md`](../TRADEMARKS.md) and
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

## Validation evidence

The exact historical source commit was reverified as the Gate 6 merge commit;
its merged-main push run `34710233503` completed `validate`, API 30
`instrumentation`, and API 23 `minimum-sdk-instrumentation` successfully.

The fresh public root commit was then validated by manually dispatching the
canonical workflow against exact `main`:

- [workflow_dispatch run 34726667547](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/34726667547): all three canonical jobs successful on `ba67e602d3cda2bb65326e8a0a5e9d156013245e`.
- [bootstrap failing run 34727661896](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/34727661896): the deliberately invalid root name made `validate` fail and GitHub reported PR #4 blocked; the obsolete run was cancelled by the corrective push.
- [bootstrap corrected run 34727700172](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/34727700172): all three required jobs successful on the restored approved tree.
- [merged-main push run 34728064091](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/34728064091): all three canonical jobs successful on authority-cutover commit `b8ecf5d084578a79c07d941b1a0be3c5648948c9`.

Local and fresh-clone evidence also passed the public-readiness validator and
its self-tests, repository portability checks, strict tree/history secret scans,
format/static analysis, JVM tests, assembly matrix, and existing artifact and
synthetic-package checks. Local API 30/API 23 lanes were not claimed; the public
GitHub-hosted runs above supply those canonical device results.

## Repository protection

Visibility and protection used a two-stage bootstrap under a write freeze:

1. The repository became public and immediately received active PR-only,
   branch-deletion, and non-fast-forward rules for `main`, with zero bootstrap
   approvals, conversation resolution, and no bypass actors.
2. Actions was enabled with a read-only default token, no PR-approval ability,
   selected actions only, mandatory full-SHA pinning, seven-day retention, and
   approval required for every external contributor. After the first successful
   exact-baseline run emitted its contexts, `validate`, `instrumentation`, and
   `minimum-sdk-instrumentation` were added as strict GitHub Actions checks.

Ruleset `23107007` was read back after configuration with all four rule types
active and the three required contexts bound to the GitHub Actions integration.
Secret scanning and push protection were enabled. At cutover, only the owner had
write/admin access, `main` was the only public branch, and no pull request was
open.

The configured Dependabot version-update service opened three private bootstrap
PRs when the initial tree first appeared. They were closed without merging, the
remaining dynamic update run was cancelled, and `main` remained unchanged.
Future dependency updates must use the protected PR path.

## Continuation and non-proofs

All future branches and worktrees must start from this public repository. The
private predecessor is evidence only. Current source, requirements,
architecture, accepted ADRs, tests, and the documentation authority map govern
forward engineering; the compact reference model remains subordinate.

This migration does not complete Multi-Brand work and does not prove production
or release readiness. P3-16 remains not started, and live signing, production
service configuration, associations/callbacks, privacy declarations,
support/rollback ownership, and release approval remain separate gates.
