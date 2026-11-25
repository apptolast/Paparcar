# MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001 · El Install Referrer se lee antes de login y el gate se quema sin publicar nada

**Estado:** ✅ Done · rama `bugfix/MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001-install-attribution`

## Problema

Ningún documento de `users/{uid}` en producción (`pap-26`, Firestore `(default)`) ha recibido nunca
`installChannel`/`installSource`/`installMedium`/`installCampaign`/`installAttributionResolvedAt` —
verificado el 25-11-2026 con un `grep` de toda la colección (`installChannel != "__none__"` → 0
documentos) y con lectura directa de usuarios creados HOY en v1.8.0 (`users/WmbkBKNaNFZqOj8tSopAOfUmk6O2`,
`users/h6zkuco5IYUTQwPF1hNAZsIX6KJ3`, `users/472Abf0sm8bIsSlSutyQC5ueXa23`,
`users/orOzuXRrxiPtgF88t8paVoxhyQg2`): ninguno tiene el campo.

Esto rompió el conteo que el user pidió (instalaciones TikTok-paid vs. Meta vs. orgánico desde hoy
07:00) — el resultado real no es "sin tráfico todavía", es que la escritura a Firestore nunca ha
ocurrido para NINGÚN canal, ni Meta ni TikTok.

**Causa**: `AppViewModel` se crea en la raíz de `App()` (`App.kt:220`), **por encima** del NavHost
de Splash→Auth→...→Home. Su `init` dispara `resolveInstallAttribution.resolve()`
(`AppViewModel.kt:276-281`) en ese mismo instante — es decir, antes de que exista sesión.

En `ResolveInstallAttributionUseCase.kt:41-87`:
```kotlin
val userId = authRepository.getCurrentSession()?.userId
if (userId != null) {
    userProfileRepository.publishInstallAttribution(...)   // se salta: userId es null
}
...
appPreferences.setInstallAttributionResolved()   // ⬅ se marca SIEMPRE, con o sin userId
```

El Google Play Install Referrer solo devuelve datos útiles la **primera vez** que la app se conecta
al servicio tras instalarse. Esa única lectura ocurre con `userId == null`, así que
`publishInstallAttribution` nunca se llama — pero el gate se cierra igual, quemando para siempre la
única oportunidad de escribir el canal a Firestore.

## Doctrina violada

Ninguna doctrina de detección; es un bug de orden de ejecución en un flujo "fire-and-forget" que
asumió (sin comprobarlo con un dispositivo real) que habría sesión disponible en su única ejecución.
El propio comentario del use case dice bien la restricción ("solo se puede leer una vez") pero el
código no la protege: separa el gate de LECTURA del gate de PUBLICACIÓN, y solo protegió el primero.

## Señales / datos disponibles

- Firestore (`pap-26`, prod): 0 documentos con `installChannel` en toda la colección `users`, pese a
  ≥4 altas de hoy en v1.8.0.
- GA4 → BigQuery (`pap-26.analytics_524957242`): sin tablas intraday; última tabla final
  `events_20261124`. Las tablas 20261120–20261124 tienen 0 eventos `install_attribution_resolved`
  (esperable: v1.8.0 solo llegó a usuarios reales a partir de hoy 25-11). La tabla de hoy no estará
  hasta mañana — pendiente de comprobar si GA4 SÍ recibe el evento (no depende de `userId`, así que
  en principio no debería estar afectado por este bug, pero no está confirmado con datos reales).
- `InstallChannel`, `InstallReferrerRaw`, `MetaInstallIds`, `InstallAttribution`:
  `domain/attribution/InstallAttribution.kt` — vocabulario cerrado, sin cambios necesarios.
- Campos Firestore ya definidos y correctos: `RemoteUserProfileDataSourceImpl.kt:464-473`
  (`FIELD_INSTALL_CHANNEL` etc.) — el problema no es el mapeo, es que nunca se llega a invocar con
  un `userId` no nulo.

## Diseño

El SISTEMA es: la lectura de Play (una vez, sin red salvo la del propio Play) y la publicación a
Firestore (necesita sesión, puede repetirse) son dos operaciones con ciclos de vida distintos y
hoy comparten un solo gate. Separarlos:

1. **Gate de lectura** (`hasResolvedInstallAttribution` en `AppPreferences`, como ahora): se marca
   en cuanto Play contesta con éxito (dato útil o referrer vacío/ilegible) — nunca antes de tener
   una respuesta válida de Play. Esto no cambia.
2. **Resultado clasificado, persistido localmente** (nuevo campo en `AppPreferences`, p. ej.
   `pendingInstallAttribution: InstallAttribution?` serializado, o los campos sueltos que ya
   produce `classifyInstallChannel`): se escribe en el momento en que Play contesta, ANTES de
   comprobar si hay `userId`.
3. **Publicación a Firestore**: un paso nuevo, separado, que se reintenta (en `resolve()` o en el
   mismo sitio donde hoy se observa `signedInUid()` en `AppViewModel`) mientras exista un resultado
   pendiente sin publicar y haya `userId`. Al publicar con éxito, se limpia el pendiente.

Así la lectura de Play sigue ocurriendo una sola vez (nunca se pierde si tarda el login), y la
publicación a Firestore puede esperar todo lo que haga falta a que exista sesión, sin reintentar
jamás la lectura de Play.

**Alternativa descartada**: retrasar `resolve()` hasta que haya sesión. Se descarta porque arriesga
perder el referrer si el usuario demora el login (Play puede dejar de contestar con el dato útil
tras la ventana inicial) — la lectura en sí no puede esperar, solo la publicación.

## Validación del arreglo original

- ✅ Test unitario nuevo en `ResolveInstallAttributionUseCaseTest`
  (`should_publishThePendingResult_when_aSessionAppearsOnALaterCall`,
  `should_keepThePending_when_firestorePublishFails`,
  `should_doNothing_when_flushingWithNothingPending`): con Play devolviendo un referrer válido y
  `userId == null` en el momento de `resolve()`, y una sesión que llega DESPUÉS, el canal
  clasificado termina publicado en Firestore sin volver a leer Play (`readCallCount` se queda en 1).
- ✅ Test de integración nuevo en `AppViewModelTest`
  (`should_flushPendingInstallAttribution_when_aSessionAppearsAfterReferrerWasRead`): cubre el
  hook real — `signedInUid()` dispara `flushPending()` en cuanto aparece un uid, DENTRO del mismo
  proceso, sin esperar a un cold start nuevo.
- ✅ `./gradlew testAll --rerun-tasks` verde (4243 tests). `:app:compileProdDebugKotlin` y
  `:app:compileMockDebugKotlin` compilan.
- ⏳ Verificación de campo (pendiente, no bloquea el merge): instalar `assembleProdDebug` en un
  móvil real, borrar datos de la app (o usar uno nuevo), abrir SIN loguear de inmediato, loguear
  después, y confirmar que `users/{uid}` recibe `installChannel` aun con ese retraso.

## Qué cambió en el arreglo original

- `domain/attribution/InstallAttribution.kt` — nuevo `PendingInstallAttribution`.
- `domain/preferences/AppPreferences.kt` — `pendingInstallAttribution` / `setPendingInstallAttribution`
  / `clearPendingInstallAttribution`, implementados en Android (`AndroidDataStoreAppPreferences`,
  claves nuevas, fuera de `USER_SCOPED_KEYS` — es per-install, no per-cuenta), iOS
  (`IosAppPreferences`, inerte en la práctica: no hay Play Install Referrer ahí) y los dos fakes
  (`shared/commonTest` y `:mockkit`, éste último es lo que rompió el primer `testAll` — el flavor
  mock tiene su PROPIO `FakeAppPreferences`).
- `domain/usecase/attribution/ResolveInstallAttributionUseCase.kt` — `resolve()` ya NUNCA intenta
  publicar directamente: clasifica, cierra el gate de lectura, guarda el pendiente, y llama a
  `flushPending()` (nuevo método público, idempotente, reintentable) para el intento inmediato.
- `presentation/app/AppViewModel.kt` — el observador de `signedInUid()` ahora lanza
  `resolveInstallAttribution.flushPending()` en cuanto `uid != null`, para no esperar al próximo
  arranque si el login ocurre en el mismo proceso que leyó el referrer (el caso más común).
- `docs/backlog/README.md` — recuento 869→870 ficheros, 82→83 abiertos (consecuencia de abrir este
  doc, medida por `BacklogStatusGuardrailTest`).

## Consumidores auditados

- `AppViewModel.kt:276-281` — único disparador de `resolve()`, ya identificado como el punto que
  hay que tocar (añadir el reintento de publicación junto a `signedInUid()`).
- `RemoteUserProfileDataSourceImpl.updateInstallAttribution` — sin cambios; solo recibe una llamada
  que hoy nunca le llega con datos reales de producción.
- GA4 (`FirebaseAnalyticsTracker` líneas 102-105, 122-123) — no depende de `userId`, no debería
  estar afectado; pendiente confirmar con el export de mañana (`events_20261126`).
- No hay otros consumidores de `InstallChannel`/`InstallAttribution` fuera de este use case y el
  tracker de analítica.

## Validación para 1.8.1 · 25-11-2026

Play Console confirma 1.8.0 (vc19) en producción. Su AAB precede a los commits de atribución.
En producción se contaron 22 perfiles nuevos hasta las 22:55 (Europe/Madrid), todos sin canal.

La revisión del corte 1.8.1 detectó dos condiciones del cliente. La sesión puede aparecer antes
de que Firestore tenga el documento de perfil, y el endpoint de Meta necesita un token. La app
conserva el referrer antes del login, reintenta la escritura cuando termina la carga del perfil
y resuelve el bloque de Meta con sesión. Android guarda el resultado pendiente y el marcador de
lectura juntos. Si falla el descifrado por un error transitorio, el resultado se conserva para
reintentar. Queda pendiente probar una instalación desde Play con inicio de sesión demorado y
verificar que aparece el canal. Para Meta pagado también queda pendiente un payload real.

`testAll --rerun-tasks` pasó con las pruebas de captura sin sesión, espera a la creación del
perfil y reintento del descifrado. El AAB firmado está registrado en
[RELEASE-1-8-1-001](release-1-8-1-001.md).
