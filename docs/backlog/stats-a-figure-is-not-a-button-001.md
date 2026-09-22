# STATS-A-FIGURE-IS-NOT-A-BUTTON-001 · Una cifra no es un botón, así que no se pinta como uno

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#118**: *«¿por qué plazas cedidas tiene un fondo verde? No queda bien.»*

El tile `SPOTS_SHARED` de la rejilla de cifras se pinta con `primaryContainer` recortado a
`PapShapes.chip`. Lo puso `cc2b08566` [STATS-THE-ONE-GREEN-BELONGS-TO-WHAT-YOU-GAVE-001] hace dos
días, y el argumento de aquel ticket era bueno: *ceder una plaza es LA acción de este producto, y el
verde es marca y acción*.

El argumento sigue siendo bueno. Lo que falla es **la forma que se eligió para decirlo**.

## Doctrina violada

Dos, y las dos las tiene escritas el propio repo:

1. **`PapShapes.chip` + `primaryContainer` es literalmente el lenguaje de un chip seleccionado**
   [UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001]. Un tile así vestido, en una rejilla de
   seis cifras que no se tocan, anuncia una **afordancia que no existe**: parece que se pulsa, y no
   se pulsa.
2. **Un solo esqueleto, un solo énfasis** [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]. Cinco tiles
   sin caja y uno con caja rompen el ritmo de la rejilla: el ojo no lee «esto es lo que aportas»,
   lee «a éste le pasa algo».

Y hay un precedente en la MISMA rejilla que ya resolvió esta pregunta bien: el anillo verde de
`AUTO_DETECTED`. Ahí el verde es una **marca dentro** del tile, no la superficie del tile — y su
KDoc lo dice: *«el anillo dice "esto lo ha hecho la app"… la cifra de al lado sigue en `onSurface`»*.

## Diseño

**El verde se muda del FONDO al GLIFO.** El tile de contribución pierde su caja y gana el icono en
`PapColor.actionText` — el mismo verde, el mismo token, la misma anatomía que el anillo de al lado.

- La rejilla recupera un solo ritmo: seis tiles con la misma caja (ninguna).
- El significado no se pierde: el glifo de nivel 2 ya existe en cada tile para decir **de qué va la
  cifra**, así que teñirlo dice «y ésta es de las tuyas» sin inventar superficie.
- Y respeta la regla que aquel ticket ya citaba —*lo que se tiñe no es la palabra*— pero la aplica
  entera: aquí tampoco se tiñe la superficie. `label` y `value` siguen en `onSurfaceVariant` y
  `onSurface`.

⛔ **No se toca el veredicto.** `isContribution(key)` sigue siendo una lista cerrada de una clave,
construida por quien arma el tile y no por un booleano que cualquiera encienda — el aviso de
`cc2b08566` sobre el CO₂ y el gasto sigue vigente palabra por palabra.

## Criterio de éxito

- La rejilla no tiene ninguna caja de color; ningún tile parece pulsable.
- «Plazas cedidas» sigue distinguiéndose de un vistazo, por su glifo.
- El anillo de `AUTO_DETECTED` y el glifo de `SPOTS_SHARED` usan el MISMO verde.

## Consumidores auditados

`grep contribution` — el parámetro sólo tiene dos extremos:

| Sitio | Qué hace | Veredicto |
|---|---|---|
| `VehicleStatsBlock:845` `isContribution(key)` | decide QUIÉN es contribución | sin cambios |
| `VehicleStatsBlock:305` lo pasa al tile | fontanería | sin cambios |
| `PapStatTile:112` `.background(primaryContainer)` | **el bug** | pasa al `tint` del icono |

⚠️ Un tile de contribución **sin icono** se quedaría sin señal. Hoy no existe —`SPOTS_SHARED` trae
el suyo— pero el código no puede depender de eso en silencio: si algún día hay uno, que se vea en el
propio fichero. Anotado en el KDoc del parámetro.
