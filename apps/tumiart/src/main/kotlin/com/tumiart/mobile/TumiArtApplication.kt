package com.tumiart.mobile

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.gurbakir.mobile.media.MobileCoreImageLoaderFactory
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.tumiart.mobile.config.TumiArtConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TumiArtApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = MobileCoreImageLoaderFactory.create(
        context,
        StorefrontMediaPolicy(TumiArtConfiguration.app.storefront.domain)
    )
}
