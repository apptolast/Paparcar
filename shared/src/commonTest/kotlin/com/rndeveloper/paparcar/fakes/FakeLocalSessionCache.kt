package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.session.LocalSessionCache

class FakeLocalSessionCache : LocalSessionCache {
    var wipeCount: Int = 0
        private set

    override suspend fun wipe() {
        wipeCount++
    }
}
