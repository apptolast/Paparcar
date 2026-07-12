# ZONE-SUBSCRIBE-001 — Suscripción a plazas por zona ("Avisarme cuando haya una")

## User story

**Como** usuario sin plaza a la vista en mi zona, **quiero** suscribirme a
"avisarme cuando haya una plaza aquí" **para** recibir una notificación cuando
la comunidad libere una plaza cerca.

## Origen

Botón secundario "Avisarme cuando haya una" (campana) del sheet browse-sin-plazas
del rediseño **UI-SHEET-001**. Se EXCLUYÓ de esa pasada porque no existe backend:
`Zone` hoy es solo "lugar habitual para navegación" — no hay suscripción ni
notificación de plazas por zona en el domain. El hueco quedó anotado en
`HomeSpotRows.kt` (empty state).

## Decisión abierta (resolver al definir)

- **(a)** Extender `Zone` existente con flag `notifyOnSpot` + radio, o
- **(b)** Entidad propia `ZoneSubscription` con TTL de la suscripción (p. ej.
  30 min) para no notificar eternamente — el interés por aparcar es efímero.

## Alcance técnico estimado

- **Domain**: entidad + use cases subscribe/unsubscribe (`Result<Unit>`), tests.
- **Firestore**: colección + security rules.
- **Trigger de notificación** al publicarse un spot dentro del radio: Cloud
  Function o query geohash en cliente vía FCM topic — evaluar coste/latencia.
- **UI**: botón campana en el PapSheet de browse-sin-plazas (empty state) +
  estado "suscrito" (misma silueta de chip, sin verde sólido) + gestión en
  Ajustes/Zonas.
- **i18n**: EN+ES mínimo, sweep P1/P2.
- **Dev Catalog**: variante de galería con la suscripción activa/inactiva.

## UX

- La campana pasa a estado activo (suscrito) con el mismo estilo de chip.
- La notificación lleva deep-link a la plaza (referencia de patrón:
  `StartAddParkingEventBus` de DET-TOGGLE-002).
- Copy sin mecánica interna (workers/frecuencias): causa + consecuencia +
  remedio.
