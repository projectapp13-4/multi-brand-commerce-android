# Multi-Brand Owner İşletim Rehberi

Durum tarihi: 2026-09-17  
Kapsam: Gürbakır gerçek mağaza uygulaması ile development-only Multi Brand Trial pilotu  
Yetki sınırı: Bu rehber merge, Play yükleme/yayını, production signing, gerçek ödeme/sipariş/müşteri değişikliği veya Gürbakır merchant içeriğine yazma yetkisi vermez.

26 Eylül 2026 güncel gizlilik/sürüm işletim kararı için [Privacy and Release Operating Contract](PRIVACY-RELEASE-OPERATING-CONTRACT.md) geçerlidir. Türkiye-only modelinde Android ayrı tercih toplamıyor ve Storefront `visitorConsent` bilerek boş bırakılıyor; gerçek görev atamaları, production kimlik/imza/sağlayıcı kararı ve P3-16 yürütmesi hâlâ ayrı aşamalardır. Aşağıdaki eski kabul/rollback kayıtları kendi tarihsel kapsamını korur.

Güncel sorumluluk ayrımı: Shopify mağaza işlemleri, politika, ilk müşteri desteği ve gizlilik/silme süreci satıcıya aittir; Android mühendisliği ve teknik olay çözümü uygulama sağlayıcısına aittir. Play yayıncısı, yükleme anahtarı, Firebase yönetimi ve alan adı/App Links koordinasyonu marka dağıtım modeline göre belirlenir. Gürbakır için Play/imza/sürüm operasyonu proje tarafından yönetilir; satıcı sorumlulukları projeye aktarılmaz. Ayrıntı ve işlem sınırları aynı [işletim sözleşmesindedir](PRIVACY-RELEASE-OPERATING-CONTRACT.md).

Güncel Gate 9 kabulü 2026-09-24'te tanımlı nonproduction pilot kapsamında
kapandı; aşağıdaki 23 Eylül A5 PARTIAL/AÇIK satırları o günün tarihsel
checkpoint'idir. [Gate 9 handoff](../multi-brand/GATE-9-PILOT-ACCEPTANCE-AND-HANDOFF.md)
güncel A1–A14 kararını ve P3-16/remote-deletion sınırlarını kaydeder.

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
Home v2 kaynak, validator, cache ve player sözleşmeleri uygulanmıştır. Samsung
`SM_A225F` / API33 (`R68RC006LPE`) üzerinde veri silmeden minified update, ayrı
yerel geçmiş, bounded player ve beşer cold/warm ölçüm kanıtı vardır. İki-app
cart/session/logout kabulü PASS'tır. Owner'ın doğrudan manuel beyanıyla kablolu
kulaklık çıkarma ve Bluetooth kesme sırasındaki pause/no-auto-resume de PASS'tır;
bu son satır agent-captured artifact değildir. Trial Home refresh sonrası
beş section ve product/collection CTA'ları ayrıca doğrulandı. Ayrıntılı sınırlar
ve tarihsel ilk test flake'i Gate 9 handoff'unda korunur.

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

### Trial Customer Account kabulü

22 Eylül 2026'da user-assisted OTP ile gerçek Trial PKCE callback'i uygulamaya döndü.
Account, Profile ve Addresses okundu; profil değiştirilmedi, adres listesi empty olduğu
için adres yazma/silme yapılmadı. Orders gerçek empty döndüğünden Order Detail doğru
olarak `NOT RUN` kaldı. Force-stop/relaunch oturumu geri yükledi; logout ve ikinci
relaunch signed-out durumunu korudu. E-posta, OTP ve customer token kanıta alınmadı;
kişisel değer taşıyan geçici UI dump'ları doğrulama özetinden sonra kaldırıldı.

23 Eylül'deki ikinci user-assisted callback ve force-stop/relaunch da authenticated
döndü. Aynı cihazdaki Gürbakır staging Account signed-out kaldı; Trial oturumu marka
partition'ını aşmadı. Bogus sipariş reserved guest e-postayla oluşturulduğundan
Customer Account Orders yine gerçek empty kalmış ve bu sipariş Order Detail kanıtı
sayılmamıştır. Daha sonraki ayrı testte authenticated cart'ın hesaba bağlı olduğu ve
Checkout Kit'in kayıtlı müşteri kimliğini kullandığı doğrulandı. Yalnız Test Payment
Gateway ve sentetik teslimat verisiyle onaylanan sipariş Orders'ta göründü; ürün, TRY
tutarı, durum ve teslimat bölümü içeren Order Detail ilk okumada ve veri silmeden
force-stop/relaunch sonrasında tekrar PASS verdi. E-posta, OTP, adres, order ID ve token
receipt'e alınmadı; gerçek ödeme kullanılmadı. Redakte receipt SHA-256
`9ff23c5c15fac48ef089274dec1c13299e3f1c5a10addc7f329aee67e47175fb`.

Bu doğal token expiry kanıtı değildir. Güvenli doğrulama mevcut şifreli Trial oturumunu
korur, sadece gözlenen UTC zamanı ve token'dan türetilen `expiresAt` değerini kaydeder,
gerçek sürenin dolmasını bekler ve normal force-stop/relaunch restore akışını
çalıştırır. Başarılı refresh daha ileri `expiresAt` ve typed profile read ile;
terminal ret ise fail-closed signed-out durumuyla kanıtlanır. Cihaz saati, token dosyası
veya Keystore state'i değiştirilmez; token/PII rapora çıkarılmaz. Release uygulama
non-debuggable olduğu için hosttan exact `expiresAt` okunamıyorsa bu alt satır `NOT RUN`
kalır. Gerçek uzak deletion, merchant acknowledgement/SLA ve customer mutation
ayrıca kapsamlandırılır.

Exact adayın deterministic kaynak kanıtı ayrıdır: `CustomerAccountSessionCoordinatorTest`,
Shopify token-response parser ve `AccountControllerTest` birlikte 23/23, sıfır
failure/error/skip geçti. Bu testler near/expired refresh, token alanı rotasyonu,
transient retention, terminal clear ve cart-protection sözleşmesini kanıtlar;
gerçek provider zamanının dolduğunu kanıtlamaz.

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

### Trial cart scope düzeltmesi ve güncel kabul

17 Eylül 2026 gerçek Trial client koşusunda ürün/collection/Home ve `TRY`
okuması geçti, fakat tam `cartCreate` response seçimi `ACCESS_DENIED` döndürdü.
İlk kayıt yalnız genel hata kodunu bildiği için bunu tek başına checkout-write
eksikliği diye yorumlamak yeterli değildi.

Task 8'de aynı onaylı Headless configuration hash'iyle tekrar yürütülen test de
`executed=1`, `skipped=0`, `failures=1` ve `graphql:ACCESS_DENIED` verdi. Sonraki
resmî Headless yönetim yüzeyi readback'inde checkout read/write zaten açıktı. Tam
query'deki `buyerIdentity.customer` alanı `unauthenticated_read_customers`
gerektiriyordu. Trial mağazasındaki paylaşılan Headless izin yüzeyinde yalnız bu
okuma kapsamı açıldı; `unauthenticated_write_customers` ve
`unauthenticated_read_customer_tags` kapalı bırakıldı. Gürbakır mağazasına
dokunulmadı ve client/token substitution yapılmadı.

Güncel işletim sırası:

1. Exact approved Headless storefront'u, canonical Trial domain'ini ve değişmeyen
   public-client binding'ini doğrula.
2. Cart query değişirse, seçilen her nested alanın scope'unu ayrıca kontrol et;
   genel `ACCESS_DENIED` kodundan tek scope sonucu çıkarma.
3. Token değerini ekrana/loga yazmadan mevcut binding ve paylaşılan kanal scope
   readback'ini kaydet. Gerekmeyen customer write/tag kapsamlarını açma.
4. Aşağıdaki gerçek testte `executed=1`, `skipped=0`, `failures=0` aranmadan provider
   cart yaşam döngüsünü PASS yapma:

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
22 Eylül 2026 koşusu aynı client ile `1/1 PASS`, `skipped=0`, `failure/error=0` verdi;
JUnit SHA-256
`d8ac50c813b7186dec3a96a46c18489d42bfa118d855414db868f56894d75342`.

Samsung release yolunda ürün/TRY/availability, add/cart/quantity/subtotal, Checkout
Kit açılışı, Close Checkout dönüşü, retained-cart refresh ve cleanup geçti. 23
Eylül 2026'da owner'ın yalnız Bogus Gateway için verdiği açık test yetkisiyle
development-store parola kapısı cihazda geçildi. İlk açılış storefront home'a
çıktığı için PASS sayılmadı; uygulamaya dönülüp aynı retained cart yeniden
açıldı. Gerçek Contact/Delivery/Shipping/Order summary/Pay now formu ve açık
`Test Payment Gateway` görüldü. Reserved `example.com`, sentetik isim/adres ve
Bogus success değerleriyle `Thank you` / `Your order is confirmed` alındı;
Checkout kapatılınca uygulama `Checkout completed` gösterip completed-cart linkini
yerelden temizledi. Gerçek kart/ödeme/müşteri verisi ve Gürbakır yazısı yoktur.
Ignored receipt SHA-256
`02a58d518896b4ec6d0bd037d73675d184bc5ebebf9fa8b60f040ba316c7702b`;
A4 `PASS`tır. Bu guest sipariş tek başına Customer Account Order Detail kanıtı değildir;
ayrı authenticated account-linked prova Orders/Order Detail'i sonradan PASS etmiştir.

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

İlk definition/probe Plan/Apply/Readback için checked-in giriş noktası
`scripts/Invoke-MultiBrandOnboarding.ps1`, redakte receipt sözleşmesi ise
`config/onboarding/operator-receipt.schema.v1.json` dosyasıdır. Gerçek Home content
editörü şu sırayı Admin UI'da uygular: **Content → Metaobjects → child entry/File
READY readback → mobile_home_v2 root → ordered references → Save**. Root'u child/File
hazır olmadan kaydetme. Receipt en az verified target shop/domain, application/profile,
contract version, before/after root ve ordered child/file GID'leri, child `updatedAt`,
media source tuple, revision digest, planned/write counts, Admin/public readback,
zero-write tekrar ve recovery ordered-list alanlarını taşır. Token, raw provider body,
customer/cart verisi veya storefront parolası receipt'e girmez.

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

### 17 Eylül 2026 gerçek Trial provası

Task 8 ignored kanıt kökü:
`out/evidence/gate9/task8/f92857c8863a62a9a929efa3c9ef4279058d77e3/`.
Shop GID `61252272257` ve beş definition sözleşmesi yeniden doğrulandı. Altı gerçek
yazıyla yeni bağımsız image child `79673852033`, app'in seçmediği probe root
`79673884801`, child revision, primary root-last publish, root-ref removal ve exact
baseline ordered-ref rollback yürütüldü. Primary root `79655141505` önceki
`79655010433, 79654944897, 79655075969, 79654977665, 79655108737` sırasına döndü.
Child değişirken probe parent `updatedAt` aynı kaldı; public child digest değişti.
Oluşturulan kaynaklar korunur; silme yoktur. Yeni File gerekmedi, mevcut READY File
kullanıldı. `rehearsal-complete.json` ve `child-revision-proof.json` gerçek receipts'tir.

Ayrı yedinci yazı, mevcut owned photo File `24034074525825` ile image-empty Trial
product `7067655995521` arasında `fileUpdate.referencesToAdd` association kurdu.
Collection'ın ilk-product fallback'i de böylece geçerli image taşır; renderer
gevşetilmedi, collection/price/availability değiştirilmedi. `catalog-recovery.json`
önce/sonra GID'leri ve gerektiğinde yalnız association'ı geri alma yolunu kaydeder;
bu recovery yürütülmedi, File silinmez. Cihazda beş section ve iki CTA görüldü.

`final-zero-write-plan.json` iki readback arasında `plannedActionCount=0`,
`writeCount=0`, `drift=false` gösterir. Task 8 receipt digest'i exact public child-node
query JSON'ının SHA-256'sıdır; Android canonical `sectionRevisionDigest` veya eski
operator digest'iyle byte-eşit varsayılmaz (`digest-provenance.json`). Provider
rehearsal PASS, customer/cart veya A8/A9 kabulünü kendi başına PASS yapmaz.

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

22 Eylül 2026 Samsung debug proof'u tek Trial Firebase app/project binding'ini,
Messaging auto-init'in kapalı olduğunu, gerçek Remote Config fetch/activate ve yalnız
açık test eylemiyle FCM register→unregister/consent cleanup akışını `2/2 PASS`
doğruladı. JUnit SHA-256
`65ab51c911b0dd0460157586661b1b8a42cf8183bee43732ca7140860da6091e`.
Bu debug instrumentation kanıtıdır; release veya gerçek bildirim teslimi değildir.
Token/değer kaydedilmedi ve bildirim gönderilmedi.

Canlı Firebase proof'u standart CI veya normal configured instrumentation sırasında
kendiliğinden çalışmaz. `runTrialFirebaseRuntimeProof=true` açık opt-in'i yoksa iki
canlı test `NOT REQUESTED/SKIPPED` kalır; normal Trial kimlik testi çalışmaya devam
eder. Bu skip Firebase PASS değildir. Credential-free API 23/30 lane'lerinde beklenen
sonuç `TrialLaunchTest` PASS ve iki canlı proof SKIP'tir.

Configured Samsung kabulünde canlı proof yalnız exact sınıf ve opt-in birlikte
verilerek çalıştırılır:

```powershell
.\gradlew.bat :trial:connectedDevelopmentDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.projectapp134.multibrandtrial.TrialFirebaseRuntimeProofTest" `
  "-Pandroid.testInstrumentationRunnerArguments.runTrialFirebaseRuntimeProof=true" `
  --no-daemon --console=plain
```

Opt-in verildiğinde eksik/yanlış Firebase app/profile/config veya SDK hatası FAIL
olmalıdır; assumption/catch ile skip'e çevrilmez. Ordinary CI'a provider config veya
sır ekleme, Trial'ı matristen çıkarma ya da sınıfı silme. `fcbbe84` düzeltmesinde
provider-free API 23/30 normal lane'leri `1 PASS + 2 SKIP`, configured API 33 opt-in
koşusu `2/2 PASS`, provider-free opt-in negatif koşusu `2/2 FAIL` verdi.

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

Task 8 `owner-simulation.json` yalnız **SIMULATION_ONLY**'dir: varsayımsal shared
`HomeVideoPlayer.kt` değişikliği üç app/full registered unit lane'e; Trial-only
`strings.xml` değişikliği Trial unit/package ve contamination/projection denetimine
eşlendi. Bu simülasyon sıfır Git/provider yazısı yaptı; gerçek kaynak değişikliği
veya ayrı brand-only release gibi sunulmaz.

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

Mevcut ledger handoff'tadır: Task 7 Samsung Trial final minified source `3ceb5a1`
ile ölçüldü; Gürbakır isolated update-test pair daha eski `b54ad305` checkpoint'idir.
Sonraki build, final-source Gürbakır fiziksel runtime kabulünü kendiliğinden sağlamaz.
Task 8 katalog görseli sonrası eski A12 ölçümleri yeni payload'a yeniden etiketlenmez.

2026-09-22 final-review Important düzeltmesi yeni bir kaynak/artifact checkpoint'idir;
Task 7/8 SHA ve APK digest'leri bu yeni adayı tanımlamaz. Yeni adayın exact source,
APK SHA-256, nonproduction certificate ve normal-update kanıtı handoff'un
final-review kaydına ve ignored `out/evidence/gate9/final-fix/` ledger'ına bağlıdır.
20-test focused Android regression GREEN, A8'in tamamını veya A9 commerce/session
izolasyonunu kendi başına PASS yapmaz. 23 Eylül current-candidate durumunda A4/A8/A9
ve A5 Order Detail PASS; A5 exact natural-expiry nedeniyle PARTIAL kalır. PR #14
approved HEAD `bbf8ab82c644ce29782ab7037e4a9ca6ea2cfa9a` normal merge ile
`c77afedaa3c89735c4f1ea06d1fdeaf2820e7f94` oldu; PR-head run `35851940911`
ve merged-main run `35877081839` üç zorunlu lane'de başarılıdır. A14 PASS olsa da
Gate 9 A5 nedeniyle AÇIK kalır.

22 Eylül 2026 current-candidate tazelemesinde Trial `80597a5` release için beş cold
start ve beş full warm playback cycle yeniden ölçüldü; raw/median/max handoff'tadır,
P95 veya first-frame SLO iddiası yoktur. Gerçek configured Gürbakır staging candidate
aynı package/nonproduction imza/UID/first-install ile veri silmeden güncellendi ve
sentinel korundu. Aynı handle iki mağazada farklı ürün/fiyat verdi; Trial cart quantity
1 iken Gürbakır cart empty kaldı ve Trial process restart sonrasında quantity 1 korundu.
Sonraki Trial authenticated restore Gürbakır signed-out partition'ına taşmadı.
Ardından Gürbakır da user-assisted callback ile authenticated edildi; iki app restart
restore sonrası yalnız Gürbakır logout edildi. Ayrı force-stop/relaunch'ta Gürbakır
`Sign in`, Trial `Sign out/Profile/Orders/Addresses` durumunu korudu. Veri silinmedi,
PII/token/OTP kaydedilmedi; ignored receipt SHA-256
`168ba834ee56954a59c1de68aee8de3062b2614491f80a49078d8d2632cc8204`.
A9 `PASS`tır.

Aynı gün `91ae165` androidTest checkpoint'inde gerçek Samsung/API33 Activity
rotation'ı player'ı yeniden kurarken aynı PlaybackAttempt, ilk-kare ve kümülatif byte
durumunu korudu; dönüş paused kaldı ve açık Play gerektirdi. Gerçek video/focus/
visibility/pause/mute/completion + rotation grubu 7/7 geçti. Shell ile noisy broadcast
göndermek Android 13 system-only route-change olayı değildir; bu deneme PASS
sayılmadı. Fiziksel kulaklık çıkarma ve kasıtlı gerçek stall/deadline satırları
bu checkpoint'te çalıştırılmamıştı. Sonraki `a2c4ded` controlled-data-source/
real-player koşusu first-frame ve post-frame stall/deadline/iptal satırlarını kapattı;
Samsung/API33 birleşik grup 15/15 geçti. Bu debug instrumentation'dır, minified
release yolculuğu değildir. 23 Eylül'de owner, gerçek playback sırasında hem
kablolu kulaklık çıkarma hem Bluetooth kesme sonrası pause/no-auto-resume sonucunu
manuel PASS bildirdi. Bu doğrudan owner attestation'dır; agent'ın tekrar çalıştırdığı
veya artifact yakaladığı iddia edilmez. A8 bu birleşik kanıtla PASS'tır. Ayrıca
`a2c4ded` production player düzeltmesi içerdiği
için korunmuş `80597a5` APK kendi exact release kanıtı olarak kalır; yeni kaynağın
artifact'i diye yeniden adlandırılmaz. Normal `build/outputs` APK'sı ledger seçimi
yerine kullanılamaz. Exact `a2c4ded` için ayrı sealed Trial minified artifact'i
üretildi; aynı nonproduction imza/package/version ile `install -r`, Home/video smoke
ve beş cold/beş warm A12 ölçümü geçti. Aynı kaynak için gerçek configured Gürbakır
staging unsigned + nonproduction update-test artifact'leri de ayrı ledger'a alındı;
UID/first-install/Search sentinel korundu ve merchant/provider yazısı yapılmadı.
Bu local artifact'lerin hiçbiri production signing veya Play-ready kanıtı değildir.

## Sorun ayırma rehberi

| Belirti | Önce bakılacak sınır | Yanlış teşhis |
|---|---|---|
| Ürün/Menu/Home okunmuyor | application/profile projection, Storefront domain/token/scope, publication | Firebase sorunu demek |
| `cartCreate ACCESS_DENIED` | Doğru Headless client + checkout read/write + query'nin nested `buyerIdentity.customer` alanı için gereken customer-read scope | Genel hata kodundan tek scope çıkarmak veya cart kodunda güvenlik kontrolünü kaldırmak |
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
