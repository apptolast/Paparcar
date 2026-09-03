# ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001 · El checklist guiado se parte letra a letra y solo existe si el usuario levanta la sheet

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)
> Cerrado DENTRO del commit de `ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001`:
> los tres tickets del checklist tocaban la misma tarjeta y la misma proyección, y separarlos en
> tres commits solo producía conflictos artificiales.

## Problema

Captura del user en device (03-09, Oppo, locale ES) del checklist recién mergeado
[ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]. Dos fallos, ambos lo dejan inservible:

**A · El texto del paso actual se parte carácter a carácter.** El título «Marca dónde has dejado el
coche» sale como `Marc / a / dónd / e has / dejad / o el / coch / e`, y el subtítulo igual. La fila
mide, en la sheet, ~263 dp de contenido:

| Pieza | Ancho |
|---|---|
| `PapIconTile` del paso actual | 40 dp |
| gap por defecto de `PapListItem` (×2) | 28 dp |
| `PapPrimaryButton(Compact)` con «Marcar aparcamiento» | ~190 dp |
| **queda para título + subtítulo** | **~5 dp** |

`PapListItem` da `Modifier.weight(1f)` a la columna de texto, pero en un `Row` de Compose los hijos
**sin** weight se miden ANTES y con la anchura máxima disponible: el botón se sirve primero y la
columna se queda con las sobras. Con una etiqueta de una palabra («Pair», «Fix», «Done») sobra
sitio; con la de este paso no queda ninguno.

No es un caso raro de un locale: el CTA del paso 1 tiene **2–3 palabras en los 9 locales**
(`Marcar aparcamiento` · `Marquer le stationnement` · `Parkeerplaats markeren` · `Zaznacz
parkowanie`…), y el del paso 3 igual (`Ver plazas`, `Plätze ansehen`).

**B · Nadie sabe que hay tutorial.** El checklist es el primer item de la LISTA de la sheet, y la
sheet arranca en `peek` — donde solo se ve la cabecera de dirección. Un usuario nuevo tiene que
arrastrar la sheet hacia arriba por su cuenta para descubrir que existe una guía de primeros pasos.
La guía que existe para que no se pierda es, ella misma, lo que hay que encontrar.

## Doctrina violada

- **A** rompe el contrato implícito de `PapListItem` [UI-LIST-ITEM-001]: `trailing` es un afijo
  compacto (glifo, switch, badge, píldora de UNA palabra), no una frase. `PermissionRow` ya lo había
  descubierto en su día y lo dejó escrito — *«Sustituye al chip de texto para no robar ancho a la
  descripción en pantallas estrechas»* — pero la regla se quedó en un comentario de un call site en
  vez de en el contrato del componente, así que el siguiente consumidor volvió a pisarla.
- **B** repite exactamente lo que `DET-ASK-STATE-001` cerró para la pregunta «¿has aparcado?»: *«la
  respuesta es lo único que la app necesita y hacérsela descubrir arrastrando es el mismo "te lo
  preguntamos donde no estabas mirando" que el ticket existe para acabar»*. La sheet YA se abre sola
  por una pregunta pendiente; el checklist es el otro caso con el mismo derecho y no lo hacía.

## Señales / datos disponibles

- `SheetTransitionEffects` ya tiene el mecanismo exacto: `LaunchedEffect(promptShownAtMs)` →
  `sheetOffsetPx.animateTo(positioning.expandedOffsetPx)`. Abre una vez por PREGUNTA, no por
  booleano, para que arrastrar la sheet hacia abajo aguante.
- `HomeBrowseListSlice.firstSteps` ya viaja con el estado, y `FirstStepsProgress.current` **solo
  avanza**: el latch persistido (`subscribeFirstSteps`) hace que un paso nunca se des-complete, así
  que usarlo como clave no puede oscilar.
- El gate «¿se ve el checklist?» (`isVisible && hasCorePermissions && vehicleCards.isNotEmpty()`)
  vivía suelto dentro de `homeSheetItems`. Para que la apertura automática y la propia tarjeta no
  puedan discrepar, tiene que resolverse **una vez**.

## Diseño

**A · El CTA del paso baja a su propia línea.** En `FirstStepsCard.StepRow` el botón deja de ser
`trailing` y se pinta bajo la fila, alineado con la columna de texto (16 + 40 + 14 = 70 dp de
sangría). Así el título y el subtítulo cobran el ancho entero de la tarjeta, que es lo que necesita
una fila cuyo contenido es una FRASE y no una etiqueta.

Y la regla sube al sitio donde vive el invariante: el KDoc de `PapListItem.trailing` dice qué cabe
ahí y qué no, con el porqué medido (los hijos sin weight se miden primero). Un comentario en un call
site no protege al siguiente call site.

**B · La sheet se abre sola por el checklist, una vez por paso.**
`HomeBrowseListSlice.showsFirstSteps` centraliza el gate y `SheetTransitionEffects` gana
`firstStepAnchor: FirstStep?` — el paso actual mientras el checklist se ve, null si no. Clave del
`LaunchedEffect`, igual que la pregunta: se abre al entrar en Home con un paso pendiente y se vuelve
a abrir cuando el paso AVANZA (que es justo cuando hay algo nuevo que enseñar), y arrastrarla abajo
aguanta hasta que cambie el paso. En modos de pin / con algo seleccionado la geometría capa
`expandedOffsetPx` en `peek`, así que se resuelve en no-op sin secuestrar esas superficies — la
misma propiedad que ya protege a la pregunta.

Trade-off asumido y NO persistido: volver a Home desde otra pestaña recompone y vuelve a abrir. Se
acepta porque el checklist solo existe mientras el usuario tenga primeros pasos pendientes y tiene
su propio «Saltar»; persistir un flag «ya te la abrí» sería una preferencia nueva (más superficie en
`AppPreferences`, fakes e iOS) para un caso acotado a tres aperturas.

## Criterio de éxito

- El paso actual se lee: título en una o dos líneas, sin partir palabras, en los 9 locales.
- Un usuario nuevo que llega a Home ve el checklist sin tocar nada.
- Tests verdes (`:shared:testDebugUnitTest`) y `assembleMockDebug` sin romper prod.
- Verificado en device con `/run` (es un bug de layout: la galería no lo demuestra sola).

## Consumidores auditados

Todos los `PapListItem` con botón/texto en `trailing`, contra la longitud real de su etiqueta en los
9 locales:

| Call site | Etiqueta | Veredicto |
|---|---|---|
| `FirstStepsCard.StepRow` (pasos 1 y 3) | 2–3 palabras en los 9 locales | 🔴 **el bug** → CTA a su propia línea |
| `FirstStepsCard.CompleteRow` | `Done`/`Hecho`/`Gata`… 1 palabra | ✅ se queda en `trailing` |
| `VehicleRegistrationScreen` (BT) | `Pair`/`Vincular`/`Sparuj`… 1 palabra | ✅ se queda |
| `SettingsScreen` (fiabilidad) | `Fix`/`Arreglar`/`Napraw`… 1 palabra | ✅ se queda |
| `PermissionRow` | ya usa glifo; el botón vive fuera de la fila | ✅ precedente del que sale la regla |
| `PapSettingRows` (switch · valor · chevron) | afijos compactos | ✅ |
| `HomeSpotRows`, `VehiclePageContent` | glifos | ✅ |

Gate del checklist — un solo sitio tras el cambio: `HomeBrowseListSlice.showsFirstSteps`, consumido
por `homeSheetItems` (render + `firstStepsOwnsColdStart`) y por `HomeScreen` (apertura automática).
