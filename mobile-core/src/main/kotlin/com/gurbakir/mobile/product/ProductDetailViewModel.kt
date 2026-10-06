package com.gurbakir.mobile.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gurbakir.mobile.cart.CartActionAdjustment
import com.gurbakir.mobile.cart.CartActionResult
import com.gurbakir.mobile.cart.CartFailure
import com.gurbakir.mobile.cart.CartRepository
import com.gurbakir.mobile.catalog.CatalogLoadFailure
import com.gurbakir.storefront.StorefrontMedia
import com.gurbakir.storefront.StorefrontMoney
import com.gurbakir.storefront.StorefrontProductDetail
import com.gurbakir.storefront.StorefrontProductOption
import com.gurbakir.storefront.StorefrontProductVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProductDetailViewModel
@Inject
constructor(
    private val repository: ProductDetailRepository,
    private val cartRepository: CartRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ProductDetailUiState())
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var productId: String? = null
    private var requestedVariantId: String? = null
    private var selectionRevision = 0L
    private var activeSubmission: CartSubmission? = null

    fun start(productId: String, requestedVariantId: String?) {
        if (this.productId == productId && this.requestedVariantId == requestedVariantId) return
        this.productId = productId
        this.requestedVariantId = requestedVariantId
        reload()
    }

    fun retry() = reload()

    fun selectOption(optionName: String, value: String) {
        val product = _state.value.product ?: return
        val selection = _state.value.selectedOptions
        val updated = (selection - optionName) + (optionName to value)
        if (updated == selection || !VariantSelectionResolver.canSelect(product, optionName, value, selection)) return
        selectionRevision++
        saveSelection(product.id, updated, userChoice = true)
        savedStateHandle[KEY_MEDIA_INDEX] = 0
        _state.value =
            _state.value.copy(
                selectedOptions = updated,
                invalidRequestedVariant = false,
                mediaIndex = 0,
                cartFeedback = null,
                cartFailure = null,
                cartAdjustment = null
            )
    }

    fun selectMedia(index: Int) {
        val lastIndex = _state.value.displayMedia.lastIndex
        if (index !in 0..lastIndex) return
        savedStateHandle[KEY_MEDIA_INDEX] = index
        _state.value = _state.value.copy(mediaIndex = index)
    }

    fun clearSelection() {
        val state = _state.value
        val product = state.product ?: return
        if (state.addingToCart || state.displayOptions.isEmpty() || state.selectedOptions.isEmpty()) return
        selectionRevision++
        saveSelection(product.id, emptyMap(), userChoice = true)
        savedStateHandle[KEY_MEDIA_INDEX] = 0
        _state.value = state.copy(
            selectedOptions = emptyMap(),
            invalidRequestedVariant = false,
            mediaIndex = 0,
            cartFeedback = null,
            cartFailure = null,
            cartAdjustment = null
        )
    }

    fun setMediaViewer(open: Boolean) {
        if (!open || _state.value.displayMedia.isNotEmpty()) {
            _state.value = _state.value.copy(mediaViewerOpen = open)
        }
    }

    fun addToCart() {
        val state = _state.value
        val currentProductId = state.product?.id ?: return
        val intent = state.purchaseIntent?.takeIf { activeSubmission == null } ?: return
        val submission = CartSubmission(currentProductId, intent, selectionRevision)
        activeSubmission = submission
        _state.value =
            state.copy(
                addingToCart = true,
                cartFeedback = null,
                cartFailure = null,
                cartAdjustment = null
            )
        viewModelScope.launch {
            try {
                val result = cartRepository.add(submission.intent.merchandiseId, submission.intent.quantity)
                val current = _state.value
                if (
                    activeSubmission !== submission || selectionRevision != submission.selectionRevision ||
                    !submission.matches(current)
                ) {
                    return@launch
                }
                _state.value =
                    when (result) {
                        CartActionResult.Completed ->
                            current.copy(
                                cartFeedback = ProductCartFeedback.ADDED
                            )

                        is CartActionResult.Adjusted ->
                            current.copy(
                                cartFeedback = ProductCartFeedback.ADJUSTED,
                                cartAdjustment = result.adjustment
                            )

                        is CartActionResult.Failed ->
                            current.copy(
                                cartFailure = result.failure
                            )

                        CartActionResult.Restricted ->
                            current.copy(
                                cartFeedback = ProductCartFeedback.RESTRICTED
                            )
                    }
            } finally {
                if (activeSubmission === submission) {
                    activeSubmission = null
                    _state.value = _state.value.copy(addingToCart = false)
                }
            }
        }
    }

    private fun reload() {
        val currentProductId = productId ?: return
        loadJob?.cancel()
        selectionRevision++
        _state.value = ProductDetailUiState(loading = true, addingToCart = activeSubmission != null)
        loadJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.load(currentProductId)) {
                        is ProductDetailLoad.Content -> result.product.toInitialState(requestedVariantId)
                        is ProductDetailLoad.Error -> ProductDetailUiState(failure = result.failure)
                        ProductDetailLoad.NotFound -> ProductDetailUiState(notFound = true)
                    }.copy(addingToCart = activeSubmission != null)
            }
    }

    private fun StorefrontProductDetail.toInitialState(requestedVariantId: String?): ProductDetailUiState {
        val requestedVariant = requestedVariantId?.let { id -> variants.firstOrNull { it.id == id } }
        val sameProduct = savedStateHandle.get<String>(KEY_SELECTION_PRODUCT) == id
        val hasUserSelectionForRoute = sameProduct &&
            savedStateHandle.get<String>(KEY_SELECTION_REQUEST) == requestedVariantId.orEmpty()
        if (!hasUserSelectionForRoute) savedStateHandle.remove<String>(KEY_SELECTION_REQUEST)
        val savedValues = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_OPTIONS)?.takeIf {
            sameProduct
        }.orEmpty()
        val restored = decodeProductOptionSelection(savedValues)
        val selection =
            when {
                hasUserSelectionForRoute -> VariantSelectionResolver.restoreSelection(this, restored)
                requestedVariant != null -> VariantSelectionResolver.selectionForVariant(requestedVariant)
                requestedVariantId != null -> emptyMap()
                restored.isNotEmpty() -> VariantSelectionResolver.restoreSelection(this, restored)
                hasOnlyDefaultVariant -> VariantSelectionResolver.selectionForVariant(variants.single())
                else -> emptyMap()
            }
        val restoredIndex = if (sameProduct) savedStateHandle.get<Int>(KEY_MEDIA_INDEX) ?: 0 else 0
        val initial =
            ProductDetailUiState(
                product = this,
                selectedOptions = selection,
                invalidRequestedVariant = !hasUserSelectionForRoute &&
                    requestedVariantId != null && requestedVariant == null
            )
        val safeIndex = restoredIndex.coerceIn(0, initial.displayMedia.lastIndex.coerceAtLeast(0))
        saveSelection(id, selection)
        return initial.copy(mediaIndex = safeIndex)
    }

    private fun saveSelection(productId: String, selection: Map<String, String>, userChoice: Boolean = false) {
        savedStateHandle[KEY_SELECTION_PRODUCT] = productId
        savedStateHandle[KEY_SELECTED_OPTIONS] =
            ArrayList(selection.toSortedMap().flatMap { (name, value) -> listOf(name, value) })
        if (userChoice) savedStateHandle[KEY_SELECTION_REQUEST] = requestedVariantId.orEmpty()
    }

    private class CartSubmission(
        val productId: String,
        val intent: ProductPurchaseIntent,
        val selectionRevision: Long
    ) {
        fun matches(state: ProductDetailUiState): Boolean =
            state.product?.id == productId && state.purchaseIntent == intent
    }

    private companion object {
        const val KEY_SELECTION_PRODUCT = "product.selectionProduct"
        const val KEY_SELECTED_OPTIONS = "product.selectedOptions"
        const val KEY_SELECTION_REQUEST = "product.selectionRequest"
        const val KEY_MEDIA_INDEX = "product.mediaIndex"
    }
}

data class ProductDetailUiState(
    val product: StorefrontProductDetail? = null,
    val selectedOptions: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val failure: CatalogLoadFailure? = null,
    val notFound: Boolean = false,
    val invalidRequestedVariant: Boolean = false,
    val mediaIndex: Int = 0,
    val mediaViewerOpen: Boolean = false,
    val addingToCart: Boolean = false,
    val cartFeedback: ProductCartFeedback? = null,
    val cartFailure: CartFailure? = null,
    val cartAdjustment: CartActionAdjustment? = null
) {
    val selectedVariant: StorefrontProductVariant?
        get() = product?.let { VariantSelectionResolver.selectedVariant(it, selectedOptions) }

    val purchaseIntent: ProductPurchaseIntent?
        get() = selectedVariant?.takeIf { it.availableForSale }?.let { ProductPurchaseIntent(it.id, 1) }

    val displayOptions: List<StorefrontProductOption>
        get() = product?.options.orEmpty().takeUnless { product?.hasOnlyDefaultVariant == true }.orEmpty()

    val displayMedia: List<StorefrontMedia>
        get() {
            val variantImage = selectedVariant?.image
            return (listOfNotNull(variantImage) + product?.media.orEmpty().map { it.image })
                .distinctBy { it.uri }
        }

    val minimumPrice: StorefrontMoney?
        get() = product?.variants?.minByOrNull { it.price.amount }?.price

    val maximumPrice: StorefrontMoney?
        get() = product?.variants?.maxByOrNull { it.price.amount }?.price

    fun valueStates(option: StorefrontProductOption): List<ProductOptionValueState> =
        product?.let { VariantSelectionResolver.valueStates(it, option, selectedOptions) }.orEmpty()
}

data class ProductPurchaseIntent(val merchandiseId: String, val quantity: Int)

private fun decodeProductOptionSelection(values: List<String>): Map<String, String> =
    if (values.size % 2 == 0) values.chunked(2).associate { it[0] to it[1] } else emptyMap()

enum class ProductCartFeedback {
    ADDED,
    ADJUSTED,
    RESTRICTED
}

private val StorefrontProductDetail.hasOnlyDefaultVariant: Boolean
    get() =
        variants.size == 1 &&
            options.size == 1 &&
            options.single().name.equals("Title", ignoreCase = true) &&
            options.single().values.singleOrNull()?.name.equals("Default Title", ignoreCase = true)
