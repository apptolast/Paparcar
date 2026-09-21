package com.rndeveloper.paparcar.domain.notification

/**
 * Por dónde se le cuenta al usuario que ha aparecido una plaza en su zona.
 * [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * ⛔ **Un aviso, un canal.** Este veredicto existe porque el aviso salía SIEMPRE por notificación
 * del sistema, también con el mapa delante — y una notificación mientras miras el mapa es un aviso
 * entregado en la puerta de al lado: obliga a bajar la persiana para leer algo que ya estabas
 * mirando. El user lo pidió al revés: *«cuando el usuario esté viendo la pantalla, si aparece un
 * spot NUEVO se le notificará también al momento»*.
 *
 * ⚠️ Y tiene que ser UNA decisión, no dos colectores. El stream de alertas apunta cada plaza en un
 * registro durable **antes** de emitirla (`recordLook`), así que dos consumidores compitiendo por
 * el mismo stream harían que el segundo no viera nada: quien llega primero marca la plaza como
 * contada. Un solo colector, y aquí se elige por dónde sale.
 */
enum class AlertChannel {
    /** El mapa está delante: se cuenta DENTRO de la app, al momento y sin sacar al usuario de ahí. */
    IN_APP,

    /** Nadie está mirando el mapa: la notificación del sistema es la única forma de llegar. */
    SYSTEM_NOTIFICATION,
}

/**
 * ⛔ La pregunta es «¿está el MAPA delante?», no «¿está la app abierta?». Con Ajustes o el garaje en
 * pantalla, la app está en primer plano y el usuario no está mirando dónde salen las plazas: ahí la
 * notificación del sistema sigue siendo el canal correcto, porque es la que sobrevive a que cambie
 * de pantalla.
 */
fun alertChannelFor(mapOnScreen: Boolean): AlertChannel =
    if (mapOnScreen) AlertChannel.IN_APP else AlertChannel.SYSTEM_NOTIFICATION
