# ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001 · El onboarding cuenta la app en tres pantallas y luego suelta al usuario en un mapa vacío

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)
> ⏳ Queda verlo en hardware: el foco sobre el pin usa `BlendMode.Clear` sobre capa offscreen y la
> galería no tiene tiles de mapa debajo. Todo lo demás está verificado por tests.

## Problema

Un usuario nuevo pasa por `OnboardingScreen` — tres páginas de pager **estáticas** (bienvenida ·
cómo funciona · privacidad) — antes de conceder permisos y antes de pisar Home. Cuando llega a
Home ya no queda nada de eso en pantalla: ha **leído** cómo funciona la app sin haber **hecho**
nada. El primer gesto real del producto (marcar dónde ha dejado el coche) es también el que arma
toda la detección, y hoy solo lo pide una fila entre otras.

Lo que hay hoy y por qué no basta:

| Pieza | Qué hace | Por qué no es un tutorial |
|---|---|---|
| `OnboardingScreen.kt` | 3 páginas de pager, pre-permisos | Estático, se ve una vez, no toca la UI real |
| `DetectionStory.AwaitingFirstPark` | Fila "Marca tu aparcamiento" en Home | Una fila suelta; no tiene continuidad ni siguiente paso |
| `EvaluateFirstParkNudgeUseCase` | Notificación de arranque en frío | Es un recordatorio, no una enseñanza; y se auto-desactiva |

No hay ninguna infraestructura de coach-marks / foco / spotlight en el repo (`grep -ri
'coachmark|spotlight|walkthrough'` → 0 resultados en `shared/src`).

## Doctrina violada

Ninguna regla existente lo prohíbe — es un hueco de producto. Pero el diseño **choca** con dos
reglas que sí existen, y de ahí salen las dos decisiones no obvias de este ticket:

1. **Una sola voz en la superficie de detección** (`UX-DETECTION-STORY-001`, endurecida por
   `DET-ASK-STATE-001`). Un checklist que diga "marca tu aparcamiento" **al lado** de la fila
   `AwaitingFirstPark`, que dice literalmente eso, son dos voces. Y `DET-ASK-STATE-001` ya corrigió
   una vez el reflejo de arbitrar con un `if` dentro del composable: la precedencia vive en la
   proyección pura y en ningún otro sitio.
2. **PLAZA es de la comunidad, APARCAMIENTO es tuyo** (`COPY-SPOT-IS-NOT-A-PARKING-001`). El paso 1
   marca un APARCAMIENTO. La PLAZA no aparece hasta que el usuario se va. El copy del tutorial no
   puede mezclarlas — es el sitio donde más fácil sería hacerlo.

## Señales / datos disponibles

Ya existe todo lo necesario para medir el progreso sin inventar telemetría:

- `AppPreferences.hasConfirmedFirstPark` — se estampa en `ConfirmParkingUseCase.kt:400`.
  ⚠️ Es un `val` **no reactivo** (se lee una vez) y **no se puede reutilizar como flag del
  tutorial**: lo consume `FirstParkNudgeWorker` vía `isFirstParkNudgeSpent`, así que resetearlo al
  "repetir tutorial" reactivaría la notificación de arranque en frío. Ver *Consumidores auditados*.
- `HomeState.activeSessions` — hay sesión de aparcamiento activa o no.
- `DetectionStory.Watching` — la app está vigilando de verdad (badge honesto, `DET-WATCH-HONEST-001`).
- `HomeSelection.Spot` / `HomeSheetAction.RequestReportMode` — el usuario ha tocado el mundo comunitario.

## Diseño

**El invariante: el progreso del tutorial es un ESTADO DERIVADO del producto, no una secuencia de
pantallas.** Un paso no se marca al pulsar su botón; se marca cuando el estado real lo satisface.
Es el mismo principio que la doctrina de detección — *el evento NOMINA, el estado MEDIDO confirma* —
aplicado al onboarding: pulsar "Marcar aparcamiento" no enseña nada; tener el coche marcado sí.

### 1 · Progreso — proyección pura

`presentation/onboarding/FirstSteps.kt`, mismo patrón y mismo sitio conceptual que
`DetectionStory.kt` + `resolveDetectionStory`:

```kotlin
enum class FirstStep { MARK_PARKING, UNDERSTAND_WATCH, FIND_SPOT }

fun resolveFirstSteps(
    done: Set<FirstStep>,
    dismissed: Boolean,
    hasActiveSession: Boolean,   // MARK_PARKING
    isWatching: Boolean,         // UNDERSTAND_WATCH
    hasTouchedSpots: Boolean,    // FIND_SPOT
): FirstStepsProgress
```

Puro, sin Compose, con su test unitario. **Un paso avanza por SEÑAL REAL**, y el resolver es el
único sitio que decide cuál es el paso actual.

### 2 · Persistencia — flags PROPIOS, nunca los de detección

`AppPreferences` gana `firstStepsDone: Set<FirstStep>` + `firstStepsDismissed: Boolean`, ambos
reactivos (`observeFirstSteps()`). **Deliberadamente separados de `hasConfirmedFirstPark`**: repetir
el tutorial limpia los flags del tutorial y no toca ni un bit de la máquina de detección.

### 3 · Una voz, no dos

`resolveDetectionStory` gana un parámetro `firstStepsOwnsColdStart: Boolean = false` que convierte
`AwaitingFirstPark` en `Hidden`. La regla queda escrita en un solo sitio y testeada:
*mientras el tutorial está en el paso "marca tu aparcamiento", él es la voz; la fila de arranque en
frío calla.* Va en la proyección, **no** en `homeSheetItems` — el `if` en el composable es
exactamente lo que `DET-ASK-STATE-001` prohibió.

### 4 · El foco sobre el pin

`ui/components/PapSpotlight.kt` — scrim con recorte (`BlendMode.Clear` sobre una capa offscreen) +
caption. Se ancla al **centro del área de mapa**, que es una posición geométrica conocida, no a un
control que se mueve: la bottom sheet cambia de altura constantemente y anclar ahí es donde este
tipo de overlay se rompe. Solo se dibuja en `HomeMode.AddingParking` y solo mientras el paso actual
es `MARK_PARKING`.

### 5 · Repetible desde Ajustes

Fila en Ajustes (`Icons.Rounded.School`, encima de las legales) → `SettingsIntent.RestartFirstSteps`
→ limpia los DOS flags del tutorial y **nada más**, confirmado con snackbar porque el resultado está
en otra pantalla.

### Dos decisiones que cambiaron al implementar

**El paso 2 no lleva CTA.** Se diseñó con botón y se quitó al escribirlo: no hay nada que pulsar. O
el coche está vigilado — y la línea honesta de la superficie de detección, justo debajo, lo dice, que
es lo que completa el paso — o la vigilancia está caída, y esa misma superficie ya es dueña del
botón "Reactivar" / "Activar". Un botón aquí habría sido una segunda voz para una sola acción: la
misma falta que este ticket suprime una fila más abajo. Se retiró la key `first_steps_watch_cta` de
los 9 locales.

**El paso 3 se banca al PUBLICAR, no al entrar en el modo.** Su CTA entra en `RequestReportMode`,
pero el latch se escribe en el `onSuccess` de `confirmReportSpot`. Bancarlo al pulsar habría
contradicho la tesis del ticket en su propio código.

### Nota de despliegue

`first_steps_dismissed` ausente = `false`, así que **el checklist aparece también a los usuarios ya
instalados** en la primera apertura tras actualizar. Es deliberado y es inofensivo pre-lanzamiento:
si ya tienen coche aparcado y vigilado, los pasos 1 y 2 se bancan solos y el checklist arranca
directamente en "2 de 3". El default de `HomeState.firstStepsDismissed` sí es `true`, para que no
haya un frame de tutorial antes de que resuelvan las preferencias.

## Criterio de éxito

- `FirstStepsTest` (commonTest): el paso avanza por señal real, no por tap; `dismissed` gana sobre
  todo; el resolver es estable ante reordenaciones del set.
- `DetectionStoryTest`: nuevo caso — `AwaitingFirstPark` + `firstStepsOwnsColdStart = true` → `Hidden`,
  y el resto de historias **no** se ven afectadas por el flag.
- Guardarraíles verdes: `LocaleParityGuardrailTest`, `TypographyGuardrailTest`, `ColorGuardrailTest`,
  `ButtonGuardrailTest`, `DividerGuardrailTest`, `ImportGuardrailTest`.
- `assembleMockDebug` + `assembleProdDebug` compilan; galería de estados con el checklist en sus
  tres posiciones.
- En device: instalación limpia → el checklist aparece en Home; marcar aparcamiento lo avanza solo.

## Consumidores auditados

> Barrido de todo lo que asume que "primer aparcamiento" es un solo concepto.

| Consumidor | Asume | Estado |
|---|---|---|
| `FirstParkNudgeWorker.kt:54` → `isFirstParkNudgeSpent` | `hasConfirmedFirstPark` gobierna la notificación de arranque en frío | **Cubierto**: el tutorial no toca ese flag; usa `firstStepsDone` |
| `ConfirmParkingUseCase.kt:400` | Estampa `setHasConfirmedFirstPark()` | Intacto — sigue siendo la única escritura |
| `resolveDetectionStory` / `DetectionUiState.AwaitingFirstPark` | Es la única voz del arranque en frío | **Cerrado**: parámetro explícito de supresión + test |
| `homeSheetItems` (`detection_surface`) | La fila de detección es el primer item de la lista | **Cerrado**: el checklist se emite encima, y las dos no coexisten |
| `IosAppPreferences` / `FakeAppPreferences` (×2: commonMain fakes + commonTest) | Implementan `AppPreferences` completo | **Cerrado**: las 4 implementaciones (Android DataStore, iOS `NSUserDefaults`, fake mock, fake test) llevan las keys nuevas; los nombres desconocidos se DESCARTAN al leer, no crashean |
| `MockScenario` + `DevCatalogScreen` + `StateGalleryScreen` | Cubren pantallas y estados nuevos | **Cerrado**: `firstStepsPending` + switch + preset "Usuario nuevo", y 2 grupos de galería (5 posiciones del checklist + el foco) en paridad con `FirstStepsPreviews.kt` |

## Estado de verificación (03-09)

```
:shared:testDebugUnitTest --rerun-tasks  → 2174 tests · 0 failures · 0 errors
  FirstStepsTest 8 · DetectionStoryTest 21 (4 nuevos)
  LocaleParity 5 · Typography 5 · Color 6 · Button 1 · Import 1 · Divider 1 · HomeSlice 1 — verdes
:app:compileMockDebugKotlin :app:compileProdDebugKotlin → BUILD SUCCESSFUL
strings: 16 keys nuevas × 9 locales, UTF-8 verificado, apóstrofos CRUDOS
```

⏳ **Falta verlo en hardware** (`/run`): el foco sobre el pin es lo único que no puede darse por
bueno sin mirarlo — el recorte con `BlendMode.Clear` depende de la capa offscreen y la galería no
tiene tiles de mapa debajo.

## Fuera de alcance (→ ticket propio)

Explicar las **dos formas de que nazca una plaza** (tu aparcamiento al irte — automático o con el
"me voy" de la modal — frente a avisar de una plaza ajena que has visto):
`ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001`, ramificado de esta rama porque toca sus mismos ficheros.
