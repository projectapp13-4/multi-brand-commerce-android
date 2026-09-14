# Gate 8 candidate handoff

Status: **Implementation present; configured nonproduction acceptance not run; Gate 8 is not technically closed.**

Base: `ea936053b6d221a2abdfccf2f207167797ca330e`. Approved plan digest:
`ce66e5ad7c5d830b4fcfc895e32dbb41a98871eaedf4636b239359a87bf6175a`.

Implemented: strict enrollment/binding/receipt schemas, deterministic non-secret
profile projections, scoped client configuration, registry-aware CI validation,
application-owned Customer Account User-Agent, read-only provider inspection,
and guarded Home-definition/DRAFT-probe Apply. Menu, Firebase, Customer
registration, domains, production, and second-brand state remain non-mutating.

Evidence: onboarding self-tests PASS 41/41; focused Foundation/Account and both
app-profile/Storefront JVM suites PASS; portability self-tests PASS 68/68 and
current validation PASS 46; public-readiness self-tests PASS 14/14; Firebase
zero-file local-default check PASS; Spotless PASS; Detekt PASS after the final
constant correction. Full lint was interrupted during aggregation. API 30/API 23
instrumentation, live configured provider acceptance, live Apply/readback, and
idempotence are NOT RUN. No provider or GitHub state was changed.

Gate 8 closure still requires the plan's configured development/staging evidence,
full candidate/security review, protected PR checks, merge, and post-merge CI.
