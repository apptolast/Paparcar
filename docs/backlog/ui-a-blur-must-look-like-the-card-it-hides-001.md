# UI-A-BLUR-MUST-LOOK-LIKE-THE-CARD-IT-HIDES-001 · El cristal tiene que tener detrás la forma de su tarjeta

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Abierto el 2026-09-16 desde master `8575d427`, fleco inmediato de
[`money-a-blur-is-only-honest-when-there-is-something-behind-it-001.md`](money-a-blur-is-only-honest-when-there-is-something-behind-it-001.md).
**Origen:** el user lo vio en el Oppo y dijo *«queda fatal, está como entrecortado, debería simular
al menos un poco cada card, el blur de LinkedIn mola más»* + *«y una descripción al menos que te
explique qué es cada cosa»*.

## Problema

La primera versión pintaba **tres barras grises iguales** para las cinco cards, con blur de 7 dp.
En device no se leía como contenido tras un cristal: se leía como una lista que no ha cargado —
justo el significado que `PapShimmerBox` ya tiene en esta app. Y al no haber ni una palabra fuera
del borrón, la card **no vendía nada**: el título solo (*«Tus sitios»*) no dice qué hace esa
pantalla si nunca la has visto.

Lo que hace legible un cristal esmerilado no es el desenfoque: es que **la mancha de debajo tenga la
geometría de lo que tapa**. Eso es lo que hace LinkedIn.

## Doctrina

- Se mantiene intacta la del ticket padre: **bajo el cristal va una FORMA, jamás una cifra** — ni
  las reales (viven al otro lado de la puerta de entitlement y no la cruzan para emborronarse) ni
  inventadas, que sería mentirle al usuario sobre sus propios datos.
- Nueva, y es la que da nombre a este ticket: **la forma tiene que ser la de ESA card**. Una silueta
  genérica es un esqueleto, y un esqueleto promete que esperando llega.
- **Las palabras no se difuminan.** La descripción habla de la FUNCIÓN, nunca de los datos del
  lector, y por eso puede leerse entera con el cristal puesto.

## Diseño

1. **`PapLockedPreview`** (antes `PapLockedShape`) — tres siluetas que copian la geometría real:
   - `Rows` (ritmo, resumen del mes): etiqueta larga a la izquierda + cifra corta a la derecha, ×3,
     y el pie de caption debajo — la fila exacta de `StatsPaceCard`.
   - `ListRows` (tus sitios, conclusiones): tile de icono 40 dp + nombre + meta line, con su
     divisoria entre filas — lo que dibuja `PapListItem`.
   - `Grid` (cuándo aparcas): eje de horas, 7 días × 6 franjas con intensidades desiguales y la
     leyenda debajo — el mismo recuento que `StatsWhenCard`.
2. **Descripción obligatoria** (`description`, ×5 strings ×9 locales): una línea que dice qué te
   contaría esa card. Va **sin blur**, en `body`/`onSurfaceVariant`, entre el título y el cristal.
3. **Blur de 12 dp** (era 7) y `BlurredEdgeTreatment` con la forma de la card + `clip`: sin eso la
   mancha se salía del bloque que la origina, que es de donde venía el aspecto «entrecortado».
4. **Bloques con dos pesos** (`STRONG_ALPHA` para nombres y cifras, normal para el resto, `FAINT`
   para ejes y divisorias): un gris plano no se parece a nada.

## Hallazgo lateral — el borrón que no tenía nada detrás

Auditando por qué «Tus sitios» no aparecía bloqueado en la galería salió un fallo del commit padre:
`lockedIntelligence` contaba **cualquier racimo**, pero `StatsPlacesCard` **solo lista los que
tienen nombre** y si no abre su estado vacío. O sea, un coche con racimos sin nombrar habría visto
un cristal sobre una card que, comprando Plus, se abre vacía — exactamente el *«un borrón sin nada
detrás es publicidad»* que el ticket padre existe para impedir. Ahora el flag aplica **el mismo
filtro que la card** (`!name.isNullOrBlank()`), con su test.

Y la silueta de lista **no la ejercitaba ninguna variante de galería** (con las sesiones por defecto
ningún racimo llega a tres): variante nueva «Free · tus sitios, bajo cristal», con el mismo racimo y
la misma zona que su gemela de Plus.

## Criterio de éxito

- Cada card bloqueada se parece a la suya y dice en una línea qué es.
- Ni una cifra bajo el cristal.
- Un racimo sin nombre no produce cristal.

## Verificación

| Qué | Resultado |
|---|---|
| `./gradlew testAll` | ✅ 3.454 tests, 0 fallos |
| Guards de Konsist `--rerun-tasks` | ✅ |
| `compileMockDebugKotlin` + `compileProdDebugKotlin` | ✅ |
| **En el Oppo** (mock `a18ec91b…`, sha verificado en device) | ✅ **visto**: ritmo y cuándo aparcas se leen como contenido tras cristal |
| «Tus sitios» bajo cristal y «Free · sin nada que bloquear» | ⏳ sin mirar en mano |

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `PapLockedCard` | reescrito (API: `description` + `preview`) |
| `StatsScreen` (5 call sites + `LockedCard`) | cambiado |
| `LockedIntelligence.places` | filtro alineado con la card |
| `StateGalleryScreen` | variante nueva para la silueta de lista |
| `PapShimmerBox` | sin tocar — sigue significando «esto está llegando», que es lo que esta card NO es |
