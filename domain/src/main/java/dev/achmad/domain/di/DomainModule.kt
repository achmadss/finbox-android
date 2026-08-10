package dev.achmad.domain.di

import org.koin.dsl.module

val domainModule = module {
    // Domain module should only define domain layer dependencies
    // like UseCases, Repository interfaces, etc.
}