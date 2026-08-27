# BUILD-TARGETSDK-AT-ANDROID-17-001 · `targetSdk` 36 → 37

**Estado:** ✅ Done · mergeado en master

## Problema

`compileSdk` lleva tiempo en 37 pero `targetSdk` seguía en 36. Compilar contra Android 17 y
declararse como app de Android 16 significa que el sistema aplica el modo de compatibilidad: la app
no ve los cambios de conducta hasta que alguien suba el número, y ese "alguien" acaba siendo el día
en que Play lo exige, con la app ya publicada y con usuarios.

Subirlo **antes** de la prueba interna es lo barato: si algo se rompe, se rompe delante de 4 testers
y no de una base instalada.

## Doctrina violada

Ninguna. Es higiene de plataforma. Pero sí aplica una regla de este trabajo: **esto no es un bump de
dependencias**. Cambiar `targetSdk` no cambia lo que compila, cambia **cómo se comporta la app en
runtime**, y por eso va en su propio commit en vez de esconderse dentro de
`BUILD-DEPS-AT-LATEST-STABLE-001`, donde nadie lo habría visto al revisar el histórico.

## Señales / datos disponibles

Android 17 (API 37) es **SDK final, no preview** — `android-37.0/source.properties` trae
`AndroidVersion.CodeName=` vacío y `PreviewSdkInt=0`.

### Auditoría: cada cambio de conducta de API 37 contra este código

| cambio para apps que apuntan a 37 | ¿nos afecta? |
|---|---|
| `ACCESS_LOCAL_NETWORK` para hablar con dispositivos de la LAN | ❌ `grep` de `NsdManager`/`MulticastLock`/`DatagramSocket`/IPs privadas → **0 hits**. Overpass, Firestore y Maps son internet, no LAN |
| `BluetoothSocket.read()` de RFCOMM ahora devuelve `-1` | ❌ `grep` de `BluetoothSocket`/`createRfcommSocket` → **0 hits**. Nuestra vía BT son broadcasts ACL y estado A2DP, no sockets |
| BAL hardening (`MODE_BACKGROUND_ACTIVITY_START_ALLOWED`) | ❌ `grep` de `ActivityOptions`/`MODE_BACKGROUND_ACTIVITY_START` → **0 hits** |
| Campos `static final` inmodificables por reflexión | ❌ `grep` de `getDeclaredField`/`setAccessible` → **0 hits** en `composeApp/src` |
| Se ignoran orientación/redimensionado en pantallas ≥600dp | ❌ el manifest no declara `screenOrientation` ni `resizeableActivity` |
| Certificate Transparency y ECH activados por defecto | ⚠️ afecta a TLS con `overpass-api.de` y Firebase. Ambos usan CAs públicas con CT; sin cambios en código |
| Hardening de audio en background | ❌ la app no reproduce audio |
| OTP por SMS · PII en CP2 · DCL nativo · límite de keystore | ❌ n/a |
| Política de Play del "botón de ubicación" | ❌ aplica solo a apps cuyo **único** uso de ubicación es por sesión. Paparcar usa ubicación en background continua |

### Lo que cambia en Android 17 **independientemente** del `targetSdk`

No son motivo para frenar el bump — ya nos aplican hoy en cualquier móvil con Android 17 — pero sí
tocan la doctrina de detección y merecen ticket propio:

1. **Re-emparejamiento Bluetooth autónomo.** El sistema restablece solo los bonds perdidos en
   background, y `ACTION_KEY_MISSING` **solo se emite si ese intento falla**. `ACTION_PAIRING_REQUEST`
   gana `EXTRA_PAIRING_CONTEXT` para distinguir un emparejamiento normal de uno autónomo.
   `BluetoothDetectionStrategy` está atada a la MAC emparejada: conviene revisar qué asume sobre
   pérdida de bond.
2. **Límites de RAM por dispositivo.** La app puede morir por exceso de memoria, y
   `ApplicationExitInfo.getDescription()` lo dice con `REASON_OTHER` + `"MemoryLimiter:AnonSwap"`.
   Esto es **una oportunidad**, no solo un riesgo: llevamos meses cazando muertes por OEM y aquí hay
   por fin una causa atribuible por API.

## Diseño

Un número en `libs.versions.toml` y la línea de stack de `CLAUDE.md`. El trabajo es la auditoría de
arriba y la verificación de abajo.

## Criterio de éxito — resultado

| gate | resultado |
|---|---|
| `testProdDebugUnitTest` | ✅ **1.671 tests, 0 fallos** |
| `assembleProdDebug` + `assembleProdRelease` (R8) + `assembleMockDebug` | ✅ los tres |
| warnings | ✅ 0, con `-Werror` activo |
| instalación en Android 17 real (emulador API 37) | ✅ hash verificado en device; `dumpsys` confirma `targetSdk=37` |
| arranque | ✅ sin crash, sin `SecurityException` propia |
| **FGS de detección arrancado desde background** | ✅ ver abajo |

La línea que importa del logcat, con la app ya declarada como API 37:

```
ActivityManager: Background started FGS: Allowed [callingPackage: io.apptolast.paparcar;
  intent: ACTION_RESUME_SENTRY cmp=.../detection.service.CoordinatorDetectionService;
  allowWiu:12; targetSdkVersion:37; callerTargetSdkVersion:37]
```

El servicio de detección arranca desde un intent de background y el sistema **le concede
capacidades while-in-use** (`allowWiu:12`), que es de lo que depende que pueda leer ubicación.

## ⚠️ Lo que esta verificación NO prueba

Honestidad sobre el alcance, porque la tentación es leer ese `Allowed` como un aprobado general:

- En esa traza el proceso estaba en `uidState: TOP` y con
  `tempAllowListReason: MY_PACKAGE_REPLACED` — o sea, un arranque **privilegiado** justo tras
  instalar. **No demuestra** que un arranque en frío disparado por una geocerca con el móvil en Doze
  reciba WIU.
- La app se queda en la pantalla de Google sin credenciales, así que **no se ejerció un ciclo real de
  detección**: ni geocerca, ni AR ENTER, ni desconexión BT, ni el `ParkingSafetyNetWorker`.
- **Ningún móvil nuestro llega a Android 17** (Redmi = Android 13/API 33; Oppo tampoco). Así que
  esto **no se validará en campo**: lo verán primero los testers internos con móviles nuevos.

Traducido: el bump es seguro para compilar y publicar, y el único camino que de verdad queda por
medir es el que siempre ha sido difícil aquí — el arranque en frío desde Doze.

## Consumidores auditados

Cubierto arriba, en la tabla de cambios de conducta: 5 `grep` con 0 hits, 4 categorías n/a, 1 sin
cambios de código (TLS).

## Follow-ups

- `DET-BT-AUTONOMOUS-REPAIRING-ANDROID-17-001` — revisar qué asume la vía BT sobre pérdida de bond.
- `DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001` — leer `ApplicationExitInfo` y distinguir por fin
  una muerte por memoria de una muerte por OEM.
