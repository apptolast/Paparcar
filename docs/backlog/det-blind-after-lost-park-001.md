# DET-BLIND-AFTER-LOST-PARK-001 · Perder un aparcamiento deja la app CIEGA para el viaje siguiente

**Estado:** 🔵 Abierto, sin código · detectado al diagnosticar el field 15-08
· **auditado el 03-09 contra master: SIGUE VIVO, y ya se sabe por qué exactamente** (ver abajo)

> ## Auditoría 03-09 — ⛔ el honest close NO lo cubre, y la razón es una línea
>
> Hipótesis natural al releerlo hoy: `DET-HONEST-CLOSE-001` llegó DESPUÉS de este ticket y su KDoc
> promete literalmente lo que aquí se pide — *«registers a fresh geofence at the new spot → the next
> departure always has a nominator, so the chain never breaks (the whole point of leaving a zone
> instead of nothing)»*. **Refutada.** En `CoordinatorDetectionService.maybeRunHonestClose`:
>
> ```kotlin
> val stalePin = ... getActiveSessionByVehicle(vehicleId)
> // No active pin (or no fence to key the step baseline) → nothing to release, nothing to prove.
> val staleGeofence = stalePin?.geofenceId ?: return
> ```
>
> El honest close **exige un pin previo que liberar** — su trabajo es *«el coche se fue de SU pin»*.
> El escenario de este ticket es el contrario: la sesión murió **sin dejar pin y sin haber ninguno
> activo** (el anterior se liberó al salir), así que ni entra en la escalera. La app se queda sin
> valla, sin sentry y con `EvaluateArEnterArmUseCase` devolviendo `NoSession`.
>
> Consecuencia para el diseño: la vía 1 del doc (*«dejar SIEMPRE algo vigilable»*) **no es aflojar el
> requisito de egress de `saveUnattendedZone`**, sino decidir si el honest close puede correr sin pin
> previo — y ahí su ancla no es «de dónde se fue el coche» sino «dónde estaba el móvil», que es una
> afirmación mucho más débil. La vía 2 (armar por AR sin sesión, en modo PREGUNTAR) sigue siendo la
> que respeta la doctrina, y sigue teniendo el riesgo de FP bus/taxi que el propio doc anota.
> **Ninguna de las dos se decide sin el user: no es implementación, es una elección de producto.**

## Problema

Field 15-08, Redmi: la ida acabó en `aborted_unattended_no_drive` sin pin (causa raíz cerrada en
[DET-UNVERIFIED-ARM-DRIVE-PROOF-001]). La **vuelta**, 4 h después, no generó ni una sola sesión ni
evento de diagnóstico. No fue OEM-kill: la app estaba viva a las 21:59Z ejecutando su propio timeout.

Sin pin no hay nada que despierte a la app:

- No hay geocerca que romper → sin `GEOFENCE_EXIT`.
- El sentry (`SignificantMotionMonitor`) se arma alrededor de la valla del coche aparcado.
- `EvaluateArEnterArmUseCase` abre con `session?.geofenceId ?: return ArEnterDecision.NoSession`.

Un aparcamiento perdido no cuesta un pin: cuesta **todos los viajes siguientes** hasta que el usuario
abre la app y coloca el coche a mano. El fallo se amplifica en vez de amortiguarse.

## Doctrina implicada

Ninguna se viola directamente — cada guard hace lo suyo. Pero el sistema entero incumple *"todo
trigger dispara SIEMPRE"* por omisión: al no haber pin, no hay trigger que pueda disparar.

## Señales / datos disponibles

- La sesión perdida conoce su ancla (en el field: precisión 5–8 m, exactamente donde estaba el coche).
- `saveUnattendedZone` ya existe (zona aproximada, [DET-NODRIVE-ZONE-001]) y su rama exige
  `noDriveAnchor != null && liveEgress && vehicularSignal`. En el field falló `liveEgress`: el móvil
  se quedó a metros del coche, así que `anchorToCurrentMeters < minEgressDisplacementMeters` — el
  usuario aparcó y no se alejó.
- `detectionPath = unattended_zone_no_drive_egress` ya existe para marcar esas zonas.

## Diseño (a decidir)

Dos vías, no excluyentes:

1. **Que el nudge no sea el único superviviente.** Cuando se pierde un aparcamiento pero hay ancla
   creíble, dejar SIEMPRE algo vigilable (zona aproximada con su geocerca) aunque no se plante un pin
   de plaza. Hoy la vía existe pero la corta el requisito de desplazamiento del egress.
2. **Una vigilancia que no dependa de tener pin.** Si el coche está "en paradero desconocido", el
   armado por AR no puede atarse a la valla del coche… pero puede seguir siendo evaluador y pedir
   confirmación en vez de rendirse.

⚠️ Riesgo: cualquier vigilancia sin pin reabre la clase de FP del autobús/taxi (nada que ate el
embarque al coche propio). Por doctrina, lo que salga de aquí debe PREGUNTAR, no plantar.

## Criterio de éxito

Tras una sesión que muere sin pin, el siguiente viaje real genera al menos una sesión de detección
(aunque acabe en pregunta). Hoy genera cero.
