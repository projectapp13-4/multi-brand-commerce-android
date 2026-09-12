package com.gurbakir.mobile

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.gurbakir.foundation.config.AppConfiguration
import com.gurbakir.mobile.config.BuildConfigurationSource
import com.gurbakir.mobile.media.MobileCoreImageLoaderFactory
import com.gurbakir.storefront.StorefrontMediaPolicy
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GurbakirApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = MobileCoreImageLoaderFactory.create(
        context,
        mediaPolicyFor(BuildConfigurationSource.current)
    )
}

internal fun mediaPolicyFor(configuration: AppConfiguration): StorefrontMediaPolicy =
    StorefrontMediaPolicy(configuration.storefront.domain)
