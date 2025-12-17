package dev.jfronny.zerointerest

import org.koin.dsl.module

fun createExtraModule() = module {
    //TODO add SummaryTrustDatabase implementation based on https://github.com/JuulLabs/indexeddb
    //TODO add Settings implementation using localStorage or something similar
}