package dev.achmad.finbox.di

import dev.achmad.finbox.util.permission.PermissionHelper
import dev.achmad.finbox.util.ui.ToastHelper
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val helperModule = module {
    single<ToastHelper> { ToastHelper(context = androidContext()) }
    single<PermissionHelper> { PermissionHelper(context = androidContext()) }
}