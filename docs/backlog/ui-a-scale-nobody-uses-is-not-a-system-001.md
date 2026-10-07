# UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001 · La escala de spacing existe, y el 80 % del árbol decide a mano

**Estado:** ✅ Done · mergeado a master (squash) el **2026-10-08** · GitHub issue
[#136](https://github.com/apptolast/Paparcar/issues/136) · mitad 1 de 2 de
[#134](https://github.com/apptolast/Paparcar/issues/134); la mitad 2 (barrido pantalla a pantalla,
con capturas) queda abierta en [#137](https://github.com/apptolast/Paparcar/issues/137)
> Verificado con `./gradlew testAll --rerun-tasks` → **3 863 tests, 0 fallos**, y con capturas
> antes/después en device (ver §Resultado visual). El doc no cita su propio hash porque viaja DENTRO
> del commit de master; el hash vive en `MEMORY.md`.

## Problema

Reportado por el user (30-09, #134) como «espacios excesivos y botones de altura poco consistente», y
precisado el 07-10 sobre la card del **coche en ruta**: *«la altura del botón depende del tamaño del
contenido y a veces parece que cada botón tiene distinto tamaño y rompe con el diseño»*.

Medido en `shared/src/commonMain/.../presentation` + `.../ui` (master `adffbbe43`):

| | |
|---|---|
| Usos de `PaparcarSpacing` | **118** |
| `dp` literales | **606** |
| De ellos, fuera de la escala 4·8·12·16·20·24·32·48 (≤48 dp) | **187** |

### Hallazgo 1 · Los 606 literales NO son un vocabulario, son cinco

Esto es lo que invalida el arreglo obvio (*«meter todo en una escala»*). Mirados uno a uno, los
literales caen en cinco familias que no tienen nada que ver entre sí:

| familia | ejemplo medido | dueño correcto |
|---|---|---|
| **gap** entre hermanos | `Arrangement.spacedBy(6.dp)` ×10 | escala / rol de gap |
| **altura de control** | `PILL_HEIGHT_DP = 40`, `FOOTER_BUTTON_HEIGHT = 48.dp` | el componente |
| **tamaño de icono** | `18.dp` declarado **6 veces** con 6 nombres distintos | rol de icono |
| **grosor de trazo** | `strokeWidth = 2.dp` ×5 | `PapBorders` |
| **radio de esquina** | `RoundedCornerShape(6.dp)` | `PapShapes` (ya tiene `cell` = 6 dp) |

Un `SpacingGuardrailTest` que prohibiera «el `dp` literal» trataría a las cinco igual y sería ruido
infalsificable: el `strokeWidth` de un spinner no es un espacio. **La prohibición tiene que ser por
familia, o no es un chequeo.**

### Hallazgo 2 · Hay dos roles de gap sin nombre, y por eso están a medias

Dos números se repiten con un significado idéntico en sitios que no se conocen entre sí:

- **`6.dp` = gap de una fila de chips** — `HomeFuelLane`, `HomeParkingsLane`, `HomeSheetContent`,
  `HomeZoneChips`, `SpotFitRow`, `StatsPeriodBar`, `CarbodyInfoCard`, `PaparcarFilterChip`,
  `NearbyLaneParts`, `BrowsePeek`. **No está en la escala** — cae entre `xs` 4 y `sm` 8 — así que
  cada sitio lo reinventó, y donde no se reinventó salió `8.dp`. Eso es exactamente el «los chips
  parecen excesivamente separados» del encargo: no es que sobre aire, es que **hay dos aires**.
- **`14.dp` = separación entre bloques de un peek / una hoja** — `PapSheet` ×3, `AddingZonePeek` ×2,
  `FuelStationPeek`, `PublicParkingPeek`, `SpotPeek`, `PapAlertDialog`. Mismo caso: entre `md` 12 y
  `lg` 16.

### Hallazgo 3 · Siete alturas de control, y una que no existe

| altura | dónde |
|---|---|
| 32 dp | `SKELETON_CHIP_HEIGHT_DP` (`HistoryContent`) |
| **34 dp** | `ZONE_CHIP_HEIGHT` · `STOP_PILL_HEIGHT_DP` (el botón del coche en ruta) |
| 36 dp | `VEHICLE_PILL_HEIGHT_DP` (= `TAB_HEIGHT_DP`) |
| 40 dp | `PapCtaPill.PILL_HEIGHT_DP` |
| **48 dp** | `PapFooterButton.FOOTER_BUTTON_HEIGHT` **y** `PapAlertDialog.BUTTON_HEIGHT_DP` (el mismo número, declarado dos veces) |
| 52 dp | `CLOSE_ROW_HEIGHT_DP` (`PaywallScreen`) |
| **ninguna** | **`PapButton`** |

32 / 34 / 36 están a 2 dp unas de otras: ahí no hay jerarquía que leer, hay ruido. Y `PapButton` no
declara altura, ni `defaultMinSize`, ni `maxLines`: su alto es `contenido + 14 dp`, así que **una
etiqueta que envuelve a dos líneas lo estira**. Con 9 locales, envuelve.

### Hallazgo 4 · ⛔ RETIRADO — la amplificación «hermanos colgando» NO existe en el árbol

Este hallazgo estaba escrito aquí como medido y **era deducido**: salió de cruzar un `grep` de
`weight(1f)` con la lista de ficheros, sin abrir el `Row` que los contiene. Abiertos uno a uno:

- `BrowsePeek` y `FuelStationPeek` **ya declaran** `verticalAlignment = Alignment.CenterVertically`.
- `SpotPeek` (los dos votos) y `PublicParkingPeek` no lo declaran, pero sus hijos con peso son un
  `PapFooterButton` —altura FIJA de 48— y un `Text`: no hay alto emergente que descuadrar.
- Y `PapPrimaryButton`, el único control sin altura, **no aparece hombro con hombro en ningún sitio**:
  `grep` de sus call sites con un `weight(1f)` a ≤8 líneas → **0**.

Se deja escrito en vez de borrado porque la conclusión equivocada ya había llegado al issue #136, y
porque la lección es la del proyecto: *deducir un defecto de UI sin abrir el código cuesta un
worktree*.

**Lo que sí queda en pie, y es el defecto que el user reportó**: la altura emergente de `PapButton`
no necesita un hermano al lado para verse. En `FirstStepsCard` los tres botones del checklist son
`Compact` con una etiqueta **variable** (`stringResource(cta)`, distinta por paso), así que la misma
card se dibujaba con tres alturas distintas según el paso en el que estuvieras — no lado a lado, sino
**a lo largo de los estados**, que es exactamente *«parece que cada botón tiene distinto tamaño»*.

## Doctrina violada

**La que ya estaba escrita en el repo.** Esto no es doctrina nueva: existe, con su razón, en UN
chip — `HomeZoneChips`, y su propio comentario lo dice:

> *«The height is DECLARED, not left to whichever child happens to be tallest: the add chip next
> door has to match it, and an emergent height gives it nothing to match.»*
> — [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]

Se descubrió para dos chips vecinos y nunca se generalizó. Es el caso de libro de
**sistemas, no parches**: el invariante está en el sitio equivocado —dentro de un consumidor— y los
demás consumidores no se barrieron.

Y es el mismo patrón que el proyecto YA aplica dos veces:

- **Tipografía** [UI-TYPE-TWO-VOICES-ONE-ROW-001]: *familia, tamaño y peso son propiedad del ROL, no
  del widget*. Nunca se elige tamaño: se elige rol.
- **Color** [UI-COLOR-DOCTRINE-001]: identidad por método, un solo resolver.

Falta el tercero. **El alto y el aire de un control son propiedad de su ROL, no de su contenido ni de
su call site.**

## Señales / datos disponibles

- Las cinco familias del Hallazgo 1, contadas arriba.
- Los 36 guardarraíles Konsist existentes — tipografía, color, divisores, botones, CTA, imports — y
  **ninguno** de spacing ni de altura. `ButtonGuardrailTest` ya prohíbe el `Button(` crudo de
  Material, así que la puerta está cerrada por un lado y abierta por el otro: no se puede traer un
  botón ajeno, pero sí deformar el propio desde fuera.

## Diseño

**Un invariante, en un sitio: el alto y el aire son del ROL.** Cuatro piezas, en este orden.

### Pieza 1 · Los roles que faltan, y solo los medidos

⛔ **No se crea una escala de 2 dp.** El Hallazgo 1 dice que el problema no es la resolución de la
escala, sino que hay familias sin dueño. `PaparcarSpacing` se queda como está —es la escala de
página y de card, y sus 118 usos son correctos— y se le añaden **los roles que la medición
encontró repetidos**, con su razón escrita:

- `chipGap` = 6 dp — el aire DENTRO de una fila de chips (10 sitios).
- `blockGap` = 14 dp — la separación entre bloques de un peek/hoja (9 sitios).
- `iconInControl` = 18 dp — el glifo dentro de un botón o chip (6 declaraciones hoy).
- La **escalera de alturas de control**: `chip` 34 · `pill` 40 · `button` 48. Tres peldaños, cada uno
  con su razón; 32 / 36 se absorben (están a 2 dp de 34), y el 52 del paywall se queda **solo si
  sobrevive a tener que escribir su razón**.

### Pieza 2 · La altura vuelve al componente

- `PapButton` fija su altura en la escalera y acota `maxLines`. Su KDoc ya promete que *«height,
  shape, padding, spinner — is this file's decision»*: la altura es la única de las cuatro que no lo
  cumplía.
- `PapAlertDialog` deja de declarar su propio 48 y consume el rol.
- El par hombro-con-hombro se alinea **donde se dibuja la pareja**, no en cada peek.

### Pieza 3 · Un chip canónico

`PapChip` en `ui/components/chips/`, dueño de altura (34), padding, gap (6), tamaño de icono (18) y
`maxLines`. Los ~19 dibujados a mano pasan a consumirlo. `PaparcarFilterChip` y `PaparcarAddChip` se
apoyan en él en lugar de repetir sus números.

### Pieza 4 · El testigo, por familia

`SpacingGuardrailTest` (Konsist), con prohibiciones **falsificables una a una** — no «nada de `dp`»:

1. Ningún call site de `presentation/` fija altura de un control (`Modifier.height(...)` /
   `defaultMinSize` sobre un botón o chip): la altura es del componente.
2. Ningún `Arrangement.spacedBy(<literal>.dp)` en `presentation/` ni `ui/components/`: hay rol.
3. La altura de control se declara **solo** en `ui/theme` y en los componentes canónicos.

⛔ Un cuarto chequeo —«ningún `Row` con hijos `weight(1f)` sin `verticalAlignment`»— se escribió y se
**retiró**: el Hallazgo 4 lo dejó sin un solo caso que cazar, y `Alignment.Top` es correcto cuando
los hermanos son columnas de texto de largo distinto. Una prohibición sin testigo no es un chequeo.

⛔ Falsificar los cuatro **con `--rerun-tasks`**: ni una constante ni un import cambian el bytecode,
y Gradle da el test por UP-TO-DATE sin llegar a ejecutarlo.

## Criterio de éxito

1. Un botón con etiqueta de dos líneas **no es más alto** que su hermano de una.
2. `grep` de alturas de control declaradas fuera de `ui/theme` y de los componentes canónicos → **0**.
3. Las 10 filas de chips comparten un solo gap, y los 9 bloques de peek una sola separación.
4. Los cuatro chequeos del guard, falsificados con `--rerun-tasks`.
5. `./gradlew testAll` en verde.
6. **Capturas antes/después en la PR** (petición explícita del user, 07-10): la card del coche en
   ruta y el par de votos de `SpotPeek` como mínimo, que es donde el valor está justo en la
   diferencia [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]. Salen de device o del Dev Catalog, no de una
   maqueta.

## Consumidores auditados

| consumidor | estado |
|---|---|
| `PapButton` — altura emergente + `maxLines` libre | ✅ cerrado: altura del rol, una línea |
| `PapButton` — `18.dp` ×2 y `8.dp` a mano | ✅ cerrado: `PapIconSize.withLabel`, `PapGap.iconLabel` |
| `PapProviderButton` — sin altura | ✅ cerrado: mismo rol que el CTA de pantalla |
| `PapFooterButton` — `FOOTER_BUTTON_HEIGHT` 48 + `FOOTER_BUTTON_ICON_SIZE` 18 + gap 10 | ✅ cerrado: los tres eran el mismo número con otro nombre |
| `PapAlertDialog` — `BUTTON_HEIGHT_DP` 48 (segunda declaración del mismo 48) | ✅ cerrado |
| `PapCtaPill` — `PILL_HEIGHT_DP` 40 | ✅ cerrado: `PapControlHeight.pill` por defecto |
| `HomeParkingRow` — `STOP_PILL_HEIGHT_DP` 34 (el botón del coche en ruta) | ✅ cerrado: `PapControlHeight.chip`; la variante sobrevive porque 6 dp SE VEN |
| 4 pares `weight(1f)` sin alineación | ⚪ exento — Hallazgo 4 retirado: no era un defecto |
| `ZONE_CHIP_HEIGHT` 34 — que `HomeLocationPill` **importaba** de un chip por no tener rol propio | ✅ cerrado |
| `VEHICLE_PILL_HEIGHT_DP` 36 **+ un segundo `TAB_HEIGHT_DP` 36** declarado aparte en `VehiclesScreen` | ✅ cerrado: absorbidos en `chip` 34 |
| `SKELETON_CHIP_HEIGHT_DP` 32 | ✅ cerrado: un esqueleto mide lo que sustituye, o la lista salta al llegar el dato |
| `CLOSE_ROW_HEIGHT_DP` 52 (paywall) | ⚪ exento: es alto de FILA, no de control — el regex del guard no lo mira, y por eso |
| 6 declaraciones de `18.dp` + 5 copias inline | ✅ cerrado: `PapIconSize.withLabel`; queda `NODE_ICON_SIZE`, que es otro rol (glifo en un nodo de 32 dp) |
| 8 filas de chips con gap a mano | ✅ cerrado: `PapGap.chipRow` |
| 3 `spacedBy(6.dp)` VERTICALES | ⚪ exento: columnas de texto y líneas de esqueleto, no filas de chips — otro rol, sin nombre todavía |
| 9 bloques de peek con `14.dp` a mano | ✅ cerrado: `PapGap.block` (8 sitios; `PapSheet` ×3) |
| ~19 chips dibujados a mano | ⚪ **reinterpretado — ver Hallazgo 5** |
| 30 `Modifier.size(<literal>.dp)` inline | ⏳ es #137: un chequeo que no puede estar verde el día que entra se desactiva, no se arregla |

### Hallazgo 5 · No hay un `PapChip`, y eso es el hallazgo

El issue prometía *«un chip canónico; los ~19 dibujados a mano pasan a consumirlo»*. Abiertos uno a
uno, **no son 19 call sites de una cosa: son cuatro composiciones distintas** —

- **píldora informativa**: ancho completo, no se pulsa, fondo teñido (`SpotFitRow`);
- **chip de elección**: seleccionable, con estado (`PaparcarFilterChip`);
- **chip de acción**: se pulsa, con borde, sin icono (`CarbodyInfoCard`, «cambiar»);
- **chip de objeto con nombre**: una zona, con su color de identidad y su lápiz (`HomeZoneChips`).

Un composable para las cuatro necesita seis ejes de estilo, que es exactamente lo que el KDoc de
`PapButton` prohíbe: *«a canonical button with five style parameters is M3 with another name»*.
Lo que comparten de verdad **no es un esqueleto, son las MEDIDAS** — y son las medidas lo que había
derivado (12/8, 10/6, 12/10 y una que fijaba alto en vez de padding). Así que se centraliza
`PapChipPadding` y las composiciones **se quedan separadas**, con la razón escrita en el token.

### Hallazgo 6 · ⚠️ La card del coche en ruta, que es lo que el user reportó, NO cambia

Y hay que decirlo antes de enseñar capturas. El botón de esa card es un `PapCtaPill` con
`height = 34` **ya fijo** antes de este ticket: su altura nunca dependió del contenido, así que este
arreglo no lo mueve (pasa de `34` literal a `PapControlHeight.chip`, que son los mismos 34 dp).

El control cuya altura SÍ la decidía el contenido es `PapPrimaryButton`, y donde eso se ve es en
`FirstStepsCard` (tres botones `Compact` con etiqueta variable), en Auth (`PapProviderButton`) y en
cualquier CTA con etiqueta larga en DE/PL/RO.

Dos lecturas posibles, y la elige el device, no el razonamiento:

1. lo que viste era este defecto en otra superficie, y la card de en ruta entró en el relato por
   cercanía; o
2. en la card de en ruta pasa **otra cosa** —tres píldoras de 34 junto a algo de 40, o padding, o el
   `IntrinsicSize.Min` de `HomeDetectionSurface`— y eso es un hallazgo de #137.

⛔ No se cierra este ticket afirmando que arregla lo que reportaste. Arregla el sistema; si al verlo
en mano la card sigue descuadrada, el defecto es el 2 y tiene su sitio en #137.

## Resultado visual — MEDIDO en device, no opinado

Capturas antes/después sobre el **mismo** emulador (Pixel 8, 1080×2400 @2.625x), flavor `mock`,
misma ruta de navegación, instalando primero master (`45044c92f`) y luego esta rama.
En `~/Pictures/paparcar-136/`.

| superficie | antes | después | |
|---|---|---|---|
| CTA `Compact` del checklist («Show me where») | **105 px = 40,0 dp** | **105 px = 40,0 dp** | idéntico |
| Píldora selectora de vehículo | 36 dp | **34 dp** | −2 dp, visible al comparar |

**Y el primero es el dato importante, no un no-resultado.** El botón ya medía 40 dp *por casualidad
del largo de esa etiqueta* — `8 + lineHeight(cta) + 8` daba 40 para «Show me where». Lo que cambia
no es cuánto mide hoy, es que **antes lo decidía el texto y ahora lo decide el rol**: con una
etiqueta que envuelva (DE, PL, RO) el de antes crecía y el de ahora no. La captura idéntica es
exactamente la prueba que hacía falta — el sistema entra **sin mover la app**.

⛔ Por eso este ticket es, casi entero, **sin resultado visual**, y decirlo en la PR vale más que
rellenarla con pares de imágenes iguales [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]. Los únicos
desplazamientos son de 2 dp: la píldora de vehículo (36→34) y el chip esqueleto del historial
(32→34).

## Fuera de alcance (es #137)

Aplicar el sistema pantalla por pantalla y corregir lo que al verlo en device siga descuadrado.
Aquí solo entra lo que hace falta para que el sistema exista y los consumidores lo consuman.
