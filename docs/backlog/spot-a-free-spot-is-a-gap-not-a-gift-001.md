# SPOT-A-FREE-SPOT-IS-A-GAP-NOT-A-GIFT-001 · Una plaza libre es un HUECO, no un regalo

**Estado:** ✅ Done — la plaza libre se dibuja con **la plaza pintada**: un trapecio en perspectiva a
rayas, con el punto de su posición en medio.

## Problema
[SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001] puso la mano + corazón
(`Icons.Rounded.VolunteerActivism`) como glifo de la plaza libre en TODAS sus superficies. Donde el
icono acompaña a un texto que nombra el ACTO de ceder está bien —el tile «plazas cedidas» y el
detalle de historial—; en el OBJETO «plaza» no:

1. **Afirma un hecho falso en el carril manual.** `ReportCenterPin` dibuja
   `FreeSpotMarker(isManual = true)` → mano + corazón, pero el copy de ese mismo flujo dice
   `home_report_subtitle` («A free spot you've seen and want to share with others») y
   `first_steps_spot_report_sub` («A space someone else left — not your own parking»). En un reporte
   manual NADIE cedió nada: se fue alguien que ni usa la app, y un testigo lo vio.
2. **Rompe la doctrina de iconos** («concepto de Paparcar → vector propio»).
3. **Degrada hacia el malentendido.** A ~16,6 dp, blanco sobre color, lo único que sobrevive legible
   es EL CORAZÓN. ✅ Confirmado en device: la mano queda en un muñón.
4. **Es redundante.** El color de frescura, el anillo TTL y el megáfono ya dicen el estado.

## Diseño
**Un solo token mueve el objeto**: `PaparcarIcons.CommunitySpot` pasa de wrapper de Material a vector
propio, y con él se mueven las cinco superficies del OBJETO (puck del mapa, puck de lista, lead del
peek, pin de reporte y pestaña «Free spots»). Las dos del ACTO no lo leen: tienen su propio token,
`PaparcarIcons.SpotGiven`, con el mismo dibujo de siempre.

El glifo es **la plaza pintada en el suelo, vista en perspectiva desde la calle**: un trapecio a
rayas con el punto de su posición en medio. Rejilla de 24, tinta **21,9 × 17,5**, trazo **2,5**.

⛔ **Las rayas son piezas RELLENAS**, no un `stroke-dasharray`: cada una es una cápsula (dos rectas y
dos arcos). Eso es lo que la hace un `ImageVector` normal que el `Icon` anfitrión tiñe, en vez de
obligar a un Canvas — que es lo que exige un trazo discontinuo de verdad (CLAUDE.md § Iconos).

**La regla que ordena las rayas: todo hueco mide al menos lo que el trazo.** Un hueco más estrecho
que su raya no se ve, y la figura se lee como una línea continua. Y el mecanismo es contraintuitivo:
**engordar el trazo ESTRECHA el hueco**, porque los casquetes redondos comen espacio por los dos
lados. Quien toque este dibujo, que mida los huecos antes de dar nada por bueno.

## ⛔ Lo que se probó y se cayó — no volver a proponerlo
El ticket daba por hecho que la plaza se dibuja «como un HUECO». **Eso no existe.** Medido
rasterizando cada candidato a 17 px blanco sobre verde, y en device a 16,6 dp:

| Familia | Candidatos | Qué se lee de verdad |
|---|---|---|
| **Hueco** | U rellena · barra con muesca · fila de plazas con la de en medio libre | **todas salen la letra U.** Lo genera la metáfora: un hueco rodeado por sus vecinos ES una U. Y el mapa ya tiene una letra, la P del parking |
| **Flechas y marcos** | doble flecha · flecha abajo · marco de esquinas | «redimensionar», «descargar», «pantalla completa» |
| **Objetos** | coche fantasma · topes de rueda + bordillo | un **bolso** y una **cara** |
| **Mano + otra cosa** | mano+casa · mano+llave · mano abierta · apretón · saludo | la mano siempre pierde: gana la casa, la llave (→ «coche», que es lo contrario) o sale un borrón. La abierta se lee **STOP** |

Las tiras rasterizadas de cada familia están en
[`spot-a-free-spot-is-a-gap-not-a-gift-001/`](spot-a-free-spot-is-a-gap-not-a-gift-001/): los huecos
(`1`), las flechas y marcos (`2`), los objetos (`3`) y la U ya montada en device (`0`), que es la que
más lejos llegó antes de caerse. Están para que nadie repita la ronda creyendo que no se probó.

**La causa es de tamaño.** A 16,6 dp caben tres o cuatro rasgos: da para una LETRA o para un
pictograma ya conocido, no para una escena. Por eso **«mano + otra cosa» no puede funcionar** —son
dos objetos y uno se come al otro—, y por eso las dos versiones anteriores (`Person`, mano + corazón)
dibujaban a la PERSONA y no al sitio.

Lo que sí funciona es lo que hay ahora: **no dibujar el hueco, dibujar la PLAZA** —la marca vial que
la delimita— y dejar que el punteado diga «vacía».

## Medidas que hay que conservar
- **`COMMUNITY_GLYPH_SIZE` se re-mide, no se hereda**: 28 con la `Person`, 26 con la mano + corazón,
  24 con la plaza pintada. La caja no mide el puck: mide cuánto hay que abrirla para que la TINTA de
  ESE dibujo ocupe lo mismo que la de los demás.
- **La caja es del GLIFO, no del overlay**: el coche de `RELEASING`
  [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] es Material y conserva su 26 en
  `RELEASING_CAR_GLYPH_SIZE`. Con una sola constante, re-medir uno movía al otro sin que nadie lo
  midiera.
- **El centrado se calcula** desde `SPOT_PUCK_CENTER_X/Y`, no dos literales que había que
  re-derivar a mano al cambiar de caja.
- Geometría del puck que el glifo respeta: anillo TTL r22 con pluma 3,4 (borde interior ~20,3), disco
  del badge en (56,15) r13. Con la caja en 24, la esquina más lejana de la tinta cae a 14,0 del
  centro y el extremo de la raya de arriba a 19,4 del centro del badge.

## Consumidores auditados
**OBJETO — cambia**, y los cinco por el MISMO token, sin tocar un solo call site:

| Consumidor | Cómo llega | Visto |
|---|---|---|
| `CommunitySpotOverlay` en `FreeSpotMarker` — puck: FRESH · RECENT · STALE · MANUAL · seleccionado · en camino · ⊙ | token | ✅ galería |
| `SpotPuckIcon` — puck sin cola de las filas (`HomeSpotRows`) | mismo overlay | ✅ app real |
| Lead del peek (`PapSheetLead.CommunitySpot` → `SpotPeek`) | vía `SpotPuckIcon` | ✅ app real |
| `ReportCenterPin` → `FreeSpotMarker(isManual = true)` | token | ✅ galería |
| Pestaña «Free spots» (`HomeNearbyLensRow`) | token | ⚠️ sólo se ve con `count == 0` |
| `PeekTimeRowOptionsPreviews.kt:173,249` | token | compila |

**ACTO — no cambia de dibujo, gana nombre:** tile `SPOTS_SHARED` (`VehicleStatsBlock.kt`) y
`parking_detail_community_gave_it` (`ParkingHistoryDetailScreen.kt`) → `PaparcarIcons.SpotGiven`.
Antes los dos llamaban a `Icons.Rounded.VolunteerActivism` a mano; ahora el acto tiene fuente única
igual que el objeto, y mover uno ya no puede mover al otro en silencio.

**EXENTOS:** el megáfono `Campaign` (es la ACCIÓN de avisar) · el `DirectionsCar` + `Logout` del puck
`RELEASING` (es el coche que sigue dentro) · `CarParkMark` y `LocalGasStation` (las otras dos cosas
de la fila de pestañas) · el clúster (sólo cifra).

**Comentarios barridos** que describían el glifo anterior: `PaparcarMapView.kt` ×2,
`PaparcarMapMarkers.kt` ×3, `HomeSpotRows.kt` ×1.

## Criterio de éxito
- ✅ `./gradlew testAll --rerun-tasks` verde — los guards de Konsist se ejecutaron de verdad.
- ✅ `:app:assembleMockDebug` · ✅ `:app:assembleProdRelease` firmada.
- ✅ Galería «Mapa · marcadores → Showcase» en Pixel 8: los cinco tiers con el glifo nuevo, sin pisar
  el badge del megáfono ni el anillo TTL.
- ✅ En el **Oppo**, `prodRelease` instalada por `install -r` con sha256 verificado en el device.
- ⛔ **El marcador EN EL MAPA no se puede ver en `mock`**: el flavour no lleva key de Maps y el mapa
  sale en negro. Lo de la galería es el MISMO composable, pero sobre teselas reales sigue sin verse.

## Lado iOS
El glifo es un `ImageVector` de `commonMain` y los cinco consumidores del objeto también, igual que
`CarParkMark`. **iOS lo hereda sin una línea de `iosMain`**: no hay hueco que apuntar.

## Trampa encontrada por el camino (no es de este ticket)
Una **debug no puede instalarse encima de una release**: las firmas no casan y `install -r` muere en
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Pasó en el Oppo y en el emulador, los dos con la release 1.3.0
puesta. La salida NO es desinstalar —eso se lleva por delante la sesión y los diagnósticos—, es
compilar `assembleProdRelease` con la clave de verdad, que entra por encima sin tocar los datos. Para
eso el worktree necesita los CUATRO ficheros gitignored: `keystore.properties` **y** `keystore/release.jks`.
