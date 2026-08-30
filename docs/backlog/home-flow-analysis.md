# HOME-FLOW — Análisis del flujo completo de Home (AS-IS + objetivo)

> **Estado**: ANÁLISIS (complemento de amplitud de `ux-park-flow-001-analysis.md`).
> Rama `feature/UX-PARK-FLOW-001-park-flow-redesign`. Redactado 2026-07-18 sobre `master @ 781cb666`.
> **Re-verificado 2026-08-06 sobre `master @ b16a56bc`**: H1/H3/H4/H5/H6 siguen vigentes; los
> defectos C1..C7 del doc de aparcar que se citaban aquí están curados en su parte de modelo
> (VEH-ACTIVE-FENCE-001 ✅ en master 21-07). Referencias `fichero:línea` refrescadas y 2 intents
> nuevos añadidos al inventario (§10).
> Mapea TODO Home (pantalla × estado × acción) menos el sub-flujo aparcar/desaparcar, que tiene su
> propio zoom en `ux-park-flow-001-analysis.md` (§2). Este doc lo referencia, no lo repite.
> No toca código de producción: define, no implementa.

## 0. Mapa mental de Home

Home es **el AHORA** (mapa + plazas en tiempo real + sesión activa + detección). Todo cuelga de un
`HomeState` único con un `mode` (`HomeMode`) y un bottom sheet cuyo peek cambia de cara según el
contexto (6 variantes). Sub-flujos:

```
Home
├── Mapa (cámara, recenter, tipo de mapa, foreground gate)   §1, §2
├── Búsqueda (geocoder, resultados, fly-to)                  §3
├── Feed de plazas comunitarias (lista + peek + señales)     §4
├── Reportar plaza manual (modo Reporting)                   §5
├── Zonas habituales (add/edit/select/delete)                §6
├── Aparcar / desaparcar  → ux-park-flow-001-analysis.md     [zoom aparte]
├── Detección (superficie de estado + banners)               §7
└── Bottom sheet (6 peeks + anclajes)                         §8
Navegación fuera de Home                                      §9
Inventario Intents / Effects                                 §10
```

Fuente de verdad de la proyección: `HomeSlices.kt` (5 slices: Header, Fabs, Map, Peek, BrowseList).

---

## 1. Modos de interacción (`HomeMode`)

`HomeState.mode` (`HomeState.kt:140`), sealed `HomeMode` (`HomeState.kt:41-46`). Cuatro modos, todos
comparten el patrón **pin central + cámara dicta la posición** salvo Browse.

| Modo | Entra por | Peek | Sale por | Notas |
|---|---|---|---|---|
| **Browse** | por defecto / `ExitXxxMode` | `BrowsePeek` | — | markers full-opacity, lista visible, cámara libre |
| **Reporting** | `EnterReportMode(lat,lon)` | `ReportPeek` | `ExitReportMode` / confirm | reporta plaza comunitaria (§5) |
| **AddingZone** | `EnterAddZoneMode` / `EnterEditZoneMode` | `AddingZonePeek` | `ExitAddZoneMode` / confirm | zona habitual (§6) |
| **AddingParking** | `EnterAddParkingMode` | `AddingParkingPeek` | `ExitAddParkingMode` / confirm | → doc aparcar/desaparcar |

**Invariantes de posicionamiento**: `pinCameraLat/Lon` (`HomeState.kt:147-148`) capturan el centro de
cámara mientras estás en un modo pin; se limpian al volver a Browse. `isCameraMoving` (`:154`)
deshabilita el botón confirmar mientras la cámara no se asienta (no confirmar coordenada inestable).

**Confusión AS-IS (H1)**: cuatro modos que se ven casi idénticos (pin central + "Confirmar aquí") pero
significan cosas muy distintas (reportar plaza ajena vs. marcar mi coche vs. crear zona). El único
diferenciador es el eyebrow/copy del peek. Riesgo de que el usuario confirme en el modo equivocado.

---

## 2. Mapa — cámara, recenter, tipo de mapa

| Acción | Intent | Efecto |
|---|---|---|
| Pan/fling del mapa | `CameraPositionChanged(lat,lon)` (`HomeIntent.kt:12`) | re-geocodifica dirección de cámara; si en Browse y pan >300 m, re-centra la query de plazas (§4) |
| FAB recentrar | `RecenterSpots` (`HomeIntent.kt:14`) | resetea centro de query a GPS del usuario + mueve cámara |
| Cambiar tipo de mapa | `SetMapType(type)` (`HomeIntent.kt:15`) | TERRAIN / SATELLITE / HYBRID |
| Resume/pause pantalla | `SetMapForeground(active)` (`HomeIntent.kt:18`) | gate de GPS alta precisión, battery-bound [UI-LOC-FOREGROUND-001] |

**Map-type picker** (`MapTypePicker.kt:58-145`): stack vertical de 3 FABs circulares (Layers →
Terrain/Satellite/Hybrid) con expand/collapse animado; anillo de selección 1.5 dp.

**Defecto AS-IS (H2)** — el picker ofrece TERRAIN/SATELLITE/HYBRID y el default es `TERRAIN`, sobre el
que **el estilo de marca Paparcar no aplica** (es ráster; el JSON de `MapStyles.kt` solo rinde del todo
sobre el tipo vectorial `NORMAL`).

> 📌 **Actualizado el 2026-08-30** [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]. `MAP-TYPES-001`, que era
> el dueño de este defecto, **se borró**: su propuesta era rediseñar un popup de 3 opciones que ya no
> existe — `UI-MAP-TYPE-TOGGLE-001` (20-08) lo sustituyó por un toggle Terreno ⇄ Híbrido
> (`MapTypeToggle.kt`) y retiró Satélite puro. La spec entera describía una pantalla muerta.
>
> Lo único suyo que seguía siendo cierto se queda aquí, que es donde se detectó: **el default sigue
> siendo `MapType.TERRAIN`** (`HomeState.kt` y `PaparcarMapView.kt`, verificado el 30-08), así que el
> estilo de marca y el dark automático siguen sin aplicar del todo. El KDoc del toggle defiende
> TERRAIN por legibilidad ("flat street plan at city zoom") pero no responde a esto. Si algún día
> molesta, se abre ticket nuevo sobre el toggle — no se resucita el del popup.

---

## 3. Búsqueda

`HomeSearchController.kt` (pipeline) + `HomeSearchBar.kt` (UI) + intents (`HomeIntent.kt:108-110`).

| Estado | Superficie | Acción | Efecto |
|---|---|---|---|
| escribiendo | search bar glass (`HomeSearchBar.kt:57`) | `SearchQueryChanged(query)` | debounce 300 ms → `SearchAddressUseCase` |
| `Searching` | spinner en la barra | — | geocoder en vuelo |
| `Success(results)` | dropdown de direcciones | tap → `SelectSearchResult` | mueve cámara al resultado |
| `Failure` | limpia resultados | — | sin error visible (silencioso) |
| — | botón limpiar | `ClearSearch` | vacía query + resultados |

**Confusión AS-IS (H3) — ✅ CURADA EN ESTA RAMA (06-08)**: `SearchUpdate.Failure` limpiaba en
silencio y una búsqueda sin resultados era el mismo silencio. Ahora: fallo del geocoder →
`ShowError(PaparcarError.Location.SearchFailed)` → snackbar; éxito con 0 resultados → fila
explícita "Sin resultados" en el dropdown (`searchNoResults` en `HomeState`). Con tests, strings
en 9 locales, previews y galería.

---

## 4. Feed de plazas comunitarias

`HomeSpotsController.kt:40-113` (suscripción Firestore) + sección en el sheet (`HomeSheetContent.kt:83-150`).

**Suscripción**: `SpotsUpdate.Data(spots)` = plazas dentro de `DEFAULT_SEARCH_RADIUS_METERS` del centro
de query (GPS del usuario, o centro de cámara si pan >300 m en Browse). `SpotsUpdate.Error` → snackbar
+ lista vacía.

| Estado | Superficie | Acción | Efecto |
|---|---|---|---|
| Browse + CORE ok | sección "PLAZAS LIBRES CERCA · N" + barra de 6 chips de talla | `LoadNearbySpots` / `SetSizeFilter(size)` | carga / filtra por `VehicleSize` |
| plaza en lista | fila de plaza | `SelectItem(itemId)` (`HomeIntent.kt:24`) | selecciona → `SpotPeek` |
| plaza seleccionada | **`SpotPeek`** (`SpotPeek.kt:64-180`) | ver abajo | — |
| sin CORE | sección oculta | — | requiere permiso ubicación |

**SpotPeek** — contenido: título (nombre/dirección/coords), eyebrow = tier de fiabilidad (color-coded),
badge en-ruta (`enRouteCount`), TTL en vivo "Caduca en X" (`rememberNowMinuteTick` [SPOT-TTL-LIVE-001]),
"Publicada hace X" + distancia, modo viaje auto (<400 m → andando). Acciones:

| Acción | Intent | Efecto |
|---|---|---|
| 👍 "sigue ahí" / 👎 "ya no está" | `SendSpotSignal(spotId, accepted)` (`HomeIntent.kt:28`) | señal comunitaria, guard `inFlightSpotSignals` contra doble-tap |
| Navegar | `HomeSheetAction.NavigateExternal` | Google Maps externo (andando/coche según distancia) |
| "Avisar plaza libre" (fin de lista) | `EnterReportMode` | → §5 |

**Confusión AS-IS (H4)**: la selección de plaza comunitaria y la selección de sesión propia comparten
`selectedItemId` (mismo espacio UUID, `HomeState.kt` [MULTI-PARKING-001]). Bien resuelto internamente,
pero es un acoplamiento a vigilar cuando toquemos el peek.

---

## 5. Reportar plaza manual (modo Reporting)

Distinto de marcar TU aparcamiento: aquí avisas de una plaza libre **ajena** para la comunidad.
Handler `HomeViewModel.kt:301-353` (`handleSpotIntent` + `confirmReportSpot`), peek `ReportPeek.kt`.

| Estado | Superficie | Acción | Efecto |
|---|---|---|---|
| entra | `EnterReportMode(lat,lon)` (`HomeIntent.kt:33`) | — | `mode=Reporting`, pin en centro cámara |
| posicionando | `ReportPeek` eyebrow "AVISAR PLAZA LIBRE" (azul), icono Campaign, chips "TAMAÑO DEL VEHÍCULO" | `SetReportingSize(size)` | selecciona talla (o null=desconocida) |
| cámara quieta | botón "Confirmar aquí" activo | `ConfirmReportSpot` | `ReportManualSpotUseCase(lat,lon,size?)` |
| cancelar | — | `ExitReportMode` | vuelve a Browse |

---

## 6. Zonas habituales (Casa/Trabajo…)

Handlers `HomeViewModel.kt:467-552` (`handleZoneIntent` + confirm/delete/edit/select), peek
`AddingZonePeek.kt`, chips en header `HomeHeaderSection.kt`.

| Estado | Superficie | Acción | Efecto |
|---|---|---|---|
| crear | `EnterAddZoneMode(lat,lon)` | — | `mode=AddingZone`, pin en cámara |
| editar | `EnterEditZoneMode(zoneId)` | — | carga datos en el form, `editingZoneId` set |
| en el form | `AddingZonePeek`: nombre (TextField) + icono (LazyRow chips) + radio (Slider) + privacidad (Switch) | `UpdateAddingZoneName` / `UpdateAddingZoneIcon` / `SetZoneRadius` / `SetZoneIsPrivate` | actualiza form |
| guardar | botón "Guardar zona" (deshabilitado si cámara móvil/guardando) | `ConfirmAddZone` | `SaveOrUpdateZoneUseCase` → `ZoneSaved` |
| Browse: chip de zona | header LazyRow | `SelectZone(zoneId)` | `MoveCameraTo` (vuela, sigue en Browse) |
| Browse: × en chip | header | `DeleteZone(zoneId)` | borrado optimista, guard `deletingZoneIds`; fallo → snackbar + re-render |
| Browse: long-press chip | header | `EnterEditZoneMode` | edita |

---

## 7. Detección — superficie de estado y banners

### Superficie de detección (acción, no banner pasivo)
`HomeDetectionSurface.kt` (composable en `:73`, fila de acciones en `:176+`), estados en
`DetectionUiState.kt`. Se renderiza en el sheet bajo la cabecera de dirección, solo para estados
de acción:

| Estado | Render | CTA |
|---|---|---|
| `NoVehicle` (`:26`) | pill ámbar "Añade un coche" | añadir vehículo |
| `Inactive` (`:34`) | pill azul "Activa la detección" | `EnableAutoDetection` (flag + permisos en 1 tap) |
| `BlockedCore` (`:37`) | pill roja urgente (GPS/ubicación off) | abre permisos — **bloquea el sheet entero** |
| `AwaitingFirstPark` (`:49`) | pill azul cold-start | "Marcar aparcamiento" (primario) + "Estoy conduciendo" (secundario) |
| `Parked` / `Monitoring` / `Silent` (`:40,43,52`) | silencioso | — (fase se muestra en el chip del vehículo) |

### Banner de precisión GPS
`HomeGpsAccuracyBanner.kt:48-92`: oculto <20 m; ámbar 20-50 m; rojo >50 m ("GPS ±XXm"), bajo la barra
de búsqueda.

### Sin banner de conectividad en Home
El offline/restored se maneja en el **root de la app** (CONN-BANNER-001), no en Home. Guardado de
aparcamiento es local-first [OFFLINE-PARK-001]; suscripciones auto-reconectan.

**Confusión AS-IS (H5 = C6 del doc aparcar)**: el estado de detección se reparte en pills sueltas
(`DetectionUiState`) + fase en chips + banner GPS. **No hay un relato único** de "qué está haciendo la
app ahora mismo": vigilando / conduciendo / aparcando / necesita que declares el coche / bloqueado.
Es el corazón de C3/C6 de `ux-park-flow-001-analysis.md`.

---

## 8. Bottom sheet — 6 variantes de peek + anclajes

Orquestador `HomePeekHandle.kt:42-213`. Resuelve `PeekState` (sealed, identity-only contra jitter
[BUG-PEEK-JITTER-001]) y anima la transición (`AnimatedContent`, `HomePeekHandle.kt:87-101`).

| # | `PeekState` | Peek | Condición | Muestra |
|---|---|---|---|---|
| 1 | `SelectedSpot` | `SpotPeek` | plaza seleccionada, sin sesión | §4 |
| 2 | `SelectedParking` | `ParkingPeek` | `selectedSession != null` | doc aparcar §2.3 |
| 3 | `Reporting` | `ReportPeek` | `mode=Reporting` | §5 |
| 4 | `AddingZone` | `AddingZonePeek` | `mode=AddingZone` | §6 |
| 5 | `AddingParking` | `AddingParkingPeek` | `mode=AddingParking` | doc aparcar §2.1 |
| 6 | `Browse` | `BrowsePeek` | nada de lo anterior | coche aparcado / estado monitorización / viaje en vivo / header de zona |

**Anclajes** (`HomeSheetPositioning.kt`, ref `HomeScreen.kt:372-385`): Peek / Half / Expanded /
Minimized + umbrales de chrome. `capExpandAtPeek` (`:382`): en modos pin o con item seleccionado, el
sheet no puede expandir por encima de peek. Pill de arrastre oculta en `BlockedCore` [DET-READY-001].

**Confusión AS-IS (H6)**: 6 caras de peek con reglas de anclaje distintas (half para Browse, minimized
para modos). El usuario puede perder el hilo de "¿por qué el sheet no sube?" en modos pin (es
`capExpandAtPeek`, invisible). Ver regresión ya cerrada BUG-SHEET-STRANDED-TALL-001.

---

## 9. Navegación fuera de Home

| Destino | Disparador | Mecanismo |
|---|---|---|
| Tab Vehículos / Ajustes | bottom nav | NavHost root |
| Pantalla permisos (con foco) | CTA de la superficie de detección | `HomeEffect.OpenDetectionPermissions(focus)` (`HomeEffect.kt:22`); `focus="core"\|"all"` |
| Registro de vehículo | `HomeSheetAction.AddVehicle` → `onAddVehicle()` | callback root |

Deep link: la ruta de permisos acepta `?focus=producer|all`. Home es la ruta por defecto.

---

## 10. Inventario Intents / Effects (fuera de aparcar/desaparcar)

**Intents** — Mapa (4): `CameraPositionChanged`, `RecenterSpots`, `SetMapType`, `SetMapForeground`.
Búsqueda (3): `SearchQueryChanged`, `SelectSearchResult`, `ClearSearch`. Plazas (8): `LoadNearbySpots`,
`SelectItem`, `SetSizeFilter`, `SendSpotSignal`, `EnterReportMode`/`ExitReportMode`, `ConfirmReportSpot`,
`SetReportingSize`, `ReportTestSpot`(debug). Zonas (9): `EnterAddZoneMode`/`ExitAddZoneMode`,
`ConfirmAddZone`, `UpdateAddingZoneName`, `UpdateAddingZoneIcon`, `SetZoneRadius`, `SetZoneIsPrivate`,
`SelectZone`, `DeleteZone`, `EnterEditZoneMode`. Detección (2): `StartDrivingDetection(vehicleId)`
(lleva coche desde VEH-ACTIVE-FENCE-001), `EnableAutoDetection`.

**Añadidos tras la redacción original** (re-verificación 06-08): `ShowParkingConfirmation(gps)`
(`HomeIntent.kt:42`, orquestación interna — el detector dispara el diálogo de confirmación) y
`DismissParkNudge` (`HomeIntent.kt:60`, dismiss del nudge "¿dónde dejaste el coche?"
[DET-NUDGE-PERSIST-001]).

**Effects (10)** (`HomeEffect.kt`): `ShowError`, `SpotReported`, `TestSpotSent`(debug),
`RequestLocationPermission`(sin uso, logueado), `SpotSignalSent`, `MoveCameraTo`, `ZoneSaved`,
`DetectionEnabled`, `OpenDetectionPermissions(focus)`, `DetectionStopped`.

---

## 11. Mapa consolidado de confusión / oportunidades (Home completo)

| # | Síntoma UX | Origen | Dueño |
|---|---|---|---|
| H1 | 4 modos pin casi idénticos; riesgo de confirmar en el equivocado | mismo esqueleto peek, solo cambia eyebrow | UX (narrativa modo) |
| H2 | Tipo de mapa por defecto sin estilo de marca | default TERRAIN ráster | 🟡 sin dueño — `MAP-TYPES-001` se borró (describía el popup que retiró `UI-MAP-TYPE-TOGGLE-001`); el defecto sigue vivo, detalle en §H2 |
| H3 | Búsqueda falla en silencio | `SearchUpdate.Failure` limpia sin feedback | ✅ CURADA en esta rama 06-08 (snackbar + fila "sin resultados") |
| H4 | Spot y sesión comparten `selectedItemId` | acoplamiento MULTI-PARKING-001 | vigilar al tocar peek |
| H5/C6 | Sin relato único de estado de detección | pills sueltas + fase + banner GPS | **UX-PARK-FLOW-001** (C6) |
| H6 | Sheet "no sube" en modos pin sin explicación | `capExpandAtPeek` invisible | UX (ya parcheado el caso agudo) |
| — | (aparcar/desaparcar C1..C7) | ver `ux-park-flow-001-analysis.md` §3 | C1/C2/C7 ✅ curados (VEH-ACTIVE-FENCE-001 en master 21-07); C3..C6 → UX-PARK-FLOW-001 |

**Foco de rediseño UX** (lo genuinamente de este análisis, no cubierto por otro ticket):
1. **Relato único de estado** (H5/C6) — el más transversal: unificar detección + fase + GPS en una
   voz. → 📋 spec en `ux-detection-story-001.md` (esta rama), pendiente revisión.
2. **Diferenciación de modos pin** (H1) — que reportar/marcar/zona no se confundan. → 🔴 pendiente.
3. **Feedback de error de búsqueda** (H3) — barato, alta fricción. → ✅ hecho en esta rama (06-08).

## 12. Qué NO es de este análisis (deuda con dueño)
- Estilo de mapa → ~~**MAP-TYPES-001**~~ **sin dueño desde el 30-08**: aquel ticket se borró por
  describir el popup de 3 opciones que `UI-MAP-TYPE-TOGGLE-001` retiró. El defecto (default `TERRAIN`,
  estilo de marca sin aplicar) sigue vivo y documentado en §H2 de este mismo doc.
- Modelo activo/liberar (release fantasma, atribución) → **VEH-ACTIVE-FENCE-001** — ✅ en master
  21-07 (pendiente field-test Pieza 1).
- Snap del ancla fuera de edificios → **SNAP-TO-PARK-001** (solo spec).
Este doc los referencia para no re-litigar, y se centra en la **narrativa de Home** (H1/H3/H5).

---

## 13. Sincronización Dev Catalog (al implementar cualquier estado nuevo)
Regla ⛔ CLAUDE.md: cada estado/variante nuevo → `StateGalleryScreen` + paridad `*Previews.kt`; cada
condición de routing → `MockScenario` + fake + control en `DevCatalogScreen`. Los 6 peeks y los 5
estados de `DetectionUiState` deben tener su escenario.
