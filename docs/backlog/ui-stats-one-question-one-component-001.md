# UI-STATS-ONE-QUESTION-ONE-COMPONENT-001 · El panel de cifras copió las piezas de sus vecinas, y cada copia perdió algo

**Estado:** ✅ Done · rama `feature/UI-STATS-ONE-QUESTION-ONE-COMPONENT-001-borrowed-pieces` ·
worktree `../Paparcar-onequestion`
**Abierto:** 2026-09-16 · desde master `00ccbd34`

## Problema

Tres flecos vistos en mano en el panel *Tus cifras*, y los tres son la misma frase: **una pregunta,
un componente.** Donde hay dos, el segundo es una copia empobrecida del primero.

1. **«Tus sitios» respira de más.** `StatsPlacesCard` pone `vertical = 14` a la card *y* dentro
   `PapListItem` pone otros `14` arriba y abajo: **28 dp** en los bordes contra los 16 del resto de
   cards del panel. Con un solo sitio la card es casi todo aire.
2. **El par cerrado del heatmap se ofrece como chips.** `HeatMeasure` tiene exactamente **dos**
   valores por tipo (aparcamientos · buscando plaza), y una elección entre dos ya tiene su control
   en la app: el segmentado de km/millas de Ajustes. Pero ese segmentado es una función **privada**
   de `SettingsScreen`, así que el panel no podía usarlo aunque quisiera.
3. **El selector de coche del panel no dice de qué coche habla.** `StatsScreen.VehicleChips` es un
   `PaparcarFilterChip(label = …)` pelado. El de Vehículos (`VehicleTabPill`) es un contrato de
   identidad entero: glifo multicolor del coche, relleno y borde en el color de VIGILANCIA
   (`vehicleIdentityColor`), el nombre en voz MARCA (`rowName`) y el punto de vigilancia. La misma
   pregunta —«¿de qué coche hablamos?»— contestada por dos componentes, y el segundo tira la mitad
   de la doctrina de color.

## Doctrina violada

- **[UI-LIST-ITEM-001]**: hay UN esqueleto de fila y es él quien manda su ritmo. Una card que
  vuelve a acolchar una fila que ya se acolcha no es un ajuste fino, es un segundo dueño del mismo
  espaciado.
- **[UI-COLOR-DOCTRINE-001]**: *el color del NOMBRE de un coche dice CÓMO se le vigila*, y hay **un
  solo resolver** (`vehicleIdentityColor`). Un selector de coches sin identidad no es un selector
  más sobrio: es el mismo dato contado a medias.
- **Sistemas, no parches**: el segmentado no se copia al panel; se sube a `ui/components` y lo usan
  los dos.

## Diseño

### 1 · El padding lo manda la fila

`StatsPlacesCard` deja de poner padding vertical alrededor de la lista (el título conserva el suyo)
y las filas quedan pegadas al ritmo de `PapListItem`, igual que en Ajustes.

### 2 · Un solo segmentado, en `ui/components`

`SettingsSegmentedRow` (privado en `SettingsScreen`) se parte en dos: la **fila** de ajustes se queda
donde está, y el **control** sale como `PapSegmentedChoice` en `ui/components`. Ajustes lo usa dentro
de su fila; la card «Cuándo aparcas» lo usa suelto.

⛔ **La regla es del TIPO, no del dato de hoy**: `HeatMeasure` son dos por definición → segmentado.
`ChartMetric` son tres por definición y a veces salen dos porque faltan datos → **siguen siendo
chips**. Si el control cambiara según cuántas opciones haya, el mismo sitio sería dos cosas distintas
según el mes.

### 3 · Un solo selector de coche

`VehicleTabPill` sale de `VehiclesScreen` a `presentation/vehicles/components/` como
`PapVehicleSelectorPill`, y lo usan la pestaña Vehículos y el panel. El panel deja de tener chips
propios.

## Criterio de éxito

- Guardarraíles (Typography, Color, Divider, Import) en verde con `--rerun-tasks`.
- «Tus sitios» con el mismo aire que las cards vecinas, medido en device.
- «Cuándo aparcas» con segmentado, y el gráfico con sus chips — la diferencia se ve en la misma
  pantalla y es deliberada.
- El selector del panel con glifo, color de vigilancia y punto, idéntico al de Vehículos.
- `testAll` · galería en paridad · device.

## Verificación

- `testAll` ✅ · guardarraíles con `--rerun-tasks` ✅ · `:shared:compileKotlinIosSimulatorArm64` ✅ ·
  `assembleMockDebug` ✅.
- **Oppo CPH2371, mock con sha256 verificado** (`9de408d8…`) — el Redmi se quedó offline a mitad, así
  que esta pasada es del otro:
  - **«Tus sitios»**: título y filas al ritmo de `PapListItem`, sin el colchón de más. Con una sola
    fila ya no es una card de aire.
  - **«Cuándo aparcas»**: segmentado `aparcamientos | ✓ buscando plaza`, con el relleno
    `primaryContainer` y el borde `primary` de la casa — y **dos cards más arriba, el gráfico con sus
    chips**, en la misma pantalla. La diferencia se ve de un vistazo y es la que dice el tipo.
  - **Selector de coche**: idéntico en Vehículos y en el panel — glifo multicolor, relleno y borde de
    identidad en el seleccionado, nombre en MARCA y punto verde/azul de vigilancia.

## Consumidores auditados

| Punto | |
|---|---|
| `StatsPlacesCard` | 🔧 padding |
| Las otras 7 cards con `PapListItem` dentro | ✅ **ninguna repetía el padding**: `LicensesScreen`, `SharedCarSection`, `SharedCarGroupSurfaces` y `BluetoothConfigScreen` meten la fila pegada al card; `FirstStepsCard` le pasa un `contentPadding` explícito (8 dp de card + fila a medida, decisión propia). `StatsPlacesCard` era la única que sumaba 14 + 14 |
| `SettingsScreen.SettingsSegmentedRow` | 🔧 se parte: fila aquí, control en `ui/components` |
| `StatsWhenCard` | 🔧 chips → `PapSegmentedChoice` |
| `HistoryWeeklyChart` (`ChartMetricRow`) | ✅ sigue con chips, por tipo |
| `VehiclesScreen.VehicleTabPill` | 🔧 sale a components |
| `StatsScreen.VehicleChips` | 🔧 borrado; usa el pill |
