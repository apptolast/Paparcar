# UI-THE-PARKING-CONFIRMATION-MODAL-IS-UNREACHABLE-001 · La modal de confirmación de aparcamiento viola la regla de modales, y encima no la levanta nadie

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)

## Problema

Salió como consecuencia de la regla que dio el user el 03-09 — *«nunca abrimos modales encima de
modales»* — mientras se arreglaba el explainer del checklist guiado
[ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]. El barrido de infractores dejó uno pendiente:
`ConfirmationBottomSheet`, un `ModalBottomSheet` que se abre desde `HomeScreen` **encima de la sheet
de Home**, que ya es una superficie modal.

Al ir a documentarlo apareció algo mayor: **no se muestra nunca**. La cadena entera está muerta en
producción.

```
HomeIntent.ShowParkingConfirmation   ← ningún emisor fuera de los tests
        ↓
HomeState.pendingParkingGps          ← su ÚNICO escritor es el handler de ese intent
        ↓
ConfirmationBottomSheet              ← HomeScreen.kt:272, tras `state.pendingParkingGps?.let`
        ↓
HomeIntent.ConfirmDetectedParking → HomeViewModel.confirmDetectedParking()
        ↓
SaveManualParkingUseCase.confirmDetected()   ← sin otro llamador
```

Comprobado por grep sobre `shared/src` y `app/src`: `ShowParkingConfirmation` aparece en su
declaración, en el `when` del ViewModel y en `HomeViewModelTest`. **En ningún productor.** La
pregunta que esta modal existía para hacer la hace hoy otra superficie, por otro camino.

## Doctrina violada

- **⛔ Nunca una modal encima de una modal** (regla del user, 03-09; ver memoria
  `feedback_no_modal_over_modal`).
- **Código que no se renderiza no está "cubierto" porque tenga tests.** El proyecto ya se comió esta
  lección en `UI-TYPE-SYSTEM-HYGIENE-001`: una exención de tipografía protegía un componente MUERTO,
  y *una excepción sobre código que no se renderiza no es una excepción, es un agujero*. Aquí hay
  tests verdes (`should_set_pendingParkingGps_on_ShowParkingConfirmation` y compañía) que dan
  cobertura aparente a una superficie que ningún usuario puede ver.
- **Un timeout que actúa solo es una promesa seria.** Esta sheet lleva una cuenta atrás de 4 minutos
  que AUTO-CONFIRMA (`CONFIRMATION_TIMEOUT_SECONDS = 240`) y publica la plaza. Una máquina así, viva
  en el código pero inalcanzable, es exactamente el tipo de cosa que alguien reconecta por error
  dentro de seis meses sin saber lo que activa.

## Señales / datos disponibles

- Los dos únicos call sites: `HomeScreen.kt:272` (producción, inalcanzable) y
  `StateGalleryScreen.kt:624` (el catálogo mock, que la renderiza a mano — por eso el componente
  "se ve" y nadie notó que estaba muerto).
- `ConfirmationBottomSheet` usa el molde `PapSheet`, así que su copy y su anatomía son reutilizables
  si se decidiera revivirla en otra forma.
- Tests implicados: los de `ShowParkingConfirmation` / `DismissConfirmation` /
  `ConfirmDetectedParking` en `HomeViewModelTest`, y la variante del catálogo.

## Diseño

**Se borra la cadena entera.** Antes de tocar nada se comprobó lo único que podía impedirlo: que la
respuesta «Sí» de la pregunta real no compartiera camino. No lo comparte — va por
`manualParkingDetection.answerPrompt()` → `CoordinatorDetectionService.ACTION_PARKING_CONFIRMED`, el
servicio de detección, que estampa su propio path. La cadena de la modal es independiente y no la
dispara nadie.

Fuera: la sheet, sus tres intents, `pendingParkingGps`, `confirmDetectedParking()` en el VM, la rama
`confirmDetected()` del use case, sus tests, la variante del catálogo mock y **las 8 keys de copy en
los 9 locales**.

⛔ Lo que NO se hizo, y sigue siendo lo correcto: dejarla viva "por si acaso" moviéndola dentro de la
sheet. Habría sido mantener una máquina con un auto-publish de 4 minutos que nadie dispara.

### Lo que destapó la limpieza

- **El guardarraíl de locales encontró el resto que yo habría dejado.**
  `LocaleParityGuardrailTest > every declared string is read by something` falló con las 8 keys de la
  modal huérfanas. Borrar una superficie no es borrar su Kotlin: borrar limpio incluye su copy, y
  aquí lo dijo un test en vez de descubrirse dentro de un año.
- ⚠️ **`DetectionPath.UserAnswered` se queda sin quien lo estampe.** Era `PATH_USER` en
  `SaveManualParkingUseCase`, y ese era su único productor. El path sigue en el vocabulario de
  diagnóstico con su test, pero hoy responder «Sí» estampa OTRO path (el del servicio). No es asunto
  de este ticket, pero es un dato para quien mire trazas: buscar `user_answered` no encontrará las
  respuestas del usuario.

## Criterio de éxito

- Cero `ModalBottomSheet` abiertos desde Home sobre su propia sheet.
- Si se borra: no queda ni un símbolo de la cadena, y el catálogo mock deja de renderizar un
  componente que el producto no tiene (regla: la galería refleja lo que existe).
- Si se revive: hay un emisor real, con su test, y la superficie vive DENTRO de la sheet.

## Consumidores auditados

| Símbolo | Dónde | Estado |
|---|---|---|
| `ShowParkingConfirmation` | HomeIntent + VM + HomeViewModelTest | sin emisor de producción |
| `pendingParkingGps` | HomeState, VM (3 usos), HomeScreen:272 | escrito solo por el intent anterior |
| `ConfirmationBottomSheet` | ui/components + HomeScreen + StateGalleryScreen | inalcanzable en la app |
| `ConfirmDetectedParking` | HomeIntent + VM | solo desde la modal inalcanzable |
| `SaveManualParkingUseCase.confirmDetected()` | domain/usecase/parking | sin otro llamador |
