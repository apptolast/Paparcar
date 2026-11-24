package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.attribution.MetaInstallIds
import com.rndeveloper.paparcar.domain.attribution.MetaReferrerDecryptPort

/** [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] */
class FakeMetaReferrerDecryptPort(
    var result: Result<MetaInstallIds?> = Result.success(null),
) : MetaReferrerDecryptPort {
    var lastContent: String? = null
        private set

    override suspend fun decrypt(rawUtmContent: String): Result<MetaInstallIds?> {
        lastContent = rawUtmContent
        return result
    }
}
