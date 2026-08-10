package com.example.core.di.util

import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatformTools

inline fun <reified T : Any> injectLazy(): Lazy<T> {
    return lazy { KoinPlatformTools.defaultContext().get().get(T::class) }
}

inline fun <reified T : Any> injectLazy(key: String): Lazy<T> {
    return lazy { KoinPlatformTools.defaultContext().get().get(T::class, named(key)) }
}

inline fun <reified T : Any> inject(): T {
    return KoinPlatformTools.defaultContext().get().get(T::class)
}

inline fun <reified T : Any> inject(key: String): T {
    return KoinPlatformTools.defaultContext().get().get(T::class, named(key))
}