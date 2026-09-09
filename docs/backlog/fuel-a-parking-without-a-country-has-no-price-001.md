# FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001 · el gasto no sale porque ningún aparcamiento sabe en qué país está

**Estado:** ✅ Done · rama `bugfix/FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001-stamp-the-country` · worktree `../Paparcar-parking-country`

Hallazgo lateral de `FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001`, **anterior a él y ajeno a él**.
Apuntado en vez de arreglado ahí: es otro subsistema (geocoder) y ampliar el encargo habría
mezclado dos cosas en un commit.

## Problema

El tile de **gasto aproximado** no aparece nunca. No es del gate de Plus: se comprobó forzando Plus
en el Oppo y sigue sin salir, mientras el resto de tiles (CO₂, estancia media, hora punta, día
habitual, detectados, plazas) sí salen.

## Medición

Diagnóstico temporal en `HistoryTimeline.resolve`, build **prodRelease** en el Oppo (09-09-2026):

```
COSTCHK countryCode=null sessions=20 addresses=20 countries=[] vehicleFuel=DIESEL override=null
COSTCHK litrePrice=null
```

Los **20** aparcamientos del periodo **tienen `address`** — y **ninguno tiene `countryCode`**.

La cadena, sin ningún eslabón roto salvo el primero:

```
address.countryCode = null
  → HistoryState.countryCode() = null
  → ResolveFuelPriceUseCase.litre(...) = null       (no hay país que consultar)
  → ComputePeriodStatsUseCase: litersContributed && fuelPricePerLiter == null ⇒ costEstimate = null
  → statTileKeys(): `if (stats.costEstimate != null) add(COST)` ⇒ el tile no se añade
```

⛔ El CO₂ **sí** sale (0,8 kg), y eso es lo que prueba que el resto del cálculo está sano: los
litros se computan (`litersContributed = true`, hay ruta medida). Lo único que falta es el precio,
y lo único que falta para el precio es el país.

## Doctrina violada

Un comentario que **afirma como hecho algo que no se cumple**. En `HistoryState.kt`, junto a la
llamada:

> *"The country comes from the parkings themselves — the reverse geocoder already stamps it on every
> one"*

Medido: no lo estampa en ninguno. El comentario describe el diseño pretendido, no el sistema real, y
por eso nadie fue a mirar: la suposición estaba escrita con la seguridad de un invariante.

## Consecuencia real

**Todo F5a es invisible.** El escalón 2 (media nacional) y el escalón 1 (surtidor) alimentan una
cifra que no se dibuja jamás, en ningún país. `FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001` corrigió los
precios de un −20 % de error a un +0,1 % contra el Ministerio, y **ese arreglo tampoco se ve** hasta
que esto se cierre. Los dos tickets son independientes, pero este es el que destapa al otro.

## Causa raíz — LOCALIZADA, y son DOS fallos

La dirección se estampa en un aparcamiento por la vía de enriquecimiento
(`EnrichParkingSessionWorker` → `UserParkingRepositoryImpl.updateParkingSessionAddressAndPlace` →
Room, y en paralelo `UpdateParkingSessionAddressAndPlaceWorker` → Firestore). El geocoder **sí**
produce el país (`AndroidGeocoderDataSourceImpl.toAddressInfo()`, `countryCode = countryCode?.lowercase()`).
Se pierde después, por enumeración campo a campo, en **cuatro sitios**.

### Fallo 1 — el país nunca se ESCRIBE (lo que se ve hoy)

| Sitio | Qué enumera | Falta |
|---|---|---|
| `UserParkingDao.updateAddressAndPlace` (SQL) | `addressStreet, addressCity, addressRegion, addressCountry` | **`addressCountryCode`** |
| `UserParkingRepositoryImpl:187` | `street, city, region, country` | **`countryCode`** |
| `UpdateParkingSessionAddressAndPlaceWorker.buildRequest` | 4 keys de `workData` | **`KEY_ADDRESS_COUNTRY_CODE`** |
| …`.toAddressDto()` | `AddressDto(street, city, region, country)` | **`countryCode`** |

⛔ La columna `addressCountryCode` **existe** en `UserParkingEntity`, y `ParkingSessionMapper` la
mapea en las dos direcciones **con test** (`assertEquals("ES", parking.toEntity().addressCountryCode)`).
Por eso el bug sobrevivió: el mapper que todo el mundo mira está bien. Quien la pierde es el
`UPDATE` parcial, que nadie miró porque *parecía* la lista completa de campos de dirección.

⛔ `AddressDto` **también tiene** `countryCode`, y `ReportSpotWorker` **sí** lo transporta
(`KEY_ADDRESS_COUNTRY_CODE`). O sea: el camino de la PLAZA lo lleva y el camino del APARCAMIENTO no.
Es exactamente la familia de [project_sync_the_workdata_field_list_is_a_contract_nobody_checks]: una
lista de campos de `workData` es un contrato que no compila y que nadie verifica.

### Fallo 2 — LATENTE, y lo destaparía arreglar el 1

`UserParkingDao.updateLocation` (arrastrar el pin a mano) pone a NULL
`addressStreet, addressCity, addressRegion, addressCountry` — y **NO toca `addressCountryCode`**.

Hoy no muerde porque ese campo nunca llega a valer nada. **En cuanto se arregle el fallo 1, mover el
pin dejaría el país VIEJO pegado a una dirección nueva** — y no es un hueco, es un dato *incorrecto*:
arrastra el pin cruzando una frontera y el gasto se valoraría en la moneda del país anterior.

Por eso esto no se arregla añadiendo una línea donde salió el síntoma: el invariante es
*«una dirección viaja ENTERA o no viaja»*, y hay que barrer sus cuatro enumeraciones y las dos
sentencias SQL a la vez.

## Diseño

**El invariante:** ningún camino que escriba o borre una dirección puede enumerar un subconjunto de
sus campos. `province` es la ÚNICA excepción, y ya está documentada en `AddressInfo` como
deliberadamente no persistida — así que la lista de exentos es de uno y está razonada.

1. **`UserParkingDao.updateAddressAndPlace`** — añadir `addressCountryCode = :countryCode`.
2. **`UserParkingDao.updateLocation`** — añadir `addressCountryCode = NULL` junto a los otros cuatro.
3. **`UserParkingRepositoryImpl.updateParkingSessionAddressAndPlace`** — pasar `address?.countryCode`.
4. **`UpdateParkingSessionAddressAndPlaceWorker`** — `KEY_ADDRESS_COUNTRY_CODE` en `workData` y en
   `toAddressDto()`, igual que ya hace `ReportSpotWorker`.

⛔ **Sin bump de esquema**: la columna ya existe desde v1 (está en el baseline
`AppDatabaseV1BaselineTest`). Se cambian sentencias, no el esquema — así que **no hace falta
`Migration`**, y eso es lo que mantiene esto lejos del ⛔⛔ de `pm clear` sobre los móviles de campo.

### El testigo

Un test que no vigile el PRÓXIMO campo no sirve de nada: el bug no es "falta el país", es "alguien
enumeró campos a mano". Así que el testigo enumera `AddressInfo` **por reflexión** y exige que cada
propiedad sobreviva al camino de enriquecimiento, con `province` como única exención nombrada. Un
campo nuevo en `AddressInfo` rompe el test hasta que alguien decida si se persiste.

## Alcance — lo que este ticket NO hace

Las sesiones **ya guardadas** siguen sin país: el arreglo es hacia delante y solo estampa
aparcamientos nuevos. Rellenar las viejas es una decisión aparte (implica volver a geocodificar
datos de campo reales) y se pregunta antes de tocar nada.

## Verificado en device — el tile existe

`prodRelease` (R8) en el Oppo, `sha256` device↔local idéntico. Antes: seis tiles y ningún gasto.
Después: **`9.43 EUR` bajo `APPROX. SPEND`**, y encabezando la fila — que es lo que manda
`statTileKeys` («spend leads when it exists — it is the figure a person can act on, so it is what
free sees»), así que su ausencia estaba además robándole al tier gratuito su única cifra accionable.

### La cifra cuadra al céntimo, y eso ata los dos tickets

Periodo: 118,3 km, 18 viajes, diésel a 5,5 l/100 km → **6,5065 L**.

| Precio del litro | Origen | Gasto |
|---|---|---|
| 1,45 € | tabla estática vieja (la que lleva ESTE build, que sale de master) | **9,43 €** ← lo mostrado |
| 1,8544 € | OpenVan, `FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001` | 12,07 € |
| 1,8146 € | Ministerio, 11.496 estaciones reales el 09-09 | 11,81 € |

O sea: `6,5065 × 1,45 = 9,43` **exacto**. Eso confirma tres cosas de una vez — que el arreglo llega
hasta la pantalla, que este build usa todavía la tabla rancia, y que al mergear el ticket de OpenVan
la misma pantalla pasará de 9,43 € a ~12,07 €, a **2 % de la verdad del Ministerio** en vez de un
20 % por debajo.

### Backfill de las sesiones ya guardadas

Autorizado por el user y ejecutado sobre `pap-26`: **25 documentos** (`users/{uid}/parkingHistory`)
de sus DOS cuentas. Por documento se tocó **solo** `address.countryCode` (null → `"es"`) y
`updatedAt`, vía `updateMask`; calle, ciudad, región, país, coordenadas, ruta y provenance quedaron
intactos, verificado en la respuesta de cada escritura. Los 25 estaban en España (`country` =
"España"/"Spain"), comprobado ANTES de estampar. ⛔ Las otras nueve cuentas del proyecto —personas
reales— no se tocaron.

`updatedAt` se subió a propósito: `reconcilePending` sólo deja ganar a la fila local si está
`pendingSync` **y** es estrictamente más nueva, así que sin eso la fila remota corregida podía no
bajar nunca a Room.

## Ampliación pedida por el user: `province` también se persiste (Room v5 → v6)

> «agreguemos los campos que quedan del geocoder, a partir de ahora irán en los datos»

El geocoder produce **seis** campos y persistían **cinco**. El que faltaba era `province`, y estaba
fuera **a propósito**: `AddressInfo` documentaba que ningún consumidor persistido lo leía y que un
null ahí era honesto. Ese argumento era correcto y aun así costó caro — es el mismo razonamiento
(«¿qué campos hacen falta de verdad?») que dejó a `countryCode` fuera de cuatro caminos de
escritura. Así que la regla pasa a ser más simple que cualquier argumento sobre consumidores:
**una dirección viaja ENTERA**. Sin lista de exentos que heredar.

### ¿Hacía falta migración?

- **`countryCode`: NO.** Las columnas `addressCountryCode` ya existían en las tres entidades desde
  el baseline v1 — los nulos ya estaban ahí. Por eso el arreglo principal no tocó el esquema.
- **`province`: SÍ.** No había columna en ninguna parte → `PAPARCAR_DB_VERSION` 5 → **6** y
  `MIGRATION_5_6` en `ALL_MIGRATIONS`, con tres `ALTER TABLE … ADD COLUMN` (`parking_sessions`,
  `cached_spots`, `geocoder_cache`). Nullable: una fila pre-v6 nunca capturó provincia, y null
  sigue queriendo decir «no capturada», nunca una afirmación.

### Barrido de `province`

Entidades (×3) · `MIGRATION_5_6` · `AddressDto` · `ParkingSessionMapper` (lectura, entity, DTO de
ida y de vuelta, helper compartido) · `SpotDtoMapper` (×3) · `RoomGeocoderCacheDataSource` (escribe
y lee) · `UserParkingDao` (el `UPDATE` y el borrado) · `UserParkingRepositoryImpl` ·
`UpdateParkingSessionAddressAndPlaceWorker` · `ReportSpotWorker`.

⛔ **Sexta fuga encontrada al barrer**: `ReportSpotWorker.toAddressInfo()` decidía «hay dirección»
mirando solo cuatro campos, así que una fila con solo país o solo provincia se leía como null y se
tiraba. Misma forma que `addressOrNull()`. Corregida.

### El testigo se queda SIN exenciones

`ParkingAddressSurvivesEnrichmentTest` llevaba un `copy(province = null)` para tolerar el único
campo que se perdía. Ese agujero tolerado es exactamente la forma por la que entró el bug del país,
así que desaparece: ahora compara la dirección **entera**. Y `MIGRATION_5_6` tiene su propio testigo
dentro de `AppDatabaseV1BaselineTest`, que abre un fichero v1 y lo pasa por toda la cadena — no basta
con que no pete, porque el ticket entero trata de una columna que existía y que nadie escribía.

Suite completa verde con `--rerun-tasks`; `mockDebug` y `prodDebug` compilan. `shared/schemas/…/6.json`
lo genera Room y entra en el commit.
