package com.gurbakir.mobile.home

import com.gurbakir.storefront.HomeCollectionReferencesObservation
import com.gurbakir.storefront.HomeCollectionsFieldObservation
import com.gurbakir.storefront.HomeDocumentObservation
import com.gurbakir.storefront.HomeDocumentSelector
import com.gurbakir.storefront.HomeFieldObservation
import com.gurbakir.storefront.HomeProductFieldObservation
import com.gurbakir.storefront.HomeResourceKey
import com.gurbakir.storefront.HomeResourceKind
import com.gurbakir.storefront.HomeSectionNodeObservation
import com.gurbakir.storefront.HomeSectionObservation
import com.gurbakir.storefront.HomeSectionReferencesObservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class HomeContentValidatorTest {
    private val validator = HomeContentValidator()
    private val selector = HomeDocumentSelector("mobile_home", "primary")

    @Test
    fun `explicit empty is accepted but a nonempty unresolved declaration is rejected`() {
        val empty = validator.validate(selector, root("0", "[]", emptyList()), 1)
        val unresolved =
            validator.validate(
                selector,
                root("1", "[\"gid://shopify/Metaobject/draft\"]", emptyList()),
                1
            )

        val accepted = assertInstanceOf(HomeDocumentValidation.Accepted::class.java, empty)
        assertEquals(emptyList<RemoteHomeSection>(), accepted.snapshot.sections)
        assertEquals(
            HomeDocumentValidation.Rejected(HomeDocumentRejection.UNRESOLVED_SECTION_REFERENCES),
            unresolved
        )
    }

    @Test
    fun `identical handles across different section types are accepted`() {
        val grid = gridSection("gid://shopify/Metaobject/grid", "primary")
        val featured = featuredSection("gid://shopify/Metaobject/featured", "primary")
        val validation =
            validator.validate(
                selector,
                root(
                    "2",
                    "[\"gid://shopify/Metaobject/grid\",\"gid://shopify/Metaobject/featured\"]",
                    listOf(grid, featured)
                ),
                1
            )

        val accepted = assertInstanceOf(HomeDocumentValidation.Accepted::class.java, validation)
        assertEquals(listOf("primary", "primary"), accepted.snapshot.sections.map { it.handle })
    }

    @Test
    fun `a repeated section family is rejected`() {
        val first = gridSection("gid://shopify/Metaobject/one", "one")
        val second = gridSection("gid://shopify/Metaobject/two", "two")

        val validation =
            validator.validate(
                selector,
                root(
                    "2",
                    "[\"gid://shopify/Metaobject/one\",\"gid://shopify/Metaobject/two\"]",
                    listOf(first, second)
                ),
                1
            )

        assertEquals(
            HomeDocumentValidation.Rejected(HomeDocumentRejection.REPEATED_SECTION_FAMILY),
            validation
        )
    }

    @Test
    fun `malformed root updated timestamp is rejected before persistence`() {
        val validation =
            validator.validate(
                selector,
                root("0", "[]", emptyList()).copy(rootUpdatedAt = "not-an-instant"),
                1
            )

        assertEquals(
            HomeDocumentValidation.Rejected(HomeDocumentRejection.ROOT_IDENTITY),
            validation
        )
    }

    private fun root(count: String, stored: String, nodes: List<HomeSectionNodeObservation>) = HomeDocumentObservation(
        rootGid = "gid://shopify/Metaobject/root",
        rootHandle = "primary",
        rootType = "mobile_home",
        rootUpdatedAt = "2026-09-13T20:00:00Z",
        schemaVersion = HomeFieldObservation("number_integer", "1"),
        declaredSectionCount = HomeFieldObservation("number_integer", count),
        sections =
            com.gurbakir.storefront.HomeSectionsFieldObservation(
                type = "list.mixed_reference",
                value = stored,
                references = HomeSectionReferencesObservation(nodes, hasNextPage = false)
            )
    )

    private fun gridSection(gid: String, handle: String) = HomeSectionNodeObservation(
        runtimeType = "Metaobject",
        section =
            HomeSectionObservation(
                sectionGid = gid,
                handle = handle,
                type = "mobile_home_collection_grid",
                title = HomeFieldObservation("single_line_text_field", "Collections"),
                collections =
                    HomeCollectionsFieldObservation(
                        type = "list.collection_reference",
                        value = "[\"gid://shopify/Collection/1\"]",
                        references = HomeCollectionReferencesObservation(emptyList(), false)
                    ),
                product = null
            )
    )

    private fun featuredSection(gid: String, handle: String) = HomeSectionNodeObservation(
        runtimeType = "Metaobject",
        section =
            HomeSectionObservation(
                sectionGid = gid,
                handle = handle,
                type = "mobile_home_featured_product",
                title = HomeFieldObservation("single_line_text_field", "Featured"),
                collections = null,
                product =
                    HomeProductFieldObservation(
                        type = "product_reference",
                        value = "gid://shopify/Product/1",
                        reference = null
                    )
            )
    )
}
