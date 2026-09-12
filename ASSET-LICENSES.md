# Asset Rights and Publication Manifest

Publication gate: RESOLVED

This manifest separates repository source licensing from rights to publish
brand assets, screenshots, and third-party material. The concrete Gürbakır
application remains unchanged; the only excluded material is non-runtime
historical screenshot evidence whose visible catalog-image redistribution
rights were not affirmatively established.

| Path | Material | Disposition | Required evidence or action |
|---|---|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Gürbakır launcher/brand artwork | `excluded from Apache grant, publication permitted` | The Gürbakır owner authorized public repository distribution on 2026-09-13; trademark and brand-asset rights remain reserved |
| `app/src/main/res/drawable/*.xml` except `ic_launcher_foreground.xml` | Licensed Material UI icon vectors used by the Gürbakır application | `third-party Apache-2.0` | Repository history records their project introduction in the approved Material-icon baseline; retain the notice in `THIRD_PARTY_NOTICES.md` |
| `mobile-core/src/main/res/drawable/*.xml` | Shared Material UI icon vectors moved unchanged from the Gürbakır implementation | `third-party Apache-2.0` | Repository history records the move into `:mobile-core`; retain the notice in `THIRD_PARTY_NOTICES.md` |
| `apps/synthetic/src/main/res/drawable/ic_gate2_synthetic_launcher.xml` | Synthetic conformance launcher artwork | `publishable under Apache-2.0` | Project-created geometric vector introduced with the synthetic application in commit `55bebf09c3df8d72a6d0d2299757eeb4bc8bed75` |
| `docs/testing/evidence/pre-cp02-android-readiness-2026-08-25/*.png` | Six historical Gürbakır application screenshots containing catalog/product imagery | `private only; excluded from public tree` | Public redistribution rights for the embedded catalog imagery were not affirmatively established; original files remain in the unchanged private historical repository and its verified private archive |
| `app/src/main/res/values*/strings.xml` | Gürbakır brand name and application copy | `excluded from Apache grant, publication permitted` | The Gürbakır owner authorized public repository distribution on 2026-09-13; trademark rights are not granted |

No tracked font, audio, video, APK, AAB, signing file, raster application asset,
or other binary application resource remains in the public candidate. The six
removed PNGs are documentation evidence, not runtime/application assets.
Dependency code and assets are not relicensed; see `THIRD_PARTY_NOTICES.md` and
the upstream dependency licenses.

Gürbakır marks and designated brand assets remain outside the repository's
Apache-2.0 grant even though their public distribution in this repository is
permitted. This disposition does not authorize changing the concrete Gürbakır
application or its runtime identities and behavior.
