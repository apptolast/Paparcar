# DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001 · Sin permiso de ubicación, reanudar el centinela MATA la app

**Estado:** 🔴 Abierto, sin rama · hallazgo lateral de
`UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001` (⛔ no se arregla ahí: es detección → skill
`det-change`).

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

## Doctrina violada
Ninguna de detección — es de robustez. Pero el fallo tiene la peor forma posible: la app **muere al
abrirla**, no falla la detección. Un usuario que revoca ubicación desde Ajustes del sistema con un
centinela armado se queda sin app hasta reinstalar o volver a concederlo.

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

## Diseño (a decidir en su ticket)
Dos piezas, y la primera sola no basta:

1. **Capturar `SecurityException` junto a `IllegalStateException`** en el mismo `try` — es la única
   red posible para la parte de "elegibilidad", que no se puede predecir.
2. **Que el fallback sin permiso arranque de verdad SIN tipo.** Opciones a valorar: declarar el
   servicio con varios tipos y pasar el que toque, o un segundo servicio sin
   `foregroundServiceType`, o `startForeground` con un tipo neutro (`shortService`/`dataSync`)
   cuando no hay ubicación. Hay que mirar qué hace el carril BT, que declara lo suyo aparte.

⚠️ Y decidir **qué pasa con el centinela** cuando esto ocurre: morir en silencio no vale, la sesión
queda armada sin vigilante. Probablemente toca degradarlo y estampar la causa, como
`ProcessDeathAttributor` con las muertes.

## Criterio de éxito
Con la app instalada y ubicación DENEGADA, `am startservice -a com.rndeveloper.paparcar.ACTION_RESUME_SENTRY`
no mata el proceso, y la app abre a Home. Test de que la promoción sin permiso no pide tipo
`location`.

## Consumidores auditados
Pendiente: mirar los otros servicios con `foregroundServiceType` en el manifest
(`BluetoothDetectionService` y los que haya) — el mismo fallback de dos argumentos vive en el mismo
`ForegroundServiceController`, así que si alguno más declara un tipo restringido tiene el agujero.
