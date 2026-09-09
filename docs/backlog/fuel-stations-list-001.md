# FUEL-STATIONS-LIST-001 · Dónde echo gasolina, y a cuánto

**Estado:** ✅ Done — en master · verificado en el Pixel 8 Pro (mockDebug, hash comprobado)

⏳ Lo único que no se ha visto: la sección con datos **reales del Ministerio** en device, que necesita
`prodDebug` y un fix GPS español. La capa de datos sí está probada contra la respuesta real (fixture
del feed + `MockEngine` con la petición incluida).

Primera mitad de la **F5b de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)); la capa del
mapa y el peek van después. Continúa [`fuel-price-resolver-001.md`](fuel-price-resolver-001.md),
que ya dejó el precio resuelto y el hueco `stationPrice` esperando.

## Problema

La app sabe lo que gastas pero no dónde repostar. Y el escalón 1 del precio —el surtidor real—
sigue sin nadie que lo produzca.

## Lo que se midió antes de elegir fuente (08-09-2026)

⛔ **El 404 del Ministerio era un error nuestro de UNA LETRA**: la ruta es `PreciosCarburantes`
(plural), no `PrecioCarburantes`. La API nunca se retiró — el archivo web no tiene ni una captura
de la ruta en singular, y la plural responde 200 desde 2022. Se llegó a proponer buscar
alternativas de pago por un 404 autoinfligido.

⛔ **FuelWide se descartó como fuente primaria por MEDICIÓN, no por prejuicio.** Es un proxy sobre
estos mismos feeds oficiales y su comodidad es real (acepta `?near=lat,lon&radius=`, devuelve
marca y horario, y los precios ya como números). Pero comparando **las mismas 153 gasolineras por
su IDEESS oficial**, el **30 % tenía un precio distinto** del que daba el Ministerio en ese
instante: su `reportedAt` iba **doce horas por detrás**. Las diferencias son de uno o dos
céntimos — exactamente la magnitud que el usuario intenta detectar, así que **el orden de "la más
barata" se puede invertir**. Para una función cuya promesa es comparar precios, eso no es un
detalle de frescura: es la función rota.
Queda como **relevo documentado**, no como primario. Y de paso nos ahorra su problema legal:
FuelWide sirve Portugal declarando uso comercial libre, cuando la DGEG lo prohíbe expresamente —
nadie puede conceder derechos que no tiene.

⛔ **Portugal queda FUERA a propósito**, no por olvido: la DGEG prohíbe el uso comercial de su feed
y publicamos en portugués. Escrito en el código para que nadie lo "arregle" dentro de seis meses.

Otras fuentes descartadas con su motivo: **OpenStreetMap** tiene 567.615 gasolineras en el mundo y
**cero precios** (la etiqueta de precio de diésel tiene 0 usos globales) → sirve de suelo, no de
precio. **Google Places** trae `fuelOptions` en Nearby Search, pero sus términos **prohíben
cachear el precio** (cada vista sería una llamada facturada, SKU Enterprise). **TomTom** y **HERE**
no publican precios ni tienen free tier. **fuel-prices.eu** no da precio por estación en su plan
gratis, y **GlobalPetrolPrices** es CC BY-**NC** — uso comercial prohibido.

## Alcance de ESTA fase (para que no se lea de más)

| | Gasolineras | Precio |
|---|---|---|
| **España** | Ministerio, una llamada, objeto entero | Real por surtidor, con su hora |
| **Resto del mundo** | OpenStreetMap | Ninguno por estación; solo la media del país |

**Francia e Italia NO entran aquí y no están decididas**: sus feeds oficiales existen, pero no se han
medido, y prometerlos por analogía es justo el error que costó el 404. Cada país nuevo es un
adaptador con su propia medición. Del francés se sabe ya que **no trae nombre de estación**, que es
el único caso donde habrá que cruzar dos fuentes.

FuelWide **no se cablea**: queda escrito como relevo por si el Ministerio muere. Un secundario activo
obligaría a decidir cuándo se prefiere un precio de doce horas a ninguno, y esa decisión es la que
rompe la función — un precio viejo no se distingue de uno bueno mirando la pantalla.

## Países con feed oficial — barrido medido (09-09-2026)

Todo lo de esta tabla se probó con `curl` contra el endpoint real, igual que España. Lo que no se
pudo probar está abajo, separado, y **no cuenta**.

| País | Fuente oficial | Clave | Consulta | Estaciones | Marca |
|---|---|---|---|---|---|
| 🇪🇸 España | Ministerio (`PreciosCarburantes`) | no | por provincia | ~11.000 | sí |
| 🇫🇷 Francia | `data.economie.gouv.fr` (flux instantané v2) | no | **por radio** | 9.805 | **no** |
| 🇮🇹 Italia | MIMIT (2 CSV) | no | **ninguna: 7,5 MB enteros** | 23.984 | sí |
| 🇩🇪 Alemania | MTS-K vía Tankerkönig | sí (gratis) | por radio | — | sí |
| 🇦🇹 Austria | E-Control | no | por radio/región, **con tope** | — | sí |
| 🇲🇽 México | CRE (2 XML) | no | ninguna: 0,8 MB | — | sí |
| 🇬🇧 Reino Unido | Fuel Finder (CMA/DBT) | **sí, OAuth2** | paginada | — | sí |
| 🇦🇺 Australia | FuelWatch (WA) · CKAN (QLD) | según estado | RSS / dataset | — | sí |

**🇫🇷 Francia es la mejor del lote, mejor que España**, y merece ir la primera: acepta radio nativo
(`distance(geom, geom'POINT(lon lat)', 5km)` → 30 estaciones a 5 km de Toulouse), da los precios ya
como números y **sella cada precio con su propia hora** (`gazole_maj`), no una sola para toda la
respuesta como España. Tres trampas medidas: **no trae nombre ni marca** (solo dirección y ciudad —
es el único país que obligará a cruzar con OSM); los campos `latitude`/`longitude` vienen **escalados
×100.000** como cadena (`'4818300'` = 48,183) y hay que usar `geom`, que sí es decimal; y un
carburante puede estar **en ruptura** (`sp95_prix: null` + `sp95_rupture_type: "definitive"`), que no
es lo mismo que no venderlo.

**🇮🇹 Italia trae la peor trampa de todo el barrido, y es peor que la que descartó a FuelWide.**
El precio depende de si te lo sirves tú: **33.483 de 59.308 pares estación+carburante tienen dos
precios, `self` y `servito`**, y la diferencia medida es de **0,36 €/L**. Treinta y seis céntimos,
cuando lo que me hizo rechazar FuelWide eran uno o dos. Enseñar el que no es sería el error más caro
que puede cometer esta pantalla, así que `isSelf` no es un campo opcional: es parte de la identidad
del precio. Además: el CSV va separado por **`|`**, la primera línea es `Estrazione del …` **antes**
de la cabecera real, los decimales son **punto** (al revés que España), y **cada precio arrastra su
propia antigüedad** — medido: 29,8 % de hoy, 69 % de 2-7 días, y una cola de 0,1 % de más de un año
(el más viejo, de 2013). Se puede enseñar honestamente porque cada fila trae su `dtComu`, pero
**Italia obliga a ocultar el precio pasado cierto umbral**.

**🇦🇹 Austria recorta la respuesta**: pedir Viena entera devolvió 15 estaciones, y solo un carburante
por consulta. Sirve para "las de al lado", no para una capa de mapa. **🇬🇧 Reino Unido es el de mejor
frescura** —los grandes están obligados a publicar en 30 minutos, con Open Government Licence— pero
**da 403 sin credenciales**: exige registro OAuth2. ⚠️ Una fuente secundaria afirmaba que era abierto
sin registro; la medición dice que no. **🇩🇪 Alemania** son datos oficiales MTS-K servidos por
Tankerkönig con CC BY 4.0 y clave gratuita. **🇲🇽 México** son dos XML de la CRE unidos por
`place_id`.

**No verificado** (endpoint no confirmado, así que no se promete nada): Grecia, Chile, Argentina.
**🇵🇹 Portugal sigue excluido** por la prohibición de uso comercial de la DGEG.

**Orden sugerido tras España:** Francia (la más limpia y la única con radio + nombre ausente que
estrena el cruce con OSM) → Alemania → Italia (la más grande, pero exige descarga entera, umbral de
antigüedad y `isSelf`) → el resto.

## Diseño

**Una fuente por país, no capas que se cruzan.** Un feed nacional devuelve el objeto ENTERO —marca,
coordenadas, horario y precios en la misma respuesta—, así que en España una sola llamada basta y
OSM no aporta nada allí. El registro es `countryCode → fuente`, y esa fuente responde entera.
Cruzar objeto a objeto solo haría falta en Francia, cuyo feed no trae nombre de estación; se verá
cuando toque Francia.

- `domain/fuel/FuelStation` — id, marca, dirección, coordenadas, horario y `prices: Map<FuelType, Money>`.
  Un precio ausente es un estado normal (esa estación no vende ese producto), nunca un cero.
- `domain/fuel/FuelStationsSource` (puerto) — ahora SÍ se escribe, porque nace con dos
  implementaciones reales (Ministerio y OSM) y no con una suposición, que es lo que hizo que
  FUEL-PRICE-RESOLVER-001 lo dejara fuera.
- `data/fuel/MineturStationParser` (commonMain, con test y fixture real) — el formato tiene cuatro
  trampas medidas: **decimales con coma** en precios Y coordenadas, **claves con acentos y
  espacios** (`Precio Gasóleo A`, `Longitud (WGS84)`), **cadena vacía = no vende ese producto**
  (no es cero), y **BOM UTF-8** en la respuesta.
- `data/fuel/SpanishProvinces` — el feed se pide POR PROVINCIA (el país entero son 12 MB). El id
  sale de `/Listados/Provincias/`, que se cachea; se casa con `AddressInfo.region` sin acentos ni
  mayúsculas. ⚠️ Su campo se llama **`IDPovincia`** — así, con la errata del Ministerio.
- **Transporte con Ktor en commonMain, NO el patrón de Overpass.** Overpass parte el transporte en
  dos clases de plataforma (61 líneas en `androidMain` + 83 en `iosMain`) porque usa
  `HttpURLConnection`. Con ocho países eso son **dieciséis clases**. Comprobado que no hace falta:
  Ktor ya entra en commonMain vía `api(libs.coil.network.ktor3)` y **los dos motores ya están**
  (OkHttp en androidMain, Darwin en iosMain) porque Coil los necesita — un cliente común **no añade
  ni una dependencia**. Cada país pasa a ser **un fichero en commonMain**, testeable con `MockEngine`
  en vez de solo a mano.
- **Caché en memoria con TTL en esta fase, Room después.** Persistir exige bump de esquema y
  `Migration`, y todavía no sabemos cuánto dura un precio útil — en Italia el propio feed dice que
  menos de lo que parece. La caché en memoria ya cubre abrir y cerrar la pantalla, que es el caso
  real; la persistencia se decide con el TTL medido, no antes.
- Home: sección colapsable bajo las plazas, con nombre, distancia, **solo el precio del combustible
  del coche activo** y la hora del dato.

## Hallazgo que cambió el modelo: el silencio no significa lo mismo en cada fuente

Medido en Madrid el 09-09: OSM **nombra el 100 %** de sus gasolineras (25/25, marca en 24/25) — o
sea que es exactamente la pieza que le falta a Francia. Pero solo **el 44 % lleva etiquetas `fuel:*`**
(11 de 25), y **su ausencia significa "no se sabe", no "no lo vende"**.

Eso choca de frente con España, donde la cadena vacía **sí** es un negativo declarado (265 de 292 en
Cádiz dicen así que no tienen GLP). La misma ausencia, dos significados opuestos, y solo la fuente
sabe cuál. Un booleano `sells()` era mentira en una de las dos.

→ `FuelAvailability { SOLD, NOT_SOLD, UNKNOWN }`, y la regla que se deriva: **una gasolinera se oculta
solo con `NOT_SOLD`**. Ocultar los `UNKNOWN` vaciaría la lista en medio mundo.

## Estado (09-09-2026) — capa de datos COMPLETA, 39 tests

| Pieza | Qué resuelve |
|---|---|
| `domain/fuel/FuelStation` + `FuelAvailability` | El modelo, con "no se sabe" como respuesta legítima |
| `domain/fuel/FuelStationsSource` + `FuelStationsQuery` | El puerto; país y región viajan juntos porque salen del mismo geocode |
| `data/fuel/MineturStationParser` + `SpanishProvinces` | España: 4 trampas medidas + la errata `IDPovincia` |
| `data/fuel/MineturStationsSource` | España entera, probada con `MockEngine` **petición incluida** |
| `data/fuel/OsmFuelStationParser` + `OsmFuelStationsSource` | El suelo mundial, sin un solo precio |
| `data/fuel/FuelStationsRegistry` | Quién contesta y **cuándo se cae a OSM** |
| `data/fuel/CachingFuelStationsSource` | TTL 15 min en memoria; ⛔ nunca cachea un fallo |
| `domain/usecase/fuel/GetNearbyFuelStationsUseCase` | El veredicto: dónde repostar ESTE coche y a cuánto |

| `presentation/home/model/HomeFuelSlice` | El estado de la sección; ⛔ vacío ≠ fallo |
| `…/sheet/components/HomeFuelSection` + `HomeFuelStationRows` | La sección plegable y la fila |
| `HomeIntent.ToggleFuelStations` + `HomeViewModel.toggleFuelStations()` | Plegar/desplegar y pedir **solo la primera vez** |
| `fakes/…/FakeFuelStationsSource` + `MockModule` | El puerto en el segundo grafo; sin red, tres surtidores relativos al GPS falso |
| `app/src/testMockDebug/…/MockKoinModuleVerifyTest` | Que no vuelva a haber un puerto declarado en un grafo y no en el otro |

**Cerrado:** suite **2.493 tests / 0 fallos** (2.491 de `:shared` + los 2 del verify mock nuevo), los 6 strings en
los **9 locales** (paridad verde), galería con **6 variantes**, `assembleMockDebug` y `prodDebug`
compilando, y klib de iOS.

⛔ **Dos cosas que cazó la suite y no el runtime:**
1. `KoinModuleVerifyTest` — registrar `single { HttpClient() }` rompe el grafo: `HttpClient()` es una
   **fábrica** que resuelve su motor del classpath, pero el verificador mira el **constructor** y
   entonces exige un `HttpClientEngine` que nadie provee. El cliente se crea dentro del `single` de
   la fuente, donde igualmente lo comparten todos los adaptadores de país.
2. El `when` de enrutado de `HomeViewModel` obligó a declarar la rama del intent nuevo — que es
   exactamente para lo que está el sealed.

## El agujero que la galería no podía ver — y los tres que arrastraba

Medido en el emulador el 09-09 con un APK de hash comprobado: **entrar en Home mataba la app mock**
con `NoDefinitionFoundException: FuelStationsSource`. El flavor `mock` **no carga `dataModule`**, así
que mi `single` no existía allí; el use case se resuelve al construir `HomeViewModel` y ahí moría.

⛔ **Sobrevivió a una ronda entera de verificación en device.** La galería de estados llama a los
composables directamente y no construye NUNCA un ViewModel: **una pasada por la galería no es una
pasada por la app, y ninguna de las dos es una comprobación del grafo.**

Y no era la primera vez: `MockModule` ya cargaba tres comentarios que dicen literalmente *"the mock
stack has no dataModule, so the port must exist here or resolving X crashes"* — uno por cada vez que
alguien se enteró igual. Por eso el arreglo no es el fake, es el **verify del segundo grafo**
(`app/src/testMockDebug/…/MockKoinModuleVerifyTest`), gemelo del que ya protege producción. Nada más
encenderlo destapó **tres agujeros preexistentes** que nadie había pisado todavía:

| Puerto que faltaba en mock | Lo ataba solo | Qué reventaba |
|---|---|---|
| `FuelStationsSource` | `dataModule` | **Home**, al entrar (este ticket) |
| `DrivingRouteStore` | `androidDetectionModule` | `HomeTripController`, al arrancar un viaje |
| `OpenSourceLicenseRepository` | `dataModule` | la pantalla de licencias, al abrirla |
| `LocalDiagnosticsLog` | `androidPlatformModule` | "Enviar reporte" de soporte |

Los tres últimos van con el **real**, no con un falso, por la misma razón en cada caso: un falso
enseñaría una atribución OSS inventada, un reporte sin el log que ES el reporte, o un sim de
conducción que no redibuja su ruta. Solo `FuelStationsSource` lleva fake, porque el mock no debe
salir a la red.

## Lo que solo se vio abriéndola en el Pixel 8

**La sección salía SIN chevron.** `PapSectionHeader` aplica `fillMaxWidth()` a su propio `Text`, así
que dentro de mi `Row` se comía la fila entera y empujaba el icono fuera de pantalla: una sección
plegable **sin nada que dijera que se pliega**. Arreglado con `Modifier.weight(1f)` en la cabecera.

⛔ La galería no podía cazarlo aunque tenía la variante «Plegada»: el fallo estaba en la fila del
encabezado, y las cinco variantes que sí se miraron eran las DESPLEGADAS. Tener la variante no es
haberla abierto.

⛔ **`uiautomator dump` devuelve el fichero ANTERIOR cuando falla**, sin decirlo. Media hora leyendo
pantallas que ya no existían — y de ahí salieron dos conclusiones falsas seguidas («la lista no
scrollea», «la sección no se emite»). Contra este emulador, la verdad es la **captura**, no el dump.

⚠️ Pendiente de verse en mano: la sección en el emulador con datos reales de España. ⛔ El emulador
compartido **no sirve para dar nada por bueno**: otra sesión reinstaló su APK encima dos veces
(comprobado descargando el APK instalado y mirando su DEX), y durante un rato estuve navegando su
build creyendo que era el mío.

## Criterio de éxito

- Tests del parser con fixture real: coma decimal, clave acentuada, vacío ≠ cero, BOM.
- Test de que la provincia se resuelve sin acentos ni mayúsculas ("Cádiz" → "11").
- Test de que una estación sin el producto del coche activo no se pinta con precio.
- La sección sale en device con datos reales de España.
- Strings ×9 · galería · Konsist · suite · assembles · klib iOS.
