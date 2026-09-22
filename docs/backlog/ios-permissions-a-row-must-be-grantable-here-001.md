# IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001 · la pantalla de permisos enseña las filas de Android en iOS

**Estado:** ✅ Done · rama `feature/IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001` ·
worktree `../Paparcar-ios-permissions` · issue **#76**

## Problema

La pantalla de permisos es una sola (`PermissionsContent`, commonMain) y está **modelada sobre el
catálogo de Android**. En iOS eso produce filas que se pintan, se pulsan y no pasa nada, y filas
que no pueden ponerse verdes nunca.

Siete defectos. Los seis primeros, leídos en el código de `master` (`b1c960693`); el séptimo
apareció al arreglar los otros:

### D1 · Nueve de las once acciones de la pantalla no hacen nada en iOS
`PermissionsScreen.ios.kt:60-67` pasa **4 de los 15** callbacks de `PermissionsContent`. Los once
restantes se quedan con su default `= {}`. Y `PermissionRow` hace la fila pulsable cuando
`onGrant != null && !granted` — `{}` **no es null**, así que la fila se dibuja pulsable, responde
al tap con su ripple, y no ocurre nada. Ubicación, servicios de ubicación, segundo plano,
actividad, notificaciones y batería: seis filas muertas que *parecen* vivas.

### D2 · La exención de batería se pinta en iOS, y bloquea el check del tier
`PermissionsContent:309` dibuja la fila de batería **sin consultar `isBatteryOptimizationRelevant`**
— que ya vale `false` en iOS (`Platform.ios.kt:19`) y que el pie de la pantalla (`:401`) y Ajustes
(`SettingsScreen:753`) **sí** consultan. Peor: `satisfied` del tier opcional (`:294`) exige
`isBatteryOptimizationExempt`, que en iOS no puede ser `true` jamás ⇒ **el tier opcional no puede
ponerse verde en iOS bajo ninguna circunstancia.**

El autostart y el freeze de ColorOS ya se excluyen solos vía
`OemBackgroundReliabilityManager.requiresAutostartWhitelist = false`. O sea: el mecanismo de
exclusión existía y a la batería se le olvidó aplicarlo.

### D3 · El permiso de Bluetooth no se lee en iOS
`IosPermissionRequester.requestBluetooth()` sí levanta el diálogo (instancia un `CBCentralManager`),
pero `IosPermissionManagerImpl` **no toca CoreBluetooth**: `hasBluetoothConnectPermission` se queda
en `false` para siempre. La fila se queda en ámbar «opcional, sin conceder» aunque el usuario acabe
de concederlo. El ticket pide «poner también permiso de BT»: está puesto en la UI y es
**inobservable**.

### D4 · En iOS la ubicación no es un booleano, y el mapeo la trata como si lo fuera
Tres cosas que Android no tiene y que el manager de iOS no lee:

- **Precisión.** Desde iOS 14 se puede conceder ubicación con *Ubicación precisa* apagada
  (`CLAccuracyAuthorization.reducedAccuracy`, del orden de kilómetros). El manager reporta
  `hasLocationPermission = true` y toda la app se cree con ubicación fina. Para Paparcar, cuya
  unidad de trabajo es *en qué plaza exacta has dejado el coche*, es **la mentira más cara de la
  pantalla**: la detección no puede funcionar y nada lo dice. En Android el equivalente no existe
  porque `PermissionManagerImpl:36` comprueba `ACCESS_FINE_LOCATION`, no `COARSE`.
- **Denegado ≠ sin preguntar.** `kCLAuthorizationStatusDenied`/`Restricted` significan que el
  diálogo **no volverá a salir**; iOS lo dice exacto, sin el `shouldShowRequestPermissionRationale`
  + flag «¿ya hemos preguntado?» que Android obliga a inventar. `locationPermanentlyDenied` nunca se
  pone a `true` en iOS ⇒ el CTA sigue diciendo «Conceder» y no lleva a ningún sitio.
- **La escalada a «Siempre» se gasta una vez.** `requestAlwaysAuthorization()` solo presenta diálogo
  desde `WhenInUse` y **solo la primera vez**; después es un no-op silencioso y el único camino son
  los Ajustes. Y la guía que se enseña antes (`permissions_bg_guide_body`) narra la pantalla de
  Android — «Pulsa «Ubicación» → «Permitir todo el tiempo» → Atrás» — que en iOS no existe.

### D5 · Movimiento: el mismo problema, más una fila que no debería existir
`CMAuthorizationStatusDenied`/`Restricted` también significan «el diálogo no vuelve». Y
`CMMotionActivityManager.isActivityAvailable()` es `false` en hardware sin coprocesador de
movimiento: ahí la fila no es un permiso pendiente, es **una fila que no se puede conceder**. Hoy
`requestActivityRecognition()` hace `return` silencioso (`IosPermissionRequester:88`) y
`hasProducerPermissions` exige actividad ⇒ la detección quedaría «no lista» para siempre sin nada
que el usuario pueda hacer al respecto.

### D6 · «Servicios de ubicación» promete un sitio al que no puede llevar
El efecto `OpenLocationSettings` abre `UIApplicationOpenSettingsURLString`, que lleva a los ajustes
**de la app**, no al interruptor global de Localización. iOS no permite enlazar ahí (`App-Prefs:` es
API privada y Apple rechaza la app). La fila es legítima —el interruptor global existe y se puede
apagar— pero su texto tiene que decir el camino con palabras en lugar de prometer un salto.

### D7 · El primer arranque de iOS levantaba TRES diálogos a la vez
`requestStep1()` pedía ubicación **y** notificaciones **y** movimiento en la misma llamada, aunque
el efecto que lo dispara (`RequestStep1`) es el paso CORE, que en Android es solo la ubicación. Eso
es el muro de permisos que el proyecto retiró en
[ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]: gastaba dos diálogos del sistema —que se presentan
una sola vez— antes de que el usuario hubiera visto un mapa, y dejaba sin nada que pedir a las
preguntas contextuales que vienen después. Apareció al cablear el resto: la misma llamada la usa el
atajo del mapa (`LocationPermissionActions.ios.kt`), que solo quiere la ubicación.


## Doctrina violada

- **Sistemas, no parches.** El invariante que falta tiene nombre: *una fila existe si esta
  plataforma puede CONCEDERLA y LEERLA, y su acción lleva donde de verdad se concede.* Hoy ese
  invariante vive a trozos: `isBatteryOptimizationRelevant` lo aplica el pie pero no la lista,
  `requiresAutostartWhitelist` lo aplica la lista pero no existe para el resto, y de la precisión
  y de los diálogos gastados no se ocupa nadie.
- **Copy sin mecánica interna: causa + consecuencia + remedio.** Una fila muerta no tiene ninguna
  de las tres.
- **Un caso de uso por VEREDICTO.** «¿Este diálogo sigue vivo?» es un veredicto que cambia lo que
  ve y toca el usuario, y hoy solo está modelado para la ubicación, en Android, vía un intent que
  empuja la pantalla de Android.

## Señales / datos disponibles

Todo lo que hace falta ya lo expone el sistema en iOS, síncrono y exacto:
`CLLocationManager.authorizationStatus` · `.accuracyAuthorization` ·
`CLLocationManager.locationServicesEnabled()` · `CMMotionActivityManager.isActivityAvailable()` y
`.authorizationStatus()` · `CBCentralManager.authorization` ·
`UNUserNotificationCenter.getNotificationSettingsWithCompletionHandler` (asíncrono, ya cableado).

Lo único que iOS **no** expone es si la escalada a «Siempre» ya se presentó: se persiste un flag
propio al pedirla.

## Diseño

### El invariante, en un sitio
Nace `domain/permissions/SystemPermissionDialog.kt`: los diálogos que la app puede levantar
(`LOCATION`, `ALWAYS_LOCATION`, `ACTIVITY`, `NOTIFICATIONS`, `BLUETOOTH`) y, en
`AppPermissionState.spentDialogs`, **cuáles ya no van a salir**. El veredicto es del diálogo, no del
permiso, que es justo la distinción que faltaba: un permiso denegado se puede seguir concediendo —
en los Ajustes—, lo que se ha gastado es la vía del diálogo.

Toda fila cuya acción sea levantar un diálogo consulta ese conjunto; si está gastado, la acción es
`OpenAppSettings`. Android sigue alimentando la ubicación por su intent (necesita la Activity para
`shouldShowRequestPermissionRationale`); iOS rellena el conjunto entero desde los estados. Un solo
concepto, dos fuentes.

### Lo que esta plataforma no tiene, no se pinta
`AppPermissionState` gana `isActivityRecognitionAvailable` (el hardware) y la fila de batería pasa a
consultar el `isBatteryOptimizationRelevant` que ya existía. El `satisfied` de cada tier y el
`requiredComplete` del pie se calculan **sobre las filas que se enseñan**, no sobre la lista de
Android.

### La precisión es una fila, y solo cuando duele
`AppPermissionState.hasPreciseLocation` (en Android = `hasLocationPermission`, que ya se lee de
`ACCESS_FINE_LOCATION`). Se pinta una fila propia **solo cuando hay ubicación concedida pero
aproximada** — el único estado en que el usuario cree estar servido y no lo está. Y entra en
`hasProducerPermissions`: sin ubicación precisa no hay detección que prometer.

### El copy que difiere, elegido por la plataforma, no por un `if`
`expect val platformPermissionCopy: PlatformPermissionCopy` con las tres claves que cambian (guía de
segundo plano, descripción de servicios de ubicación, remedio de la precisión). Dos actuals. Ningún
`isIos` suelto por la UI.

## Criterio de éxito

- Ninguna fila pulsable de la pantalla en iOS deja de hacer algo al pulsarla (el barrido de los 15
  callbacks queda cerrado en la tabla de abajo).
- El tier opcional puede ponerse verde en iOS.
- Conceder BT en iOS pone su fila en verde.
- Con la ubicación en «aproximada», la pantalla lo dice y ofrece el remedio.
- Con un diálogo gastado, el CTA lleva a Ajustes en el primer frame, no tras dos taps a la nada.
- `./gradlew testAll` verde (incluye `:shared:compileKotlinIosSimulatorArm64`).

## Consumidores auditados

### Las 15 acciones de `PermissionsContent` (el defecto D1)

| Acción | Android | iOS | Estado |
|---|---|---|---|
| `onRequestPermissions` | ✅ | ✅ | ya estaba |
| `onRequestForegroundLocation` | ✅ | ✅ **nuevo** | cerrado |
| `onOpenLocationServices` | ✅ | ✅ **nuevo** | cerrado |
| `onRequestBackgroundLocation` | ✅ | ✅ **nuevo** | cerrado |
| `onRequestPreciseLocation` | ✅ **nuevo** | ✅ **nuevo** | nace en este ticket |
| `onRequestActivityRecognition` | ✅ | ✅ **nuevo** | cerrado |
| `onRequestNotifications` | ✅ | ✅ **nuevo** | cerrado |
| `onRequestBluetooth` | ✅ | ✅ | ya estaba |
| `onRequestBatteryOptimization` | ✅ | ⛔ ausente | exento: la fila no se pinta (`isBatteryOptimizationRelevant`) |
| `onRequestOemAutostart` | ✅ | ⛔ ausente | exento: la fila no se pinta (`requiresAutostartWhitelist`) |
| `onRequestOemBatterySettings` | ✅ | ⛔ ausente | exento: la fila no se pinta (`requiresOemBatterySettings`) |
| `onConfirmBackgroundLocationGuide` | ✅ | ✅ **nuevo** | cerrado |
| `onDismissBackgroundLocationGuide` | ✅ | ✅ **nuevo** | cerrado |
| `onNotNow` | ✅ | ✅ | ya estaba |
| `onFinish` | ✅ | ✅ | ya estaba |

⛔ Las tres exenciones son **exenciones completas**: la acción no se cablea Y su fila no se pinta.
Cablear un `Unit` fingiría que el permiso existe; dejarla fuera sin quitar la fila es el defecto
original. Lo vigila `PermissionsScreenParityGuardrailTest`, que falla en los dos sentidos —sin
cablear y no declarado, o declarado y cableado—. Falsificado quitando `onRequestNotifications` de
iOS (rojo) y rompiendo su propio selector (rojo por población vacía).

### `RequiredPermission` gana `PRECISE_LOCATION`

| Consumidor | Estado |
|---|---|
| `SettingsScreen.firstMissingLabel` (`when` exhaustivo) | **cerrado** — rama nueva |
| `SettingsScreen.HEALTH_PRIORITY` | **cerrado** — entra 2.º, justo tras la ubicación |
| `ObserveDetectionReadinessUseCase` | cubierto por convergencia (lee `missingPermissions()`) |
| `PublishDetectionStateUseCase` (publica `.name` a Firestore) | cubierto — aditivo; el nombre nuevo es citable en diagnóstico, que es justo para lo que existe el enum |
| `AppViewModel`, `StatsViewModel`, `VehiclesViewModel`, `PermissionsFocus.detectionPermissionGap` | cubiertos por convergencia (`missingProducerPermissions()` / `missingPermissions()`) |
| `DetectionPause`, `DetectionUiState` | exentos — operan sobre el `Set` sin nombrar valores |

### Quién construye `AppPermissionState`

Los tres campos nuevos traen el **default honesto**, y por eso ninguna fixture existente cambia de
significado: `hasReducedLocationAccuracy = false` (se nombra por lo que va mal),
`isActivityRecognitionAvailable = true`, `spentDialogs = emptySet()`.

| Constructor | Estado |
|---|---|
| `PermissionManagerImpl` (androidMain) | exento — allí conceder ubicación ES `ACCESS_FINE_LOCATION`, y no hay nada que leer distinto |
| `IosPermissionManagerImpl` | **cerrado** — rellena los tres |
| `FakePermissionManager`, `FakeOtherDataSources` y las 8 fixtures de test | cubiertos por el default |

⚠️ Si `hasReducedLocationAccuracy` se hubiera llamado `hasPreciseLocationPermission` (default
`false`), **todas** esas fixtures habrían pasado a «detección no lista» sin que nadie tocara nada.
Nombrar el campo por el defecto honesto es lo que evitó el barrido.

## Sin cerrar, a propósito

- ⚠️ **`CLLocationManager.locationServicesEnabled()` se lee en el hilo principal.** Apple avisa de
  que puede bloquear. Ya era así antes de este ticket y sacarlo de ahí cambia la forma del refresco
  entero (lecturas síncronas + un callback ya asíncrono), así que no entra aquí: queda anotado como
  follow-up y no se ha medido su coste real, que solo se puede medir en un dispositivo.
- ⏳ **Nada de esto se ha visto en un iPhone.** Desde Windows compila el klib, no se enlaza el
  framework ni se arranca un simulador. Lo que este ticket puede demostrar es lo que compila y lo
  que los tests afirman; el comportamiento de los diálogos del sistema exige un dispositivo.
