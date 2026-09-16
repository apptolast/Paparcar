# FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001 · Italia, y el precio que cambia según quién sostiene la manguera

**Estado:** ✅ Done — en master · verificado en el Oppo (galería) y con `prodDebug` + GPS real en
Roma (emulador)

Cuarto país del orden medido de [`fuel-stations-list-001.md`](fuel-stations-list-001.md), tras
🇪🇸 España, 🇫🇷 [Francia](fuel-france-a-price-carries-its-own-hour-001.md) y
🇩🇪 [Alemania](fuel-germany-the-feed-answers-whole-001.md).

## Problema

En Italia la app enseña gasolineras de OSM sin un solo precio, y el país publica los suyos por ley.
Pero Italia trae la trampa que el barrido del 09-09 llamó *la peor de todo el lote*: **el precio
depende de si te lo sirves tú**. En una misma estación conviven `self` y `servito`, y la diferencia
medida llega a **0,35 €/L** — treinta y cinco céntimos, cuando lo que hizo rechazar a FuelWide como
fuente eran uno o dos. Enseñar el que no es sería el error más caro que puede cometer esta pantalla.

## Doctrina violada

Ninguna, todavía: Italia no está cableada. Lo que este ticket **no puede** violar:

- *«La fuente que contesta, contesta entera»* ([`FuelStationsSource`](../../shared/src/commonMain/kotlin/com/rndeveloper/paparcar/domain/fuel/FuelStationsSource.kt)) — un país, una fuente, sin cruzar catálogos por proximidad.
- *«Un precio con su hora»* ([`StationPrice`](../../shared/src/commonMain/kotlin/com/rndeveloper/paparcar/domain/fuel/StationPrice.kt)) — el KDoc de esa clase **ya reservó la caja para el `isSelf` italiano**. Aquí se ocupa.
- *Cada país nuevo es un adaptador con su propia MEDICIÓN*, nunca una analogía. Es la regla que
  nació del 404 autoinfligido y la que corrigió a Francia dos veces.

## Lo medido (15/16-09-2026) — y lo que refuta del plan escrito

`fuel-stations-list-001.md` daba por hecho para Italia: **«2 CSV, ninguna consulta: 7,5 MB
enteros»**, más «umbral de antigüedad» por la cola de trece meses. **Las dos cosas son falsas hoy**,
y no por un cambio del ministerio: por no haber mirado el otro canal del MISMO ministerio.

### 1 · El CSV oficial está a día y medio, y discrepa del 71,6 % de los precios vivos

| | Medido |
|---|---|
| `anagrafica_impianti_attivi.csv` | 3.584.918 B · 24.000 estaciones |
| `prezzo_alle_8.csv` | 3.949.351 B · 92.992 precios |
| Total por refresco | **7,5 MB** |
| `Accept-Encoding: gzip` | ⛔ **no comprime**: devuelve los mismos 3.584.918 B, `Content-Type: text/csv` |
| Licencia | IODL 2.0, MIMIT (permisiva, uso comercial OK — no es el caso de Portugal) |
| Frescura **declarada** | *«Quotidianamente vengono pubblicate in questa sezione le informazioni in vigore alle ore 8 del giorno precedente a quello di pubblicazione»* |

Esa última línea es la que lo mata. No es un retraso ocasional: **por construcción el CSV tiene
entre 24 y 48 horas**. Y se comprobó con la metodología exacta que descartó a FuelWide — las mismas
claves `estación+carburante+modo`, en el mismo instante, contra el feed vivo:

> **820 precios comunes en Roma · 587 discrepan = 71,6 % · mediana 0,02 €/L · máximo 0,17 €/L**

FuelWide se rechazó con **30 %** de discrepancia a uno o dos céntimos, escribiendo que *«el orden de
"la más barata" se puede invertir, y para una función cuya promesa es comparar precios eso no es un
detalle de frescura: es la función rota»*. El CSV oficial es **peor que el proxy que rechazamos**.
No se puede cablear el CSV y a la vez sostener aquella decisión.

### 2 · Hay consulta por radio, del mismo ministerio, y responde en 110 KB

`POST https://carburanti.mise.gov.it/ospzApi/search/zone` — el backend del propio buscador público
de Osservaprezzi (`carburanti.mise.gov.it/ospzSearch/`, el sitio oficial del MIMIT).

```
{"points":[{"lat":41.9028,"lng":12.4964}],"radius":5,"fuelType":"x-x","priceOrder":"asc"}
→ 229 estaciones · 109.564 B · 0,96 s
```

**Sesenta y ocho veces menos datos que el CSV, y contesta lo que le preguntas.**

Trampas medidas, todas:

- ⛔ **`points` lleva UN punto, no un polígono.** Con 4 o 5 vértices responde
  `{"success":false,"center":{"lat":41.890546,"lng":41.890546},"results":[]}` — un 200 con la lista
  vacía y un `center` que repite la latitud en las dos coordenadas. Se probó con cinco `fuelType`
  distintos antes de ver que el error estaba en los puntos.
- ⛔ **El `success` es la puerta, nunca el status.** Misma familia que el `ok:false` de Tankerkönig
  y que el 200 de un espejo Overpass con la base vacía. Y ojo a la asimetría: un área sin
  gasolineras responde `success:true, results:[]` — eso **sí** es una respuesta.
- ⛔ **`radius` va en KM y está topado en 10, en silencio.** Medido: 1→7 · 3→67 · 5→229 · 10→642 ·
  **25→642 · 50→642**, con la distancia máxima clavada en 9,99 km. Pedir 25 no da error: da 10 sin
  decirlo. (Alemania pide KM y topa en 25; Francia pide metros. Tres países, tres unidades/topes.)
- ⛔ **`fuelType` filtra QUÉ ESTACIONES vuelven, jamás qué precios.** Cada estación devuelve
  **todos** sus precios, en los dos modos, pidas lo que pidas. Medido en Roma 5 km:
  `1-x`→229 estaciones · `1-1`→192 · `1-0`→167 · `x-x`→229, y las tres devuelven las mismas 822
  filas de precio para las estaciones que comparten. Por tanto **se pregunta `x-x` una vez y la
  respuesta viene entera** — que es justo lo que exige la doctrina.
- El vocabulario sale de `GET /ospzApi/registry/fuels`: `"<fuelId>-<self>"`, con
  `1` Benzina · `2` Gasolio · `3` Metano · `4` GPL · `323` L-GNC · `324` GNL, y sufijo
  `1` self · `0` servito · `x` cualquiera.
- ⛔ **`address` es `null` SIEMPRE** (229/229). A cambio `name` y `brand` vienen al **100 %** — la
  situación inversa a Francia, que traía dirección y ningún nombre. Aquí **no hace falta cruzar con
  OSM**: la fila viste marca y nombre, y la dirección no es lo que se lee en una fila de 5 km.
- `distance` llega como **cadena**, en km.
- Sin clave, sin `User-Agent` que valga (`okhttp/4.12.0`, `Ktor client`, vacío y uno propio: los
  cuatro 200 — ⚠️ no es el 406 de Overpass), y **sin rate limit** en 10 peticiones seguidas (10/10
  en 200, ~1,1 s cada una). Cobertura nacional comprobada: Milán 134 · Nápoles 140 · Palermo 150 ·
  Bolzano 24 · Cagliari 62 · Basilicata rural 1 · mar Tirreno 0 · París 0.

### 2 bis · `fuelId` es un vocabulario ABIERTO, y el metano se vende al kilo

Censo sobre Roma, Milán, Nápoles y Palermo (1.544 estaciones): **30 `fuelId` distintos**, no los 6
que lista `registry/fuels`. Cada cadena registra su premium — `Blue Diesel` (20), `Supreme Diesel`
(327), `Hi-Q Diesel` (6), `Diesel Shell V Power` (9)… — y **`HVO` aparece bajo SIETE ids** (394,
404, 406, 424, 435, 448, 452). Por eso el mapeo va por número y sólo sobre las categorías del
ministerio: un premium es OTRO producto a OTRO precio (Blue Diesel mediana 2,378 contra Gasolio
2,259), y citarlo como diésel sería el error de este ticket con otro disfraz.

Y **el metano no se cita en absoluto**, aunque el feed lo trae: se vende al **kilo** (`Metano`
mediana 1,799) y caería en el mismo hueco `LPG_CNG` que el `GPL`, que va al litro (mediana 0,705).
Mismo hueco, 2,5× de diferencia. España y Francia ya citan sólo su GLP para ese tipo; aquí la razón
lleva número.

### 3 · La cola de trece meses era del CSV, no del país

El plan prometía «umbral de antigüedad» por el `dtComu` por precio. El feed vivo sella **una hora
por ESTACIÓN** (`insertDate`, ISO con offset), y su reparto es otro:

> Roma, 229 estaciones: **p10 5,7 h · p50 16,8 h · p90 85,1 h · máx 54,7 días** ·
> **66,8 % por debajo de 24 h · 0,4 % por encima de 7 días**

No hay cola de trece meses que recortar. Y el sello es de la ESTACIÓN, así que Italia estrena una
CUARTA frase de frescura: España sella la RESPUESTA, Francia sella cada PRECIO, Alemania no sella
nada y lo garantiza la ley, e Italia sella la ESTACIÓN.

⚠️ **Y ese sello es el del precio MÁS NUEVO de la estación** — contrastado contra
`registry/servicearea/{id}`, que sí fecha cada surtidor. Eso es exactamente lo que la doctrina de
Francia llama la mitad peligrosa («el más nuevo esconde justo el precio rancio que hay que
descartar»), así que hubo que acotar cuánto puede esconder:

> 45 estaciones muestreadas · **entre TODOS sus carburantes** la dispersión interna llega a
> **1.177 días** · **entre los tres productos que citamos**, 3 estaciones (7 %) pasan de 1 h y el
> **máximo es 24,0 h**; el 93 % no tiene ninguna.

La cola de años vive ENTERA en los premium que el punto 2 bis ya descarta. Sobre lo que se enseña,
el sello miente como mucho un día — más fino que el de España, que sella una respuesta entera. Con
eso medido, cada precio de una estación lleva su sello y la UI lo lee como edad, igual que Francia.

### 4 · Y el precio depende de quién sirve

- **130 de 229** estaciones de Roma publican los dos modos del mismo carburante.
- Diferencia `servito − self` en esos 300 pares: **mediana 0,00 · máximo 0,35 €/L**.
- La mediana cero es lo que hace peligrosa a la máxima: la mayoría de las veces da igual, así que un
  fallo aquí no se ve en la prueba de al lado de casa — se ve en la factura de quien paró donde no
  daba igual.

## Diseño

**`isSelf` entra en `StationPrice`, que es la caja que su propio KDoc reservó.** No un mapa paralelo
ni un campo de la estación: un precio italiano sin su modo no es un precio, igual que un precio
francés sin su hora no lo era.

```kotlin
data class StationPrice(
    val perUnit: Money,
    val reportedAtMs: Long? = null,
    /** null = la fuente no distingue el modo (España, Francia, Alemania, OSM). */
    val isSelf: Boolean? = null,
)
```

**Cuál de los dos precios va al mapa `prices: Map<FuelType, StationPrice>`: el `self`, y si no lo
hay, el `servito` MARCADO.** El mapa admite un precio por producto y esa restricción no se toca
(ampliarla a `Map<FuelType, List<StationPrice>>` obligaría a que cinco superficies decidieran cuál
enseñar, y cuatro de ellas no saben de Italia). Se elige `self` porque es el modo que el conductor
escoge por defecto y el barato; y cuando **solo** hay `servito`, el precio se enseña con su marca
encima, nunca disfrazado de self. Ocultarlo sería peor: son 99 estaciones de 229 en Roma.

Piezas:

| Pieza | Qué resuelve |
|---|---|
| `domain/fuel/StationPrice.isSelf` | El modo como parte de la identidad del precio |
| `data/fuel/ItalyStationParser` | JSON del zone search → `FuelStation`; puerta `success`; self gana a servito |
| `data/fuel/ItalyFuelStationsSource` | Una pregunta `x-x`, radio en km topado a 10, orden por distancia nuestro, `source = "it-osservaprezzi"` |
| `FuelStationsRegistry` + `DataModule` | Una entrada más en `national` — nada aguas arriba se entera |
| `RoomFuelStationsCache` + Room **v15** | El modo también se recuerda, o el disco degrada a self una fila que era servito |
| `FuelPriceSample` + `EvaluateDailyPriceMoveUseCase` | ⛔ Que un cambio de MODO no se notifique como una subida de 0,35 € |
| Fila, peek y cápsula del mapa | Decir «servito» donde se cita un precio que no es self |
| Licencias | Atribución MIMIT / Osservaprezzi, IODL 2.0 |

⛔ **El CSV no se cablea, y queda escrito por qué** — como FuelWide y como Portugal. Si el zone
search muriera, el relevo es el CSV **asumiendo sus 24-48 h**, y esa decisión (un precio de ayer vs
ninguno) es exactamente la que rompe la función, así que se toma en ese momento y no ahora.

## Criterio de éxito

- Test del parser con **fixture real** del zone search: `success:false` es fallo aunque el HTTP sea
  200 · `success:true, results:[]` es una respuesta válida · self gana a servito · servito solo se
  marca · los seis `fuelId` mapeados · `distance` como cadena · `address` nulo no rompe la fila.
- Test de que `radius` sale en KM y topado a 10 (el gemelo del de Alemania, que topa a 25).
- Test de que una estación con los dos modos enseña el **self** y otra con solo `servito` enseña el
  precio **marcado** — con los dos valores separados por los 0,35 € medidos.
- El modo sobrevive al viaje por Room (cacheada y releída, sigue diciendo servito).
- Strings ×9 · galería · Konsist · `./gradlew testAll` · `assembleMockDebug` + `prodDebug` · klib iOS.
- ✅ **Campo: `prodDebug` con GPS real en Roma, hecho en el emulador el 16-09** — ver abajo.

## ✅ Visto en device: `prodDebug` con GPS real en Roma (16-09-2026)

Emulador `emulator-5554`, APK `68f722ce…` (sha256 verificado en device), `adb emu geo fix` a Piazza
dei Cinquecento. **No se tocó ningún ajuste de los móviles**: el fix es del emulador.

⚠️ **El emulador venía de Berlín, del build de Alemania, y NO bastaba con mover el GPS**: la cámara
seguía al coche aparcado allí, así que el carril seguía preguntando por Berlín. Hizo falta el FAB
«Center on my location» para que la cámara —y con ella el carril, que sigue a la CÁMARA— se mudara.
📌 Un `geo fix` no es una verificación: hay que comprobar que la pantalla cambió de sitio.

Con la cámara en Roma, la cabecera dice **Fontana delle Naiadi** y el carril **229 FUEL STATIONS**
— el mismo número que había medido la sonda. Las filas, verbatim del device:

| | | |
|---|---|---|
| Api-Ip | 713 m | **2.129 EUR · Attended service** |
| VIALE CASTRO PRETORIO 55 | 893 m | 2.099 EUR |
| Api-Ip | 924 m | **2.139 EUR · Attended service** |
| UNION - BORGHESANO LUCCHESE | 932 m | 2.078 EUR |

Coinciden fila por fila con lo que imprimió la sonda. Las tres reglas se ven funcionando a la vez:
la marca sólo en las atendidas, la marca **ausente** en las self, y el nombre propio donde la marca
era `PompeBianche` (`VIALE CASTRO PRETORIO 55`, `UNION - …`) contra `Api-Ip` donde sí hay cadena.

También visto en el **Oppo** (mockDebug `aeea72de…`, hash verificado), variante de galería «Precio
SELF vs SERVITO (Italia)»: 2.084 sin marca contra 2.434 · Atendido, los 0,35 €/L medidos, uno
encima del otro.

### 🟢 Y algo que sólo se vio en la pantalla: tres UNION a 932 m exactos

No salió de ningún dato, salió de mirar la lista: **tres UNION distintas a 932 m exactos**, con tres
precios distintos. El feed las registra en el MISMO punto.

> Roma, 229 estaciones: **220 coordenadas distintas · 4 puntos compartidos · 13 estaciones (5,7 %)**

Los nombres lo zanjan: en `41.89470, 12.49348` conviven `UNION - VALCO S. PAOLO` y
`UNION - TRIONFALE` —extremos opuestos de Roma— y en otro punto `LUDOIL CIVITAVECCHIA` y
`MONTEPORZIOCATONE-STAROIL`, que están **a 60-80 km**. Son operadores registrando varias
instalaciones contra su domicilio social. ⚠️ **No es universal**: Francia devuelve 37 estaciones en
37 coordenadas distintas.

**Se tira el grupo ENTERO, no todas menos una.** Como mucho una está ahí y el feed no dice cuál, así
que quedarse con una es una moneda al aire que se lee como una fila normal y miente 3 de cada 4
veces. Tirar el grupo cuesta una gasolinera real por punto y no deja ninguna fila mintiendo sobre
dónde está — la misma asimetría que la app ya elige cuando prefiere perder un aparcamiento a plantar
uno fantasma.

⛔ **Igualdad EXACTA, sin clustering por distancia.** Lo demostrable es que el feed escribió el mismo
número dos veces; cualquier cosa más difusa es adivinar, y medido los casi-empates son genuinamente
ambiguos: `TOTALERG` y `totalerg rampa di uscita` están a 5,97 m y las dos son reales, mientras que
cinco nombres napolitanos repartidos en 18 m claramente no. Los dos blobs de 1,11 m de Roma son
**dos grupos exactos distintos**, así que caen igual sin necesidad de nada de eso.

📌 Este bug no lo encontró ningún test ni ninguna sonda: lo encontró mirar la pantalla.

## Sonda de viabilidad, 13 puntos de Italia (16-09-2026)

⚠️ **No es un field-test.** Es el adaptador REAL (`ItalyFuelStationsSource` + `ItalyStationParser`,
no un curl paralelo) contra el endpoint vivo, con el radio de 5 km que pide la app.

**Cobertura: 958 gasolineras, 950 con precio de gasolina — el 99,2 %.**

| Sitio | n | con precio | self % | edad p50 |
|---|---|---|---|---|
| Roma centro | 229 | 229 | 83 % | 18,0 h |
| Milano Duomo | 134 | 132 | 96 % | 18,0 h |
| Napoli centro | 140 | 139 | 78 % | 19,1 h |
| Torino centro | 115 | 114 | 93 % | 17,9 h |
| Palermo centro | 150 | 149 | 81 % | 18,3 h |
| Firenze A1 | 51 | 49 | 100 % | 17,7 h |
| Bolzano | 24 | 24 | 100 % | 17,9 h |
| Venezia centro storico | 5 | 5 | 100 % | 17,5 h |
| Aosta | 26 | 25 | 100 % | 18,0 h |
| Cagliari | 62 | 62 | 100 % | 18,4 h |
| Matera | 18 | 18 | 100 % | 17,7 h |
| Molise rural | 2 | 2 | 100 % | 18,8 h |
| Lampedusa | 2 | 2 | 100 % | 42,0 h |

Lo que confirma:

- **El país entero contesta**, islas y valles alpinos incluidos, y hasta Lampedusa y el Molise
  rural devuelven forecourts con precio. Venezia da 5, que es lo correcto para un centro histórico
  sin coches.
- **Ni Lugano (CH) ni Niza (FR) son reclamadas** — `claims` dice que no y caen al suelo de OSM,
  que es lo que debe pasar.
- **Estable y rápida**: la misma pregunta tres veces da 229 gasolineras y la misma más barata, en
  844 / 807 / 801 ms.
- **La marca de «servito» hace trabajo real y está donde hay gente**: 16-21 % de las estaciones de
  Roma, Nápoles y Palermo, contra 3 % en Milán. La brecha visible dentro de una misma lista llega a
  **0,51 €/L** en Roma y Nápoles — por encima de los 0,35 que motivaron el ticket.
- **El tope de 10 km nunca muerde**: la app pide siempre `DEFAULT_RADIUS_METERS` (5 km). El
  `isTruncated` queda como guard dormido, no como comportamiento.

### 🔴 Lo que encontró la sonda, y NO es de Italia

**Las más baratas son las más rancias**, sistemáticamente: en Roma las 10 más baratas promedian
175 h contra 28 h del resto (6,3×); en Nápoles 166 vs 47; en Palermo 171 vs 34; en Turín 115 vs 23.
Sólo Milán no tiene sesgo. Una estación que deja de reportar congela su último precio, y como los
precios suben, el precio congelado parece el chollo del barrio.

Eso no rompe la fila de Home —que ordena por DISTANCIA y escribe la edad al lado de cada precio—
pero sí la alerta Plus «hay una más barata cerca», que sí ordena por precio. Ejecutado el evaluador
REAL sobre los datos reales, sin estación seguida: en Roma dispara **«STAR OIL, 1,669 € — ahorras
0,455 €/L a 1,9 km»**, o sea 18,20 € por depósito, sobre un precio de **6,6 días**.

⛔ **Es un agujero preexistente de `EvaluateCheaperStationNearbyUseCase`, no del adaptador
italiano** — su guard de frescura compara contra la estación seguida y **devuelve `true` para todo
cuando no hay ninguna**, que es el estado inicial de cualquier usuario. Francia está igual de
expuesta (cola medida de trece meses); España y Alemania son inmunes por construcción.

→ Abierto aparte en
[`fuel-a-cheap-price-with-no-floor-is-the-stalest-one-001.md`](fuel-a-cheap-price-with-no-floor-is-the-stalest-one-001.md).
**Recomendación: no mergear Italia sin cerrar ese**, o el primer usuario Plus italiano recibe una
promesa de 18 € sobre un precio de la semana pasada — exactamente el fallo que esta familia de
tickets existe para evitar.

## Verificado (16-09-2026)

- **`./gradlew testAll` → 3.184 tests / 0 fallos**, y repetido con `--rerun-tasks` (los guards de
  Konsist dan UP-TO-DATE si sólo cambian imports).
- **19 tests nuevos** del adaptador italiano, con fixture de cuatro estaciones REALES de Roma —
  entre ellas la del máximo medido, 2,084 self contra 2,434 servito.
- **Falsado uno a uno, no sólo verde**:
  - preferir servito en vez de self → cae `should_quoteTheSelfPrice…` ✅
  - dejar entrar `Blue Diesel` (fuelId 20) como diésel → cae `should_quoteOnlyTheBaseProducts…` ✅
  - citar `Metano` como GLP → cae `should_notQuoteMethaneAsLpg…` ✅ *(a la segunda: ver arriba)*
  - borrar `home_fuel_attended_price` de `values-pl` → cae `LocaleParityGuardrailTest` ✅
- **Las colisiones, falsadas por los DOS lados**: quedarse con una tumba el test del grupo;
  tirarlo todo tumba otros cinco, incluido el testigo de que una estación con punto propio sobrevive.
- **Las dos columnas de Room tienen testigo**: `AppDatabaseV1BaselineTest` (que camina v1→v15)
  siembra una fila `isSelf = false` y la relee, y `FuelWatchStoreTest` hace lo propio con el
  historial. Sembradas ATENDIDAS a propósito: un `false` que sobrevive por defecto no prueba nada.
- `assembleMockDebug`, `prodDebug` y `:shared:compileKotlinIosSimulatorArm64` compilan.
- 2 keys nuevas × 9 locales, paridad verde.

## Lo que encontró el barrido, y que no estaba en el plan

### ⛔⛔ La vigilancia de precios habría gritado un cambio de MODO como una subida de 0,35 €

`EvaluateDailyPriceMoveUseCase` compara la lectura de hoy contra la de ayer y avisa a partir de
**3 céntimos**. Una estación italiana que deja de cotizar `self` sigue cotizando `servito`: el
precio guardado salta hasta 0,35 € sin que nada haya subido, y el aviso que sale es el **más
ruidoso que la función puede emitir**, un orden de magnitud por encima de su propio umbral.

El propio evaluador ya tenía escrito el razonamiento para el caso hermano —*«las monedas se
comparan, nunca se convierten: una estación que cambió de moneda no ha cambiado de precio»*— así que
el arreglo es la misma frase una unidad más abajo, en la misma puerta. Pero exige que el modo VIAJE
con la muestra: la fila vieja es el único sitio donde el modo viejo todavía existe. De ahí
`FuelPriceSample.isSelf`, `fuel_price_history.isSelf` y la segunda mitad de `MIGRATION_14_15`.

### ⛔ Un orden de declaración que funcionaba por suerte (lo cazó una falsación, no la suite)

La primera versión de `readPrices` recorría los PRODUCTOS escribiendo en el mapa. Con dos productos
apuntando al mismo `FuelType`, el **último** declarado pisaba al primero, en silencio. Pasaba los 17
tests, y al intentar falsificar «el metano no se cita» metiéndolo en el enum **el test siguió en
verde** — porque `GPL` estaba declarado después y ganaba por orden alfabético.

O sea: la protección existía, pero por accidente. Reescrito para recorrer el FUEL y quedarse con el
primer producto que conteste, que es exactamente lo que ya hacían Francia y Alemania con su
`preference`. Con eso, la falsación sí tumba el test.
**📌 La lección: una falsación que sigue en verde no siempre absuelve al test — a veces acusa al
código de estar bien por casualidad.**

## Consumidores auditados

`grep` de todo lo que construye o lee un `StationPrice`:

| Consumidor | Veredicto |
|---|---|
| `MineturStationParser` · `FranceStationParser` · `GermanyStationParser` | **Exentos y correctos**: no distinguen modo, así que `isSelf` queda null — que es la respuesta, no un hueco |
| `RoomFuelStationsCache` | **Cerrado**: escribe y lee el modo; Room v15 |
| `FuelPriceSample` → `RoomFuelWatchRepository` → `EvaluateDailyPriceMoveUseCase` | **Cerrado**: ver arriba. Era el único consumidor que podía MENTIRLE al usuario |
| `HomeFuelStationRows` (fila) · `FuelStationPeek` (peek) · `FuelStationMarker` (cápsula) | **Cerrados**: los tres sitios que citan un precio lo marcan; la cápsula lo mete en su línea de cualificación, que ya existía para eso, y por tanto en su `contentId` |
| `EvaluateCheaperStationNearbyUseCase` · `NearbyOrdering` | **Cubiertos sin tocar**: ordenan y comparan precios DENTRO de una misma respuesta, donde todos los surtidores han contestado hoy y cada cifra es la mejor que esa estación ofrece. La comparación entre estaciones sigue siendo la que el conductor haría |
| `ResolveFuelPriceUseCase` | **Exento**: resuelve el precio con el que se factura un viaje (media nacional / coche), otro escalón de la escalera; no toca surtidores |
| `FakeFuelStationsSource` (mock) | **Exento**: el grafo mock no sale a la red y su forma es española; `isSelf` null es lo correcto |
| `StateGalleryScreen` | **Cerrado**: variante nueva «Precio SELF vs SERVITO (Italia)» con los 0,35 € medidos |
