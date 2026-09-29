# Gür Bakır pre-tester platform alignment

Status: **implementation in progress** (2026-09-29). This record covers the first
of two planned pull requests for the closed-test candidate. It does not claim
public production readiness or Play-distributed acceptance.

## Starting evidence

- Source started from `5efa4b820031e9c86af023212bfab32117cfb95a`.
  A fresh exact-source `productionDebug` rebuild produced SHA-256
  `4B277D138561C8EF4A43BD73967C4D02FAD27FF063781437D944BD8544AAF45A`,
  matching the installed Xiaomi `com.gurbakir.mobile.debug` base APK.
- The existing AndroidJUnitRunner/Compose test companion was built for the
  installed package and matching signer. HyperOS refused installation with
  `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. No test action ran;
  the base APK, app data, and authenticated session were left intact. Private
  Xiaomi visual acceptance remains open.
- The historical dirty primary checkout was archived outside Git, its tracked
  and untracked file hashes passed a disposable restore drill, and the canonical
  checkout was fast-forwarded cleanly to current `origin/main`. Obsolete tracked
  Trial-like changes were not reapplied. Current ignored development/staging
  inputs were revalidated and copied into the isolated implementation worktree.

## Bounded client changes

- Gür Bakır alone selects `mobile_home_v2/primary` with schema version 2.
  The shared v2 contract stays typed and finite. The existing
  `mobile_home/primary` root remains untouched; a rollback can select the
  still-supported v1 tuple in the application configuration.
- Video source selection now accepts MP4 renditions in either orientation up
  to 1280 pixels on either axis and 921,600 total pixels. The validator,
  renderer, and playback coordinator use one policy. The manual-play viewport
  is bounded from 220 to 480 dp high and retains the source proportions.
- The app-owned `GurbakirBrand` tokens remain the presentation seam for
  PR 2. Shared modules receive neutral tokens and typed Home content; no
  brand-name switch, provider credential, or arbitrary native component enters
  remote content.
- Gür Bakır supports Turkish as its selected locale. The shared English
  resources and Trial's separate locale policy remain available to their
  existing consumers.
- Only live collection paths under `gurbakir.com/collections/` are declared
  for verified App Links. HTTP and HTTPS collection links reach the same typed
  route. Product and order URLs remain typed internal routes but are not
  declared OS web defaults while their sampled site paths return 404.
- No cart warning or ownership change is justified by the blocked Xiaomi
  instrumentation result. Existing cart ownership is preserved.

## External and acceptance gates

- Shopify currently serves `[]` at `/.well-known/assetlinks.json`.
  Association with the exact Play App Signing certificate and device
  verification remain pending.
- The live website mobile hero maps to an owned Shopify Video file ending in
  `40448681705711`. A separate Gür Bakır v2 Home definition, child records,
  and root will be created and validated before selecting the root last.
  The current v1 root remains the provider rollback point.
- Focused tests, broad Multi-Brand regression, exact-head CI, configured
  Storefront readback, and PR review/merge evidence must be added before this
  record is marked complete.
