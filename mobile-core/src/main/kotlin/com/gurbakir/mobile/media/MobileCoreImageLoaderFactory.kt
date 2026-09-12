package com.gurbakir.mobile.media

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.gurbakir.storefront.StorefrontMediaClientFactory
import com.gurbakir.storefront.StorefrontMediaPolicy

object MobileCoreImageLoaderFactory {
    fun create(context: Context, mediaPolicy: StorefrontMediaPolicy): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = { StorefrontMediaClientFactory.create(mediaPolicy) }
                )
            )
        }.build()
}
