# Gate 9 İkinci Mağaza ve Sınırlı Medya Pilot Handoff'u

Durum: **IMPLEMENTATION CANDIDATE; GATE 9 AÇIK**  
Tarihsel provider/kabul kanıtı: 2026-09-17; final-review düzeltmesi: 2026-09-22 (Europe/Istanbul)
Exact taban: `5c402241e22a45001bdc57da6d08a83d2ce407cc`  
İlk handoff kaynağı: `033b69f38cd9df34f5a4b7a4d639afcbd4dce0c3`
Cihaz düzeltmesi kaynağı: `3ceb5a172dd829f9006a6f1af5116a7c5d0489b3`
Task 8 doğrulama/provider kaynağı: `f92857c8863a62a9a929efa3c9ef4279058d77e3`
Dal: `codex/gate9-second-store-media-pilot`

Bu kayıt owner-approved Gate 9 planının uygulanmış ve gerçekten çalıştırılmış
kısmını, başarısızlıkları ve çalıştırılmayan kabul satırlarını ayırır. Merge, PR,
Play yükleme/yayını, production signing, gerçek müşteri/sipariş/ödeme, Gürbakır
merchant içeriğine yazma veya yıkıcı provider işlemi yapılmadı.

Dokümantasyon commit'i kendi SHA'sını içeremez. Final review/PR adayı bu dosyanın
bulunduğu commit'i ve aşağıdaki implementation SHA'larını ancestor olarak
göstermelidir.

## Final-review Important düzeltme kaydı — 2026-09-22

Bu bölüm `ee899131a36cdf4e421ac885e22d86e1c644ecf6` üzerindeki yedi Important
bulgunun tek düzeltme dalgasını kaydeder. Önceki source/artifact/cihaz satırları
tarihseldir; yeni kaynak için otomatik kabul kanıtı değildir. Exact final-fix SHA,
artifact digest/imza ve ham RED/GREEN çıktıları ignored
`out/evidence/gate9/final-fix/` ledger'ında ve final-fix raporunda tutulur.

- Image/video Product CTA gerçek production navigation binding'ine Product GID
  verir; Collection handle davranışı değişmedi.
- Media3/OkHttp iç içe policy/cancellation nedenleri retry almaz; rejected rendition
  redirect zincirini yeniden başlatamaz. Meşru same-URL tek transport retry aynı
  attempt/byte budget içinde kalır.
- Player dikdörtgeni görünür alanı terk ettiğinde pending load iptal edilir,
  player pause/release olur; attempt/position korunur, dönüşte açık Play gerekir.
- V2 caption CRLF/CR → LF normalize edilir; LF dışındaki ISO kontrolleri ve
  mevcut uzunluk sınırı korunur. Remote, persistence ve restart testleri vardır.
- LKG optional target kimlikleri yeniden hydrate edilir; güncel handle kullanılır,
  missing target CTA'yı kaldırır ama geçerli medyayı kaldırmaz.
- Duckable/transient/permanent audio-focus kaybı pause olur; otomatik resume yoktur.
- Yalnız Home-v2 decode FIT/EXACT ile <=1600/<=2.56M pixel sınırında; görsel Crop
  sunumu ve app-wide/non-v2 Coil politikası değişmedi. Gerçek decode boyutları
  landscape/portrait/extreme oranlarda `1600×800`, `800×1600`, `1600×8`, `8×1600`.

Samsung `SM_A225F` / API33 / explicit `R68RC006LPE` üzerinde final focused Android
grubu **20 executed, 0 skip/failure** geçti. İlk koşudaki route-text beklentisi,
focus-acquisition yarışı ve 0ms-position fixture hataları gerçek kusurlardan ayrı
korundu; düzeltilmiş F1/F6 negative control yeniden RED üretip sonra GREEN geçti.
Kayıtlı JVM lane: **510 total, 505 executed, 5 conditional skip, 0 failure/error**.
Her kayıtlı JVM test task'i `--rerun` ile yeniden çalıştırıldı. Son tam static
lane (`spotlessCheck detekt lint`) ve **896 task** kayıtlı assemble lane geçti;
Maven DNS/eksik offline metadata denemeleri ayrı başarısız altyapı kayıtlarıdır.
Kayıtlı API30 lane yeniden yürütülerek **199 total, 190 executed, 9 conditional
skip, 0 failure/error** geçti. İlk emülatör denemesi Android package/activity
servisleri bulunamadığı için test başlamadan başarısızdı; değişikliksiz tekrar
geçti, bu başlangıç hatası uygulama assertion failure olarak sunulmaz.
Kayıtlı API23 lane **153 total, 145 executed, 8 conditional skip,
0 failure/error** geçti. Koşullu atlamalar PASS sayılmadı.
Onboarding **240/240**, portability self-test **68**, synthetic fixture/package
**49/63**, Trial fixture/package **6/25**, projection/Firebase/registry kontrolleri
geçti. Bunlar provider readback veya hosted exact-SHA CI değildir.

Commit öncesi dirty-source minified Trial candidate aynı development certificate
ile normal `install -r` üzerinden kuruldu; UID `10279` ve firstInstallTime
`2026-09-17 19:08:21` korundu, Home launch/görsel smoke geçti. Bu yalnız
package/update sürekliliğidir; cart/customer session korunması test edilmedi.
Commit öncesi APK `release-ledger-precommit.json` içinde açıkça dirty-source diye
ayrılır; commit sonrası R8/package yeniden üretimi ve exact SHA/digest/certificate
eşlemesi final-fix ledger/raporunda ayrı tutulur. Eski artifact satırları yeni
kaynak için yeniden etiketlenmez.
Bu kanıtlar A8'in tamamını veya commerce/session izolasyonunu kapatmaz.

Bu dalgada provider yazısı, müşteri/sipariş/ödeme, Gürbakır merchant mutation,
production signing, Play, push/PR/merge, uninstall veya data clear yoktur.
**A4 FAIL; A5 NOT RUN/EXTERNALLY BLOCKED; A8/A9 PARTIAL; A14 NOT RUN** kalır.
Gate 9 AÇIK, P3-16 başlamamıştır. Deferred Minor/closure checklist bu dalganın
dışındadır; tarihsel A10/A12 kanıtları yeni candidate'e taşınmaz.

Yukarıdaki durum `80597a5` final-review anının tarihsel kaydıdır. Aşağıdaki
current-candidate tazelemesi daha yeni gerçek provider ve Samsung kanıtını ayrı
tutar; eski failure/skip kayıtlarını silmez.

## Current-candidate kabul tazelemesi — 2026-09-22

Release kaynağı ve artifact sınırı değişmedi: `80597a55899a6c16d5a43c7620e4de91578c2b70`
ve tree `597bfd01afe56d61072339ef4370542390669a04`. Bu tazelemedeki Firebase proof
yalnız debug instrumentation ve dokümantasyon/test kaynaklarıdır; minified release
kanıtı olarak kullanılmaz. Ham/redakte kanıt kökü
`out/evidence/gate9/continuation-80597a5/` olup Git'e alınmaz. Redakte current receipt
`current-acceptance-receipt.json` SHA-256
`df4ea99e1dc3fbe02380594670198cf234ad3094031a6eec8d6a23f99a33c05b`.

- Korunmuş gerçek Gürbakır staging girdileri ana checkout değiştirilmeden yalnız
  pilotun ignored yollarına provenance/hash kontrolüyle taşındı. Configured
  `stagingRelease` eski candidate → `80597a5` normal `install -r` güncellemesi aynı
  package, nonproduction certificate, UID `10284` ve first-install time
  `2026-09-17 19:09:35` ile geçti; arama sentinel'i veri silmeden korundu.
- Trial Headless checkout read/write kapsamları zaten açıktı. Tam cart query'sindeki
  `buyerIdentity.customer` alanı `unauthenticated_read_customers` gerektiriyordu.
  Trial mağazasındaki paylaşılan Headless izin yüzeyinde yalnız bu okuma kapsamı
  açıldı; customer write ve customer-tag read kapalı bırakıldı. Gürbakır mağazası
  değiştirilmedi. Aynı onaylı public client ile `OwnedStorefrontCartProofTest`
  **1/1 PASS**, `skipped=0`, `failure/error=0`, JUnit SHA-256
  `d8ac50c813b7186dec3a96a46c18489d42bfa118d855414db868f56894d75342`.
- Samsung `R68RC006LPE` üzerinde Trial release ürünü `TRY299.00 / Available`,
  add/cart/quantity/subtotal, Checkout Kit açılışı, güvenli Close Checkout dönüşü,
  retained-cart refresh ve son cleanup doğrulandı. Development storefront'un zorunlu
  parola sayfası görüldüğü için checkout iletişim/teslimat formu ve formu yeniden
  açma adımı çalıştırılmadı; sipariş/ödeme yoktur. A4 bu nedenle provider yaşam
  döngüsünde PASS, uçtan uca native satırda **PARTIAL**dır.
- Aynı handle iki configured release uygulamada farklı gerçek ürün verdi: Trial
  `Çekiç Dokulu Trial Seramik Kupa / TRY299.00`, Gürbakır `Çekiç Dokulu Bakır Kupa /
  TRY680.00`; product GID'leri de farklıdır. Trial sepetinde quantity 1 varken
  Gürbakır sepeti boş kaldı. Trial process force-stop/relaunch sonrasında quantity 1
  korundu, sonra yalnız Trial satırı kaldırıldı ve empty readback alındı. Bu logged-out
  cart/process-death izolasyonunu kapatır; müşteri login/logout/session izolasyonu A5'e
  bağımlı kaldığı için A9 bütünü **PARTIAL**dır.
- Trial debug runtime, tek doğru Firebase app/project binding'i, kapalı Messaging
  auto-init, gerçek Remote Config fetch/activate ve yalnız açık test eylemiyle FCM
  register→unregister/consent cleanup akışında Samsung'da **2/2 PASS** verdi. JUnit
  SHA-256 `65ab51c911b0dd0460157586661b1b8a42cf8183bee43732ca7140860da6091e`.
  Token/değer receipt'e veya loga alınmadı; bildirim gönderilmedi.
- User-assisted OTP ile gerçek Trial Customer Account PKCE callback'i uygulamaya
  döndü. Account, Profile ve Addresses gerçek client üzerinden okundu; profil
  alanları değiştirilmedi, adres listesi empty olduğundan yazma/silme yapılmadı.
  Orders gerçek empty döndü; güvenli order olmadığı için Order Detail `NOT RUN`.
  Force-stop/relaunch sonrasında oturum ve Account işlemleri geri geldi; Sign out
  ardından ikinci relaunch signed-out `Sign in` durumunu korudu. E-posta/OTP/token
  receipt'e alınmadı; kişisel değer içeren ara UI dump'ları kanıt özetinden sonra
  yerel ve cihaz geçici yollarından kaldırıldı. Doğal token-expiry beklenmedi veya
  zorlanmadı; A5 bütünü bu nedenle **PARTIAL**dır.
- Aynı `80597a5` Trial release ve aynı payload/retained-cache şartında A12 yeniden
  ölçüldü. Cold-start ms `[405,417,405,395,408]`, median `405`, max `417`;
  UI warm-cycle ms `[11038,10882,10909,10867,10895]`, median `10895`, max `11038`;
  eşleşen ExoPlayer Init→Release ms `[8847,8699,8667,8655,8692]`, median `8692`,
  max `8847`. UI cycle dump overhead içerir; P95 veya first-frame SLO iddiası yoktur.
- Acceptance-only kaynak deltası için `spotlessCheck`, tüm module `detekt`, Trial
  developmentDebug lint/unit/AndroidTest assembly **PASS** (`330` task; `23`
  executed, `307` up-to-date); public readiness **23/23**, repository portability
  **46/46**, projection/onboarding read-only Validate ve delta secret taraması PASS.
  DevelopmentRelease merged manifest debug-only Messaging opt-in'i içermiyor;
  DevelopmentDebug içeriyor. Gerçek ignored Gürbakır staging girdileri varken
  `Test-MultiBrandOnboarding -Suite All` bunları overwrite etmeyi reddedip
  `DESTINATION_EXISTS` ile durdu; girdiler silinmedi/taşınmadı ve bu koşu PASS
  sayılmadı. Aynı operator kaynağının `80597a5` exact-SHA geniş lane kanıtı korunur.

### Current-candidate A1–A14 matrisi

| ID | Güncel durum | Current-candidate kanıt ve kalan sınır |
|---|---|---|
| A1 | **PASS** | Temiz izole pilot, gerçek Gürbakır staging ve Trial provenance; kirli main/old worktree değişmedi |
| A2 | **PASS** | Korunmuş gerçek Trial v1 Plan→Apply→Readback ve zero-write tekrar; yeniden üretilmedi |
| A3 | **PASS (identity smoke only)** | Trial debug identity launch alt satırı geçti; geniş commerce parent'ı bu etiketle kapatılmaz |
| A4 | **PARTIAL** | Exact cart lifecycle 1/1 PASS; fiziksel add/cart/Checkout Kit/return/cleanup PASS; development-store parola kapısı sonrası checkout formu NOT RUN |
| A5 | **PARTIAL** | Gerçek PKCE + user-assisted OTP, profile/address/order read, process-restart restore, logout ve post-logout restart PASS; address/order empty, Order Detail NOT RUN; natural token expiry NOT RUN; yazma/silme yok |
| A6 | **PASS** | Exact v2 codegen/actual-client ve strict Gürbakır-v1/Trial-v2 cutover korunur |
| A7 | **PASS** | HTTP/attempt/cache/revision kaynak, JVM ve gerçek data-source kanıtı korunur |
| A8 | **PARTIAL** | Gerçek video, focus/visibility/pause/mute/completion ve data-source kanıtı var; physical noisy/rotation/deadline matrisi bütünü çalıştırılmadı |
| A9 | **PARTIAL** | Same-handle, ayrı ürün/price, ayrı cart ve Trial process-death/cleanup PASS; authenticated session/logout izolasyonu A5'e bağlı |
| A10a | **PASS** | Trial v1 minified baseline ve kendi nonproduction imzası korunur |
| A10b | **PASS** | Güncel Trial `80597a5` release, v1→v2 package/imza/UID/first-install ve veri-silinmeden update kanıtı |
| A10c | **PASS** | Gerçek configured Gürbakır staging baseline→`80597a5` candidate, aynı package/imza/UID/first-install ve sentinel sürekliliği |
| A11 | **PARTIAL** | Geçmiş local static/JVM/build/API23/API30 PASS; current delta Spotless/detekt/Trial lint+unit+AndroidTest assemble/public23/portability46/projection PASS; configured-worktree `Suite All` overwrite guard nedeniyle NOT PASS; hosted exact-SHA CI yok |
| A12 | **PASS (bounded current measurement)** | Yukarıdaki yeni beş cold/beş warm raw/median/max; P95 yok |
| A13 | **PASS (bounded rehearsal)** | Root-last/change/remove/rollback/zero-write receipts korunur; silme veya yeniden prova yok |
| A14 | **NOT RUN** | Push/PR/merge yetkisi verilmedi; H/M ve hosted exact-SHA CI yok |

Gate 9 hâlâ AÇIK; A5 doğal-expiry alt satırı, A8'in çalıştırılmayan fiziksel alt
satırları, A9 cross-app authenticated session izolasyonu, hosted exact-SHA CI ve
owner merge/merged-main kanıtı tamamlanmadı. P3-16 ayrı ve başlamamıştır.

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
- Task 7'deki Shopify plugin reauthentication engeli tarihsel kayıttır. Task 8'de
  Trial Shopify CLI/Admin erişimiyle gerçek child revision, root-last publish,
  remove ve exact baseline rollback provası tamamlandı; kaynaklar silinmedi.
- Mevcut owned Trial photo File yalnız image-empty fallback product'a bağlandı;
  değişmeyen renderer collection'ın first-product image fallback'ini de kullandı.
  Samsung'da refresh sonrası beş section ve gerçek product/collection CTA'ları
  görüldü. A4 aynı approved token ile yine `ACCESS_DENIED`; A8/A9 hâlâ PARTIAL.

Gate 9 bu dalda kapanmaz. A4 başarısızlığı, güvenli customer ve kalan zorunlu cihaz
kabul satırları, PR-head exact-SHA CI, owner merge ve merged-main exact-SHA CI
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

### Task 8 gerçek operator provası ve katalog readback'i

Ignored kanıt kökü (`E8`):
`out/evidence/gate9/task8/f92857c8863a62a9a929efa3c9ef4279058d77e3/`.
Shop `61252272257`, development/TRY kimliği ve beş definition exact compatibility
yeniden doğrulandı. Admin operations Shopify validator ile kontrol edildi; bundled
validator schema 2026-04 olduğu için exact mutation/input contract ayrıca canlı
2026-07 introspection ile doğrulandı. Raw body/token/media URL raporlanmadı.

Altı gerçek Trial yazısı: bağımsız image child `79673852033` oluşturma; uygulamanın
seçmediği probe root `79673884801` oluşturma; yalnız yeni child revision; primary
root'a doğrulanmış child'ı son yazıyla seçme (5 refs); o section'ı root refs üzerinden
kaldırma (4 refs); primary root'u en son exact baseline sırasına döndürme (5 refs).
Yeni File gerekmedi; mevcut READY banner File kullanıldı. Eski seçili child'lar
yerinde değiştirilmedi. Yeni child/probe root korunur ve primary tarafından seçilmez.

| Kanıt | Sonuç |
|---|---|
| Primary root GID | `gid://shopify/Metaobject/79655141505` |
| Baseline/rollback ordered child numeric IDs | `79655010433, 79654944897, 79655075969, 79654977665, 79655108737` |
| Baseline/rollback operator content digest | `d4afc442fef7f150154cb7c4ca9d20b4dd59a36ccf831d1e58af1c3845b9f965` |
| Probe parent `updatedAt`, child revision öncesi/sonrası | İkisinde `2026-09-17T17:24:24Z` |
| Probe child digest önce | `d554a590059fd08ba0a2534a5c6a6016e291833572e1ebd3cd99a582af73beac` |
| Probe child digest sonra | `5cca070b8c88843685f4c22ce901885d893865182ea2e15f28415512e475d381` |
| Rollback root `updatedAt` | `2026-09-17T17:25:30Z` |

Receipts: `baseline.json`, `child-revision-proof.json`, `publish-root-last.json`,
`remove-root-last.json`, `rollback-root-last.json`, `rehearsal-complete.json`.
Rollback receipt SHA-256:
`96d9127fe2727ed424d55f3e98fba69166a1994911b1406be25314bb14ef3d4a`.

Ayrı yedinci provider yazısı, mevcut READY owned photo File
`gid://shopify/MediaImage/24034074525825` ile önceden media'sız Trial fallback product
`gid://shopify/Product/7067655995521` arasında `fileUpdate.referencesToAdd`
association kurdu. Collection `300730876033` doğrudan image'sız kalır; mevcut
first-product fallback şimdi geçerli image taşır. Collection/price/availability/
publication veya renderer validation değiştirilmedi. `catalog-recovery.json`
önce/sonra GID'leri ve recheck sonrası yalnız bu association'ı `referencesToRemove`
ile geri alma yolunu kaydeder. Recovery çalıştırılmadı; File ve product korunur.

Katalog hydration sonrası final operator digest:
`e13f04f8268c38e53cb64310f534080d533936617e6f0c5380c36a2b65bcd4ce`.
İki final readback ve tekrar Plan: sıfır planned action/yazı, drift=false, aynı
baseline ordered refs. `final-zero-write-plan.json` SHA-256:
`461b232bc67f6008e69a9a6393dd51c78f69d015558244e7f4a91e6f8c4d92d3`.

**Digest sınırı:** E8 `public.sectionRevisionDigest`, exact public child-node query
JSON'ının SHA-256'sıdır; kendi seri içinde karşılaştırılır. Android canonical
revision digest'i veya önceki helper'ın `9b2d...` digest'i ile aynı algoritma
değildir. Hydrated image değişikliği final digest'i bilinçli değiştirdi.
`digest-provenance.json` bu sınırı korur; raw URL/body yalnız bellekte işlendi.

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
| **A3 PASS (identity smoke)** | Trial `developmentDebug`; Samsung API33 ve ayrı API30/API23 managed cihazlar | Trial-owned application/package/composition launch, Gürbakır olmadığı | `TrialLaunchTest`; Task 7 explicit serial, Task 8 registered managed lanes | İlk `OK (0 tests)` kabul edilmedi. Samsung, API30 ve API23'te ayrı ayrı 1 executed, 0 skipped/failure; release update ile karıştırılmaz |
| **A4 FAIL** | Aynı approved Trial Headless public client; config hash değişmedi | Catalogue/availability/TRY/cart/checkout uyumu; cart lifecycle temizlenmeli | Task 8 actual cart + Home proof tekrar yürütüldü | Home 1/1 PASS; cart 1 executed, 0 skipped, 1 failure: `graphql:ACCESS_DENIED`; SETUP REQUIRED, alternatif CLI-owned token kullanılmadı |
| **A5 NOT RUN / EXTERNALLY BLOCKED** | Trial Customer Account client kayıtlı; yetkili synthetic session/inbox yok | Login/logout/reopen/expiry/profile/address; order yoksa detail NOT RUN | Güvenli önceden yetkili test customer gerekir | Callback/client digest receipt mevcut; OTP/2FA bypass veya gerçek customer kullanılmadı |
| **A6 PASS** | Trial v2 projection + aynı gerçek approved public client | Exact schema/query/codegen, strict readers, Gürbakır-v1/Trial-v2 aynı compile; actual Home v2 readback | Task 8 `OwnedHomeV2ReadProofTest`, force-rerun/no-build-cache | `f92857c`; executed 1, skipped 0, failure 0; `E8/owned-a4-a6-junit/` ve exact contract hash tablosu |
| **A7 PASS** | Source/JVM fixtures; gerçek data-source instrumentation için Infinix X6817 | Wrong-type/PARTIAL/cache/revision; same-URL Range/seek/one retry; rejected rendition/redirect/budget/deadline/rebuild | JVM `Home*` suites + `HomePlaybackDataSourceTest` physical | `5354f5fad98622e1e09f5904656004cecb961df2`; JVM media suites PASS; physical data-source 3/3 PASS, serial evidence local |
| **A8 PARTIAL** | Trial gerçek video; Samsung API33 | Frame/time/pause/mute/completion; kalan seek/15s/0/focus/noisy/timeout/cancel/retry gerçek-player matrisi açık | Opt-in `OwnedHomeVideoPlayerTest` + Home/data-source explicit-serial runner | RED→GREEN; final 12 executed, 0 skipped/failure; gerçek first-frame/bytes, Pause/Mute/Unmute ve hatasız completion. Tam A8 PASS değildir |
| **A9 PARTIAL** | Gürbakır staging + Trial release side-by-side; Samsung API33 | Ayrı search history korunur; Trial refresh sonrası beş section ve catalogue CTA'ları açılır | Task 7 replacement/history; Task 8 gerçek refresh/scroll/UI readback | Trial collection 1 product/TRY199.00/Available, product detail photo1/1/Available; cart/session/logout/aynı-handle cross-app commerce ve tam cache matrisi NOT RUN |
| **A10a PASS** | Trial v1 release, source `b4cdc45...` | Minified v1 baseline ve nonproduction imza | `:trial:assembleDevelopmentRelease`, R8/package/signature runner | APK SHA-256 `3836e475...6f0bc`; cert `4a8a0f0f...dfc77`; package/code/name `com.projectapp134.multibrandtrial.dev / 1 / 0.1.0-trial-v1` |
| **A10b PASS (local continuity)** | Trial v1 `b4cdc45...` → v2 `b54ad305...`; Samsung API33 | Minified v2, code 1→2, aynı package/imza; `install -r`, veri silmeden | Release artifact validators + explicit-serial install/package/UI readback | `3836e475...6f0bc` → `ce16f823...19b93`; aynı cert/userId/firstInstallTime; `gate9-trial-continuity` korundu. Customer/cart migration kanıtı değildir |
| **A10c PASS (isolated pair)** | Gürbakır staging base `5c402241...` → candidate `b54ad305...`; protected real config kopyalanmadı | Aynı package/imzalı minified update-test pair; veri silmeden update | Nonproduction test imzası + explicit-serial `install -r` | `5c285437...173b4` → `4809918c...75d6b`; aynı cert/userId/firstInstallTime; `gate9-gurbakir-continuity` korundu. Final `3ceb5a1` Gürbakır artifact/runtime kanıtı değildir |
| **A11 PARTIAL (local lanes PASS)** | Tüm module/app'ler; local Windows runner | Static/JVM/assemble/package/manifest/DEX/permission/secret/projection; API23/30 ve exact-SHA CI | Registry-driven Gradle lanes + PowerShell validators | Task 8 static365/assemble896-task PASS; JVM505 total/5skip/0failure; API30 191total/185executed/6skip/0failure; API23 145total/140executed/5skip/0failure; package/validators PASS. Koşullu proof skip'leri korunur; GitHub exact-SHA CI NOT RUN |
| **A12 PASS (bounded measurement)** | Samsung API33; aynı final `3ceb5a1` release/root; retained cache, thermal status 0 | 5 process-cold launch + 5 full warm playback; raw/median/max; P95 yok | Explicit-serial `measure-cycles.ps1`; `am start -W` ve matching ExoPlayer Init/Release | Cold median411/max447ms; full playback median8674/max8862ms; aşağıdaki ham değerler ve ortam sınırlamaları geçerlidir; first-frame/performance-SLO kanıtı değildir |
| **A13 PASS (bounded rehearsal)** | Trial gerçek Admin CLI + approved public client + owner guide | Child revision; root-last publish/change/remove/exact rollback; zero-write Plan; simulation | E8 altı rehearsal yazısı, ardından ayrı bir catalog association; final readback/Plan | Parent updatedAt sabit/child digest değişti; rollback exact ordered refs/digest; resource deletion yok. Shared/brand-only flow açık SIMULATION_ONLY, sıfır Git/provider yazısı |
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

Task 8 aynı config SHA-256 `33a1c23e...b204` ile tekrar çalıştı:
`E8/owned-a4-a6.log` ve `owned-a4-a6-junit/`, cart JUnit SHA-256
`4c02ae8d2bbed6f0812e6ae389e69bdb9a130cb611dc2d7c8049772373db6111`.
İki actual test: Home PASS, cart FAIL, toplam 2 executed/0 skipped/1 failure;
`BUILD FAILED in 1m 38s`, `Owned cart create failed: graphql:ACCESS_DENIED`.
Onaylı Headless client yerine CLI-owned public token oluşturulmadı/substitute edilmedi.
Yetkili Headless operator'ü aynı client'ın checkout-write scope'unu düzeltip readback
etmeli ve değişmeyen testi tekrar yürütmelidir. Admin CLI content yetkisi cart scope
kanıtı değildir.

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

Bu Task 7 null-image bulgusu tarihsel olarak korunur. Task 8'de yukarıdaki bounded
Trial photo association bu önkoşulu giderdi: aynı final minified Trial app'te normal
Refresh sonrası beş aile UI ağacında/görüntüde bulundu. Collection CTA bir ürünlü
Pilot Koleksiyonu listesine; product CTA Pilot Ürün detayına açıldı (TRY199.00,
Available, photo1/1). `E8/catalog-refresh-top.xml`, `catalog-middle.xml`,
`catalog-product-ready.xml`, `catalog-video-visible.xml`, `collection-opened.xml`,
`product-opened.xml` ve iki PNG kanıttır. Add to cart tıklanmadı; A4/A9 tamamlanmadı.
Task 7 A12 ölçümleri eski sabit payload'a aittir, Task 8 içeriği için tekrar ölçülmedi.
Mute testi paused-state control geçişini kanıtlar; audible-playing mute kanıtı değildir.

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
| Registry-driven assemble lane | Task 8 PASS; 14 görev, 896 Gradle işi; 69 executed, 3 cache, 824 up-to-date; 16m15s |
| Onboarding all suite | PASS 240/240 |
| Public readiness self/current | PASS 19/19 ve 24/24 |
| Repository portability self/current | PASS 68 fixture ve 47 check |
| Synthetic package self/current | PASS 49 fixture ve 63 check |
| Trial package self/current | PASS 6 fixture ve 25 check |
| Firebase config isolation | PASS; iki ignored Trial config, tek isolated project/profile |
| Gitleaks | Task 8 PASS; 79 commit, yaklaşık 7,72 MB, leak yok |
| Gerçek Trial Home v2 public-client | PASS; 1 executed, 0 skip, 0 failure |
| Gerçek Trial cart lifecycle | **FAIL**; 1 executed, 1 failure, `ACCESS_DENIED` |
| API 30 instrumentation | Task 8 PASS, 25m02s/470 görev; JUnit 191 total, 185 executed, 6 skipped, 0 failure/error |
| API 23 instrumentation | Task 8 PASS, 7m19s/317 görev; JUnit 145 total, 140 executed, 5 skipped, 0 failure/error |

Ek olarak tarihsel `Test-Phase2Foundation.ps1` çalıştırıldı ve 16 PASS/10 FAIL
döndürdü. Repository'nin kendi audit'i bu scripti `HISTORICAL EVIDENCE / SUPPORT`
olarak sınıflandırır; canonical workflow çağırmaz. Fail'ler eski altı-module ve kaldırılmış
Phase 2 proof-controller varsayımlarıdır. Sonuç `PREEXISTING historical validator drift`
olarak kaydedilir; güncel A11 PASS kanıtı gibi kullanılmaz.

Task 8 static tekrar: 365 görev, 5m44s; registry JVM tekrar: 264 görev, 1m33s,
505 test/0 failure/error/5 conditional skip. Actual A4/A6 opt-in koşusu bu koşullu
skip'lerden ayrı yürütüldü ve A4 failure gizlenmedi. E8 canonical log/JSON'ları
kaynak `f92857c` için tutulur. Onboarding/projection/Firebase/public/portability ve
dört lane enrollment tekrarları exit0 verdi. Portability scriptinin iç Gradle
`projects` sorgusu assemble sürerken başlamıştı; sonraki managed lane'ler seri
yürütülür. Bu yardımcı sorgu ayrı bir build/runtime kabulü sayılmaz.

Managed lane'ler mevcut AEHD2.2 ve mevcut API30 `aosp_atd`/API23 `default` image'ları
ile yürüdü; yeni virtualization kurulmadı. API30 Account7, Storefront7, core134,
app30, Synthetic12, Trial1; API23 core132, Synthetic12, Trial1 JUnit test kaydı
vardır. Her iki lane'de owned-media3 ve opt-in synthetic process-proof2 skip;
API30'da ayrıca configured Firebase proof1 skip vardır. Bunlar gerçek media,
process-restart veya configured provider PASS sayılmaz. Konsol finished sayıları
skip callback'lerini ek saydığı için final XML totals kullanıldı. `E8/api30-counts.json`,
`api23-counts.json`, copied `*-junit/`, exact task list/log/result dosyaları kanıttır.
Trial identity smoke iki lane'de de gerçekten 1/1 yürüdü. Bunlar local execution'dır,
PR-head veya merged-main GitHub CI değildir.

İlk ignored package-helper self-test çağrıları tek-string argument splat hatasıyla
(`Variant '-'`) test başlamadan exit1 verdi; actual package kontrolleri geçti.
Argument array düzeltildikten sonra tam self/current tekrarları geçti. Orijinal
`package-*.log`/results ve `package-repeat-*` birlikte korunur; product source
değişmedi. Final Trial signature verify PASS; META-INF coverage uyarıları receipt'te
korunur, production-signing kabulü olarak kullanılmaz.

## Release ledger

| App/profile | version | Source | Artifact SHA-256 | Signing certificate SHA-256 | Home/API | Dağıtım |
|---|---|---|---|---|---|---|
| Trial developmentRelease v1 | `1 / 0.1.0-trial-v1` | `b4cdc45f696a314252a6be4d597c336774d65b97` | `3836e475f801e8a03a55c3dad30b007aad14ee6b69ceceda0d02da776106f0bc` | `4a8a0f0f9e15583d7212c588fb043a86583666b2cea8dada38150947889dfc77` | v1 / 2026-07 | Local only; minified; Samsung baseline install PASS |
| Trial developmentRelease v2 | `2 / 0.2.0-trial-v2` | `b54ad305f4a7ec19e2e44b4db731249796d123c6` | `ce16f823b2b4b5e9adcb6773e3e0f2eeb58949558e97b0e799a7f4b1fc419b93` | aynı Trial nonproduction cert | v2 / 2026-07 | Local only; minified; Samsung replacement/local continuity PASS |
| Trial developmentRelease v2 player fix | `2 / 0.2.0-trial-v2` | `3ceb5a172dd829f9006a6f1af5116a7c5d0489b3` | `0d81d0019db04269c42e9c8d89919346c46529e36659478f135982bf3398c57c` | aynı Trial nonproduction cert | v2 / 2026-07 | Local same-version replacement/local continuity PASS; A12 artifact |
| Trial developmentRelease Task 8 rebuild | `2 / 0.2.0-trial-v2` | `f92857c8863a62a9a929efa3c9ef4279058d77e3` | `0d81d0019db04269c42e9c8d89919346c46529e36659478f135982bf3398c57c` | aynı Trial nonproduction cert, fresh apksigner verify | v2 / 2026-07 | Aynı byte-identical APK; Task 8 yeniden install yapmadı; mevcut Samsung app'te beş-section/CTA readback |
| Gürbakır staging Task 8 unsigned build | `1 / 0.1.0` | `f92857c8863a62a9a929efa3c9ef4279058d77e3` | `bca972f924d65bb15600217d733db08a96916b472bf78dde2a3482cbacf8f7a8` | Yok; unsigned | v1 / 2026-07 | Local R8 build; provider-configured final runtime/update acceptance değildir; install edilmedi |
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
| 8. Full validation/owner/handoff | Kısmi | A13 gerçek rehearsal/rollback ve owner simulation tamam; A4 FAIL, A5/A8/A9 ve exact-SHA CI açık |

## Gate 9'u kapatmak için kalanlar

1. Onaylı Trial Headless client'ın `unauthenticated_write_checkouts` kapsamını
   ver/readback et; client/token substitute etmeden A4 cart testini 1/1 PASS yap.
2. Veri silmeden A8'in kalan gerçek-player koşullarını, A9 commerce/session/logout
   izolasyonunu tamamla. Yerel API23/API30 lane'leri geçti; koşullu proof skip'leri
   kendi prerequisites ve ayrı acceptance kapsamları olmadan PASS yapılmaz.
3. Güvenli önceden yetkilendirilmiş synthetic customer/session/inbox ile A5'i
   tamamla. Task 8 A13 gerçek rollback ve katalog image önkoşulu tamamdır; yeni
   resource deletion veya mevcut receipt'i aşan provider yazısı gerekli sayılmaz.
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
- Task 8: root baseline ordered refs'e geri döndü; iki yeni rehearsal metaobject'i
  korunur/unselected. Mevcut photo File'ın Trial fallback product association'ı
  korunur; gerektiğinde `E8/catalog-recovery.json` ile recheck/association-only
  recovery yapılabilir. Toplam 7 yazı; silme yok.
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
