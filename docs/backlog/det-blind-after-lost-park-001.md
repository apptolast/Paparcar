# DET-BLIND-AFTER-LOST-PARK-001 · Perder un aparcamiento deja la app CIEGA para el viaje siguiente

**Estado:** 🔵 Abierto, sin código · detectado al diagnosticar el field 15-08

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
