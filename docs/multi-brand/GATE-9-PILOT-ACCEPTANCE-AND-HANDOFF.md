# Gate 9 İkinci Mağaza ve Sınırlı Medya Pilot Handoff'u

Durum: **IMPLEMENTATION CANDIDATE; GATE 9 AÇIK**  
Kanıt tarihi: 2026-09-17 (Europe/Istanbul)  
Exact taban: `5c402241e22a45001bdc57da6d08a83d2ce407cc`  
İncelenen kaynak: `033b69f38cd9df34f5a4b7a4d639afcbd4dce0c3`  
Dal: `codex/gate9-second-store-media-pilot`

Bu kayıt owner-approved Gate 9 planının uygulanmış ve gerçekten çalıştırılmış
kısmını, başarısızlıkları ve çalıştırılmayan kabul satırlarını ayırır. Merge, PR,
Play yükleme/yayını, production signing, gerçek müşteri/sipariş/ödeme, Gürbakır
merchant içeriğine yazma veya yıkıcı provider işlemi yapılmadı.

Dokümantasyon commit'i kendi SHA'sını içeremez. Final review/PR adayı bu dosyanın
bulunduğu commit'i ve aşağıdaki implementation SHA'larını ancestor olarak
göstermelidir.

## Sonuç özeti

- Ayrı `:trial` real-application module'ü, bağımsız package/persistence/OAuth/
  Shopify/Firebase/legal/support/tracking bileşimiyle eklendi.
- Gerçek Trial v1 Storefront/Menu/Home checkpoint'i v2'den önce oluşturuldu;
  Apply 15 yazı yaptı, readback geçti, tekrar Apply sıfır yazı yaptı.
- Trial v2 için exact schema/query/manifest, root-last provider içeriği, iki image,
  bir video, revision digest, rollback receipt ve zero-write repeat oluşturuldu.
- Gürbakır `gate7-v1` ile Trial `pilot-media-v2` aynı kapalı projection ve compile
  diliminde birlikte geçiyor; unknown/missing/wrong-case/duplicate key kontrolleri
  gevşetilmedi.
- Home v2 mapper/validator/PARTIAL/NON_PLAYABLE/IntentionalEmpty/
  NONE_RENDERABLE, child-revision freshness, isolated atomic cache ve cancellation
  davranışı uygulandı.
- Media3 1.11.1 player, 15 s ileri/0 geri buffer, tek attempt 32 MiB, same-URL
  Range/seek/tek retry, rejected rendition ve redirect-loop ayrımı, ilk-kare ve
  post-frame buffering deadline'ları, visibility/audio-focus/noisy/revision
  iptalleri uygulandı.
- Trial v1/v2 ve Gürbakır staging baseline/candidate minified artifact çiftleri
  üretildi ve paket/imza/digest kanıtı alındı.
- Fiziksel ADB bağlantısı koptuğu için tam player, iki-app, install-update,
  data-continuity ve performans satırları `NOT RUN` bırakıldı.
- Gerçek Trial `cartCreate` testi çalıştırıldı ve `ACCESS_DENIED` ile **FAIL** oldu.
  Katalog/Home/TRY/availability okumaları geçiyor; cart için gerekli
  `unauthenticated_write_checkouts` development kurulumu eksik veya mevcut public
  token'a etkin yansımamış durumda. Güvenlik kontrolü kaldırılmadı.
- Shopify plugin oturumu yeniden kimlik doğrulaması istediği için yeni
  change/remove/root-rollback provider provası tamamlanamadı. Önceki gerçek root-last
  publish ve zero-write receipt'leri geçerlidir.

Gate 9 bu dalda kapanmaz. A4 başarısızlığı, zorunlu ADB kabul satırları, provider
rollback provası, PR-head exact-SHA CI, owner merge ve merged-main exact-SHA CI
tamamlanmadan closure verilemez. P3-16 ayrıca başlamamıştır.

## Mimari ve uygulama ayrımı

| Sınır | Gürbakır | Trial |
|---|---|---|
| Rol | Gerçek marka, gelecekte production | Development-only ikinci mağaza pilotu |
| Module | `:app` | `:trial` → `apps/trial` |
| Home tuple | `mobile_home / 1 / gate7-v1` | `mobile_home_v2 / 2 / pilot-media-v2` |
| Package | Korunan development/staging kimlikleri | release `com.projectapp134.multibrandtrial.dev`; debug `.debug` |
| Shopify | Gerçek merchant mağazası; bu çalışma salt-okunur | `multi-brand-trial-store.myshopify.com`; provider yazıları yalnız burada |
| Firebase | Korunan mevcut nonproduction binding'ler | Ayrı `projectapp134-multibrand-trial` project ve iki Android app |
| Persistence | Korunan Gürbakır ad/alias'ları | `trial-store-local.db` ve Trial-only preferences/Keystore/Home partition |
| Production | P3-16 girdileri bekliyor | Yasak; production'a yükseltilmez |

Shared module'ler hiçbir concrete application module'e bağımlı değildir. Runtime
merchant switch veya brand flavor eklenmedi. Synthetic provider'sız fixture rolünü
korur.

## Provider provenansı

### Trial Shopify

| Alan | Doğrulanmış değer |
|---|---|
| Canonical domain | `multi-brand-trial-store.myshopify.com` |
| Numeric shop ID | `61252272257` |
| Shop GID | `gid://shopify/Shop/61252272257` |
| API | Storefront/Admin `2026-07` |
| Headless publication | `gid://shopify/Publication/110960869505` |
| Customer callback | `shop.61252272257.multibrandtrial://oauth/callback` |
| Customer client evidence | Client ID değeri saklanmadı; receipt SHA-256 `40e0290a409cfe598bba83fe09a489f9646ac36d71dd4dd28bfb566d92a101e0` |
| Scoped local config SHA-256 | `33a1c23e6c958c7a77125692b7ec7f4ff40d14215b7ff718ca278adc06d3b204` |
| Provider binding SHA-256 | `21596b33cb398c8ff661feff0123b951af6453426952c701275af583ca47cd94` |

Admin readback product/publication/Menu/metaobject/File/Page read/write kapsamlarını
gösterdi. Storefront gerçek `cartCreate` sonucu ayrıca scope eksikliğini ortaya çıkardı;
Admin app scope listesi Storefront token'ın unauthenticated scope kanıtı değildir.

### Trial Firebase

| Alan | Doğrulanmış değer |
|---|---|
| Project ID | `projectapp134-multibrand-trial` |
| Project number | `194899849371` |
| Debug app ID | `1:194899849371:android:6ad398ea2a4bb0f654163e` |
| Release app ID | `1:194899849371:android:9c1689d48a5c4b7654163e` |
| `google-services.json` SHA-256 | Her iki scoped kopya: `91017ebf98c94b7f5144ce79cb3382db027ad0a68474919567e97182f82c6818` |
| Validator | `Test-FirebaseConfiguration.ps1`: PASS; iki ignored config aynı isolated Trial profile'a bağlı |

Firestore, Hosting ve Functions açılmadı. Firebase config dosyaları, Shopify public
token/client bilgileri ve signing materyali ignored/Git-dışı kaldı. Gitleaks 75 commit
ve yaklaşık 7,65 MB üzerinde sızıntı bulmadı.

## Gerçek Shopify checkpoint'leri

### V1

- Initial Apply: 15 provider yazısı; `readback=PASS`.
- Repeat Apply: 0 yazı; `idempotence=ZERO_WRITE`.
- Product: `gid://shopify/Product/7067655995521`, handle `pilot-urun`, public
  readback `availableForSale=true`, `199.0 TRY`.
- Collection: `gid://shopify/Collection/300730876033`, handle
  `pilot-koleksiyonu`.
- Menu: `gid://shopify/Menu/200940028033`, handle `main-menu`.
- V1 root: `gid://shopify/Metaobject/79651635329`.
- V1 child'ları:
  `gid://shopify/Metaobject/79651537025` ve
  `gid://shopify/Metaobject/79651569793`.
- Altı provisional legal/support Page oluşturuldu; production hukuk kabulü değildir.

Redakte receipts:

- `out/onboarding/gate9-trial/trial-v1-content-apply-initial.json`
- `out/onboarding/gate9-trial/trial-v1-content-apply-idempotent.json`
- `out/onboarding/gate9-trial/trial-v1-public-readback.json`

### V2

- Root: `gid://shopify/Metaobject/79655141505`.
- Ordered sections: image banner, collection grid, image photo, featured product,
  video; beş benzersiz child GID.
- Banner: `gid://shopify/MediaImage/24034074493057`.
- Photo: `gid://shopify/MediaImage/24034074525825`.
- Video: `gid://shopify/Video/24034075082881`.
- Final publish root-last receipt: 3 son provider yazısı, readback PASS.
- Repeat Apply: 0 yazı, readback PASS.
- Canonical final zero-write `sectionRevisionDigest`:
  `9b2d6637c9f5fc0ee7e8d166eb0bc12e16f7921912bd4a39d06c464969c284f7`.
- İlk post-apply receipt digest'i `aec6561ac6b139d32a003ee1dce82f6bc03b2cfcdde862d28b5b86ed76b17bc6`;
  final readback digest'i yukarıdaki zero-write receipt'tir. İki değer gizlenmedi.
- Rollback receipt v1/v2 root, child ve file GID'lerini taşır;
  `automaticDelete=false`, `rootLast=true`.

Redakte receipts:

- `out/onboarding/gate9-trial/trial-v2-content-apply-final-publish.json`
- `out/onboarding/gate9-trial/trial-v2-content-apply-repeat-zero.json`

Gürbakır merchant içeriğine yazılmadı. Trial kaynakları otomatik silinmedi.

## İlk contract checkpoint'i

| Dosya | SHA-256 |
|---|---|
| `config/onboarding/application-registry.v1.json` | `2a8b7267ac7de52fbeb3f4b8e17114464ef794ce543da2757a8b77f42d725a6d` |
| `config/onboarding/shopify-home-schema.v2.json` | `782cbf8f21275f7f7d6a46c29bdbf3c0976eeb723d8c8681cfb0832f839d06b7` |
| `config/onboarding/shopify-home-contract.v2.manifest.json` | `55af141a13474d6a0346a7001c3e6d6a01e332260ac6c2116f1e1534505eeaf9` |
| `HomeContentV2Metaobject.graphql` | `dab92a88a1a7a95a57bac87e7f47254eaf3a9295dc97c856a578caa50951a54f` |
| `HomeV2Resources.graphql` | `26cdbc0aa6846564eaf8fec6dea5d57260fe24982d287dae194444a280a7b78e` |
| Trial development projection | `6556a2bb0a9e4bd3c16175a08619081756d95938950cddc0d02587609e1b16e7` |
| Gürbakır staging projection | `55af8a5ae831dac44a38f8c228965c6954619c9d106160845cdf0bd41dfc6f92` |

Bu hash'ler provider token değeri içermez. Manifest, exact GraphQL dosyalarını ve
fixture setini schema/API contract'ına bağlar.

## A1–A14 kabul kaydı

Durumlar çalıştırılmış kanıta göre verilir. `PARTIAL`, alt parçaların tamamlanmadığı;
`NOT RUN`, komutun yürütülmediği; `FAIL`, yürütülen zorunlu beklentinin sağlanmadığı
anlamına gelir.

| ID / durum | App/profile ve başlangıç | İşlem / beklenen sonuç | Komut veya runner | Exact kanıt |
|---|---|---|---|---|
| **A1 PASS** | Gürbakır staging + Trial development; remote/main `5c402241...`; kirli ana checkout korunmuş | İzole worktree/dal, strict projection ve provider provenansını doğrula; main'e dokunma | Git read-only inventory, projection/Firebase validators; Windows local runner | Base `5c402241...`; Trial/Gürbakır projection hash'leri; provider binding/readback; ana checkout/old worktree değişmedi |
| **A2 PASS** | Trial development, provider'da v2 öncesi v1 yok | V1 Plan→Apply→Readback; tekrar Plan empty/Apply zero-write | Ignored bounded provider helper; Shopify CLI authenticated development session | `b4cdc45f696a314252a6be4d597c336774d65b97`; initial 15 write, repeat 0; receipts ve V1 root GID |
| **A3 NOT RUN** | Trial `developmentDebug`; ADB bağlantısı yok | Instrumentation'da gerçek executed/skip/failure sayısı; debug release kanıtı sayılmaz | Planlanan `:trial:ciApi30DevelopmentDebugAndroidTest`; Android test APK yalnız derlendi | `:trial:assembleDevelopmentDebugAndroidTest` PASS; cihaz yürütmesi yok |
| **A4 FAIL** | Trial public client; ürün/collection/Menu/Home okunuyor | Catalogue/availability/TRY/cart/checkout uyumu; cart lifecycle temizlenmeli | `OwnedHomeV2ReadProofTest` PASS; `OwnedStorefrontCartProofTest` gerçek Trial flag'iyle çalıştı | Home product `TRY`, public product available; cart test `executed=1`, `failures=1`, `ACCESS_DENIED`; neden `SETUP REQUIRED` (`unauthenticated_write_checkouts`) |
| **A5 NOT RUN** | Trial Customer Account client kayıtlı; güvenli test customer oturumu/ADB yok | Login/logout/reopen/expiry/profile/address; order yoksa detail NOT RUN | Fiziksel client/PKCE runner gerekir | Callback/client digest receipt mevcut; kullanıcı journey çalışmadı |
| **A6 PASS** | Trial v2 projection + gerçek public client | Exact schema/query/codegen, strict readers, Gürbakır-v1/Trial-v2 aynı compile; actual Home v2 readback | Onaylı plandaki `OwnedHomeV2ReadProofTest` komutu, Windows local runner | `7122c0b8a96101e4e014c61116de9efecc12fada`; executed 1, skipped 0, failure 0; exact file hash tablosu |
| **A7 PASS** | Source/JVM fixtures; gerçek data-source instrumentation için Infinix X6817 | Wrong-type/PARTIAL/cache/revision; same-URL Range/seek/one retry; rejected rendition/redirect/budget/deadline/rebuild | JVM `Home*` suites + `HomePlaybackDataSourceTest` physical | `5354f5fad98622e1e09f5904656004cecb961df2`; JVM media suites PASS; physical data-source 3/3 PASS, serial evidence local |
| **A8 NOT RUN** | Trial gerçek video; ADB bağlantısı koptu | Frame/time/pause/seek/mute, 15s/0, visibility/focus/noisy, timeout/cancel/retry | Fiziksel `HomeScreenTest`/player acceptance | Son deneme `No connected devices!`; source/component tests PASS ama runtime PASS değildir |
| **A9 NOT RUN** | Gürbakır staging + Trial side-by-side; ADB yok | Aynı handle farklı product; cart/session/cache/logout/process-death izolasyonu | İki APK physical-device journey | Artifact/package ayrımı kanıtlı; cihaz journey yok |
| **A10a PASS** | Trial v1 release, source `b4cdc45...` | Minified v1 baseline ve nonproduction imza | `:trial:assembleDevelopmentRelease`, R8/package/signature runner | APK SHA-256 `3836e475...6f0bc`; cert `4a8a0f0f...dfc77`; package/code/name `com.projectapp134.multibrandtrial.dev / 1 / 0.1.0-trial-v1` |
| **A10b PARTIAL** | Trial v2 release, source `b54ad305...`; ADB yok | Minified v2, code artışı, aynı package/imza; sonra `adb install -r` veri silmeden | Release build + `Test-TrialPackage.ps1`; install beklemede | APK SHA-256 `ce16f823...19b93`; code/name `2 / 0.2.0-trial-v2`; aynı cert; install/data continuity NOT RUN |
| **A10c PARTIAL** | Gürbakır staging base `5c402241...`, candidate `b54ad305...`; protected real config kopyalanmadı | Aynı package/imzalı isolated update-test pair; veri silmeden update | İki minified release build, local debug-keystore ile açıkça nonproduction test imzası; install beklemede | Signed hashes `5c285437...173b4` → `4809918c...75d6b`; package `com.gurbakir.mobile.staging`; cert `ee90c9e1...e3a8`; install/data continuity NOT RUN |
| **A11 PARTIAL** | Tüm module/app'ler; local Windows runner | Spotless/detekt/lint/JVM/assemble/package/manifest/DEX/permission/secret/strict keys; API23/30 ve canonical CI | Root Gradle lanes ve PowerShell validators | Static 365-task PASS; JVM 505 tests/0 failure/5 conditional skip; assemble 894-task PASS; onboarding 240/240; public 24/24; portability 47/47; synthetic 63/63; Trial 25/25; Gitleaks PASS. API23/API30 execution ve exact-SHA GitHub CI NOT RUN |
| **A12 NOT RUN** | Aynı Trial build/payload/cache/thermal; ADB yok | 5 cold + 5 warm playback; raw/median/max; P95 yok | Physical performance runner | Ölçüm yok; P95 iddiası yok |
| **A13 PARTIAL** | Trial real provider + owner guide | Publish/change/remove/revision/root rollback provası; shared/brand-only simulation | V1/v2 root-last ve zero-write receipts PASS; yeni provider session reauth istedi | Bu owner guide teslim edildi; initial publish/revision/root-last gerçek. Change/remove/rollback execution `EXTERNALLY BLOCKED` by reauthentication |
| **A14 NOT RUN** | Branch local; PR/merge yetkisi yok | PR head H exact-SHA CI; merge M için H ancestry/tree + M exact-SHA CI | GitHub protected workflow | PR/push/merge yapılmadı; H/M yok |

### A4 başarısızlığının sınıflandırması

Bu sonuç dış ticari engel değildir. Resmî Shopify access-scope sözleşmesine göre Cart
yazımı `unauthenticated_write_checkouts` ister. Trial Headless izinleri/readback'i
düzeltilip aynı exact test yeniden çalıştırılmalıdır. `ACCESS_DENIED` beklenen sonuç
olarak kabul edilmez ve A4 PASS yapılamaz.

Redakte JUnit XML yerel/ignored olarak
`out/evidence/gate9/a4/033b69f38cd9df34f5a4b7a4d639afcbd4dce0c3/TEST-com.gurbakir.storefront.OwnedStorefrontCartProofTest.xml`
altında korunur; SHA-256
`51bb1c8f6c274da2708aba4f8cb593474964d1d4a123e5ead889c034e03a9f3d`,
`tests=1`, `failures=1`, `skipped=0`.

### Debug, release ve cihaz kanıtı ayrımı

- JVM/component testleri player state machine'i kanıtlar; gerçek codec/render/audio
  focus cihaz davranışını kanıtlamaz.
- Physical data-source 3/3, HTTP/byte/range güvenliğini kanıtlar; A8 gerçek video
  deneyimini tamamlamaz.
- Minified release artifact/package/signature kanıtı `adb install -r` ve data
  continuity yerine geçmez.
- Android test APK'sının derlenmesi instrumentation'ın yürüdüğü anlamına gelmez.

## Yerel doğrulama özeti

| Doğrulama | Sonuç |
|---|---|
| `spotlessCheck detekt lint` | PASS; 365 görev, 0 hata |
| Registry-driven JVM lane | PASS; 10 görev, 264 Gradle işi; XML toplamı 505 test, 0 failure, 0 error, 5 koşullu skip |
| Registry-driven assemble lane | PASS; 14 görev, 894 Gradle işi; 329 executed, 565 exact-SHA up-to-date |
| Onboarding all suite | PASS 240/240 |
| Public readiness self/current | PASS 19/19 ve 24/24 |
| Repository portability self/current | PASS 68 fixture ve 47 check |
| Synthetic package self/current | PASS 49 fixture ve 63 check |
| Trial package self/current | PASS 6 fixture ve 25 check |
| Firebase config isolation | PASS; iki ignored Trial config, tek isolated project/profile |
| Gitleaks | PASS; 75 commit, yaklaşık 7,65 MB, leak yok |
| Gerçek Trial Home v2 public-client | PASS; 1 executed, 0 skip, 0 failure |
| Gerçek Trial cart lifecycle | **FAIL**; 1 executed, 1 failure, `ACCESS_DENIED` |
| API 30 / API 23 instrumentation | NOT RUN; ADB talimatı nedeniyle beklemede |

Ek olarak tarihsel `Test-Phase2Foundation.ps1` çalıştırıldı ve 16 PASS/10 FAIL
döndürdü. Repository'nin kendi audit'i bu scripti `HISTORICAL EVIDENCE / SUPPORT`
olarak sınıflandırır; canonical workflow çağırmaz. Fail'ler eski altı-module ve kaldırılmış
Phase 2 proof-controller varsayımlarıdır. Sonuç `PREEXISTING historical validator drift`
olarak kaydedilir; güncel A11 PASS kanıtı gibi kullanılmaz.

## Release ledger

| App/profile | version | Source | Artifact SHA-256 | Signing certificate SHA-256 | Home/API | Dağıtım |
|---|---|---|---|---|---|---|
| Trial developmentRelease v1 | `1 / 0.1.0-trial-v1` | `b4cdc45f696a314252a6be4d597c336774d65b97` | `3836e475f801e8a03a55c3dad30b007aad14ee6b69ceceda0d02da776106f0bc` | `4a8a0f0f9e15583d7212c588fb043a86583666b2cea8dada38150947889dfc77` | v1 / 2026-07 | Local only; minified; device update NOT RUN |
| Trial developmentRelease v2 | `2 / 0.2.0-trial-v2` | `b54ad305f4a7ec19e2e44b4db731249796d123c6` | `ce16f823b2b4b5e9adcb6773e3e0f2eeb58949558e97b0e799a7f4b1fc419b93` | aynı Trial nonproduction cert | v2 / 2026-07 | Local only; minified; device update NOT RUN |
| Gürbakır staging baseline update-test | `1 / 0.1.0` | `5c402241e22a45001bdc57da6d08a83d2ce407cc` | `5c285437c450033af83ff67177600f4e88ecfd8d8157a599e84f55bd696173b4` | `ee90c9e1977458c66c8670e2e656a65ee8a760786407dd1e327607af5114e3a8` | v1 / 2026-07 | Isolated local update-test; real provider config yok; install NOT RUN |
| Gürbakır staging candidate update-test | `1 / 0.1.0` | `b54ad305f4a7ec19e2e44b4db731249796d123c6` | `4809918cb59d0dacdf563bc10c8d65d89dffa3a8c5f101a1e981bc0db4675d6b` | aynı isolated cert | v1 / 2026-07 | Isolated local update-test; real provider config yok; install NOT RUN |

Gürbakır unsigned baseline/candidate digest'leri sırasıyla
`dfaa629e...cb6d` ve `05cca7e9...01c8` olarak da korunur. Update-test imzası production
signing değildir. Trial/Gürbakır artifact'ları Play'e yüklenmedi.

## Plan checkpoint kontrolü

| Dilim | Durum | Not |
|---|---|---|
| 1. Kalıcı plan/contract/RED | Tamam | Exact schema/query/manifest/fixtures ve adversarial tests mevcut |
| 2. Trial composition + v1 | Tamam | Ayrı app/provider/data; gerçek v1 ve zero-write checkpoint |
| 3. Projection tek-dilim cutover | Tamam | Gürbakır v1 + Trial v2; strict key denetimi korunuyor |
| 4. V2 provider/root-last | Tamam | Gerçek Trial definitions/content/media/root-last/rollback receipt |
| 5. Mapper/validator/cache | Tamam | Sonuç matrisi, child freshness, atomic v2 store |
| 6. HTTP/player | Kaynak/JVM tamam; cihaz kısmi | A7 PASS; A8 NOT RUN |
| 7. Release/two-app/device | Artifact kısmı tamam; cihaz kısmi | A10a PASS; A9/A10 update/A12 beklemede |
| 8. Full validation/owner/handoff | Kısmi | Local canonical kontroller geçti; owner guide var; A4 FAIL, provider rehearsal/CI/device açık |

## Gate 9'u kapatmak için kalanlar

1. Shopify/Headless bağlantısını yeniden yetkilendir; Trial public client'a gerçek
   `unauthenticated_write_checkouts` kapsamını ver/readback et; A4 cart testini 1/1 PASS yap.
2. ADB geri geldiğinde veri silmeden A3, A8, A9, A10b install/update, A10c
   install/update ve A12 raw/median/max ölçümlerini çalıştır.
3. Trial content için gerçek change, remove, child/file revision ve önceki root'a
   rollback provası yap; otomatik resource deletion yapma.
4. Zorunlu satırlarda failure/skip/NOT RUN kalmazsa final review ve exact candidate
   source/artifact kanıtını tazele.
5. Owner seçerse PR açılır; PR head `H` exact-SHA CI geçer. Merge ayrı owner eylemidir.
6. Merge `M`, `H` ile eşit olmak zorunda değildir; `H` ancestry/tree karşılaştırması
   ve `M` exact-SHA validate/API30/API23 post-merge CI gerekir.

## P3-16 production/Play tablosu

Trial bu tablonun production adayı değildir. Gelecekte yayımlanacak uygulama
Gürbakır'dır. Her profile için yeni production Shopify mağazası zorunlu değildir;
onaylı gerçek Gürbakır mağazası doğru production client/provider/environment binding'iyle
kullanılabilir.

| Girdi | Sorumlu | Gerekli input | Eylem | Kabul kanıtı | Eylem anı onayı |
|---|---|---|---|---|---|
| Production package/version | Product + Android release owner | Kalıcı package, versionCode/name ve channel politikası | Production variant/identity ekle; dev/staging'i koru | Manifest/bundle metadata ve migration testi | Kimlik iş kararı kesinleşmeden başlanmaz |
| Play app ownership | Owner-designated Play Console owner | Doğru package için Play kaydı ve roller | App kaydı/erişim devri | Console ownership receipt | Play upload/publish anında açık owner onayı |
| App Signing/upload key | Signing custodian | Play App Signing kararı, upload key custody/recovery/rotation | Secure signing wiring; key APK/Git dışında | Signed AAB + cert fingerprint + custody record | Production private key kullanma/değiştirme anında açık onay |
| Production Shopify | Merchant + integration owner | Gerçek Gürbakır store, production Storefront/Customer clients/scopes/callbacks | Exact binding ve readback; merchant content değişikliği ayrı | Domain/shop/client digest/scope/runtime readback | Ticari storefront setting veya merchant content yazısında point-of-action onay |
| Production Firebase | Firebase owner | Production project/app veya bilinçli absence kararı | Package/cert/app binding; RC/FCM policy | Management readback + artifact/runtime test | Billing/paid service veya destructive project/app action'da onay |
| Verified App Links | Domain + signing owner | Package ve Play/App Signing cert fingerprint | `assetlinks.json` yayımla ve verify et | Public HTTPS association + device link verification | Domain publication owner'ı onaylar |
| Data Safety/privacy | Privacy/legal owner | Gerçek SDK/data/purpose/retention/deletion envanteri | Play declarations ve privacy corpus | Approved response set + artifact inventory eşleşmesi | Bağlayıcı public declaration öncesi owner approval |
| Legal/support/deletion | Legal/support/merchant owner | Final corpus, support escalation, deletion acknowledgement/SLA/retention/execution | Release URLs ve operatör prosedürü | Public readback + test deletion lifecycle | Gerçek customer data/deletion action'da açık onay |
| Release hardening | Android/security owner | Exact signed AAB, dependency locks, SBOM/license/secret policy | Bundletool, R8, permission/DEX, dependency/SBOM, 16 KB artifact/runtime test | Exact-SHA reports; targetSdk 36 tek başına yeterli değil | Production key kullanımı sınırına kadar autonomous |
| Listing/rollout/incident/rollback | Release + support owner | Listing assets/copy, test track, staged rollout/stop thresholds, incident contacts | Internal/closed test, staged rollout ve forward rollback runbook | Track receipt, monitoring, rollback drill | Her Play upload/publish/rollout adımında owner onayı |

P3-16 ancak signed production candidate, production provider/callback/App Links,
Data Safety/legal/deletion, support/release/incident/rollback ownership ve exact artifact
testleri geçtiğinde kapanabilir. `targetSdk 36`, development PR'si veya Gate 9 closure
tek başına Play-ready kanıtı değildir.

## External-state ve rollback kaydı

- Trial Shopify: V1/V2 resource'ları oluşturuldu; final v2 root seçildi; eski V1/V2
  child/file revision'ları korundu.
- Gürbakır Shopify: salt-okunur; merchant content yazısı yok.
- Trial Firebase: ayrı project ve iki Android app config'i oluşturuldu; production
  Firebase yok.
- Customers/orders/payments/deletion: değiştirilmedi.
- Play/domain/production signing: değiştirilmedi.
- ADB: daha önce A7 data-source 3/3 kanıtı alındı; sonraki bağlantı kaybından sonra
  cihaz komutları zorlanmadı.

Source rollback, destructive history rewrite değil forward revert/build'dir. Provider
rollback önceki receipt root/child/file GID'lerini readback edip root'u en son yeniden
seçer. Git revert cihazı güncellemez; aynı package/cert ve daha yüksek versionCode ile
yeni signed build gerekir. Uninstall/clear-data ile test geçirilmez.

## Resmî sözleşme referansları

- [Shopify Storefront access scopes](https://shopify.dev/docs/api/usage/access-scopes)
- [Shopify cartCreate 2026-07](https://shopify.dev/docs/api/storefront/latest/mutations/cartcreate)
- [Shopify metaobjects](https://shopify.dev/docs/apps/build/metaobjects)
- [Firebase Remote Config template/version](https://firebase.google.com/docs/remote-config/templates)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [Android 16 KB page-size support](https://developer.android.com/guide/practices/page-sizes)

Günlük işletim adımları ve rollback örnekleri
[`../operations/MULTI-BRAND-OWNER-OPERATIONS-TR.md`](../operations/MULTI-BRAND-OWNER-OPERATIONS-TR.md)
dosyasındadır.
