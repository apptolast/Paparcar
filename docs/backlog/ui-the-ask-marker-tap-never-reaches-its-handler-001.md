# UI-THE-ASK-MARKER-TAP-NEVER-REACHES-ITS-HANDLER-001 · El pin `?` se dibuja, pero su tap muere en la rama del coche aparcado

**Estado:** ✅ **Done** — en master (squash sobre `70e4d297`). ⏳ Queda verlo en device: el criterio de
usuario (tocar el `?` encuadra y abre la sheet) sólo se comprueba con una pregunta abierta viva.

**Procedencia:** hallazgo de `UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001` (30-08-2026), abierto con
`67dcbc83`. Este documento sustituye a aquella nota: de las dos opciones que planteaba se ejecuta la
**2** (el rol deja de deducirse de cómo empieza la cadena), no la 1 (mover la rama arriba).

## Problema

`e47240fd` [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] dio a la pregunta «¿has aparcado?» un
sitio en el mapa: un marcador con marco discontinuo y un `?`. Tocarlo debía encuadrar ese punto y
abrir la sheet expandida, que es donde están las dos respuestas (`HomeScreen.kt`, `onAskMarkerClick`).

**Tocarlo no hace nada.** Ni encuadra, ni abre la sheet. El pin se dibuja bien; es el gesto lo que
está muerto, y lo está en silencio: no hay log, no hay error, no hay feedback.

La causa está en `PaparcarMapView.kt`, `onMarkerClick`:

```kotlin
cid?.startsWith(MARKER_MY_CAR) == true ||        // "my_car"
    cid?.startsWith("vehicle_badge_") == true ->
    sessionIdByCoords[marker.coordinates]?.let(onMyCarClick)
// The ask marker resolves to no session on purpose — see [onAskMarkerClick].
cid?.startsWith(MARKER_MY_CAR_ASKING) == true -> onAskMarkerClick()   // "my_car_asking"
```

El marcador se emite con `contentId = "${MARKER_MY_CAR_ASKING}_$themeKey"` → `my_car_asking_lt`.
Y `"my_car_asking_lt".startsWith("my_car")` es **true**: la primera rama gana siempre y la segunda
es código inalcanzable desde el día que se escribió.

Lo que remata el fallo silencioso es la línea que sí se ejecuta. `sessionIdByCoords` se construye
sólo con `parkedVehicles`, y el ask marker está en el candidato **no confirmado** — que por diseño
no puede coexistir con una sesión aparcada del mismo coche (confirmar es lo que cierra la ventana).
El lookup devuelve `null`, el `?.let` no hace nada, y no hay `else` que lo note.

El comentario de la línea de arriba —*«The ask marker resolves to no session on purpose»*— describe
literalmente el bug creyendo que describe una decisión.

**Alcance de usuario:** la pregunta se sigue pudiendo responder por sus otras dos superficies (la
notificación de la bandeja y la fila con dos botones en Home). Lo muerto es sólo el atajo del mapa.

## Doctrina violada

- **Sistemas, no parches.** La gramática de ids de marcador y quien la lee (el router de taps) son
  el mismo sistema y viven separados: los ids se construyen en cinco builders repartidos por un
  fichero de 2.000 líneas y se leen por prefijo en un `when` cuya corrección depende del ORDEN de
  las ramas. Reordenar la rama del ask arregla el síntoma y deja la trampa armada para el próximo
  `my_car_*`.
- **Una prohibición sin testigo no es un chequeo** [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001].
  `grep` de `MARKER_MY_CAR|onMarkerClick` en `commonTest` + `androidUnitTest` → **cero**. El routing
  de taps del mapa no tiene ni un test, porque vive dentro de una lambda de un `@Composable`: no hay
  dónde llamarlo. Por eso el bug pudo nacer con su propio commit y pasar la suite en verde.

## Señales / datos disponibles

Ninguna telemetría: el tap no llega a ningún sitio que registre nada. El bug es estático y se
demuestra leyendo los ids que el propio fichero emite.

Inventario completo de `contentId` emitidos hoy (`PaparcarMapView.kt`), con su destino esperado:

| id emitido | ejemplo | destino |
|---|---|---|
| `vehicleBadgeContentId(...)` | `vehicle_badge_abc12345_MEDIUM_SUV_RED_nrm_act_pt_lt` | coche aparcado |
| `fallbackParkingContentId(my_car…)` | `my_car_lt`, `my_car_dim_lt`, `my_car_selected_apx_dk` | coche aparcado |
| ask | `my_car_asking_lt` | **la pregunta** ← roto |
| `zone_<id>_(prv\|pub)_(nrm\|dim)` | `zone_z1_pub_nrm` | zona |
| `freeSpotContentId(...)` | `free_spot_high_nrm`, `free_spot_er3_sel`, `free_spot_low_m_nrm` | plaza |
| `cluster` / `cluster_dim` | — | inerte (a propósito) |
| `departure` / `arrival` | — | inerte |
| `user_location_dot` | — | inerte |
| `loc_active_<body>_<color>` | `loc_active_SEDAN_RED` | inerte |

Sólo hay una colisión de prefijo real: `my_car_asking` bajo `my_car`. Las otras dos (`my_car_dim`,
`my_car_selected`) comparten rama **a propósito**.

Y una cuarta que sí acierta, pero por suerte: `my_car_apx_lt` (el fallback de una sesión guardada
como zona, nacido en el mismo `67dcbc83` que abrió esta nota) quiere caer en `onMyCarClick` y cae —
porque su familia resulta ser la que gana el orden. Nadie lo comprobó al añadirlo. Ese es el motivo
de que el arreglo no sea reordenar: el siguiente `my_car_*` volvería a jugárselo a la posición.

## Diseño

Tres piezas, todas sobre el mismo invariante: *el destino de un tap se decide por el id del
marcador, la decisión es total, y ningún id puede pertenecer a dos destinos.*

1. **Sacar el ask del namespace `my_car`.** `MARKER_PARKING_ASK = "parking_ask"`. Ya no es
   prefijo de nada ni nada es prefijo suyo, así que la corrección deja de depender del orden.
   Renombrar la constante mueve a la vez el emisor y la clave del registro de bitmaps
   (`customMarkerContent`), que la comparten — el rename es atómico por construcción. Invalidar la
   caché de kmpmaps con una key nueva es inofensivo: se rasteriza una vez.
   Vocabulario: es TU sesión la que está en duda, no una plaza de la comunidad, así que `parking_`
   es la palabra correcta [COPY-SPOT-IS-NOT-A-PARKING-001].
2. **Un router puro y testeable.** El `when` sale de la lambda del composable a
   `resolveMarkerTapTarget(contentId): MarkerTapTarget` (sealed) en su propio fichero, junto a las
   constantes de id que lee. Es el patrón ya establecido del proyecto para predicados compartidos
   (`SentryWakeCooldown.kt`, `VehicleFenceOwnershipPolicy.kt`): función pura de nivel superior,
   directamente testeable, sin ceremonia de clase inyectada. El composable pasa a hacer sólo lo que
   le toca — el `when` sobre el target, que es I/O de UI.
3. **El testigo.** El test no escribe ids a mano: los construye con **los mismos builders que usa el
   render** (por eso pasan a `internal`) y afirma el destino de cada uno. Un builder que cambie
   mañana arrastra al test consigo. Y una comprobación cruzada — ningún id emitido resuelve a un
   destino que no sea el suyo — que caza la próxima colisión de prefijo sin que nadie la anticipe.

Lo que **no** se hace: mover los cinco builders a un fichero propio. Están entrelazados con las
`AndroidMarkerOptions` y el registro de bitmaps; sacarlos sería un diff enorme por estética. Basta
con que sean alcanzables desde el test.

## Criterio de éxito

- Tocar el pin `?` con una pregunta abierta encuadra el punto y abre la sheet expandida. ⏳ **sin
  ver en device** — necesita una pregunta abierta real.
- `MapMarkerIdsTest` en verde, y **rojo** si se revierte el rename. ✅
- Tocar un coche aparcado, una zona y una plaza siguen haciendo exactamente lo de antes. ✅ los ids
  emitidos son byte a byte los mismos salvo el del ask (el de zona ahora lo construye una función en
  vez de dos literales iguales, y produce la misma cadena: `zone_<id>_pub_nrm`).

## Lo que enseñó la falsación

Se probaron las dos direcciones, y **sólo una de las dos reglas del test ve el bug**:

| Experimento | Regla 1 (cada id a su familia) | Regla 2 (familias disjuntas) |
|---|---|---|
| `MARKER_PARKING_ASK` de vuelta a `"my_car_asking"` | 🟢 **verde** | 🔴 rojo |
| Rename bueno + ramas del `when` intercambiadas | 🟢 verde | 🟢 verde |

La primera fila es el hallazgo. El test obvio —*«¿el id del ask resuelve a `ParkingAsk`?»*— sigue
pasando con el id roto, porque el router mira la rama del ask primero: está midiendo el ORDEN de las
ramas, no la gramática. Es exactamente el mismo verde falso que dejó nacer el bug. Lo que caza la
colisión es la regla 2, que no depende de cómo esté escrito el `when`. La segunda fila es la
afirmación positiva: con los ids disjuntos, reordenar el `when` ya no cambia nada.

**1990 tests**, 0 fallos (master `70e4d297`: 1985). `:app:compileProdDebugKotlin` +
`:app:compileMockDebugKotlin` en verde.

## Qué NO cambia

- Ni un string nuevo → los 9 locales no se tocan.
- Ni pantalla ni estado nuevo → Dev Catalog y galería de estados quedan como están.
- Ninguna decisión de detección: el marcador se emite en los mismos casos y en el mismo sitio. Lo
  único que cambia es a dónde va el dedo.

## Consumidores auditados

Barrido `grep` de todo lo que decide por id de marcador:

| Sitio | Estado |
|---|---|
| `PaparcarMapView.onMarkerClick` | ✅ el bug; pasa a delegar en `resolveMarkerTapTarget` |
| `PaparcarMapView.onCircleClick` | ✅ **exento**: no mira ids, resuelve por coordenadas del centro (`zoneIdByCoords` → `sessionIdByCoords`). El ask no dibuja círculo |
| `customMarkerContent` (registro de bitmaps) | ✅ cubierto: usa la MISMA constante que el emisor, el rename lo arrastra |
| Literal `"vehicle_badge_"` duplicado en el `when` | ✅ cerrado: pasa a constante compartida con el builder |
| Id de zona construido a mano en DOS sitios (lista + `zoneHandlers`) | ✅ cerrado de paso: un `zoneContentId(id, isPrivate, dim)` para ambos. Eran dos literales que tenían que coincidir exactamente o kmpmaps no encuentra handler y pinta el pin por defecto |
| `MARKER_MY_CAR_ASKING` fuera de `PaparcarMapView.kt` | ✅ ninguno (`grep my_car` → sólo claves de strings de Vehículos, sin relación) |
| Tests que toquen routing de mapa | ✅ ninguno existía — este ticket crea el primero |
