# TumiArt Stage 4 P6 — functional acceptance

Date: 2026-10-18. Draft candidate; protected integration and outstanding runtime
boundaries remain separate. No P7, signing, Play publication or production
readiness is claimed.

## Source and scope

Acceptance started from merged `main`
`0a0b5009271df3f029cc57efe12fe35604808f1e` in an isolated P6 worktree.
[Merged-main run 39085342364](https://github.com/projectapp13-4/multi-brand-commerce-android/actions/runs/39085342364)
passed validate, API30 instrumentation, minimum-SDK instrumentation and native
16KiB checks. Older worktrees and the primary checkout were preserved.

P6 uses the existing Customer Account, Storefront, Cart and Checkout Kit owners.
There are no new provider layers, concrete-brand branches in shared code,
merchant identity changes, Home schema changes or presentation changes.
Ignored `out/tumiart-stage4-p6/` holds the checkpoint, exact artifact hashes,
sanitized receipts, test logs and private native captures. Merchant media,
customer identifiers, tokens, cart capabilities and verification codes are not
public evidence.

## Reproduced defect and bounded correction

On API30, controlled hosted sign-in succeeded and the live Cart belonged to the
same Customer Account identity. Native logout cleared protected account state,
but left the Cart quarantined. The provider still associated that Cart with the
customer. Existing quarantine correctly prevented exposure and checkout.

The Storefront adapter constructed an all-absent `CartBuyerIdentityInput` when
asked to detach a buyer. A controlled comparison on that same synthetic Cart
returned success while retaining the customer for the original empty update.
Sending `customerAccessToken: null` explicitly returned no GraphQL/user errors,
and a separate subsequent read confirmed the customer was absent with the
selected variant and quantity preserved.

The correction sends explicit null only for detachment. Association still sends
the supplied protected token. Coordinator ownership postconditions, quarantine,
persistence and error handling remain unchanged. It is a shared provider-contract
correction justified by live TumiArt evidence, not a brand-specific exception.
Wire-level regression coverage distinguishes a missing key from JSON null and
also verifies that association retains the supplied token. The detachment test
failed on the original adapter before the correction.

Provider contract: [cartBuyerIdentityUpdate](https://shopify.dev/docs/api/storefront/2026-07/mutations/cartBuyerIdentityUpdate)
and [CartBuyerIdentityInput](https://shopify.dev/docs/api/storefront/2026-07/input-objects/CartBuyerIdentityInput).
The detachment behavior above was observed directly; nullability alone was not
treated as proof of provider behavior.

## Runtime evidence and limits

| Area | Observed result | Boundary |
| --- | --- | --- |
| OAuth | Actual native launch, exact configured callback/client, S256, state/nonce, hosted cancellation, fresh controlled sign-in | Callback security also has deterministic suite coverage; not every hostile callback was injected into a live transaction |
| Session | Protected persistence, cold restart and explicit real-provider refresh with a changed access-token fingerprint | Natural token expiry was not observed; explicit refresh does not prove it |
| Cart | Exact selected variant `48735083888885` of product `9939567280373`; live inventory/pricing; anonymous and authenticated ownership readback | No hardcoded commercial values; provider may split repeated identical variants into separate discounted lines |
| Recovery | Anonymous Cart restored after process restart; offline quantity update showed an error; explicit retry retained the last valid Cart without replay | Pending behavior also has deterministic native coverage |
| Checkout | Actual API30 authenticated and API36 anonymous Checkout Kit rendered; close returned cancellation/retained-Cart feedback; anonymous reopen after restart rendered again | No order, payment, address submission or genuine customer mutation |
| Home | Active banner, five collections and About in accepted order; native refresh, restart and online recovery | Offline cold restart showed a truthful content-load error; missing/invalid/expired cases use deterministic fixtures without mutating the merchant root |
| Home persistence | Refresh advanced acceptance time; 24-hour editorial snapshot contained no price, inventory, Cart, customer or token authority | Not a 24-hour natural-expiry observation |
| Legal/deletion | Merchant contact, privacy and membership pages returned HTTP200; native privacy route opened and returned; deletion UI truthfully reports no verified dedicated request page | No remote deletion claim or request submission; privacy notice provides email/postal rights channels; contact page has no form |
| TalkBack | Actual service binding, touch exploration and visible title focus | Representative link/variant/Cart activation and spoken output are incomplete; injected input was unreliable on both app and system modal |

Verified owned pages are [contact](https://tumiart.com/pages/iletisim),
[privacy](https://tumiart.com/pages/kvkk-aydinlatma-metni) and
[membership](https://tumiart.com/pages/uyelik-sozlesmesi). Unverified shipping,
returns and legal-notice destinations remain disabled. Gürbakır policies are not
substituted. A dedicated deletion request route remains a merchant-content input,
not an excuse to imply that opening the privacy notice deletes an account.

## API23 live TLS classification

Configured API23 discovery failed with `SSLHandshakeException` and
`CertPathValidatorException: Trust anchor for certification path not found`.
A separate token-free `HttpsURLConnection` probe using the default trust manager
reproduced the failure for the owned Storefront and account domains. The clock
was correct. The default API23 root set lacked ISRG Root X1/X2; the current owned
domain chain passed on API30. Shopify CDN succeeded on API23 through a different
chain, so this was not a general inability to make HTTPS requests.

API23 native fixture tests passing does **not** accept live TumiArt commerce.
No trust-all manager, hostname bypass, user CA installation, SDK-floor change or
provider certificate change was made.

For release planning, prefer an explicit TumiArt application-only supported
Android version decision backed by live tests on the selected minimum. Let's
Encrypt documents ISRG Root X1 trust from Android 7.1.1; this makes API25 a
candidate to verify, not an already accepted floor. A secure alternative is a
narrow, maintained application-owned CA compatibility configuration with normal
hostname/path validation and expiry/rotation tests; it is separate scoped work
requiring a demonstrated need. Do not copy another brand's API24 decision and
assume it repairs this chain.

Sources: [Let's Encrypt compatibility](https://letsencrypt.org/docs/certificate-compatibility/),
[current chains](https://letsencrypt.org/certificates/) and
[Android TLS guidance](https://developer.android.com/privacy-and-security/security-ssl).

## Verification ledger

Before the correction, fresh registered JVM tasks reported 1,068 tests:
1,063 passed, five opt-in Storefront tests skipped, zero failures/errors.
On each of API30 and API23, TumiArt launch/Home/navigation/artwork passed 17/17,
shared functional Account/Cart/Checkout/product/deletion tests passed 60/60,
Account native tests passed 7/7 and protected Cart storage passed 9/9.
The separate configured API23 live discovery failed for the TLS reason above.
These fixture suites are not presented as hosted provider acceptance.

After the correction, every registered JVM task was explicitly rerun: 1,070
reported, 1,065 passed, five opt-in Storefront tests skipped, zero failures/errors.
This includes Gürbakır development/staging/production, Trial, Synthetic and
TumiArt. The corrected configured development APK has SHA256
`24e101df889de32157998ad00d3dc6637355d635f3538b096bb1fe1e13acfae1`.
It was installed in place without clearing application data. Independent focused
review found no actionable issue in the provider correction and its tests.

Final candidate verification, exact commits, runtime correction acceptance and
CI readback are recorded in the ignored checkpoint and draft PR. Protected CI
does not exercise the private controlled account or substitute for device
evidence. P7 must retain the natural-expiry, remote-logout, TalkBack activation,
dedicated deletion-route and API23 live-support limitations unless new direct
evidence closes them.
