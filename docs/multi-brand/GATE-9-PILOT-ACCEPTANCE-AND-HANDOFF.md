# Gate 9 İkinci Mağaza ve Sınırlı Medya Pilot Handoff'u

Durum: **IMPLEMENTATION CANDIDATE; GATE 9 AÇIK**  
Kanıt tarihi: 2026-09-17 (Europe/Istanbul)  
Exact taban: `5c402241e22a45001bdc57da6d08a83d2ce407cc`  
İlk handoff kaynağı: `033b69f38cd9df34f5a4b7a4d639afcbd4dce0c3`
Cihaz düzeltmesi kaynağı: `3ceb5a172dd829f9006a6f1af5116a7c5d0489b3`
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
- İlk handoff'taki ADB kopması tarihsel durumdur. Sonraki Task 7 çalışması yalnız
  Samsung `SM_A225F` / Android 13 / API 33, serial `R68RC006LPE` üzerinde yürütüldü;
  aşağıdaki yeni kanıtlar tarihsel Infinix kanıtıyla karıştırılmaz.
- Trial ve ayrı Gürbakır staging minified update-test çiftleri `install -r` ile
  veri silmeden güncellendi; ayrı yerel arama geçmişleri update/process-death sonrası
  korundu. Cart/customer session izolasyonu bununla kanıtlanmış sayılmaz.
- Gerçek video decode edilirken görünür alan/controls eksikliği cihazda doğrulandı.
  Sınırlı aspect-ratio, açık controls/mute ve normal completion'ın hata olmaması
  düzeltildi; Trial launch 1/1 ve son player/Home/data-source grubu 12/12 geçti.
  İlk 10-test grubundaki ayrı Home action failure kaydı korunur.
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
| **A3 PASS (API33 smoke)** | Trial `developmentDebug`; Samsung `R68RC006LPE` | Trial-owned application/package/composition launch, Gürbakır olmadığı | Explicit-serial AndroidJUnitRunner `TrialLaunchTest` | İlk runner `OK (0 tests)` kabul edilmedi; eklenen gerçek smoke 1 executed, 0 skipped/failure. API23/API30 yerine geçmez |
| **A4 FAIL** | Trial public client; ürün/collection/Menu/Home okunuyor | Catalogue/availability/TRY/cart/checkout uyumu; cart lifecycle temizlenmeli | `OwnedHomeV2ReadProofTest` PASS; `OwnedStorefrontCartProofTest` gerçek Trial flag'iyle çalıştı | Home product `TRY`, public product available; cart test `executed=1`, `failures=1`, `ACCESS_DENIED`; neden `SETUP REQUIRED` (`unauthenticated_write_checkouts`) |
| **A5 NOT RUN** | Trial Customer Account client kayıtlı; güvenli test customer oturumu yok | Login/logout/reopen/expiry/profile/address; order yoksa detail NOT RUN | Fiziksel client/PKCE runner gerekir | Callback/client digest receipt mevcut; kullanıcı journey çalışmadı |
| **A6 PASS** | Trial v2 projection + gerçek public client | Exact schema/query/codegen, strict readers, Gürbakır-v1/Trial-v2 aynı compile; actual Home v2 readback | Onaylı plandaki `OwnedHomeV2ReadProofTest` komutu, Windows local runner | `7122c0b8a96101e4e014c61116de9efecc12fada`; executed 1, skipped 0, failure 0; exact file hash tablosu |
| **A7 PASS** | Source/JVM fixtures; gerçek data-source instrumentation için Infinix X6817 | Wrong-type/PARTIAL/cache/revision; same-URL Range/seek/one retry; rejected rendition/redirect/budget/deadline/rebuild | JVM `Home*` suites + `HomePlaybackDataSourceTest` physical | `5354f5fad98622e1e09f5904656004cecb961df2`; JVM media suites PASS; physical data-source 3/3 PASS, serial evidence local |
| **A8 PARTIAL** | Trial gerçek video; Samsung API33 | Frame/time/pause/mute/completion; kalan seek/15s/0/focus/noisy/timeout/cancel/retry gerçek-player matrisi açık | Opt-in `OwnedHomeVideoPlayerTest` + Home/data-source explicit-serial runner | RED→GREEN; final 12 executed, 0 skipped/failure; gerçek first-frame/bytes, Pause/Mute/Unmute ve hatasız completion. Tam A8 PASS değildir |
| **A9 PARTIAL** | Gürbakır staging + Trial release side-by-side; Samsung API33 | Ayrı search history update ve process-death sonrasında korunur | İki APK `install -r`, UI hierarchy, force-stop/reopen | Her app yalnız kendi sentinel'ini gösterdi; cart/session/logout/aynı-handle commerce ve tam cache matrisi NOT RUN |
| **A10a PASS** | Trial v1 release, source `b4cdc45...` | Minified v1 baseline ve nonproduction imza | `:trial:assembleDevelopmentRelease`, R8/package/signature runner | APK SHA-256 `3836e475...6f0bc`; cert `4a8a0f0f...dfc77`; package/code/name `com.projectapp134.multibrandtrial.dev / 1 / 0.1.0-trial-v1` |
| **A10b PASS (local continuity)** | Trial v1 `b4cdc45...` → v2 `b54ad305...`; Samsung API33 | Minified v2, code 1→2, aynı package/imza; `install -r`, veri silmeden | Release artifact validators + explicit-serial install/package/UI readback | `3836e475...6f0bc` → `ce16f823...19b93`; aynı cert/userId/firstInstallTime; `gate9-trial-continuity` korundu. Customer/cart migration kanıtı değildir |
| **A10c PASS (isolated pair)** | Gürbakır staging base `5c402241...` → candidate `b54ad305...`; protected real config kopyalanmadı | Aynı package/imzalı minified update-test pair; veri silmeden update | Nonproduction test imzası + explicit-serial `install -r` | `5c285437...173b4` → `4809918c...75d6b`; aynı cert/userId/firstInstallTime; `gate9-gurbakir-continuity` korundu. Final `3ceb5a1` Gürbakır artifact/runtime kanıtı değildir |
| **A11 PARTIAL** | Tüm module/app'ler; local Windows runner | Spotless/detekt/lint/JVM/assemble/package/manifest/DEX/permission/secret/strict keys; API23/30 ve canonical CI | Root Gradle lanes ve PowerShell validators | Static 365-task PASS; JVM 505 tests/0 failure/5 conditional skip; assemble 894-task PASS; onboarding 240/240; public 24/24; portability 47/47; synthetic 63/63; Trial 25/25; Gitleaks PASS. API23/API30 execution ve exact-SHA GitHub CI NOT RUN |
| **A12 PASS (bounded measurement)** | Samsung API33; aynı final `3ceb5a1` release/root; retained cache, thermal status 0 | 5 process-cold launch + 5 full warm playback; raw/median/max; P95 yok | Explicit-serial `measure-cycles.ps1`; `am start -W` ve matching ExoPlayer Init/Release | Cold median411/max447ms; full playback median8674/max8862ms; aşağıdaki ham değerler ve ortam sınırlamaları geçerlidir; first-frame/performance-SLO kanıtı değildir |
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

### Task 7 Samsung fiziksel cihaz kanıtı

Bu ek yalnız `R68RC006LPE`, inventory model `SM_A225F` / getprop `SM-A225F`,
Android 13/API33 üzerindedir. Bütün device komutları `adb -s R68RC006LPE` kullandı.
Eski Infinix A7 sonucu ayrı tarihsel kanıttır. Ignored kanıt kökleri:

- `out/evidence/gate9/device/d4fdbc08a378b908c44ab9b7a44978b366260a2b/`
  (`D1`): başlangıç kaynak checkout'u, release çiftleri, teşhis, RED→GREEN ve
  düzeltme öncesi/sonrası çalışma ağacı testleri. D1 altındaki yeni test/fix kanıtı
  temiz `d4fdbc08` artifact'ı gibi sunulmaz; final test tree'si `3ceb5a1` commit'idir.
- `out/evidence/gate9/device/3ceb5a172dd829f9006a6f1af5116a7c5d0489b3/`
  (`D2`): commit edilmiş final kaynak build/install/readback ve performans kanıtı.

Release update komutları normal `install -r` kullandı. Trial v1→v2 sırasında
userId `10279`, firstInstallTime `2026-09-17 19:08:21` korundu; versionCode 1→2
oldu. Gürbakır isolated staging baseline→candidate sırasında userId `10284`,
firstInstallTime `2026-09-17 19:09:35` korundu. Her ikisinde search üzerinden
önceden eklenen ayrı `gate9-trial-continuity` / `gate9-gurbakir-continuity`
sentinel'leri update ve ayrı force-stop/reopen sonrasında bulundu, diğer app'in
sentinel'i bulunmadı. `*-install.txt`, `*-package.txt`, `*-history.xml` ve
`*-after-death-history.xml` D1'de komut/UTC/çıktıyla korunur. Bu yerel search/Room
kanıtıdır; oturum, cart, OAuth veya tüm encrypted-store migration kanıtı değildir.

#### Player root cause ve kabul kapsamı

Salt-okunur `video-storefront-readback.json` gerçek Video
`gid://shopify/Video/24034075082881` kaynaklarını doğruladı. Seçilen owned MP4
1280×720; CDN HEAD `200`, `video/mp4`, `1702314` byte. İlk device `media.metrics`
240 AVC frame ve AAC decode gösterdi; ilgili app için crash/ANR bulunmadı.
Blank görünüm URL/erişim varsayımıyla sınıflandırılmadı. Scroll içindeki sınırsız
player yüksekliği ve varsayılan kapalı controls gerçek regresyonla ayrıştırıldı.
Görünür player aspect-ratio ile sınırlandı, controls/mute açıldı; normal completion
error/retry mesajına dönüştürülmez. `layout-visible.png` ilk layout düzeltmesinin
görünür sahne ve 00:01/00:08 timeline kanıtıdır, final release ile karıştırılmaz.

Provider root beş section ailesini hâlâ içerir. Tam scroll/UI readback ve exact
`HomeV2Resources` sorgusu, collection image ve fallback ilk-product image'ının,
ayrıca featured product image'ının `null` olduğunu gösterdi. Mevcut renderer'ın
güvenli image koşulu nedeniyle bu iki aile render edilmez. Bu provider-content
prerequisite eksikliğidir; mapper gevşetilmedi, provider'a yazılmadı. A4/A9 ticaret
kabulü tamamlanmış sayılmaz.

#### Instrumentation ve ayrı başarısızlık kaydı

Komut biçimi: `adb -s R68RC006LPE shell am instrument -w -r -e class <classes>
[-e ownedHomeMediaUrl <selected-owned-URL>] <test-package>/androidx.test.runner.AndroidJUnitRunner`.
Trial hedefi debug application'dır; core test APK ayrı test target'ıdır.
`ownedHomeMediaUrl` verilmezse üç owned-media testi koşullu skip olur; burada gerçek
public owned URL açıkça verildi, skip yoktur. Test APK'ları yalnız `install -r -t`
ile yüklendi. Bu instrumented sonuçlar minified release sonuçlarının yerine geçmez.

| D1 raw kayıt / JUnit | Gerçek sonuç | Yorum |
|---|---|---|
| `trial-instrumentation.txt` | 0 tests | İlk boş runner **NOT RUN**, PASS değildir |
| `trial-smoke.txt` / `.junit.xml` | 1 executed, 0 skipped/failure/error | Trial-owned package/application/composition smoke |
| `core-player-red.txt`, `core-player-red-2.txt` | Harness failure | v2 Compose effect wrong-thread, ardından build/install yarışı class-not-found; acceptance PASS sayılmaz |
| `core-player-red-3.txt` | 1 executed, 1 failure | Görünür Pause kontrolü yok; gerçek layout/controls RED |
| `core-player-green.txt` / `.junit.xml` | 10 executed, 1 failure, 0 skipped/error | Yeni player testi geçti; mevcut typed Home action testi `Product(1)` yerine null; ilk başarısızlık korunur |
| `home-action-repeat.txt` | 1 executed, 0 failure | Aynı Home action isolated tekrar geçti |
| `core-player-group-repeat.txt` / `.junit.xml` | 10 executed, 0 skipped/failure/error | Aynı grup temiz tekrar geçti; önceki failure flake olarak ayrı kalır |
| `core-mute-completion-red.txt` / `.junit.xml` | 3 executed, 2 failure, 0 skipped/error | Mute kontrolü ve completion-error RED; first-frame/bytes geçti |
| `core-final-media.txt` / `.junit.xml` | 12 executed, 0 skipped/failure/error | Owned media 3 + Home 6 + data-source 3; final GREEN |

JUnit dosyaları raw AndroidJUnitRunner status protokolünden oluşturulmuş özetlerdir;
`sourceLogSha256` taşır, per-test süre uydurulmaz. Özet SHA-256'ları:
Trial smoke `bafdfeb1ab136452b5c712d6428e9646b7a2565e588a1cceed31212d6de65db1`;
ilk 10-test failure `2dba4ab2785ddb4e0afa1bd9d6f23cb46c1038ec7a6847e7d9ca8f65f07d03f3`;
10-test repeat `3ad49e9270b21f82a5e65416536cc0287f5966f7a4606fdac28487e3bd8e1690`;
final 12-test `a9bf276837d24cf16d287f79c09db5a17c3b7179b29a8bd6d62951235383b94e`.

Commit sonrası D2 `final-core-regression.junit.xml` tekrar 12/12, 0 skipped/failure/error
(`ab9b8aadcc5cd76bbcfe2ca6d41cad0b9a4011cd6a0aaa8b04cd9ec251bdd63d`);
final rebuilt debug app/test `final-trial-smoke.junit.xml` 1/1, 0 skipped/failure/error
(`781bd67eb185268abdace23caeb277dac0916cc228c67a3b3084faf9d41c487e`).

Final focused kaynak kontrolü `spotlessApply :mobile-core:assembleDebugAndroidTest
:trial:assembleDevelopmentDebugAndroidTest :mobile-core:testDebugUnitTest
:trial:testDevelopmentDebugUnitTest :mobile-core:detekt :trial:detekt
:mobile-core:lintDebug :trial:lintDevelopmentDebug` geçti: 6m18s, 368 görev;
JVM XML toplamı 224 test, 0 failure/error/skip (`D1/final-focused-checks.log`).
Yanlış module-level Spotless task denemesi `player-fix-build.log` içinde task-not-found
olarak korunur; ardından root Spotless komutuyla build başarılıdır.

Exact source `3ceb5a1` üzerinde `spotlessCheck :trial:assembleDevelopmentDebug
:trial:assembleDevelopmentDebugAndroidTest :trial:assembleDevelopmentRelease`
PASS: 10m17s, 481 görev (34 executed, 447 up-to-date). Final package validator
25/25 geçti (`D2/final-package-validation.txt`). Final minified release
`install -r` ile `2026-09-17 20:01:43` güncellendi; aynı Trial cert, userId10279,
firstInstallTime ve history sentinel korundu (`final-release-package.txt`,
`final-release-history.xml`). R8 build ve instrumented debug kanıtları ayrıdır.

#### A12 ham ölçümler ve sınırlar

Kaynak/APK ledger'daki final `3ceb5a1` Trial release, aynı 8 s/1280×720 MP4 ve
aynı Home payload ile çalıştı. Önce/sonra root SHA-256
`675277a0723fa4a604a47de2b18e58c275d6c23a0aff964262422fa9553e6563`
eşit. App/Home disk cache korundu; cache/data clear yapılmadı, transport policy
değişmedi. Cold ölçümü yalnız process-cold'dur, fresh-install veya disk-cold değildir.
USB power, aynı bağlantı ve her çevrim öncesi/sonrası thermal status 0; battery
32.6–33.0°C, level39–40. Değerler doğal sıcaklık/cache değişimini gizlemez.

| Metrik (ms) | Sıralı 5 ham örnek | Medyan | Maksimum |
|---|---|---|---|
| Cold `am start -W TotalTime` | 447, 411, 406, 393, 437 | 411 | 447 |
| Warm matching ExoPlayer Init→Release (tam 8 s video + startup) | 8862, 8672, 8685, 8642, 8674 | 8674 | 8862 |
| Warm tap→gözlenen poster (UI dump overhead dahil) | 11120, 10859, 10775, 10825, 10757 | 10825 | 11120 |

D2 `Cold-raw.json`, `Warm-raw.json`, `Cold-summary.json`, `Warm-summary.json`,
`cold-*-start.txt`, `warm-*-player-log.txt`, termal/battery kayıtları ve
`warm-1-frame.png`–`warm-5-frame.png` korunur. Beş capture tek tek incelendi:
gerçek video sahnesi, Pause/Mute ve ilerleyen 00:01/00:08 timeline görünür;
beş çevrim de error mesajı olmadan postere döndü. Summary SHA-256'ları:
Cold `ce88e33bd9a394d5f77e163a3235cad694ff0979a88e691d0d98b90d393bb0ed`;
Warm `bd6adc9a6637868e31aec3d2c68c23581cc9169632188ab55408ab5ba88839ed`.

P95, first-frame latency, fps/jank, audio kalite ölçümü veya performans SLO PASS
iddiası yoktur. Final Trial exit-info yalnız beklenen install/force-stop kayıtlarını
gösterir, Trial crash/ANR bulunmadı. Device crash buffer ayrıca Samsung
`com.sec.android.diagmonagent` için background crash-loop gösterir (non-existent
ExceptionService job). Bu Trial process'i değildir; sistem uygulamasına müdahale
edilmedi. Ölçümler bu mevcut gürültülü cihaz koşulunun küçük örneklemidir, temiz
laboratuvar benchmark'ı veya regression budget karşılaştırması değildir.

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
| API 30 / API 23 instrumentation | NOT RUN; Task 7 fiziksel yürütme yalnız API33'tür |

Ek olarak tarihsel `Test-Phase2Foundation.ps1` çalıştırıldı ve 16 PASS/10 FAIL
döndürdü. Repository'nin kendi audit'i bu scripti `HISTORICAL EVIDENCE / SUPPORT`
olarak sınıflandırır; canonical workflow çağırmaz. Fail'ler eski altı-module ve kaldırılmış
Phase 2 proof-controller varsayımlarıdır. Sonuç `PREEXISTING historical validator drift`
olarak kaydedilir; güncel A11 PASS kanıtı gibi kullanılmaz.

## Release ledger

| App/profile | version | Source | Artifact SHA-256 | Signing certificate SHA-256 | Home/API | Dağıtım |
|---|---|---|---|---|---|---|
| Trial developmentRelease v1 | `1 / 0.1.0-trial-v1` | `b4cdc45f696a314252a6be4d597c336774d65b97` | `3836e475f801e8a03a55c3dad30b007aad14ee6b69ceceda0d02da776106f0bc` | `4a8a0f0f9e15583d7212c588fb043a86583666b2cea8dada38150947889dfc77` | v1 / 2026-07 | Local only; minified; Samsung baseline install PASS |
| Trial developmentRelease v2 | `2 / 0.2.0-trial-v2` | `b54ad305f4a7ec19e2e44b4db731249796d123c6` | `ce16f823b2b4b5e9adcb6773e3e0f2eeb58949558e97b0e799a7f4b1fc419b93` | aynı Trial nonproduction cert | v2 / 2026-07 | Local only; minified; Samsung replacement/local continuity PASS |
| Trial developmentRelease v2 player fix | `2 / 0.2.0-trial-v2` | `3ceb5a172dd829f9006a6f1af5116a7c5d0489b3` | `0d81d0019db04269c42e9c8d89919346c46529e36659478f135982bf3398c57c` | aynı Trial nonproduction cert | v2 / 2026-07 | Local same-version replacement/local continuity PASS; A12 artifact |
| Gürbakır staging baseline update-test | `1 / 0.1.0` | `5c402241e22a45001bdc57da6d08a83d2ce407cc` | `5c285437c450033af83ff67177600f4e88ecfd8d8157a599e84f55bd696173b4` | `ee90c9e1977458c66c8670e2e656a65ee8a760786407dd1e327607af5114e3a8` | v1 / 2026-07 | Isolated local update-test; real provider config yok; Samsung baseline install PASS |
| Gürbakır staging candidate update-test | `1 / 0.1.0` | `b54ad305f4a7ec19e2e44b4db731249796d123c6` | `4809918cb59d0dacdf563bc10c8d65d89dffa3a8c5f101a1e981bc0db4675d6b` | aynı isolated cert | v1 / 2026-07 | Isolated local update-test; real provider config yok; Samsung replacement/local continuity PASS |

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
| 6. HTTP/player | Kaynak/JVM tamam; cihaz kısmi | A7 PASS; A8 PARTIAL; Samsung RED→GREEN ve final 12/12 |
| 7. Release/two-app/device | Release update/local continuity ve bounded ölçümler tamam; acceptance kısmi | A3/A10a–c/A12 scoped PASS; A8/A9 PARTIAL |
| 8. Full validation/owner/handoff | Kısmi | Local canonical kontroller geçti; owner guide var; A4 FAIL, provider rehearsal/CI/device açık |

## Gate 9'u kapatmak için kalanlar

1. Shopify/Headless bağlantısını yeniden yetkilendir; Trial public client'a gerçek
   `unauthenticated_write_checkouts` kapsamını ver/readback et; A4 cart testini 1/1 PASS yap.
2. Veri silmeden A8'in kalan gerçek-player koşullarını, A9 commerce/session/logout
   izolasyonunu ve API23/API30 instrumentation'ı tamamla. Eksik collection/product
   image içeriğini ayrı yetkili provider diliminde düzelt/readback et.
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
- ADB: tarihsel Infinix A7 data-source 3/3 kaydı korunur. Task 7 yalnız Samsung
  `R68RC006LPE` üzerinde explicit target ile normal replacement install kullandı;
  uninstall, clear-data ve mevcut Gürbakır debug package'ına müdahale yok.

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
