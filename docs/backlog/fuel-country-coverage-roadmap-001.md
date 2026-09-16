# FUEL-COUNTRY-COVERAGE-ROADMAP-001 · Qué países tienen precio de surtidor, y en qué orden entran los siguientes

**Estado:** 🔵 **Plan, sin empezar** — ninguna fase tiene rama ni worktree. Escrito el **2026-09-16**
contra master `e575350e`, leyendo el código (no el recuerdo): `FuelStationsRegistry`,
`DataModule.kt:283` y las cuatro fuentes nacionales.

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

1. **El ticket del galón** — pequeño, cierra deuda ya escrita, y arregla un fallo que **hoy corrompe
   datos en silencio** en cualquier US que use la app. No depende de ningún feed.
2. **En paralelo: lanzar el registro OAuth2 de UK.** Es el único plazo que no controlamos.
3. F1 UK → F2 MX → F3 RO (medición) → F4 AT (solo con el contrato ya decidido).
4. F5 US solo como medias EIA, y solo si el peldaño 1,5 se gana su propia frase en la UI.

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
