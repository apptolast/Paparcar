# PLUS-A-PLAN-MUST-NAME-WHAT-IT-JUST-GAINED-001 · El paywall vende «cualquier periodo» y detrás hay una ola entera

**Estado:** ✅ Done · mergeado en master (verificado en device, Redmi mock)
**Abierto:** 2026-09-16 · desde master `855cde2c` · rebasado sobre `c0b9c11a` antes de mergear

> ⚠️ **Va DELANTE de UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001, y el orden no es indiferente.** Esa
> rama rehace el paywall como una lista única y **borra** `paywall_plus_stats` y
> `paywall_plus_alerts`, las dos celdas que este ticket acaba de poner al día. Al borrarlas, git no
> tiene nada que enfrentar: si se mergease primero, estas decisiones de copy desaparecerían **sin
> un solo conflicto**. Mergeando este primero, la otra rama tiene que trasladarlas a sus keys
> nuevas a mano — que es lo que la obliga a leerlas.

## Problema

Once tickets después, Plus incluye: **un año** de gráfico con tres series, **tus sitios** con lo que
cuesta aparcar en cada uno, la **rejilla día × franja**, **conclusiones con su evidencia**, las
**proyecciones** del mes y del año, el **resumen del mes cerrado** y **su notificación**.

El paywall dice:

| Dónde | Hoy |
|---|---|
| Tagline | «Historial completo y avisos de plazas libres en tus zonas» |
| Fila de cifras | free «Todas, últimos 7 días» · Plus **«Cualquier periodo»** |
| Fila de avisos | Plus «En las zonas que elijas» |
| Motivo desde stats | «Con Plus ves tus cifras en el periodo que elijas» |

La única función de toda la app que **busca al usuario** —el resumen mensual— no se nombra en ningún
sitio, y la fila que debería venderla dice sólo «zonas».

⚠️ **Corrección sobre el diagnóstico inicial**: la tabla NO está vacía ni desordenada. Tiene nueve
filas y una doctrina de orden ya escrita en el código — *lo que puedes PREGUNTAR es gratis; lo que se
vende es que la app VIGILE y RECUERDE por ti*. Esa doctrina es exactamente el eje sobre el que se
construyó la ola. No hay que reestructurar nada: hay que poner al día tres celdas que se quedaron
viejas.

## Doctrina violada

- **Lo que se cobra hay que poder leerlo dentro de la app** [plus-alerts-and-tiers-001]: «todo lo
  que entre aquí toca la ficha de Play y el copy in-app». Once tickets entraron y el copy no se
  movió.
- **Copy sin jerga ni mecánica**: la fila nueva no puede decir «insights» ni «heatmap». Dice lo que
  el usuario obtiene.

## Diseño

**Tres celdas y un titular**, sin tocar la estructura:

1. `paywall_plus_stats`: «Cualquier periodo» → **«Todo tu historial, y lo que aprende de él»**.

   ⛔ **No «un año»**, que fue el primer intento y era falso por los dos lados: el gate es
   `FREE_HISTORY_WINDOW_DAYS = 7` y *«Plus ve TODO»* — no hay TTL ni poda en las sesiones, ni en
   Room ni en Firestore (las retenciones que existen son de cachés y diagnósticos). El único año de
   la app son los **12 meses de barras** del gráfico en «Todo» (`ALL_MONTHS_CAP`), que es un límite
   de DIBUJO, no de memoria: las cifras de ese mismo filtro se calculan sobre todo lo que hay.
   Vender un año sería vender menos de lo que se entrega, y encima mal.
2. `paywall_plus_alerts`: «En las zonas que elijas» → **«Plazas, precios y el resumen de cada mes»**
   — la fila del eje VIGILANCIA nombra por fin lo único que llama a la puerta sola.
3. `paywall_reason_stats`: pasa a nombrar lo que el usuario acaba de tocar, no el selector de periodo.
4. `paywall_tagline`: los dos ejes —vigilancia y memoria— con lo que hoy hay detrás.

**Y una sola señal premium**: la card del resumen del mes lleva el **cresta** —`PapPremiumMark`, el
logo de Paparcar fundido en el mismo metal— junto a su título. Es la única card de la app que habla
sola y la única que un free no puede tener nunca; firmarla dice «tu Plus está trabajando» **sin
teñir un solo dato**.

### ⛔ Primero se vistió la CARD entera, y se midió en device que no

El primer intento fue `PapPremiumSurface(Subdued)` sobre toda la card. Se instaló, se miró, y se
midió sobre el pixel:

| | Etiquetas de fila (`onSurfaceVariant`) | Cifras (`onSurface`) |
|---|---|---|
| Card normal (lecho `#1A2232`) | **5,9:1** | 8,6:1 |
| Card premium `Subdued` (lecho `#224E39`) | **3,5:1** | 8,1:1 |

Las etiquetas —«gasto aprox.», «buscando plaza»— caen por debajo del 4,5:1 en **la pantalla que
existe para leer cifras**. Y encima coincide con lo que el propio producto ya aprendió: la única
superficie `Subdued` que se acepta a lo ancho es **una FILA** (el pie del historial), y la versión
anterior de ese pie —una losa de tres bandas— se revocó en device por esto mismo; *área amplifica
croma* [UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001] [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001].

El metal, pues, va en la **marca** y no en el lecho: mismo `PapPremiumSurface` (el crest se construye
con él), tamaño de glifo, cero contraste perdido, ninguna señal premium nueva inventada.

## ⛔ Lo que NO se hace, y por qué

**Los indicadores ▲/▼ NO se colorean.** Verde-arriba/rojo-abajo sería la app juzgando, y en la mitad
de estas cifras el juicio es falso: menos kilómetros no es peor, más aparcamientos no es mejor,
buscar más tiempo no es culpa de nadie. Está escrito en `PapStatTile` desde STATS-UI-001 y en
`ACTIVITY-CARD-TITLE-001`, que ya tumbó una flecha por exactamente esto. Un segundo señalizador
premium además diluiría el que ya existe: `PapPremiumSurface` es LA superficie que dice Plus
[UI-COLOR-PLUS-WEARS-THE-BRAND-WITH-A-FINISH-001].

## ⛔ Y de paso: el botón que disparaba la notificación disparaba al vacío

El disparador del Dev Catalog que nació en `STATS-PLUS-THE-RECAP-KNOCKS-001` pedía
`AppNotificationManager` **por Koin**, y en el flavor mock eso está bindeado a
`FakeAppNotificationManager`, que no postea nada. Por eso la notificación del resumen seguía sin
verse en mano: el botón existía, se pulsaba, y no ocurría nada — `dumpsys notification` no tenía ni
un registro del paquete mock. Ahora construye el `AppNotificationManagerImpl` real.

*Un disparador de notificaciones que usa el fake no prueba nada*, y se descubrió sólo al ir a mirar.

## Criterio de éxito

- Las cuatro strings en los 9 locales · guardarraíles · `testAll`.
- La card del resumen con su firma premium, vista en device.
- ⏳ La **ficha de Play** con el mismo mensaje — queda fuera de esta rama porque vive sin commitear
  en `project_play_listing_copy_001`; se anota como pendiente del user.

## Verificación

- `./gradlew testAll` ✅ · guardarraíles con `--rerun-tasks` (Typography · Color · Divider · Import ·
  LocaleParity) ✅ · `:shared:compileKotlinIosSimulatorArm64` ✅ · `assembleMockDebug` ✅.
- **Redmi 2201117TY, mock, sha256 verificado en device** (`33d0907d…`):
  - Card del resumen: crest metálico + «El mes pasado», etiquetas de vuelta a 5,9:1, cifras en
    blanco. Capturada.
  - **Notificación del resumen, vista por primera vez en mano**: «El mes pasado» · «38.40 EUR y
    72 min buscando plaza», canal `upload_channel`, id 1012, `category=event`, abre la app al tocar.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `PaywallScreen` (tabla y motivos) | ✅ estructura intacta; sólo cambia el texto de tres celdas |
| `StatsMonthRecapCard` | 🔧 `PapOutlinedCard` + `PapPremiumMark` de 22 dp junto al título |
| `PapPremiumSurface` | ✅ sin cambios: el crest ya se construye con él |
| `DevCatalogScreen` (disparador) | 🔧 usaba el manager FAKE del grafo mock; ahora el real |
| Ficha de Play | ⏳ del user, fuera de la rama |
