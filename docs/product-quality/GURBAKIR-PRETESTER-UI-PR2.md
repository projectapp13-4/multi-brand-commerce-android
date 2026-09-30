# Gür Bakır pre-tester UI refinement

Status: **implementation and acceptance in progress** (2026-09-30). This is
the second pull request of the approved closed-test candidate work. It builds
on merged platform PR #25 at `232a0f451808d6f7921593eef458d2c0b554ae95`.
It does not establish a Play-distributed artifact or public production readiness.

## Product decisions

- Keep the application-owned Gür Bakır presentation tokens, Turkish-first
  resources, existing destination graph, and provider-neutral shared code.
  No Trial asset, credential, or runtime brand switch is introduced.
- Present product media before title and price, while retaining live
  Storefront-owned price, availability, variant, cart, and checkout truth.
- Keep the owned Home v2 portrait video manual-play and bounded. The play
  action is centered on its poster; playback retains source proportions and
  returns to the poster after completion.
- Reflow Categories, product grids, navigation labels, and money rows for
  large text and narrow windows. All primary destinations remain accessible.
- Give order cards a calmer hierarchy. Keep fulfillment, totals, tracking
  allowlisting, and support recovery visible and truthful.
- Reduce the default density of Help and Policies. Each page still exposes
  its source class, source/adoption date, and adopted baseline version through
  an explicit disclosure. Owned browser links and failure recovery retain
  their established security boundary.
- Set the candidate to `versionCode=2` and `versionName=0.2.0`.

## Rendered inspection

The installed Xiaomi base APK is the previously hash-matched, authenticated
exact-main build. Its screenshot is readable. HyperOS rejected installation of
the matching analysis-only AndroidJUnitRunner test APK with
`INSTALL_FAILED_USER_RESTRICTED`; no base APK, app data, or session was changed.
That leaves live private-screen visual acceptance open.

The first API 30 automated test device returned black frames for its launcher
as well as the application. A standard API 36 Play Store emulator with software
GPU rendering produced readable captures. This distinguishes the capture
failure from an application secure-window or Firebase restriction. The
configured production-debug candidate on that emulator read the selected
Shopify Home v2 root and rendered the owned video, collection grid, and
featured product.

The local visual package covers Home, Categories, Search with keyboard,
Product, empty and locally populated Wishlist, empty Cart, signed-out Account,
Help, dark appearance, large text, and timed video playback. The populated
Wishlist was verified on the configured production-debug emulator with a
Storefront product added to its device-local list; no remote customer or cart
state was changed. The 10-second video was captured at
00:06 and 00:09, then returned to its poster without an error state. Nine
synthetic Compose fixtures cover authenticated Account, Profile, Address list
and form, Order list and large-text detail, populated Wishlist and Cart, and
deletion. The first device-level fixture screenshots were obscured by an
emulator system dialog; clean Compose-root captures replaced them. Inspection
found verbose implementation-facing copy on Profile, Addresses, Orders, and
Cart. The revised copy keeps editable-field, market, session, privacy, and
checkout limits while bringing the useful action into view sooner. These
fixtures check layout and copy; they do not prove a live Customer Account
readback.

## Verification and remaining gates

Focused production-debug emulator instrumentation passed the legal/support
suite (3 tests), the Home/navigation/catalog/product/video suite (32 tests),
and the Account/Cart/Order/Search/Wishlist suite (38 tests). The broad local
formatting, detekt, JVM, Android test-APK, and Gür Bakır/Trial/Synthetic build
command passed on 2026-09-30: 460 tasks, 202 executed, 28 cached, 230
up-to-date. After the copy revision, formatting and production-debug/core
test-APK builds passed; five affected Compose fixtures passed and were
recaptured. The synthetic Cart fixture was corrected so quantity, line total,
and summary total agree; its nine-test suite passed and the image was
recaptured. Gür Bakır locale instrumentation passed 4 tests and core resource
instrumentation passed 3 tests. Exact-head CI, API 23/API 30 lanes, private
Xiaomi acceptance, and Play-distributed acceptance are not yet claimed.

The selected v1 Home root remains an available rollback target. The merchant
v2 root and its three children remain bounded Shopify content; editing their
supported content does not require an Android release. Tester configuration,
invitations, and the 14-day process remain outside this workstream.
