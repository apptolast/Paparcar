# WIDGET-A-TALL-WIDGET-HANGS-FROM-THE-TOP-001 · La lista del widget flotaba en medio, y el umbral que la enciende estaba a 2 dp del layout que nunca debe encenderla

**Estado:** ✅ Done — en master (squash 08-09-2026)

> Los dos defectos salieron **mirando el emulador** justo después de mergear
> `WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001`. Ninguno lo podía ver un test: las filas, su orden y sus
> acciones por coche estaban verdes mientras la cosa se veía mal.

## Problema

**1 · La lista flotaba.** `WidgetContent` pinta su `Column` raíz con
`verticalAlignment = Alignment.CenterVertically`. Con UNA fila eso es correcto — se centra en su
caja. Con una LISTA deja dos bandas de aire muerto, arriba y abajo, casi tan altas como las propias
filas. Una lista cuelga del borde superior; centrarla es tratarla como si fuera un solo dato.

**2 · El umbral `TALL` estaba mal medido, y por dos dp.** El cubo alto se declaró en **110 dp**.
Medido en el emulador (densidad 480, `wm density`): el widget de **una celda dibuja 108 dp**.

| Medida | px | dp |
|---|---|---|
| Widget 3×1 (el que ve todo el mundo) | 323 | **108** |
| El mismo estirado a 3 celdas | 1041 | 347 |

O sea que el umbral que decide "esto es una lista" estaba **2 dp por encima del layout que nunca
debe dispararlo**. En cualquier launcher con la celda un pelo más alta —y varían entre ~100 y 140 dp
por densidad y por número de filas de la rejilla— el widget corto de siempre habría empezado a
pintar la lista sin que nadie la pidiera. No falló aquí por suerte, no por diseño.

**3 · Y de paso: las filas que no caben se cortaban en silencio.** Un `Column` de Glance no hace
scroll. Con el cubo alto en un tamaño justo y tres coches, el tercero simplemente no se veía — y un
widget que **esconde** un coche es peor que uno que dice "+1 más".

## Doctrina violada

Ninguna regla escrita: es que el layout no respondía al alto que él mismo pidió responder. Lo más
cercano es la regla de este proyecto de **medir en vez de suponer** — el 110 dp salió de una
estimación de cuánto ocupan dos filas, no de mirar cuánto mide el widget corto.

## Diseño

- **Alineación por modo**: `verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically`.
  El `LocalSize` se lee **por encima** de la `Column`, porque ahora la alineación también depende de
  él. Una fila se centra; una lista cuelga de arriba.
- **`TALL_SIZE` 110 dp → 150 dp**: por encima de cualquier widget de una celda (~100–140 dp) y por
  debajo de cualquiera de dos. El número sale de la medición de arriba, y queda escrito junto a la
  constante para que nadie lo vuelva a estimar.
- **`rowsThatFit(height)`**: cuántas filas caben de verdad (`ROW_BLOCK_DP` = 50, que es lo que ocupa
  una fila con sus botones de 40 dp, más el hueco). Lo que sobra **no se corta**: vuelve al contador
  `+N más` de la primera fila, que ya existía. En la rama de conducción se descuenta una fila,
  porque la línea del viaje ya ocupa la suya.

## Criterio de éxito

- Widget alto → filas pegadas arriba, sin banda de aire encima.
- Widget de una celda → sigue centrado y sigue siendo el layout corto, con margen de sobra (108 dp
  frente a un umbral de 150).
- Con más coches de los que caben, el último visible dice "+N más"; ninguno desaparece.
- El hueco bajo la última fila se queda: es el tamaño que eligió el usuario, no un defecto.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `WidgetContent` (alineación + lectura de `LocalSize`) | **cerrado** |
| `Parked` / `Driving` en modo alto | **cerrado** — ambos pasan por `rowsThatFit` |
| `CarRow` | **exento** — no decide alto, lo decide quien la lista |
| `parked_widget_info.xml` | **exento** — el `resizeMode` ya era correcto; el problema era el cubo |
| `ParkedWidgetStateTest` | **exento** — el estado no cambia; esto es puro layout de Glance |

## Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` ✅ · `:shared:testDebugUnitTest` ✅
- ⚠️ **Esto no se puede probar con tests** y por eso el ticket existe: el modelo estaba bien y la
  pantalla estaba mal. Verificado en emulador (densidad 480), redimensionando el widget a mano.
- ⏳ En mano: en los dos móviles, que es donde el alto de celda cambia de verdad.

## Notas

Cierra dos defectos de `WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001` D5, encontrados al mirarlo. Sin
dominio, sin strings, sin detección.
