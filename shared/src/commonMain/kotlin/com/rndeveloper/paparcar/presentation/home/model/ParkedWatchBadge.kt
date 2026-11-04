package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.ServicePresence

/**
 * Honest status badge for the parked/active vehicle's departure watch. [DET-WATCH-HONEST-001]
 *
 * "Vigilando tu sitio" must be TRUE, not aspirational: it may only show when the watch is genuinely
 * live. The old surface derived "Parked → watching" from a session merely existing, so it kept
 * claiming to watch even after an OEM killed the resident foreground service — a silent false
 * negative dressed as a green badge.
 *
 * This is a pure decision over the real signals so it is unit-testable and platform-free; the UI
 * renders the chosen badge and its action. It is scoped to the "detection applies" context (there is
 * a parking-capable active vehicle, detection is ON, permissions granted) — the caller resolves the
 * NoVehicle / Blocked / Inactive / Silent cases upstream via [DetectionUiState].
 */
enum class ParkedWatchBadge {
    /** No parked session for a car that could park → invite the user to mark where they left it. */
    PARK_MY_VEHICLE,

    /** Parked and genuinely watching (FGS resident/active, or the Bluetooth receiver covers it) on a
     *  reliable setup → the real "Vigilando tu sitio". */
    WATCHING,

    /** Parked and watching, but background survival is fragile (no battery exemption on an aggressive
     *  OEM) → still watching for now, but warn + offer the exemption so it doesn't get killed. */
    WATCHING_FRAGILE,

    /** Parked under COORDINATOR but the foreground service is DEAD — the OS killed the watcher.
     *  Not watching → offer to reactivate (a foreground start is legal). */
    WATCH_INTERRUPTED,

    /**
     * Aparcado bajo COORDINATOR y **todavía no sabemos** si se está vigilando: el servicio no ha
     * hablado en este proceso ([ServicePresence.Unknown]).
     * [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
     *
     * ⛔ **No es un estado de la vigilancia, es la falta de información sobre ella**, y por eso no
     * puede colapsarse en ninguno de los otros tres:
     *  - como [WATCH_INTERRUPTED] acusaría al sistema de un asesinato que nadie ha medido — que es
     *    exactamente lo que Home hacía en cada arranque en frío;
     *  - como [WATCHING] prometería una vigilancia que no podemos demostrar, que es la mentira que
     *    [DET-WATCH-HONEST-001] existe para impedir.
     *
     * ⛔ **Y no se pinta.** La historia lo resuelve a `Hidden`: la card del coche ya dice la verdad
     * — aparcado, y dónde — sin afirmar nada sobre el vigilante, y un beat después habrá veredicto.
     * Un aviso que se desdice solo cuesta más que el silencio que lo precede.
     */
    WATCH_UNKNOWN,
}

/**
 * Resolve the honest watch badge from the real detection signals.
 *
 * @param hasParkedSession whether the active/parked vehicle has a live parking session.
 * @param isBluetoothCovered whether the parked vehicle is BT-paired — its departure (the reconnect
 *   when the user gets back in) is caught by the manifest ACL receiver with no resident FGS, so it is
 *   honestly "watching" regardless of [presence]. Fix-independent of the connected-gate resolver.
 * @param presence the Coordinator foreground service's real lifecycle. Only [ServicePresence.Sentry]
 *   or [ServicePresence.Active] mean the departure watch is actually live — y
 *   [ServicePresence.Unknown] no significa muerto, significa que nadie ha mirado todavía.
 * @param isReliabilityReduced true when the setup is fragile (no BT + no battery exemption + aggressive
 *   OEM) — the watch may be killed at any time. Never true for a Bluetooth-covered car.
 */
fun resolveParkedWatchBadge(
    hasParkedSession: Boolean,
    isBluetoothCovered: Boolean,
    presence: ServicePresence,
    isReliabilityReduced: Boolean,
): ParkedWatchBadge = when {
    !hasParkedSession -> ParkedWatchBadge.PARK_MY_VEHICLE
    // Bluetooth: the reconnect is caught by the manifest receiver independently of any FGS, so the
    // watch is live without a resident process — honestly "watching". [DET-BT-CONNECTED-NOT-PAIRED-001]
    isBluetoothCovered -> ParkedWatchBadge.WATCHING
    // ⛔ Sin noticias del servicio no hay veredicto, y eso va ANTES de cualquier afirmación: ni
    // «vigilando» ni «en pausa». [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
    presence == ServicePresence.Unknown -> ParkedWatchBadge.WATCH_UNKNOWN
    // Coordinator: the watch is only real while the foreground service is alive.
    presence == ServicePresence.Dead -> ParkedWatchBadge.WATCH_INTERRUPTED
    isReliabilityReduced -> ParkedWatchBadge.WATCHING_FRAGILE
    else -> ParkedWatchBadge.WATCHING
}

/**
 * ¿Esta transición del badge se ha ganado **interrumpir** al usuario abriendo la hoja sobre la fila
 * que lo explica? [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * ⚠️ **Es una corrección del plan de diseño.** La tarea 1.7 pedía *«un chip en la cabecera, en los
 * tres modos»* para los tres avisos de vigilancia. Al abrir el código, dos de las tres partes se
 * caen y la tercera cambia de sitio:
 *
 * | Lo que pedía el plan | Qué dice el código |
 * |---|---|
 * | chip «Falta permiso» | **ya existe, y mejor**: [com.rndeveloper.paparcar.presentation.home.sections.map.components.HomeLocationPill] dice «Sin ubicación» flotando sobre el mapa, es pulsable y **sobrevive a los modos de selección y de pin** — justo cuando un fix de ±80 m decide si el pin vale algo |
 * | el chip vive en el trailing de la cabecera | **ese badge VIVIÓ ahí y se retiró** (issue #27 del user): competía en ancho con el nombre del coche y la dirección, y la cabecera se oculta en selección/pin, así que el aviso desaparecía exactamente cuando hacía falta. Volver a ponerlo es revertir una decisión medida [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001] |
 * | que el aviso se vea en reposo | **esto sí falta**, y sólo para [ParkedWatchBadge.WATCH_INTERRUPTED]: hoy enterarse de que la app dejó de vigilar tu coche exige abrir la hoja |
 *
 * Y el remedio es el mecanismo que la app ya tiene para la ubicación rechazada —rebobinar la lista y
 * desplegar la hoja sobre la fila—, no una superficie nueva. La fila ya lleva causa, consecuencia y
 * remedio; un chip no cabe el remedio. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
 *
 * ## ⛔ Sólo la vigilancia MUERTA, nunca la frágil
 *
 * La asimetría es la de siempre en este proyecto, aplicada a quién puede interrumpir:
 * - [ParkedWatchBadge.WATCH_INTERRUPTED] es un **suceso**: el sistema mató al vigilante y la promesa
 *   está rota **ahora**. Se gana la interrupción.
 * - [ParkedWatchBadge.WATCHING_FRAGILE] es una **condición de configuración** —sin exención de
 *   batería en un OEM agresivo— y la vigilancia **sigue viva**. Es permanente para quien no da la
 *   exención, así que abrir la hoja por ella sería abrirla en cada arranque: eso no es avisar, es
 *   insistir. Ya tiene su fila y su paso del checklist, que es donde se arregla.
 *
 * ## ⚠️ Y esta función nació con un bug que su propio testigo defendía
 *
 * La primera versión abría la hoja en **cada arranque en frío** con coche aparcado, porque
 * `ServicePresence` valía `Dead` por defecto y el badge derivado pasaba por
 * [ParkedWatchBadge.WATCH_INTERRUPTED] antes de que el servicio dijera nada. Y había un test
 * —falsificado— que afirmaba que eso era lo correcto: *«llegar con la vigilancia ya muerta es la
 * misma noticia que verla morir delante»*. La frase es razonable; el problema es que la premisa era
 * falsa, porque aquello no era una vigilancia muerta, era una **sin medir**.
 *
 * La lección, que vale más que el arreglo: **un testigo verde puede estar protegiendo la regla
 * equivocada.** Falsificar prueba que el test MIRA; no prueba que mire lo correcto. Lo arregló
 * [ServicePresence.Unknown], no un `if` aquí. [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
 *
 * @param from el badge anterior, o null si no había contexto de vigilancia (ni aparcado ni arranque
 *   en frío). ⛔ Null sigue contando como transición, y ahora **sí** es honesto: con `Unknown` como
 *   valor inicial, llegar directamente a [ParkedWatchBadge.WATCH_INTERRUPTED] exige que el servicio
 *   haya dicho `Dead`, que es una muerte medida.
 */
fun watchInterruptionEarnsAnOpen(from: ParkedWatchBadge?, to: ParkedWatchBadge?): Boolean =
    to == ParkedWatchBadge.WATCH_INTERRUPTED && from != ParkedWatchBadge.WATCH_INTERRUPTED
