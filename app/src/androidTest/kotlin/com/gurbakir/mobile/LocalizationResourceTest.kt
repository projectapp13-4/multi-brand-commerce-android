package com.gurbakir.mobile

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun turkishBaselineAndEnglishOverrideResolveFromTheSameProductionKey() {
        val turkish = localizedContext("tr-TR").getString(R.string.home_title)
        val english = localizedContext("en-US").getString(R.string.home_title)

        assertEquals("Ana Sayfa", turkish)
        assertEquals("Home", english)
    }

    @Test
    fun expansionPseudoLocaleTransformsProductionCopy() {
        val english = localizedContext("en-US").getString(R.string.home_featured_product_title)
        val expanded = localizedContext("en-XA").getString(R.string.home_featured_product_title)

        assertNotEquals(english, expanded)
        assertTrue(expanded.length > english.length)
        assertTrue(expanded.startsWith("["))
        assertTrue(expanded.endsWith("]"))
    }

    @Test
    fun rtlPseudoLocaleResolvesWithRtlLayoutDirection() {
        val rtlContext = localizedContext("ar-XB")
        val rtlCopy = rtlContext.getString(R.string.home_title)

        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, rtlContext.resources.configuration.layoutDirection)
        assertTrue(rtlCopy.isNotBlank())
        assertNotEquals("Home", rtlCopy)
    }

    @Test
    fun gurbakirBrandAndMarketOverlaysRemainExact() {
        assertResourceValues(
            "tr-TR",
            mapOf(
                R.string.address_market_explanation to
                    "Bu sürüm yalnızca Türkiye adresi oluşturur ve düzenler. Adresler Shopify'da müşteri hesabınıza kaydedilir; cihazda kalıcı kopya tutulmaz.",
                R.string.address_empty_message to "Shopify hesabınıza ilk Türkiye adresinizi ekleyebilirsiniz.",
                R.string.address_unsupported_country to
                    "Bu adres desteklenen Türkiye pazarının dışında. Bu sürüm adresi gösterir ancak düzenlemez veya varsayılan yapmaz.",
                R.string.address_create_heading to "Türkiye adresi ekleyin",
                R.string.address_edit_heading to "Türkiye adresini düzenleyin",
                R.string.address_country_value to "Ülke: Türkiye (TR)",
                R.string.address_phone to "Telefon, E.164: +90… (isteğe bağlı)",
                R.string.address_error_phone to
                    "Telefon numarasını ülke koduyla girin (ör. +905551234567).",
                R.string.address_error_postal_code to "Türkiye posta kodunu 5 rakam olarak yazın.",
                R.string.address_failure_unsupported_country to
                    "Bu adres Türkiye dışında olduğu için bu sürümde düzenlenemez.",
                R.string.account_deletion_boundary to
                    "Uygulama Shopify müşteri hesabını doğrudan silemez. Gür Bakır’a bir silme talebi iletebilir ve bu cihazdaki seçili verileri ayrıca temizleyebilirsiniz.",
                R.string.account_deletion_request_explanation to
                    "Gür Bakır hesap silme talebi sayfası tarayıcıda açılır. Sayfadaki formu göndermeniz gerekir; sayfayı açmak tek başına talep iletmez. Kimlik doğrulaması gerekebilir ve bazı bilgiler geçerli saklama yükümlülükleri kapsamında tutulabilir.",
                R.string.account_deletion_identity_warning to
                    "Gür Bakır kimliğinizi doğrulamak için ek bilgi isteyebilir. Parolanızı veya tek kullanımlık doğrulama kodunuzu hiçbir forma ya da mesaja yazmayın.",
                R.string.account_deletion_open_request to "Hesap silme talebi sayfasını aç",
                R.string.account_deletion_request_unavailable to
                    "Bu uygulama için hesap silme talebi sayfası henüz doğrulanmadı.",
                R.string.account_deletion_confirm_message to
                    "Seçili yerel veriler geri alınamaz. Bu işlem uzak Shopify hesabını silmez ve Gür Bakır’a silme talebi göndermez.",
                R.string.account_deletion_feedback_opening to "%1\$s Gür Bakır sitesinde açılıyor.",
                R.string.account_deletion_feedback_rejected to
                    "%1\$s adresi Gür Bakır güvenlik politikasıyla eşleşmedi ve açılmadı."
            )
        )
        assertResourceValues(
            "en-US",
            mapOf(
                R.string.address_market_explanation to
                    "This version creates and edits Turkey addresses only. Addresses are saved to your Shopify customer account; no persistent copy is kept on this device.",
                R.string.address_empty_message to "You can add your first Turkey address to your Shopify account.",
                R.string.address_unsupported_country to
                    "This address is outside the supported Turkey market. This version displays it but does not edit it or make it default.",
                R.string.address_create_heading to "Add a Turkey address",
                R.string.address_edit_heading to "Edit the Turkey address",
                R.string.address_country_value to "Country: Turkey (TR)",
                R.string.address_phone to "Phone, E.164: +90… (optional)",
                R.string.address_error_phone to
                    "Enter the phone number with its country code (for example, +905551234567).",
                R.string.address_error_postal_code to "Enter the Turkey postal code as 5 digits.",
                R.string.address_failure_unsupported_country to
                    "This address is outside Turkey and cannot be edited in this version.",
                R.string.account_deletion_boundary to
                    "The app cannot delete the Shopify customer account directly. You can send a deletion request to Gür Bakır and separately clear selected data from this device.",
                R.string.account_deletion_request_explanation to
                    "The Gür Bakır account deletion request page opens in your browser. You must submit its form; opening the page alone sends no request. Identity verification may be needed, and some information may be retained where applicable.",
                R.string.account_deletion_identity_warning to
                    "Gür Bakır may request more information to verify your identity. Never enter your password or one-time verification code in any form or message.",
                R.string.account_deletion_open_request to "Open the account deletion request page",
                R.string.account_deletion_request_unavailable to
                    "The account deletion request page has not yet been verified for this app.",
                R.string.account_deletion_confirm_message to
                    "Selected local data cannot be recovered. This does not delete the remote Shopify account or send a deletion request to Gür Bakır.",
                R.string.account_deletion_feedback_opening to "%1\$s is opening on the Gür Bakır site.",
                R.string.account_deletion_feedback_rejected to
                    "The %1\$s address did not match the Gür Bakır security policy and was not opened."
            )
        )
    }

    private fun assertResourceValues(languageTag: String, expected: Map<Int, String>) {
        val localized = localizedContext(languageTag)
        expected.forEach { (resourceId, value) -> assertEquals(value, localized.getString(resourceId)) }
    }

    private fun localizedContext(languageTag: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        return context.createConfigurationContext(configuration)
    }
}
