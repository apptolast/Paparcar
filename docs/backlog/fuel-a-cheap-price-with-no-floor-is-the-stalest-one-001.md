# FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001 · La alerta recomienda el precio más viejo del barrio

**Estado:** ✅ Done — en master · **descubierto evaluando Italia**
([`fuel-italy-a-price-depends-on-who-pumps-it-001.md`](fuel-italy-a-price-depends-on-who-pumps-it-001.md)),
pero **NO es un bug italiano**: vive en `EvaluateCheaperStationNearbyUseCase` desde
[`fuel-a-price-remembered-is-a-price-that-can-move-001.md`](fuel-a-price-remembered-is-a-price-that-can-move-001.md).

## Problema

La alerta Plus «hay una más barata cerca» **recomienda sistemáticamente el precio más rancio del
barrio**, y lo hace precisamente en el caso por defecto: el usuario que todavía no sigue ninguna
gasolinera.

Ejecutado el 16-09-2026 con el evaluador REAL y datos REALES del feed italiano, sin seguidas:

| Ciudad | Lo que diría la notificación | Edad del precio |
|---|---|---|
| **Roma** | «STAR OIL, 1,669 € — ahorras **0,455 €/L** a 1,9 km» (≈ **18,20 €** por depósito de 40 l) | **158 h · 6,6 días** |
| **Napoli** | «EOIL, 2,029 € — ahorras 0,117 €/L a 1,7 km» | **199 h · 8,3 días** |
| Torino | «Q8, 1,999 € — ahorras 0,130 €/L» | 39 h ✅ |
| Palermo | «GAN, 2,069 € — ahorras 0,070 €/L» | 42 h ✅ |
| Milano | «Q8, 2,099 € — ahorras 0,040 €/L» | 18 h ✅ |

El conductor conduce 1,9 km y paga el precio de hoy, que no es el que le prometimos.

## Doctrina violada

**Es la trampa que descartó a FuelWide, entrando por la puerta de casa.**
`fuel-stations-list-001.md` rechazó aquel proxy porque iba doce horas por detrás y *«el orden de "la
más barata" se puede invertir, y para una función cuya promesa es comparar precios eso no es un
detalle de frescura: es la función rota»*. Aquí el feed es impecable y el orden se invierte igual,
porque **una estación que deja de reportar congela su último precio** y, como los precios suben, el
precio congelado parece el chollo del barrio.

El propio guard lo tiene escrito y no lo cumple:

```kotlin
// ⛔ A candidate quoted from an older reading than the reference is not proof of
// anything: half the "savings" in a stale feed are just two different days compared.
.filter { it.isAtLeastAsFreshAs(reference) }
```

```kotlin
fun isAtLeastAsFreshAs(other: PricedStation?): Boolean {
    val mine = reportedAtMs ?: return true
    val theirs = other?.reportedAtMs ?: return true   // ⛔ AQUÍ
    return mine >= theirs
}
```

Sin estación seguida **`reference` es null**, así que el filtro devuelve `true` para todo y el
evaluador se queda con el mínimo absoluto. La rama `AREA_MEDIAN` —que es la de cualquiera que no
haya puesto una estrella todavía— **no tiene ninguna puerta de frescura**. El guard existe,
está razonado, y es inalcanzable justo donde más falta hace: mismo patrón que
`GEO-CACHE-ANSWERS-NEARBY-001`, que tampoco se ejecutaba jamás.

`RunFuelPriceWatchUseCase` **no exige ninguna seguida** para correr (sólo Plus + interruptor + fix),
así que la rama sin referencia no es un caso raro: es el estado inicial de todo el mundo.

## Señales / datos disponibles

`StationPrice.reportedAtMs` ya viaja con cada precio. No hace falta nada nuevo.

Medido el 16-09 sobre 5 ciudades italianas, radio 5 km — **las baratas son las viejas**:

| Ciudad | Edad media, 10 más baratas | Edad media, el resto | Sesgo |
|---|---|---|---|
| Roma | **175 h** | 28 h | 6,3× |
| Napoli | **166 h** | 47 h | 3,5× |
| Palermo | **171 h** | 34 h | 5,0× |
| Torino | **115 h** | 23 h | 5,0× |
| Milano | 21 h | 22 h | 1,0× (sin sesgo) |

La mediana de edad es **18 h** en las cinco. Las colas son de días o semanas: la más barata de
Palermo llevaba **1.194 h (50 días)** congelada y la de Nápoles **1.146 h (48 días)**.

⚠️ **Italia lo hace evidente, pero no es el único expuesto.** Francia sella cada precio y su cola
medida llega a **trece meses** — misma forma, mismo agujero. España sella la respuesta entera con
una hora reciente, y Alemania no sella nada (`null`), así que los dos son inmunes por construcción.

## Diseño

**Un suelo absoluto de frescura, en la misma puerta donde ya vive el comparativo.** No un segundo
guard apilado: `isAtLeastAsFreshAs` contesta «¿es al menos tan nueva como la referencia?» y le falta
la mitad «¿y es lo bastante nueva como para nombrarla?».

- El suelo sólo aplica **si hay sello**. `reportedAtMs == null` sigue sin descalificar — medio mundo
  no publica hora y Alemania garantiza la frescura por ley. Esa línea no se toca.
- **48 h** como candidato de umbral, elegido sobre lo medido y no redondo: la mediana real es 18 h,
  así que 48 h es ~2,7× la mediana y deja pasar el ciclo normal de un fin de semana. Con él:
  sobreviven 198/229 filas en Roma y 106/139 en Nápoles, y la recomendación de Roma pasa de
  1,669 € (158 h) a **1,709 € (5 h)** — sigue siendo un ahorro de 0,415 €/L, y además es verdad.
- ⛔ **El suelo es de la ALERTA, no de la lista.** La fila de Home se ordena por DISTANCIA y escribe
  la edad al lado de cada precio, así que ahí un precio viejo se ve y no engaña a nadie. Ocultarlo
  de la lista sería vaciar el mapa para arreglar una notificación.

## Criterio de éxito

- Test con el caso de Roma medido: sin seguidas y con una fila de 158 h como mínimo absoluto, la
  alerta nombra la de 5 h, no la rancia.
- Test de que un candidato **sin sello** sigue pudiendo ganar (Alemania no debe quedarse muda).
- Test de que con una seguida el comportamiento actual no cambia.
- Falsar: quitar el suelo y ver caer el primero.

## Hecho (16-09-2026)

`isFreshEnoughToName(nowMs, maxAgeMs)` junto a `isAtLeastAsFreshAs`, en la MISMA puerta. Dos gates
porque son dos preguntas: *«¿es más nueva que aquello con lo que la comparo?»* y *«¿es lo bastante
nueva como para mandar a alguien allí?»*.

- **`nowMs` es un parámetro OBLIGATORIO, sin default.** El veredicto sigue siendo puro y síncrono,
  así que lo único que no puede hacer es mirar un reloj; y un `0L` por defecto habría envejecido
  todos los precios cincuenta y seis años, apagando la alerta en todas partes en silencio. Lo pasa
  `RunFuelPriceWatchUseCase` con el `clock()` que ya comparte toda la ejecución.
- **Sin sello sigue sin descalificar.** Alemania no publica ninguna hora y la garantiza por ley;
  leer `null` como «antiquísimo» habría dejado muda a un país entero para arreglar otro.
- **El suelo mide al CANDIDATO, nunca a la referencia**, y la asimetría es el diseño: una candidata
  rancia hace que la app diga «conduce 1,9 km y paga 1,669 €», que es falso al llegar; una
  referencia rancia sólo debilita el «más barata que la tuya» mientras la cifra sobre la que se
  actúa sigue siendo cierta. Una manda a alguien a un número equivocado, la otra no.
- ⚠️ **La mediana del área se sigue calculando con TODAS las filas**, rancias incluidas, y es
  deliberado: las viejas tiran la mediana hacia abajo, o sea que el ahorro sale SUBESTIMADO y la
  alerta dispara menos. Filtrarlas subiría la mediana y haría saltar más avisos, que es la
  dirección insegura.

**Verificado:** suite **3.182 / 0**, mock + prod + klib iOS compilan. **5 tests nuevos**, y falsados:
quitando el `.filter` caen **tres** a la vez (el caso de Roma, el borde y el «callar»).

📌 **Dos de mis propios tests fallaron primero por el MONTAJE, no por el código**: con sólo tres
estaciones la mediana del área *era* el propio candidato, así que el ahorro salía cero y la alerta
callaba por la razón equivocada. Montados con cinco filas, la mediana cae donde la midió Roma
(2,124 €) y el test dice lo que cree decir. Un test que pasa a null puede estar pasando por una
puerta que no es la que se quería probar.

## Consumidores auditados

| Consumidor | Veredicto |
|---|---|
| `EvaluateCheaperStationNearbyUseCase` | **Cerrado** — el único sitio que ordena por PRECIO entre estaciones |
| `RunFuelPriceWatchUseCase` | **Cerrado** — único llamante; pasa su propio reloj |
| `EvaluateDailyPriceMoveUseCase` | **Exento**: ya tiene su propia puerta de sellos (exige que las dos lecturas tengan sello y que difieran), y compara una estación contra SÍ MISMA, no contra el barrio |
| `NearbyOrdering` · `HomeFuelStationRows` · `FuelStationPeek` | **Exentos**: ordenan por DISTANCIA y escriben la edad al lado de cada precio. Un precio viejo ahí se ve, y ocultarlo vaciaría el mapa para arreglar una notificación |
