package com.example.gate2synthetic

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.example.gate2synthetic.config.Gate2SyntheticConfiguration
import com.gurbakir.mobile.media.MobileCoreImageLoaderFactory
import com.gurbakir.storefront.StorefrontMediaPolicy
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Gate2SyntheticApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = MobileCoreImageLoaderFactory.create(
        context,
        StorefrontMediaPolicy(Gate2SyntheticConfiguration.app.storefront.domain)
    )
}
