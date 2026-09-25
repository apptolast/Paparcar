# UI-A-PILL-PAIR-SPLITS-THE-BUDGET-IN-TWO-001 · dos cápsulas en una fila cobran cada una la MITAD del presupuesto, y nadie se lo cobraba

**Estado:** ✅ Done · mergeado a master por squash

## Problema

Captura del user (25-09, ES, card del coche en el sheet de Home): la fila de arranque en frío
—«¿Dónde está tu coche?»— pinta sus dos botones **lado a lado**, y los dos salen cortados:

```
[ Voy conducie… ]  [ Marcar mi apa… ]
```

Ninguno de los dos se puede leer. Y el corte no es un caso raro del español: de los 9 locales,
`home_det_awaiting_cta_primary` mide **15–25 caracteres** (EN «Mark my parking» 15, FR «Marquer mon
stationnement» 25) y ninguno cabe en media fila. **La maqueta está rota en los 9 idiomas**, incluida
la base.

## Doctrina violada

[UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] — «la colocación la decide el LABEL, no un booleano
que alguien tenga que acordarse de pasar». Ese ticket escribió el predicado `ctaFitsInline(label)` y
lo aplicó al hueco *trailing* de la fila… y dejó fuera la única maqueta donde un botón NO compite
contra texto, sino **contra otro botón**.

Peor: el sistema ya sabía la respuesta y la estaba ignorando. `CtaPlacementGuardrailTest` declara
`home_det_awaiting_cta_primary` como `STACKED` —*«una frase: baja a ancho completo, en los 9
locales»*— y la rama de dos CTAs de `HomeDetectionSurface` ni mira `stacksPrimary`: mete esa misma
frase en una `Row` a `weight(1f)`. **Un label declarado frase se pintaba a media anchura.**

## Señales / datos disponibles

Medido en el fichero de recursos, los tres pares que usan esa rama (long. máx. de los dos labels):

| par | EN | ES | IT | PT | FR | DE | NL | PL | RO |
|---|---|---|---|---|---|---|---|---|---|
| `awaiting` primary+secondary | 15 | 22 | 19 | 23 | 25 | 19 | 22 | 18 | 19 |
| `nudge` cta+dismiss | 15 | 22 | 23 | 27 | 25 | 19 | 27 | 22 | 23 |
| `ask` yes+no | 13 | 16 | 20 | 16 | 18 | **11** | 14 | 18 | 13 |

Calibración del presupuesto, con el dato de la captura como testigo:
- fila útil ≈ 300 dp − 8 dp de hueco ⇒ **~146 dp por cápsula**; menos los 32 dp de padding de
  `PapCtaPill` ⇒ **~114 dp de texto**.
- el rol `cta` es 15 sp SemiBold (+0,1 de tracking) ⇒ ~8,4 dp por carácter ⇒ **~13 caracteres**.
- la captura lo confirma: «Voy conduciendo» (15) se pintó «Voy conducie…», o sea 12 glifos + puntos
  suspensivos ≈ 13 anchos de carácter. El cálculo y el device dicen lo mismo.
- y la variante **empotrada** (dentro de la card del coche, que es la de la captura) tiene menos
  sitio que la suelta, porque paga además el padding de su huésped ⇒ el presupuesto honesto es
  **12**, no 13.

## Diseño

Un predicado hermano en `ui/components/CtaPlacement.kt`, al lado del que ya existe:

```kotlin
fun ctaPairFitsSideBySide(primary: String, secondary: String): Boolean
const val CTA_SIDE_BY_SIDE_MAX_CHARS = 12
```

⛔ **No se reutiliza `ctaFitsInline`**, y no por gusto: ese predicado gobierna el hueco *trailing*,
donde el vecino es copy acotado que puede envolver a dos líneas — la misma distinción que
`HomeParkingRow` ya dejó escrita al negarse a pasar por él. Son dos preguntas distintas sobre dos
anchuras distintas; que los números salgan parecidos no las fusiona.

`HomeDetectionSurface` pasa a preguntarlo en la rama de dos CTAs: si caben, la `Row` de siempre; si
no, una `Column` con las dos cápsulas a ancho completo, **primaria arriba**. Ningún call site decide
nada: los tres pares heredan la respuesta del label, que es justo lo que
[UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] pedía.

## Criterio de éxito

- ✅ `CtaPlacementGuardrailTest` crece con los **tres pares × 9 locales** y **está falsificado**:
  subiendo `CTA_SIDE_BY_SIDE_MAX_CHARS` a 40 —o sea, la maqueta de antes, donde todo cabía al
  lado— el test se pone rojo. Restaurado a 12, verde.
- ✅ `./gradlew testAll` verde; `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` verdes.
- ✅ Sin estado ni pantalla nuevos ⇒ nada que añadir al Dev Catalog: la galería ya tiene
  `AwaitingFirstPark`, `PendingAsk` y la ventana de pregunta, y ahora las enseña apiladas.
- ✅ iOS entra solo: todo el cambio es `commonMain`.
- ⏳ **En device**: la card de arranque en frío tiene que enseñar «Marcar mi aparcamiento» y «Voy
  conduciendo» **enteros**. Es la captura que pide [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## Consumidores auditados

`grep -rl PapCtaPill shared app` → 4 ficheros:

| sitio | veredicto |
|---|---|
| `CtaPlacement.kt` | es el invariante — aquí vive |
| `PapCtaPill.kt` | la cápsula: `maxLines = 1` + `Ellipsis`, no decide colocación — **exento** |
| `HomeParkingRow.kt` | una sola cápsula, ya apilada a ancho completo siempre — **cerrado** |
| `HomeDetectionSurface.kt` | la rama de dos CTAs, la única maqueta de PAR de toda la app — **es el bug**; sirve a los 3 pares (`awaiting`, `nudge`, `ask`) y se arreglan los tres a la vez |

No hay ninguna otra `Row` con dos `PapCtaPill`. Los botones grandes (`PapPrimaryButton`) no entran:
no comparten fila.
