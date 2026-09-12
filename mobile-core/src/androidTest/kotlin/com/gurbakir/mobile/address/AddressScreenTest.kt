@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.address

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.account.CustomerAddressField
import com.gurbakir.mobile.CoreTestTheme
import com.gurbakir.mobile.core.R
import com.gurbakir.mobile.performDeterministicClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyAddressListOffersFunctionalCreateAction() {
        var create = 0
        setListContent(
            listState(emptyList()),
            listActions(onCreate = { create += 1 })
        )

        composeRule.onNodeWithTag(AddressListTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(AddressListTestTags.CREATE).assertHasClickAction().performDeterministicClick()
        assertEquals(1, create)
    }

    @Test
    fun listDistinguishesDefaultAndUnsupportedAddressActions() {
        val default = address("1", isDefault = true)
        val unsupported = address("2", territory = "XX", isSupported = false)
        setListContent(listState(listOf(default, unsupported)))

        composeRule.onAllNodesWithTag(AddressListTestTags.edit(default.id)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(AddressListTestTags.delete(default.id)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AddressListTestTags.edit(unsupported.id)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AddressListTestTags.setDefault(unsupported.id)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AddressListTestTags.delete(unsupported.id)).assertCountEquals(1)
    }

    @Test
    fun destructiveAndDefaultMutationsRequireExplicitConfirmation() {
        var confirmed = 0
        var dismissed = 0
        setListContent(
            listState(listOf(address("1"))).copy(
                confirmation = AddressConfirmation("1", AddressConfirmationType.DELETE)
            ),
            listActions(
                onConfirm = { confirmed += 1 },
                onDismiss = { dismissed += 1 }
            )
        )

        composeRule.onNodeWithTag(AddressListTestTags.DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(AddressListTestTags.CONFIRM).performDeterministicClick()
        assertEquals(1, confirmed)

        composeRule.onNodeWithTag(AddressListTestTags.DISMISS).performDeterministicClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun createFormExposesConfiguredPolicyFieldsAndMemoryDisclosure() {
        var city = ""
        val countryLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.address_country_value)
        setFormContent(
            formState(),
            formActions(onFieldChanged = { field, value -> if (field == CustomerAddressField.CITY) city = value })
        )

        composeRule
            .onNodeWithTag(AddressFormTestTags.COUNTRY)
            .assertIsDisplayed()
            .assertTextContains(countryLabel)
        val content = composeRule.onNodeWithTag(AddressFormTestTags.CONTENT)
        ADDRESS_FIELDS.forEach { field ->
            val tag = AddressFormTestTags.fields.getValue(field)
            content.performScrollToNode(hasTestTag(tag))
            composeRule.onAllNodesWithTag(tag).assertCountEquals(1)
        }
        content.performScrollToNode(
            hasTestTag(AddressFormTestTags.fields.getValue(CustomerAddressField.CITY))
        )
        composeRule.onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.CITY))
            .performTextReplacement("Updated City")
        assertEquals("Updated City", city)
        content.performScrollToNode(hasTestTag(AddressFormTestTags.MAKE_DEFAULT))
        composeRule.onNodeWithTag(AddressFormTestTags.MAKE_DEFAULT).assertIsDisplayed()
    }

    @Test
    fun serverRejectedAddressFieldIsFocusedAndAnnounced() {
        var handled = 0
        setFormContent(
            formState().copy(
                fieldErrors = mapOf(CustomerAddressField.ADDRESS1 to AddressFieldError.SERVER_REJECTED),
                focusRequest = CustomerAddressField.ADDRESS1
            ),
            formActions(onFocusHandled = { handled += 1 })
        )

        composeRule.onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.ADDRESS1)).assertIsFocused()
        assertEquals(1, handled)
    }

    @Test
    fun addressImeNextMovesFocusToTheFollowingField() {
        setFormContent(formState())

        composeRule
            .onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.FIRST_NAME))
            .performClick()
            .performImeAction()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.LAST_NAME))
            .assertIsFocused()
    }

    @Test
    fun unconfirmedAddressSaveKeepsDraftAndOffersAuthoritativeReload() {
        var reload = 0
        setFormContent(
            formState().copy(
                phase = AddressFormPhase.FAILED,
                input = input().copy(city = "Private draft"),
                failure = AddressFormFailure.SAVE_UNCONFIRMED
            ),
            formActions(onReload = { reload += 1 })
        )

        composeRule.onNodeWithTag(AddressFormTestTags.FEEDBACK).assertIsDisplayed()
        val content = composeRule.onNodeWithTag(AddressFormTestTags.CONTENT)
        content.performScrollToNode(
            hasTestTag(AddressFormTestTags.fields.getValue(CustomerAddressField.CITY))
        )
        composeRule.onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.CITY))
            .assertTextContains("Private draft")
        content.performScrollToNode(hasTestTag(AddressFormTestTags.RELOAD))
        composeRule.onNodeWithTag(AddressFormTestTags.RELOAD).performDeterministicClick()
        assertEquals(1, reload)
    }

    @Test
    fun addressSurfacesReflowAtTwoHundredPercentFontScaleAndBackStaysReachable() {
        var back = 0
        setFormContent(
            formState(),
            formActions(onBack = { back += 1 }),
            fontScale = 2f
        )

        composeRule.onNodeWithTag(AddressFormTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AddressFormTestTags.CONTENT).performScrollToNode(
            hasTestTag(AddressFormTestTags.fields.getValue(CustomerAddressField.PHONE))
        )
        composeRule.onNodeWithTag(AddressFormTestTags.fields.getValue(CustomerAddressField.PHONE)).assertExists()
        composeRule.onNodeWithTag(AddressFormTestTags.BACK).performDeterministicClick()
        assertEquals(1, back)
    }

    private fun setListContent(
        state: AddressListUiState,
        actions: AddressListActions = listActions(),
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            TestTheme(fontScale) { AddressListScreen(state, actions) }
        }
    }

    private fun setFormContent(
        state: AddressFormUiState,
        actions: AddressFormActions = formActions(),
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            TestTheme(fontScale) { AddressFormScreen(state, actions) }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TestTheme(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        CoreTestTheme(
            darkTheme = false
        ) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                content()
            }
        }
    }

    private fun listActions(onCreate: () -> Unit = {}, onConfirm: () -> Unit = {}, onDismiss: () -> Unit = {}) =
        AddressListActions(
            onBack = {},
            onCreate = onCreate,
            onEdit = {},
            onSetDefault = {},
            onDelete = {},
            onConfirm = onConfirm,
            onDismissConfirmation = onDismiss,
            onReload = {}
        )

    private fun formActions(
        onBack: () -> Unit = {},
        onFieldChanged: (CustomerAddressField, String) -> Unit = { _, _ -> },
        onReload: () -> Unit = {},
        onFocusHandled: () -> Unit = {}
    ) = AddressFormActions(
        onBack = onBack,
        onFieldChanged = onFieldChanged,
        onMakeDefaultChanged = {},
        onSave = {},
        onReload = onReload,
        onFocusHandled = onFocusHandled
    )

    private companion object {
        val ADDRESS_FIELDS =
            listOf(
                CustomerAddressField.FIRST_NAME,
                CustomerAddressField.LAST_NAME,
                CustomerAddressField.COMPANY,
                CustomerAddressField.ADDRESS1,
                CustomerAddressField.ADDRESS2,
                CustomerAddressField.CITY,
                CustomerAddressField.ZIP,
                CustomerAddressField.PHONE
            )

        fun listState(addresses: List<AddressContent>) = AddressListUiState(
            phase = AddressListPhase.READY,
            addresses = addresses,
            loaded = true
        )

        fun formState() = AddressFormUiState(
            postalCodeInputMode = PostalCodeInputMode.NUMERIC,
            phase = AddressFormPhase.READY,
            input = input(),
            loaded = true
        )

        fun input() = AddressInput("Ada", "Lovelace", "", "Private street", "", "Example City", "ZX-1234", "")

        fun address(id: String, isDefault: Boolean = false, territory: String = "ZZ", isSupported: Boolean = true) =
            AddressContent(
                id = id,
                firstName = "Ada",
                lastName = "Lovelace",
                company = "",
                address1 = "Private street",
                address2 = "",
                city = "Example City",
                zip = "ZX-1234",
                phoneNumber = "",
                territoryCode = territory,
                formatted = listOf("Ada Lovelace", "Private street", "Example City ZX-1234"),
                isDefault = isDefault,
                isSupported = isSupported
            )
    }
}
