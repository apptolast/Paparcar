# STATS-THE-ONE-GREEN-BELONGS-TO-WHAT-YOU-GAVE-001 · el verde de las cifras

**Estado:** ✅ Done · rama `feat/STATS-THE-ONE-GREEN-BELONGS-TO-WHAT-YOU-GAVE-001` · issue #52
Abre #107 (el «ahorro monetario» que el ticket nombraba y no existe).

## Problema, y por qué no se hizo lo que pedía
> «Destacar el bloque de CO₂ y Ahorro monetario como *Impacto Positivo* con un badge o color verde sutil.»

Tres hechos, comprobados antes de tocar nada:

1. **`co2Kg` es lo que tu conducción EMITE** y **`costEstimate` es lo que PAGAS**. Ninguno es un
   impacto positivo. Pintarlos de verde le diría al usuario que emitir 24 kg está bien.
2. **No existe «ahorro monetario».** Sólo hay gasto. → #107, con la pregunta escrita: *¿ahorro
   frente a qué?*
3. `PapStatTile` ya tenía escrita **la decisión contraria**: *«Direction is a SHAPE, not a colour.
   Green-up/red-down would also be a judgement the app has no business making: more kilometres is
   not "bad" and less CO₂ is not "good" for someone who simply travelled less.»*

Consultado el user (21-09) con las tres salidas sobre la mesa, eligió: **el verde va a lo que sí
aportas**. CO₂ y gasto se quedan neutros.

## Diseño
La única cifra de las once que el usuario **da** en vez de gastar es **las plazas cedidas a la
comunidad** — y es la razón de ser del producto. Se lleva la superficie de marca
(`primaryContainer`), con la palabra en `onSurface`: *lo verde es la superficie, no la palabra*
[UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001].

⛔ **No es un verde nuevo.** Lo que la doctrina de color prohíbe es que el verde signifique una COSA
nueva, no que aparezca dos veces por la misma. Aquí dice «marca y acción», igual que el anillo de la
tasa de detección de al lado: aquél dice «esto lo ha hecho la app», éste dice «esto lo has hecho
tú». Mismo significado, dos objetos.

⛔ **El veredicto vive en una función, no en un booleano suelto**: `isContribution(key)`, una lista
cerrada de una sola clave. Escrito como flag en cada `StatTileData`, el día que alguien añada un
tile lo copiaría del de al lado — y el de al lado, tarde o temprano, es el gasto.

## ⛔ Lo que se intentó y los tests pararon
Adelantar la aportación al **primer puesto** de la rejilla. Tres testigos existentes lo rechazaron,
y uno se llama `should_leadWithSpend_when_itExists_becauseItIsTheFigureYouCanActOn`: **el gasto
encabeza porque es la cifra sobre la que puedes actuar**, y eso es una decisión tomada, no una
casualidad del orden.

Así que la aportación se destaca por COLOR y no por posición — y resulta ser mejor lectura: el
primer puesto dice «empieza a leer por aquí», el verde dice «esto lo diste tú». Dos relieves
distintos para dos afirmaciones distintas.

## Criterio de éxito
`StatContributionTest`, 4 testigos. El que importa es
`should_neverMarkACost_as_aContribution`: CO₂, coste, combustible, energía y tiempo de búsqueda
**no pueden** marcarse como aportación. Es el que hay que mirar antes de ampliar la lista.

3586 tests, 0 fallos. Sin strings nuevos. La galería ya tenía variantes de «Cifras», así que la
superficie nueva sale sola — `SPOTS_SHARED` está siempre en la rejilla.

## Consumidores auditados
`PapStatTile` tiene **1** consumidor real (`StatTileGrid`), que pasa el flag desde `StatTileData`.
El resto de tiles ni lo mencionan, así que su valor por defecto —`false`— es el correcto para las
diez restantes sin tocar ninguna.
