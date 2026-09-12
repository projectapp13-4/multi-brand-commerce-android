package com.gurbakir.mobile.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private var cartJob: Job? = null

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
        if (!VariantSelectionResolver.canSelect(product, optionName, value, selection)) return
        val updated = (selection - optionName) + (optionName to value)
        saveSelection(product.id, updated)
        savedStateHandle[KEY_MEDIA_INDEX] = 0
        _state.value =
            _state.value.copy(
                selectedOptions = updated,
                invalidRequestedVariant = false,
                mediaIndex = 0
            )
    }

    fun selectMedia(index: Int) {
        val lastIndex = _state.value.displayMedia.lastIndex
        if (index !in 0..lastIndex) return
        savedStateHandle[KEY_MEDIA_INDEX] = index
        _state.value = _state.value.copy(mediaIndex = index)
    }

    fun setMediaViewer(open: Boolean) {
        if (!open || _state.value.displayMedia.isNotEmpty()) {
            _state.value = _state.value.copy(mediaViewerOpen = open)
        }
    }

    fun addToCart() {
        val intent = _state.value.purchaseIntent ?: return
        if (cartJob?.isActive == true) return
        _state.value =
            _state.value.copy(
                addingToCart = true,
                cartFeedback = null,
                cartFailure = null
            )
        cartJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = cartRepository.add(intent.merchandiseId, intent.quantity)) {
                        CartActionResult.Completed ->
                            _state.value.copy(
                                addingToCart = false,
                                cartFeedback = ProductCartFeedback.ADDED
                            )

                        is CartActionResult.Failed ->
                            _state.value.copy(
                                addingToCart = false,
                                cartFailure = result.failure
                            )

                        CartActionResult.Restricted ->
                            _state.value.copy(
                                addingToCart = false,
                                cartFeedback = ProductCartFeedback.RESTRICTED
                            )
                    }
            }
    }

    private fun reload() {
        val currentProductId = productId ?: return
        loadJob?.cancel()
        _state.value = ProductDetailUiState(loading = true)
        loadJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.load(currentProductId)) {
                        is ProductDetailLoad.Content -> result.product.toInitialState(requestedVariantId)
                        is ProductDetailLoad.Error -> ProductDetailUiState(failure = result.failure)
                        ProductDetailLoad.NotFound -> ProductDetailUiState(notFound = true)
                    }
            }
    }

    private fun StorefrontProductDetail.toInitialState(requestedVariantId: String?): ProductDetailUiState {
        val requestedVariant = requestedVariantId?.let { id -> variants.firstOrNull { it.id == id } }
        val sameProduct = savedStateHandle.get<String>(KEY_SELECTION_PRODUCT) == id
        val restored = restoredSelectionFor(id)
        val selection =
            when {
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
                invalidRequestedVariant = requestedVariantId != null && requestedVariant == null
            )
        val safeIndex = restoredIndex.coerceIn(0, initial.displayMedia.lastIndex.coerceAtLeast(0))
        saveSelection(id, selection)
        return initial.copy(mediaIndex = safeIndex)
    }

    private fun saveSelection(productId: String, selection: Map<String, String>) {
        savedStateHandle[KEY_SELECTION_PRODUCT] = productId
        savedStateHandle[KEY_SELECTED_OPTIONS] =
            ArrayList(selection.toSortedMap().flatMap { (name, value) -> listOf(name, value) })
    }

    private fun restoredSelectionFor(productId: String): Map<String, String> {
        val values =
            if (savedStateHandle.get<String>(KEY_SELECTION_PRODUCT) == productId) {
                savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_OPTIONS).orEmpty()
            } else {
                emptyList()
            }
        return if (values.size % 2 == 0) values.chunked(2).associate { it[0] to it[1] } else emptyMap()
    }

    private companion object {
        const val KEY_SELECTION_PRODUCT = "product.selectionProduct"
        const val KEY_SELECTED_OPTIONS = "product.selectedOptions"
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
    val cartFailure: CartFailure? = null
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

enum class ProductCartFeedback {
    ADDED,
    RESTRICTED
}

private val StorefrontProductDetail.hasOnlyDefaultVariant: Boolean
    get() =
        variants.size == 1 &&
            options.size == 1 &&
            options.single().name.equals("Title", ignoreCase = true) &&
            options.single().values.singleOrNull()?.name.equals("Default Title", ignoreCase = true)
