package com.rndeveloper.paparcar.presentation.home.model

/**
 * Un aviso de SALUD DEL SISTEMA que el usuario puede callar con la × del badge.
 * [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
 *
 * Esta lista es exactamente la de las historias que NO hospeda la card —lo que le pasa al sistema,
 * no a un coche— y eso no es casualidad: es la misma frontera de [DetectionStory.isTheCarsQuestion].
 * Lo que se puede callar es un AVISO; una PREGUNTA no, porque callar una pregunta sin contestarla es
 * perder la respuesta, y las tres preguntas ya traen su propia salida («Descartar», «Sigo
 * conduciendo», «No voy en mi coche»). Ponerle una × a la card del coche habría añadido una cuarta
 * forma de decir que no, indistinguible de las otras tres y sin mecánica detrás.
 */
enum class DetectionNotice {
    /** Falta el permiso de ubicación: rompe el mapa y las plazas, no sólo la detección. */
    LocationBlocked,

    /** La detección está apagada — el interruptor o un permiso de productor. */
    DetectionOff,

    /** Vigila, pero el móvil puede cortarla (sin exención de batería). */
    WatchFragile,

    /** El sistema mató al vigilante: la vigilancia prometida ya no existe. */
    WatchInterrupted,

    /** No hay ningún coche registrado. */
    NoVehicle,
}

/**
 * ¿Qué aviso es esta historia, o null si no es un aviso que se pueda callar?
 *
 * ⛔ Las tres preguntas de un coche devuelven null a propósito: ver arriba.
 */
fun DetectionStory.notice(): DetectionNotice? = when (this) {
    DetectionStory.BlockedCore -> DetectionNotice.LocationBlocked
    DetectionStory.Inactive -> DetectionNotice.DetectionOff
    DetectionStory.NoVehicle -> DetectionNotice.NoVehicle
    is DetectionStory.Watching -> when (watchBadge) {
        ParkedWatchBadge.WATCHING_FRAGILE -> DetectionNotice.WatchFragile
        ParkedWatchBadge.WATCH_INTERRUPTED -> DetectionNotice.WatchInterrupted
        // La línea calmada y la de «marca tu vehículo» no son avisos: no hay nada que callar.
        // ⛔ Y «todavía no se sabe» tampoco: callar una duda no la resuelve, y la historia ya la
        // resuelve a `Hidden` antes de llegar aquí, así que no hay ni fila que llevar la ×.
        // [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
        ParkedWatchBadge.WATCHING,
        ParkedWatchBadge.PARK_MY_VEHICLE,
        ParkedWatchBadge.WATCH_UNKNOWN,
        -> null
    }
    is DetectionStory.AwaitingAnswer,
    DetectionStory.PendingAsk,
    DetectionStory.AwaitingFirstPark,
    DetectionStory.Hidden,
    -> null
}

/**
 * La historia, callada si el usuario ya dijo «ahora no» a ese aviso.
 *
 * ⛔ El silencio dura lo que dure la pantalla en memoria — **NO se persiste, y es deliberado**. Un
 * permiso de ubicación que falta deja la app rota; guardarlo en disco convertiría un «ahora no» en
 * un «nunca más» y Paparcar se quedaría callada sobre su propia avería. Volviendo a abrir, el aviso
 * vuelve, porque el problema sigue ahí. [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
 */
fun DetectionStory.silencedBy(silenced: Set<DetectionNotice>): DetectionStory =
    if (notice() in silenced) DetectionStory.Hidden else this
