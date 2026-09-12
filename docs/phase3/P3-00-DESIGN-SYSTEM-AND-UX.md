# P3-00 Design System and UX Baseline

Date: 2026-08-10

Design review: **PASS for implementation specification**

Brand/release review: **OPEN for final logo, app icon, marketing imagery, merchant content, and legal/support content**

This baseline approves a reversible, independently designed production system built from existing project-owned Phase 2 tokens and Material 3 behavior. It does not claim that a final brand kit or marketing identity exists. Reference-application layouts, branding, assets, copy, and remotely supplied values were not used.

## Input classification

| Classification | Inputs and treatment |
|---|---|
| Approved existing project input | Product name `Gürbakır`; TR baseline and EN resources; Phase 2 light/dark color roles; 4/8/16/24 spacing; 8/12/20 corner system; system font fallback; typed brand/theme contracts; Material icons; project-owned Shopify product media subject to merchant/provenance review |
| P3-00 proposed and accepted decision | Calm editorial commerce direction; local-first state behavior; semantic Material 3 components; adaptive window layouts; color/contrast baseline; Turkish-first copy rules; component/state/motion/accessibility contracts below |
| Permitted planning fallback | Text-only Gürbakır wordmark, Material icons, geometric/skeleton surfaces, and empty media placeholders without marketing claims |
| External asset/content blocker | Final logo, launcher/store icon, splash artwork, hero/editorial imagery, campaign copy, final merchant Home sections, legal/support/deletion text and URLs, shipping/returns/refund policy, production store/listing identity |

## Visual principles

1. **Product first.** Merchandise, price, availability and selected variant receive stronger hierarchy than decoration.
2. **Warm restraint.** Deep green, warm copper and ivory create an understated craft/material character already present in the project-owned token set; no jewelry or reference-brand motif is inferred.
3. **Honest state.** Loading, empty, stale, unavailable, error, offline and destructive outcomes are visually distinct and never represented by fake products or claims.
4. **One clear action.** Each surface has a clear primary action, optional secondary action, and no disabled placeholder for an unimplemented destination.
5. **Accessible by construction.** Use semantic Material components, scalable text, visible focus, large targets, non-color state cues and predictable traversal before custom drawing.
6. **Owned content only.** Product/collection content comes from the project-owned Shopify environment; app copy comes from reviewed TR/EN resources; legal/marketing content is never invented.

## Color roles and verified contrast

The existing `GurbakirBrand` token values become the P3-00 implementation baseline. They are not a final marketing-brand claim and may be revised through an approved token-only change when the owner supplies a brand kit. The paired foreground/background ratios below were calculated from the tracked ARGB values using WCAG relative luminance.

| Role pair | Light | Ratio | Dark | Ratio | Use |
|---|---|---:|---|---:|---|
| Primary / on-primary | `#173F35` / `#FFFFFF` | 11.67:1 | `#B6CCBF` / `#21372F` | 7.49:1 | Primary buttons, selected navigation, important highlights |
| Primary container / on-container | `#D7E7DF` / `#09251E` | 12.65:1 | `#344E44` / `#D7E7DF` | 7.06:1 | Selected chips, calm status containers |
| Secondary / on-secondary | `#8B5E34` / `#FFFFFF` | 5.60:1 | `#FFB77B` / `#4C2706` | 7.69:1 | Limited secondary emphasis, not competing CTAs |
| Background / on-background | `#FFFBF5` / `#201A17` | 16.68:1 | `#18120F` / `#EDE0DA` | 14.37:1 | App background and primary text |
| Surface / on-surface | `#FFFBF5` / `#201A17` | 16.68:1 | `#18120F` / `#EDE0DA` | 14.37:1 | Cards, sheets, fields and standard text |
| Error / on-error | `#BA1A1A` / `#FFFFFF` | 6.46:1 | `#FFB4AB` / `#690005` | 7.72:1 | Errors and destructive confirmation, never color alone |

Text must meet WCAG 2.2 AA: at least 4.5:1 for normal text and 3:1 for large text. Active control boundaries, icons conveying state, focus indicators and charts target at least 3:1 against adjacent colors. Disabled controls remain recognizable by shape/label/context and are not used as roadmap placeholders.

## Typography

- Use the Android/Material 3 system sans-serif stack; the current brand font token remains `null`. Do not bundle or name a final typeface without license/provenance approval.
- Roles: `display` only for short owned campaign headings when content exists; `headline` for screen/section hierarchy; `title` for cards/dialogs; `body` for product/legal/support copy; `label` for controls, chips and metadata.
- Default body text is at least 16sp. Do not encode essential hierarchy through size alone; pair size with semantic heading and spacing.
- Respect user font scaling through at least 200 percent without clipped actions, overlapping text, missing price/status, or two-dimensional scrolling for ordinary content.
- Use tabular-number styling only when a future approved font supports it; correctness and locale-aware currency formatting outrank alignment.

## Spacing, shape and surface system

- Existing tokens: compact `4dp`, normal `8dp`, generous `16dp`, section `24dp`. Larger gaps use integer combinations (`32dp`, `48dp`) rather than new unexplained tokens.
- Screen horizontal padding: 16dp compact width; 24dp medium; content is centered/max-width constrained on expanded layouts.
- Minimum interactive target: 48dp by 48dp. Visual icons may be smaller within that target.
- Corners: 8dp controls/chips, 12dp cards/fields, 20dp large sheets/hero containers. Full-pill shape is reserved for filters/status chips and does not replace the token hierarchy.
- Prefer tonal surface separation, dividers and whitespace. Elevation is limited to navigation/app bars, modal sheets/dialogs and transient overlays. Scrolling content does not become a wall of elevated cards.
- Edge-to-edge layouts handle system bars/insets and IME without hiding focused fields or checkout/account return actions.

## Iconography and imagery

- Use licensed Material icons with platform-familiar meaning. Every actionable icon has a localized label/content description or is merged into a labeled control. Decorative icons are hidden from accessibility services.
- Do not use the current generic launcher foreground as final release identity. It remains an engineering asset pending the owned icon package.
- Product/collection media may come only from the project-owned Shopify catalog and allowed HTTPS media hosts. Record source, crop/focal behavior, aspect ratio, format/size limit and meaningful description policy in the owning slice.
- Product cards use a consistent aspect ratio selected by the merchant media audit; `1:1` is the implementation fallback, not a crop promise. Use center-inside/fit for product truth; do not crop away the product to satisfy decoration.
- Skeletons use neutral tonal blocks with no text/image simulation that could be mistaken for real content. Broken/unsafe media shows a neutral fallback and retains accessible product text.
- No reference, third-party reference/legacy prototype brand, unlicensed stock, invented campaign, fake review badge, fake discount, fake shipping promise, or text embedded in imagery.

## Component inventory

| Family | Required variants and states | Earliest slice |
|---|---|---|
| App shell | Compact bottom navigation; medium/expanded rail; top app bar; contextual cart action after P3-06 | P3-01/P3-06 |
| Brand mark | Text wordmark; final owned logo slot when provided | P3-01/P3-16 |
| Content hierarchy | Screen title, section header, supporting copy, metadata row, divider | P3-01 |
| Product | Product card/list row, media tile, price block, availability/status, option group, variant selector | P3-01/P3-04 |
| Discovery | Category tile, horizontal section, product grid/list, pagination footer | P3-01/P3-02 |
| Search/filter | Search field, history row, filter chip, checkbox/radio group, sort selector, modal bottom sheet | P3-02/P3-03 |
| Wishlist | Stateful favorite action, local-only message, unavailable saved-item row | P3-05 |
| Cart | Line item, quantity stepper, remove confirmation, totals block, ownership/recovery banner, checkout action | P3-06/P3-07 |
| Account | Signed-out/sign-in card, account menu row, private-data skeleton, session-expiry message | P3-09 |
| Forms | Text field, dropdown/autocomplete only when justified, inline error, destructive confirmation, save progress | P3-10/P3-11/P3-13 |
| Orders | Order summary row, fulfillment group, status/timeline, tracking action | P3-12 |
| System state | Skeleton, inline progress, full-screen first-load state, empty state, offline banner, error block, snackbar, route recovery | P3-01 |
| External context | Browser/Checkout launch explanation, safe return state, owned-page metadata | P3-07/P3-08 |
| Update policy | Optional update notice and separately gated hard-update screen | P3-14 |

Every component has default, pressed/focused, selected where meaningful, loading, disabled only for momentary action validity, success/confirmation where meaningful, recoverable error, high-contrast/dark, large-text, long-TR/EN and TalkBack semantics states. An unimplemented feature is absent, not disabled.

## Interaction and feedback

- Prefer immediate local response followed by authoritative reconciliation. Do not optimistically claim checkout completion, account deletion, profile/address save, or cart ownership change.
- Single-item, reversible local actions such as wishlist toggles can update immediately and roll back visibly on local failure.
- Remote mutations disable only the affected action while pending. Other safe navigation remains available.
- Use inline error near the affected content or field. Use snackbar for brief non-blocking confirmation with an undo only when undo is real. Use dialog for irreversible/destructive confirmation and sheet for bounded selection.
- Duplicate taps are idempotently ignored or coalesced. Debounce applies to search input, not to primary accessibility activation.
- Focus moves to the first invalid field, returned screen heading, newly shown error, or originating control as appropriate. Do not announce every loading-frame change.

## Loading, empty, offline and error patterns

- First load: stable skeleton matching final layout, then content or a single typed empty/error state.
- Pagination: keep prior items and show footer progress/retry. Never replace a valid list with a full-screen spinner.
- Empty: state what is empty and why when known; offer only a functional next action. Wishlist says device-local; filtered lists offer clear filters; cart offers continue shopping.
- Offline: distinguish no cache, approved stale cache, and mutation-unavailable. Show last-updated only when it is accurate and useful.
- Recoverable error: concise cause category, safe retry, retained valid state. Terminal error: safe exit and support/legal route when relevant; no raw server/SDK text.
- Quarantined cart: do not render customer-associated lines as anonymous. Offer re-authenticate, retry detachment, or explicitly discard according to P3-06 policy.

## Destructive, disabled and unavailable behavior

- Confirm address deletion, wishlist/history full clear, eligible cart discard, logout consequences when cart detachment matters, and deletion request submission.
- Confirmation names the exact object/data class, immediate local effect, remote uncertainty and recovery/support path. Avoid coercive color/copy.
- Disabled state is used only for a currently invalid or pending action and retains a discoverable explanation. Unsupported, blocked, deferred, or not-yet-implemented destinations are absent.
- Unavailable product/variant/tracking uses label plus icon/structure, never opacity or color alone.

## Motion

- Motion explains hierarchy/state rather than decorates: short fades for content replacement, shared-axis transitions for sibling navigation, container transform only when it preserves product context, and progress for real pending work.
- Default durations target approximately 100–200ms for micro feedback and 200–300ms for screen/container transitions; do not delay interaction to finish animation.
- Respect platform reduced-motion/animator settings. Reduced mode removes parallax, auto-scrolling and nonessential scale; state changes remain understandable without animation.
- Skeleton shimmer is optional and off/reduced under reduced motion; a static tonal skeleton is sufficient.

## Responsive/adaptive behavior

- Layout decisions use current app-window width classes, not device-type checks. Compact uses one pane and bottom navigation. Medium may use a rail and wider grids. Expanded may use a rail plus list-detail for categories/products/orders when it improves continuity.
- Width class can change during split screen, resize or fold/unfold; selection and navigation state are hoisted and preserved.
- Product grids adapt column count to minimum card width; controls and text do not shrink below accessibility requirements.
- Forms use a readable max width. Checkout remains the SDK-hosted surface. External pages remain external regardless of screen size.
- Test compact, medium and expanded widths, landscape/short height, IME, split screen and 200 percent text. The current Android guidance is [Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes) and [Build adaptive apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps).

## Accessibility baseline

- Meet WCAG 2.2 AA for contrast and reflow where applicable. Current primary references: [WCAG 2.2](https://www.w3.org/TR/WCAG22/), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility), and [Android accessible-app guidance](https://developer.android.com/guide/topics/ui/accessibility/apps).
- Minimum touch target 48dp. Use Material components and correct semantics before custom controls.
- TalkBack: meaningful names/roles/states/actions, semantic headings, merged cards only when one action, deterministic traversal, live-region announcements only for important completed changes.
- State is never color-only. Focus indicator is visible. Error text is associated with its field/action.
- Support 200 percent font scale, content reflow, keyboard/switch access, portrait/landscape, dark theme, reduced motion and screen-magnification use.
- Images use meaningful descriptions when informative and null/decorative semantics otherwise. No required text exists only in an image.
- Each slice adds semantic assertions and focused manual TalkBack checks; P3-15/P3-16 cover complete critical journeys.

## Turkish-first localization and content resilience

- `res/values` remains Turkish and `values-en` remains English. Both sets must have key parity. Turkish copy is the product-writing authority; English is a reviewed fallback, not a machine placeholder.
- Use Android per-app language behavior and resource resolution. On Android 13+, languages should be discoverable in system App Languages when P3-01 deliberately enables the tracked configuration. An in-app language selector is not required for P3-01; if later added, it uses platform/AndroidX APIs rather than a parallel custom store. Current reference: [Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages).
- Locale and market are separate. Changing language does not silently change market, currency, buyer identity or cart ownership.
- Resource formatting owns plurals, dates, currency and quantities. Do not concatenate translated sentences or hardcode Turkish casing.
- Design for Turkish expansion, English resource fallback, pseudolocale expansion, long product names, unbroken order/variant identifiers and narrow widths. Lines wrap; essential actions do not clip.
- Hosted Customer Account and Checkout content remains Shopify-owned; app launch/return/help copy accurately describes that boundary. Current Shopify sources are [Customer Account API getting started](https://shopify.dev/docs/storefronts/headless/building-with-the-customer-account-api/getting-started), [Customer Account API reference](https://shopify.dev/docs/api/customer/2026-01), [Shopify customer accounts](https://help.shopify.com/en/manual/customers/customer-accounts/new-customer-accounts), and [Checkout Kit](https://shopify.dev/docs/storefronts/mobile/checkout-kit).

## Analytics policy

No production Analytics or Crashlytics is part of Phase 3. Screen names, product views, search queries, cart events, identity, errors, addresses, orders and external-page activity are not collected by default. Test tags and local redacted engineering diagnostics are not an event taxonomy. Any future telemetry requires a separate product/privacy/retention/deletion/owner decision and updated design copy/controls.

## Design review evidence

P3-00 design review passes because every production surface has a state contract, the existing token pairs exceed required text contrast, component families and adaptive behavior are defined, accessibility/localization/motion/error/destructive rules are testable, permitted assets are enumerated, and missing final content/assets are explicitly owned. The PASS is for implementation readiness, not final brand approval or production readiness.
