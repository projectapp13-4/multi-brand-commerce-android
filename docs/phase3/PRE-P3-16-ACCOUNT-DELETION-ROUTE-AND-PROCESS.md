# Pre-P3-16 account deletion route and merchant process

Status: **public route, synthetic form acceptance and owner-supplied merchant inbox receipt evidence passed; end-to-end deletion remains unverified** (2026-09-25).

This scoped continuation follows the historical [P3-13 handoff](P3-13-HANDOFF.md). It does not start P3-16 or turn local cleanup into evidence of remote deletion.

## Typed route and trust boundary

The application identity owns `webRoles.legalSupport.origin` and optional `paths.accountDeletionRequest` in the tracked onboarding registry. The narrow role is a relative `/pages/<slug>` path, distinct from Support and Privacy. The registry rejects unsafe path syntax and duplicate roles, then projects the public value into the brand application build. The app's exact-route policy checks the resulting HTTPS URL before Custom Tabs opens it. Shopify content may provide words and a contact form behind that approved route; it cannot select Android navigation, arbitrary URLs, credentials, or executable behavior.

Gürbakır development and staging share one brand-level resource: `https://gurbakir.com/pages/uygulama-hesap-silme-talebi`. A separate profile path was unnecessary because both profiles use the same merchant origin. The published Shopify Page uses the existing `contact` theme template and identifies the app-account and associated-data request. It offers the merchant contact email as a fallback and avoids a deadline or blanket erasure promise. Signed-out GET currently returns HTTP 200 with deletion-specific text and a contact form. One project-controlled synthetic form submission returned `contact_posted=true` and a storefront success message. The owner supplied a merchant-inbox screenshot showing a Shopify contact notification with the matching synthetic rehearsal reference; sender and recipient metadata were cropped. The screenshot remains in task context and is not copied into the public repository. Acknowledgement and downstream processing remain unverified.

Trial has no deletion-specific merchant resource. Its development-only deletion screen therefore has no request button; it does not inherit Gürbakır's route or treat its generic Support page as a deletion endpoint. Synthetic keeps Customer Account disabled and has no deletion entry. Before any account-enabled brand becomes release-ready, its own public resource and process must pass all three acceptance layers below.

## Merchant operational process

The merchant, not Android, owns intake and completion. A restricted ledger or helpdesk can use the following states:

| State | Required evidence or action |
|---|---|
| Available | Public deletion-specific page and form work signed out. |
| Received | Merchant records an opaque request ID, timestamp and channel after confirming inbox/helpdesk receipt. |
| Verification pending or matched | Match only the necessary account reference; verify identity without asking for password or one-time code. |
| Acknowledged | Send a human acknowledgement through the controlled merchant channel. No invented fixed SLA. |
| Retention review | Record the applicable exception decision and processor checklist without copying customer PII into repository evidence. |
| Provider action pending | Authorized operator uses Shopify Admin's customer personal-data erasure workflow and addresses other relevant processors. Profile deletion is a distinct action with different eligibility. |
| Provider confirmed or partial retention | Read back provider status and recorded exceptions; an accepted mutation is not completion proof. |
| Notified and closed | Tell the requester the supported outcome and close the restricted record. Failures and duplicate requests remain tracked for retry or escalation. |

The minimum restricted record is request ID, timestamps, channel, merchant account reference, verification status, assigned operator, acknowledgement, retention decision reference, processor action/status references, notification and closure state. Keep it out of Git and public reports. A merchant owner must assign the operator and decide any response target. Shopify's ten-day cancellation window for a submitted erasure request is **not** a merchant response SLA.

## Acceptance layers and remaining evidence

1. **Tracked configuration:** the registry, projections, build fields, role mapping, path policy and unit tests must pass. Missing or unsafe deletion paths fail the release-readiness check.
2. **Public resource:** run `pwsh -NoProfile -File scripts/Test-AccountDeletionResource.ps1 -Application <brand> -Profile <profile>` from a signed-out context. This verifies HTTP 200, brand/deletion wording and a form. It does not submit a request.
3. **Merchant process:** use only an unmistakably project-controlled synthetic identity to prove form submission, merchant inbox/helpdesk receipt, acknowledgement, identity review, applicable Shopify action/readback, other processors, completion or partial-retention notice, and duplicate/failure handling. No actual customer should be touched without the owner-policy point-of-action confirmation.

The app screen treats opening and returning from the browser as unverified navigation. Local cleanup remains separately confirmed and has its existing Search, Wishlist, Cart and sign-out choices. Remote erasure cannot be inferred from either action.

Current visitor-consent disposition is **BLOCKED — MERCHANT/LEGAL CONSENT DECISION REQUIRED**. Shopify's Türkiye consent-policy readback says `consentRequired=false` and `dataSaleOptOutRequired=false`; active-market readback showed Türkiye only. Authenticated Gürbakır Admin readback found a configured cookie banner for 31 listed regions, Shopify Network Intelligence enabled, and one Google & YouTube Web app pixel with optimized data access. That pixel's privacy setting says consent is not required and its collection is not classified as data sale. The connected API scope could not read Customer Privacy or server-pixel inventory; the browser supplied only the visible Web-pixel view. The merchant has not chosen whether the Android application will collect consent, which purposes it would cover, or how that choice will relate to site/checkout controls. Storefront omission means no expressed preference, not refusal. Do not inject false consent values. If the merchant model requires Android collection, plan that as a separate source change.

P3-16 remains **NOT STARTED**. Production identity, signing, Play ownership/declarations, service bindings, public App Links, reviewer access, accountable operator and full deletion/retention evidence remain separate entry inputs.
