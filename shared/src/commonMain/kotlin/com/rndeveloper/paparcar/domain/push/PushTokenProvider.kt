package com.rndeveloper.paparcar.domain.push

/**
 * El token de push de ESTE dispositivo. [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * Lo único de plataforma que tenían las dos vigilancias que lo usan: en Android se le pide a FCM, en
 * iOS lo entrega Swift cuando APNs contesta. El resto —a quién se le publica, cuándo y con qué
 * condiciones— es la misma regla en los dos sitios, y por eso vive en `commonMain`.
 *
 * ⛔ **Pedirlo no es gratis ni es neutro**: un token es un identificador de dispositivo, y en iOS
 * pedirlo significa registrarse en APNs. Los dos implementadores lo entregan bajo demanda, y quien
 * llama solo llama cuando hay algo que suscribir.
 */
interface PushTokenProvider {

    /** El token actual, o `null` si no se pudo obtener — quien llama sigue adelante sin él. */
    suspend fun currentToken(): String?

    /**
     * Invalida el token de este dispositivo. [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
     *
     * Es el cinturon del barrido de filas: aquel quita las que conocemos, este hace que una fila
     * que se nos escapara —o una escritura que fallo por estar sin red— no alcance igualmente a
     * este telefono. El siguiente que suscriba algo recibira un token nuevo.
     */
    suspend fun retireToken()
}
