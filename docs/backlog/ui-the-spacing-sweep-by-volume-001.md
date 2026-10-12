# UI-THE-SPACING-SWEEP-BY-VOLUME-001 · El barrido pantalla a pantalla que #137 dejó fuera

**Estado:** ✅ Done (12-10-2026) · rama `refactor/UI-THE-SPACING-SWEEP-BY-VOLUME-001-spacing-volume` ·
cierra [#137](https://github.com/apptolast/Paparcar/issues/137), que
[`ui-the-spacing-sweep-is-screen-by-screen-001`](ui-the-spacing-sweep-is-screen-by-screen-001.md)
dejó parcial; mitad 2 de #134

## Problema

#137 entregó el sistema (la ley del hueco visible, `TouchTargetGuardrailTest`) pero dejó fuera, a
propósito, dos cosas:

1. **El barrido por volumen**: aplicar el sistema pantalla a pantalla. Medido al abrir (master
   `0d69b7c09`): `SettingsScreen` 30 literales, `PaparcarMapMarkers` 29, `VehicleRegistrationScreen`
   24, `HistoryContent` 22, `ParkingHistoryDetailScreen` 21, `HomeSearchBar` 14,
   `BluetoothConfigScreen` 14.
2. **El 4º chequeo de `SpacingGuardrailTest`** (aplazado ya en #136): ningún `Modifier.size(<literal>.dp)`
   inline. **30 violaciones vivas** al abrir este ticket.

## Doctrina

Toda la de #136 y #137 sigue vigente sin re-discutir:

- El alto y el aire de un control son del ROL, no del contenido ni del call site [#136].
- Un hueco visible no es su constante: `PapGap.betweenControls(visible, controlHeight, sides)`
  descuenta el overhang del mínimo táctil [#137].
- ⛔ **Un literal no es un defecto por ser literal.** Las cinco familias de #136 siguen vigentes: el
  `strokeWidth` de un spinner y el radio de una celda no son spacing. Cada cambio dice **qué se ve
  distinto, o por qué el valor ya tenía dueño**.
- ⛔ **No es un rediseño.** Identidad, colores, tipografía y estilo de card se mantienen.
- **Una PR con resultado visual se enseña**: antes/después por pantalla que se mueva, del estado
  FINAL de la rama [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## Señales / datos disponibles

- `SpacingGuardrailTest` (#136, 3 chequeos) y `TouchTargetGuardrailTest` (#137, 2 chequeos), los 5 en
  verde al abrir.
- 30 `Modifier.size(<literal>.dp)` que el 4º chequeo cazará.
- Dev Catalog + galería de estados + emulador Pixel 8.
- ⛔ `testAll` no corre entero en esta máquina (klib de iOS tapado por Windows) — medir con
  `:shared:testDebugUnitTest :app:testMockDebugUnitTest`.

## Diseño

1. Barrer fichero a fichero por volumen, clasificando cada literal por familia (gap · alto de
   control · icono · trazo · radio) y enrutándolo a su token, o dejándolo con su razón escrita si no
   tiene dueño.
2. Cerrar el 4º chequeo de `SpacingGuardrailTest` cuando los 30 inline lleguen a cero.
3. Capturas antes/después de cada pantalla que se mueva visualmente.

## Criterio de éxito

1. Los ficheros de mayor volumen barridos, con su razón si algo se queda fuera.
2. 4º chequeo de `SpacingGuardrailTest` activo y falsificado con `--rerun-tasks`.
3. Capturas en `~/Pictures/paparcar-137/` (misma carpeta que el ticket que continúa).
4. `:shared:testDebugUnitTest :app:testMockDebugUnitTest` en verde.

## Consumidores auditados

| fichero | literales | resultado |
|---|---|---|
| `SettingsScreen.kt` | **30 → 0 sin dueño** | 23 sustituciones exactas a token/constante nombrada; 7 nombrados sin cambiar su valor (visibles, anotados) |
| `PaparcarMapMarkers.kt` | **29 → 0 sin dueño** | ⚠️ 26 ya eran correctos — geometría de ILUSTRACIÓN de marcador (Nivel 3), no spacing de interfaz; sólo 3 sin nombrar, nombrados |
| `VehicleRegistrationScreen.kt` | **24 → 0 sin dueño** | 21 sustituciones exactas (`spacedBy(12)`→`md` ×3, `spacedBy(8)`→`sm` ×10, dos `Spacer` de altura); 3 nombrados sin cambiar valor (14/6/6, cada uno único) |
| `ParkingHistoryDetailScreen.kt` | **22 → 0 sin dueño** | 15 sustituciones exactas (incluye reuso de `PapShapes.cell`/`PapBorders.strong` en vez de `6.dp`/`2.dp` sueltos); 2 píldoras idénticas nombradas (estaban repetidas verbatim); 6 dimensiones de esqueleto (alto/radio) nombradas sin pedir prestado a `PaparcarSpacing` |
| `HistoryActiveCard.kt` | **2 → 0** | geometría de animación ACOPLADA (caja = núcleo + expansión), nombrada como tal |
| `HistoryComponents.kt` | **3 → 0** | 2 exactas + 1 nombrada |
| `AddingZonePeek.kt` | **7 → 0** | 6 exactas + **1 área de toque corregida**: el selector de icono de zona medía 40 dp, subido a 48 |
| `VehiclesScreen.kt` | **8 → 0** | 5 exactas + 3 nombradas (hero vacío) |
| `CarbodyInfoCard.kt` | **12 → 0** | 7 exactas (incluye 2 reusos de `PapBorders.thin`) + 5 nombradas |
| `CarbodyManualPicker.kt` | **9 → 0** | 6 exactas (incluye reuso de `PapBorders.medium` para el borde seleccionado, 1,5 dp) + 3 nombradas |
| `PapAlertDialog.kt` | **12 → 0** | 8 exactas + 1 nombrada única + **un hallazgo aplicado** (ver abajo) |
| `BluetoothConfigScreen.kt` | **14 → 0** | 10 exactas + **1 hallazgo aplicado**: icono+gap de un botón pasó de 16/6 a 18/8 (mismo defecto que #136 y Settings) + 3 nombradas |
| `HomeSearchBar.kt` | **14 → 0** | 10 exactas + 3 nombradas (2 de ellas, duplicadas verbatim en dos filas) |
| `HomeSpotRows.kt` | **11 → 0** | 5 exactas + 2 recetas duplicadas nombradas + 3 nombradas únicas |
| `BrowsePeek.kt` | **10 → 0** | 2 exactas (incluido reuso de `PapGap.block`) + 8 dimensiones de esqueleto nombradas, misma categoría que `HistoryContent`/`ParkingHistoryDetailScreen` |

### `SettingsScreen.kt` · los 30, uno a uno

- **8 sustituciones zero-visual** por coincidencia exacta con `PaparcarSpacing` (4·8·12·16·20·24·32·48):
  `Spacer(8dp)`, `padding(vertical=16dp)`, `padding(horizontal=16dp)` ×2, `Spacer(12dp)`,
  `Spacer(16dp)` ×2, `spacedBy(4dp)`→`xs`, cabecera `MiniHeader` (16/16/12/4 → lg/lg/md/xs).
- **2 `height(48.dp)`** (Logout, Delete) → `PapControlHeight.button`: son botones reales, 48 ya era
  el mínimo táctil — el token sólo nombra lo que ya era cierto.
- **1 bloque repetido 2 veces, nombrado**: el padding de cabecera muda/error (12/2/4 dp) — antes
  copiado verbatim en `SectionHeaderMuted` y `SectionHeaderDanger`, ahora `SECTION_HEADER_*_DP`.
- **2 roles repetidos 2 veces cada uno, nombrados**: el aire texto→marca de confirmación (4 dp,
  `TRAILING_CHECK_GAP_DP`) y texto→flecha (2 dp, `TRAILING_CHEVRON_GAP_DP`) — dos parejas idénticas
  en `EmailVerificationTrailing` y `SetupStatusTrailing` que no se conocían entre sí.
- **7 literales únicos, nombrados sin tocar su número**: el botón «Fix» (padding/icono/gap) y el par
  icono+gap de los botones destructivos (Logout, Delete ×2) — ver el hallazgo de abajo.
- **1 literal con su razón YA escrita, intacto**: el sello de 16 dp del correo verificado
  («16 dp: la altura de la línea del correo») — no es spacing disperso, es una medida deliberada.

### ✅ Hallazgo aplicado (decisión del user, 09-10): Logout/Delete pasan a 18/8

`DESTRUCTIVE_BUTTON_ICON_DP` y `DESTRUCTIVE_BUTTON_ICON_GAP_DP` eran la MISMA pareja icono+gap
(16/6), repetida en los dos botones de pie de esta pantalla, y ninguno de los dos coincidía con los
tokens del sistema para «icono junto a etiqueta»: `PapIconSize.withLabel` (18) y `PapGap.iconLabel`
(8). Mismo defecto que #136 ya corrigió una vez en `PapFooterButton` (gap 10 vs 8) — ahora con
Logout y Delete.

Capturado en device antes/después del botón «Delete account»
(`~/Pictures/paparcar-137/cmp_delete_row.png`): icono y aire crecen de forma apenas perceptible,
coherente con el resto de botones del sistema — no es un rediseño, es la misma pareja que ya usa
todo lo demás.

### `PaparcarMapMarkers.kt` · por qué 26 de 29 NO se tocan

⛔ **Este fichero dibuja marcadores de mapa — Nivel 3 del sistema de iconos**: *«vector propio,
multicolor, NO tintar […] SVG VectorDrawable-compatible […] o Compose Canvas en commonMain»*. Sus
constantes (`PLATE_W`, `SPOT_PIN_W`, `TAG_MARKER_W`, `CAPSULE_DISC_SIZE`…) no son spacing de
interfaz — son la FORMA del dibujo, en la escala del marcador (muchas se multiplican por `s`, el
factor de zoom). Convertirlas a `PaparcarSpacing` sería aplicar un token de chrome de pantalla a
geometría de ilustración: el mismo error de categoría que #136 ya identificó y descartó para íconos
Nivel 3.

Y lo mejor: **ya estaban bien** — 26 de 29 eran constantes con nombre, companion-object, sin un solo
literal suelto. Sólo 3 lo estaban (el padding del borde de la cápsula de gasolinera, su padding
interno, y el hueco glifo↔texto): nombrados ahora, **cero cambio visual**.

### `ParkingHistoryDetailScreen.kt` · lo que salió al mirar

Dos píldoras flotantes (ruta recalculando / ruta perdida) tenían el MISMO padding
(`top=8, start=24, end=24`) copiado verbatim, sin saberlo la una de la otra — nombradas
`ROUTE_NOTICE_PILL_TOP_DP` / `ROUTE_NOTICE_PILL_H_DP`, con valores que además coinciden EXACTOS con
`PaparcarSpacing.sm`/`.xxl`. Dos reusos directos de tokens ya existentes en vez de reinventar el
literal: `RoundedCornerShape(999.dp)` → `PapShapes.chip`, `RoundedCornerShape(6.dp)` → `PapShapes.cell`,
`strokeWidth = 2.dp` → `PapBorders.strong`.

⛔ **Las dimensiones del esqueleto (alto y radio de las líneas de carga) NO se tocaron con
`PaparcarSpacing`** aunque algunas coincidieran en número (16, 12): son geometría de un RECTÁNGULO
de relleno, no aire entre bloques — mismo criterio que en `PaparcarMapMarkers.kt`. Nombradas aparte
(`SKELETON_EYEBROW_HEIGHT_DP`, `SKELETON_TITLE_CORNER_DP`…), sin pedir prestado un token cuyo rol es
otro.

### Verificado en device, no solo en código

Capturado el mismo estado (Settings, flavor `mock`) en master y en esta rama:
`~/Pictures/paparcar-137/cmp_settings_barrido.png`. Diferencia medida con `ImageChops.difference`:
**0 píxeles con diff > 15** fuera de un parpadeo de 2 px del icono de wifi del sistema (ajeno a la
app). El barrido de este fichero es, literalmente, invisible — que es la prueba que pedía el
criterio «no es un rediseño».

## ✅ Barrido completo (12-10) · los 16 ficheros, 184 literales → 0 sin dueño

`.size(<literal>.dp)` en `presentation/` + `ui/`: **30 → 2** (las dos exenciones de `SettingsScreen`,
cada una con su razón ya escrita). El 4º chequeo de `SpacingGuardrailTest` **ya está escrito,
falsificado y verde**: inyectada una violación real (`size(60.dp)` en `VehiclesScreen`), confirmado
rojo, restaurado, confirmado verde de nuevo.

### Dos hallazgos del mismo defecto — y el cuarto, el de mayor alcance, también aplicado

El patrón «icono+gap de un botón a 16/6 en vez de los 18/8 del sistema» (ya corregido una vez en
#136 para `PapFooterButton`, y en esta tanda para Logout/Delete de `SettingsScreen`) apareció DOS
veces más:

- **`BluetoothConfigScreen.kt`** — un botón propio de esa pantalla. **Aplicado**, mismo criterio que
  Settings: blast radius de un call site.
- **`PapAlertDialog.kt`** — el spinner y el icono de `PrimaryAction`/`SecondaryAction`, el componente
  de diálogo CANÓNICO de toda la app. Primero **nombrado, NO aplicado**: cambiar esto mueve pixels
  en CUALQUIER diálogo de Paparcar, y eso pedía su propia decisión explícita, no la misma pasada que
  un botón de una pantalla. **Decisión del user (12-10-2026): aplícalo también.** `DIALOG_BUTTON_
  GLYPH_DP` pasa de `16.dp` inline a `PapIconSize.withLabel` (18 dp), y el gap que lo acompañaba —
  que YA era un token, pero el equivocado: `PapGap.chipRow` (6 dp, "aire de una fila de chips") en
  vez de `PapGap.iconLabel` (8 dp, el mismo que usa `PapButton` para "icono junto a su etiqueta
  dentro de un botón") — pasa a `PapGap.iconLabel`. Verificado antes/después en el diálogo real de
  borrar cuenta (`SettingsScreen` → Danger zone → Delete account), mismo preset de Dev Catalog en
  las dos ramas: `~/Pictures/paparcar-137/cmp_dialog_icon_gap.png`.

### Un área de toque más, encontrada barriendo (no por el guard de #137)

`AddingZonePeek.kt`: el selector circular de icono de zona medía **40 dp** de área de toque — el
mismo patrón de `Modifier.size(X).clickable{}` sin `minimumInteractiveComponentSize()` que
`TouchTargetGuardrailTest` ya vigila en `presentation/`+`ui/`, pero el guard sólo mira ficheros que
**ya** usan `PapControlHeight.*` en el mismo fichero — éste no, así que se escapó. Corregido a 48 dp,
mismo patrón que los 7 de #137.

### Verificación cruzada: cero regresión en los 38 tests bloqueados — y luego se desbloquearon

`:shared:testDebugUnitTest --rerun-tasks` en esta rama dio al principio **exactamente los mismos 38
tests en rojo** que en master limpio (diff de las dos listas: vacío) — el bloqueo de Windows sobre
`robolectric-nativeruntime.dll` era ajeno a este ticket, ver [[reference_tooling_gotchas]].
`:app:testMockDebugUnitTest` y `:app:assembleMockDebug`, verdes y forzados con `--rerun-tasks`.

**Al cerrar (12-10-2026)**: `:shared:testDebugUnitTest :app:testMockDebugUnitTest --rerun-tasks` →
**BUILD SUCCESSFUL, todo verde** — incluidos los 38 de Room/Robolectric. El bloqueo de Windows App
Control sobre esa DLL se levantó entre medias; no fue un arreglo de este ticket.

### El último hallazgo, aplicado con captura (decisión del user, 12-10-2026)

`PapAlertDialog.kt` quedó arriba como «nombrado, NO aplicado» por ser el componente canónico de
diálogo de TODA la app. El user pidió aplicarlo también, con capturas: `DIALOG_BUTTON_GLYPH_DP` pasa
de `16.dp` inline a `PapIconSize.withLabel` (18 dp), y el gap que lo acompañaba —ya era un token,
pero el equivocado, `PapGap.chipRow` (6 dp)— pasa a `PapGap.iconLabel` (8 dp), el mismo que usa
`PapButton`. Antes/después del diálogo real de borrar cuenta (`SettingsScreen` → Danger zone →
Delete account), mismo preset de Dev Catalog en master y en la rama, build en worktree desechable:
`~/Pictures/paparcar-137/cmp_dialog_icon_gap.png`.
