# UI-HISTORY-IDENTITY-AND-SOURCE-001 · El histórico pinta la identidad del coche y dice por qué vía se detectó

**Estado:** ✅ Done (2026-08-22) — mergeado a master por squash. ⏳ Queda **verlo en device**.
**Rebases 2026-08-22:** dos, sobre `6cb72a73` (venía de `1f7e6cde`, 5 por detrás) y luego sobre
`865f0f8a` (DET-ASK-STATE-001), **ninguno con conflictos**, pese a que entremedias master tocó
`StateGalleryScreen.kt`, `PapSheet.kt` y **los 9 `strings.xml`** — los mismos ficheros que esta rama.
Verificado a mano tras el último rebase: las claves `parking_detail_detection_*` están en los 9
locales y conviven con las `home_det_ask_*` de DET-ASK-STATE-001; la galería conserva las variantes
de las dos ramas. **1.386 tests verdes**, prod y mock compilan.

## Problema

Dos cosas, la misma pantalla (Vehículos → Historial → «ver en mapa» = detalle de aparcamiento):

1. **El acento sale verde en un coche de Bluetooth.** El user abre el detalle de una sesión activa
   del Kamiq (emparejado por BT, vigilado por la estrategia determinista) y las filas meta se pintan
   en verde de marca. Debería ser azul: *el color del coche es su MÉTODO de vigilancia*.
2. **«Detección automática» no dice quién la hizo.** La fila de detección solo lee `spotType`, que
   tiene tres valores (`AUTO_DETECTED` / `MANUAL_REPORT` / `HOME_GEOFENCE`) y colapsa las DOS
   estrategias en una sola palabra. El user no puede saber si ese pin lo puso el BT o el Coordinator
   — que es justo lo que necesita saber cuando está cazando falsos positivos en campo.

## Doctrina violada

`UI-COLOR-DOCTRINE-001` (CLAUDE.md → ⛔ Color): *«Un solo resolver: `ui/theme/VehicleIdentity.kt` →
`vehicleIdentityColor(watch)`»*. El barrido de esa doctrina cubrió Home y Vehículos y **se dejó
fuera el histórico**: ni `presentation/map/ParkingLocationScreen.kt` ni
`presentation/vehicles/components/HistoryTimeline.kt` ni `…/HistoryComponents.kt` pasan por el
resolver — usan `colorScheme.primary` / `primaryContainer` fijos. Un resolver único al que dos
pantallas no llaman no es un resolver único.

Y `feedback_detection_trigger_provenance` (⛔ *«identificar SIEMPRE qué trigger/path colocó cada
pin»*): la provenance existe, está persistida y sincronizada… y la única pantalla donde el user mira
un pin concreto no la enseña.

## Señales / datos disponibles

Ya persistido y sincronizado, sin migración ni campo nuevo:

- `UserParking.detectionPath` — `"bt"` / `"bt_timeout"` (BluetoothParkingDetector), `"manual"` /
  `"user"` / `"nudge"` (SaveManualParkingUseCase), `"safety_net_backfill"` (ParkingBackfillWorker),
  y las etiquetas ricas del coordinator (`"steps=… kinematicFixes=…"`, `"motorBand=…ms ≥…mps"`,
  `"unattended_timeout"`, `"unattended_zone_*"`…). **`null` en filas legacy.**
- `UserParking.spotType` — `AUTO_DETECTED` / `MANUAL_REPORT` / `HOME_GEOFENCE`.
- `Vehicle.monitoringStatus()` → `VehicleMonitoringStatus` → `.watch()` → `VehicleWatch`, ya
  disponible en las dos pantallas (`ParkingLocationState.focusedVehicle`,
  `VehiclePageContent.vehicleWithStats.vehicle`).

⚠️ Las etiquetas del coordinator son **jerga de diagnóstico** (`steps=3 kinematicFixes=7`): no se
enseñan nunca al user (regla ⛔ *no copy con mecánica interna*). Se clasifican, no se imprimen.

## Diseño

### 1 · La lectura de provenance vive en UN sitio, en dominio

`domain/detection/ParkingDetectionSource.kt` — enum + función pura de nivel superior, patrón ya
establecido (`HumanPoweredRide.kt`, `SentryWakeCooldown.kt`). **No es un caso de uso**
(`DET-VERDICT-NOT-PREDICATE-001`): no emite un veredicto nuevo ni entra en el vocabulario de
diagnóstico, solo LEE provenance que ya se decidió en otro sitio.

```
HOME_GEOFENCE                       → PrivateZone
MANUAL_REPORT                       → Manual
AUTO_DETECTED + path "bt…"          → Bluetooth
AUTO_DETECTED + manual/user/nudge   → Manual        (el nudge guarda AUTO_DETECTED a propósito)
AUTO_DETECTED + null/blank          → Unknown       ← filas legacy
AUTO_DETECTED + cualquier otro      → Assisted
```

`Unknown` es deliberado: una fila legacy **no sabemos** por qué vía se detectó, y el fallo asimétrico
del proyecto dice que ante la duda no se afirma. `Unknown` conserva el copy actual («Detección
automática») en vez de inventarse un tier.

### 2 · Copy: el icono dice la vía, el texto dice el nivel

Reusa el vocabulario que el user YA lee en la tarjeta de niveles de Permisos
(`permissions_tier_automatic_name` = «Automático», `…_assisted_name` = «Asistido») en vez de
inventar jerga:

| source | icono | texto |
|---|---|---|
| `Bluetooth` | `Rounded.Bluetooth` | **Automático (Bluetooth)** ← key nueva |
| `Assisted` | `Rounded.Radar` | **Asistido** ← key nueva |
| `Unknown` | `Rounded.Bolt` | Detección automática *(key existente)* |
| `Manual` | `Rounded.EditLocationAlt` | Aviso manual *(existente)* |
| `PrivateZone` | `Rounded.Home` | Zona privada *(existente)* |

Los iconos `Bluetooth` / `Radar` son exactamente los de `VehicleWatchLeadingIcon`, así que la vía se
lee igual en Home y en el histórico.

### 3 · Color: el resolver único, también aquí

- **Detalle** (`ParkingLocationScreen`): las dos filas meta (reloj + detección) toman
  `vehicleIdentityColor(vehicle.monitoringStatus().watch())` cuando la sesión está viva, y
  `onSurfaceVariant` cuando está cerrada (el apagado de las cerradas no se toca). Un solo color por
  tarjeta: el del coche, no el de la vía del pin — un coche BT con un pin manual sigue leyéndose azul
  (decisión del user, 21-08-2026).
- **Timeline** (`HistoryTimeline` + `HistoryComponents`): los puntos del raíl toman la identidad del
  coche de esa página (el pager de Vehículos es de UN coche por página). La tarjeta de la sesión viva
  **conserva su relleno sólido de siempre** y solo cambia de HUE, vía
  `vehicleIdentityContainer(watch)` + `onVehicleIdentityContainer(watch)`: la pierna verde ES el
  `primaryContainer` del esquema, la azul su espejo con los tokens azules que el esquema ya tiene.
  ⚠️ El primer intento fue un wash traslúcido de la identidad al 14 % — **descartado en device**
  (21-08-2026): quedaba demasiado tenue, la tarjeta viva tiene que leerse rellena.
- **La cabecera «APARCADO ACTUALMENTE» se queda NEUTRA** (corrección del user en device, 21-08-2026;
  primero se tiñó de identidad y estaba mal). Es un ESTADO, y el estado se escribe en texto neutro y
  se cuenta con ANIMACIÓN — `COLOR-SYSTEM.md §3.1`. Sólo su punto pulsante lleva color, y lleva el
  del COCHE, igual que el punto del raíl que tiene justo debajo.
- **Se quedan verdes a propósito** (son chrome de marca, no identidad de coche, mismo criterio que
  el comentario ya escrito en `VehiclePageContent.StatCell`): el punto de la cabecera de día, el
  botón «ver en mapa» (es una ACCIÓN) y la fila de stats de la hero card.

### 4 · Los días del historial dejan Barlow y bajan un peldaño (no un cambio de color)

Los separadores «HOY» / «AYER» / «VIERNES, 14 AGO 2026» vestían el rol `badge` — **Barlow
Condensed, la familia DATA**, que existe para *tokens que se repiten dentro de una fila o compiten
en horizontal con un nombre* (`ACTIVO`, `3 LIBRES`, `179 m · 1 min`). Un separador de día no es
ninguna de las dos cosas: es estructura de layout, y la estructura es Inter.

Pasan al mismo `PapSectionHeaderRow` que «APARCADO ACTUALMENTE», con `dense = true` → rol nuevo
**`subsectionHeader`** (Inter, 11 sp, Bold, ls 1.0), un peldaño por debajo de `sectionHeader`
(12 sp, ExtraBold). La jerarquía se cuenta con el TAMAÑO, no cambiando de familia. Nada de
`fontSize` inline: la regla del proyecto es *si falta un tamaño, se añade el rol* — de ahí que el
censo de roles pase de 18 a 19 y `CLAUDE.md` lo refleje. El `dense` vive dentro de
`PapSectionHeaderRow` (en vez de un parámetro `style` abierto) para que siga siendo cierto que los
roles de cabecera no salen nunca de ese fichero.

## Criterio de éxito

- `ParkingDetectionSourceTest` cubre las 6 ramas + `bt_timeout` + legacy `null` + la trampa del
  nudge (`AUTO_DETECTED` + `"nudge"` → Manual, no Assisted).
- En device: el detalle de una sesión del Kamiq (BT) sale **azul** y dice «Automático (Bluetooth)»;
  el del C5 Aircross (sin BT, Coordinator) sale **verde** y dice «Asistido».
- Galería mock con las 3 variantes nuevas (BT / asistido / legacy) y el historial en las 3
  vigilancias, verificable sin conducir.
- `ColorGuardrailTest` + `TypographyGuardrailTest` siguen verdes.

## Consumidores auditados

Barrido de `colorScheme.primary` en las superficies de histórico:

| Sitio | Veredicto |
|---|---|
| `ParkingLocationScreen.DateTimeRow` | **cerrado** — identidad |
| `ParkingLocationScreen.DetectionRow` | **cerrado** — identidad + fuente |
| `HistoryTimeline.EndedSessionTimelineNode` punto raíl | **cerrado** — identidad |
| `HistoryTimeline` `PulsingDot` sesión activa | **cerrado** — identidad |
| `HistoryTimeline.SessionCardContent` `primaryContainer` | **cerrado** — `vehicleIdentityWash` |
| `HistoryComponents.ActiveSectionHeader` | **cerrado** — identidad |
| `HistoryTimeline` botón «ver en mapa» | **exento** — acción → verde de marca |
| `HistoryTimeline.DayHeaderRow` punto | **exento** — chrome de fecha, no coche |
| `VehiclePageContent.StatCell` | **exento** — furniture (ya documentado en el fichero) |
| `HistoryWeeklyChart` / `ActivityCard` | **exento** — gráfico de actividad, no identidad |
| `ParkingLocationScreen` chip «recalculando» | **exento** — spinner de proceso |
| `PaparcarMapView` marcador aparcado | **fuera de alcance** — el user lo descartó al elegir alcance; su tono ya se decide por `isBluetoothPaired` en la clave del bitmap |
