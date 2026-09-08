# ZONE-SUBSCRIBE-001 — Suscripción a plazas por zona ("Avisarme cuando haya una")

**Estado:** 🟡 **ENTREGADO por la opción (a) vía MONEY-001 — queda ABIERTA solo la decisión (b), el TTL.**
Reverificado contra el código el **09-09-2026**.

> ⚠️ Este doc dijo «sin código, 0 commits de implementación» hasta el 09-09-2026, y para entonces
> llevaba tiempo siendo **falso**: la premisa de abajo («no hay suscripción ni notificación de
> plazas por zona en el domain») la derogó MONEY-001 sin que nadie volviera a esta ficha. Es
> exactamente el fallo que el barrido [DOCS-BACKLOG-TRUTH-002] existía para evitar: un doc no se
> queda obsoleto por estar mal escrito, sino porque **otro ticket le quita la razón de ser y no
> pasa por aquí**.

## Qué existe ya (verificado, no recordado)

La **opción (a)** de la decisión de abajo está implementada:

| Pieza | Dónde |
|---|---|
| Flag de suscripción por zona | `Zone.notifyOnSpot` (`domain/model/Zone.kt:32`) |
| Observador que dispara el aviso | `domain/usecase/zone/ObserveZoneSpotAlertsUseCase.kt` |
| Notificación al usuario | `AppViewModel:128` → `AppNotificationManager.showZoneSpotAlert` |
| Interruptor maestro | Ajustes → Notificaciones (`settings_notif_zone_alerts`) |
| Elección por zona | modal de la zona (`ZoneNotifyToggle` → `SetZoneNotify`) |
| Gate de pago | Plus, con paywall (`PaywallReason.ZONE_ALERTS`) |

## Lo que QUEDA de este ticket

1. **La decisión (b), sin tomar: ¿la suscripción caduca?** Hoy es un opt-in **permanente** por zona.
   La idea original proponía un TTL (~30 min) porque *el interés por aparcar es efímero*: te
   suscribes cuando estás dando vueltas, no para siempre. Con una zona "Casa" suscrita para siempre,
   el aviso llega también un martes a las 4 de la mañana. **Es la única parte con sustancia.**
2. **Un comentario que miente** en `HomeSpotRows.kt:281`: justifica la ausencia del botón "Avisarme
   cuando haya una" en el empty state de browse diciendo *«there is no zone-subscription backend
   yet»*. El backend existe. Si ese botón sigue fuera hoy, es por otra razón (¿un empty state debe
   ofrecer crear una zona?), y el comentario tiene que decir esa, o irse.

---

> Lo que sigue es el texto ORIGINAL (03-09-2026), conservado para que se lea de dónde venía la
> decisión. **Su premisa ya no es cierta** — ver arriba.

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
