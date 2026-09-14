# MAP-A-STYLE-THAT-NEVER-RAN-001 · El mapa nunca aplicó su estilo, porque su tipo no era estilizable

**Estado:** ✅ Done · rama `bugfix/MAP-A-STYLE-THAT-NEVER-RAN-001-normal-map-type`

## Problema

Dos quejas del user que parecían dos tareas:

1. **El mapa claro no contrasta con la modal.** Es una llanura casi blanca y la hoja del peek flota
   encima sin que se vea dónde acaba una y empieza la otra.
2. **Salen los POI de Google** — píldoras moradas de museos, rombos de metro, nombres de parques en
   verde— compitiendo con nuestros propios marcadores de zona y las cápsulas de gasolinera.

Son **el mismo bug**: `MapStyles.kt` pide las dos cosas (un tono propio y `labels.icon: off` +
`transit: off`) y **nunca se ha ejecutado ni una sola de sus reglas**, en ninguno de los dos temas,
desde que existe.

## Causa (medida, no deducida)

`PaparcarMapConfig`, `HomeState` y `ParkingHistoryDetailScreen` arrancan en **`MapType.TERRAIN`**. Y
la documentación de Google sobre JSON styling dice, literalmente:

> *Styling works only on the `normal` map type.*

O sea que el `mapStyleOptions` que `PaparcarMapView.kt:1787` entrega viaja entero hasta
`GoogleMap.setMapStyle()` y **el SDK lo tira**, porque el tipo de mapa no es estilizable. No hay
nada roto en el camino: está bien construido, bien pasado y bien aplicado, sobre un tipo de mapa que
no lo acepta.

### Probado en device (Oppo CPH2371, 14-09-2026)

APK de sondeo con **el único cambio de `TERRAIN` a `NORMAL`**, muestreando píxeles de las capturas:

| Qué | Antes (TERRAIN) | Después (NORMAL) | Lo que pide `MapStyles.kt` |
|---|---|---|---|
| Suelo, tema oscuro | `(28,42,64)` azul | **`#212121`** | `#212121` ✔ exacto |
| Vía, tema oscuro | — | **`#2c2c2c`** | `#2c2c2c` ✔ exacto |
| Suelo, tema claro | — | **`#f5f5f5`** | `#f5f5f5` ✔ exacto |
| POI / transporte | píldoras moradas, metro, parques verdes | **ninguno** | `labels.icon: off`, `transit: off` ✔ |

Tres coincidencias exactas de hex y la desaparición completa de los POI. No hay ambigüedad.

### ⛔ La causa que estaba escrita en el backlog era la equivocada

`MAP-STYLE-IS-SILENTLY-IGNORED-001` concluía que **`mapColorScheme` anulaba el JSON** y de ahí
sacaba tres salidas, dos de las cuales exigían tocar el fork de kmp-maps o montar un Map ID de Cloud
Styling. Es falso, y verificado en las fuentes:

- El fork (`v0.9.1-puck5`) pasa las dos cosas — `Map.kt:150` el scheme, `Extensions.kt:154` el JSON.
- `maps-compose` 6.12.2 las aplica **las dos, una detrás de otra**, sin pisarse
  (`MapUpdater.kt:136` `map.setMapStyle(it)` · `MapUpdater.kt:140-143` `map.mapColorScheme = it`).

Conviven perfectamente. El experimento que "demostró" lo contrario se hizo en el emulador cambiando
el JSON **sin tocar nunca el tipo de mapa**, así que midió el efecto de TERRAIN y se lo atribuyó al
scheme. Lección: cuando un experimento deja fija la variable que manda, confirma cualquier hipótesis
que le pongas delante.

## Doctrina violada

- **`MapStyles.kt` decía una cosa y la pantalla hacía otra.** Un estilo que no se aplica hace creer
  que el mapa está bajo control cuando no lo está — el propio ticket anterior ya lo señalaba.
- **[UI-COLOR-DOCTRINE-001]** El mapa es una superficie de la app y no se vestía con nada nuestro:
  el tono venía de Google, y los POI ajenos usaban el MISMO lenguaje de píldora-con-icono que
  nuestros marcadores de zona y nuestras cápsulas de gasolinera. Tres lenguajes de píldora en un
  mapa, dos nuestros.
- **Tono claro:** suelo `#f5f5f5` contra la hoja del peek `PapAzure` `#ECF0F9` = **1,05 : 1**,
  medido sobre la captura real. Lo único que separaba mapa y modal era la sombra de elevación.

## Diseño

### 1 · El tipo de mapa por defecto pasa a `NORMAL`

Un solo sitio conceptual, cuatro literales: `PaparcarMapConfig`, `HomeState`,
`ParkingHistoryDetailScreen` y el `else` de `String.toMapType()`.

**Qué se pierde:** el sombreado de relieve de TERRAIN. En ciudad, a zoom de calle, nada — y el KDoc
del propio toggle ya describía TERRAIN como *«a flat street plan at city zoom: no relief, no
buildings»*, que es exactamente lo que es NORMAL. Defendía TERRAIN con la definición de NORMAL.

**La preferencia persistida:** `"TERRAIN"` pasa a ser alias legacy → `NORMAL`, con el mismo patrón
que ya existía para `"SATELLITE"` → `HYBRID`. Nadie se queda anclado a un tipo que el toggle ya no
sabe producir, y las escrituras nuevas guardan `"NORMAL"`.

### 2 · `LIGHT_MAP_STYLE` se repinta

El suelo baja de `#f5f5f5` a **`#cdd8ea`**. Las **calles se quedan en blanco**: al oscurecer el
suelo, la red de calles pasa a ser lo luminoso del mapa, y las autovías se quedan entre medias para
que la red se lea como un solo material.

| | Antes | Ahora |
|---|---|---|
| Suelo | `#f5f5f5` | **`#cdd8ea`** |
| Calles | `#ffffff` | `#ffffff` |
| Autovía | `#dadada` | `#e7eef8` |
| Agua | `#c9d8e8` | `#9ac9ee` |
| POI / parques | `#eeeeee` / `#e5e5e5` | `#c5d1e5` / `#b6ddb0` |

El tema oscuro **no se toca**: ya estaba bien resuelto, solo que nunca se había visto.

#### Agua y parques son LUGARES, no superficie

La primera pasada los apagó junto con el resto —agua a croma 16, parques a croma 5,5, o sea un
verde que no era verde— y el user lo paró: *«se ven muy apagados, como estaban antes quedaba
mejor»*. Tenía razón, y la razón es de fondo: el suelo, las calles y los POI genéricos son
SUPERFICIE y deben callarse; el agua y un parque son **sitios**, y apagarlos es borrar información.

Suben a `#9ac9ee` (croma 24) y `#b6ddb0` (croma 28). No hay riesgo de que se lean como nuestros:
todos los acentos de Paparcar son oscuros y saturados (`PapCarBlueLight` #0057CA, `PapLiveMap`
#2F6BFF, `PapGreenLight` #009F5E) y estos son rellenos pálidos — el par más cercano mide ΔE00 23.
Un marcador destaca sobre un parque por ser una píldora pequeña y oscura sobre un campo pálido y
grande, no porque el campo fuera gris.

#### ⛔ El vecino del mapa no es el blanco — y por poco lo damos por bueno

La primera ronda eligió `#e2e9f3` sobre una tabla de contrastes **calculada contra `#FFFFFF`**. El
vecino real del mapa es la hoja del peek y la barra de navegación, que son `PapAzure` **`#ECF0F9`**
(`surfaceContainer`, un token con nombre — no un blanco). Medido contra quien de verdad toca:

| Suelo | Contraste vs `#ECF0F9` | ΔE00 | Dirección |
|---|---|---|---|
| `#f5f5f5` (el de siempre) | 1,05:1 | **4,5** | mapa más CLARO que la hoja |
| `#e9eef6` | 1,02:1 | 0,9 | más oscuro |
| `#e2e9f3` (1.ª elección) | 1,07:1 | **2,1** | más oscuro |
| `#d8e0ee` | 1,16:1 | 4,3 | más oscuro |
| **`#cdd8ea`** (elegido) | **1,26:1** | **6,8** | más oscuro |

Y el hallazgo que lo explica: **el gris viejo contrastaba mal en luminancia pero bien en tono**
—gris neutro contra hoja azulada, ΔE00 4,5—. Teñir el mapa de azul para casarlo con la paleta lo
mete **dentro de la familia de tono de la hoja** y gasta justo esa diferencia: `#e2e9f3` acabó
perceptualmente MÁS CERCA de la hoja (ΔE00 2,1) que el gris al que sustituía, pese a ser más oscuro.
Conservar el tinte obliga a pagar la separación en luminancia, que es lo que hace `#cdd8ea`.

Lección, y el motivo de que `MapStylesTest` mida contra `#ECF0F9` y no contra blanco: **un contraste
contra el vecino equivocado es un número verdadero sobre una pregunta falsa.** Se vio porque se
instaló y se volvió a muestrear el píxel; sobre el papel, `1,22:1` parecía un arreglo.

### 3 · Los POI: iconos fuera, nombres dentro

Decisión del user. No es una regla nueva — es **exactamente lo que `MapStyles.kt` ya pedía** y nunca
llegó a correr: `labels.icon: off` mata todos los iconos, `transit: off` mata el transporte, y
`poi.labels.text.fill` **colorea** el texto, o sea que lo conserva a propósito.

El icono era el problema (píldora-con-icono, el lenguaje de nuestros marcadores). El nombre no
compite con nada y te orienta. Verificado en Cádiz: cero iconos, cero transporte, y se quedan
CENTRO, EL PÓPULO, Jardines Clara Campoamor.

## Criterio de éxito

- En device, tema claro: el suelo del mapa muestrea `#cdd8ea` y la hoja del peek se recorta contra él.
- En device, ambos temas: ni un icono de POI, ni transporte, ni píldora morada.
- `HomeViewModelTest` cubre que una preferencia `"TERRAIN"` guardada por una versión anterior
  resuelve a `NORMAL`, y que el toggle persiste `"NORMAL"`.
- Suite verde + `assembleMockDebug` + `compileProdDebugKotlin`.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `PaparcarMapConfig.mapType` (`PaparcarMapView.kt:133`) | → `NORMAL` |
| `HomeState.mapType` (`HomeState.kt`) | → `NORMAL` |
| `ParkingHistoryDetailScreen.kt:167` | → `NORMAL` |
| `HomeViewModel.toMapType()` / `toPreferenceString()` | `"TERRAIN"` pasa a alias legacy; se persiste `"NORMAL"` |
| `AndroidDataStoreAppPreferences.DEFAULT_MAP_TYPE` | → `"NORMAL"` |
| `IosAppPreferences.DEFAULT_MAP_TYPE` | → `"NORMAL"` |
| `FakeAppPreferences` / `FakeOtherDataSources` | → `"NORMAL"` |
| `HomeViewModelTest` (312, 1067-1079) | actualizado + caso nuevo de migración legacy |
| `MapTypeToggle` KDoc | reescrito: describía TERRAIN con la definición de NORMAL |
| `AppPreferences.kt:153` KDoc | vocabulario persistido actualizado |
| `MapType.HYBRID` (la otra mitad del toggle) | **exento**: es imagen aérea, el JSON no le aplica ni le hace falta |
| `DARK_MAP_STYLE` | sin cambios **aquí** — y al verlo por primera vez resultó no gustar: lo repinta `MAP-THE-DARK-MAP-KEEPS-ITS-NIGHT-BLUE-001` |
| Edificios (`isBuildingEnabled`) | **verificado, no hay que tocar nada** — ver abajo |

## Verificado después del merge: los edificios no salen

Quedó abierto si `NORMAL` traería huellas de edificio, porque enciende una capa que en TERRAIN no se
dibujaba (`isBuildingEnabled` viene a `true` en kmp-maps y nadie lo pone a `false`). Medido en el
Oppo sobre la **Plaza de la Catedral de Cádiz** —casco antiguo, manzanas pegadas: el peor caso que
se me ocurrió— al **zoom máximo** (el tope es 20):

| | Suelo | Calles | Resto |
|---|---|---|---|
| Claro | `#cdd8ea` **91,08 %** | `#ffffff` 2,94 % | nada por encima del 0,3 % |
| Oscuro | `#1c2a40` **91,47 %** | `#31435e` 3,07 % | nada por encima del 0,3 % |

Ni un polígono. Los residuos por debajo del 0,3 % son antialiasing de etiquetas: unas huellas de
edificio en un casco antiguo a ese zoom ocuparían decenas de puntos porcentuales, no décimas.

Dos cosas empujan en la misma dirección, aunque lo afirmado aquí es lo medido y no el mecanismo: la
regla raíz `{"elementType":"geometry"}` pinta también `landscape.man_made` —donde caen las huellas—
del color del suelo; y la extrusión 3D necesita cámara inclinada, que está desactivada en toda la
app a propósito (`rotateEnabled = false`, `togglePitchEnabled = false`) para que el mapa sea siempre
cenital y el puck no se despegue de su coordenada. **No hay que apagar `isBuildingEnabled`.**

## Tickets que esto cierra o mueve

- **`MAP-STYLE-IS-SILENTLY-IGNORED-001`** → se cierra aquí. Su síntoma era real, su causa no, y sus
  tres salidas (incluida la recomendada, el Map ID de Cloud) resolvían un problema que no existía.
- **`home-flow-analysis.md` §2** tenía la respuesta escrita desde el 30-08 —*«el default sigue siendo
  `MapType.TERRAIN`, así que el estilo de marca y el dark automático siguen sin aplicar del todo»*—
  y decía *«si algún día molesta, se abre ticket nuevo»*. Molestó. Este es el ticket.
- **`MAP-NEARBY-MARKERS-GREEN-CONTRASTS-ON-LIGHT-MAP-001`**, pregunta abierta 1 (el verde oscuro
  sobre satélite) — **fuera de alcance**, pero ahora se puede responder mirando baldosas de verdad.
