# UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001 · Aplicar el sistema pantalla por pantalla, con capturas

**Estado:** 🟡 **Parcial — mergeado el 2026-10-09** · rama
`refactor/UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001-spacing-sweep` · GitHub issue
[#137](https://github.com/apptolast/Paparcar/issues/137) · mitad 2 de 2 de
[#134](https://github.com/apptolast/Paparcar/issues/134); la mitad 1 cerró en
[#136](https://github.com/apptolast/Paparcar/issues/136), y el desvío a
[#138](https://github.com/apptolast/Paparcar/issues/138) ya está en master.

> Este cierre entrega la **ley del hueco junto a un control** (Hallazgo 0), su aplicación donde se
> midió, los **siete** toques bajo el mínimo accesible y el guard que vigila las dos caras —
> `TouchTargetGuardrailTest`, falsificado dos veces. **No** entrega el barrido pantalla a pantalla
> por volumen ni el 4º chequeo de `SpacingGuardrailTest` (los 30 `Modifier.size(<literal>.dp)`
> inline): eso reabre en un ticket nuevo cuando toque, con el sistema ya puesto.
> ⛔ `testAll` sigue sin poder correr entero en esta máquina — ver `reference_tooling_gotchas`.

## Problema

El encargo original (#134) venía con **tres casos concretos observados por el user**, y son los
únicos con un síntoma visto en vez de deducido:

1. **Chips de cifras / estadísticas de vehículos** — se ven demasiado separados.
2. **Chips de tiempo** — lo mismo.
3. **Card del vehículo en Home con los 3 botones** — demasiado aire entre ellos.
   Precisado el 07-10: *«la altura del botón depende del tamaño del contenido y a veces parece que
   cada botón tiene distinto tamaño»*.

⛔ **El (3) NO lo arregló #136, y hay que verificarlo antes de tocar nada.** El botón de la card de
en ruta es un `PapCtaPill` con altura **ya fija** antes de aquel ticket; lo que #136 arregló fue
`PapPrimaryButton`, que no aparece en esa card. Dos lecturas posibles, y la elige el device:

- lo que el user vio era el defecto de #136 en otra superficie y la card entró por cercanía; o
- en esa card pasa **otra cosa** — píldoras de 34 junto a algo de 40, padding, o el
  `IntrinsicSize.Min` de `HomeDetectionSurface`.

*Deducir un defecto de UI sin abrir el código cuesta un worktree; deducirlo del código sin la
captura, también.* Por eso este ticket **empieza en el device, no en el `grep`**.

## Estado del árbol al abrir (master `3005f67f7`, ya con #136)

| | |
|---|---|
| `dp` literales en `presentation/` + `ui/` | **580** (eran 606) |
| Usos de token (`PaparcarSpacing`·`PapGap`·`PapControlHeight`·`PapIconSize`·`PapChipPadding`) | **145** (eran 118) |

Peores ficheros: `SettingsScreen` 30 · `PaparcarMapMarkers` 29 · `VehicleRegistrationScreen` 24 ·
`HistoryContent` 22 · `ParkingHistoryDetailScreen` 21 · `HomeSearchBar` 14 · `BluetoothConfigScreen`
14 · `PapAlertDialog` 12 · `CarbodyInfoCard` 12.

## 🔑 Hallazgo 0 · La causa de TODO esto: un hueco visible NO es su constante

Medido en device el 08-10, con dos sondas y revertidas las dos.

El hueco entre las dos cápsulas apiladas de la pregunta medía **20,2 dp** con
`CTA_ROW_GAP_DP = 12`. No era un literal perdido ni un `Spacer` olvidado:

| sonda | altura de la cápsula | `CTA_ROW_GAP_DP` | hueco VISIBLE medido |
|---|---|---|---|
| — (como está) | 40 dp | 12 | **20,2 dp** |
| 1 | 40 dp | **4** | **12,2 dp** |
| 2 | **48 dp** | 4 | **4,2 dp** |

La ley que sale de ahí, exacta:

> **hueco visible = `spacedBy` + (48 dp − altura del control)**

La causa es `minimumInteractiveComponentSize` de Material3: un `Surface(onClick = …)` **más bajo
que 48 dp** se infla en LAYOUT hasta 48 y pinta su altura real centrada, así que deja la diferencia
repartida arriba y abajo como aire invisible. Con la cápsula a 48 el sobrante desaparece y el hueco
visible **es** la constante.

### Por qué esto es el ticket entero, y no una anécdota

La escalera de [#136] tiene tres peldaños y **dos de ellos mienten**:

| rol | altura | aire invisible por lado | el que una constante de hueco se come |
|---|---|---|---|
| `PapControlHeight.chip` | 34 dp | 7 dp | **+14 dp** |
| `PapControlHeight.pill` | 40 dp | 4 dp | **+8 dp** |
| `PapControlHeight.button` | 48 dp | 0 | 0 |

O sea: **toda constante de hueco junto a un control miente por (48 − altura)**, y eso explica por qué
los huecos se re-decidieron a mano por todo el árbol — la gente no estaba afinando la constante,
estaba afinando lo que SE VE contra una constante que no lo gobierna sola. Es la misma enfermedad que
#136 arregló para la altura, un nivel más abajo.

⛔ **Y no se arregla quitando el mínimo táctil**: son 48 dp de área de toque, accesibilidad, y
recortarlos para ganar píxeles sería pagar el diseño con el dedo del usuario.

## Doctrina

- La de #136: **el alto y el aire de un control son propiedad de su ROL**. Aquí no se re-decide, se
  aplica.
- ⛔ **No es un rediseño** (requisito explícito del user en #134): identidad, colores, tipografía y
  estilo de card se mantienen.
- ⛔ **Un literal no es un defecto por ser literal.** Las cinco familias de #136 siguen vigentes: el
  `strokeWidth` de un spinner y el radio de una celda no son spacing. Sustituir por sustituir es
  churn, no sistema — cada cambio tiene que poder decir **qué se ve distinto, o por qué el valor
  tenía dueño**.
- **Una PR con resultado visual se enseña** [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]: antes/después por
  pantalla que se mueva, del estado FINAL de la rama.

## Señales / datos disponibles

- El guard `SpacingGuardrailTest` de #136, con sus 3 chequeos verdes, y el cuarto que quedó
  **aplazado a este ticket**: *ningún `Modifier.size(<literal>.dp)` inline* — **30 violaciones
  vivas** al abrir.
- Dev Catalog (`DevMainActivity`) + galería de estados: es como se capturaron las de #136.
- Emulador Pixel 8 (1080×2400 @2.625x) y el móvil físico.

## Diseño

> Se rellena al volver del device. El orden es deliberado: **primero mirar, después tocar.**

1. **Device** — capturar BEFORE de las tres superficies reportadas y diagnosticar el caso (3).
2. **Clasificar** los 580 literales por familia y enrutarlos a su dueño (no «sustituir todos»).
3. **Barrer** pantalla por pantalla, empezando por lo que el user vio.
4. **Cerrar el 4º chequeo** del guard si el barrido deja los 30 inline a cero.

## Criterio de éxito

1. ✅ Los tres casos reportados: el de las cápsulas arreglado, los chips arreglados, el de los 3
   botones NO se reproducía — los tres con diagnóstico medido.
2. ✅ Capturas antes/después en `~/Pictures/paparcar-137/`.
3. 🟡 `:shared:testDebugUnitTest` + `:app:testMockDebugUnitTest` en verde (no `testAll` completo —
   klib de iOS tapado por Windows). `TouchTargetGuardrailTest` nace y se falsifica; el 4º chequeo de
   `SpacingGuardrailTest` (#136) **queda fuera de este cierre**, para el ticket que retome el
   barrido por volumen.

## Consumidores auditados

| sitio | qué tenía | qué se hizo | medido |
|---|---|---|---|
| Pareja de cápsulas apiladas (`HomeDetectionSurface`) | `spacedBy(12)` con dos controles enfrentados | pide hueco **visible**, `sides = 2` | **20,2 → 12,2 dp** |
| Columna de la superficie (texto ↔ cápsula) | `spacedBy(12)` con un control | `sides = 1` | — |
| Barra de periodo (`StatsPeriodBar`) | `vertical = 4.dp` **encima** de los 7 dp invisibles del chip | el aire lo pide en visible | aire chip→card **19,0 → 14,9 dp** |
| Fila de métricas (`HistoryWeeklyChart`) | `METRIC_CHIP_GAP_DP = 8` mientras la otra fila usaba **6** | las dos piden `PapGap.chipRow` | un solo aire entre chips |
| `METRIC_ROW_GAP_DP` (título ↔ chips) · `CARD_TITLE_GAP_DP` (chips ↔ barras) | constantes crudas | hueco visible, `sides = 1`; el segundo **sólo** si arriba hubo chips | — |

⛔ **Lo que NO se tocó, y por qué**: el aire HORIZONTAL entre chips. El mínimo táctil infla en las
dos dimensiones, pero un chip es más ancho que 48 dp, así que horizontalmente no sobra nada y la
constante dice la verdad. Descontar ahí habría juntado los chips de más.

## ✅ Caso 3 del encargo · **NO se reproduce** — medido, no supuesto

*«En la Home, dentro de la card del vehículo, cuando aparecen los 3 botones, están demasiado
separados entre sí.»*

Abierto el peek del coche aparcado (`~/Pictures/paparcar-137/BEFORE_parked.png`) y medido:

| par | hueco medido | su constante |
|---|---|---|
| «I'm leaving» ↔ «View vehicle» | **8,0 dp** | `ACTIONS_GAP_DP = 8` ✓ |
| los dos botones redondos | **8,0 dp** | `META_ACTIONS_GAP_DP = 8` ✓ |

Los dos dicen **exactamente** lo que su constante promete, y la ley explica por qué: `PapFooterButton`
mide **48 dp** —el mínimo táctil— así que no cobra overhang, y los redondos usan `Modifier.clickable`
crudo, que **no** infla.

Lo que el user vio como «demasiado separado» era casi seguro **la pareja de cápsulas de la
pregunta**, que sí medía 20,2 dp con su constante en 12 — y ésa ya está arreglada arriba. El caso 3
se cierra con la medida, no con un cambio.

## 🔴 Lo que apareció MIRANDO: tres áreas de toque por debajo del mínimo

El reverso de la misma ley. `Modifier.clickable` **no** aplica `minimumInteractiveComponentSize`
—sólo lo hacen los componentes de Material3 (`Surface(onClick)`, `Button`, `Chip`…)—, así que estos
tres se quedan con el área que pintan:

| control | tamaño | área de toque |
|---|---|---|
| `PapSheetDismissButton` (la ✕ del peek) | 34 dp | **34 × 34** |
| `PapSheetRoundIconButton` (ir al coche) | 38 dp | **38 × 38** |
| `PapSheetRoundIconButton` (editar) | 38 dp | **38 × 38** |

El mínimo son **48 × 48**. Y al buscar el resto apareció un cuarto: `PapStepperButton` (los ‹ › del
peek) a **32 dp**.

### ✅ Arreglado (decisión del user, 08-10)

`Modifier.minimumInteractiveComponentSize()` en los tres componentes, con el patrón del `IconButton`
de Material3 — **el mínimo primero, el tamaño pintado después** —, así que **lo dibujado no cambia**:
sólo el área que recibe el dedo.

⛔ **El chevron GASTADO reserva lo mismo que el tappable.** Su KDoc dice que el slot sostiene su
hueco «para que el bloque entre los dos chevrones no se deslice al llegar a un extremo»; darle el
mínimo sólo al pulsable habría producido exactamente ese deslizamiento.

Y como ahora sí inflan, sus huecos pasan a pedirse en **visible** (`STEPPER_TO_DISMISS_GAP_DP`,
`META_ACTIONS_GAP_DP`). ⚠️ Con 38 dp pintados el aire invisible es de 5 por lado, así que **lo
mínimo que puede verse entre los dos redondos son 10 dp**: pedir 8 solaparía las áreas de toque. Es
el caso que `coerceAtLeast(0)` cubre, y medido en device los dos redondos se separaron **+1,9 dp** —
los +2 previstos.

### El testigo: el árbol de accesibilidad, con el peek abierto

`adb shell uiautomator dump` sobre el estado real, midiendo **todos** los `clickable`:

| control | antes | ahora |
|---|---|---|
| ‹ › del peek | 32 × 32 | **48 × 48** |
| ✕ y los dos redondos | 34 / 38 | **48 × 48** |
| «I'm leaving» · «View vehicle» | 48 | 48 |

**Ningún control que dibujemos queda por debajo de 48 × 48.** Los tres `clickable` que siguen por
debajo en el volcado están DENTRO del nodo `Google Map` — son marcadores del SDK, con su propia área
de impacto, y no son nuestros.

## 🛡 El testigo · `TouchTargetGuardrailTest`

Dos chequeos, uno por cada cara de la ley, **los dos falsificados** con `--rerun-tasks`:

1. **Un `clickable` dimensionado declara su área de toque** — el par `.size(…)` → `.clickable(` es
   la firma de un control dibujado a mano, y tiene que pedir el mínimo.
2. **Un hueco VERTICAL junto a un control se pide en lo que se ve** — acotado a los ficheros que ya
   consumen la escalera de alturas.

### ⛔ Lo que enseñó falsificarlo, dos veces

- **El chequeo 2 nació demasiado romo.** Sin acotar a `verticalArrangement` cazó dos
  `horizontalArrangement = spacedBy(8.dp)` (`HistoryContent`, `VehiclesScreen`) que **eran
  correctas**: el mínimo infla en las dos dimensiones, pero esta escalera (34 · 40 · 48) sólo queda
  por debajo de 48 en el ALTO. Horizontalmente la constante dice la verdad.
- **Y el chequeo 1 salió VERDE al romperlo.** Buscaba la palabra `minimumInteractiveComponentSize`, y
  al quitar el modificador de `PapClearIconButton` **el import seguía teniéndola**. Un import
  huérfano satisfacía el guard. Ahora busca la LLAMADA, `.minimumInteractiveComponentSize()`. Es el
  mismo agujero que ya costó caro en este proyecto: *buscar un símbolo en vez de su uso*.

### Siete controles por debajo del mínimo, no cuatro

Al escribir el chequeo 1 aparecieron tres más que el ojo no había visto:

| control | área antes |
|---|---|
| ‹ › del peek (`PapStepperButton`) | 32 |
| ✕ del peek (`PapSheetDismissButton`) | 34 |
| los dos redondos (`PapSheetRoundIconButton`) | 38 |
| lápiz del chip de zona (`HomeZoneChips`) | 28 |
| muestra de color (`VehicleColorSelector`) | 40 |
| **× de los campos de texto (`PapClearIconButton`)** | **22** |

⚠️ El lápiz del chip de zona lleva ya el modificador, pero **su alto lo capa el chip (34 dp)**, así
que ahí el mínimo sólo gana a lo ancho. Subir el chip es decisión de diseño; queda escrito, no
fingido en el guard.

## 🔵 Pendiente con su dato

El hueco **texto ↔ cápsula** mide **16,8 dp** donde la cuenta da 12. No es la ley fallando: ahí
arriba el vecino es el **glifo de 56 dp**, cuya caja no es la del texto, así que la medida va contra
el borde equivocado. Hay que decidir cuál es el borde que manda antes de tocarlo.

## Capturas

`~/Pictures/paparcar-137/` — las de #136 están en `~/Pictures/paparcar-136/`.
