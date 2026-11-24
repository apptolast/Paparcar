package com.rndeveloper.paparcar.data.attribution

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.attribution.MetaInstallIds
import com.rndeveloper.paparcar.domain.attribution.MetaReferrerDecryptPort
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Llama a la Cloud Function `decryptMetaInstallReferrer` para descifrar el bloque de Meta.
 * [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * ⛔ La clave de descifrado NUNCA viaja a este módulo — vive en Secret Manager, del lado del
 * backend (`functions/src/installReferrer.ts`). Esto sólo manda el `utm_content` crudo y recibe
 * los tres identificadores, o nada.
 *
 * Un filtro barato ANTES de llamar a la red: la mayoría de instalaciones (enlace propio, búsqueda
 * en Play) llevan un `utm_content` corriente o ninguno, y cada llamada de más es una invocación de
 * Cloud Function pagada por nada. Sólo lo que PARECE el bloque de Meta (`{"source":{"data":…,
 * "nonce":…}}`) hace el viaje.
 */
class HttpMetaReferrerDecryptDataSource(
    private val client: HttpClient,
    private val authRepository: AuthRepository,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : MetaReferrerDecryptPort {

    override suspend fun decrypt(rawUtmContent: String): Result<MetaInstallIds?> = runCatching {
        if (!looksLikeMetaBlock(rawUtmContent)) return@runCatching null

        // Sin sesión no hay a quién atribuir el referrer en Firestore de todos modos — el
        // clasificador ya cae a META_ORGANIC_* sin esto. Ahorra la llamada en vez de pedirla y
        // que el backend la rechace con 401.
        val token = authRepository.getIdToken() ?: return@runCatching null

        val http = client.post("$baseUrl$PATH") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(DecryptRequestDto(content = rawUtmContent)))
        }
        if (!http.status.isSuccess()) {
            error("decryptMetaInstallReferrer HTTP ${http.status.value}")
        }
        val response = json.decodeFromString<DecryptResponseDto>(http.bodyAsText())
        if (!response.decrypted) return@runCatching null
        MetaInstallIds(
            campaignId = response.campaignId,
            adsetId = response.adsetId,
            adId = response.adId,
        )
    }

    private fun looksLikeMetaBlock(content: String): Boolean {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            val source = json.parseToJsonElement(trimmed).jsonObject[FIELD_SOURCE]?.jsonObject
                ?: return@runCatching false
            source.containsKey(FIELD_DATA) && source.containsKey(FIELD_NONCE)
        }.getOrDefault(false)
    }

    @Serializable
    private data class DecryptRequestDto(val content: String)

    @Serializable
    private data class DecryptResponseDto(
        val decrypted: Boolean,
        val campaignId: String? = null,
        val adsetId: String? = null,
        val adId: String? = null,
    )

    private companion object {
        /** La misma Hosting que `/api/parkings.json` — el `rewrite` de `firebase.json` apunta aquí
         *  también, así que la app nunca nombra el dominio `cloudfunctions.net`. */
        const val DEFAULT_BASE_URL = "https://paparcar.com"
        const val PATH = "/api/decryptMetaInstallReferrer"
        const val FIELD_SOURCE = "source"
        const val FIELD_DATA = "data"
        const val FIELD_NONCE = "nonce"
        val json = Json { ignoreUnknownKeys = true }
    }
}
