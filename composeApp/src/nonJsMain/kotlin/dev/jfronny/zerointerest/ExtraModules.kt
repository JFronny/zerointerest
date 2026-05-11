package dev.jfronny.zerointerest

import org.koin.dsl.module

fun createExtraModule() = module {
    single { get<Platform>() as AbstractPlatform }
}
