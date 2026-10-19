package com.gurbakir.mobile.product

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurbakir.storefront.StorefrontProductDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryProductDescriptionTest {
    @Test
    fun sourceParagraphsHeadingsBreaksUnicodeAndUrlsKeepTheirTextInOrder() {
        val product = product(
            "İlk paragraf.İkinci paragraf.Baskı HakkındaÖlçü: 30 × 40 cmhttps://tumiart.com",
            "<p>İlk paragraf.</p><p>İkinci paragraf.</p><h3>Baskı Hakkında</h3>" +
                "<p>Ölçü: 30 &times; 40 cm<br>https://tumiart.com</p>"
        )
        val text = product.galleryDescription()
        assertTrue(text.contains("İlk paragraf.\n\nİkinci paragraf."))
        assertTrue(text.contains("Baskı Hakkında\n\nÖlçü:"))
        assertTrue(text.contains("30 × 40 cm\nhttps://tumiart.com"))
        assertFalse(text.contains('<'))
    }

    @Test
    fun missingEmptyMismatchedAndOversizedStructureKeepsAuthoritativePlainText() {
        val plain = "İlk satır.\n\nİkinci satır. https://tumiart.com"
        listOf(null, "", "<p>Different content</p>", "<p>" + "a".repeat(48_001) + "</p>").forEach {
            assertEquals(plain, product(plain, it).galleryDescription())
        }
        assertEquals("", product("", "").galleryDescription())
        assertEquals("", product("", "<p></p>").galleryDescription())
    }

    @Test
    fun inlineFormattingAndLinksRemainInertTextWithoutChangingMerchantWords() {
        val plain = "Özgün çalışma & baskı. Ayrıntılar"
        val html = "<p><strong>Özgün çalışma</strong> &amp; baskı. " +
            "<a href=\"https://tumiart.com\">Ayrıntılar</a></p>"
        assertEquals(plain, product(plain, html).galleryDescription())
        assertEquals(plain, product(plain, "$html<script>extra content</script>").galleryDescription())
    }

    private fun product(plain: String, html: String?) = StorefrontProductDetail(
        "gid://shopify/Product/1", "owned-text-fixture", "Owned fixture", plain,
        false, emptyList(), emptyList(), emptyList(), descriptionHtml = html
    )
}
