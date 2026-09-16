# DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001 · Si un usuario tiene la detección apagada o rota, no hay forma de saberlo

**Estado:** ✅ Done
⚠️ **Pendiente del user antes del SIGUIENTE release de Play** (no del merge): el delta de Data
Safety por el tipo de dato nuevo — ver §Privacidad. Mergear no publica; declarar sí.

## Coste medido

**Escrituras a Firestore: ~5 por usuario y VIDA**, no por uso. El desglose: la primera publicación,
más una por cada transición del trío (intent · bloqueadores · exención) — que en la práctica son los
permisos que se conceden durante el onboarding y la exención de batería. Después, cero, salvo que el
usuario revoque, reinstale o toque el interruptor. Un sign-out limpia el fingerprint local, así que
la cuenta siguiente publica el suyo (1 escritura más).

Con 10.000 usuarios eso es ~50.000 escrituras **totales**, repartidas en meses. La cuota gratuita de
Firestore son 20.000 escrituras/día, así que esto cabe entero dentro de ella. En dinero, a la tarifa
multi-región de `eur3`, del orden de céntimos en total (⚠️ verificar la tarifa vigente antes de
citar una cifra; lo que este ticket mide con certeza es el número de escrituras, no su precio).

La versión ingenua —publicar al observar— habría costado ~3 escrituras por usuario y **día**: unas
30.000/día con esa misma base, es decir *por sí sola* por encima de la cuota gratuita, todos los
días. Pero el argumento decisivo no es el dinero, que tampoco sería enorme:

1. **Escalaría con el USO, no con la información.** Un usuario que abre la app 20 veces al día
   pagaría 20 escrituras por no contar nada nuevo.
2. **Dejaría `detectionStateUpdatedAt` sin significado.** Si cada apertura reescribe la fecha, todos
   los docs dicen «ahora» y el campo deja de poder distinguir un permiso revocado ayer de uno
   revocado hace tres meses — que es justo para lo que existe.

**Analytics: gratis e ilimitado.** El coste real son **2 de las 25 propiedades de usuario** que
Firebase permite por instalación. `setUserProperty` no hace llamada de red propia (el SDK adjunta el
valor a los eventos siguientes), y por eso la propiedad se fija en cada emisión sin dedupe.

**Tamaño del doc:** 4 campos (~100 bytes) en `users/{uid}`, que se lee en cada arranque en frío.
Despreciable.

**Coste en código:** 23 ficheros tocados (+378/−22) + 5 nuevos (~558 líneas, de las que 357 son
test). 22 tests nuevos. Suite completa: **3.318 tests, 0 fallos** (`testAll --rerun-tasks`).

**Veredicto: el coste NO es demasiado** — siempre que el dedupe esté, que es la mitad del ticket.
Lo caro de esta feature nunca fue Firestore; es la declaración de Data Safety.

## Problema

Mirando en Firestore la cuenta `4cIm2tNiqEViB0xYxmvgSwpz1Ew2` (16-09-2026): un aparcamiento en
Francia con `detectionPath: "manual"`, `armEvidence: null`, `spotType: "MANUAL_REPORT"`, y su coche
(Skoda Fabia, `bluetoothDeviceId: null`, `isActive: true`).

De ahí **no se puede decidir** si esa persona tiene la detección automática funcionando. Los dos
campos que parecen contestar, no contestan:

- `Vehicle.isActive` es el **selector entre coches** (`Vehicle.kt:44-45`), no un interruptor de
  detección. Con un solo vehículo vale `true` siempre — `Inactive` solo puede existir con 2+ coches
  sin BT. Es ortogonal a permisos y al interruptor.
- `detectionPath: "manual"` es **procedencia de ese pin**, no un ajuste: dice que lo puso una mano.
  Es compatible tanto con "la detección está apagada" como con "está perfectamente activa y el
  usuario marcó antes de que confirmara".

Barrido de los DTO remotos: **no existe ningún campo en Firestore que diga si la detección está
activa**. `UserProfileDto` (`UserProfileDto.kt:6-18`) lleva `userId, email, displayName, photoUrl,
createdAt, updatedAt, defaultVehicleId, firstStepsDismissedAt` y nada más. No hay Remote Config en
el proyecto (cero ocurrencias). `FirebaseAnalyticsTracker` **no usa user properties** en absoluto.

Lo único que hoy contesta algo parecido:

| Fuente | Qué contesta | Alcance |
|---|---|---|
| `parkingHistory/{id}.armEvidence` + `detectionPath` | *post-hoc*: la detección estaba armada **en ese momento** | toda la población, pero solo cuando produce un pin |
| `VehicleDto.bluetoothDeviceId` | una de las dos mitades del tier (BT emparejado) | toda la población |
| `diagnostics/{uid}/sessions` → `batteryUnrestricted`, `requiresAutostart`, `requiresOemBatteryFreeze`, `exactHeartbeatLaneDead` | el snapshot completo | **solo allowlist**: se auto-desactiva salvo `diagnostics_config/{uid}.enabled == true`, retención 7 días |

Consecuencia medible: un usuario que nunca concedió background location, o que apagó el interruptor,
es **indistinguible** de uno que simplemente no ha aparcado. Ambos son silencio. Y el segundo grupo
—el que tiene la app rota sin saberlo— es exactamente el que hay que poder contar.

## Doctrina violada

Ninguna regla existente se rompe: esto es un **hueco**, no una infracción. Pero el ticket sí hereda
una doctrina que tiene que respetar, y es la trampa principal del diseño:

**[DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]** — `SettingsState.kt:64-79`. El estado real
NO es un booleano: es `autoDetectActive = autoDetectParking && detectionHealthy`, y el comentario
insiste en que el intent almacenado **no se sobrescribe** cuando falta un permiso, porque olvidar un
OFF deliberado re-armaría la detección a espaldas del usuario.

⛔ Sincronizar un solo campo `detectionEnabled: Boolean` **destruiría justo esa distinción**: "el
usuario lo apagó" y "el usuario lo quiere pero le falta background location" son dos poblaciones con
dos remedios opuestos, y colapsarlas hace el dato inútil para lo que se pide. El invariante viaja
entero o no viaja.

## Señales / datos disponibles

Todo existe ya y es local:

- `AppPreferences.autoDetectParking` (`domain/preferences/AppPreferences.kt:52-56`) — el intent.
  DataStore en Android, `NSUserDefaults` en iOS (`auto_detect_parking`, default `true`).
- `AppPermissionState` (`domain/permissions/AppPermissionState.kt:3-43`) — los 7 campos, incluidos
  `isBatteryOptimizationExempt` (`:18`) y `isLocationServicesEnabled` (`:11`).
- `missingProducerPermissions()` (`RequiredPermission.kt:44-45`) — la lista de bloqueadores ya
  calculada, con el vocabulario cerrado de `RequiredPermission`.
- `DetectionReliabilityLevel` (GOOD / REDUCED) de `ObserveDetectionReliabilityUseCase` — el eje
  ortogonal del entorno OEM.
- `ObserveDetectionReadinessUseCase` (`:46-190`) — ya combina las 5 fuentes en vivo. No hay que
  inventar ningún observador nuevo desde cero.

## Diseño

Dos superficies, deliberadamente distintas, porque contestan **preguntas distintas sobre poblaciones
distintas** — la separación que `AnalyticsTracker.kt:11-15` ya establece entre diagnósticos (por
usuario, opt-in) y analytics (anónimo, poblacional).

### (a) Firestore — *"¿esta cuenta concreta puede detectar?"*

Campos nuevos en `UserProfileDto`, **todos nullable**, con la misma semántica de ausencia que
`firstStepsDismissedAt` (`:14-17`): ausente en todo doc escrito antes de este ticket → `null` →
**desconocido**, nunca "apagado".

```kotlin
/** Intent almacenado del interruptor maestro. NO es "la detección funciona" — para eso hace
 *  falta cruzarlo con [detectionBlockers]. [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] */
val autoDetectParking: Boolean? = null,
/** Permisos PRODUCER que faltan, con el vocabulario de RequiredPermission. Lista vacía =
 *  nada falta. null = nunca se sincronizó. Es lo que separa "lo apagó" de "no puede". */
val detectionBlockers: List<String>? = null,
/** [DOZE-001] La otra mitad del tier: sin exención, la detección se degrada en OEMs hostiles. */
val batteryOptimizationExempt: Boolean? = null,
/** Cuándo se tomó el snapshot. Un snapshot sin fecha es peor que ninguno: no se puede saber si
 *  describe el ahora o un permiso que se revocó hace tres meses. */
val detectionStateUpdatedAt: Long? = null,
```

Escritura por un actualizador **field-targeted**, calcado de `updateFirstStepsDismissedAt`
(`RemoteUserProfileDataSourceImpl.kt:51-56`) y por su misma razón declarada — *un `set(profile)`
empujaría el doc entero desde el DTO que el llamante tenga a mano*:

```kotlin
suspend fun updateDetectionState(userId: String, state: RemoteDetectionState)
```

**⛔ Write-only, sin campo en el modelo de dominio y sin lector.** `UserProfile` (domain) NO gana
estos campos y nada en la app los lee de vuelta. Es telemetría, no estado. En el momento en que
alguien los leyera se convertirían en una segunda fuente de verdad compitiendo con la local, y un
valor remoto rancio podría contradecir al permiso real del device — exactamente el bug que
`updateIdentityFields` existe para no tener.

**El problema real del diseño es la amplificación de escrituras.** `permissionManager.permissionState`
se refresca en cada `onResume`; engancharse a él sin más = una escritura a Firestore por cada vez que
el usuario abre la app, por usuario. Inaceptable en coste y en batería. El invariante:

> Se escribe cuando el **snapshot CAMBIA**, no cuando se observa.

Un `distinctUntilChanged` no basta por sí solo: se pierde entre procesos. Hace falta el último
snapshot sincronizado **cacheado localmente** (DataStore, junto al resto de prefs) y comparado antes
de escribir. La decisión de "¿esto merece una escritura?" es una **función pura** en
`domain/detection/` (patrón ya establecido: `SentryWakeCooldown.kt`, `HumanPoweredRide.kt`), no un
método privado de quien la llame — y así es directamente testeable sin Firestore.

Queda por resolver durante la implementación: si el snapshot cambia estando offline, ¿se encola en
el `SyncOutbox` o se pierde? Mirar cómo lo resuelve `UserProfileRepositoryImpl.kt:84`
(`runCatching` alrededor de `updateFirstStepsDismissedAt`). **Perder el snapshot offline es
aceptable** —el siguiente cambio lo reescribe, y el campo es una foto del ahora, no un log— pero
tiene que ser una decisión escrita, no un descuido.

### (b) Analytics — *"¿qué porcentaje de la población tiene la detección rota?"*

Una **user property** (no un evento): la pregunta es sobre el estado en que vive un usuario, no
sobre un instante. Eso permite además segmentar todos los eventos que YA existen
(`det_trigger_fired`, `parking_session_started`) por salud de detección, que es de donde sale el
dato accionable.

- Extender `AnalyticsTracker` (`domain/analytics/AnalyticsTracker.kt:24-26`) con
  `fun setUserProperty(property: AnalyticsUserProperty)` — sealed class al lado de `AnalyticsEvent`,
  para que los call sites nunca toquen magic strings, igual que hoy.
- `NoOpAnalyticsTracker` (`:110-112`) la implementa a vacío — el Dev Catalog no emite nada.
- Nombres y claves de wire **solo** en `FirebaseAnalyticsTracker`, como el resto del fichero.
- **Vocabulario CERRADO y baja cardinalidad** — Firebase topa en 25 propiedades y una propiedad de
  alta cardinalidad no agrega. Propuesta: `detection_state` ∈
  `{ on, off_by_user, blocked_permissions, reduced_reliability }` (precedencia: un bloqueador duro
  gana a un aviso de fiabilidad, igual que `detectionReliabilityReduced` en `SettingsState.kt:83-84`)
  y `detection_tier` ∈ `{ automatic, assisted }`.
- ⛔ Sin lista de permisos concretos aquí: eso es el detalle por-cuenta y vive en (a). Analytics
  cuenta poblaciones.

**✅ Verificado (16-09-2026), no hace falta plan B**: GitLive 2.7.0 **sí** expone
`setUserProperty` en `commonMain` — leído del `-sources.jar` del artefacto en la caché de Gradle:

```
commonMain/dev/gitlive/firebase/analytics/analytics.kt:15  fun setUserProperty(name: String, value: String)
appleMain/dev/gitlive/firebase/analytics/analytics.kt:25   actual fun setUserProperty(...) { ios.setUserPropertyString(value, name) }
```

O sea: `expect`/`actual` ya resuelto upstream, con `actual` de Apple incluido. **iOS lo hereda sin
hueco** y no hay que degradar la propiedad a evento. Hoy el tracker solo usa `logEvent`
(`FirebaseAnalyticsTracker.kt:74`) porque nunca se necesitó, no porque faltara.

### Privacidad y Play Data Safety — ⚠️ requiere decisión del user

Esto **añade un tipo de dato nuevo** atado a una cuenta identificada: el estado de permisos del
dispositivo. La doctrina del proyecto ya es explícita en este terreno — `AnalyticsEvent.TriggerFired`
(`AnalyticsTracker.kt:54-57`) renuncia a llevar celda **precisamente** para no añadir un tipo a la
declaración de Data Safety.

- No es ubicación, ni identificador de dispositivo: lo más cercano es *App activity / App info and
  performance*.
- El user ya tiene pendiente revisar Data Safety contra §4 y el pivote de trayectos. **Este ticket
  entra en ese mismo delta** y no se mergea sin que esa revisión lo contemple.
- Verificar además que `firestore.rules` permite los campos nuevos en el doc propio del usuario.

## Criterio de éxito

1. Un doc de `users/{uid}` recién escrito permite contestar, sin abrir diagnósticos: *¿lo apagó, o no
   puede?* — y se distingue del caso "nunca se sincronizó" (`null`).
2. Abrir y cerrar la app N veces **sin cambiar nada** produce **cero** escrituras a Firestore.
   Test unitario sobre la función pura de decisión + un test del repositorio con un doble contando
   llamadas.
3. Revocar background location desde Ajustes del sistema y volver a la app deja
   `detectionBlockers: ["BACKGROUND_LOCATION"]` en el doc, y `autoDetectParking` **intacto**.
4. `detection_state` aparece como user property en DebugView de Firebase con el vocabulario cerrado.
5. Los docs antiguos siguen deserializando (campos ausentes → `null`, sin crash). Cubierto por el
   test de paridad de deserializadores Firestore que ya existe en `androidUnitTest`.
6. `./gradlew testAll` verde (⛔ `testAll`, no `:shared:testDebugUnitTest`).

## Lo implementado (y lo que cambió respecto al diseño)

- `domain/detection/DetectionStatePublication.kt` — `DetectionStateSnapshot` (4 campos),
  `remoteFingerprint()`, `needsPublishing()`, `DetectionStateLabel` + `label()`. Puro, sin
  inyección, 13 tests.
- `domain/usecase/detection/PublishDetectionStateUseCase.kt` — `observe()` (combina permisos +
  interruptor + fiabilidad) y `publish()` (analytics siempre, Firestore deduplicado). 9 tests.
- Colector único en `AppViewModel`, por la razón que su propio comentario ya daba para las alertas
  de zona: **es el único ViewModel vivo toda la sesión de UI**. Un colector por pantalla solo se
  enteraría de una revocación si el usuario abriera esa pantalla después.

Tres desviaciones respecto al diseño escrito arriba, todas a mejor:

1. **Los campos SÍ se leen de vuelta en `toUserProfileDto`.** El diseño decía «write-only, sin
   lector»; `FirestoreDeserializerParityTest` lo rechazó, y tenía razón: un DTO que declara un campo
   y no lo lee lo PIERDE en cualquier `set(profile)` futuro. La asimetría correcta no era «no
   leerlo», era **no meterlo en el modelo de dominio** — y así quedó: el DTO es un espejo fiel del
   documento, `UserProfile` no tiene los campos y nada en la app los consume.
   ⚠️ Comprobado con testigo: quitando la lectura de `detectionBlockers`, el guard falla.
2. **La propiedad de tier reutiliza `DetectionTier`** (AUTOMATIC / ASSISTED_PLUS / ASSISTED) en vez
   del booleano `hasBluetoothPairedVehicle` que proponía el diseño. El vocabulario ya existía, ya
   estaba cerrado, y una segunda forma de decir «cuán automática es esta instalación» habría podido
   divergir de la que la UI promete.
3. **`reliabilityReduced` no se persiste**, solo viaja a analytics — y por eso el fingerprint compara
   los tres campos guardados, no el snapshot entero. Si entrara en la comparación, un cambio de
   fiabilidad gastaría una escritura para reescribir tres valores idénticos.

Decisión pendiente que el diseño dejaba abierta, ya resuelta: **offline no se encola**. Un fallo de
escritura **no** graba el fingerprint, así que el mismo estado se reintenta en la siguiente emisión.
Perder la foto estando sin red es aceptable —el campo es una foto del ahora, no un log— y el
siguiente cambio real la reescribe.

## Consumidores auditados

Añadir un método a `RemoteUserProfileDataSource` rompe en compilación a todos sus implementadores —
bueno, es la red. Inventario a cerrar:

- [x] `RemoteUserProfileDataSource` + `Impl` — `updateDetectionState`, field-targeted, 4 campos en
      UNA escritura (un lector no puede ver medio snapshot)
- [x] `UserProfileRepository` + `Impl` — `publishDetectionState`, `runCatching`, sin outbox (ver arriba)
- [x] `toUserProfileDto` — lectura de vuelta de los 4 (exigido por el guard de paridad)
- [x] `AppPreferences` × 4 impls — Android (DataStore), iOS (`NSUserDefaults`), fake de commonTest,
      fake del flavor mock. En **ambas** listas `USER_SCOPED_KEYS`: conservado tras un sign-out, el
      fingerprint haría que la cuenta siguiente se creyera ya reportada y callara para siempre
- [x] `SyncOutboxDrainerTest` · `UserProfileRepositoryImplTest` (doble que CUENTA escrituras) ·
      `VehicleDeleteCascadeTest` · `ParkingSyncWorkerTest` · `DeleteVehicleRemoteWorkerTest`
- [x] `NoOpAnalyticsTracker` + `FakeAnalyticsTracker` (lista `properties` SEPARADA de `events`: un
      test que las mezclara no podría distinguir «una vez por cambio» de «una vez por emisión»)
- [x] `AppViewModelTest` — 22 sitios de construcción
- [x] Grafo mock (`MockKoinModuleVerifyTest` en `:app`) resuelve — verde en `testAll`
- [x] **Dev Catalog / galería: nada que añadir.** El ticket no crea pantalla, estado ni variante
      visible; su única superficie de usuario es la que ya existía en Ajustes
- [x] `firestore.rules` — **no hace falta tocar nada**. La regla del doc de perfil es
      `match /users/{uid} { allow read, write: if request.auth != null && request.auth.uid == uid; }`
      (`firestore.rules:39-40`): permiso a nivel de DOCUMENTO, sin lista blanca de campos. A
      diferencia de `spots` y `sharedCarInvites`, que sí acotan con
      `diff(resource.data).affectedKeys().hasOnly([...])`, aquí un campo nuevo entra solo.
      Fichero validado contra el servicio: *OK, no errors detected*.
- [x] **iOS sin hueco**: toda la lógica vive en `commonMain`, `IosAppPreferences` guarda el
      fingerprint y GitLive expone `setUserProperty` con `actual` de Apple. La única asimetría real
      es `batteryOptimizationExempt`, que en iOS no existe como concepto: `IosPermissionManagerImpl`
      lo deja en `false` y el campo viaja como tal. ⚠️ **Eso es una mentira pequeña pero mentira** —
      un iPhone se leería como «sin exención» igual que un Android hostil. Follow-up abajo.

## Hallazgo colateral: las reglas del repo NO son las desplegadas

Al comprobar lo anterior salió una deriva **ajena a este ticket**, en
`sharedCars/{carId}/members/{uid}`. Comparado el fichero del repo contra lo que devuelve
`firebase_get_security_rules` (ignorando comentarios):

| | repo (`firestore.rules`) | desplegado en `pap-26` |
|---|---|---|
| crear fila de miembro | dueño **o** invite vivo | dueño **o** invite vivo |
| actualizar tu propia fila | `uid == request.auth.uid` **y** `resource != null` | dueño **o** invite vivo |

Es decir: **en producción, un miembro no puede modificar su propia fila una vez caducado su
código de invitación.** El repo ya tiene la versión correcta (separa `update` de `create`); lo que
falta es desplegarla.

Impacto verificado, no deducido de las reglas a solas:
`RemoteSharedCarDataSourceImpl.setMemberSharesRoutes` (`:171-176`) hace
`members(carId).document(uid).update(mapOf("sharesRoutes" to …))` — un `update` sobre esa misma
fila. En un `update` field-targeted `request.resource.data` es el documento FUSIONADO, así que
`holdsValidInvite()` se evalúa contra el `inviteCode` guardado, y un invite caducado ya no existe
como condición válida. Resultado: **un miembro que no sea el dueño deja de poder apagar/encender
«compartir rutas» en cuanto caduca su código**.

Y falla **en silencio**: la llamada va envuelta en `runCatching` con un
`PaparcarLogger.w(TAG, "setMemberSharesRoutes skipped — not a member of $carId")`, un mensaje que
además **atribuye mal la causa** — diría «no eres miembro» cuando sí lo eres y lo que caducó es el
invite.

Esto ya estaba anotado como pendiente en memoria bajo F8 `VEH-SHARED-001` («⏳ desplegar
función+reglas»), así que **no se abre ticket nuevo**: queda aquí la medida concreta de qué se
rompe mientras no se despliegue. ⚠️ **Este ticket no necesita ningún despliegue de reglas**, así que
no arrastra el arreglo por la puerta de atrás — se despliega cuando toque F8, con su prueba.

> ✅ **RESUELTO el 16-09: reglas desplegadas.** Lo que se había desplegado de F8 era la
> función/push, no `firestore:rules`. Verificado releyendo del servidor: `members/{uid}` ya tiene
> `create` y `update` separados, con el comentario de `[SHARED-A-PARKING-TELLS-THE-GROUP-001]`.
> `firebase.json` declara **dos** bases (`(default)` y `debug`) sobre el MISMO fichero, y la CLI
> confirmó dos releases.
>
> ⛔⛔ **`firebase_deploy` del MCP devolvió `{"status":"success","progress":100}` SIN aplicar nada.**
> Releído el ruleset después, seguía el antiguo. Lo que funcionó fue
> `firebase deploy --only firestore:rules --project pap-26` por CLI, cuya salida sí dice
> *"released rules firestore.rules to cloud.firestore"* (dos veces, una por base).
> **Un deploy por MCP no se da por bueno con su propio `success`: hay que releer el ruleset.**

## Follow-ups deliberadamente fuera de alcance

- **`batteryOptimizationExempt` en iOS miente por omisión**: viaja `false` porque el concepto no
  existe allí, no porque falte el permiso. Hoy no engaña a nadie (no hay usuarios iOS), pero en
  cuanto los haya, un iPhone se contará junto a los Android hostiles. El arreglo honesto es que el
  campo sea nullable end-to-end y iOS mande `null`; requiere tocar `AppPermissionState`, que hoy lo
  declara `Boolean` no-nulo, y eso es más ticket del que esto justifica ahora.
- Un panel/consulta que agregue esto (dashboard). Este ticket deja el dato; leerlo es otra tarea.
- `DET-A-DEPARTURE-HAS-NO-PROVENANCE-001` ya está citado en `AnalyticsTracker.kt:74-82` y sigue
  abierto; no se toca aquí.
