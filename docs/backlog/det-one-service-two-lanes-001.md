# DET-ONE-SERVICE-TWO-LANES-001 · ¿El carril BT se muda al sentinel, o dos servicios con dos notificaciones?

**Estado:** ✅ Done (2026-09-15) — fusión ejecutada (opción A, decisión del user: "Ahora");
mergeado a master con squash; el hash vive en `MEMORY.md`. Gate de campo pendiente: el viaje del
Kamiq valida este hosting junto a todo lo BT.
**Verificado:** suite completa verde (replays de detección intactos · `EvaluateBtParkUseCaseTest`
37/37 sin tocar) · verify mock · compiles mock/prod/iosSimulatorArm64 · manifest sin el servicio ·
cero referencias vivas a los símbolos retirados (`ACTION_BT_OVERRIDE`, `BT_TRIP_START/STOP`,
`BT_DETECTION_NOTIFICATION_ID`).
**Contexto:** la fase 3 del pivote decidió "FGS único: el tracker monta en el sentinel" — y el
tracker montó. Pero el carril BT entero sigue teniendo servicio propio, y el receiver del
disconnect ahora habla con LOS DOS: `ACTION_BT_TRIP_STOP` al sentinel y `ACTION_BT_DISCONNECTED`
a `BluetoothDetectionService`. La pregunta del user: si ya usamos el Coordinator, ¿para qué el otro?

## Estado real (14-09, master `6622f8d9`)

| | `BluetoothDetectionService` | `CoordinatorDetectionService` |
|---|---|---|
| Hospeda | caza del disconnect (`BluetoothParkingDetector.detectParking`) · trabajo instantáneo del connect (cancelar caza + acelerar red) · vigilancia de salida [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] | coordinator + sentry + grabador (`btTrackingJob`) |
| Vida | por evento (nace/muere) | residente |
| Decisión | **ya extraída a puro**: `EvaluateBtParkUseCase` (engagement, candidato, ride proof, walk-away) — el service es I/O: loops de sampling, timeouts, notificaciones | decisión en use cases, service I/O + intake [DET-INTAKE-001] |

**Dato que inclina la balanza hacia la fusión**: el interior de `BluetoothDetectionService` ya está
"use-case-ificado" — la mudanza es mover HOSTING (coroutines + timeouts), no reescribir decisiones.

## Opción A · Fusión: el carril BT entra al sentinel como acciones del intake

`ACTION_BT_DISCONNECTED` / `ACTION_BT_CONNECTED` pasan a comandos del intake; la caza vive en un
`btHuntJob` (slot hermano de `btTrackingJob`); `BluetoothDetectionService` se borra.

**Pros**: de verdad UN FGS y UNA notificación · el connect-cancela-caza y el override de arbitraje
[DET-TIERS-001] se serializan por la misma puerta que todo lo demás (hoy son dos servicios
coordinándose por intents) · el receiver queda con UN destinatario · menos superficie de manifest.

**Contras / riesgos (lo que hay que analizar con calma)**:
1. **Cancelación por reconnect (BT-005)**: hoy el CONNECT cancela la caza vía `onStartCommand` del
   mismo servicio; fusionado, es un comando de intake que cancela `btHuntJob` — semántica igual,
   pero hay que demostrarlo con los tests/replays, no asumirlo.
2. **La promesa de 5 s** (clase BUG-FGS-001): el receiver decide `startService` vs
   `startForegroundService` según haya plaza que vigilar; el sentinel promueve SIEMPRE en
   `onStartCommand` → un connect sin plaza pagaría un parpadeo de notificación que hoy no paga.
   Mitigable (acción-sin-promoción) pero es tocar la promoción del sentinel.
3. **Epílogos**: `resolveIdleEpilogue` ganaría un tercer job que poseer (`btHuntJob`) — tres guards
   de teardown; el patrón ya existe (fase 3) pero cada slot nuevo multiplica estados.
4. **Arbitraje**: con la caza dentro, el sentinel hospeda a la vez la sesión que aborta y la
   autoridad que la aborta. El intake lo serializa, pero es LA doctrina (carriles separados)
   encarnada físicamente la que se difumina — habría que re-escribirla como "carriles separados =
   slots + use cases separados, mismo host".
5. La vigilancia de salida del connect (`f2800a11`, ⏳ sin validar en campo) se mudaría también —
   apilar mudanza sobre código sin validar es lo que la ficha del Kamiq desaconseja.

## Opción B · Statu quo documentado

Dos servicios, doble notificación posible en dos ventanas acotadas (disconnect con sentinel vivo;
connect-con-plaza ≤5 min + grabador). Cero riesgo, coste = estética + esas ventanas.

## Alcance adicional (va con este ticket, decida lo que se decida)

- **Limpieza del receiver** (pedida por el user): los dos envíos del disconnect están desalineados
  (uno con variable `serviceIntent`, otro inline) — unificar estilo y, de paso, un helper local
  `fun send(service, action, extras)` que deje el bloque legible.

## Recomendación provisional (a discutir)

La fusión es **coherente con la dirección ya decidida** (FGS único, decisión en puro, acciones por
intake) y más barata de lo que parecía porque la decisión ya está extraída. Pero es cirugía sobre
dos zonas field-validated (BT-005, promoción FGS) con una pieza aún sin validar en campo
(`f2800a11`). Orden sensato: **validar en campo lo pendiente (viaje Kamiq) → entonces fusionar**,
con replays antes/después. Hasta entonces, B.

## Criterio de éxito (si se fusiona)

- `BluetoothDetectionService` borrado; manifest sin su entrada; receiver con un solo destinatario.
- Suite completa + replays BT idénticos en veredicto; galería/mock intactos.
- En device: un connect sin plaza NO parpadea notificación; el reconnect sigue cancelando la caza.
