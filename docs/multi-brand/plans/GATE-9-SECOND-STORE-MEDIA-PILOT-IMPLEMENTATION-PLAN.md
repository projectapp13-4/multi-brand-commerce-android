# İkinci Mağaza, Sınırlı Medya ve İşletim Hazırlığı — Onaylı Uygulama Planı

> **Uygulama otoritesi:** Proje sahibi bu planı 2026-09-17 tarihinde, son üç teknik
> netleştirme ile birlikte uygulama için onayladı. Bu dosya kullanıcı mesajındaki
> onaylı kapsamı kalıcılaştırır; merge, Play yükleme/yayını, production signing,
> gerçek müşteri/sipariş/ödeme, Gürbakır merchant içeriğine yazma veya yıkıcı işlem
> yetkisi vermez.

**Durum:** Uygulama için onaylı  
**Plan tarihi:** 2026-09-17  
**Repository:** `projectapp13-4/multi-brand-commerce-android`  
**Exact taban:** `5c402241e22a45001bdc57da6d08a83d2ce407cc`  
**Çalışma dalı:** `codex/gate9-second-store-media-pilot`  
**İzole worktree:** `C:\src\projects\multi-brand-commerce-android-gate9-pilot`

## Amaç

Mevcut tek-repo/uygulama-modülü mimarisinde ikinci gerçek mağaza kanıtı olarak
development-only `:trial` uygulamasını kurmak; önce gerçek Trial Home/Menu v1
checkpoint'i, sonra sınırlı Home v2 image/video sözleşmesini ve güvenli Android
runtime'ını teslim etmek; iki uygulama/provider/veri ayrımını kanıtlamak; owner
işletim rehberini tamamlamak; Gate 9 ile P3-16 durumlarını ayrı ve kanıta dayalı
raporlamak.

## Global constraints

- `:app` Gürbakır gerçek mağaza/future production uygulamasıdır; merchant
  içeriğine yazılmaz. `:trial` yalnız development pilotudur ve production'a
  yükseltilmez. `:synthetic` fixture/conformance rolünde kalır.
- Bir repo, her marka için ayrı application module, ortak `:mobile-core`; runtime
  merchant switch, brand flavor veya shared marka koşulu yoktur.
- Kirli ana checkout, ignored girdileri ve eski worktree'ler korunur; stash,
  reset, clean veya kopyalama yapılmaz.
- Sırlar yalnız onaylı Git-dışı yerel depolarda tutulur; token/private key/customer
  session değeri kaynak, rapor, receipt, log veya komut argümanına girmez.
- Trial yazısından önce Shopify canonical domain/numeric shop ID/development
  niteliği ve Firebase hedef kimliği yeniden okunur. Gürbakır provider kontrolü
  salt-okunurdur.
- Gerçek OTP/2FA/onay ekranları atlanmaz. Ödeme, gerçek sipariş/müşteri mutasyonu,
  production publication/signing ve yıkıcı provider işlemi yapılmaz.
- Test-first uygulanır; çalıştırılmayan veya skip edilen sonuç `PASS` değildir.
  Debug instrumentation minified release kanıtı değildir.
- Gürbakır v1 sözleşmesi ve migration-sensitive package/callback/Room/preferences/
  Keystore/Firebase kimlikleri aynen korunur.
- Eski Graphify çıktısı ve referans APK implementation kaynağı olarak kullanılmaz.
  Referans yalnız gözlenen tarihsel tek-mağaza davranışıdır.
- Shopify Storefront, Admin ve Customer Account sözleşmeleri exact `2026-07`
  sürümünde güncel resmî belgeler/şema ile doğrulanır.
- Merge, Play upload/publish ve production signing bu planın parçası değildir.

## Sıra ve kanıt modeli

Sıra değiştirilemez:

1. Temiz worktree, kalıcı plan, exact taban ve ilk sözleşme kaydı.
2. Tam Trial bileşimi.
3. Gerçek Trial Home/Menu v1 release checkpoint'i.
4. Exact v2 schema/query/projection checkpoint'i.
5. V2 mapper, validator, persistence ve player.
6. Trial release v1→v2 ve Gürbakır staging release baseline→candidate.
7. İki-app/provider/device kabulü ve owner rehberi provası.
8. Gate 9 kapanış değerlendirmesi ve ayrı P3-16 tablosu.

Her acceptance kaydı app/profile, başlangıç, işlem, beklenen sonuç, komut/runner,
source SHA, artifact digest ve redakte kanıtı taşır. Durum kümesi:
`PASS`, `FAIL`, `PARTIAL`, `NOT RUN`, `SETUP REQUIRED`, `EXTERNALLY BLOCKED`,
`NOT APPLICABLE`, `PREEXISTING`, `UNCONFIRMED`.

Config/provider provenansı source SHA, config SHA-256, shop domain/ID,
client/callback, Firebase project/app kimlikleri, doğrulama zamanı ve redakte
readback taşır; token değerleri taşımaz. Provider receipt hedef, scope,
before/after, resource ID, idempotence ve recovery içerir.

## Trial application contract

| Alan | Sözleşme |
|---|---|
| Module | `:trial`, fiziksel `apps/trial` |
| Kimlik | app key `trial`; brand key `multi-brand-trial`; ad `Multi Brand Trial`; namespace `com.projectapp134.multibrandtrial` |
| Variant | development release `com.projectapp134.multibrandtrial.dev`; debug `com.projectapp134.multibrandtrial.dev.debug` |
| OAuth | ayrı public mobile client; `shop.<verified-trial-shop-id>.multibrandtrial://oauth/callback`; PKCE S256 |
| Shopify | `multi-brand-trial-store.myshopify.com`; Storefront API `2026-07`; Menu `main-menu` |
| Dil/pazar | `tr,en`; default `tr`; search `tr-TR`; fixed `TR/TRY` |
| Bileşim | `[HOME, CATEGORIES, SEARCH, WISHLIST, ACCOUNT]`; Account ve Firebase enabled |
| Veri | `trial-store-local.db`; ayrı preferences/Keystore/Home partition; backup/transfer disabled |
| Fallback | Trial-only `pilot-koleksiyonu` ve `pilot-urun` |
| Firebase | ayrı Trial-development project; debug/release Android app kayıtları |
| Tracking | `TrackingUrlPolicy.denyAll()`; doğrulanmış taşıyıcı domain olmadan dış URL yok |

Support, Privacy, Terms, Shipping, Returns, Legal Notice ve deletion kaynakları
Trial'a ait olacaktır. Provisional development sayfaları izlenebilir ve
değiştirilebilir olmalı; production hukuki kabulü sayılmaz. Yerel temizleme,
silme talebi ve uzak silme birbirinden ayrı raporlanır. TR/TRY yalnız config ile
değil katalog, sepet ve checkout sonuçlarıyla kanıtlanır.

## Home v2 contract

V1 wire/codec/fixture'ları korunur. V2 mevcut collection-grid ve
featured-product child definition'larını kullanır ve şunları ekler:

| Tip | Exact alanlar |
|---|---|
| `mobile_home_v2` | `schema_version=2`; `declared_section_count` 0–5; `sections:list.mixed_reference` |
| `mobile_home_image_v1` | `title`; `presentation=banner/photo`; Image `media`; `alt_text`; optional `caption`; en çok bir Product/Collection hedefi |
| `mobile_home_video_v1` | `title`; Video `media`; optional Image `poster`; `alt_text`; optional `caption`; en çok bir Product/Collection hedefi |

Kök en çok bir collection grid, bir featured product, iki image ve bir video
kabul eder. İlk contract checkpoint'inin exact dosyaları:

- `config/onboarding/shopify-home-schema.v2.json`
- `config/onboarding/shopify-home-contract.v2.manifest.json`
- `storefront/src/main/graphql/com/gurbakir/storefront/HomeContentV2Metaobject.graphql`
- `storefront/src/main/graphql/com/gurbakir/storefront/HomeV2Resources.graphql`
- `storefront/src/test/resources/home-v2/`

Manifest dosya SHA-256'larını, contract/API sürümünü ve schema-validation kaydını
taşır. `2026-07` Apollo codegen ve gerçek Trial public-client readback'i olmadan
checkpoint `PASS` değildir.

### Tek-dilim projection cutover

`shopify.homeDefinitionContract` şu okuyucuların tamamına aynı derlenebilir
değişiklikte taşınır: projection üreticisi ve `Onboarding.Registry.psm1`,
`app/build.gradle.kts`, `apps/trial/build.gradle.kts`,
`storefront/build.gradle.kts`, `OwnedOnboardingConfiguration.kt`, registry/
projection/build validator'ları, ilgili PowerShell/Kotlin testleri ve
`HomeRemoteSource` implementation/fake tüketicileri. Allowlist sıkılığı
gevşetilmez; eksik, bilinmeyen, yinelenen veya yanlış-case anahtar reddedilir.

Yalnız şu tuple'lar geçerlidir:

- Gürbakır: `(mobile_home, 1, gate7-v1)`
- Trial: `(mobile_home_v2, 2, pilot-media-v2)`

Yabancı tuple provider request veya write öncesi `HOME_CONTRACT_MISMATCH` üretir.
Public arayüz `HomeContentContractId = GATE7_V1 | PILOT_MEDIA_V2` ve
`HomeRemoteSource.ShopifyMetaobject(selector, contractId)` kullanır. Provider-
neutral `RemoteHomeSection.Image` ve `.Video`, kapalı v1/v2 gateway/validator/
codec/UI/operator descriptor'ları ve ayrı v2 receipt/schema dispatch'i eklenir;
v1 receipt zinciri korunur.

### Query, revision ve publication

- Root query raw sıralı section GID'lerini, `references(first:6)`, `pageInfo`,
  child `updatedAt`, exact field type/value/reference ve media alanlarını okur.
- Resource query en fazla 14 benzersiz GID hydrate eder.
- Parent `updatedAt` transit child sürümü değildir.
- `sectionRevisionDigest` child GID/`updatedAt`, normalize alanlar, sıralı
  reference GID'leri ve media source tuple'larından hesaplanır.
- V1/v2 root'ları mutable child paylaşmaz.
- Yeni child/file revision hazırlanıp okunur; root ordered references en son
  değiştirilir. Rollback receipt önceki root/child/file GID ve digestlerini taşır;
  eski kaynaklar otomatik silinmez.
- Varsayılan medya yenileme yolu yeni file GID'dir. Aynı ID/URL replacement ancak
  ayrı freshness testi geçerse desteklenir.

### Result/cache matrix

| Durum | Sonuç | Restart/cache |
|---|---|---|
| Root/type/version/count/order mismatch | Belge reddi | Fresh LKG korunur; empty değildir |
| Gerçek `count=0` | `IntentionalEmpty` | Eski snapshot/player temizlenir; empty atomik saklanır |
| Zorunlu field/type/GID veya yanlış `__typename` | Section reddi | Başka geçerli section varsa `PARTIAL` |
| Geçerli GID, geçici çözülemeyen reference/source | `NON_PLAYABLE` | Metin/GID saklanır; refresh tekrar hydrate eder |
| Çözülemeyen optional target | CTA'sız geçerli section | Eski target navigation üretilmez |
| Tüm section'lar reddedilmiş | `NONE_RENDERABLE` | LKG üzerine yazılmaz; fresh LKG veya açık hata |
| Geçerli+hatalı karışım | `PARTIAL` | Yalnız geçerli section'lar kalite/provenansla saklanır |

V2 store ayrı `home_content_v2`, storage version 2 ve 24 saat TTL kullanır.
Marker/snapshot atomiktir; latest request ekran/disk için geçerlidir. URL, media
byte, raw provider body, fiyat veya stok saklanmaz. Disk yazma hatasında session
state otoriter, restart sonucu `UNCONFIRMED` olur.

## HTTP, playback ve media safety contract

`PlaybackAttempt` iki ayrı küme taşır:

- `rejectedRenditions`: MIME/container/dimension/codec/origin veya terminal
  rendition hatasıyla kalıcı reddedilmiş source tuple'ları.
- `redirectChain`: mevcut HTTP zincirinde ziyaret edilmiş normalize URL'ler.

Kalıcı reddedilmiş rendition fallback adayına dönemez. Redirect'te aynı URL ikinci
kez görülürse loop reddedilir; toplam redirect üst sınırı 5'tir. Aynı geçerli
rendition URL'si initial request, Range, seek ve izinli tek transport retry için
yeniden kullanılabilir. Tek retry aynı request/rendition için bir kezdir. `200`
full-body Range yanıtı dahil okunan bütün response-body byte'ları aynı attempt'in
32 MiB bütçesini tüketir. Range/seek/retry/redirect/fallback/pause/resume/rotation/
rebuild yeni bütçe açmaz. Bütçe veya deadline sonrasında otomatik yeni attempt yok;
yalnız explicit user Retry yeni attempt açar.

Player Media3 `1.11.1` ve yalnız `media3-exoplayer`,
`media3-datasource-okhttp`, `media3-ui-compose-material3` kullanır. Forward buffer
en fazla 15 saniye, back buffer 0'dır. İlk Play→ilk rendered frame wall clock
deadline 20 saniyedir. İlk kare sonrası tek sürekli BUFFERING en fazla 20 saniye,
aynı attempt toplam post-first buffering en fazla 30 saniyedir. Aşım pending I/O'yu
iptal eder, poster/güvenli hata durumuna geçer; yalnız explicit Retry yeniler.

Pause/resume, rotation, visibility rebuild ve aynı media identity'ye dönüş attempt
bütçesini sıfırlamaz. Visibility loss playback'i durdurur, load'u iptal eder ve
player'ı bırakır; dönüşte autoplay yoktur. Geçici/kalıcı audio-focus kaybı ve
`ACTION_AUDIO_BECOMING_NOISY`/headphone removal pause eder, auto-resume yapmaz.
Navigation, background, section removal/change, empty, expiry veya revision
değişimi eski coroutine/data source/player işini iptal eder. Rotation konumu
korur fakat paused döner. Autoplay, loop, preload, video byte cache, background
service ve download yoktur.

Image max1600/decode≤2.56M pixel/8 MiB kuralları yalnız Home v2 image/poster
isteklerine uygulanır. Video Admin preflight READY, duration 1–60000 ms,
original≤25 MiB, resolution≤1920×1080/2.0736M pixels ve MP4 ister. Güvenli media
client auth/cookie göndermez, connect 10 s/read 20 s sınırları ve origin policy
uygular. App-wide image pipeline etkisi varsa Gürbakır Home/Product/Collection
regresyonu zorunludur.

## Acceptance matrix

| ID | Acceptance |
|---|---|
| A1 | Temiz worktree, exact base, Gürbakır/Trial config ve provider provenansı |
| A2 | Trial v1 definition/content Plan→Apply→Readback; ikinci Plan empty ve ikinci Apply zero-write |
| A3 | Trial developmentDebug instrumentation; executed/skip JUnit kanıtı; release kanıtı değildir |
| A4 | Trial catalogue/cart/checkout TR/TRY ve availability uyumu |
| A5 | Sentetik müşteri login/logout/reopen/expiry/profile/address; güvenli order yoksa detail `NOT RUN` |
| A6 | Exact v2 files, 2026-07 codegen/actual-client proof; tüm homeDefinitionContract okuyucuları; Gürbakır-v1+Trial-v2 aynı build |
| A7 | Validator/cache/revision ve HTTP positive/negative: same-URL Range/seek/retry, rejected exclusion, redirect loop, cumulative budget, buffering clock, rebuild reset escape |
| A8 | Gerçek Trial video: frame/time, pause/seek/mute, 15s/0, visibility, focus/noisy, deadlines, cancellation, explicit retry |
| A9 | Gürbakır staging+Trial side-by-side; same handle different product; cart/session/cache/logout/process-death isolation |
| A10a | Minified Trial developmentRelease v1 baseline, stable nonproduction signature |
| A10b | Minified Trial v2 candidate, increased versionCode, same package/signature, `adb install -r`, no data clear |
| A10c | Gürbakır stagingRelease baseline→candidate, same package/signature/data; mismatch olursa telefondaki veri silinmez, isolated pair kullanılır |
| A11 | Spotless/detekt/lint/JVM/build/minified/package/manifest/DEX/permission/secret, strict keys, Trial API23/30, canonical CI |
| A12 | Same build/payload/cache/thermal: 5 cold-start + 5 warm playback cycles; raw/median/max; no P95 claim |
| A13 | Owner guide real publish/change/remove/revision/root rollback rehearsal; brand-only/shared simulation |
| A14 | PR head H exact-SHA review/CI; merge M uses H ancestry/tree and its own exact-SHA CI; merge remains owner-only |

A6 actual-client command:

```powershell
.\gradlew.bat :storefront:generateStorefrontApolloSources :storefront:testDebugUnitTest --tests "com.gurbakir.storefront.OwnedHomeV2ReadProofTest" -PonboardingApplication=trial -PonboardingProfile=development -PonboardingRunOwnedHomeV2Readback=true --rerun-tasks --no-build-cache
```

`executed>0`, `skipped=0`, `failures=0` olmadan A6 `PASS` değildir. Crash, ANR,
background audio, zorunlu failure/skip, signing/data continuity ihlali,
budget/deadline reset escape veya güvenlik kontrolü kaldırılması açık `FAIL`dır.

## Task 1: Kalıcı contract checkpoint ve RED testleri

**Amaç:** Exact v2 contract artefact'larını, hash manifestini ve önce-failing
contract/projection tests'i eklemek; production davranışını henüz implement etmemek.

**Files/behaviors:**

- Exact v2 schema JSON, contract manifest, iki Storefront GraphQL operation ve
  representative `home-v2` fixtures oluştur.
- GraphQL yazmadan önce Shopify skill'in current Storefront docs search'ünü, sonra
  local schema validation scriptini exact model/client/artifact/revision metadata ile çalıştır.
- Manifest hashlerini deterministic üret/validate eden test ekle.
- RED tests: wrong type, wrong version, wrong count/order, PARTIAL,
  NON_PLAYABLE, IntentionalEmpty, NONE_RENDERABLE, parent-unchanged child change,
  same-ID/URL freshness unsupported/default new-file-GID.
- RED projection tests: `shopify.homeDefinitionContract` eksik/unknown/duplicate/
  wrong-case reddi ve yalnız iki allowlisted tuple.

**Verification:** Focused PowerShell/Kotlin tests önce beklenen nedenle fail, artefact
ve test fixture checkpoint'i static/schema doğrulamasından geçer. Gerçek public-client
readback ayrı A6 durumudur; çalışmadıysa `NOT RUN`.

## Task 2: Trial application composition ve gerçek v1 checkpoint

**Amaç:** `:trial` uygulamasını mevcut application-shell kalıbıyla eklemek, bağımsız
kimlik/veri/provider sınırlarını doğrulamak ve v2 öncesi gerçek v1 release baseline
oluşturmak.

**Files/behaviors:**

- `settings.gradle.kts`, registry/projections/CI/portability allowlists ve `apps/trial`
  module'ünü ekle; shared module'ler Trial'a bağımlı olamaz.
- Exact Trial composition contract, deny-all tracking, separate persistence names,
  no-backup/data-extraction rules, Trial-only fallbacks/legal/support/deletion roles.
- Trial development debug/release, minification, stable ignored local signing input;
  no production variant/provider fallback.
- Shopify connector Trial'a switch edilir; canonical domain/numeric ID/development
  state okunmadan mutation yok. Firebase'te yeni independent Trial project ve
  debug/release app kayıtları kullanılır; existing Gürbakır projects reused edilmez.
- Gerçek Trial Menu/Home v1 için operator Plan→Apply→Readback; tekrar Plan empty,
  tekrar Apply zero-write. Gürbakır control read-only.
- Minified v1 APK digest/signing fingerprint/release ledger kaydı.

**Verification:** Trial JVM/build/manifest/DEX/permission/secret checks; actual provider
receipt; configured Storefront/Menu/Home readback. Cihaz yoksa device rows `NOT RUN`.

## Task 3: homeDefinitionContract tek-dilim cutover

**Amaç:** Contract ID'yi bütün okuyuculara aynı compile slice'ta taşımak, v1/v2
dispatch'i fail-closed yapmak ve strict key validation'ı korumak.

**Files/behaviors:**

- Projection producer/registry module, app/trial/storefront Gradle readers,
  `OwnedOnboardingConfiguration.kt`, validators/tests, `HomeRemoteSource`
  consumers/implementations/fakes birlikte güncellenir.
- `HomeContentContractId` sealed/enum contract'i; only GATE7_V1/PILOT_MEDIA_V2.
- Mismatch network/provider write öncesi `HOME_CONTRACT_MISMATCH`.
- Gürbakır exact v1 and Trial exact v2 same Gradle invocation.
- Unknown/duplicate/case errors remain closed; no relaxed map parsing.

**Verification:** Projection generator and validator fixture tests observed RED→GREEN;
`:app`, `:trial`, `:storefront`, `:mobile-core` compile/test same invocation.

## Task 4: V2 provider/operator definitions ve root-last publication

**Amaç:** Exact v2 Admin/Storefront definition operations, file/media preflight,
revision receipts ve reversible root-last workflow eklemek.

**Files/behaviors:**

- Closed operator descriptors for v2 root/image/video; definitions-before-values-before-read.
- V2 Plan/Apply/Readback/idempotence and rollback receipt; child/file created/read first,
  root ordered references last; no automatic delete.
- Max 14 unique resource IDs, exact typename/field/reference/media readback.
- New file GID default replacement; same ID/URL only explicit freshness proof.
- Trial v2 definitions/content only after v1 checkpoint; Gürbakır write impossible.
- Admin GraphQL operations official schema searched/validated before use.

**Verification:** Unit/fixture receipts and dry Plan, then authorized Trial development
Apply/Readback/zero-write repeat; root-last ordering and rollback proof. If connector
identity cannot be verified, only dependent provider row is blocked.

## Task 5: V2 mapper, validation, revision ve persistence

**Amaç:** Provider-neutral Image/Video mapping, exact result matrix, revision freshness
and separate atomic v2 cache implement etmek.

**Files/behaviors:**

- V2 gateway/codegen mapping and max-cardinality validation.
- `RemoteHomeSection.Image`/`Video`; typed optional Product/Collection action.
- Deterministic `sectionRevisionDigest` includes child version and media source tuple,
  not just parent `updatedAt`.
- Result matrix exactly as above; PARTIAL stores only valid sections/provenance,
  transient non-playable rehydrates, intentional empty clears old state, all-invalid
  cannot overwrite LKG.
- `home_content_v2`, version 2, 24h; atomic marker/snapshot; latest-request wins;
  disk failure makes restart `UNCONFIRMED`; forbidden data never persisted.
- Revision/removal/empty/expiry cancels old media identity/job.

**Verification:** Focused mapper/validator/store/repository concurrency and restart tests,
including parent unchanged child updated and same ID/URL freshness cases.

## Task 6: HTTP attempt budget ve bounded player lifecycle

**Amaç:** Same-URL Range/seek/retry ayrımını, cumulative budget/deadlines ve lifecycle/
audio safety'yi Home v2 player'da test-first implement etmek.

**Files/behaviors:**

- Media3 exact dependencies and bounded LoadControl 15s forward/0 back.
- `PlaybackAttempt` carries rejected rendition set, redirect chain, cumulative 32 MiB,
  one transport retry, first-frame and post-frame buffering clocks.
- Same valid URL initial→Range→seek and one retry allowed; permanent rejected source
  excluded; self/A→B→A redirect loop blocked; max redirects 5.
- All response bytes including Range→200 body consume same attempt. Pause/resume,
  rotation/rebuild do not reset ID/counters/deadlines.
- Visibility/background/navigation/disposal/source-revision change cancels pending I/O
  and releases player; no autoplay on return.
- Audio focus/noisy/headphone removal pause with no automatic resume.
- First-frame 20s, continuous post-frame 20s, cumulative post-frame 30s; terminal
  state only explicit Retry creates new attempt.
- No autoplay/loop/preload/download/background service/video byte cache.
- V2-only image/poster constraints and Gürbakır image regression if pipeline shared.

**Verification:** Fake-clock/scripted HTTP positive and negative tests named in A7;
player lifecycle/component tests; debug instrumentation separate from release evidence.

## Task 7: Release updates, two-app/provider/device acceptance

**Amaç:** V1→v2 Trial ve baseline→candidate Gürbakır staging release updates'i,
same-signature/no-data-clear koşuluyla bağımsız kanıtlamak; two-app isolation ve
runtime media acceptance yürütmek.

**Files/behaviors:**

- Trial v2 candidate versionCode increments, same package/nonproduction signature.
- Gürbakır staging candidate preserves package/signature; mismatch on main phone never
  triggers data deletion, use isolated pair.
- `adb install -r`; no uninstall/clear-data. Trial and Gürbakır side-by-side.
- A3–A10c and A12 acceptance rows with exact commands/artifacts/JUnit counts.
- Five cold-start and five warm playback cycles report raw/median/max only.
- Device absent leaves only device-dependent rows `NOT RUN`; source/provider work remains.

**Verification:** Fresh ADB inventory; minified release builds/digests/signing/manifest;
instrumentation actual executed/skipped/failure counts; device captures where available.

## Task 8: Full validation, owner operations ve handoff

**Amaç:** Canonical checks, owner content rehearsal, Gate 9 closure assessment and
separate P3-16 owner table deliver etmek without merge/publication.

**Files/behaviors:**

- `docs/operations/MULTI-BRAND-OWNER-OPERATIONS-TR.md`: Shopify product/collection/
  Menu/Home v1-v2; Storefront/Admin/Customer distinction; banner/photo/video revision,
  root-last publish, remove/change/rollback; shared UI vs brand-only; local/GitHub/CI;
  per-app versions/artifacts; credentials/API/Remote Config/content/cache/auth/Firebase;
  Trial/Gürbakır update; Git revert vs forward rollback; new machine/brand/offboarding.
- Release ledger fields: app/profile/version/source SHA/APK-AAB digest/signing fingerprint/
  Home/API versions/provider/device/test/distribution status.
- Owner guide publish/change/remove/revision/root rollback rehearsal on Trial; simulated
  shared/brand-only flow clearly marked.
- Play/P3-16 table has owner/input/action/evidence/point-of-action approval; Trial never
  production, Gürbakır uses approved real store; package/Play/App Signing/upload key/
  production providers/App Links/Data Safety/legal/listing/rollout/incident remain separate.
- Full A1–A14 evidence matrix. Gate 9 closes only after mandatory rows, owner merge and
  merged-main exact-SHA CI; this branch can at most be PR-ready.
- PR candidate H gets exact-SHA review/CI. Future merge M must prove H ancestry/tree and
  its own exact-SHA CI; equality is not required. No merge is performed here.

**Verification:** Spotless, detekt, lint, all JVM/build/minified/package/manifest/DEX/
permission/secret/projection, API23/API30 where environment allows, canonical CI only if
owner later chooses PR/push. Final report preserves FAIL/PARTIAL/NOT RUN/open items.

## Rollback

Source rollback is a forward commit/build, never destructive history rewrite. Provider
rollback reselects receipt-recorded prior root references only after prior child/file
readback; resources are not automatically deleted. Git revert does not update installed
devices; a new signed build and normal update path are required. No rollback step may
clear user data, rotate production credentials, delete provider projects/apps/content,
or write Gürbakır merchant content under this plan.
