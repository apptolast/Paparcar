# DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001 · la red de seguridad pide un fix GPS declarándose sincronización de datos

**Estado:** ✅ Done · en master como `2e777e3b` — *"the safety net asked for a GPS fix calling itself
data synchronisation"*. La rama `bugfix/…-location-type` y el worktree `../Paparcar-fgs-type` ya no
existen.

> Cerrado el 2026-08-30 por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]: este doc siguió declarando
> *"🔵 En progreso"* sobre una rama borrada. Verificado por las tres vías de `DOCS-BACKLOG-TRUTH-001`
> — `git log master --grep`, existencia de la rama, y el tipo del `ForegroundInfo` en el árbol.
>
> ⛔ El aprendizaje que **no** hay que perder: `DATA_SYNC` lo inyecta AGP por su cuenta al fusionar
> manifiestos. Que el permiso apareciera no significaba que lo hubiéramos pedido nosotros.

**Origen:** 2026-08-27. Salió de una pregunta del user sobre qué nos aprietan las versiones nuevas de
Android. Al auditar la superficie de foreground services apareció un permiso
`FOREGROUND_SERVICE_DATA_SYNC` que **yo di por muerto y no lo estaba** — y al buscar su consumidor
apareció esto, que es mejor hallazgo que el permiso.

## Problema

`ParkingSafetyNetWorker` se encola con `setExpedited(...)` y define `getForegroundInfo()`, así que
**WorkManager lo promueve a foreground service**. Pero el `ForegroundInfo` se construye sin tipo:

```kotlin
override suspend fun getForegroundInfo(): ForegroundInfo =
    ForegroundInfo(
        AppNotificationManager.DETECTION_NOTIFICATION_ID,
        foregroundNotificationProvider.buildDetectionNotification(),
    )   // constructor de 2 args → el tipo lo decide WorkManager
```

El worker **pide un fix GPS** (`getOneLocation: GetOneLocationUseCase`) — es la comprobación que mide
si sigues junto al coche. Su tipo real es `location`. El permiso que la app declara para cubrir esa
promoción es `FOREGROUND_SERVICE_DATA_SYNC`.

O sea: **el servicio que sostiene la red de seguridad se anuncia al sistema como sincronización de
datos mientras hace trabajo de ubicación.**

## Por qué importa, más allá de la etiqueta

1. **`dataSync` tiene techo de duración desde Android 15 y `location` no.** Estamos apoyados en un
   tipo que el sistema caduca, para un trabajo que no debería caducar. Hoy no muerde porque los
   chequeos son cortos, pero es deuda apuntando en la dirección equivocada.
2. **Es exactamente el componente que no puede fallar en silencio.** El safety net existe para
   reconciliar las salidas que el OS no entregó; si su promoción a foreground es rechazada, se pierde
   la última red — y sin telemetría que lo diga.
3. **Ninguno de nuestros móviles puede verlo.** Los dos están en Android 13 y el endurecimiento de
   tipos es de 14+. Con `targetSdk = 37` estamos aceptando reglas que no ejercitamos nunca. Ver el
   punto ciego del banco en la memoria del setup de dispositivos.

## Doctrina violada

Ninguna de detección — el algoritmo no cambia. Lo que rompe es el principio del proyecto de que
**una declaración debe decir la verdad sobre lo que hace**: es el mismo defecto de forma que
`DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C` y `DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001`, donde
el sistema decidía cosas que su propia declaración no reflejaba.

## Señales / datos disponibles

- `androidx.work 2.11.2` declara en su AAR **sólo** `FOREGROUND_SERVICE` — verificado descomprimiendo
  su `AndroidManifest.xml`. `FOREGROUND_SERVICE_DATA_SYNC` sale **únicamente de nuestro manifiesto**.
- En el manifiesto fusionado, `androidx.work.impl.foreground.SystemForegroundService` aparece **sin
  `foregroundServiceType`**.
- Nuestros tres servicios propios ya son `foregroundServiceType="location"`; el desajuste es sólo el
  de WorkManager.

## Diseño

Tres piezas, y **el orden importa** — quitar el permiso primero sería el bug que este ticket evita:

1. **Tipar la promoción**: `ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)`.
   El constructor de 3 args existe en WorkManager; por debajo de API 29 el tipo se ignora, así que
   `minSdk 26` no necesita guarda.
2. **Permitirlo en el manifiesto**: el tipo que se pasa en runtime debe estar declarado en el
   servicio, y el de WorkManager no declara ninguno. Se fusiona con `tools:node="merge"` (requiere
   añadir `xmlns:tools` al manifiesto, que hoy no lo tiene).
3. **Y sólo entonces** retirar `FOREGROUND_SERVICE_DATA_SYNC`, que pasa a estar cubierto por
   `FOREGROUND_SERVICE_LOCATION`, ya declarado.

## Criterio de éxito

- El manifiesto fusionado muestra `SystemForegroundService` con `foregroundServiceType="location"` y
  **sin** `FOREGROUND_SERVICE_DATA_SYNC` en los permisos.
- `assembleProdDebug` y `assembleMockDebug` verdes; suite completa verde.
- ⏳ **No verificable en nuestros móviles**: el endurecimiento es de Android 14+ y ambos están en 13.
  Primer candidato a validar el día que haya un aparato con 15/16 — en cuyo caso hay que ver el
  safety net promoverse sin `SecurityException` y su notificación aparecer.

## Consumidores auditados

`SystemForegroundService` es **compartido por todo el trabajo de WorkManager en foreground**, así que
tiparlo `location` sería un bug si algún otro worker hiciera trabajo de otra naturaleza.

`grep -rln "getForegroundInfo\|setForeground(" composeApp/src` · `grep -rn "setExpedited"`

| Sitio | Qué es | Clasificación |
|---|---|---|
| `worker/ParkingSafetyNetWorker.kt` | **el único** con `getForegroundInfo()` y el único con `setExpedited` | **cerrado** — es el cliente que se tipa |
| `detection/ArrivalHandoffDetectionImpl.kt` | sólo una mención en KDoc | **exento** |
| `domain/detection/GhostFgsReapDecision.kt` (+ su test) | doc sobre la notificación que el worker publica | **exento** |
| Los otros 21 workers del proyecto | ninguno se promueve a foreground | **exento** — no tocan ese servicio |

✅ Un solo cliente, y hace trabajo de ubicación. El tipo `location` es correcto para el 100% del uso.

## Estado de ejecución

- [x] **Pieza 1** — `ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)`.
- [x] **Pieza 2** — `SystemForegroundService` fusionado con `foregroundServiceType="location"` vía
      `tools:node="merge"` (+ `xmlns:tools`, que el manifiesto no tenía).
      **Verificado en el manifiesto fusionado del APK.**
- [x] `assembleProdDebug` + `compileMockDebugKotlinAndroid` verdes, sin warnings.
      **1.708 tests, 0 fallos.**
- [x] Sin strings, pantallas ni estados MVI → no toca los 9 locales ni el Dev Catalog.
- [x] Sin cambios en `detectionPath` / `armEvidence`.

### ⚠️ Pieza 3: el permiso NO se puede quitar — y eso es un hallazgo, no un olvido

Retirarlo de nuestro manifiesto **no lo elimina del fusionado**. Lo comprobado, en este orden:

1. Borrado de `androidMain/AndroidManifest.xml` → sigue apareciendo.
2. `tools:node="remove"` explícito → **sigue apareciendo**.
3. Escaneadas **1.095 `AndroidManifest.xml`** de la caché de transforms de AGP: **ninguna dependencia
   lo declara**, y ninguna declara un `<service>` tipado `dataSync` del que pudiera derivarse.
4. El blame del merger (`manifest_merge_blame_file`) **no se lo atribuye a nadie**: aparece en la
   salida de `processProdDebugMainManifest` sin estar en su propio informe de autoría.

Conclusión: **lo inyecta AGP 9.3.2 después del merge**, presumiblemente por la mera presencia de
`androidx.work`. No está bajo nuestro control.

Lo que el ticket sí consigue, que era el fondo del asunto:

- **la promoción ya se declara `location`**, que es lo que el worker realmente hace;
- **deja de depender de un tipo con techo de duración** (`dataSync` caduca desde Android 15,
  `location` no) — este era el riesgo real, y está cerrado;
- **nuestro manifiesto deja de reclamar un permiso que no usa**, que es la parte que sí controlamos.

El permiso sobrante en el APK queda como ruido de AGP, sin consumidor. Si algún día molesta (revisión
de Play, auditoría de permisos), el sitio donde mirar es el manifest merger de AGP, no nuestro código.

## Pendiente

- [ ] ⏳ **Verificación en Android 14+**, imposible en nuestros móviles (ambos en 13). Al probarlo:
      el safety net debe promoverse sin `SecurityException` y su notificación aparecer. Primer
      candidato para el tercer aparato del punto ciego del banco.
