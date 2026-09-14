# UI-A-HALO-MUST-FOLLOW-THE-GLYPH-NOT-ITS-BOX-001 · el halo del rótulo de zona era un CUADRADO, y le comía la punta al pin

**Estado:** ✅ Done — en master · ⏳ pendiente de verse en device

## Problema

El user lo reportó como *«el borde del marcador queda recortado y no llega a verse completo»*
(captura del 14-09-2026). **No hay recorte**: el halo está entero, pero tiene la forma equivocada.

`ZoneMarker` dibuja su halo de papel componiendo el rótulo 8 veces desplazado
([UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001](ui-a-zone-is-an-area-not-a-pin-001.md), porque un `Icon` no se
puede trazar). Los 8 desplazamientos son `(±1,0) (0,±1) (±1,±1) × ZONE_LABEL_HALO`, o sea **las
ocho posiciones de un cuadrado**: las cuatro diagonales caen a `1.5·√2 ≈ 2.12 dp`, un 41% más lejos
que las ortogonales. La unión de esas copias no es el glifo engordado, es el glifo **dilatado por un
cuadrado**, y dilatar una punta con un cuadrado la convierte en una barra plana.

Volcado de píxeles de la captura (pin izquierdo; `G` = verde del glifo, `#` = halo):

```
377 ...............#########..##########....   ← aquí acaba la punta verde
378 ................###################.....
379 .................#################......
380 ..................###############.......
381 ....................##..##...##.........   ← tres pezones sueltos
```

Esa última fila es la prueba de que nada está recortado: son las tres copias de abajo
(`dy=+1`, `dx=-1/0/+1`) asomando por separado. Si el bitmap clipara, la fila final sería continua.

Simulación de la misma geometría (izquierda, hoy) contra la propuesta (derecha):

```
SQUARE (hoy)            CÍRCULO 12 (fix)
 40 #######GG#######      40 ..#####GG#####...
 41 .##############.      41 ..############...
 42 .##############.      42 ..############...
 43 ..###.####.###..      43 ...##########....
 44 ..###.####.###..      44 ....########.....
 45 ..###..##..###..      45 .....#.##.#......
```

## Doctrina violada

Ninguna regla escrita se rompe — la que falla es la promesa del propio componente: *«darle a glifo y
nombre el MISMO casing, que es lo que hace que se lean como un solo objeto dibujado por el mapa»*.
Un casing sólo es el mismo en todas las direcciones si el vecindario es **isótropo**, y el de 8
vecinos de un cuadrado no lo es. En el nombre no se notó porque el texto no tiene puntas; el glifo
de la zona (`Icons.Rounded.Place` y familia) sí.

## Señales / datos disponibles

- Captura del user del 14-09-2026 (dark, density 3 ⇒ `ZONE_LABEL_HALO` = 4.5 px).
- Volcado de píxeles + simulación de ambas dilataciones, arriba: reproducen la captura exactamente.
- Afecta a los dos sujetos que usan `ZoneMarker`: la zona guardada y `ZoneCenterPin` (el pin que se
  arrastra al dar de alta), que es el mismo rótulo — por eso en la captura fallan los dos.

## Diseño

Las 8 posiciones del cuadrado pasan a **12 direcciones sobre una circunferencia de radio
`ZONE_LABEL_HALO`**. El invariante se escribe donde vive la constante, no en el sitio donde mordió:

- radio CONSTANTE ⇒ el casing mide lo mismo en todas las direcciones, que es lo que el componente
  ya decía hacer;
- ninguna copia excede `ZONE_LABEL_HALO` en ningún eje ⇒ el `padding(ZONE_LABEL_HALO)` del `Box`
  sigue siendo exactamente el hueco que necesita (antes las diagonales lo desbordaban en
  intención, aunque no en recorte: cada eje por separado seguía cabiendo);
- 12 y no 8: con 8 sobre la circunferencia el hueco angular es de 45° y a tamaño de marcador el
  casing se ve poligonal. 12 pasadas son 12 composiciones de una fila diminuta dentro de un bitmap
  que kmpmaps cachea por `contentId` — se pagan una vez, no por frame (mismo argumento que ya
  justificaba las nueve).

## Criterio de éxito

- El pin del rótulo de zona conserva su punta, con halo de grosor uniforme, en las dos superficies
  (zona guardada y pin de colocación) y en los dos temas.
- Ningún cambio en la caja medida del marcador: el ancla (0.5, 0.5) y el umbral `zoneLabelShowsName`
  siguen intactos.
- Verificado EN DEVICE con la misma zona de la captura.

## Consumidores auditados

`grep` de los sitios que dan por hecho la geometría del halo:

- `ZoneMarker` (`PaparcarMapMarkers.kt`) — único que usa `ZONE_HALO_OFFSETS` ⇒ es el único sitio.
- `ZoneCenterPin` — delega en `ZoneMarker` ⇒ cubierto sin tocarlo.
- `FuelStationMarker` — comparte *gramática* de rótulo pero tiene cápsula propia y no halo de 8
  copias ⇒ no aplica. (Ojo histórico: la gasolinera llevaba los alphas PRESTADOS de la zona, y ese
  préstamo ya se cortó en `UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001`.)
- `ZONE_LABEL_HALO` — sigue siendo el grosor del casing; no cambia de valor ni de significado.
