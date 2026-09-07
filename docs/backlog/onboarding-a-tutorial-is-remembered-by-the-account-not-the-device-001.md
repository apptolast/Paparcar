# ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001 · El tutorial lo recordaba el teléfono, no el usuario

**Estado:** ✅ Done · mergeado a master el **2026-09-07** (squash, rebasado sobre `MONEY-001`)
> ⏳ Sin verificar en dos sitios, y ninguno de los dos lo puede cerrar un test: el veredicto del job
> `apple` del CI (`iosMain` no compila en Windows) y la prueba en mano — reinstalar sobre una cuenta
> que ya cerró el tutorial y comprobar que no vuelve.

## Problema

Reportado por el user (06-09): **desinstala, reinstala, vuelve a iniciar sesión y el tutorial
guiado reaparece a mitad — en el paso 3 de 4.** Ni empieza limpio ni se queda callado: retoma un
recorrido que esa instalación nunca hizo.

Todo el estado del checklist vive en un único sitio, y es el teléfono:

| Clave (DataStore `paparcar_prefs`) | Qué es |
|---|---|
| `first_steps_done` | el latch de pasos banqueados |
| `first_steps_deferred` | los "ahora no" |
| `first_steps_dismissed` | omitido o cerrado en la tarjeta final |

Leído del emulador conectado el 06-09 (`files/datastore/paparcar_prefs.preferences_pb`):

```
onboarding_completed, legal_consent_accepted, has_confirmed_first_park,
first_steps_dismissed, first_steps_done = { MARK_PARKING, UNDERSTAND_WATCH }
```

Eso es exactamente un tutorial parado en el paso 3, en un fichero que sobrevive a todo lo que el
usuario entiende por "borrar datos".

Hay **tres** vías por las que el estado del tutorial sobrevive a un borrado, y las tres están
abiertas hoy:

1. **`allowBackup="true"` sin reglas de extracción** (`app/src/main/AndroidManifest.xml:52` — no hay
   `dataExtractionRules` ni `fullBackupContent`). El Auto Backup se lleva `files/datastore/` y la
   base Room, y los restaura en la instalación. Ésta es la que mordió al user: desinstalar y
   reinstalar devuelve el latch viejo intacto.
2. **Cerrar sesión no limpia las preferencias.** `SplashViewModel.wipeLocalUserData()`
   (`SplashViewModel.kt:135-149`) es el hook de `SESSION-ISOLATION-001`: drena geocercas y vacía
   **todas las tablas de Room**… y no toca el DataStore. `DeleteAccountUseCase` tampoco
   (`domain/usecase/user/DeleteAccountUseCase.kt:24-31`): barre repos, spots y cuenta auth, y deja
   las preferencias donde estaban. El tutorial del usuario A sigue ahí cuando entra el usuario B.
3. **La mitad VIVA de la proyección adelanta pasos sola.** Aunque el latch se borre de verdad, al
   re-loguear el bootstrap baja de Firestore el vehículo y la sesión de aparcamiento activa, y
   `resolveFirstSteps` tica `MARK_PARKING` (hay sesión) y `UNDERSTAND_WATCH` (hay sesión + detección
   viva). Esto **no se toca**: es la doctrina "un paso se completa con estado MEDIDO, nunca con un
   tap", y es honesta — quien tiene un coche aparcado sabe aparcar. Lo que estaba mal no es que se
   adelante, es que el tutorial se ofrezca siquiera a quien ya lo cerró.

Y dos defectos colaterales del mismo agujero:

4. **`RestartFirstSteps` no limpia `deferred`** (`SettingsViewModel.kt:167-171`): borra `done` y
   `dismissed` y nada más. El contrato promete lo contrario por escrito
   (`AppPreferences.kt:58-59` "Replaying from Settings clears this together with
   `setFirstStepsDone`", y `:70-74` "Replaying from Settings clears it with the rest"). Consecuencia:
   repites el tutorial y los pasos que un día declinaste se saltan en silencio, para siempre. Sin
   ningún test que lo vigile.
5. **`pending_park_nudge` y `pending_prompt_window` cruzan de cuenta**, por la vía 2 y con más
   consecuencias que el tutorial: un "¿dónde dejaste el coche?" del usuario A queda vivo en la
   sesión del usuario B apuntando a un `vehicleId` que ya no existe en Room.

## Doctrina violada

- **`SESSION-ISOLATION-001` con un agujero.** El invariante ya está escrito en el KDoc de
  `wipeLocalUserData`: *"Paparcar treats local storage as the active user's cache only … no previous
  user's rows leak into the new session, regardless of which path triggered the sign-out"*. Se
  aplicó a Room y a las geocercas, y se dejó fuera el tercer almacén local que existe. Un invariante
  que sólo cubre dos de sus tres almacenes es la definición de parche.
- **Sistemas, no parches.** La pregunta de fondo no es de qué clave nos olvidamos, sino **de quién
  es cada hecho guardado**: del dispositivo o de la cuenta. Hoy no hay respuesta escrita en ninguna
  parte y por eso el tutorial —un hecho del usuario— acabó viviendo donde viven el tema y las
  unidades.

## Señales / datos disponibles

- El perfil remoto `users/{uid}` ya se lee **remote-first en cada arranque**
  (`UserProfileRepositoryImpl.getOrCreateProfile`, con timeout de 8 s y fallback a Room), y el
  `SplashViewModel` ya tiene ese `UserProfile` en la mano en el paso 2/3 del bootstrap. No hay que
  inventar transporte: hay un sitio por cuenta que se lee en el momento exacto en que hace falta.
- `updateDefaultVehicleId` ya establece el patrón de escritura puntual sobre ese doc
  (`update(mapOf(campo to valor))`), que es lo que evita el `set()` sin merge que en su día mandó
  ~10 campos a null en el workData.

## Diseño

**Regla que se escribe una vez y decide todas las claves:** un hecho que describe **al usuario**
vive en su cuenta; un hecho que describe **este teléfono o esta instalación** vive en el DataStore
y se queda cuando el usuario se va.

### 1 · "Ya cerró el tutorial" pasa a ser de la cuenta

- `UserProfile.firstStepsDismissedAt: Long?` + el mismo campo en `UserProfileDto` y su línea en el
  deserializador campo-a-campo de `RemoteUserProfileDataSourceImpl`.
- Escritura puntual: `RemoteUserProfileDataSource.updateFirstStepsDismissedAt(userId, millis?)`,
  calcada de `updateDefaultVehicleId` — nunca un `set(profile)` completo.
- **No se añade columna a Room, y es una decisión, no un olvido.** La copia de Room no compraría
  nada: el único lector es el bootstrap, que ya recibe el DTO remoto; y en el único caso en que
  Room contestaría (arranque offline) el fichero de preferencias de esa misma instalación ya tiene
  la respuesta. Es seguro porque **la restauración es monótona**: un `null` sólo puede *no
  restaurar*, jamás borrar un dismissal local. El KDoc del campo dice esto, para que nadie lo lea
  como el bug de paridad DTO/Entity que normalmente sería.
- Restauración: en `SplashViewModel.bootstrap`, con el perfil ya en la mano, si
  `firstStepsDismissedAt != null` → `prefs.setFirstStepsDismissed(true)`. Tres líneas, sin caso de
  uso nuevo: la cuenta le devuelve al dispositivo lo que el dispositivo perdió.
- Escritura: `SetFirstStepsDismissedUseCase(prefs, authRepository, userProfileRepository)` —
  preferencia local primero (la UI no espera a la red) y espejo remoto best-effort. Lo usan Home
  (al omitir o cerrar la tarjeta) y Ajustes (al reiniciar el tutorial, poniéndolo a null). Es un
  caso de uso y no dos líneas en cada ViewModel porque son **dos almacenes que no pueden divergir**
  y hay dos escritores; `HomeViewModel` no gana dos repositorios más por esto.
- **El latch por pasos sigue siendo local**: es estado de trabajo, y la mitad viva lo reconstruye
  sola. Lo que cruza de dispositivo es el veredicto "este usuario ya no quiere el tutorial".

### 2 · Cerrar sesión limpia lo que era del usuario

`AppPreferences.clearUserScopedState()` (Android + iOS + fake), llamado desde
`wipeLocalUserData()` junto al wipe de Room. Clasificación completa de las 20 claves, que es el
barrido de consumidores de este ticket:

| Se borra al desloguear | Por qué |
|---|---|
| `first_steps_done` · `first_steps_deferred` · `first_steps_dismissed` | del usuario; el dismissal vuelve de la cuenta |
| `pending_park_nudge_*` (3) | pregunta viva sobre un `vehicleId` que el wipe de Room acaba de borrar |
| `pending_prompt_window_*` (6) | ídem: la pregunta muere con la sesión que la hizo |
| `has_confirmed_first_park` · `first_park_nudge_count` · `last_first_park_nudge_at` | historia de aparcamiento del usuario, no del teléfono |

| Se queda | Por qué |
|---|---|
| `onboarding_completed` · `gps_accuracy_disclaimer_seen` · `location_permission_requested` | describen esta INSTALACIÓN; borrarlas castigaría al mismo usuario que sólo cerró sesión |
| `legal_consent_accepted` | es por instalación **por decisión de `AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001`**, y ese diseño no se cambia de refilón desde aquí |
| `auto_detect_parking` · `notify_parking_detected` · `theme_mode` · `use_imperial_units` · `default_map_type` | ajustes de este teléfono |

### 3 · "Borrar datos" tiene que borrar datos

`android:allowBackup="false"` + `data_extraction_rules.xml` que excluye también la transferencia
entre dispositivos. Lo que el Auto Backup se llevaba —DataStore, SharedPrefs de detección y la base
Room— es **cache local de una cuenta que ya vive en Firestore**: restaurarlo no recupera nada que el
login no recupere, y ya nos ha costado dos sustos (la base vieja restaurada en instalación primera,
y este ticket). Se pierden tema/unidades/tipo de mapa al cambiar de móvil: es el precio, y es
barato.

### 4 · Reiniciar el tutorial cumple su contrato

`RestartFirstSteps` limpia `done`, `dismissed` **y `deferred`**, y borra el dismissal remoto. Con
test, que es lo que no tenía.

## Criterio de éxito

- Desinstalar + reinstalar + login de una cuenta que cerró el tutorial → **no aparece**, ni entero
  ni a mitad.
- Desinstalar + reinstalar + login de una cuenta a mitad → aparece en el paso que su estado real
  justifique, sin heredar latch de la instalación anterior.
- Cerrar sesión con A y entrar con B en el mismo teléfono → B no ve el tutorial de A, ni su
  "¿dónde dejaste el coche?".
- Reiniciar el tutorial desde Ajustes → vuelve a empezar por el paso 1, **incluidos los pasos
  declinados**.
- Tests: restauración monótona en Splash, wipe de las claves de sesión (y testigo de que las de
  dispositivo NO se tocan), `RestartFirstSteps` limpiando los tres sets, paridad del deserializador
  del perfil.

## Consumidores auditados

**Quién escribe el estado del tutorial** (`grep setFirstSteps`):

| Sitio | Estado |
|---|---|
| `HomeViewModel` · `DismissFirstSteps` | ✅ pasa por `SetFirstStepsDismissedUseCase` (device + cuenta) |
| `HomeViewModel` · `CompleteFirstStep` / `DeferFirstStep` / `ResumeFirstStep` / latch | ✅ siguen siendo locales **a propósito**: son estado de trabajo que la mitad viva reconstruye |
| `SettingsViewModel` · `RestartFirstSteps` | ✅ los tres sets + el dismissal remoto |
| `SplashViewModel` · `reconcileFirstStepsMemory` | ✅ restaura (cuenta→device) y repara (device→cuenta) |

**Implementaciones de `AppPreferences`** (el método nuevo obliga a las cuatro):
`AndroidDataStoreAppPreferences` ✅ · `IosAppPreferences` ✅ (misma lista, clave a clave) ·
`fakes/FakeAppPreferences` de mock ✅ (deja intactos los switches del Dev Catalog: el preset ES la
demo) · `commonTest/FakeAppPreferences` ✅ (con contador, para que un fake vacío no dé por buena una
llamada que no ocurrió).

**Implementaciones de `RemoteUserProfileDataSource`** (método nuevo): impl real ✅ + los tres dobles
de test (`VehicleDeleteCascadeTest`, `DeleteVehicleRemoteWorkerTest`, `ParkingSyncWorkerTest`) ✅.

**Escritores del doc `users/{uid}`** — el riesgo de que alguien pise el campo nuevo con un `set()`
completo: `createOrUpdateProfile` es el ÚNICO `set`, y sólo corre en la creación del perfil, con un
DTO recién construido. Los demás escritores (`updateDefaultVehicleId`, y ahora
`updateFirstStepsDismissedAt`) son `update(mapOf(...))` sobre un campo. ✅ sin colisión.

**Room**: sin cambio de esquema, y por tanto sin bump ni `Migration` — decisión razonada arriba, no
un olvido de `DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001`.

**Lo que el wipe de sesión cambia para el resto del sistema:** `FirstParkNudgeWorker` lee
`hasConfirmedFirstPark` / los contadores del nudge, que ahora se borran al desloguear. Es lo
correcto (son la historia de aparcamiento de UN usuario) y el efecto asumido es que el mismo usuario
que cierra sesión y vuelve puede recibir otra vez el aviso de arranque en frío.

## Verificación

- `:shared:testDebugUnitTest` → **2242 tests, 0 fallos**, con las 9 pruebas nuevas ejecutadas
  (comprobado en los XML de resultados, no sólo por el verde del build).
- `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` + `:app:processProdDebugResources` ✅.
- Manifest fusionado de `prodDebug`: `allowBackup="false"` + `dataExtractionRules="@xml/data_extraction_rules"` ✅.
- ⏳ iOS: `iosMain` no compila en Windows; queda para el job `apple` del CI.
- ⏳ En mano: reinstalar sobre una cuenta que cerró el tutorial y comprobar que no vuelve.
