# P3-00 Content, Asset, Legal, Market, and Merchant Inventory

Date: 2026-08-10

Status: **PLANNING COMPLETE — EXTERNAL INPUTS REMAIN EXPLICIT**

No row below supplies invented legal, merchant, marketing, support, deletion, policy, or brand content. “Available” means a project-owned input exists and may be used within its stated boundary; it does not imply final release approval.

## Ownership model

| Role | Accountability |
|---|---|
| Project owner / merchant | Final product scope, supported market/currency, Shopify taxonomy/merchandising, product/content accuracy, brand asset acceptance and support contact |
| Android product design/engineering owner | Token/component/state/accessibility specification, asset integration/provenance record, localization keys, safe fallbacks and implementation evidence |
| Project owner with qualified legal/privacy source | Privacy, terms, shipping/returns/cancellation/refund, account-deletion process/copy, consent, effective date, retention and data-controller obligations |
| Operations/support owner designated by project owner | Customer contact route, deletion-request handling/SLA, carrier escalation, incident/update communication and content maintenance |
| Release owner designated by project owner | Production identity, app/store assets, signing/Play, production Shopify/Firebase/App Links, Data Safety, rollout and rollback |

Role-based ownership is sufficient for planning. P3-08, P3-13 and P3-16 require named accountable people/processes and real owned resources before implementation/release can pass.

## Inventory

| ID | Item | Current evidence/state | Classification | Required owner/source | Needed by | Consequence if missing |
|---|---|---|---|---|---|---|
| CI-01 | Product display name | `Gürbakır` exists in the project-owned brand configuration | Available owned input | Project owner confirms final spelling/casing before release | P3-01/P3-16 | Text wordmark can support implementation; final listing/identity stops without confirmation |
| CI-02 | Color roles | Project-owned light/dark ARGB tokens exist and pass P3-00 contrast checks | Accepted P3-00 implementation baseline; reversible | Android design/engineering; project owner for later brand-kit reconciliation | P3-01 | No blocker for implementation; unreviewed divergence from final brand kit blocks release |
| CI-03 | Spacing/shape system | 4/8/16/24dp and 8/12/20dp typed tokens exist | Accepted P3-00 baseline | Android design/engineering | P3-01 | Implementation would be inconsistent without it; now closed |
| CI-04 | Typography | No custom font asset; system/Material font behavior | Accepted safe baseline, not final font identity | Project owner supplies licensed font only if required; engineering records license/performance | P3-01/P3-16 | No blocker with system font; unlicensed or unproven custom font cannot ship |
| CI-05 | Text wordmark | Product name can be rendered as text | Permitted neutral implementation fallback | Project owner | P3-01 | P3-01 may proceed after merchant packet; final logo is a separate release input |
| CI-06 | Final logo | No approved logo resource | External asset blocker | Project owner supplies owned source/vector variants and usage rules | P3-16 | Production brand/release review stops; no invented logo |
| CI-07 | Launcher/store icon | Current foreground is an engineering asset, not accepted final identity | External asset blocker | Project owner/release owner supplies adaptive foreground/background, monochrome and store artwork with provenance | P3-16 | Production artifact/listing stops |
| CI-08 | Splash artwork | No approved final artwork | External asset blocker; branded text/solid token surface is permitted for implementation | Project owner/release owner | P3-16 | Final release identity review stops; startup logic does not |
| CI-09 | Hero/editorial imagery | No approved local campaign/hero asset | External merchant-content blocker | Merchant supplies owned media, crop/focal/alt text, campaign dates and fallback | P3-01 if Home design requires hero | P3-01 must omit the hero rather than use stock/fake content; final Home source packet decides |
| CI-10 | Shopify product media | Project-owned store is the authorized source; exact production catalog/media has not been audited in P3-00 | Available source, runtime content still requires merchant policy | Merchant confirms allowed hosts, ownership, aspect/crop and alt-text policy | P3-01/P3-02/P3-04 | P3-01 blocked until source/policy is in merchant packet; unsafe/unowned media omitted |
| CI-11 | Home merchandising sections | No approved collection handles/query/source order/labels/fallback | **P3-01 external input blocker** | Merchant supplies exact source, order, TR/EN label and empty behavior for every section | P3-01 | P3-01 cannot start; no generic “featured” or promotion is invented |
| CI-12 | Category taxonomy | Shopify collections exist as a capability, but intended hierarchy/visibility/naming is not approved | External merchant input | Merchant supplies hierarchy, market visibility, labels and unmapped-collection treatment | P3-02 | Category/list implementation stops |
| CI-13 | Filter/sort vocabulary | Storefront schema supports capabilities, but merchant-supported controls are not approved | External merchant/schema input | Merchant plus current Storefront schema review | P3-02 | Unsupported/dead controls must remain absent |
| CI-14 | Related-product source | Not approved | Optional external merchant input | Merchant identifies a Shopify-native owned source or the feature remains absent | P3-04 | No blocker for core detail; related section omitted |
| CI-15 | Product claims/reviews/badges | No approved review or marketing-claim source | Prohibited unless supplied by owned source | Merchant/legal owner | P3-04/P3-16 | Fake reviews, discounts, scarcity and delivery claims remain absent |
| CI-16 | Cart note policy | Reference contains a note capability, but no owned merchant purpose exists | P3-00 decision: not in Phase 3 | New product decision required to reopen with bounds/privacy/lifecycle | P3-06 | No P3-06 blocker; note UI/operation absent |
| CI-17 | Quantity/availability wording | Server validation is authoritative; exact merchant maximum/purchase policy is not approved | External merchant policy | Merchant defines user-facing limits; engineering preserves server reconciliation | P3-06 | P3-06 must use safe server-driven bounds/error copy and cannot invent stock limits |
| CI-18 | Supported market(s) | Not documented in tracked planning; paid/unlaunched store status is not a market commitment | **P3-01 external input blocker** | Project owner/merchant supplies market list/default/switching policy | P3-01/P3-02/P3-06/P3-09/P3-11 | Pricing, cache partition, account and address behavior remain ambiguous |
| CI-19 | Currency display | No approved final market/currency policy; use server-returned money only | **P3-01 external input blocker** | Merchant supplies allowed currencies and price-display/tax wording | P3-01/P3-02/P3-06/P3-16 | Home/catalog implementation and release stop if presentation can mislead |
| CI-20 | Locale support | Turkish baseline and English resource set exist | Available owned technical input | Project owner confirms both are supported product locales; engineering keeps parity | P3-01/P3-16 | P3-01 can use TR/EN; final release stops on incomplete production translations |
| CI-21 | Production app copy | Only foundation/proof strings exist; production screen/error/empty/help copy does not | Implementation content work, not permission to invent policy/claims | Android design/content owner creates TR source and reviewed EN fallback per slice | Every slice | Owning slice stops until its functional copy is reviewed; legal/merchant content remains external |
| CI-22 | Support contact and channel | Merchant-owned public contact page and Shopify contact information verified; canonical app route is `https://gurbakir.com/pages/contact` | P3-08 source adopted under active owner authorization | Merchant/site operations owns hosted content; application owns packaged route metadata | P3-08/P3-13/P3-16 | P3-08 current baseline complete; deletion process remains a separate P3-13 decision |
| CI-23 | Privacy policy | Merchant Shopify privacy policy verified at its canonical HTTPS route; source states 2026-04-26 update | P3-08 source adopted as `gurbakir-legal-baseline-1` on 2026-08-11 | Merchant/Shopify policy owner; application owns exact route and baseline metadata | P3-08/P3-16 | P3-08 complete; hosted revisions remain externally owned and replaceable |
| CI-24 | Terms of service | Merchant Shopify terms verified at its canonical HTTPS route; source states 2026-04-26 update | P3-08 source adopted as `gurbakir-legal-baseline-1` on 2026-08-11 | Merchant/Shopify policy owner; application owns exact route and baseline metadata | P3-08/P3-16 | P3-08 complete; no in-app legal acceptance state is invented |
| CI-25 | Shipping policy | Merchant Shopify shipping policy verified at its canonical HTTPS route; source states 2026-04-26 update | P3-08 source adopted as `gurbakir-legal-baseline-1` on 2026-08-11 | Merchant/Shopify policy owner | P3-08/P3-16 | P3-08 complete; application does not restate or extend hosted delivery promises |
| CI-26 | Returns/refunds/cancellation policy | Merchant Shopify refund/return policy verified at its canonical HTTPS route; source states 2026-04-26 update | P3-08 source adopted as `gurbakir-legal-baseline-1` on 2026-08-11 | Merchant/Shopify policy owner | P3-08/P3-16 | P3-08 complete; application summary creates no additional promise |
| CI-27 | Account-deletion instructions | No functional process/resource/operator/retention/SLA | External release-critical blocker | Project owner with legal/privacy and operations owner | P3-13/P3-16 | Group 17 stays blocked and production release stops |
| CI-28 | Permission purposes | Notifications deferred; no other new dangerous permission is justified by accepted Phase 3 screens | P3-00 decision: request contextually only when a feature with approved purpose exists | Product/privacy owner for any future permission | Per owning slice/P3-16 | Unjustified permission cannot be added or released |
| CI-29 | Address countries/fields | Turkey-only create/edit policy implemented from the established Turkey/TRY market, current Customer Account schema, and merchant Shopify country data; existing non-TR addresses remain display-only except explicit non-default deletion | Resolved for P3-11; worldwide behavior remains unsupported | Active owner market policy plus current Shopify Customer Account schema and merchant-owned country metadata | P3-11 COMPLETE at its implementation checkpoint | Fixed `TR`; first/last name, address line 1 and city required; company, address line 2, five-digit zip and E.164 phone optional; no invented province/zone selector |
| CI-30 | Order/fulfillment status vocabulary | Originally not approved; the active owner policy superseded that automatic blocker. P3-12 now maps only current Customer Account API enum values to conservative TR/EN labels; unknown values remain generic and no ETA or merchant promise is inferred | Resolved for P3-12 from current primary Shopify schema plus active owner authorization | Application owns reversible labels; Shopify owns returned status values | P3-12 COMPLETE at its implementation checkpoint | No invented status, timeline, ETA, or fulfillment completion; unknown remains explicit |
| CI-31 | Carrier allowlist/support fallback | Originally not approved; the active owner policy superseded that automatic blocker. P3-12 accepts only normalized HTTPS URLs on official PTT, Yurtiçi Kargo, Aras Kargo, and Sürat Kargo domains/subdomains; all other or absent links fall back to the owned support route | Resolved for P3-12 from official carrier domains, existing P3-08 support baseline, and active owner authorization | Application owns the exact fail-closed URL policy; carriers own hosted tracking content | P3-12 COMPLETE at its implementation checkpoint | No arbitrary URL, redirect promise, tracking synthesis, or unsupported-carrier launch |
| CI-32 | Customer profile fields | Earlier P3-00 state proved minimum identity only; P3-10 now implements the current Customer Account 2026-07 `firstName`/`lastName` allowlist under the active owner policy | Resolved for first/last name by current primary schema and active owner authorization; any expansion remains a separate privacy/schema decision | Active owner policy plus current Shopify Customer Account schema | P3-10 COMPLETE at `f0d01a5` | Email, phone, birthdate, metafield, marketing permissions, and inferred fields remain excluded; SR-03 still governs any future birthdate/metafield request |
| CI-33 | Customer Account hosted copy | Hosted/passwordless TR/EN launch, progress, cancel, expiry, retry and help copy implemented; manual owned-environment launch/cancel verified without credential submission | P3-09 implementation input complete | Application owns launch/return/error copy; Shopify owns hosted identity copy and journey | P3-09 | Account baseline complete; app never implies passwords, app-owned verification, or registration |
| CI-34 | Legal/support page rendering | Six exact merchant-owned HTTPS routes packaged in a versioned local index; AndroidX Custom Tabs selected | P3-08 implementation input complete | Application owns exact host/path allowlist, metadata and return behavior; merchant owns hosted accessibility/content | P3-08 | Local index remains available offline; external failure is explicit; no generic WebView, arbitrary URL or placeholder |
| CI-35 | Update message and store URL | No approved release copy/listing URL/version owner | External release input | Release owner supplies packaged TR/EN copy, owned listing and rollback policy | P3-14/P3-16 | Optional notice omitted; hard gate prohibited |
| CI-36 | Production store/service identity | Only authorized non-production Shopify/Firebase inputs exist locally; no production flavor/package/services | External release-security blocker | Release owner plus project owner securely provisions after approval | P3-16 | Production release stops; non-production values are not cloned blindly |
| CI-37 | Play listing/screenshots/marketing | None approved | External release asset/content blocker | Release owner/project owner with owned media and policy review | P3-16 | Store submission stops |
| CI-38 | Synthetic proof data cleanup | One paid synthetic test order is retained as evidence and one possible abandoned cart may expire; proof UI/data must not ship | Existing evidence plus release cleanup obligation | Engineering/release owner | P3-07/P3-09/P3-13/P3-15/P3-16 | Release stops if synthetic identities/carts/orders/labels/config appear in app/artifact/logs; historical evidence itself is not deleted casually |

## P3-01 merchant input packet

P3-01 is blocked until the project owner/merchant supplies one reviewable packet containing:

1. market IDs/countries, default market, whether switching is supported, currency display/tax wording and language-market relationship;
2. each Home section's stable identifier, source (owned collection handle/query), order, Turkish label, English label, item limit and empty fallback;
3. permitted Shopify media hosts, crop/aspect/focal/alt-text rules and confirmation that only project-owned catalog media is used;
4. acceptance of the text wordmark plus current P3-00 token/system-font baseline for P3-01, with final logo/icon/marketing assets explicitly deferred to P3-16.

No token, private identifier, secret, live customer value, or production credential belongs in that packet.

## Legal/support content record required for P3-08

For every mandatory page, record: page ID, owner, canonical HTTPS URL, supported language(s), title/summary resources, version, effective date, last review, update/rollback process, cache/offline policy, required acknowledgements (normally none unless legally approved), contact/escalation route and accessibility owner. P3-00 defines the schema only; it does not populate fake values.

### P3-08 adopted record (2026-08-11)

The active owner policy authorized verified merchant/Shopify content as the current application baseline. Public storefront routes and the authenticated Shopify Admin policy inventory were inspected without mutation. All records use packaged integration version `gurbakir-legal-baseline-1`, adoption/review date `2026-08-11`, Turkish/English application labels, no required acknowledgement, no hosted-content cache, and the merchant-owned contact page as the escalation route. Shopify policy pages report source update date `2026-04-26`; the support page exposes no separate source-effective date. The app packages only metadata and an exact allowlist, so a hosted revision requires metadata review but no application architecture change. Rollback is the P3-08 implementation commit. Hosted content and accessibility remain merchant/Shopify-owned; application index accessibility remains Android-owned.

| Page ID | Canonical HTTPS route | Source/owner | Offline and update policy |
|---|---|---|---|
| `SUPPORT` | `https://gurbakir.com/pages/contact` | Merchant public page / site operations | Local title and summary remain; external content is unavailable honestly when offline |
| `PRIVACY` | `https://gurbakir.com/policies/privacy-policy` | Shopify policy / merchant policy owner | No cached legal body; exact route and source date are packaged |
| `TERMS` | `https://gurbakir.com/policies/terms-of-service` | Shopify policy / merchant policy owner | No cached legal body; exact route and source date are packaged |
| `SHIPPING` | `https://gurbakir.com/policies/shipping-policy` | Shopify policy / merchant policy owner | No cached legal body; application makes no separate delivery promise |
| `RETURNS` | `https://gurbakir.com/policies/refund-policy` | Shopify policy / merchant policy owner | No cached legal body; application makes no separate return/cancellation promise |
| `LEGAL_NOTICE` | `https://gurbakir.com/policies/legal-notice` | Shopify policy / merchant policy owner | No cached legal body; exact route and source date are packaged |

## Market and merchant assumptions that are not facts

- The project name, Turkish baseline and Europe/Istanbul workstation do not prove a Turkey-only production market.
- Store plan status does not prove production readiness, public launch, currency, tax, shipping, return, address or catalog policy.
- Existing Shopify products/media are an owned potential source, not automatic Home merchandising approval.
- Reference app brands, content, categories, Worker endpoints and Remote Config values provide no Gürbakır merchant input.

These items remain explicit external inputs and cannot be converted into implementation assumptions.
