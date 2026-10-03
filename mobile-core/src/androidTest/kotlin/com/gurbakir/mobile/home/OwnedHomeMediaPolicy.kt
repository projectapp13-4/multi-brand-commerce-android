package com.gurbakir.mobile.home

import androidx.test.platform.app.InstrumentationRegistry
import com.gurbakir.storefront.StorefrontMediaPolicy

/** An explicit owned-media origin changes only this controlled runtime fixture. */
internal fun ownedHomeMediaPolicy(mediaUrl: String): StorefrontMediaPolicy {
    val merchantDomain = InstrumentationRegistry.getArguments()
        .getString("ownedHomeMediaMerchantDomain") ?: DEFAULT_OWNED_MEDIA_MERCHANT_DOMAIN
    val policy = StorefrontMediaPolicy(merchantDomain)
    require(mediaUrl.isBlank() || policy.accepts(mediaUrl)) {
        "The owned Home video must match the explicitly configured media policy."
    }
    return policy
}

private const val DEFAULT_OWNED_MEDIA_MERCHANT_DOMAIN = "multi-brand-trial-store.myshopify.com"
