package com.gurbakir.mobile

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.mobile.core.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreResourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun defaultAndTurkishLibraryResourcesRetainTitleAndPluralFormatting() {
        // French has no override, so it exercises the default Turkish resource table.
        for (languageTag in listOf("fr-FR", "tr-TR")) {
            val resources = localizedResources(languageTag)
            assertEquals("Ana Sayfa", resources.getString(R.string.home_title))
            assertEquals(
                "1 yeni sipariş özeti yüklendi.",
                resources.getQuantityString(R.plurals.order_list_added, 1, 1)
            )
            assertEquals(
                "3 yeni sipariş özeti yüklendi.",
                resources.getQuantityString(R.plurals.order_list_added, 3, 3)
            )
            assertEquals("1 ürün", resources.getQuantityString(R.plurals.wishlist_product_count, 1, 1))
            assertEquals("3 ürün", resources.getQuantityString(R.plurals.wishlist_product_count, 3, 3))
        }
    }

    @Test
    fun englishLibraryResourcesRetainTitleAndSingularPluralFormatting() {
        val resources = localizedResources("en-US")
        assertEquals("Home", resources.getString(R.string.home_title))
        assertEquals("1 new order summary loaded.", resources.getQuantityString(R.plurals.order_list_added, 1, 1))
        assertEquals("3 new order summaries loaded.", resources.getQuantityString(R.plurals.order_list_added, 3, 3))
        assertEquals("1 product", resources.getQuantityString(R.plurals.wishlist_product_count, 1, 1))
        assertEquals("3 products", resources.getQuantityString(R.plurals.wishlist_product_count, 3, 3))
    }

    @Test
    fun reusableAddressAndDeletionFallbacksRemainBrandAndMarketNeutral() {
        assertResourceValues(
            "tr-TR",
            mapOf(
                R.string.address_market_explanation to
                    "Bu sürüm desteklenen bölgedeki adresleri oluşturur ve düzenler. Adresler Shopify'da müşteri hesabınıza kaydedilir; cihazda kalıcı kopya tutulmaz.",
                R.string.address_empty_message to "Shopify hesabınıza ilk adresinizi ekleyebilirsiniz.",
                R.string.address_unsupported_country to
                    "Bu adres desteklenen bölgenin dışında. Bu sürüm adresi gösterir ancak düzenlemez veya varsayılan yapmaz.",
                R.string.address_create_heading to "Adres ekleyin",
                R.string.address_edit_heading to "Adresi düzenleyin",
                R.string.address_country_value to "Ülke: Desteklenen bölge",
                R.string.address_phone to "Telefon, E.164 (isteğe bağlı)",
                R.string.address_error_phone to "Telefonu E.164 biçiminde yazın.",
                R.string.address_error_postal_code to "Geçerli bir posta kodu yazın.",
                R.string.address_failure_unsupported_country to
                    "Bu adres desteklenen bölgenin dışında olduğu için bu sürümde düzenlenemez.",
                R.string.account_deletion_boundary to
                    "Uygulama Shopify müşteri hesabını doğrudan silemez. Destek kanalı üzerinden bir silme talebi iletebilir ve bu cihazdaki seçili verileri ayrıca temizleyebilirsiniz.",
                R.string.account_deletion_request_explanation to
                    "Hesap silme talebi sayfası tarayıcıda açılır. Sayfayı açmak talep göndermez; gönderim ve kimlik doğrulaması ayrı adımlardır. Bazı bilgiler geçerli saklama yükümlülükleri kapsamında tutulabilir.",
                R.string.account_deletion_identity_warning to
                    "Destek ekibi kimliğinizi doğrulamak için ek bilgi isteyebilir. Parolanızı veya tek kullanımlık doğrulama kodunuzu hiçbir forma ya da mesaja yazmayın.",
                R.string.account_deletion_open_request to "Hesap silme talebi sayfasını aç",
                R.string.account_deletion_request_unavailable to
                    "Bu uygulama için hesap silme talebi sayfası henüz doğrulanmadı.",
                R.string.account_deletion_confirm_message to
                    "Seçili yerel veriler geri alınamaz. Bu işlem uzak Shopify hesabını silmez ve destek kanalı üzerinden silme talebi göndermez.",
                R.string.account_deletion_feedback_opening to "%1\$s destek sitesinde açılıyor.",
                R.string.account_deletion_feedback_rejected to
                    "%1\$s adresi yapılandırılan güvenlik politikasıyla eşleşmedi ve açılmadı."
            )
        )
        assertResourceValues(
            "en-US",
            mapOf(
                R.string.address_market_explanation to
                    "This version creates and edits addresses in the supported region. Addresses are saved to your Shopify customer account; no persistent copy is kept on this device.",
                R.string.address_empty_message to "You can add your first address to your Shopify account.",
                R.string.address_unsupported_country to
                    "This address is outside the supported region. This version displays it but does not edit it or make it default.",
                R.string.address_create_heading to "Add an address",
                R.string.address_edit_heading to "Edit the address",
                R.string.address_country_value to "Country: Supported region",
                R.string.address_phone to "Phone, E.164 (optional)",
                R.string.address_error_phone to "Enter the phone in E.164 format.",
                R.string.address_error_postal_code to "Enter a valid postal code.",
                R.string.address_failure_unsupported_country to
                    "This address is outside the supported region and cannot be edited in this version.",
                R.string.account_deletion_boundary to
                    "The app cannot delete the Shopify customer account directly. You can send a deletion request through the support channel and separately clear selected data from this device.",
                R.string.account_deletion_request_explanation to
                    "The account deletion request page opens in your browser. Opening it does not submit a request; submission and identity verification are separate steps. Some information may be retained where applicable.",
                R.string.account_deletion_identity_warning to
                    "Support may request more information to verify your identity. Never enter your password or one-time verification code in any form or message.",
                R.string.account_deletion_open_request to "Open the account deletion request page",
                R.string.account_deletion_request_unavailable to
                    "The account deletion request page has not yet been verified for this app.",
                R.string.account_deletion_confirm_message to
                    "Selected local data cannot be recovered. This does not delete the remote Shopify account or send a deletion request through the support channel.",
                R.string.account_deletion_feedback_opening to "%1\$s is opening on the support site.",
                R.string.account_deletion_feedback_rejected to
                    "The %1\$s address did not match the configured security policy and was not opened."
            )
        )
    }

    private fun assertResourceValues(languageTag: String, expected: Map<Int, String>) {
        val resources = localizedResources(languageTag)
        expected.forEach { (resourceId, value) -> assertEquals(value, resources.getString(resourceId)) }
    }

    private fun localizedResources(languageTag: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
    ).resources
}
