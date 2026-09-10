# UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001 · Barrido de `PapIconTile` tras el presupuesto de verde

**Estado:** ✅ Done — **cerrado sin cambios de código** · rama
`refactor/UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001-tile-sweep` · worktree
`../Paparcar-neutral-tile` · verificado en device el 10-09-2026

> Inventario y capturas hechos sobre master `b967323b`. Al cerrar, master ya llevaba `04f4747c`
> (Overpass): **no toca ni un fichero de UI** —solo `data/`, `di/` y sus tests—, así que el
> inventario de `PapIconTile` sigue siendo el mismo. Comprobado, no supuesto.

## Problema

`UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001` (`b967323b`) cambió el default de `PapIconTile` de
verde (`primaryContainer` + `primary`) a neutro (`surfaceContainerHighest` + `onSurfaceVariant`).
El cambio es correcto —un tile que era verde por defecto convertía en verde a todo el que usaba el
esqueleto canónico, y seis verdes en una pantalla no son un acento, son papel pintado— pero tiene
un riesgo conocido y ya materializado **una vez**:

> **Un tile que SÍ era una acción se queda gris y pasa a leerse como un control APAGADO.**

Ocurrió en Home con el megáfono de «avisar de una plaza» (`HomeSpotRows.kt:358`), cazado por el
user en device el 10-09 y arreglado en el propio `b967323b` pasándole el verde a mano. El pendiente
que abre este ticket es comprobar **los demás** sitios que se quedaron neutros, uno por uno.

## Doctrina que decide

`UI-COLOR-DOCTRINE-001` + `UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001`:

- Verde **solo** si el tile es (a) **una acción que puedes hacer AHORA** o (b) **la identidad de un
  coche vigilado**.
- Un glifo que solo **etiqueta** su fila —«Idioma», «Unidades», una gasolinera en una lista de
  gasolineras— es neutro. Ahí el cambio es una MEJORA, no una regresión.
- **Presupuesto: como mucho dos verdes por pantalla.**
- El estado se **escribe** en `onSurface` (o cambia de glifo), **nunca se tiñe**.

### La pregunta de diagnóstico que hay que hacerse (aprendida del megáfono)

No es *«¿la fila es pulsable?»* — casi todas lo son, y por ahí se vuelve al papel pintado. Es:

> **Tras el cambio, ¿esta fila se queda con CERO énfasis siendo lo que la pantalla te está pidiendo
> que hagas?**

El megáfono cumplía las dos: la tarjeta entera ES la acción, y su presupuesto de énfasis estaba
gastado en el tile + el `+`, no en un botón. Al volverse gris no le quedó nada. Una fila cuyo
énfasis vive en su **`trailing`** (un `Switch`, un `PapPrimaryButton`, un segmento seleccionado, un
chevron) **no se queda muda**: el énfasis es una variable de la fila, y esa fila ya la gastó en otro
sitio.

## Inventario regenerado sobre `b967323b`

19 apariciones de `PapIconTile(` (1 es la definición). **9 pasan `container`/`tint` explícitos** y
no los toca este ticket; **10 salen neutras** y son el sujeto del barrido.

### Los 10 neutros — veredicto

| # | Sitio | Qué es | ¿Acción? | Veredicto |
|---|---|---|---|---|
| 1 | `ui/components/PapSettingRows.kt:57` (`PapSwitchRow`) | Molde de fila con `Switch` | ❌ | **Neutro** — el énfasis es el `Switch`, que ya es verde al estar ON. Y el estado no se tiñe: se escribe. |
| 2 | `ui/components/PapSettingRows.kt:79` (`PapNavRow`) | Molde de fila que navega | ❌ | **Neutro** — navegar no es actuar; el afordance es el chevron. Es el molde de 6 filas: verde aquí = 6 verdes en Ajustes. |
| 3 | `ui/components/PapSettingRows.kt:98` (`PapInfoRow`) | Molde de fila de dato estático | ❌ | **Neutro** — no es pulsable siquiera. |
| 4 | `presentation/settings/SettingsScreen.kt:728` | `CheckCircle` de «detección sana» | ❌ | **Neutro** — es ESTADO, y el estado no se tiñe. Además su hermana ámbar (`:732`, con su botón «Arreglar») es la que tiene que destacar: si la sana también gritara, la que pide algo dejaría de distinguirse. |
| 5 | `presentation/settings/SettingsScreen.kt:809` (`ImprovementRow`) | Bluetooth / batería, «mejorar detección» | ❌ | **Neutro** — el énfasis vive en el `trailing`: «Configurado ✓» en verde, o «Configurar ›». Son 2 filas: verdes las dos = presupuesto gastado en lo declaradamente OPCIONAL («mejoras, nunca bloqueantes»). |
| 6 | `presentation/settings/SettingsScreen.kt:1000` (`SettingsSegmentedRow`) | Tema / idioma | ❌ | **Neutro** — etiqueta pura; el énfasis es el segmento seleccionado (`primaryContainer` + `primary`), justo debajo. |
| 7 | `presentation/onboarding/FirstStepExplainerScreen.kt:106` | Las «maneras» en que pasa el paso | ❌ | **Neutro** — filas de prosa NO interactivas. La pantalla ya gasta sus dos verdes: el hero de `:76` y el `PapPrimaryButton` fijo abajo. |
| 8 | `presentation/vehicleregistration/VehicleRegistrationScreen.kt:508` | Fila de emparejar Bluetooth | ❌ | **Neutro** — la fila lleva un `PapPrimaryButton` verde RELLENO en el `trailing`. Tile verde + botón verde = doble énfasis en una sola fila. |
| 9 | `presentation/home/.../HomeFuelStationRows.kt:59` | Gasolinera en la lista de gasolineras | ❌ | **Neutro** — etiqueta la fila; ya visto y validado en device. Verde aquí = N verdes, uno por resultado. |
| 10 | `presentation/home/.../HomePublicParkingRows.kt:55` | Parking público en su lista | ❌ | **Neutro** — igual que el anterior; ya visto en device. |

**Cambios de código: 0.** Ninguno de los diez era una acción muda.

### Los 9 que ya pasan color — verificados, no tocados

| Sitio | Color | Por qué es legítimo |
|---|---|---|
| `HomeSpotRows.kt:358` | `primaryContainer` + `PapColor.action` | El megáfono. La tarjeta ENTERA es la acción. Excepción documentada en el propio código. |
| `HomeDetectionSurface.kt:507` | el `tone` de la fila | La superficie de detección tiñe por tono (error = relleno); no toca el default. |
| `FirstStepExplainerScreen.kt:76` | `primary` + `onPrimary` | Hero del paso: el disco que el checklist le da a su paso ACTUAL. |
| `FirstStepsCard.kt:336` | `primary` + `onPrimary` | El paso actual — «la única fila que está pidiendo algo». |
| `FirstStepsCard.kt:359` (`CompleteRow`) | `primary` + `onPrimary` | Cierre del checklist. |
| `FirstStepsCard.kt:392` (`DeferredCloseRow`) | `primary` + `onPrimary` | Cierre con algo declinado. |
| `SettingsScreen.kt:732` | `secondaryContainer` + `PapColor.attention` | Salud de detección rota: hueco arreglable, con su CTA «Arreglar». |
| `CarbodyInfoCard.kt:206` | `primary` tenue + `primary` | Identidad de vehículo (moto). |

## Auditoría de los consumidores de los 3 moldes

Los tres moldes de `PapSettingRows` multiplican: un fallo ahí sale muchas veces. Enumerados **todos**
sus call sites para verificar que ninguno es la acción de su pantalla:

- `PapSwitchRow` ×3 — `SettingsScreen:484` (notif. aparcamiento), `:500` (alertas de zona),
  `:677` (**maestro de detección**), `VehicleRegistrationScreen:539` (mostrar marca en la plaza).
  Todos con `Switch` verde al estar ON. El maestro `:677` además **cambia de glifo**
  (`Sensors` / `SensorsOff`): es el ejemplo canónico de estado escrito, no teñido.
- `PapInfoRow` ×1 — `SettingsScreen:567` (versión de la app).
- `PapNavRow` ×6 — `SettingsScreen:574` (privacidad), `:586` (widget), `:593` (repetir primeros
  pasos), `:599` (licencias), `:605` (contacto), `:611` (enviar diagnósticos). Navegación con
  chevron; ninguna es «lo que la pantalla te pide».

## Corrección al enunciado del pendiente

El pendiente original decía «Ajustes y la ficha de Vehículos». **`presentation/vehicles/` no usa
`PapIconTile` en ningún sitio.** La superficie real es Ajustes (vía `PapSettingRows`), el explainer
de onboarding y el **alta** de vehículo.

## Criterio de éxito

La tabla completa, con el porqué de cada veredicto, y verificación **en device** (que es donde se
cazó el megáfono) de las tres superficies: Ajustes entera, alta de vehículo y explainer del
checklist. Un barrido que concluye «no había nada» es un resultado válido siempre que se vea que se
miró.

## Verificación en device — 10-09-2026, emulador `sdk_gphone16k_x86_64` (Android 17), tema oscuro

Que ninguno fuera una acción hay que **verlo**, no deducirlo: el megáfono también parecía inocente
en el código. Las tres superficies, mirada directa:

1. **Ajustes entera** (app real, flavor mock, preset «Home (todo OK)»), tres pantallazos:
   - *Detección*: «Auto-detect parking» tile neutro + **switch verde ON** → la fila grita por el
     switch. «All set» tile neutro con su check: **queda callada, que es lo que se quiere** — su
     hermana ámbar es la que tiene que llamar. «Car Bluetooth» → *Set up ›*; «Unrestricted battery»
     → **«Configured ✓» en verde**.
   - *Apariencia y mapa*: Tema y Unidades con tile neutro y el **segmento seleccionado en verde**
     justo debajo. La etiqueta no compite con la elección.
   - *Acerca de*: **siete filas seguidas** (Versión, Privacidad, Widget, Repetir primeros pasos,
     Licencias, Contacto, Reportar un problema) con tile neutro + chevron. **Esta es la prueba
     visual del ticket anterior**: con el default viejo, esa tarjeta eran siete tiles verdes
     seguidos. Ninguna es «lo que la pantalla te pide».
2. **Alta/edición de vehículo** (galería, variante «Edición»): la fila «Pair Bluetooth» sale con
   tile neutro y un **botón «Pair» verde relleno** al lado. El énfasis está donde se pulsa; teñir
   también el tile sería decir dos veces lo mismo en una fila.
3. **Explicador del checklist** (galería, «Paso 2 · las 2 formas de liberar»): disco verde del paso
   arriba + botón «Got it» verde abajo = **exactamente dos verdes**. Las dos filas de «maneras»
   (*On its own* / *Or you say so*) salen neutras y leen como ilustración, no como controles
   apagados — no son pulsables.

Ninguna de las diez lee como un control apagado. **Cero cambios.**

## Comandos

- `./gradlew :shared:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL** (43 tareas ejecutadas;
  los guards Konsist —`ColorGuardrailTest`, `TypographyGuardrailTest`, `DividerGuardrailTest`—
  corrieron de verdad, no UP-TO-DATE).
- `./gradlew :app:assembleProdDebug :app:assembleMockDebug` → **BUILD SUCCESSFUL**.

## Hallazgo lateral — NO se toca aquí

La variante **«Edición» de la galería de `Registro de vehículo`** deja `vehicleType = null` (el
default del state), y `expectsCarbody` mira `vehicleType?.hasCarbody == true` → la pantalla cae en
`NonCarSizeBadge`, que pinta un **glifo de MOTO**. Resultado: un «Toyota Corolla · Medium» con icono
de motocicleta. La variante no ejercita su caso (`DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001`)
y lo mismo les pasa a «Edición · borrar con historial» y «Edición · aparcado». Fuera del alcance de
este barrido → ticket propio en `docs/backlog/dev-an-edit-variant-must-edit-a-car-001.md`.
