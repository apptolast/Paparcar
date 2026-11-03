package com.rndeveloper.paparcar.domain.analytics

/**
 * El carril que existe para que un anuncio se pueda atribuir y optimizar — hoy Meta App Events, y
 * nada más. [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
 *
 * Es un [AnalyticsTracker] porque se alimenta del MISMO punto de estrangulamiento que el resto
 * ([com.rndeveloper.paparcar.data.analytics.CompositeAnalyticsTracker]): una segunda lista de
 * llamadas sería libre de derivar de la primera. Lo que lo distingue no es de dónde LEE, es a
 * dónde ESCRIBE — una red publicitaria, un tercero, con fin publicitario — y por eso tiene nombre
 * propio: `grep AdAttributionTracker` contesta de una vez "qué sale de este móvil hacia un
 * anunciante", que es exactamente la pregunta del formulario de Data Safety y de la política.
 *
 * ## Dos reglas que no son opinión
 *
 * 1. **El vocabulario es CERRADO y lo decide el TIPO, no un `if`**: [asAdAttributionEvent]. Lo que
 *    no está en [AdAttributionEvent] no sale. Misma doctrina que el espejo del coche compartido,
 *    donde `armEvidence` y `detectionPath` no tienen campo porque son mecánica de detección
 *    [VEH-SHARED-001].
 * 2. **Sin parámetros, y eso incluye la celda.** El resto del sistema reduce la ubicación a una
 *    celda geohash de ~150 m; aquí no viaja ni eso. Una red publicitaria no puede optimizar por
 *    `detection_path` ni por una celda, así que mandarlos sólo ensancharía la declaración.
 *
 * ## Y no manda nada sin consentimiento
 *
 * La implementación no se enciende hasta que el usuario ha aceptado términos + política
 * (`AppPreferences.hasAcceptedLegalConsent`, la puerta que ya bloquea el sign-in
 * [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001]). Sin esa aceptación el SDK no se inicializa, no
 * recoge el identificador de publicidad y no manda el evento de sesión.
 */
interface AdAttributionTracker : AnalyticsTracker

/**
 * Lo único que sale hacia la red publicitaria: tres hechos que un anunciante puede optimizar.
 *
 * - [PARKING_CONFIRMED] — la app hizo su trabajo. Es el evento de **activación**: el que distingue
 *   una instalación que sirvió de una que se abrió y se cerró.
 * - [SPOT_RELEASED] — el usuario aportó a la comunidad. El acto que hace crecer el producto.
 * - [PAYWALL_SHOWN] — llegó a la pantalla de compra. La primera columna del embudo de ingreso.
 *
 * ⛔ No se añade uno "por si acaso": cada nombre nuevo es un hecho más que un tercero sabe de
 * nuestros usuarios, y hay que poder defenderlo en la política.
 */
enum class AdAttributionEvent(val wire: String) {
    PARKING_CONFIRMED("parking_confirmed"),
    SPOT_RELEASED("spot_released"),
    PAYWALL_SHOWN("paywall_shown"),
}

/**
 * La lista blanca, como función pura y `when` exhaustivo: añadir un [AnalyticsEvent] obliga a
 * decidir aquí si sale del móvil, en vez de heredar un `else -> null` que nadie vuelve a leer.
 *
 * `null` = no sale. Y los `null` de hoy son deliberados:
 * - [AnalyticsEvent.TriggerFired] es mecánica de detección — el denominador de nuestras tasas.
 *   A Meta no le dice nada y es el evento más frecuente de la app con diferencia.
 * - [AnalyticsEvent.ParkingSessionEnded] es el cierre de la sesión, no un logro: la salida ya se
 *   cuenta como [SPOT_RELEASED] cuando publica plaza.
 * - [AnalyticsEvent.SpotReported] es un aviso manual sin coche de por medio. Se puede repetir sin
 *   límite, así que como señal de valor es ruido.
 */
fun AnalyticsEvent.asAdAttributionEvent(): AdAttributionEvent? = when (this) {
    is AnalyticsEvent.ParkingSessionStarted -> AdAttributionEvent.PARKING_CONFIRMED
    is AnalyticsEvent.SpotReleased -> AdAttributionEvent.SPOT_RELEASED
    is AnalyticsEvent.PaywallShown -> AdAttributionEvent.PAYWALL_SHOWN
    is AnalyticsEvent.TriggerFired -> null
    is AnalyticsEvent.ParkingSessionEnded -> null
    is AnalyticsEvent.SpotReported -> null
}
