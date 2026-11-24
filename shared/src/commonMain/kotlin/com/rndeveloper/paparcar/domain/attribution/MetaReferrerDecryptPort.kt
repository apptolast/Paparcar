package com.rndeveloper.paparcar.domain.attribution

/**
 * Descifra el bloque de Meta del `utm_content` del referrer de Play, llamando al backend — la
 * clave de descifrado NUNCA viaja al APK. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * `rawUtmContent` es el valor crudo (ya parseado del referrer, sin decodificar JSON): la función
 * decide ella misma si parece el bloque de Meta (`{"app":...,"source":{"data":...,"nonce":...}}`)
 * o un `utm_content` corriente de nuestros propios enlaces (`z-mostoles`, un `placement`…), en
 * cuyo caso devuelve `null` sin llamar a nadie.
 */
interface MetaReferrerDecryptPort {
    suspend fun decrypt(rawUtmContent: String): Result<MetaInstallIds?>
}

/** El Dev Catalog nunca llama a un backend real. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] */
class NoOpMetaReferrerDecryptPort : MetaReferrerDecryptPort {
    override suspend fun decrypt(rawUtmContent: String): Result<MetaInstallIds?> = Result.success(null)
}
