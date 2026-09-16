# IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001 · en iOS no hay FCM, así que ningún aviso llega

**Estado:** ✅ Done · en master
⏳ Pendiente del user: clave de APNs en la consola, `GoogleService-Info.plist` en el árbol, Push
Notifications en el perfil de aprovisionamiento, desplegar las dos functions, y el primer build del
Swift en un Mac (en Windows el link del framework sale SKIPPED, así que nadie lo ha compilado aún).
**Sale de:** `SHARED-A-PARKING-TELLS-THE-GROUP-001` / `SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001` /
`SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001`, al preguntar el user qué pasa con un miembro en
iOS (16-09).

## Problema

**En `iosMain` no hay mensajería. Ni token, ni recepción.** `grep` limpio: la única pieza de push del
proyecto (`PaparcarMessagingService`, `ZoneAlertSubscriptionSync`, `SharedCarMemberSync`) vive entera
en `androidMain`. Consecuencia, hoy:

| Aviso | Android | iOS |
|---|---|---|
| Plaza en tu zona (`zone_spot`) | push inmediato | **nada** |
| El grupo ha aparcado el coche (`shared_car_parked`) | push + valla movida al instante | **nada** — la valla se mueve en la siguiente pasada de reconciliación |
| Se han llevado el coche (`shared_car_left`) | push + valla levantada | **nada**, ídem |
| Combustible más barato / precio que se mueve | notificación local, ya funciona | igual (no es push) |

Y no es solo que no reciba: **el iPhone tampoco publica token**, así que para el reparto del servidor
ese miembro no tiene dispositivos (`targetsFor` lo descarta) y la Cloud Function nunca intenta
llegarle. El hueco es mudo por los dos lados — ni error, ni log, ni fila.

### Lo que el grep encontró de más, al abrir

Dos huecos que el ticket original no listaba y que hacen que **la tarjeta no se pueda dibujar**
aunque el mensaje llegue:

1. `IosAppNotificationManagerImpl` **no implementa** `showZoneSpotAlert`, `showSharedCarParked` ni
   `showSharedCarLeft`. Las tres tienen cuerpo vacío por defecto en la interfaz → el iPhone se calla
   incluso por el carril que YA funciona: `AppViewModel` llama a `showZoneSpotAlert` desde el stream
   en vivo, con la app abierta, y en iOS eso hoy no pinta nada.
2. Las diez claves `notif_zone_spot_*` / `notif_shared_car_*` **no están espejadas** en
   `composeResources`, que es de donde lee la superficie de iOS. Sin espejo no hay copia que poner.

Y uno en el servidor, que es el que lo haría inútil aunque todo lo anterior estuviera:

3. **Las dos funciones mandan un mensaje de solo datos sin bloque `apns`.** APNs no entrega un
   mensaje de datos a una app que no está en primer plano si no lleva `content-available`, y FCM no
   lo añade solo. Un iPhone con token publicado seguiría sin recibir nada.

## Doctrina violada

Ninguna de detección. La que se rompe es de producto: **Plus = la app te avisa a ti**. Un usuario de
iOS con Plus pagaría por la vigilancia y no la tendría — y el eje de la tabla de precios es
exactamente quién trabaja cuando tú no miras.

## Diseño — el sistema, no el gemelo

⚠️ El encargo decía «gemelos en `iosMain`, pero mira antes qué sube a `commonMain`». Mirado: **sube
casi todo**. Lo único que es de plataforma es *de dónde sale el token* y *quién despierta al
proceso*. Duplicar las dos clases habría dejado dos copias de la regla de suscripción y —lo grave—
**dos copias del orden «primero la valla, después la tarjeta»**, que es justo el invariante que el
commit `d6e890f2` puso por escrito.

### `commonMain` (nuevo)

| Pieza | Qué es | Por qué ahí |
|---|---|---|
| `domain/push/PushPayload.kt` | **Función pura** `pushPayloadFor(data, nowMs)` → veredicto sellado (`ZoneSpot`, `SharedCarParked`, `SharedCarLeft`, `Ignored(reason)`) | El contrato de cable con `functions/` se lee en UN sitio y se testea sin móvil. Predicado compartido → función pura de nivel superior, no caso de uso [DET-VERDICT-NOT-PREDICATE-001] |
| `push/PushMessageHandler.kt` | El reparto: **valla primero, tarjeta después**, dedup de zona, silencio del propio viaje | El orden vive una vez. En iOS es literalmente imposible equivocarse porque no hay segunda copia donde equivocarse |
| `domain/push/SharedCarFenceRefresher.kt` | Puerto: «reconcilia las vallas YA» | Android → `GeofenceJanitorWorker`; iOS → `IosDetectionController`. Lo único distinto es el mecanismo de despertar |
| `domain/push/PushTokenProvider.kt` | Puerto: el token de ESTE dispositivo | Android lo pide a FCM; iOS lo recibe de Swift |
| `push/ZoneAlertSubscriptionSync.kt` · `push/SharedCarMemberSync.kt` | **MOVIDAS** desde `androidMain` sin cambiar una regla | Qué publicar y cuándo ya eran comunes; solo se les cambia `FirebaseMessaging` por el puerto y `System.currentTimeMillis()` por el reloj inyectado |

### `androidMain`

`PaparcarMessagingService` se queda en lo que siempre debió ser: una puerta. Recibe, convierte a
`Map<String,String>`, se lo da al handler común. Cero decisiones dentro. `onNewToken` sigue
resincronizando las dos vigilancias.

### `iosMain`

- `push/IosPushRegistry.kt` — el token que Swift entrega, más el puente
  `PushRegistrationBridge` que Swift implementa. **Mismo patrón que `CrashReporterBridge`**, y por la
  misma razón que `iOSApp.swift` ya documenta para `BGTaskScheduler`: lo que solo existe en un
  delegate de UIKit se pide desde Swift, no se adivina desde Kotlin/Native.
  ⛔ Conserva la doctrina de `ZoneAlertSubscriptionSync`: **el token no se pide si no hay nada que
  suscribir**. En iOS «pedirlo» es registrarse en APNs, así que el registro se dispara la primera vez
  que alguien llama a `currentToken()` y no en el arranque.
- `push/IosSharedCarFenceRefresher.kt` — `IosDetectionController.reconcileFencesNow(source)`, una
  entrada nueva que mete `Command.Reconcile` en el intake de siempre. ⛔ No registra vallas por su
  cuenta: el reconciliador sigue siendo el único sitio que lo hace.
- `IosAppNotificationManagerImpl` — las tres tarjetas que faltaban, con las claves espejadas y el
  mismo gating que Android (`notifyZoneSpots` para la de zona; las del coche compartido no se gatean,
  igual que en Android).
- `PaparcarIosBootstrap` — arranca las dos vigilancias y expone dos puertas nuevas a Swift:
  `onPushToken(token)` y `onRemoteMessage(userInfo, completion)`.

### `iosApp` (Swift)

`UNUserNotificationCenter` + `Messaging.delegate` + `didReceiveRemoteNotification`, la capability de
Push Notifications (`iosApp.entitlements` con `aps-environment`) y el background mode
`remote-notification`. `FirebaseMessaging` **ya estaba enlazado** en el target por SPM.

### `functions`

Bloque `apns` en los dos envíos: `apns-push-type: background`, `apns-priority: 5`,
`content-available: true`. Es la única forma de que un mensaje **de datos** llegue a una app iOS que
no está delante — y de datos tiene que seguir siendo, porque la copia en nueve idiomas y el dedup de
`zone_alerted_spots` viven en el cliente.

## Lo que este ticket NO puede dar, y no es un olvido

⛔ **El carril Bluetooth de iOS no entra, porque no existe y no puede existir.** Apple no expone el
Bluetooth clásico (HFP/A2DP) —el que usan los coches— a apps de terceros;
`IosBluetoothScanner.getBondedDevices()` devuelve lista vacía por diseño y lo documenta en su
cabecera. Un iPhone cae SIEMPRE en Coordinator, y por eso un miembro en iOS nunca podrá anunciar que
se lleva el coche por la vía de `SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001`. Eso no es un hueco
que tapar aquí: es una capacidad que la plataforma no da. Lo que sí le llega con este ticket es
enterarse de lo que hacen los demás.

⛔ **Un push de fondo en iOS es best-effort, y a prioridad 5.** Apple presupuesta los
`content-available` por app y por dispositivo, los retrasa cuando el móvil está en bajo consumo, y
**no entrega ninguno si el usuario ha forzado el cierre de la app**. Android recibe a prioridad alta
y despierta de Doze; iOS no tiene equivalente para un mensaje silencioso. La alternativa —un push de
alerta que APNs pinta solo— rompería las dos cosas por las que el mensaje es de datos: los nueve
idiomas y el dedup. Follow-up escrito: `ios-a-silent-push-can-arrive-late-or-never-001.md`.

⛔ **Tocar la tarjeta no centra el mapa.** iOS no tiene hoy el equivalente del `buildFocusIntent` de
Android — `showParkingSaved` ya vive con esa carencia desde que existe. No se introduce aquí ni se
tapa aquí. Follow-up: `ios-a-notification-tap-cannot-focus-the-map-001.md`.

## Acciones del USER que no puedo hacer desde aquí

1. **Clave de APNs en la consola de Firebase** (Project Settings → Cloud Messaging → APNs
   Authentication Key, `.p8` + Key ID + Team ID). Sin ella, FCM no puede entregar a ningún iPhone,
   aunque el token esté publicado.
2. **`iosApp/iosApp/GoogleService-Info.plist`** — está gitignored (como `google-services.json`) y no
   puede versionarse: el repo es público. Descargar de la consola y dejarlo en el worktree.
3. **Push Notifications en el perfil de aprovisionamiento** del App ID `com.rndeveloper.paparcar`.
4. **Desplegar las dos functions** con el bloque `apns` nuevo.

## Criterio de éxito — medido

- ✅ **`./gradlew testAll` verde**, 3.231 tests. Nuevos: `PushPayloadTest` (8 casos del contrato de
  cable), `PushMessageHandlerTest` (11) y `ZoneAlertSubscriptionSyncTest` (5).
  Lo que prueban y no se podía probar antes:
  - **la valla se mueve ANTES que la tarjeta**, en las dos noticias del coche compartido. Los dos
    lados escriben en el MISMO diario, porque dos contadores separados no distinguen «primero» de
    «también»;
  - un `shared_car_parked` **sin posición mueve la valla y no pinta tarjeta**;
  - el aviso de un viaje que era el MÍO calla la tarjeta y **no** la valla;
  - una plaza ya anunciada no se repite, y una caducada antes de llegar no se muestra;
  - el token **no se pide** sin Plus, con el interruptor cerrado, o sin zonas suscritas.
- ✅ `:shared:compileKotlinIosArm64` y `:shared:compileKotlinIosSimulatorArm64` compilan.
  ⚠️ `linkDebugFramework*` sale **SKIPPED en Windows**: enlazar un framework de Apple exige macOS, y
  con él se genera la cabecera de Objective-C que Swift consume. **El código Swift de `iosApp/` no
  está compilado por nadie todavía** — queda para el Mac, junto con la firma de la nueva capability.
- ✅ `functions`: `npm test` (tsc + 25 tests) verde con el bloque `apns`.
- ⏳ Campo (user): dos cuentas, un coche compartido, un iPhone y un Android. El iPhone recibe las
  tres noticias y su valla se mueve con la del Android.

## Encontrado de paso, fuera de alcance

`composeResources` tiene **247 cadenas con `\'`**, que Compose Resources no desescapa: se leen con la
barra, **y no solo en iOS** — es el árbol del que lee toda la UI de producto en las dos plataformas.
FR (107) e IT (64) se llevan 171. Ticket propio, con el barrido y el guard que falta:
`copy-247-strings-still-print-a-backslash-001.md`.

## Consumidores auditados

| Sitio que asumía «esto es de Android» | Estado |
|---|---|
| `AndroidPlatformModule` — `ZoneAlertSubscriptionSync` / `SharedCarMemberSync` con comentario «Android only, because FCM…» | ✅ reescrito: el comentario era cierto cuando se escribió y ya no lo es |
| `PaparcarApp.kt` — arranca las dos | ✅ sigue igual; ahora `PaparcarIosBootstrap` hace lo propio |
| `PaparcarMessagingService` — todo el reparto dentro | ✅ vaciado a puerta |
| `IosAppNotificationManagerImpl` — tres surfaces sin override | ✅ implementadas |
| `NotificationCopyParityTest` — testigo de población en 20 claves | ✅ subido a 30 con las diez nuevas |
| `functions/src/index.ts` · `sharedCarFanout.ts` — envíos sin `apns` | ✅ bloque añadido en los dos |
| `IosDetectionController` — sin puerta pública para reconciliar vallas | ✅ `reconcileFencesNow(source)` |
| `MockModule` / `MockKoinModuleVerifyTest` | ✅ sin cambios: lo nuevo se registra en los módulos de plataforma, que el grafo mock no incluye |
| `IosBluetoothScanner` — el carril BT | ⛔ exento por plataforma, documentado arriba |
