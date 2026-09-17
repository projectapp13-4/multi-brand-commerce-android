package com.projectapp134.multibrandtrial

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.gurbakir.mobile.media.MobileCoreImageLoaderFactory
import com.gurbakir.storefront.StorefrontMediaPolicy
import com.projectapp134.multibrandtrial.config.TrialConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrialApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = MobileCoreImageLoaderFactory.create(
        context,
        StorefrontMediaPolicy(TrialConfiguration.app.storefront.domain)
    )
}
