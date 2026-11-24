package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.attribution.InstallReferrerPort
import com.rndeveloper.paparcar.domain.attribution.InstallReferrerRaw

/** [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] */
class FakeInstallReferrerPort(
    var result: Result<InstallReferrerRaw?> = Result.success(null),
) : InstallReferrerPort {
    var readCallCount = 0
        private set

    override suspend fun read(): Result<InstallReferrerRaw?> {
        readCallCount++
        return result
    }
}
