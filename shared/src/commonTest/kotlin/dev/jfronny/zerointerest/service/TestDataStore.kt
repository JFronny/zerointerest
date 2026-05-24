package dev.jfronny.zerointerest.service

import androidx.datastore.core.CurrentDataProviderStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class TestDataStore<T>(initial: T) : CurrentDataProviderStore<T> {
    private val _data = MutableStateFlow(initial)

    override val data: Flow<T> = _data

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        var transformed: T? = null
        _data.update {
            transformed = transform(it)
            transformed
        }
        return transformed!!
    }

    override suspend fun currentData(): T {
        return _data.value
    }
}
