# Verificación en Mac — Fase 0 del port iOS (IOS-F0)

**Para:** compañero con Mac · **Fecha:** 2026-08-13 · **Contexto:** la Fase 0 del port iOS
(9 tickets, ver `docs/backlog/ios-f0-001.md`) se desarrolló íntegramente en Windows, donde los
targets iOS de Kotlin/Native **no compilan** — ni siquiera como check de tipos. Todo lo común y
Android está verificado (suites mock+prod verdes, APK instalable); lo que toca `iosMain` está
escrito "a ciegas" y necesita tu compilación.

## 1. Qué hacer (5 minutos si todo va bien)

```bash
git fetch origin
git checkout fix/IOS-F0-08-dead-toggles   # la punta del stack — contiene TODA la Fase 0
./gradlew :composeApp:compileKotlinIosArm64
./gradlew :composeApp:compileKotlinIosSimulatorArm64   # si el proyecto tiene este target
```

- **Si compila limpio:** responde "iOS compila ✓" y listo — no hace falta probar nada en device
  (la Fase 0 no añade comportamiento iOS en runtime, solo contratos, esqueletos y módulos Koin).
- **Si falla:** pega el error completo tal cual. Los fallos esperables son de firma de interop
  (ver §3) y se corrigen desde Windows en minutos con tu output.

## 2. El stack de ramas (por si prefieres revisarlas una a una)

Apiladas en este orden sobre `master`; cada una contiene las anteriores:

1. `fix/IOS-F0-01-bt-receiver-kdoc` — docs + KDoc, no toca iosMain
2. `feature/IOS-F0-02-trace-ingestion` — no toca iosMain
3. `feature/IOS-F0-05-ar-query-pull` — no toca iosMain (método con default en interfaz común)
4. `feature/IOS-F0-03-device-capabilities` — **toca iosMain**
5. `feature/IOS-F0-04-geofence-contract` — **toca iosMain**
6. `feature/IOS-F0-06-side-stores` — **toca iosMain**
7. `fix/IOS-F0-07-backup-rules` — no toca iosMain
8. `fix/IOS-F0-08-dead-toggles` (punta, incluye IOS-F0-09) — **toca iosMain**

## 3. Ficheros iosMain a vigilar (los escritos sin compilador)

| Fichero | Cambio | Riesgo típico |
|---|---|---|
| `di/IosPlatformModule.kt` | `single { DeviceCapabilities(false, false) }` | import/nada |
| `di/IosDetectionModule.kt` | bus común `SharedFlowGeofenceEventBus` + 4 singles nuevos (side-records, step-anchors) | referencias FQN |
| `detection/IosGeofenceManagerImpl.kt` | `override val minRadiusMeters = 100f`, clamp vía contrato, borrado del const privado | firma del override |
| `detection/IosGeofenceEventBusImpl.kt` | **BORRADO** (lo sustituye la impl común) | alguna referencia huérfana |
| `detection/IosDetectionSideRecords.kt` | **NUEVO** — 3 impls NSUserDefaults | firmas interop: `setObject(_:forKey:)`, `stringForKey`, `dictionaryRepresentation().keys.filterIsInstance<String>()` |
| `detection/sensor/IosDetectionStepAnchors.kt` | **NUEVO** — seal a NSUserDefaults, `stepsSinceSeal` → null (TODO F2) | `NSDate().timeIntervalSince1970` (patrón ya usado en el repo) |
| `ios/preferences/IosAppPreferences.kt` | eliminados `notifyParkingDetected`/`notifySpotFreed` (+2 consts) | ninguno — la interfaz común ya no los declara |

## 4. Qué NO hace falta verificar

- Comportamiento en runtime iOS: los side-records y step-anchors son esqueletos que nadie
  consume todavía (su primer consumidor será el orquestador de F1).
- Android: verificado en Windows en Redmi/OPPO territory — suites mock+prod verdes tras cada
  ticket, `assembleMockDebug` y `assembleProdDebug` OK.

## 5. Después del OK

Con tu "compila ✓", las ramas se mergean a `master` en el orden del stack (o directamente la
punta, que las contiene todas) y arranca la F1 del plan (`docs/IOS-IMPLEMENTATION-PLAN.md` §8):
`IosDetectionController`, el orquestador mínimo — ahí sí te necesitamos con Xcode y un iPhone.
