# Prompt para el barrido de tiles tras el presupuesto de verde

> Copiar de la línea de abajo hasta el final y pegarlo tal cual en una sesión nueva.
> Autocontenido: no hace falta contexto previo.

---

Haz el barrido de `PapIconTile` que dejó pendiente
`UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001` (`b967323b`, en master). Abre ticket con la skill
**`nuevo-ticket`** (worktree aislado + rama + doc en `docs/backlog/`) y llámalo
`UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001`.
⛔ No commitees ni mergees sin permiso explícito de ese turno.

## Qué pasó y qué hay que comprobar

`PapIconTile` era **verde por defecto** (`primaryContainer` + `primary`), así que **todo** el que
usaba el tile canónico salía verde y el verde dejó de significar nada. El default pasó a **neutro**
(`surfaceContainerHighest` + `onSurfaceVariant`) y el verde ahora se pide explícitamente.

El riesgo conocido, y la razón de este ticket: **un tile que SÍ era una acción se quedó gris y ahora
lee como un control apagado.** Ya pasó una vez en Home — el megáfono de «avisar de una plaza», que el
user cazó en device — y se arregló pasándole el verde a mano. Falta comprobar los demás.

## La regla que decide, caso por caso

Verde solo si el tile es **una acción que puedes hacer ahora** o **la identidad de un coche
vigilado** (`UI-COLOR-DOCTRINE-001`). Un glifo que solo **etiqueta** su fila —el icono de «Idioma»,
«Unidades», una gasolinera en una lista de gasolineras— es neutro, y ahí el cambio es una mejora, no
una regresión. ⛔ Ojo con el reflejo de «esto se ve apagado, le pongo verde»: si todo vuelve a ser
verde, volvemos al problema que este sistema arregló.

## Los 10 sitios que hoy salen neutros (inventariados sobre `b967323b`)

Sospechosos de verdad — filas de Ajustes y flujos de alta:

- `presentation/settings/SettingsScreen.kt:728`
- `presentation/settings/SettingsScreen.kt:809`
- `presentation/settings/SettingsScreen.kt:1000`
- `ui/components/PapSettingRows.kt:57` · `:79` · `:98` ← **estos tres multiplican**: son el molde que
  usan las filas de Ajustes, así que un fallo aquí sale muchas veces en pantalla
- `presentation/onboarding/FirstStepExplainerScreen.kt:106`
- `presentation/vehicleregistration/VehicleRegistrationScreen.kt:508`

Ya vistos en device y correctos (son etiquetas de fila, no acciones) — no los toques sin argumento:

- `presentation/home/sections/sheet/components/HomeFuelStationRows.kt:59`
- `presentation/home/sections/sheet/components/HomePublicParkingRows.kt:55`

**No afectados** (pasan su propio color y siguen mandando ellos): `HomeDetectionSurface:507`,
`HomeSpotRows:358` (el megáfono ya arreglado), `FirstStepExplainerScreen:76`, `FirstStepsCard:336
:359 :392`, `SettingsScreen:732`, `CarbodyInfoCard:206`.

⚠️ **Corrección al enunciado original del pendiente**: decía «Ajustes y la ficha de Vehículos», pero
`presentation/vehicles/` **no usa `PapIconTile` en ningún sitio**. La superficie real es Ajustes (vía
`PapSettingRows`), el explainer de onboarding y el **alta** de vehículo. Verifícalo tú mismo con el
inventario en vez de fiarte de esta lista si ha pasado tiempo.

## Cómo hacerlo

1. Regenera el inventario (no confíes en la lista de arriba si master ha avanzado): busca las
   llamadas a `PapIconTile(` y separa las que pasan `container`/`tint` de las que no.
2. Para cada una de las neutras, decide con la regla de arriba: **¿es una acción?** Si sí, pásale
   `container = MaterialTheme.colorScheme.primaryContainer` y `tint = PapColor.action`, y **escribe
   en el código POR QUÉ** esa es una excepción del presupuesto.
3. **Míralo en device**, que es donde se cazó el megáfono: Ajustes entero, el alta de vehículo y el
   explainer del checklist. Una captura por pantalla tocada.

## Reglas que no puedes romper

- ⛔ **Presupuesto de verde: como mucho dos por pantalla.** Si una pantalla acaba con tres tiles
  verdes, la regla la estás rompiendo tú, no el ticket anterior.
- ⛔ El **ámbar es solo caducidad**; `PapRed` **nunca** en un CTA.
- ⛔ Un solo esqueleto de fila (`PapListItem` en `PapOutlinedCard` con `PapIconTile`); el énfasis es
  una variable (relleno / borde / nada), no un contenedor nuevo. Cuatro radios: `row 12` · `card 16` ·
  `sheet 20` · `chip 999`.
- ⛔ Nada de `Color(0x…)` literal ni `colorScheme.tertiary` en `presentation/` ni `ui/components/`
  (lo vigila `ColorGuardrailTest`).
- ⛔ Si tocas pantalla o estado, **Dev Catalog en la misma tarea**, y la variante tiene que
  **ejercitar su caso** (`DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001`).

## Verificación

```bash
./gradlew :shared:testDebugUnitTest --rerun-tasks   # ⛔ sin --rerun-tasks los guards Konsist salen UP-TO-DATE sin ejecutarse
./gradlew :app:assembleProdDebug :app:assembleMockDebug
```

## Qué me tienes que decir al terminar

La **tabla completa**: cada sitio, qué decidiste (acción → verde / etiqueta → neutro) y por qué. Ese
es el entregable — el ticket puede cerrarse legítimamente **sin cambiar una sola línea** si resulta
que ninguno de los diez era una acción. Un barrido que concluye «no había nada» es un resultado,
siempre que se vea que se miró.
