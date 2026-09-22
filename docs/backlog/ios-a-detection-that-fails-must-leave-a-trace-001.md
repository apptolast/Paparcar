# IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001 · en iOS, una detección que falla no deja rastro en ninguna parte

**Estado:** ✅ Done · nace de la **issue #125** · ⏳ la verificación que falta es de CAMPO: primer
viaje con el iPhone y el gate ya encendido en las dos bases

## Problema

La issue #125 dice que en iOS la detección automática no funcionó **ni una sola vez** (cuenta
`oglnuRxQ…`, permisos dados, coche aparcado a mano el 19-09). Al ir a diagnosticarlo no había nada
que leer:

- `diagnostics/{uid}/sessions` **vacío en las dos bases** (`(default)` y `debug`).
- Ningún `diagnostics_reports/{uid}`.
- El gate se encendió el **21-09 22:39**, y la sesión ya estaba cerrada desde las **16:17** de ese
  día. La ventana del fallo no la cubre nadie.

Y eso no es mala suerte: **en iOS no hay ningún sitio donde el fallo pudiera haberse escrito.**
Tres agujeros encadenados, más la causa raíz que los cuatro juntos taparon:

### A · No hay sumidero de log

`Napier` **no tiene ningún antilog instalado en iOS**. El KDoc de `PaparcarLogger` dice que iOS
llama a `NapierProxyKt.debugBuild()` «en el entry point de Swift»: `iOSApp.swift` no lo hace, y no
existe el equivalente de `FileAntilog`. Cada línea que escribe el carril iOS se evapora.

### B · El informe del usuario viaja vacío

`LocalDiagnosticsLog` sólo tiene implementación en `androidMain`. En `DomainModule` se resuelve con
`getOrNull()`, así que en iOS es `null` y `SendDiagnosticsReportUseCase` sube **sólo cabeceras**.
Está documentado («headers-only»), pero es justo lo que convierte a iOS en un móvil del que no se
puede sacar un viaje: en Android, `parkdiag.log` es la red que salva lo que Firestore pierde.

### C · Las etiquetas del carril iOS no son `PARKDIAG`

`FileAntilog` sólo persiste lo que lleva el prefijo `PARKDIAG`. Los diez `TAG` de `iosMain` son
`"IosDetectionController"`, `"IosStepDetector"`, `"IosWakeMonitors"`… **Instalar el fichero sin
esto escribiría cero líneas del carril.** Lo de `commonMain` (`PARKDIAG/Coord`, `PARKDIAG/Route`,
`PARKDIAG/SyncOutbox`) sí entraría.

### D · La causa raíz: una valla que no se registra dice que sí

```kotlin
// IosGeofenceManagerImpl.createGeofence
runCatching {
    …
    manager.startMonitoringForRegion(region)   // ⛔ NO lanza. Nunca.
}
```

`startMonitoringForRegion` no lanza: si la autorización no es **«Siempre»**, si se pasó el tope de
**20 regiones** o si la clase no se puede monitorizar, falla **asíncronamente** por el delegate.
Así que `createGeofence` devuelve `Result.success` **siempre**, y la app cree que el coche está
vigilado cuando no hay ninguna región puesta. Sin valla registrada **el carril de salida no puede
dispararse jamás** — que es exactamente el síntoma «ni una sola vez».

¿Y el único canal por el que ese fallo asíncrono habla?

```kotlin
// IosDetectionController:399
is GeofenceEvent.Error -> {
    Napier.w("region monitoring error: ${event.error}", tag = TAG)  // (A) sin sumidero, (C) sin PARKDIAG
    reconcileRegions("monitoring-error")                            // reintenta y vuelve a fallar
}
```

No llama al `detectionEventLogger`, así que **tampoco llega a Firestore ni con el gate encendido**.

## Doctrina violada

- **«Todo pin persiste su provenance» / el contrato de detección**: *la única excusa aceptable es el
  OS, **y debe ser detectable a posteriori***. Aquí ni siquiera el fallo propio es detectable.
- **Un caso de uso por VEREDICTO**: `DetectionEvent.GeofenceRegistration(success, failure, source)`
  **ya existe** en `commonMain` y lo emiten el janitor y el safety-net de Android con su
  `GeofenceRegistrationFailure`. iOS tiene el vocabulario y no lo habla. No hay que inventar nada.
- **Copy/telemetría sin mecánica interna, pero SIN silencios**: una vigilancia que no está puesta es
  un hecho del producto, no un detalle de implementación.

## Señales / datos disponibles

- `DetectionEvent.GeofenceRegistration` + `GeofenceRegistrationFailure` (`NOT_AVAILABLE`,
  `TOO_MANY_GEOFENCES`, `PERMISSION_DENIED`, `UNKNOWN`) — ya en commonMain, ya con su `label` de wire.
- iOS puede **preguntar antes de registrar**, que es mejor que adivinar después:
  `CLLocationManager.authorizationStatus`, `isMonitoringAvailableForClass(CLCircularRegion)`,
  `monitoredRegions.count` contra el tope de 20.
- `platform.zlib` está en las platform libs de `ios_arm64` → el snapshot gzip es posible sin cinterop
  propio.

## Diseño

**Una detección que falla deja rastro en el móvil, y el móvil lo puede entregar sin cable.** Cuatro
piezas, una por agujero:

1. **`IosFileAntilog`** — el espejo de `FileAntilog`: persiste las líneas `PARKDIAG*` en
   `Library/Application Support/parkdiag.log`, misma política de rotación (5 generaciones × 5 MB),
   handle abierto entre líneas y `synchronize()` por entrada (el equivalente del `flush`: entrega los
   bytes al sistema, así que sobreviven a una muerte del proceso). Se instala en
   `PaparcarIosBootstrap.ensureStarted()`, que es el único sitio por el que pasan **las dos** puertas
   (arranque de UI y relanzamiento en background).
2. **`IosLocalDiagnosticsLog`** — snapshot de `.1` + activo, concatenado del más viejo al más nuevo y
   gzip por `platform.zlib` (`deflateInit2_` con `windowBits = 15 + 16`, que es la envoltura **gzip**,
   no la zlib de `compress2`: lo que hay al otro lado es un `gunzip`). Se liga en `iosPlatformModule`.
3. **Las etiquetas del carril iOS pasan a `PARKDIAG/…`**, con el mismo reparto que Android.
4. **El fallo de valla habla el vocabulario que ya existe**: `createGeofence` comprueba ANTES lo que
   iOS sí sabe contestar (autorización, disponibilidad, tope de 20) y devuelve `Result.failure` con
   su `GeofenceRegistrationFailure`; el error asíncrono del delegate entra por el mismo sitio. Los dos
   caminos emiten `DetectionEvent.GeofenceRegistration(success = false, failure = …, source = …)`.
   El mapeo `CLError → GeofenceRegistrationFailure` vive **en commonMain**, junto al de Play Services
   y por la misma razón que dice su KDoc: «el mapeo vive aquí, puro y testeable; la plataforma sólo
   saca el entero».

⛔ **Lo que NO entra**: cambiar la política de reintento ni el contenido de la pantalla de salud.
Que el fallo sea VISIBLE para el usuario es la consecuencia natural de esto y se decide con el dato
delante, cuando el primer viaje instrumentado diga qué falla de verdad.

## Criterio de éxito

- `GeofenceRegistrationFailureTest` (commonTest, corre también en iOS): cada código de `CLError`
  mapea a su razón, y uno desconocido cae en `UNKNOWN` sin perder el mensaje.
- `./gradlew :shared:compileKotlinIosSimulatorArm64` verde (Windows compila el klib de iOS; sólo el
  enlazado del framework necesita Xcode).
- En device: con el gate encendido, un intento de detección deja **o** una sesión en
  `diagnostics/{uid}/sessions` **o** un `GeofenceRegistration(success=false)` que diga por qué no.
- «Informar de un problema» desde el iPhone sube un `logGzip` no nulo.

## Estado

Implementadas las cuatro piezas. `./gradlew testAll` ✅ · `:shared:compileKotlinIosSimulatorArm64`
✅ · guards de Konsist con `--rerun-tasks` ✅.

**Testigos falsificados** (revertida la producción, fallan):
- `ConfirmParkingUseCaseTest.should record why the fence failed when the platform named the reason`
  — el registro de valla de CADA aparcamiento archivaba un `success = false` pelado.
- Más su pareja: una excepción sin nombre **no** inventa una razón (`failure` sigue null).
- `GeofenceRegistrationFailureTest` ×3 para el mapeo de `CLError`, incluido que el tope de regiones
  gana al código que lo esconde.

⚠️ **Lo que NO tiene testigo ejecutable, y hay que decirlo**: el escritor de fichero
(`IosFileAntilog`), el snapshot gzip y el preflight de `CLLocationManager` son I/O de iOS. Windows
compila el klib pero **no puede ejecutar nada de iOS**, así que de esas tres piezas sólo está
probado que compilan. Lo que se pudo mover a commonMain —el mapeo de razones, y que el `Result` del
registro llegue con su porqué— se movió y sí está probado. La verificación de las otras tres es de
campo: primer viaje con el iPhone, `parkdiag` no vacío en el informe.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `SendDiagnosticsReportUseCase` (`localLog = getOrNull()`) | iOS sin log → cabeceras | ✅ sigue igual de tolerante; ahora hay qué mandar |
| `DiagnosticsReport.logGzip` KDoc («null en iOS») | iOS no tiene log | ✅ reescrito, junto al de `LocalDiagnosticsLog` y el de `DomainModule` |
| `FileAntilog` (Android) | nombres de fichero propios | ✅ pasan a `ParkdiagLogFiles` en commonMain, una sola definición |
| `AndroidLocalDiagnosticsLog` | `FileAntilog.BASE_NAME` | ✅ misma constante compartida |
| `PaparcarLogger` KDoc («iOS: NapierProxyKt.debugBuild() en Swift») | iOS sin sumidero | ✅ reescrito — y la frase era doblemente falsa: nadie lo llamaba, ni en debug |
| `GeofenceRegistrationFailure.fromStatusCode` | sólo códigos GMS | ✅ gana hermano `fromCLErrorCode`, no se toca el existente |
| Android `GeofenceFailureMapping` | `Throwable` → razón | ✅ exento, es el lado Android del mismo contrato |
