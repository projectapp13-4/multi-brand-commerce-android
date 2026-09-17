# Multi-Brand Owner İşletim Rehberi

Durum tarihi: 2026-09-17  
Kapsam: Gürbakır gerçek mağaza uygulaması ile development-only Multi Brand Trial pilotu  
Yetki sınırı: Bu rehber merge, Play yükleme/yayını, production signing, gerçek ödeme/sipariş/müşteri değişikliği veya Gürbakır merchant içeriğine yazma yetkisi vermez.

## Bugün hangi uygulama ne yapıyor?

| Uygulama | Rol | Shopify Home | Android kimliği | Production durumu |
|---|---|---|---|---|
| Gürbakır `:app` | Gerçek marka ve gelecekte yayımlanacak uygulama | `mobile_home / 1 / gate7-v1` | Development ve staging kimlikleri migration-sensitive olarak korunur | P3-16 başlamadı; Play-ready değildir |
| Multi Brand Trial `:trial` | İkinci mağaza ve medya pilotu | `mobile_home_v2 / 2 / pilot-media-v2` | `com.projectapp134.multibrandtrial.dev` ve debug eki | Yalnız development; production'a yükseltilmez |
| Synthetic `:synthetic` | Provider'sız conformance fixture | Remote Home kapalı | Ayrı fixture kimliği | Merchant veya production uygulaması değildir |

İki gerçek uygulama aynı `:mobile-core` davranışını kullanır; marka, provider,
kalıcı veri ve sürüm seçimi application module'de yapılır. Runtime mağaza değişimi,
brand flavor veya marka adına göre shared-code dalı yoktur.

Trial bugün ürün/koleksiyon/Menu, Home v1 geçmiş checkpoint'i, Home v2 image/video,
Account bileşimi, legal/support sayfaları ve bağımsız Firebase yapılandırmasını taşır.
Home v2 kaynak, validator, cache ve player sözleşmeleri uygulanmıştır. Fiziksel cihaz
üzerindeki tam player/iki-app/update/performance kabulü ADB bağlantısı geri gelene
kadar `NOT RUN` durumundadır.

## Üç Shopify API'sinin görev ayrımı

| API | Kim kullanır? | Kullanım | Kesinlikle yapılmaması gereken |
|---|---|---|---|
| Admin GraphQL | Yalnız yetkili operator/Shopify Admin oturumu | Definition, metaobject entry, File, Page, publication ve scope yönetimi | Admin token'ı APK'ya, Git'e, rapora veya komut argümanına koymak |
| Storefront API | Android public mobile client | Ürün, koleksiyon, Menu, Home, cart ve checkout URL okuma/yazımı | Public token'ı private backend credential gibi varsaymak veya yetkisiz işlemi client'ta aşmaya çalışmak |
| Customer Account API | Giriş yapan müşterinin PKCE oturumu | Profil, adres ve sipariş okuma; izinli müşteri işlemleri | Customer token'ını Storefront/Admin token'ıyla karıştırmak veya düz metin depolamak |

Customer Account istemcisi mobil/public türdedir ve PKCE S256 kullanır. Access ve
refresh token'ları Android Keystore arkasındaki uygulama soyutlamasında kalır. Profil,
adres, sipariş ve uzak silme birbirinden ayrı operasyonlardır; uygulamadaki yerel veri
temizliği uzak müşteri silme sonucu değildir.

## Shopify katalog ve Menu işletimi

1. Ürün fiyatı, para birimi, varyant availability ve stok Shopify'da yönetilir.
   Android bunları canlı commerce truth olarak okur; Home snapshot'ı fiyat/stok otoritesi değildir.
2. Collection'a ürün eklerken ürünün ilgili Headless publication'da erişilebilir
   olduğunu da kontrol et.
3. Uygulamanın Categories kaynağı exact `main-menu` handle'ıdır. Menü entry'si yalnız
   desteklenen Collection hedeflerini taşımalıdır; keyfi URL uygulama rotası olmaz.
4. Trial fallback handle'ları `pilot-koleksiyonu` ve `pilot-urun`dur. Gürbakır
   içeriği Trial fallback'i olarak kullanılmaz.
5. Değişiklikten sonra Storefront public client ile ürün, koleksiyon ve Menu readback
   yap. Admin ekranında görünmesi tek başına Android runtime kanıtı değildir.

### Trial cart kurulum eksiği

17 Eylül 2026 gerçek Trial client koşusunda ürün/collection/Home ve `TRY`
okuması geçti, fakat `cartCreate` `ACCESS_DENIED` döndürdü. Güncel Shopify scope
sözleşmesinde Cart için `unauthenticated_write_checkouts` gerekir. Bu bir production
veya dış iş kararı değil, giderilebilir development kurulumu eksikliğidir.

Shopify bağlantısı yeniden yetkilendirildikten sonra:

1. Headless Storefront API izinlerinde `unauthenticated_write_checkouts` kapsamını
   Trial public client için etkinleştir ve değişikliği Shopify'dan readback et.
2. Token değerini ekrana/loga yazmadan ignored Trial local configuration'ını güvenli
   biçimde güncelle veya mevcut token'ın scope readback'ini doğrula.
3. Aşağıdaki gerçek testte `executed=1`, `skipped=0`, `failures=0` aranmadan A4'ü
   PASS yapma:

```powershell
.\gradlew.bat :storefront:testDebugUnitTest `
  --tests "com.gurbakir.storefront.OwnedStorefrontCartProofTest" `
  -PonboardingApplication=trial `
  -PonboardingProfile=development `
  -PonboardingRunOwnedStorefrontCartProof=true `
  --rerun-tasks --no-build-cache
```

Test yalnız geçici bir cart oluşturur, availability/TRY/checkout hostunu doğrular ve
satırları `finally` içinde kaldırır. Sipariş oluşturmaz, checkout'u tamamlamaz ve ödeme yapmaz.

## Home v1 ve Home v2 içerik modeli

Gürbakır v1 yalnız collection-grid ve featured-product ailelerini kabul eder. Trial
v2 bunlara en fazla iki image ve bir video ekler. V2 kökünde en fazla beş bölüm vardır:

- en fazla bir collection grid;
- en fazla bir featured product;
- en fazla iki `mobile_home_image_v1` (`banner` veya `photo`);
- en fazla bir `mobile_home_video_v1`.

Kök `mobile_home_v2/primary`, `schema_version=2`, doğru
`declared_section_count` ve sıralı `sections:list.mixed_reference` taşımalıdır.
Remote içerik yeni ekran, route, class, capability veya executable davranış tanımlayamaz.

### Banner/foto/video preflight

Yeni medya root'a bağlanmadan önce:

- File status `READY` olmalı;
- image/poster uygulama isteğinde en fazla 1600×1600 ve 2,56 milyon decode pixel,
  response bütçesinde en fazla 8 MiB olmalı;
- video MP4 olmalı, süre 1–60000 ms, original en fazla 25 MiB, çözünürlük en fazla
  1920×1080 olmalı;
- Android'in gördüğü rendition origin, MIME/container, dimension ve codec kurallarını
  geçmeli;
- poster ve erişilebilir `alt_text` bulunmalı;
- en çok bir Product veya Collection target seçilmeli.

Admin preflight'in başarılı olması Android player garantisi değildir. Ağ redirect,
Range/seek, cumulative byte budget, codec, ilk-kare ve buffering sınırları gerçek client
ve cihaz testlerinde ayrıca doğrulanır.

## Root-last yayın akışı

Her içerik değişikliği yeni revision olarak ele alınır:

1. Yeni media gerekiyorsa yeni File yükle ve yeni file GID'nin `READY` readback'ini al.
   Aynı file ID/URL üzerine byte replacement varsayılan olarak desteklenmez.
2. Yeni image/video/collection/featured child entry oluştur. Eski root'un kullandığı
   mutable child entry'yi yerinde değiştirme.
3. Child `updatedAt`, exact field type/value/reference, media source tuple ve target
   readback'ini al.
4. Mevcut root GID, ordered child GID listesi, child/file GID'leri ve
   `sectionRevisionDigest` değerini rollback receipt'ine kaydet.
5. Yeni root veya yeni ordered references listesini **en son** yayımla.
6. Public Storefront client ile root, child sırası, media ve Product/Collection
   hydration readback'i yap.
7. Aynı planı tekrar çalıştır; ikinci Apply `zero-write` olmadan idempotence PASS değildir.

Parent `updatedAt` tek başına yeterli değildir. Child `updatedAt`, normalize alanlar,
sıralı reference GID'leri ve media source tuple digest'e dahildir. Böylece parent tarihi
değişmeden child içeriği değişirse cache yenilenir.

### Değiştirme, kaldırma, empty ve expiry

- Bir bölümü kaldırmak için yeni ordered root listesinde o child GID'yi çıkar; root
  yine son yazıdır. Child/File otomatik silinmez.
- Bütün içeriği bilerek boşaltmak için geçerli kökte `declared_section_count=0` ve
  boş sections kullan. Bu `IntentionalEmpty`dir; eski snapshot ve player temizlenir.
- Yanlış root/type/version/order empty değildir. Fresh LKG korunur veya açık hata gösterilir.
- Geçici çözülemeyen media `NON_PLAYABLE` kalır; metin/GID saklanır ve sonraki refresh
  yeniden hydrate eder.
- Geçerli+hatalı karışım `PARTIAL`dır; yalnız geçerli bölümler saklanır.
- Tüm bölümler reddedilirse `NONE_RENDERABLE`; LKG üzerine yazılmaz.
- Section removal/change, intentional empty, 24 saat expiry veya revision değişimi
  eski coroutine, network load ve player işini iptal eder.

### Rollback

Rollback bir silme değildir:

1. Receipt'teki önceki root/child/file GID'lerinin hâlâ okunabildiğini doğrula.
2. Önceki ordered references listesini seçen yeni root-last yayın yap.
3. Public client readback ve zero-write repeat al.
4. Eski ve yeni kaynakları otomatik silme; deletion ayrı, yıkıcı ve ayrıca onaylanan iştir.

Kod rollback'i için Git revert cihazı kendiliğinden geri döndürmez. Aynı package ve doğru
imzayla daha yüksek versionCode taşıyan yeni bir forward build üretmek, test etmek ve normal
update yoluyla dağıtmak gerekir. Veri silerek rollback veya test geçirme yasaktır.

## Player ve ağ işletim sınırları

- Tek `PlaybackAttempt` toplam 32 MiB response-body bütçesi taşır.
- Aynı geçerli URL initial request, Range, seek ve izinli tek transport retry için
  kullanılabilir. Bunlar yeni bütçe açmaz.
- Kalıcı reddedilmiş rendition fallback'te tekrar seçilmez.
- Redirect zincirinde self-loop veya `A→B→A` ilk tekrar noktasında kesilir; en fazla
  beş redirect vardır.
- Forward buffer en fazla 15 saniye, back buffer 0'dır.
- Play→ilk kare en fazla 20 saniye; ilk kare sonrası tek kesintisiz buffering en fazla
  20 saniye, attempt toplamı en fazla 30 saniyedir.
- Pause/resume, rotation, player rebuild ve görünürlük dönüşü bütçeyi sıfırlamaz.
- Visibility loss, navigation, background, revision/remove/empty/expiry pending I/O'yu
  iptal eder ve player'ı bırakır; dönüşte autoplay yoktur.
- Audio-focus kaybı ve kulaklık çıkarılması pause eder; otomatik resume yoktur.
- Terminal hata veya deadline sonrası yalnız kullanıcı `Retry` yeni attempt açar.
- Autoplay, loop, preload, download, background service ve video byte cache yoktur.

## Legal, support, tracking ve deletion

Trial sayfaları development için provisional ve izlenebilirdir:

- `/pages/trial-destek`
- `/pages/trial-gizlilik`
- `/pages/trial-kullanim-kosullari`
- `/pages/trial-kargo`
- `/pages/trial-iade`
- `/pages/trial-yasal-bildirim`

Bu sayfalar production hukuki kabulü değildir. Trial tracking policy `denyAll()`dır;
doğrulanmış taşıyıcı domain'i olmadan dış tracking URL'si açılmaz. Uygulamada yerel veri
temizliği, merchant'a silme talebi, merchant acknowledgement/SLA ve gerçek uzak silme
ayrı kayıtlanır.

## Firebase işletimi

Trial development ayrı `projectapp134-multibrand-trial` projesi ve ayrı debug/release
Android app kayıtları kullanır. Gürbakır Firebase projeleri Trial için tekrar kullanılmaz.
Firestore, Hosting ve Functions bu pilotta kapalıdır.

Güvenli rutin:

1. Registry ve provider binding'den application/profile/project/app eşleşmesini oku.
2. Remote Config güncellemeden önce mevcut template'i ve version bilgisini al; değişikliği
   yalnız Trial project üzerinde planla.
3. Yazma sonrası template/version readback ve gerçek debug fetch/activate kanıtı al.
4. Hatalı template'te önceki version'a dön; proje/app silme yapma.
5. Messaging testi yalnız test device/token ile yapılır. Gerçek pazarlama bildirimi gönderme.
6. Analytics/Crashlytics veya yeni data product'ı mevcut scope'a sessizce ekleme.

Credential rotasyonunda önce yeni credential/client oluşturulur, ignored local/CI binding
güncellenir, readback ve app testi alınır; eski credential ancak ayrı güvenli retirement
adımıyla kaldırılır. Sırlar receipt, Git, chat, analytics veya APK'ya girmez.

## Shared değişiklik ile brand-only değişiklik ayrımı

| Değişiklik | Örnek | Dosya sahibi | Zorunlu regresyon |
|---|---|---|---|
| Shared UI/bugfix | Home player, cache, cart mapping, route guard | `mobile-core`, `storefront`, `foundation` | Gürbakır + Trial + Synthetic uygun lane'leri |
| Brand-only composition | Trial ad, legal URL, fallback, navigation capability | `apps/trial` ve Trial registry/profile | Trial unit/package; shared contamination denetimi |
| Gürbakır-only composition | Gürbakır resource veya app-owned provider seçimi | `app` ve Gürbakır projection | Gürbakır development/staging; migration-sensitive identity kontrolü |
| Provider content | Product/Menu/Home/File/metaobject | Shopify/Firebase; Git kaynak kodu değil | Admin readback + public/runtime readback + receipt |

Shared kodda `if (brand == "...")` ekleme. Yeni marka ayrı application module ve
registry kaydıyla eklenir. Her marka bağımsız application ID, datastore, database,
Keystore alias, OAuth callback, Firebase app ve release version taşır.

## Local build, GitHub CI ve bağımsız sürümler

Repo kökünden temel kontroller:

```powershell
.\gradlew.bat spotlessCheck detekt lint
pwsh -NoProfile -File scripts/Test-MultiBrandOnboarding.ps1 -Suite All
pwsh -NoProfile -File scripts/Test-PublicReadiness.ps1 -RequireCleanWorktree
pwsh -NoProfile -File scripts/Test-RepositoryPortability.ps1 -RequireCleanWorktree
```

Registry-driven lane'ler:

```powershell
$unit = @(& .\scripts\Get-RegisteredGradleTasks.ps1 -Lane unit)
.\gradlew.bat @unit

$assemble = @(& .\scripts\Get-RegisteredGradleTasks.ps1 -Lane assemble)
.\gradlew.bat @assemble
```

Paket doğrulamaları:

```powershell
pwsh -NoProfile -File scripts/Test-Gate2SyntheticPackage.ps1 -Variant All
pwsh -NoProfile -File scripts/Test-TrialPackage.ps1 -Variant All
```

GitHub `validate`, API 30 `instrumentation` ve API 23
`minimum-sdk-instrumentation` ayrı iş kanıtlarıdır. Yerel build, debug instrumentation,
PR head CI, merge ve merged-main CI birbirinin yerine geçmez.

Trial ve Gürbakır versionCode/versionName bağımsız artar. Her profile için yeni production
Shopify mağazası açmak gerekmez; environment/client/provider binding doğruysa aynı onaylı
gerçek Gürbakır mağazası production'da kullanılabilir. Trial hiçbir zaman production
profile'a dönüştürülmez.

## Release update kabulü

### Trial v1 → v2

- aynı package: `com.projectapp134.multibrandtrial.dev`;
- v1 `versionCode=1`, v2 `versionCode=2`;
- aynı kalıcı nonproduction test sertifikası;
- iki APK da minified/R8;
- `adb install -r` ile veri silmeden update ve commerce/media smoke zorunlu.

### Gürbakır staging baseline → candidate

- aynı staging package ve aynı imza gerekir;
- bu pilotta production key kullanılmaz;
- imza uyuşmazsa ana telefondaki veri silinmez; isolated nonproduction pair kullanılır;
- update sonrası cart/session/Home cache/logout/process-death veri sürekliliği ayrı kanıtlanır.

ADB yokken artifact/package/signature sonucu alınabilir; install/update/data-continuity
satırı `NOT RUN` kalır.

## Sorun ayırma rehberi

| Belirti | Önce bakılacak sınır | Yanlış teşhis |
|---|---|---|
| Ürün/Menu/Home okunmuyor | application/profile projection, Storefront domain/token/scope, publication | Firebase sorunu demek |
| `cartCreate ACCESS_DENIED` | `unauthenticated_write_checkouts` ve doğru Headless token | Cart kodunda güvenlik kontrolünü kaldırmak |
| Login callback dönmüyor | Customer Account client, discovery, PKCE callback, app scheme | Storefront token yenilemek |
| Remote Config gelmiyor | Firebase project/app binding, template/version, fetch/activate | Shopify cache temizlemek |
| Eski Home görünüyor | root/child/file digest, 24h TTL, request ordering, LKG kalite durumu | Fiyat/stok snapshot'ı yazmak |
| Video başlamıyor | File READY, rendition codec/origin, byte/deadline, visibility/audio focus | Admin preview'i runtime PASS saymak |
| Update kurulamadı | package, versionCode, signing fingerprint | Uygulama verisini/uninstall ile silmek |

## Yeni makine, yeni marka ve offboarding

Yeni makinede repository clone edilir; ignored secrets veya signing materyali Git'ten
beklenmez. Onaylı güvenli kaynaktan yalnız gerekli application/profile dosyaları alınır,
hash/provenance doğrulanır ve önce projection-only validation yapılır.

Yeni marka için ayrı application module, kimlik, profile, provider binding, OAuth client,
Firebase app/project kararı, persistence isimleri, legal/support kaynakları ve CI task
enrollment gerekir. Gürbakır veya Trial dosyalarını kopyalayıp yalnız ad değiştirmek yeterli
değildir.

Offboarding erişim devri sırasında credential owner, rotation/revocation sırası, provider
resource IDs, open receipts, CI secrets, signing custody ve incident contact kaydedilir.
Provider resource veya project otomatik silinmez.

## Release ledger şablonu

Her aday için şu alanları doldur:

| Alan | Değer |
|---|---|
| Application/profile | |
| versionCode/versionName | |
| Source exact SHA | |
| APK/AAB SHA-256 | |
| Signing certificate SHA-256 | |
| Storefront API/Home contract | |
| Provider target ve redakte receipt | |
| JVM/static/package sonucu | |
| Device model/API ve install yöntemi | |
| executed/skipped/failure sayıları | |
| Distribution status | Local only / test track / production |
| Rollback source/artifact/root receipt | |

Ledger'da token, private key, cart ID, customer token veya raw provider body bulunmaz.

## Resmî sözleşme bağlantıları

- [Shopify API access scopes](https://shopify.dev/docs/api/usage/access-scopes)
- [Shopify Storefront cartCreate](https://shopify.dev/docs/api/storefront/latest/mutations/cartcreate)
- [Shopify metaobjects](https://shopify.dev/docs/apps/build/metaobjects)
- [Firebase Remote Config templates](https://firebase.google.com/docs/remote-config/templates)
- [Firebase Cloud Messaging Android](https://firebase.google.com/docs/cloud-messaging/android/get-started)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [Android 16 KB page-size support](https://developer.android.com/guide/practices/page-sizes)

Gate 9 kabul durumu ve production'a kalanlar
[`../multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md`](../multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md)
dosyasında tutulur.
