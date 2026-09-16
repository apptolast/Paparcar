# FUEL-COUNTRY-COVERAGE-ROADMAP-001 · Qué países tienen precio de surtidor, y en qué orden entran los siguientes

**Estado:** 🔵 **Plan, sin empezar** — ninguna fase tiene rama ni worktree. Escrito el **2026-09-16**
contra master `e575350e`, leyendo el código (no el recuerdo): `FuelStationsRegistry`,
`DataModule.kt:283` y las cuatro fuentes nacionales.

🔴 **F1 (UK) está bloqueada por ACCESO desde el mismo día que se escribió esto**: el portal —y el
sitio público— devuelven **403 desde España** en el borde de CloudFront. ✅ **Geobloqueo CONFIRMADO
el 16-09 con una sonda desplegada en Londres y borrada el mismo día**: desde `europe-west2` las
mismas URLs dan 200 y la API contesta un 403 *de aplicación* pidiendo el token. Lo único que sigue
bloqueado es **el alta** (One Login, flujo de navegador). Medición completa y sus consecuencias en
F1; el orden de ejecución (§8) ya está reordenado: **empieza México**.

Continúa [`fuel-stations-list-001.md`](fuel-stations-list-001.md), cuyo «barrido medido» del 09-09
abrió esta cola y **ya ha quedado desfasado en dos puntos** (ver §3). Sucede en la cola menor de
[`plus-roadmap-001.md`](plus-roadmap-001.md) a la entrada «Italia, siguiente país del orden medido»,
que se cerró el 16-09.

---

## 1 · Lo que hay hoy — dos escalones, y no se mezclan

La escalera de precio tiene peldaños distintos (`FuelPriceOrigin`) y contar «cobertura» sin decir
cuál se cuenta es lo que hace que un país parezca cubierto cuando no lo está.

### Escalón 1 · precio POR SURTIDOR — cuatro países

| País | Fuente | Clave | Estaciones | Lo que ese país enseñó al sistema |
|---|---|---|---|---|
| 🇪🇸 ES | Ministerio `PreciosCarburantes` | no | ~11.500 | la hora es de la RESPUESTA · se enruta por código postal |
| 🇫🇷 FR | `data.economie.gouv.fr` | no | 9.805 | la hora es del PRECIO (`StationPrice`) · sin nombre → cruce con OSM **por id oficial** (`ref:FR:prix-carburants`), nunca por proximidad |
| 🇩🇪 DE | MTS-K vía Tankerkönig | **sí**, gratis | — | `pricesAreLive` (frescura por ley) · la clave entra como **proveedor**, y sin clave **no reclama** |
| 🇮🇹 IT | MIMIT `ospzApi/search/zone` | no | 23.984 | `isSelf` es parte de la IDENTIDAD del precio (0,36 €/L de diferencia) |

**Suelo mundial:** OSM, 567.615 gasolineras y **cero precios**. Dice dónde repostar, no a cuánto.

### Escalón 2 · media nacional — 161 países

`EmbeddedAverageFuelPrices` (OpenVan, `AS_OF 2026-09-05`) + refresco de red. Alimenta el **gasto
estimado de los viajes**, no las filas del mapa. ⛔ Siempre por litro y en la moneda del país: los 14
países que cotizan por galón los normaliza el generador **al entrar**.

## 2 · Cuánto queda

Medido contra **nuestros 9 locales**, que es la cobertura que decide si esto se nota:

- ✅ con precio de surtidor: **ES · FR · DE · IT** (4 de 9)
- ❌ sin precio de surtidor: **PT · NL · PL · RO**, y el locale base **EN** (UK, US, IE)
- ⛔ **PT está excluido a propósito, no pendiente**: la DGEG prohíbe el uso comercial de su feed y
  publicamos en portugués. Ya está escrito en el código para que nadie lo «arregle» dentro de seis
  meses. No se revisa salvo que cambie la licencia.

## 3 · Dos correcciones al barrido del 09-09

1. **🇬🇧 UK cambió de estado.** Entonces daba 403 y se aparcó. Hoy el esquema **Fuel Finder** es
   estatutario: regulaciones de 2025, vivo desde el **2-feb-2026**, enforcement desde el
   **1-may-2026**, obligación de reportar cada cambio de precio en **30 minutos**, feed JSON único y
   datos públicos. Sigue exigiendo OAuth2. Pasa de «descartado» a **primero de la cola**.
2. **🇦🇹 Austria ya cabe en el modelo.** Se descartó por recortar la respuesta (Viena entera → 15
   estaciones), pero `FuelStationsResult.isTruncated` **no existía** cuando se escribió eso. Hoy una
   respuesta capada es un resultado legítimo que se sabe describir. Lo que sigue sin caber es otra
   cosa — ver F4.

---

## 4 · Fase 0 · la regla que no se salta, por país

Este proyecto se ha equivocado **dos veces** prometiendo un feed sin medirlo: el 404 del Ministerio
(era un error nuestro de una letra) y el CSV italiano (era el canal equivocado del ministerio
correcto). Los dos fallos tienen la misma forma: razonar sobre una fuente en vez de llamarla.

⛔ **Ningún país entra en `national` sin esta tabla rellena con `curl` contra el endpoint real.**

| Qué medir | Por qué muerde |
|---|---|
| Endpoint vivo, y qué devuelve **sin** credenciales | de aquí salieron el 403 de UK y el 404 de ES |
| ¿Consulta por radio, o hay que filtrar en el móvil? | decide si sirve para la capa de mapa o solo para «las de al lado» |
| **Unidad del precio** | UK publica en peniques; leerlo como libras es un ×100 mudo |
| ¿Un precio por surtidor, o varios? | `isSelf` italiano = 0,36 €/L. En US sería *cash vs credit* |
| Dispersión de edad DENTRO de una respuesta | decide si la hora es de la respuesta o del precio |
| ¿Trae nombre y marca? | si no, cruce con OSM **por id oficial**. Por proximidad se pierde la mitad o se etiqueta mal 1 de cada 10 |
| Tope de salida | rellena `isTruncated` |
| Licencia, y si permite uso comercial | esto es lo que dejó fuera a Portugal |

**Y una comparación obligatoria**: contra el mismo instante de otra fuente, como se hizo con
FuelWide (30 % de precios distintos → descartado) y con el CSV italiano (71,6 % → descartado). Un
feed que no se compara con nada no está medido, está leído.

## 5 · El molde — lo que cuesta un país sin sorpresas

```
+ data/fuel/<Pais>FuelStationsSource.kt      ← covers/claims/stationsNear, const SOURCE
+ data/fuel/<Pais>StationParser.kt           ← objeto puro
~ di/DataModule.kt                           ← UNA línea en `national`
+ commonTest/.../<Pais>StationParserTest.kt  ← fixture del feed real + MockEngine
```

Cero cambios en use cases, UI, Room o caché: `CachingFuelStationsSource` y
`DiskBackedFuelStationsSource` envuelven al **registro**, no a una fuente, así que heredan a quien
conteste y la fuente se nombra sola en el resultado. **Ese es el presupuesto base.** Todo lo que
sigue son los sobrecostes propios de cada país.

⛔ Y lo que el molde NO permite: mezclar dos fuentes para un mismo país. Una fuente contesta ENTERA.
El único cruce que existe es el de Francia con OSM **para el nombre**, y es por id, no por cercanía.

---

## 6 · Las fases, en orden

### F1 · 🇬🇧 Reino Unido — `FUEL-UK-A-PRICE-REPORTED-IN-THIRTY-MINUTES-001`

El único país que aporta algo que hoy no tenemos: **frescura garantizada por ley**, y encima en el
locale base.

**Sobrecoste real, y es de verdad:** las cuatro fuentes actuales son un `GET` sin estado. Alemania
tiene clave, pero es un parámetro estático. UK exige **OAuth2 client-credentials** → sería el primer
**token con ciclo de vida** del proyecto: obtener, cachear en memoria, refrescar al 401, y **no
reintentar en bucle**.

Diseño propuesto, para que eso no contamine a nadie más:
- El token vive **dentro** de `UkFuelStationsSource`. Las credenciales entran por el mismo patrón
  proveedor que Alemania (`() -> String` leído de `local.properties`) — **el repo es público**.
- ⛔ **Sin credenciales, no reclama.** Copia literal del `claims` alemán y por su misma razón: un
  build sin clave (cualquiera que clone el repo) debe comportarse como «UK no tiene feed», no como
  «el feed de UK está caído», que es lo que pondría `didFallBack` y una disculpa perpetua en la UI.
- Un fallo de token es un `Result.failure` normal → el registro cae a OSM. No hay ruta nueva.

**A medir antes de escribir una línea:** el trámite de registro (¿cuánto tarda?, ¿hay sandbox?, ¿pide
entidad legal?), la **unidad del precio** (peniques, casi seguro), y el mapeo de grados: `E10`, `E5`,
`B7`, `B7 premium`, `B10`, `HVO`. Los tres últimos no tienen casilla en `FuelType`, e **inventarles
una es peor que ignorarlos** — un HVO enseñado como diésel es un precio que no se puede comparar con
el de al lado.

#### 🔴 Medición del 16-09 — F1 está BLOQUEADA antes de empezar, y no por código

Primer intento de abrir el portal (navegador del user, desde España) → **403**. Caracterizado con
`curl` desde la misma máquina y desde un segundo punto de salida:

| Host | Desde Madrid | Cabeceras |
|---|---|---|
| `www.developer.fuel-finder.service.gov.uk` (portal + `/api/v1/oauth/generate_access_token`) | **403** | `Server: CloudFront`, `Content-Length: 0`, `X-Cache: Error from cloudfront`, `X-Amz-Cf-Pop: MAD56-P1` |
| `www.fuel-finder.service.gov.uk` (el sitio **público**, el del ciudadano) | **403** | idéntico |
| `www.gov.uk/guidance/…` (otra infraestructura, nginx/varnish) | **200** | — |

**Lo que eso descarta y lo que deja vivo:**
- ⛔ No es autenticación. Un rechazo de la aplicación sería **401 con cuerpo**; esto es un 403 **con
  cuerpo vacío desde el borde de CloudFront**, y cae igual sobre el sitio público que cualquier
  británico debe poder abrir sin cuenta.
- ⛔ No es el User-Agent: mismo resultado con UA de Chrome, y el user lo vio en un navegador real.
- ⛔ No es «la red del user»: un segundo punto de salida, fuera de España, devuelve el mismo 403.
- 🔵 **Hipótesis, no hecho**: geo-restricción de CloudFront (o regla WAF) que solo admite tráfico del
  Reino Unido. **La prueba que falta es una sola**: abrirlo desde una IP británica. Si carga, queda
  confirmado; si no, hay que escribir al equipo de Fuel Finder.

**Lo que NO significa, y conviene no exagerarlo:** si el geobloqueo es real, **el producto sigue en
pie**, porque quien pregunta por gasolineras del Reino Unido está en el Reino Unido. Lo que rompe es
**nuestra Fase 0**: no se puede medir, ni capturar el fixture, ni depurar desde aquí — y aquí no
entra ningún país sin medición. F1 no está bloqueada por dificultad, está bloqueada por **acceso**.

⚠️ Y deja un riesgo de runtime que habrá que verificar el día que se cablee: un usuario británico
cuyo tráfico salga por VPN o CGNAT extranjero recibiría 403. El camino ya existe y es el correcto
(`Result.failure` → OSM), pero hay que comprobar que no se queda pegado disculpándose.

#### Cómo se desbloquea sin VPN — medido el 16-09

**1 · ✅ HECHO — la sonda de Londres, y el geobloqueo queda CONFIRMADO.** Se desplegó una función
HTTP de usar y tirar en `europe-west2`, se midió y **se borró el mismo día** (función eliminada,
código fuera, `functions/` sin cambios; la política de purga de imágenes ya existía, 1 día).

Salida verificada en la propia respuesta — `34.34.246.120`, **London, GB**, AS396982 Google — y con
eso el veredicto ya no es una hipótesis:

| URL | Desde España | Desde Londres |
|---|---|---|
| `/` del portal | **403** CloudFront, cuerpo vacío | **308** → `/fuel-finder`, `X-Cache: Miss from cloudfront`, PoP **LHR82** |
| `/fuel-finder/public-api` | **403** | **200**, 14.747 B de página real |
| `/api/v1/pfs?batch-number=1` | **403** de borde | **403 de APLICACIÓN**: `{"error":"Unauthorized","message":"Missing access token…"}` |

⛔ Esa última fila es la que importa: desde el Reino Unido **el plano de datos contesta**, y lo único
que le falta es el token. Desde España no se llega ni a la aplicación.

**Lo que la sonda además arrancó del portal, que ahorra media Fase 0:**
- **Hay TRES vías de acceso y solo una usa OAuth**: (a) **descarga de un CSV** con precios y
  forecourts, (b) **suscripción por email** que manda el enlace del CSV cuando se publica
  (`/fuel-finder/info-recipient/email-subscription/sign-up`), y (c) la API.
- **OAuth 2.0 client credentials**, `scope=fuelfinder.read`, `expires_in: 3600`. Dos entornos, **test
  y producción**, con credenciales distintas.
- **Límites medidos, y son el problema de diseño**: **100 peticiones/minuto por CLIENTE** y **UNA
  petición concurrente por cliente**, 429 al pasarse. Su propia guía recomienda *cachear los datos de
  estación una hora*.
- El alta es **GOV.UK One Login**, sin requisito de entidad británica en la página.

🔴 **Consecuencia que cambia el coste de F1, y hay que decirla ahora:** el límite es **por cliente**,
no por usuario, y un `client_secret` embebido en un APK público es a la vez un secreto regalado y un
cupo de 100 rpm **compartido entre todos nuestros usuarios**. Alemania se permite la clave embebida
porque su cupo lo tolera; UK, con 1 concurrente, no. Eso empuja a UK hacia un **relé propio con
caché** — o sea, el primer país que **no es un adaptador, sino un adaptador más backend**, en la
liga de coste de `PARKINGS-LIVE-OCCUPANCY-001`. Antes de escribir una línea de Kotlin hay que
decidir eso.

⚠️ **Lo que la sonda NO resuelve: el alta.** El registro es un flujo interactivo de navegador con
GOV.UK One Login detrás del mismo 403. La función da el plano de datos, no la cuenta.
➡️ **Siguiente prueba barata, cuando se retome:** la **suscripción por email** no parece pedir One
Login. Si el formulario se puede enviar desde una salida británica y **el CSV que llega por correo
no está geovallado**, UK se desbloquea entero sin cuenta, sin OAuth y sin relé — y con el cupo de la
API fuera de la ecuación. Es la primera medición del día que se retome F1.

**2 · Escribir al equipo de Fuel Finder** — la única vía que resuelve el ALTA sin un navegador
británico. La pregunta tiene que ser explícita: *¿el acceso exige IP del Reino Unido, y puede
registrarse un desarrollador desde fuera?* La respuesta es un hecho duradero: si la API es UK-only
por diseño, un relé propio deja de ser una comodidad de desarrollo y pasa a ser **dependencia de
producción**, con su coste y su decisión de arquitectura.

**3 · El mosaico de minoristas — banco de pruebas, JAMÁS fuente.** Barridos los 15 endpoints del
esquema voluntario anterior desde España: **8 contestan**, sin clave y sin bloqueo. Motor Fuel Group
1.224 estaciones, Asda 790, Esso 194, SGN 112.

⛔ Y aun así **no se cablea**, por dos medidas:
- **Decae, y se ve dónde.** Rontec congelado el **01-05-2026** — *exactamente* el día en que empezó
  el enforcement de Fuel Finder—, Applegreen en **feb-2025**, Morrisons devuelve **una** estación, y
  BP y Tesco dan 403 de antibot. El mosaico se está apagando porque el esquema estatutario lo
  sustituyó.
- **Los vivos se actualizan UNA VEZ AL DÍA** (MFG `00:00:01`, Esso `00:05:11`), no en 30 minutos. Es
  peor que los cuatro países que ya tenemos, y un precio de ayer **no se distingue en pantalla** de
  uno bueno: la razón exacta por la que se rechazó FuelWide con doce horas.

✅ Para lo que **sí** sirve, y es mucho: medir el esquema **hoy**, sin acceso. Ya medido sobre MFG:
`site_id` · `brand` (≠ operador: hay «BP» dentro del fichero de MFG) · `address` · `postcode` ·
`location{latitude,longitude}` decimal · `prices{E10, E5, B7, SDV}`.
🔴 **Y confirma el ×100 que este doc daba por «casi seguro»: los precios vienen en PENIQUES**
(`"E10": 174.9` = 1,749 £/L, rango medido 131,9–209,9). Ya no es una sospecha, es una medición.
`SDV` (diésel súper) es un cuarto grado que tampoco tiene casilla en `FuelType`.

#### Corrección a este mismo doc: UK **no** consulta por radio

El proyecto de terceros que ya integra el feed usa `GET /api/v1/pfs?batch-number=N` y
`GET /api/v1/pfs/fuel-prices?batch-number=N` — es decir, **descarga paginada del país entero**, no
una consulta por radio, con `POST /api/v1/oauth/generate_access_token` y **bearer de 1 h de TTL**.
O sea que UK se parece mucho más a **México** (bulk + filtrado en el móvil) de lo que sugería este
plan, y el sobrecoste del token se suma al del bulk en vez de compensarse. ⚠️ Esto es lectura de un
repo ajeno, **no medición nuestra**: entra en la Fase 0 como lo primero a confirmar el día que haya
acceso.

**Riesgo que puede reordenar el plan:** si el registro OAuth2 tarda o exige algo que no tenemos, F1
se bloquea y **F2 pasa delante**. Eso se decide al medir, no después de empezar.

### F2 · 🇲🇽 México — `FUEL-MX-TWO-FILES-ARE-ONE-STATION-001`

Sin clave y sin OAuth. No suma locale, suma mercado hispanohablante grande.

**Sobrecoste:** dos XML de la CRE (`places` + `prices`) unidos por `place_id`, y **hoy no hay un solo
parser XML en `data/`** — todo es JSON o CSV. Hay que decidir si entra un parser XML de verdad.
Además son ficheros enteros (~0,8 MB) **sin consulta por radio** → filtrado en el móvil, como ya hace
España con la provincia; la caché de disco existente absorbe la mitad del coste.

**Trampa a medir:** cuántos `place_id` casan entre los dos XML. Una estación sin precio es una
estación **sin precio** (`prices` vacío, `availability` UNKNOWN), nunca una estación que se oculta.

### F3 · 🇷🇴 Rumanía — `FUEL-RO-THE-MONITOR-HAS-A-BACKEND-001`

Locale nuestro. El **Monitorul Prețurilor Carburanților** (Consiliul Concurenței, desde 2019) tiene
web y **app propia iOS/Android** → hay un backend que responde por zona. Es exactamente la situación
que resolvió Italia cuando el CSV resultó ser el canal equivocado del mismo ministerio.

**Esta fase es 80 % medición.** Dos desenlaces, los dos válidos:
- El endpoint contesta y la licencia lo permite → país barato, molde estándar.
- No → **se cierra con el motivo escrito**, como Portugal, y no se vuelve a proponer.

⚠️ Cubre solo las redes grandes (OMV Petrom, Mol, Rompetrol, Lukoil, Socar, Gazprom), **no el país
entero**. Eso NO es `isTruncated`: «la respuesta se cortó» y «esta fuente solo conoce a los grandes»
son dos frases distintas y la segunda no se puede decir hoy. Si F3 sale adelante, nace su campo
hermano — y ese campo es la parte de diseño de la fase, no el parser.

### F4 · 🇦🇹 Austria — `FUEL-AT-A-CAPPED-ANSWER-IS-STILL-AN-ANSWER-001`

Barata en lo que la descartó (el recorte ya se sabe decir), **cara en lo que no se miró**: E-Control
devuelve **un carburante por consulta**. Eso rompe la forma del contrato — `stationsNear` devuelve
estaciones con *todos* sus precios. Opciones: N peticiones (lento y feo) o servir solo el carburante
del coche (cambia el contrato para todos).

⛔ **No abrir antes que F1-F3.** La decisión correcta depende de si para entonces la capa ya pide por
carburante, y decidirla ahora sería fijar el contrato del sistema por el país más pequeño de la cola.

### F5 · 🇺🇸 EEUU — `FUEL-US-A-STATE-AVERAGE-IS-NOT-A-PUMP-001` · **bloqueada**

**No hay fuente per-station legal.** No existe mandato federal de publicación (el equivalente a Fuel
Finder o al MIMIT), así que el dato por surtidor es propietario: GasBuddy no publica licencia pública
—hay que negociar— y OPIS es de pago. Todo lo que aparece «con API» son *scrapers* de GasBuddy.
⛔ **Scrapear el inventario de un tercero para una app publicada en Play no es una opción**, y esto no
se arregla con esfuerzo: se arregla si algún día hay feed.

Lo que sí es público: la **EIA** sirve medias semanales por región PADD y algunos estados y áreas
metropolitanas, dominio público y clave gratis. Eso **no es el escalón 1**: es un escalón 1,5 — *la
media de tu estado* — y hoy `FuelPriceOrigin` no tiene esa casilla (`UserOverride` / `Station` /
`NationalAverage`). Entra como peldaño nuevo **con su propia frase en la UI**, o no entra.

**Dos bloqueos previos, en este orden:**
1. **El ticket del galón** (§7). Sin él, un estadounidense lee «$0.95 per litre» y no sabe qué es.
2. Decidir si un peldaño 1,5 puede pintarse en la **capa del mapa**. Mi recomendación: **no**. La
   capa del mapa promete el precio *de esa gasolinera*; poner la media del estado sobre pines
   individuales es la mentira que descartó a FuelWide, solo que mayor. EIA alimenta el **gasto del
   viaje**; el mapa sigue enseñando forecourts de OSM sin precio, que es honesto y accionable.

---

## 7 · Anexo · el ticket del galón, que bloquea F5

Hereda el follow-up abierto de
[`i18n-a-radius-written-by-hand-ignores-miles-001.md`](i18n-a-radius-written-by-hand-ignores-miles-001.md).
Id propuesto: **`I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001`**.

⛔ **Aquel follow-up está redactado al revés y no debe ejecutarse literalmente.** Dice que hay que
«bajar el galón a toda la capa de precios (`StationPrice`, `AverageFuelPriceTable`, las alertas y los
feeds)». El KDoc de `AverageFuelPriceTable` **prohíbe justo eso**, y con su razón medida: *un galón
llegando a esa clase infla un viaje en US ×3,785 en silencio*. El galón es un problema de
**frontera**, simétrico a cómo el generador ya normaliza al entrar los 14 países que cotizan por
galón. **El dominio se queda en litros.** Corregir esa frase en aquel doc es parte del ticket.

**El fallo tiene dos mitades, y la segunda es la cara:**
- **Salida** — «Price per litre», el precio de estación, la media, las alertas: se leen mal, pero se
  leen.
- **Entrada** — `vehicle_registration_fuel_price_label`: un estadounidense teclea `3,49` creyendo que
  es su galón y la app lo guarda como precio del **litro**. El gasto de todos sus viajes queda
  inflado ×3,785, **con un número que parece correcto**. Sin síntoma visible.

**Alcance:**
1. Función pura de frontera junto a `ConsumptionFormat.kt` (commonMain → sus tests corren también en
   iOS): litro↔galón según `GallonKind`. Los dos sentidos, un solo sitio.
2. Reusar el resolver que YA existe: `LocaleUnits.kt` → `countryUsesUsGallon()` / `localeGallonKind()`
   / `US_GALLON_COUNTRIES = {US, LR}`. ⛔ Y respetar lo ya decidido y medido allí: **el galón sale del
   LOCALE, no del GPS**, y `GB` está en `IMPERIAL_COUNTRIES` pero **no** en `US_GALLON_COUNTRIES` —
   en UK el surtidor es en litros aunque el coche se hable en mpg. **Este ticket no toca UK.**
3. Salida: `FuelPriceFormat.kt`, `HomeFuelStationRows.kt`, la media en las superficies de stats y el
   copy de las dos alertas de precio. Barrido por `«/L»`, `«per litre»`, `«litre»` en `presentation/`
   y en `strings.xml`. ⚠️ Los **tres decimales se quedan**: US también cotiza `$3,499/gal`.
4. Entrada: `VehicleRegistrationViewModel` + `State`/`Intent`. Convierte a litros **antes** de
   persistir, y la etiqueta dice qué unidad se espera. Verificar el campo en **todos** sus call sites
   (alta y edición), no solo el alta.
5. i18n ×9 en la misma tarea. Apóstrofo **crudo** en `composeResources`; `%` literal **sin doblar**.
6. Dev Catalog: variante en galones en la galería (ficha de vehículo + lista de gasolineras).
7. Lado iOS: `LocaleUnits.kt` ya es commonMain; verificar que `Locale.current.region` contesta en iOS
   y, si no, dejar el hueco escrito con la causa.

**El guard que falta** — prohibición sin testigo no es chequeo: un test que demuestre que un precio
tecleado en galones se **persiste en litros**. El fallo de entrada no tiene síntoma; sin ese test,
vuelve.

⛔ **No** meter un ajuste explícito de unidad de volumen: ya se descartó al decidir que el galón sale
del locale.

---

## 8 · Orden recomendado de ejecución

⚠️ **Reordenado el mismo 16-09, horas después de escribirlo**, por el 403 medido en F1. El orden
original era «F1 UK primero»; UK no puede ir primero mientras no se pueda ni abrir el portal.

1. **El ticket del galón** — pequeño, cierra deuda ya escrita, y arregla un fallo que **hoy corrompe
   datos en silencio** en cualquier US que use la app. No depende de ningún feed, ni de ningún
   acceso: es lo único de esta lista que se puede empezar sin pedirle permiso a nadie.
2. **F2 México** sube al primer puesto de países: sin clave, sin OAuth, y **medible hoy mismo desde
   aquí**, que es justo lo que UK ya no es.
3. **En paralelo, desbloquear UK** — tres vías, por orden de coste: (a) abrir el portal desde una IP
   británica para confirmar o descartar el geobloqueo; (b) cuenta **GOV.UK One Login** y alta en
   `https://www.developer.fuel-finder.service.gov.uk/access-latest-fuelprices`; (c) escribir al
   equipo de Fuel Finder si (a) confirma el bloqueo. **F1 arranca el día que haya acceso, no antes.**
4. Luego F3 RO (medición) → F4 AT (solo con el contrato ya decidido).
5. F5 US solo como medias EIA, y solo si el peldaño 1,5 se gana su propia frase en la UI.

## 9 · Fuentes consultadas el 16-09-2026

Búsqueda web, **no medición**. Nada de aquí cuenta como verificado hasta que pase la Fase 0.

- UK Fuel Finder — [gov.uk, access fuel price data](https://www.gov.uk/guidance/access-fuel-price-data) ·
  [Fuels Industry UK](https://www.fuelsindustryuk.org/consumer-information/fuel-finder-scheme-essential-information-for-industry-and-consumers/)
- GasBuddy, sin licencia pública — [perfil en Datarade](https://datarade.ai/data-providers/gasbuddy/profile)
- Rumanía — [Monitorul Prețurilor](https://monitorulpreturilor.info/Home/Gas/Descriere)
- Polonia (descartada: lo oficial es el **precio máximo** ministerial, no el del surtidor; el resto es
  crowdsourcing privado) — [dane.gov.pl](https://dane.gov.pl/en/dataset/3453,wykaz-stacji-paliwowych-i-zakladowych-stan-aktualn)
- 🇳🇱 Países Bajos: sin feed oficial abierto conocido. Mismo veredicto que Polonia — **no cualifica
  hoy**, y por eso no tiene fase.
