# UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001 · Un aparcamiento guardado como ZONA no puede dibujarse como un pin exacto

**Estado:** ✅ Done · escrito sobre master `e8b67a45`, rebasado sobre `9fc1b674` sin conflictos
(master avanzó durante la tarea), verde otra vez después del rebase

**Verde:** `:shared:testDebugUnitTest` · `:app:compileProdDebugKotlin` · `:app:compileMockDebugKotlin`

**Visto corriendo** (30-08, AVD `Pixel_8_Pro`, Android 17, `assembleMockDebug`, sha256 device↔local
`f0f7bdd4045d8479…`): las tres dudas en fila en la galería de marcadores, claro y oscuro, y la
diana en los cuatro tonos de identidad (verde · azul BT · gris · seleccionado). En el mapa real, el
escenario «Aparcado · aproximado (zona de 154 m)» deja los dos casos en la misma pantalla — el
coche de la zona con anillo + diana, el de Bluetooth limpio — que es justo la comparación que este
ticket existía para hacer posible. ⏳ Sin ver todavía en teléfono físico.

## Problema

Una sesión guardada como **zona aproximada** (`zoneRadiusMeters != null`) pinta exactamente el
mismo marcador que un pin de 3 m de precisión: mismo borde sólido, misma etiqueta opaca, misma
esquina vacía. `DET-DOUBT-MUST-REACH-THE-SCREEN-001` ya dibuja el **anillo** alrededor, pero el
coche de dentro sigue afirmando la misma certeza que uno exacto.

Caso de campo: pin `825dcb60` del 30-08 01:49 — zona de 250 m centrada a **157 m** del coche real,
fiabilidad 0.5, guardada por el timeout desatendido.

El anillo tapa el caso fácil (alejado, con el círculo entero en pantalla). No tapa el caso real: a
zoom de calle el borde del círculo está **fuera de pantalla** y lo único que se ve es un marcador
que parece exacto.

## Doctrina violada

*Fallo asimétrico: mejor falso negativo que falso positivo* — la app **midió** la duda, la
**guardó** y luego la **oculta** en la superficie donde el usuario decide a dónde caminar.
Misma familia que `DET-DOUBT-REACHES-REMOTE-001` (la duda no llegaba al diagnóstico) y
`DET-DOUBT-MUST-REACH-THE-SCREEN-001` (no llegaba al Historial): la duda se calcula y se pierde
por el camino.

## Señales / datos disponibles — la decisión del disparador

Dos candidatos, y **no son el mismo conjunto**. Corpus real de `users/{uid}/parkingHistory`
(proyecto `pap-26`, uids `fiypNb…` y `90lnZz…`, agosto 2026):

| doc | `detectionPath` | fiabilidad | ¿es área? |
|---|---|---|---|
| `5b0ef993` (24-08) | `closed_approximate_zone` | **0.5** | **sí** |
| `297c2313` (21-08) | `closed_approximate_pin` | **0.5** | **no — punto exacto** |
| `724befda` (27-08) | `safety_net_backfill` | **0.5** | no (accuracy 2.5 m) |
| `8646fc39` (27-08) | `kinematic+egress` | 0.85 | no |
| `b3b71800`, 12 más | `steps+egress` | 0.9 | no… |
| — | …salvo cuando `inferredPinDoubtRadius` lo degrada | **0.9** | **sí** |

**Fiabilidad 0.5 y "es un área" son ortogonales, medido en las dos direcciones**: hay 0.5 que son
puntos exactos (`closed_approximate_pin`, `safety_net_backfill`) y hay 0.9 que son áreas
(`DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001` degrada un pin inferido a zona por la accuracy del fix,
sin tocar la fiabilidad).

→ **El disparador es `isApproximate` (`zoneRadiusMeters != null`), nunca la fiabilidad.** Es
además el MISMO campo que dibuja el anillo: un solo dato decide el círculo y el marcador, así que
no pueden contradecirse. Si tirásemos de fiabilidad, un `closed_approximate_pin` llevaría la marca
de área **sin anillo alrededor** — el marcador diciendo una cosa y el mapa la contraria.

⚠️ `zoneRadiusMeters` sí viaja ya a Firestore (`DET-DOUBT-REACHES-REMOTE-001`, 30-08), pero ningún
documento remoto lo lleva todavía: todos los del corpus son anteriores. La tabla de arriba se
reconstruye por `detectionPath`, que es el que delata cuáles eran zonas.

## Diseño — un TERCER estado, en el mismo hueco, con otra palabra

`unconfirmed` y "zona aproximada" **no son el mismo estado** y no pueden dibujarse igual:

| | qué le pasa al usuario | se resuelve… |
|---|---|---|
| pregunta abierta | no sabemos si aparcaste | contestando, o venciendo el veredicto |
| zona aproximada | **sí aparcaste, está guardado**; no sabemos dónde exactamente | sólo si el usuario lo edita |

Reutilizar el vestido de `unconfirmed` (borde discontinuo + `?` + relleno al 72 %) le diría al
usuario que hay una pregunta pendiente, y le mandaría a buscar una notificación que ya no existe.

**Decisión:** el hueco de la esquina — el que ya inauguró el `?` — es el sitio donde este marcador
dice de qué duda se trata. La zona guardada lleva ahí una **diana** (disco del color de identidad
con un anillo y un punto central recortados en el relleno de la etiqueta): el gemelo en Canvas de
`Icons.Rounded.Adjust`, que es **el glifo que la app ya usa** para esto mismo en la fila del peek
(«Somewhere within %1$d m…», `ApproximateZoneRow`). Una superficie más hablando la palabra que ya
existía, no una palabra nueva.

Y lo que **no** cambia es lo que dice el estado:

| estado | borde | esquina | relleno etiqueta | carbody |
|---|---|---|---|---|
| exacto | sólido | — | 100 % | opaco, a todo color |
| pregunta abierta (`ASKING`) | **discontinuo** | **`?`** | 72 % | opaco, a todo color |
| zona guardada (`APPROXIMATE`) | sólido | **diana ⊙** | 100 % | opaco, a todo color |

- **Borde sólido y relleno opaco a propósito**: esto es un HECHO guardado. Lo provisional es la
  pregunta, no la zona.
- ⛔ **El carbody sigue opaco y a todo color en los tres**. La duda sobre el SITIO no se expresa
  dudando de QUÉ COCHE es. La identidad no es estado.
- **No dice dos veces lo mismo que el anillo**: el anillo dice **cuánto** de grande es la duda; la
  diana dice **que este pin es un centro, no un punto** — y es la única de las dos que sobrevive
  al zoom de calle, al pase `dim` y al recorte de pantalla.
- **El punto de posición de la etiqueta se queda como está**: sigue siendo el ancla del marcador
  ("dónde está clavado este dibujo"), no una afirmación sobre dónde está el coche. Convertirlo en
  anillo hueco de 5 px no es una señal que nadie lea de un vistazo, y sí un segundo dialecto para
  la misma frase.

### La exclusividad se modela, no se documenta

`unconfirmed: Boolean` pasa a ser `doubt: VehicleMarkerDoubt` (`NONE` / `ASKING` / `APPROXIMATE`).
Las dos dudas compiten por **un solo hueco físico** y no pueden coexistir (una pregunta abierta no
tiene sesión guardada todavía, así que no tiene radio). Con dos booleanos eso es un comentario que
alguien tiene que leer; con un enum es un estado que no se puede escribir.

## Criterio de éxito

- Una zona guardada se distingue de un vistazo de un pin exacto **y** de una pregunta abierta.
- El carbody sigue opaco y a todo color en los tres estados.
- Los 7 call sites cubiertos (`_DIM` y `_SELECTED` incluidos), con `_$themeKey` y con la duda
  horneada en el `contentId` (kmpmaps cachea el ráster por ese id).
- `parkingZoneRadiusMeters` entra en las claves del `remember` que construye la lista de
  marcadores, o el fallback no se re-rasteriza al cambiar de sesión.
- Previews + variantes en la galería mock.
- `:shared:testDebugUnitTest`, `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin` verdes.
- ⏳ **Verlo en device**: es puramente visual; el APK es la única prueba real.

## Consumidores auditados

| sitio | qué asume | estado |
|---|---|---|
| `PaparcarMapView` · handlers `MARKER_MY_CAR` / `_DIM` / `_SELECTED` (fallback Historial) | pin exacto siempre | **cubierto** — variante `_apx` por cada uno, alimentada por `parkingZoneRadiusMeters` |
| `PaparcarMapView` · 3 handlers por vehículo (`vehicleBadgeContentId`) | pin exacto siempre | **cubierto** — `v.isApproximate` horneado en el id |
| `PaparcarMapView` · `MARKER_MY_CAR_ASKING` | pregunta abierta | **exento** — no tiene sesión, luego no tiene radio |
| `PaparcarMapView` · `doubtCircles` / `fallbackDoubtCircle` | ya leen `zoneRadiusMeters` | **cerrado** (`DET-DOUBT-MUST-REACH-THE-SCREEN-001`) — misma fuente, ahora también para el marcador |
| `ParkingCenterPin` (colocar/editar aparcamiento) | pin exacto | **exento a propósito** — colocar a mano es justamente convertirlo en exacto; el preview debe mostrar el resultado, no el origen |
| `MyVehicleMarker` (teardrop legacy) | — | **exento** — sólo alcanzable desde previews y galería, sin call site de producción |
| `ParkingPeek` · `ApproximateZoneRow` | ya dice la duda en palabras | **cerrado** — de ahí sale el glifo que reusamos |
| `ParkingHistoryDetailScreen` | pasa `parkingZoneRadiusMeters` | **cubierto** vía el fallback |
| `StateGalleryScreen` + `PaparcarMapMarkersPreviews` | sólo tenían `unconfirmed` | **cubierto** — variante nueva en ambos |

**Strings:** ninguno. El marcador es un bitmap rasterizado por kmpmaps sin capa de semántica
(ningún estado de este marcador, tampoco `unconfirmed`, declara `contentDescription`), y las
etiquetas de la galería son de dev, no de producto. No hay key nueva → los 9 locales no se tocan.

## Hallazgo fuera de alcance

⚠️ **El tap del marcador de pregunta está muerto** (`PaparcarMapView.onMarkerClick`): la rama
`cid.startsWith(MARKER_MY_CAR)` se evalúa ANTES que la de `MARKER_MY_CAR_ASKING`, y
`"my_car_asking_lt".startsWith("my_car")` es `true` — así que el tap cae en la primera, busca una
sesión por coordenadas, no la encuentra, y `onAskMarkerClick()` **nunca se ejecuta**. Introducido
con `DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001` (`e47240fd`). No se toca aquí: cambia el
comportamiento de esa feature y merece su propia verificación en campo →
`docs/backlog/ui-the-ask-marker-tap-never-reaches-its-handler-001.md`.
