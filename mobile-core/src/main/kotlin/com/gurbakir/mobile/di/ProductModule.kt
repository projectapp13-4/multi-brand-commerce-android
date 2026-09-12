package com.gurbakir.mobile.di

import com.gurbakir.mobile.product.DefaultProductDetailRepository
import com.gurbakir.mobile.product.ProductDetailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProductModule {
    @Binds
    abstract fun bindProductDetailRepository(implementation: DefaultProductDetailRepository): ProductDetailRepository
}
