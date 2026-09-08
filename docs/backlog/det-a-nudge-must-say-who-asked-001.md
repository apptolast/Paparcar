# DET-A-NUDGE-MUST-SAY-WHO-ASKED-001 · El pin que responde a un nudge no dice qué carril preguntó

**Estado:** ✅ Done — en master. Descubierto cosechando el corpus de
[DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001](det-bt-a-ride-is-measured-not-timed-001.md).

## Problema

Todas las vías de «no puedo colocarlo, pregúntale» pasan por
`AppNotificationManager.showMarkParkingNudge(source = …)`, y el `source` **se persiste**: el adapter
lo guarda en `PendingParkNudge.source`, documentado ahí mismo como *"which detection path asked —
diagnostics only"*. Pero cuando el usuario responde, el pin se guarda por
`SaveManualParkingUseCase` con `detectionPath = "nudge"` y `armEvidence = null`: **el origen se cae
en el último paso**, justo en el artefacto que sobrevive.

Medido el 08-09-2026 sobre el corpus vivo: una cuenta con BT emparejado desde el 06-09 tiene 12
sesiones en dos días y **6 de ellas son `nudge`**. Con lo que hay en Firestore es imposible decir si
esos seis los preguntó el carril BT (gate de duración), el coordinator (`unattended_*`), o el
watchdog de nudge caducado — y por tanto es imposible responder a la pregunta que los tres tickets
de esa familia acaban haciendo: *¿cuánto estamos preguntando, y quién pregunta de más?*

Los `source` que hoy se pierden son al menos: `bt_no_ride`, `bt_no_move`, `bt_ride_unmeasurable`,
`unattended_<key>` (uno por `UnattendedSaveReason`), `stale_pending_watchdog`, y el `outcome` que
pasa `RunHonestCloseUseCase`.

## Doctrina violada

⛔ *En diagnósticos, identificar SIEMPRE qué trigger/path colocó cada pin* — y `DET-PIN-PROVENANCE-001`,
que existe precisamente para que un pin de campo no haya que trazarlo por cable. `nudge` dice CÓMO
se colocó (lo confirmó el usuario) pero no QUIÉN lo nominó, que es la mitad interesante: el pin es
ground truth del usuario, la pregunta es de quién fue la duda.

Nota: no es un caso de uso nuevo [DET-VERDICT-NOT-PREDICATE-001]. Es un campo que ya existe
viajando un paso más.

## Señales / datos disponibles

- `PendingParkNudge.source` ya está en disco (DataStore en Android, `NSUserDefaults` en iOS) cuando
  el usuario responde: nadie lo lee para el pin.
- `armEvidence` es exactamente la columna de "qué armó esto", ya espejada a Firestore.

## Diseño

⛔ **El diseño esbozado al abrir la ficha era incorrecto, y la razón importa más que el arreglo.**
Decía: *«el pin de respuesta estampa `armEvidence` con el `source`»*. `armEvidence` NO es una columna
de provenance libre: es el **vocabulario cerrado de un ARM** (`ArmLabel`), y sus palabras contestan
dos preguntas de doctrina —`isVerifiedDeparture` y `confirmsSilentlyWithoutMeasuredDrive`— mediante
`when` exhaustivos, con un guardarraíl que prohíbe expresamente que un conjunto de etiquetas
mantenido a mano decida nada [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001].
Escribir ahí `bt_no_move` habría metido una palabra que el tipo no sabe parsear (`ofPersisted` →
null) justo en el sitio donde el proyecto ya se quemó una vez. Y encima es falso de raíz: **una
respuesta a un nudge no tiene arm** — la evidencia es el usuario.

**El sitio correcto ya tenía precedente en el propio fichero**: `DetectionPath.UnattendedZone`, que
COMPONE su etiqueta (`unattended_zone_<reasonKey>`) y se parsea por prefijo en `ofLabel`. `Nudge`
hace exactamente lo mismo:

- `DetectionPath.Nudge(askedBy)` → `label = "nudge_<askedBy>"`, o `"nudge"` pelado cuando no hay
  fuente. Un `nudge` pelado **no es otro TIPO de pin**: es este con el que preguntó desconocido, que
  es lo que son todos los pines escritos antes de este ticket — y por eso siguen resolviendo.
- `ofLabel` gana su rama de prefijo, gemela de la de `UnattendedZone`.
- `SaveManualParkingUseCase` cambia `fromDetectionNudge: Boolean` por `nudgeSource: String?`:
  **null = nadie preguntó** (pin a mano, `manual`), **vacío = preguntaron y no sabemos quién**
  (etiqueta pelada), **con valor = el carril**. Un booleano al lado de un string son dos hechos que
  pueden discrepar; esto es un hecho, un campo.
- **Quién resuelve la fuente: el ViewModel, una vez.** Ya observa `pendingParkNudge` para el banner,
  así que la lee de su propio estado al ENTRAR en modo pin — no al guardar (el nudge se limpia
  cuando una sesión confirma, y el usuario puede pasar minutos colocando el pin). Eso arregla **las
  dos puertas a la vez** (notificación y banner) sin tocar `MainActivity`, el intent extra ni
  `StartAddParkingEventBus`: la misma verdad no viaja por dos caminos.
- `HomeState.addingParkingFromDetectionNudge: Boolean` pasa a `addingParkingNudgeSource: String?`;
  el slice sigue exponiendo el booleano que la UI necesita, **derivado** (`!= null`), que es para lo
  que existe un slice.

## Criterio de éxito

- ✅ Un pin nacido de un nudge dice en `detectionPath` qué carril preguntó, verificado en test
  (`nudge_bt_no_move`), y un pin a mano sigue siendo `manual`.
- ✅ Los pines históricos (`nudge` pelado) siguen resolviendo — hay test.
- ⏳ Sobre el corpus: los `nudge` se agrupan por origen sin abrir un `parkdiag`. Se verá con los
  pines nuevos; los 6 que motivaron el ticket ya están escritos y son irrecuperables.

## Consumidores auditados

`grep -rn "fromDetectionNudge\|DetectionPath.Nudge\|PIN_TO_PIN_ELIGIBLE_PATHS\|nudge" shared/src app/src`:

- `SaveManualParkingUseCase` — **cerrado**: `nudgeSource: String?`, path compuesto, sin constante
  `PATH_NUDGE` (una etiqueta que el tipo compone no se vuelve a deletrear en otro sitio).
- `HomeViewModel` — **cerrado**: resuelve la fuente al entrar en modo pin, desde su propio estado.
- `HomeState` / `HomeStateTransitions` / `HomeSlices` — **cerrado**: el campo pasa a `String?` y el
  booleano de UI se deriva en el slice.
- `HomePeekHandle` (oculta el stepper en modo nudge) — **exento**: consume el booleano del slice,
  que sigue existiendo y significando lo mismo.
- `EnrichParkingSessionWorker.PIN_TO_PIN_ELIGIBLE_PATHS` — **cerrado, y era el que mordía**:
  comparaba `detectionPath !in setOf(… "nudge")` por igualdad, así que el día que `nudge` compone,
  **todo pin de nudge se habría caído de la elegibilidad y se habría quedado sin ruta, en silencio**
  (una ruta que falta no peta: no se reporta). Ahora PARSEA con `DetectionPath.ofLabel` y el `when`
  es exhaustivo, así que un path nuevo no entra por defecto — tiene que clasificarse.
- `MainActivity` / `StartAddParkingEventBus` / `HomeSheetContent` — **exentos por convergencia**: el
  booleano «vengo de un nudge» sigue siendo su contrato; el QUIÉN no viaja por ahí a propósito.
- `AndroidDataStoreAppPreferences` / `IosAppPreferences` / `PendingParkNudge` — **exentos**: ya
  persistían `source`; este ticket sólo lo lee.
- `AppNotificationManager.showMarkParkingNudge` y sus call sites — **exentos**: ya fijaban su
  `source`; ninguno cambia.
- Room / DTO / mappers — **no aplica**: `detectionPath` ya existe y ya viaja a Firestore. Cero
  migraciones, cero cambios de serialización.
- Dev Catalog, strings, i18n — **no aplica**: ni pantalla, ni estado MVI, ni copy nuevo.

## Estado de ejecución (08-09-2026)

**2 452 tests verdes** (11 nuevos: composición y round-trip de la etiqueta, resolución de la etiqueta
histórica, las 6 fuentes reales que escriben los call sites, el sellado en el use case, el pin a mano
que sigue siendo `manual`, el sellado end-to-end desde el ViewModel, y 5 de elegibilidad pin-a-pin
con testigo de población). `:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin` OK.
