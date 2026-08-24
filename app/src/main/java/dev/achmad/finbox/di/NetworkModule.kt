package dev.achmad.finbox.di

import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.util.network.NetworkHelper
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val networkModule = module {
    single<OkHttpClient> { get<NetworkHelper>().client }
    single<NetworkHelper> {
        NetworkHelper(
            context = androidContext(),
            isDebugBuild = BuildConfig.DEBUG
        )
    }
}