# UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001 · El aire vertical de una página es una ley, y la escribe Ajustes

**Estado:** ✅ Done · en master · ⏳ queda verlo en un móvil real (solo hubo emulador)

## Problema

El user, con las tres pantallas delante (19-10):

> «basándome en la pantalla de settings, es realmente como me gustaría, pero en vehicles el
> espaciado vertical es más alto entre vehicle info card y compartir coche por ejemplo […] quiero
> que apliques una regla de espaciado estricta basándote en la de settings aplicada a las demás.
> […] mira el espacio entre el buscador y los chips de zona».

No es una preferencia estética suelta: **el mismo hueco —"dos bloques hermanos de una página"— vale
tres cosas distintas según quién lo escribió**, y ninguno de los tres lo sabe.

| hueco | Ajustes | Vehículos | Home (cabecera) |
|---|---|---|---|
| bloque ↔ bloque | `spacedBy(sm)` = **8** | `spacedBy(12.dp)` = **12** | — |
| card ↔ fila de chips | — | `spacedBy(8.dp)` + overhang = **15 visibles** | `CHIP_SHADOW_ROOM 8` + overhang = **15 visibles** |
| bloque → cabecera de sección | 8 + `md` = **20** | `SECTION_TITLE_TOP_GAP 24` + overhang = **31 visibles** | — |
| cabecera → su bloque | 2 + 8 = **10** | `SECTION_TITLE_BOTTOM_GAP 8` − … = **8** | — |
| card ↔ card dentro de una sección | 8 | `STATS_CARD_GAP` = **16** | — |
| margen lateral de página | `CONTENT_H_PADDING 16` | `PaparcarSpacing.lg` 16 | — |
| margen vertical de página | `CONTENT_V_PADDING` = **8** | `LIST_V_PADDING` = **8** | — |

Y el mismo rol, el margen de una página, está **declarado cinco veces** con dos valores:

| fichero | lateral | vertical | hueco de fila |
|---|---|---|---|
| `SettingsScreen` | `CONTENT_H_PADDING` 16 | `CONTENT_V_PADDING` **8** | `spacedBy(sm)` 8 |
| `HistoryContent` | `PaparcarSpacing.lg` 16 | `LIST_V_PADDING` **8** | — (Spacers a mano) |
| `BluetoothConfigScreen` | `SCREEN_H_PADDING` 16 | `LIST_V_PADDING` **12** | `spacedBy(sm)` 8 |
| `LicensesScreen` | `SCREEN_H_PADDING` 16 | `LIST_V_PADDING` **12** | `ROW_GAP` 8 |
| `LicenseDetailScreen` | `SCREEN_H_PADDING` 16 | `LIST_V_PADDING` **12** | `ROW_GAP` 8 |
| `StatsScreen` | `H_PAD_DP` 16 | `LIST_V_PAD_DP` **8** | `SECTION_GAP_DP` 12 |

Seis páginas, seis declaraciones privadas del mismo rol. Que cuatro coincidan en 16 es suerte, no
sistema: nada las ata, y los dos valores que sí difieren (8 vs 12) demuestran que la moneda al aire
ya cayó de los dos lados.

## Doctrina violada

- **Sistemas, no parches** — el invariante vive en UN sitio y se barren sus consumidores. Aquí el
  invariante ("cuánto aire separa dos bloques de una página") no vivía en ningún sitio.
- `UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001` — *"una escala solo sirve si sus escalones se ven"*,
  y su corolario: un rol sin dueño lo re-decide cada call site.
- `UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001` — **un hueco visible no es su constante**. Ese
  ticket midió la ley (`PapGap.betweenControls`) y la aplicó donde la midió; estas tres filas de
  chips quedaron fuera, y son justo las que el user ve "más separadas".

⛔ **No es un rediseño.** No se toca identidad, color, tipografía, estilo de card ni jerarquía. Lo
único que cambia es CUÁNTO aire hay entre dos bloques, y pasa a decirlo un solo sitio.

## Señales / datos disponibles

- Las tres capturas del device (19-10, 15:37) con las tres pantallas en el mismo estado.
- `PapControlHeight.overhang(height)` ya calcula el aire invisible del mínimo táctil, y
  `PapGap.betweenControls(visible, controlHeight, sides)` ya lo descuenta. Lo que faltaba no era la
  cuenta: era QUIÉN dice cuánto se tiene que ver.
- `SpacingGuardrailTest` (4 chequeos) y `TouchTargetGuardrailTest` (2), verdes al abrir.

## Diseño

### 1 · La ley, medida en Ajustes y escrita una vez: `PapPageRhythm`

Ajustes no es "la pantalla que gusta": es la única que ya tenía su ritmo declarado entero en un
sitio (`LazyColumn` + `spacedBy` + `contentPadding` + el padding de `SectionHeaderMuted`). Se lee de
ahí y se asciende a token en `ui/theme/Spacing.kt`:

| token | valor | qué separa |
|---|---|---|
| `PapPageRhythm.block` | 8 | dos bloques hermanos (card ↔ card, card ↔ fila de chips) |
| `PapPageRhythm.beforeSection` | 20 | el último bloque y el texto de una cabecera de sección |
| `PapPageRhythm.afterSection` | 10 | la cabecera y el primer bloque de su sección |
| `PapPageRhythm.sideInset` | 16 | el contenido y el borde de la pantalla |
| `PapPageRhythm.edgeInset` | 8 | el contenido y el principio/final de la lista |

Los cinco son **huecos VISIBLES**. Cuando uno de los lados es un control pulsable, el call site pide
el suyo por `PapGap.betweenControls(…, sides = 1)`, que descuenta el overhang del mínimo táctil —
la ley de #137, aplicada por fin a las filas que quedaron fuera.

### 2 · El barrido

Las seis páginas dejan de declarar su margen y piden el ritmo. Home entra por su cabecera (buscador
↔ chips), que es el caso que el user señaló.

### 3 · El guard

`PageRhythmGuardrailTest`, dos chequeos con testigo (`GuardrailScope`):

1. **Prohibición** — ningún fichero de `presentation/`+`ui/` escribe
   `verticalArrangement = Arrangement.spacedBy(<literal>.dp)`: el aire vertical entre hermanos es un
   rol, y un literal ahí es el rol re-decidido.
2. **Población positiva** — todo fichero que monta una lista de PÁGINA (un `LazyColumn` con
   `contentPadding`) nombra `PapPageRhythm`. Es el chequeo que caza la página NUEVA, que es como
   volvió a entrar el defecto las dos veces anteriores.

## Criterio de éxito

1. Los cinco huecos de la tabla tienen un solo dueño, y las seis páginas lo citan.
2. El hueco card↔chips de Vehículos y buscador↔chips de Home **se ven** como el card↔card de
   Ajustes, medido sobre captura de device, no deducido.
3. `PageRhythmGuardrailTest` verde y **falsificado** con `--rerun-tasks` (los dos chequeos).
4. `:shared:testDebugUnitTest :app:testMockDebugUnitTest` en verde.
5. Capturas antes/después de Vehículos y Home [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## 🔑 Hallazgo · El hueco que el user vio no estaba donde lo escribía nadie

Medido en device (emulador Pixel, 1080×2400, densidad 420 ⇒ 1 dp = 2,625 px), antes y después, con
el mismo escenario del Dev Catalog:

| hueco | antes | después | la ley |
|---|---|---|---|
| Ajustes · card ↔ card | 21 px = **8,0 dp** | 21 px = **8,0 dp** (sin tocar) | 8 |
| Vehículos · hero card ↔ card del grupo | 53 px = **20,2 dp** | 21 px = **8,0 dp** | 8 |
| Home · buscador ↔ chip de zona | **15 dp** | **8 dp** | 8 |
| Home · buscador ↔ invitación expandida | 26 px = **9,9 dp** | 26 px = **9,9 dp** | ~8 |

El `spacedBy(12.dp)` de la cabecera era solo la mitad del problema: **2,5 veces el hueco de Ajustes
no salen de 12 dp**. La otra mitad estaba escondida donde ningún contenedor podía verla —
`VehicleHeroCard` se ponía `padding(start = 16, end = 16, top = 8, bottom = 8)` **dentro del
componente**, o sea su propio margen con el mundo, que se suma al `spacedBy` del que lo ordena:
12 + 8 = 20,2 dp medidos. Un bloque no se separa a sí mismo.

Ese margen interno explica además el `if (header != null) 0.dp else LIST_V_PADDING` de
`HistoryContent`: el hueco con la cabecera de pantalla existía, pero lo ponía la card, así que la
lista tenía que anularse a sí misma para no duplicarlo. Con el margen fuera, el condicional sobra.

⚠️ **El overhang verificado en device, no deducido**: con el hueco pedido a 1 dp (8 − 7 de un chip
de 34), la invitación de zona —que mide 44 dp porque crece con su texto— quedó a **3 dp** del
buscador, exactamente `1 + (48−44)/2`. La fórmula de `PapGap.betweenControls` se comporta como dice.
Por eso esa fila pide el hueco ENTERO cuando lleva la invitación: su altura es emergente y con nueve
locales no hay número que suponer.

### El suelo: hay un hueco que NO puede valer 8, y es correcto

Verificado en el panel de cifras: entre la fila de chips de VEHÍCULO y la de PERIODO se miden
**19,4 dp** y no bajan de ~14 por mucho que se pida, porque el overhang entra por los **dos** lados
(7 + 7). Recortarlo exigiría tocar el mínimo táctil de 48 dp, que es accesibilidad y no se toca —
*"lo que se corrige es la CUENTA, no el área"*. Lo que sí se corrigió es que el call site lo
DECLARE: ese `Spacer` pide `sides = 2`. No cambia un píxel (los dos cálculos caen en el suelo de 0),
pero deja de mentir sobre qué tiene a cada lado, que es lo único que el siguiente lector se va a
creer.

## Consumidores auditados

| fichero | qué declaraba | resultado |
|---|---|---|
| `SettingsScreen` | `CONTENT_H_PADDING` 16 · `CONTENT_V_PADDING` 8 · `spacedBy(sm)` · padding de cabecera 12/2 | **la fuente de la ley** — ahora la cita. Ni un píxel se mueve: 20−8 y 10−8 dan los 12 y 2 que ya tenía |
| `VehiclePageContent` | `spacedBy(12.dp)` + `padding(16.dp)` del call site + el margen INTERNO de la hero card | cerrado: la cabecera ordena (lateral una vez, `block` entre hermanos) y la card ya no se separa sola |
| `HistoryContent` | `SECTION_TITLE_TOP_GAP` 24 · `BOTTOM` 8 · `STATS_CARD_GAP` 16 · `LIST_V_PADDING` 8 · el `0.dp` condicional | cerrado: 20/10/8/8, con el overhang descontado donde debajo hay chips |
| `SharedCarSection` | `spacedBy(8.dp)` entre una card y una fila de chips = **15 visibles** | cerrado con `betweenControls(sides = 1)` |
| `HomeHeaderSection` | `CHIP_SHADOW_ROOM_DP` 8 + overhang = **15 visibles** | cerrado; la invitación expandida pide el hueco entero (razón escrita) |
| `StatsScreen` | `H_PAD_DP` 16 · `LIST_V_PAD_DP` 8 · `SECTION_GAP_DP` 12 | cerrado: `block`, y `betweenControls` en los dos huecos que caen bajo chips |
| `BluetoothConfigScreen` | `SCREEN_H_PADDING` 16 · `LIST_V_PADDING` **12** | cerrado (el margen vertical baja a 8, el de Ajustes) |
| `LicensesScreen` · `LicenseDetailScreen` | `SCREEN_H_PADDING` 16 · `LIST_V_PADDING` **12** · `ROW_GAP` 8 | cerrados igual; el `spacedBy(2.dp)` interno de una fila se queda con su razón escrita |
| `VehicleRegistrationScreen` | `SCREEN_H_PADDING` 16 | cerrado por el token (no es una lista de bloques, pero el margen lateral es el mismo rol) |
| `HomeBottomSheet` | `contentPadding(top = 4, bottom = 16)` | **exento y nombrado en el guard**: es una hoja, no una página — su aire es `PapGap.block` |
| `PaywallScreen` | `SCREEN_H_PADDING_DP` **20** | exento: superficie de venta a pantalla completa, con un margen propio deliberado |
| `HomeFuelLane` · `HomeParkingsLane` · `NearbyLaneParts` · `PapSheet` · `SharedCarGroupSurfaces` | `verticalArrangement` con literal (4/4/6/8/8) | nombrados, **sin cambiar de valor**: son aire DENTRO de un bloque, no ritmo de página |

### Lo que NO es una página, barrido aparte y exento a propósito

El guard detecta páginas por su forma (`LazyColumn` + `contentPadding`), así que estas cinco no
entran por ahí — y tampoco deben. Se auditaron una a una (`grep verticalScroll`) y la razón se
escribe aquí para que el próximo barrido no "las arregle":

| fichero | por qué no es una página |
|---|---|
| `OnboardingScreen` · `FirstStepExplainerScreen` · `VehicleSizeExplainerScreen` | una COMPOSICIÓN centrada —ilustración, título, prosa, CTA—, no una columna de bloques hermanos. Sus márgenes son `xxl`/`xxxl` (24/32) **a propósito**: una pantalla de bienvenida respira más que una lista, y estrecharla a 16 sería el rediseño que este ticket no es |
| `PaywallScreen` | lo mismo, más su `SCREEN_H_PADDING_DP` 20 propio de superficie de venta |
| `ParkingHistoryDetailScreen` | es una HOJA sobre el mapa: su aire es el de un peek (`PapGap.block`), igual que `HomeBottomSheet` |

Con eso el barrido queda **completo**: no hay ninguna superficie con ritmo vertical sin dueño o sin
razón escrita.

## Estado

- ✅ `PapPageRhythm` (5 roles) en `ui/theme/Spacing.kt`, medido en Ajustes.
- ✅ Las seis páginas lo citan; `GuardrailScope.pageFiles()` las detecta por su forma.
- ✅ `PageRhythmGuardrailTest`, 2 chequeos, **falsificados los dos** con `--rerun-tasks`
  (`spacedBy(13.dp)` → nombra fichero y línea; página sin el token → la nombra).
- ✅ `:shared:testDebugUnitTest :app:testMockDebugUnitTest` verdes (3.911 tests).
- ⏳ Falta verlo en un móvil real: aquí sólo había emulador conectado.
- ✅ **Medido en A/B contra master, no deducido** (20-10, emulador Pixel_8 1080×2400 @420dpi).
  Ver «§ La prueba» abajo.
- 🔄 **Rebasado sobre master `785754c01`** (20-10). Único conflicto:
  la cifra de cabecera de `docs/backlog/README.md` —los dos tickets de marca cerrados entre medias
  la habían movido—, resuelta recontando el directorio (798 ficheros, 711 cerrados, 78 abiertos),
  que es justo lo que mide `BacklogStatusGuardrailTest`. Ni un fichero de `presentation/` solapaba.

## 🧪 La prueba · dos APK que sólo difieren en `shared/`

El 20-10 el user miró el emulador y dijo **«yo lo sigo viendo igual que antes»**. Tenía razón en
ese instante y por un motivo que no era el ticket: el emulador tenía instalado el APK de master,
porque se estaba montando justo esta comparación. La lección que vale guardar es la otra: **la
respuesta no fue explicarlo, fue medirlo**, y para eso hace falta tener los dos binarios a mano.

Receta, por si vuelve a hacer falta: compilar `assembleMockDebug` desde el worktree, guardar el APK
aparte, `git checkout master -- shared/`, recompilar, guardar el segundo, `git checkout HEAD --
shared/`. Los dos APK comparten `applicationId`, así que se alternan con `install -r` sin
desinstalar, y **se verifica el sha256 en device antes de creerse una captura** — los hashes del
día fueron `6d0ca428…` (rama) y `ece4c9a9…` (master).

| hueco | master | la rama |
|---|---|---|
| Vehículos · hero card ↔ «Share this car» | 53 px = **20,2 dp** | 21 px = **8,0 dp** |
| Ajustes · perfil ↔ Plus | 21 px = **8,0 dp** | 21 px = **8,0 dp** (fuente de la ley, intacta) |
| Home · buscador ↔ chip de zona | 40 px = **15,2 dp** | 22 px = **8,4 dp** |
| Home · buscador ↔ invitación expandida | 26 px = **9,9 dp** | 26 px = **9,9 dp** (pide el hueco entero) |

El chip de zona del mock no sale de un preset: lo siembra `FakeZoneRepository` cuando el escenario
tiene **«Aparcado en zona privada»** encendido. Sin ese switch, Home enseña la invitación y la fila
que el user señaló no se puede ni ver.

### Y la fórmula se falsificó, no se creyó

Que un número baje no prueba que lo haya bajado tu código. Se forzó `visible = 47.dp` en
`CHIP_TO_SEARCH_BAR_GAP`, se recompiló y la pantalla midió **47,2 dp**: el `contentPadding` de esa
fila es quien gobierna el hueco, y `PapGap.betweenControls` descuenta los 7 dp de overhang que dice
descontar. Una sonda JVM confirmó los valores (`block=8 → gap=1`, `47 → 40`). El experimento se
revirtió; no queda en la rama.

## ⏳ Cabo suelto · una medición de Home que no se ha reproducido

La **primera** captura de Home con el APK de la rama midió 15,2 dp — el valor viejo — y las tres
siguientes, con el mismo binario, dieron 8,4. No se ha conseguido reproducir en tres intentos
(arranque limpio, vuelta al catálogo, y la secuencia exacta de la primera vez).

Las dos explicaciones que quedan en pie, ninguna descartada:

1. un frame cazado a mitad de la animación de `AnimatedVisibility` de la fila de chips;
2. que al pasar `inviteToAddZone` de `true` a `false` con las zonas llegando tarde, la fila se quede
   con el `contentPadding` del primer frame — el `if` de `HeaderZoneChips` elige entre **dos**
   paddings, y esa es justo la forma de un hueco que depende de quién llegó antes.

Si vuelve a verse el chip de zona despegado del buscador, es la (2) y tiene arreglo: que el hueco no
dependa del estado, midiendo la altura real del chip trailing en vez de ramificar.
