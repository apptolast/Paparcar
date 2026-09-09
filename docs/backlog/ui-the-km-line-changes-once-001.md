# UI-THE-KM-LINE-CHANGES-ONCE-001 · La línea de km cambiaba varias veces por cada cambio de filtro

**Estado:** ✅ Done — en master. Verificado: `:shared:testDebugUnitTest` ✅ con
`LocaleParityGuardrailTest` **forzado** (`--rerun-tasks`: cambiar solo XML lo deja UP-TO-DATE y no
llega a ejecutarse) · `prodRelease` instalado en el Oppo, sha256 `652e5009…fc4a90cc` device↔local.

## Problema

User en device, 09-09-2026, sobre `29aefb22` ya instalado:
*«lo único que molesta es el texto de km, que debería animarse con un solo cambio, no cambiar de
texto x veces cada vez que cambiamos de filtro»*.

Un toque en un chip de filtro produce **una sola** emisión de estado — comprobado: en la ficha,
`VehiclesIntent.SetHistoryFilter` resuelve el timeline de forma síncrona dentro de un `updateState`
(`VehiclesViewModel.kt:117-139`), y en el panel, `StatsIntent.SetFilter` hace lo mismo con
`withResolvedStats` (`StatsViewModel.kt:92-95`). O sea: el texto cambia **una vez**, y aun así se
ven varios.

## Causa 1 · la transición SOLAPA en vez de encadenar

El `Crossfade` que envolvía la frase. **Un crossfade mantiene los DOS hijos compuestos y los dibuja
SUPERPUESTOS** durante todo el fundido: no encadena, solapa. Dos frases alineadas a la derecha, de
longitudes distintas —y desde `UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001`, una de ellas de dos
líneas dentro de una caja que reserva altura, o sea a distinta altura que la otra— se solapan en un
borrón que se lee como el texto cambiando varias veces.

No es un problema de duración ni de curva: es que durante ~300ms hay dos frases legibles a la vez.
Bajar el tiempo lo haría más rápido, no más limpio.

## Causa 2 · el hueco del km lo decide un número EN MOVIMIENTO

Verificado en device con la causa 1 ya arreglada (build `fd72d442`), el user vuelve a señalarlo:
*«el valor cambia conforme sube el resultado de aparcamientos; el valor debería aparecer al momento,
no cambiar conforme sube la estadística»*.

La cuenta (`shownTotal`) tiene **ancho intrínseco** y el cualificador toma `weight(1f)`, o sea el
sobrante. Mientras el count-up sube de `5` a `20`, el ancho de la cuenta crece dígito a dígito, el
hueco del km se estrecha en cada frame y la frase se **re-parte** una y otra vez. El km no cambia de
valor: se re-maqueta al ritmo de la estadística, y eso se lee como el valor cambiando.

Esto ya se conocía — estaba escrito en `UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001` §Fuera de
alcance, donde se dejó fuera a propósito porque entonces el salto que molestaba era el de la
tarjeta. Ahora que la tarjeta no se mueve, el temblor de dentro es lo único que queda visible.

## Doctrina violada

*Un cambio se ve una vez.* Una transición existe para que el ojo siga UN valor de un estado al
siguiente; si durante la transición hay dos valores legibles, la transición está contando algo que
no pasó. El `Crossfade` es la herramienta correcta para dos superficies que se sustituyen (un
gráfico por otro, un estado vacío por una lista), y la equivocada para **texto**, donde solaparse no
es fundirse: es hacerse ilegible.

Y su gemela, que es la causa 2: *una animación no puede decidir la maquetación de lo que tiene al
lado.* Es la misma frase que ya sostiene la altura reservada del ticket anterior, aplicada al otro
eje. Si el ancho de un vecino sale de un valor en vuelo, todo lo que dependa de ese ancho se anima
sin haber pedido animarse.

## Medición en device — lo que decidió el diseño

Oppo CPH2371, 1080×2400 @480dpi (3px por dp), filtro «All», 20 parkings, locale EN:

| Nodo | bounds (px) | ancho |
|---|---|---|
| contenido de la tarjeta | 96→984 | **296dp** |
| `20 parkings` | 96→409 | **104,3dp** |
| hueco | 409→430 | 7dp (`TITLE_GAP_DP`) |
| caja del km | 430→984 | **184,7dp** |
| alto del km | 45 | 15dp → **una línea** |

De ahí salen los 9,5dp/carácter a 18sp bold del sustantivo. ⚠️ **En inglés hoy CABE**: el 2-líneas
no se reprodujo en este móvil. Los anchos de español están extrapolados de esa medida, no vistos:
«20 aparcamientos» ≈ 152dp deja 137dp, y «118,3 km en 18 de ellos» pide ≈ 145dp. Eso es lo que
parte, y es lo que la copy nueva arregla — pero conste que la parte española es cálculo, no captura.

## Diseño · causa 2a — la frase se acorta (decisión del user)

`118,3 km en 18 de ellos` → **`118,3 km (18 viajes)`**: 3 caracteres menos, ≈19dp, que en español
es justo la diferencia entre partir y no partir (126dp pedidos contra 137dp disponibles).

Y es mejor copy por una razón que no es el ancho: *«en 18 de ellos»* necesita el sustantivo del OTRO
extremo de la fila para entenderse, mientras que *«(18 viajes)»* se sostiene solo y nombra lo que
esos 18 son de verdad — los aparcamientos cuyo trayecto se midió. «Viaje/trip» ya es vocabulario de
la app (el viaje vivo), así que no inventa término.

⚠️ La key pasa de `<string>` a **`<plurals>`** en los 9 idiomas, porque «1 viajes» es un bug de copy
esperando: `%1$s (%2$d viaje)` / `(%2$d viajes)`, con las 4 categorías de PL
(`przejazd/przejazdy/przejazdów/przejazdu`) y las 3 de RO (`călătorie/călătorii/de călătorii`). Los
dos call sites pasan a `pluralStringResource(res, fromParkings, km, fromParkings)`.

⚠️ Sigue siendo una apuesta sobre el ancho: con 3 cifras y 4 dígitos de km
(`1.234,5 km (128 viajes)` ≈ 145dp contra 128dp) vuelve a partir. Lo único inmune sería bajar el km
a su propia línea; el user eligió esta vía sabiéndolo.

## Diseño · causa 2b — la columna del sujeto se mide por el valor FINAL

```kotlin
val subjectSlot = remember(total, noun, titleStyle, density) {
    with(density) { measurer.measure("$total $noun", titleStyle, maxLines = 1).size.width.toDp() }
}
Text(/* la cifra animada */, modifier = Modifier.width(subjectSlot)
    .wrapContentWidth(align = Alignment.Start, unbounded = true))
```

- ⛔ **NO por el número más ancho del vuelo.** Se implementó así primero y es peor de un modo
  silencioso: estrechar el periodo (20 → 3) pasa por números más anchos que donde acaba, así que
  reservar el máximo dejaría la frase partida PARA SIEMPRE — quieta, pero mal. Reservar el
  **objetivo** es lo que hace que el km quede maquetado como va a quedarse desde el primer frame.
- `unbounded = true` deja que un número momentáneamente más ancho se derrame sobre el hueco de 7dp
  en vez de empujar la frase. Nada se mueve porque nada de eso llega a la maquetación.
- El sustantivo ya lo fija el objetivo (decisión previa, para que no parpadee singular/plural), así
  que lo único que se mueve son los dígitos.

## Diseño · causa 1 — el relevo se encadena, no se solapa


**El invariante en UN sitio**: `ui/components/PapTextSwap.kt`, un `AnimatedContent` con las dos
mitades **encadenadas** en vez de simultáneas.

```kotlin
transitionSpec = {
    fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) togetherWith
        fadeOut(tween(FADE_OUT_MS)) using null
}
```

- El `delayMillis` de la entrada es exactamente la duración de la salida: la frase vieja está a
  alpha 0 antes de que la nueva empiece a aparecer. En cualquier instante hay **una** frase legible.
- `using null` quita el `SizeTransform` que `AnimatedContent` pone por defecto: la altura ya la
  posee la caja de `UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001` y una transición no debe animar un
  tamaño que decidió otro.
- 110ms fuera + 170ms dentro = 280ms, por debajo de los 300 del `Crossfade` anterior, y aun así se
  lee como un solo cambio porque no hay solape.
- ⛔ Los NÚMEROS no pasan por aquí: siguen subiendo con `rememberCountUp`. *Las palabras se
  sustituyen, los números trepan* — esto arregla la mitad de las palabras, no la de las cifras.

## Criterio de éxito

Tocar los chips de filtro en la ficha de vehículo y en el panel de Stats con un coche que tenga
distancia: la línea de km se sustituye **una vez** por toque —sin momento en el que se lean dos
frases encima una de otra— y una vez sustituida **no se mueve** mientras la cifra de aparcamientos
termina de subir.

## Consumidores auditados

`grep -rn "Crossfade(" presentation/ ui/` — tres usos, y la línea que los separa es **sobre qué**
funde: solapar dos superficies ES la disolución que se busca; solapar dos textos los hace ilegibles.

| Uso | Sobre qué | Veredicto |
|---|---|---|
| `HistoryWeeklyChart.kt:160` · línea de km | **texto** (frase de 1-2 líneas) | ❌ era este → `PapTextSwap` |
| `PapStatTile.kt:103` · valor de palabra del tile («Martes» → «Jueves») | **texto** | ❌ mismo defecto → `PapTextSwap` |
| `MapTypeToggle.kt:41` · el FAB cambia de glifo (mapa ↔ satélite) | superficie (un FAB entero) | ✅ correcto, se queda |

El de `PapStatTile` pasaba desapercibido porque dos palabras cortas y centradas se solapan en algo
que sigue pareciendo una palabra — pero es el mismo error, y el barrido existe para que no vuelva a
morder cuando esos valores sean más largos. Cerrar solo la vía por la que mordió no basta.

## Fuera de alcance (apuntado, no hecho)

`ActivityBarChart` (`HistoryWeeklyChart.kt:205-214`) tira TODAS las barras a cero y las hace crecer
800ms en cada cambio de dataset — cambiar de filtro, cambiar de coche o que aterrice una sesión
nueva — y además, al vivir su `remember` dentro de un `item(key = "chart")` de un `LazyColumn`,
repite la entrada cada vez que el item se descompone al scrollear y vuelve. Es exactamente lo que
la doctrina de `rememberCountUp` (`PapStatTile.kt:174`) prohíbe dos ficheros más allá: *«a counter
that runs every time something scrolls into view is decoration»*. Si algún día molesta, el arreglo
es que la entrada sea una ENTRADA (`rememberSaveable` para que sobreviva a la descomposición del
item, y sin keys de datos en el `LaunchedEffect`).
