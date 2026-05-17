package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.session.LocalSessionCache

class FakeLocalSessionCache : LocalSessionCache {
    var wipeCount: Int = 0
        private set

    override suspend fun wipe() {
        wipeCount++
    }
}
