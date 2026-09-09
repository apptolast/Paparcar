# DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001 · Sin permiso de ubicación, reanudar el centinela MATA la app

**Estado:** ✅ Done (09-09-2026) · abierto como hallazgo lateral de
`UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001` (⛔ no se arregló ahí: es detección → skill
`det-change`). Suite `2465 tests, 0 failures` + 51 guardarraíles Konsist con `--rerun-tasks`.
⏳ Queda la repro en device con ubicación denegada.

## Problema — reproducido, no supuesto
Emulador Pixel 8 Pro (Android 17, targetSdk 37), `prodDebug` del 09-09-2026, con la app instalada y
**sin conceder ubicación**. Al abrirla: *«Paparcar keeps stopping»*, en bucle. Del buffer `crash`:

```
FATAL EXCEPTION: main
java.lang.RuntimeException: Unable to start service …CoordinatorDetectionService
  with Intent { act=com.rndeveloper.paparcar.ACTION_RESUME_SENTRY … }
Caused by: java.lang.SecurityException: Starting FGS with type location … targetSDK=37
  requires permissions: all of [FOREGROUND_SERVICE_LOCATION] and any of
  [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION] and the app must be in the eligible
  state/exemptions to access the foreground only permission
  at ForegroundServiceController.promote(ForegroundServiceController.kt:43)
  at CoordinatorDetectionService.onStartCommand(CoordinatorDetectionService.kt:251)
```

Concediendo `ACCESS_FINE/COARSE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` por `pm grant`, la app
arranca a Home sin un solo FATAL. O sea: **el disparador es exactamente la falta del permiso.**

### Confirmado en Crashlytics — no es solo del emulador
Issue `8c03bc6614b4031ddf88d6e44d6302dd`, **abierto, 14 eventos / 9 sesiones**, `1.1.0 (12)`,
`SIGNAL_FRESH`. Dos detalles que la repro local no daba:

- El `blameFrame` es **`ForegroundServiceController.kt:43`**, que es la línea del `else` — el
  `startForeground` de dos argumentos. Es decir: **petó dentro del fallback que se creía seguro**,
  no en la rama tipada. La sospecha de abajo está medida, no deducida.
- Uno de los dos eventos ocurrió con `processState: FOREGROUND`. La app estaba en primer plano y
  el sistema la refusó igual, o sea que ahí la causa era el permiso puro y duro, sin que la
  cláusula de elegibilidad tuviera nada que ver.

## Doctrina violada
Ninguna de detección — es de robustez. Pero el fallo tiene la peor forma posible: la app **muere al
abrirla**, no falla la detección. Un usuario que revoca ubicación desde Ajustes del sistema con un
centinela armado se queda sin app hasta reinstalar o volver a concederlo. Y choca de frente con el
**fallo asimétrico**: ante la duda se PREGUNTA, y aquí ni siquiera llegaba a poder preguntar, porque
el crash ocurría antes de que ninguna Activity se dibujara.

## 🔑 Por qué el guard que YA existe no protege
`CoordinatorDetectionService:249` hace lo correcto en apariencia:

```kotlin
val hasPerms = hasRequiredPermissions()            // fine + background, bien comprobados
try {
    fgs.promote(…, withLocationPermission = hasPerms)
} catch (e: IllegalStateException) { …             // [DET-G-01]
```

y `ForegroundServiceController.promote` cae al `startForeground(id, notification)` de dos argumentos
cuando `withLocationPermission` es false. **Ese fallback no quita el tipo.** El manifest declara:

```xml
<service android:name=".detection.service.CoordinatorDetectionService"
         android:foregroundServiceType="location" … />
```

y `startForeground(id, notification)` sin tipo usa **los tipos declarados en el manifest**. Así que
el servicio arranca igualmente como FGS de tipo `location`, con o sin permiso: el comentario
`[BUG-FGS-001a]` describe una protección que el manifest anula.

Y la segunda mitad del mensaje del OS —*«and the app must be in the eligible state/exemptions»*— dice
que ni siquiera teniendo el permiso está garantizado: desde background, en API reciente, un FGS de
ubicación exige además elegibilidad. **Eso no es comprobable con un `checkSelfPermission`**, luego
ninguna comprobación previa puede cubrir el caso: hay que capturar.

`SecurityException` **no** es `IllegalStateException`, así que el `catch` de al lado no la ve.

## Diseño — decidido

El invariante en UN sitio: **`ForegroundServiceController.promote()` no lanza. Devuelve si el
servicio llegó a foreground**, y cada llamante decide qué hacer con el "no".

```kotlin
enum class ForegroundPromotion { PROMOTED, REFUSED_MISSING_LOCATION_PERMISSION, REFUSED_NOT_FOREGROUND_ELIGIBLE }
```

Por qué ahí y no en cada servicio: la promoción es una llamada IPC comprobada por permisos que el
sistema puede rechazar legítimamente. Que eso llegue como excepción a tres call sites distintos es lo
que garantizaba que alguno se olvidara — y los tres estaban en un estado distinto (uno capturaba la
excepción equivocada, otro no capturaba nada, el tercero sí). Convertido en valor de retorno, el
compilador no obliga a mirarlo, pero el `when`/`if` queda a la vista en el sitio donde importa.

### ⛔ Lo que se ha DESCARTADO, y por qué
La versión anterior de esta ficha proponía *"que el fallback sin permiso arranque de verdad SIN
tipo"* (tipo neutro `shortService`/`dataSync`, o un segundo servicio sin `foregroundServiceType`).
**No se hace, y no es un atajo:**

- **No hay nada que ejecutar.** Todo lo que este servicio hace necesita GPS. Un FGS que arranca sin
  el tipo `location` no puede tomar un fix, así que promocionarlo solo consigue **una notificación
  permanente con cero trabajo detrás** — exactamente el orphan-FGS que `[DET-B-02]` prohíbe.
- **Ya existe el remedio honesto**: `notificationPort.showPermissionRevoked()`, que es lo que
  `guardPermissions()` usa en el intake para los otros cinco carriles. El crash lo saltaba porque
  ocurría en `onStartCommand`, antes de que ningún comando se entregara al intake.
- `dataSync` además **caduca por duración desde Android 15** — es justo el error que
  `DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001` arregló en el worker. No se reintroduce aquí.

Así que un rechazo por permiso = **stand-down limpio**: parar el servicio (obligatorio: un servicio
arrancado con `startForegroundService()` que no llega a foreground muere con
`ForegroundServiceDidNotStartInTimeException` a los ~5 s), estampar la traza y dejar que la
notificación de permiso revocado haga la pregunta.

### Provenance del rechazo — y qué sobrevive de verdad
Un rechazo deja dos rastros, y **no valen lo mismo**:

- **`PaparcarLogger.e` → línea `PARKDIAG`, síncrona.** Se escribe antes de parar el servicio y
  aterriza en `parkdiag.log`, que es el fichero que viaja en el reporte de soporte. **Este es el
  rastro fiable.**
- **`logTrigger(<acción>, REFUSED_PERMISSIONS, detail = "fgs_promote=<outcome>")`, best-effort.**
  Usa el mismo vocabulario que `guardPermissions`, pero `logTrigger` emite dentro de
  `lifecycleScope.launch`, y aquí paramos el servicio en la línea siguiente: `onDestroy` cancela el
  scope y la emisión puede perder la carrera. Se emite igual porque cuando llega es la entrada
  estructurada que el flujo de `field-test` sabe leer, pero **no se puede razonar por su ausencia**.

Deliberadamente **no** se registra un non-fatal en Crashlytics: el rechazo se repite en cada arranque
de proceso mientras el permiso siga sin conceder (9 sesiones en el issue de arriba), y eso sería una
tormenta.

## Consumidores auditados
`grep -rn "\.promote(" shared/src app/src` → 3 call sites, más el carril de WorkManager:

| Call site | Estado ANTES | Ahora |
|---|---|---|
| `CoordinatorDetectionService:251` (`onStartCommand`, todos los triggers) | 🔴 **crasheaba** — solo capturaba `IllegalStateException` | ✅ stand-down + `showPermissionRevoked()` + traza, `START_NOT_STICKY` |
| `BluetoothDetectionService:116` (`handleDisconnected`) | 🔴 **peor**: `withLocationPermission = true` **hardcodeado**, sin comprobar nada ni capturar nada | ✅ comprobación real (`hasLocationPermission()`) + stand-down + `showPermissionRevoked()` |
| `CoordinatorDetectionService:1522` (swap de notificación al entrar en centinela) | 🟢 ya envuelto en `runCatching` | ✅ ahora lee el veredicto y lo registra; un rechazo aquí es inocuo (ya estamos en foreground) |
| `ParkingSafetyNetWorker.getForegroundInfo()` → `SystemForegroundService` | 🟢 **exento**: lo cubre la librería | Verificado en las fuentes de `androidx.work 2.11.2`: `SystemForegroundService.Api31Impl.startForeground` captura `ForegroundServiceStartNotAllowedException` **y** `SecurityException` (líneas 193/198). Por debajo de API 31 no hay validación de tipo. |

⚠️ El carril del worker está cubierto **pero en silencio**: WorkManager solo hace `Logger.warning`,
así que un safety-net que no consigue promocionarse no deja rastro nuestro. Es el riesgo que ya
apuntaba `DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001` ("un rechazo aquí pierde la ÚLTIMA red con nada
en la traza que lo diga"). **No se toca en este ticket** — no crashea, que es el encargo. Queda
apuntado como follow-up.

## Criterio de éxito
- Con la app instalada y ubicación DENEGADA,
  `am startservice -a com.rndeveloper.paparcar.ACTION_RESUME_SENTRY` **no mata el proceso** y la app
  abre a Home (en la práctica, a `PERMISSIONS`, que es donde el gate CORE la lleva).
- El issue `8c03bc6614b4031ddf88d6e44d6302dd` deja de recibir eventos en la siguiente versión.
- ⛔ Criterio ANTERIOR retirado: *"test de que la promoción sin permiso no pide tipo `location`"* —
  se apoyaba en el diseño descartado. Sin permiso **no se promociona en absoluto**.

## Tests
`ForegroundServiceControllerTest` (Robolectric, `@Config(sdk = [34])`, 4 casos, verdes):
`PROMOTED` en el camino feliz; `REFUSED_MISSING_LOCATION_PERMISSION` con el texto **literal** del
rechazo de producción; el mismo veredicto **aun creyendo tener el permiso** (la cláusula de
elegibilidad, que ningún pre-check predice); y `REFUSED_NOT_FOREGROUND_ELIGIBLE` para el
`IllegalStateException` de `[DET-G-01]`.

Ningún shadow de Robolectric lanza estos rechazos — los lanza el sistema desde
`ActiveServices.validateForegroundServiceType`, al otro lado de un IPC. Por eso `promote` recibe la
llamada de plataforma como parámetro con valor por defecto: es la costura mínima para poder
reproducir un rechazo, y usa `FOREGROUND_SERVICE_TYPE_MANIFEST` — el nombre que el propio Android le
da a "los tipos del manifest", que es justo la lección de este ticket.

## Follow-ups (fuera de alcance, apuntar sin hacer)
- Un rechazo de promoción en `ParkingSafetyNetWorker` es invisible para nuestra telemetría (ver
  tabla). Merece su propia ficha si alguna vez se ve un hueco de safety-net sin explicación.
