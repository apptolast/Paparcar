# UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001 · cada color se gana su significado, y su sitio

**Estado:** ✅ **Done** — validado en device (Oppo, mock, 29-08) · rama
`refactor/UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001-hue-earns-meaning` · worktree `../Paparcar-color-v4`

> **Fase 1 ✅** roles de color, guardarraíl "un hex una historia", dedupe de los 5 pares y los 3
> bugs de criterio. (El arreglo AA del verde claro se intentó aquí y se revocó en device — abajo.)
> **Fase 2 ✅** los tres verdes separados, con su contenedor propio para el coche vigilado y un
> guardarraíl perceptual que impide que vuelvan a juntarse.
>
> Verificado: **1.767 tests verdes** · `:shared:compileDebugKotlinAndroid` ·
> `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin`.
>
> ✅ **Visto y corregido en device** (Oppo, mock, 29-08). El tema oscuro se aprobó tal cual. El claro
> se revocó — *«te has cargado el verde corporativo, has metido un verde oscuro camuflaje»*— y se
> rehízo en la misma sesión: el corporativo `#009F5E` vuelve intacto y la vigilancia sube de L\*=31
> a L\*=50. Veredicto tras el arreglo: **«mucho mejor»**.

## Paleta final (fase 2)

| Rol | Dark | Light / mapa | Historia |
|---|---|---|---|
| **BRAND** marca y acción | `#25F48C` *(intacto)* | `#009F5E` *(intacto)* | CTAs, links, nav, gráficas |
| **WATCH** coche vigilado | `#0FBF9A` | `#05876D` | glifo, badge, borde, marcador |
| **WATCH** contenedor | `#0B3A31` | `#AAF0D8` + `#00201A` | tarjeta de sesión viva |
| **FRESH** plaza fresca | `#8FE83C` | `#5FBF1F` (UI) · `#398701` (puck) | etiqueta, eyebrow, meter, puck |
| **RAMPA** enfriándose / caducando | `PapAmber` / `PapRed` | `#E08200` / `#E0322F` | la rampa estrena tokens propios: ya no toma prestados el ámbar y el rojo de ERROR del tema |
| **FRESH** contenedor | `#0F3B08` | — | cama del chip TTL |

Espectro: `FRESH` lima (h≈128) — **`BRAND` menta (h≈151)** — `WATCH` teal-esmeralda (h≈166).
Todos los pares: **ΔE00 ≥ 12.5 y ≥ 14° de tono en ambos temas**. Cada valor cumple el suelo de
contraste **del trabajo que hace**, medido contra su peor fondo real.

Hermano del refactor de tipografía [UI-TYPE-TWO-VOICES-ONE-ROW-001]. Allí la regla fue *el ROL posee
su peso*. Aquí es: **el color lo decide el TRABAJO que hace, no el widget que lo pinta — y dos
trabajos que coinciden en pantalla no pueden compartir hex.**

---

## Problema

El sistema de color se documentó bien (`docs/design/COLOR-SYSTEM.md`, v3) y la disciplina de call
sites es buena: **0 literales `Color(0x…)` en `presentation/`**, `PapAlpha` y `PapBorders` adoptados.
El desorden no está en cómo se consume la tabla. Está **en la tabla**.

### 1 · Tres historias distintas comparten un único hex

Medido con CIEDE2000 (`ΔE00 < 1` = indistinguibles para el ojo):

| Historia A | Historia B | ΔE00 | Dónde coinciden |
|---|---|---|---|
| Marca / CTA | Coche con detección activa | **0.00** | Cualquier pantalla con un botón y una ficha de coche |
| Marca / CTA | Plaza comunitaria fresca | **0.00** | El sheet de Home: CTA verde + chips de plaza |
| Coche vigilado (mapa) | Plaza fresca (mapa) | **0.00** | **El mapa, siempre, a la vez** |

La tercera fila es la peor y es literal: `PaparcarMapMarkers.kt:164` pinta el marcador del coche
activo con `PapGreenLight` = `#009F5E`, y `PaparcarMapMarkers.kt:484` pinta el puck de plaza libre
con `SpotPalette.Green` = `#009F5E`. **Mismo valor, misma capa, simultáneos siempre.**

La v3 declara este verde compartido «deliberado» porque *«la app trabajando para ti» y «tu coche
vigilado» son la misma promesa*. Eso defiende **marca + coche**. No defiende que la **plaza de otro
usuario** — que el propio doc llama rampa *exclusiva* — sea el mismo píxel que mi coche.

### 2 · `colorScheme.primary` hace NUEVE trabajos distintos

50 lecturas en 26 ficheros. Clasificadas una por una:

| # | Trabajo | Ejemplos |
|---|---|---|
| 1 | **Acción** — CTA, link, pastilla rellena | `PaparcarBottomActionBar:47`, `PapAlertDialog:310`, `PapFooterButton` |
| 2 | **Selección** — "esta opción es la elegida" | `VehicleTypeSelector:91/115/124`, `VehicleColorSelector:98`, `HomeSpotRows:117`, `OnboardingScreen:278` |
| 3 | **Foco** — el campo con el cursor | `PapTextField:85` |
| 4 | **Logro** — "este escalón está conseguido" | `PermissionTier:61`, `PermissionRow:105`, `DetectionTierStatusCard:98/114/116` |
| 5 | **Dato de marca** — cifras, gráfica, iconos de sección | `HistoryWeeklyChart:140/188/198/237`, `HistoryTimeline:74`, `PapIconTile:32`, `PapListItem:54` |
| 6 | **Identidad de coche asistido** | `VehicleIdentity.kt:53` → *es* `primary` |
| 7 | **Mapa** — punto de usuario, anillo de zona, controles | `PaparcarMapMarkers:792/1028`, `PaparcarMapView:1244`, `MapControlButtons:66` |
| 8 | **Espera** — spinner | `VehiclesScreen:182` |
| 9 | **Dato AUSENTE** — coche de color desconocido | `VehicleColorLabels.kt:56` |

El nº 9 es un bug de criterio: *"no sé de qué color es este coche"* se pinta hoy con el verde de la
marca. Un dato que falta no puede vestirse de identidad — va en neutro.

### 3 · El verde de marca del tema claro NO cumple WCAG AA como texto

`PapGreenLight` `#009F5E` sobre el scaffold `PapAzure` `#ECF0F9` mide **3.01:1**. El mínimo para
texto normal es 4.5:1. Ese token es el `primary` del tema claro: **todo texto verde, link, cifra de
gráfica e icono de sección del tema claro está por debajo de AA.** El doc §8 dice «tokens AA»
heredado de la v1 — medido, es falso para el `primary` claro.

Comparación que lo deja claro: el azul BT del tema claro `#0057CA` mide **5.72:1**. El coche
Bluetooth es accesible y la marca no.

⚠️ **Resuelto como deuda aceptada, no como arreglo.** Se movió a `#237A46` y el user lo revocó en
device: *«te has cargado el verde corporativo del logotipo»*. Tenía razón y es medible — el
sustituto está 12.5 puntos de L\* por debajo. El corporativo se queda intacto: como relleno y como
glifo cumple el suelo de objeto gráfico (3.0); como texto pequeño sobre claro no, exactamente como
ha ido shippeando durante meses. Si se arregla, será dándole al TEXTO su propia variante oscura.

### 4 · CINCO pares de tokens duplicados

| Hex | Nombres que lo comparten |
|---|---|
| `#009F5E` | `PapGreenLight`, `PapGreenOutlineLight`, `SpotPalette.Green` |
| `#5B9EFF` | `PapBlue`, `PapCarBlueDark` |
| `#0057CA` | `PapBlueLight`, `PapCarBlueLight` |
| `#25F48C` | `PapGreen`, `SpotPalette.LegacyGreen` |
| `#3D2A10` | `PapAmberMuted`, `PapOnAmberContainerLight` |

`COLOR-SYSTEM.md` §1 denunciaba exactamente este patrón en los azules como el desorden original. Se
renombró, no se eliminó.

⚠️ El quinto par **no lo encontró la auditoría a mano — lo encontró el guardarraíl nuevo en su primera
ejecución.** Mi barrido inicial sólo comparó los tokens que yo había pensado en listar, y
`PapOnAmberContainerLight` no estaba entre ellos. Es la mejor prueba de por qué la regla tenía que
dejar de ser prosa: un relleno de contenedor oscuro y un color de contenido claro coincidiendo en
`#3D2A10` es justo el tipo de colisión que el ojo no busca.

### 5 · Otros suelos de contraste por debajo del mínimo

| Token | Peor caso | Uso | Mínimo |
|---|---|---|---|
| `PapNeutralOutlineLight` `#7A8FA0` | 2.94:1 | outline (objeto gráfico) | 3.0 |
| `PapGreenOutline` `#226D49` | 2.24:1 | borde de acción secundaria | 3.0 |
| `PapRed` `#FF5252` | 4.39:1 sobre modales | texto de error | 4.5 |
| `PapCarBlueLight` vs `PapLiveMap` | ΔE00 **10.0** | coche BT y traza de viaje, ambos en el mapa | ~15 |

---

## Doctrina violada

- `COLOR-SYSTEM.md` §7.4 — *«Todo token nuevo en `Color.kt` exige una fila en §2/§3 con su historia
  única»*. Cuatro pares de tokens comparten valor sin historia propia, y tres historias comparten
  valor.
- `COLOR-SYSTEM.md` §2 — la rampa de frescura es **exclusiva** de las plazas. Hoy su cabeza es el
  mismo hex que la marca y que el coche vigilado.
- **CLAUDE.md → sistemas, no parches** — el invariante «un color, un significado» no vive en ningún
  sitio ejecutable: el guardarraíl actual prohíbe `tertiary` y literales, pero **no comprueba que dos
  historias no colapsen al mismo valor**. Por eso volvió a pasar.

---

## Señales / datos disponibles

Todo lo de arriba está medido, no estimado. Utillaje en el scratchpad de la sesión:
`colormetrics.py` (contraste WCAG 2.1 + CIEDE2000 + Lab), `audit_current.py` (auditoría de la tabla
actual), `final_palette.py` (búsqueda de candidatos bajo restricciones).

**Barrido completo de la app:** 180 ficheros de UI, **78 deciden color**. El vocabulario real:
`onSurface` (72) · `onSurfaceVariant` (59) · `primary` (50) · `vehicleIdentityColor` (36) ·
`primaryContainer` (14) · `stateColors` (9) · `papCarBlue` (7).

---

## Diseño

### D.1 · La regla, en una frase

> **El color lo decide el TRABAJO, no el widget. Dos trabajos pueden compartir un hex sólo si el doc
> declara que son la misma promesa — y nunca si coinciden en pantalla.**

### D.2 · Los tres verdes, y por qué la separación va en el TONO

La v1 se revocó porque la distinción más importante viajaba en **el alfa de un borde**: un eje de
énfasis, no de identidad. Separar dos verdes por luminosidad repite ese error con otro nombre — se
lee como *"el mismo color, más flojo"*, no como *"otra cosa"*. **La separación va en el tono**, con
un suelo de ΔE00 ≥ 12 y ≥ 14° de tono.

### D.3 · Los tres verdes NO coinciden los tres a la vez — y eso lo hace posible

Al buscar tres verdes mutuamente distintos, todos legibles como texto sobre el scaffold claro, el
solver devuelve un `WATCH` claro con **C\*=21**: un verde-gris apagado, que es exactamente el
«parecía apagado, muerto» por el que se revocó la v1. El tema claro no da para tres.

Pero **no hace falta que dé**, porque los tres nunca coinciden:

| Par | ¿Coinciden? | Dónde | Restricción real |
|---|---|---|---|
| marca ↔ coche | **sí, constantemente** | cualquier pantalla | ambos en UI temática, ΔE00 ≥ 12 |
| marca ↔ plaza | **sí** | el sheet de Home | ambos en UI temática, ΔE00 ≥ 12 |
| coche ↔ plaza | **sí** | **sólo el mapa** | ambos son valores **FIJOS** sobre teselas: sólo deben 3:1 contra su anillo blanco, no 4.5 sobre el scaffold |

Esa última fila es la que desatasca el problema: el marcador de coche y el puck de plaza no son texto
sobre nuestra superficie, así que tienen mucho más sitio para separarse.

### D.4 · Paleta candidata (medida — **a validar en device, no cerrada**)

| Rol | Dark | Light | Separación |
|---|---|---|---|
| **BRAND** · marca y acción | `#25F48C` *(congelado)* | `#237A46` | — |
| **WATCH** · coche vigilado | `#01A886` | *(pendiente de calibrar, ver D.3)* | ΔE00 21.9 vs marca (dark) |
| **FRESH** · plaza fresca | `#319101` | *(fijo de mapa)* | ΔE00 27.5 vs marca, 20.5 vs watch (dark) |

- **`#25F48C` no se toca.** Es la identidad que el usuario lee desde el día uno; moverla es lo que
  hundió la v2.
- **`#237A46`** es el verde claro *más parecido al actual* `#009F5E` que pasa AA (4.67:1 sobre
  scaffold, 5.32:1 sobre blanco; ΔE00 13.0 respecto al de hoy). No es una elección estética: el
  actual falla el mínimo legal de contraste y tiene que moverse igualmente.
- Los valores de `WATCH` y `FRESH` son candidatos con las restricciones cumplidas; **la última
  palabra es el device**, como en las tres versiones anteriores del sistema.

### D.5 · El color pasa a tener ROLES, como la tipografía

`primary` deja de ser el cajón de nueve trabajos. Cada trabajo obtiene **nombre propio**, aunque dos
compartan valor — porque un nombre permite que diverjan luego sin arqueología, y obliga a que el doc
declare por qué comparten:

| Rol | Valor | Historia |
|---|---|---|
| `action` | BRAND | CTA, link, pastilla rellena, botón de footer |
| `selected` | BRAND | la opción elegida de un selector |
| `focus` | BRAND | el campo que tiene el cursor |
| `progress` | BRAND | un escalón conseguido (permisos, tiers) |
| `brandData` | BRAND | cifras, gráfica, iconos de sección, overline |
| `watchAssisted` / `watchBluetooth` / `watchOff` | WATCH / azul BT / gris | identidad de vehículo — **sólo** vía `vehicleIdentityColor` |
| `spotFresh` / `spotCooling` / `spotExpiring` | FRESH / ámbar / rojo | rampa de frescura — **sólo** plazas comunitarias |
| `live` | `PapLiveMap` | movimiento sobre el mapa |
| `attention` | ámbar | algo te necesita: permiso pendiente, GPS pobre |
| `danger` | rojo | error, destructivo, TTL crítico |
| `unknown` | **neutro** | un dato que falta — **nunca** marca |

### D.6 · Guardarraíl nuevo — que no vuelva a pasar

`ColorGuardrailTest` gana un test que **falla si dos tokens con historias distintas colapsan al mismo
valor**, con una allowlist explícita de los pares que el doc declara «misma promesa». Es el análogo
de `TypographyGuardrailTest`: la doctrina deja de ser prosa y pasa a ser ejecutable.

---

## Criterio de color, COMPONENTE A COMPONENTE

Barrido de los 78 ficheros que deciden color. La pregunta de cada fila: *¿qué trabajo hace este
color aquí?*

### A · Chrome y superficies
`PapCollapsingTopBarScaffold` · `PapBottomActionBar` · `HomeBottomSheet` · `GlassSurface` ·
`PaparcarBottomActionScaffold` · `HomeSheetContent` · `HistoryFilterBar` · `PapCard`

**Criterio:** sólo rampa de superficie (`surfaceContainer*`). **Cero acento.** El chrome no compite
con el contenido. Un flotante sobre el mapa es `GlassSurface`, nunca un relleno de acento.

### B · Botones y acciones
`PapButton` · `PapFooterButton` · `PaparcarBottomActionBar` · `PapAlertDialog` ·
`ConfirmationBottomSheet` · `PapStepperButton` · `PaparcarAddChip` · `PapScrollToTopButton`

**Criterio:** `action` (BRAND). Destructivo → `danger`. **Prohibido `WATCH` y `FRESH`**: un botón
nunca lleva el color de un coche ni de una plaza. `PapRed` jamás en un CTA (ya en CLAUDE.md).

### C · Entrada y selección
`PapTextField` · `VehicleTypeSelector` · `VehicleColorSelector` · `VehicleSizeSelector` ·
`CarbodyManualPicker` · `SettingsSegmentedRow` · `HomeSpotRows:117` · `OnboardingScreen:278`

**Criterio:** `focus` para el cursor, `selected` para lo elegido. La selección se dice con **color +
borde + check**, nunca con peso tipográfico (regla de tipografía). ⛔ El segmento activo nunca con
`secondaryContainer` de M3 (= amarillo) [SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001].

### D · Listas, tarjetas y estructura
`PapListItem` · `PapOutlinedCard` · `PapIconTile` · `PapBadge` · `PapSettingRows` · `PapDivider` ·
`PapSectionHeader` · `PapShimmer` · `BluetoothConfigScreen`

**Criterio:** texto en `onSurface` / `onSurfaceVariant` con la escala `PapAlpha`; bordes en
`PapBorders`. El único acento permitido es `brandData` en el tile de icono y el overline. Una fila
de lista **no** se tiñe por su estado.

### E · Identidad de vehículo ← el corazón del ticket
`VehicleBadge` · `VehicleIdentityHeader` · `VehicleStatusIndicators` · `HomeParkingRow` ·
`VehiclePageContent` · `HomeMapFab` · `BrowsePeek` · `ParkingPeek` · `AddingParkingPeek` ·
`HistoryTimeline` · `HistoryComponents` · `HistoryContent` · `VehiclesScreen` ·
`ParkingHistoryDetailScreen`

**Criterio:** el color sale **exclusivamente** de `vehicleIdentityColor(watch)` — verde `WATCH` /
azul BT / gris. Lo lleva el **glifo de método** (icono, badge, punto, borde, marco), y el nombre
queda en `onSurface`; excepción, el eyebrow del peek, donde no hay glifo. **El ESTADO (aparcado / en
ruta / sin aparcar) se escribe en `onSurface` y se anima — nunca se tiñe.**

🐞 **Defecto encontrado:** `HistoryActiveCard.kt:23` — `PulsingDot(color: Color = PapGreen)`. El
punto pulsante de la sesión viva tiene por defecto el **verde de marca hardcodeado**, cuando el doc
§3 dice que es justo el elemento que lleva la identidad. Los dos call sites reales sí le pasan la
identidad; el default es una trampa esperando a un tercero. → el parámetro pasa a ser obligatorio.

### F · Plazas comunitarias
`HomeSpotRows` · `SpotPeek` · `PeekShared` · `ReliabilityMeter` · `SpotIndicators` ·
`SpotStateColors`

**Criterio:** **sólo** la rampa `spotFresh → spotCooling → spotExpiring`, exclusiva de la caducidad
de plazas. La procedencia (reporte manual, antigüedad) es un **glifo**, nunca un color. Una plaza no
usa jamás el color de un vehículo, ni al revés.

### G · Mapa
`PaparcarMapMarkers` · `PaparcarMapView` · `MapControlButtons` · `HomeMapFab`

**Criterio:** valores **FIJOS**, no temáticos — los marcadores van sobre imagen de calles, no sobre
nuestra superficie. `live` (`PapLiveMap`) sólo para movimiento: traza, punto de origen, pin en-route,
FAB sigue-coche. El punto de usuario es marca. El marcador de coche lleva su identidad de método.
Contraste que aplica aquí: **3:1 contra el anillo blanco del propio marcador**, no 4.5 sobre el
scaffold. Es la allowlist legítima de literales de color.

### H · Permisos, salud y avisos
`PermissionRow` · `PermissionTier` · `PermissionsContent` · `DetectionTierStatusCard` ·
`HomeDetectionSurface` · `HomeGpsAccuracyBanner` · `HomeLocationBlockedState` · `ConnectivityBanner`

**Criterio:** `progress` (verde) para lo conseguido · `attention` (ámbar) para lo pendiente ·
`danger` (rojo) para lo bloqueado. La barra/tile de la superficie de detección lleva el color del
**método del coche vigilado**, no el de marca.

🐞 **Defecto encontrado:** `HomeGpsAccuracyBanner.kt:80/87` pinta `tint`/`color` con `Color.White`
crudo sobre un relleno de `error`/`secondary`. Debe ser el `on…` del token de relleno, o el banner se
rompe si la paleta clara cambia.

### I · Ilustración — Nivel 3 de iconos
`VehicleCarGeometry` · `VehicleCarPaint` · `VehicleTopdownIcon` · `VehicleIconPainter` ·
`PaparcarLogo` · `VehicleColorSelector` (muestras)

**Criterio:** color propio multicolor, **NO se tintan**. Dicen **QUÉ coche es**, jamás su estado ni
su método. `Color.White`/`Color.Black` calculados por luminancia para el check de una muestra son
legítimos: son contraste computado, no tokens.

### J · Onboarding y pantallas explicativas
`OnboardingScreen` · `VehicleSizeExplainerScreen` · `GpsDisclaimerScreen` · `PermissionsContent` ·
`VehicleRegistrationScreen`

**Criterio:** `brandData` para iconografía y acentos; texto en `onSurface`/`onSurfaceVariant`. Aquí
la app se presenta: es verde de marca de principio a fin, **nunca** identidad de vehículo.

### K · Ajustes
`SettingsScreen` · `PapSwitchRow` · `PapNavRow`

**Criterio:** neutro + `action` en lo pulsable + `danger` en lo destructivo (borrar cuenta). El
selector de Tema es la **única** excepción documentada del sistema: pinta a propósito el color del
tema contrario porque ahí el color **es el dato**, un muestrario
[UI-THEME-OPTION-SHOWS-ITS-THEME-001].

---

## Criterio de éxito

1. `ColorGuardrailTest` falla si dos historias distintas comparten hex (allowlist explícita).
2. Ningún token de acento por debajo de su suelo: 4.5:1 texto · 3:1 objeto gráfico, medido sobre
   **su peor fondo real**, no sobre uno de conveniencia.
3. ΔE00 ≥ 12 entre cualesquiera dos verdes que puedan coincidir en pantalla; ≥ 14° de tono.
4. Cero duplicados de hex sin historia declarada (hoy: 4 pares).
5. `COLOR-SYSTEM.md` v4 con la identidad y el sitio de cada color, y una fila por rol.
6. `:shared:testDebugUnitTest` verde y `assembleMockDebug` + `assembleProdDebug` sin romper.
7. **Validación en device de los tres verdes**, en los dos temas, antes de cerrar — las tres
   versiones anteriores del sistema se decidieron ahí, y dos se revocaron ahí.

---

## Consumidores auditados

180 ficheros de UI · **78 deciden color** · clasificados en las 11 familias A–K de arriba.

| Hallazgo | Sitio | Estado |
|---|---|---|
| Coche activo y plaza fresca, mismo hex en el mapa | `PaparcarMapMarkers.kt:164` / `:484` | ✅ `PapWatchGreenLight` vs `PapSpotFreshLight` — ΔE00 24.7 |
| `vehicleIdentityColor(Assisted)` = `primary` | `VehicleIdentity.kt:53` | ✅ → `papWatchGreen` |
| `stateColors` HIGH = verde de marca | `SpotStateColors.kt:16-17`, `SpotIndicators.kt:79/85` | ✅ → `PapSpotFresh*` |
| `vehicleIdentityContainer(Assisted)` = `primaryContainer` | `VehicleIdentity.kt:86` | ✅ cama propia — encontrado al barrer, no estaba en la auditoría inicial |
| `MarkerColors.LegacyGreen` = `PapGreen` | `PaparcarMapMarkers.kt:109` | ✅ alias declarado (marcador sin call site en producción) |
| Nada impedía que dos verdes se re-acercasen sin ser idénticos | — | ✅ `the three greens stay perceptually apart in both themes` |
| `primary` claro falla AA como texto (3.01:1) | `Color.kt` | 🟡 medido y documentado; el cambio se REVOCÓ en device — la marca no se apaga |
| 5 pares de hex duplicados | `Color.kt` | ✅ `PapBlue`/`PapBlueLight`/`PapForest` borrados · `PapGreenOutlineLight` = alias declarado · `PapOnAmberContainerLight` → `#402400` |
| `PulsingDot` con `PapGreen` por defecto | `HistoryActiveCard.kt:23` | ✅ parámetro obligatorio |
| Dato ausente pintado de marca | `VehicleColorLabels.kt:56` | ✅ → `onSurfaceVariant` |
| `Color.White` crudo sobre relleno semántico | `HomeGpsAccuracyBanner.kt` | ✅ → `onError`/`onSecondary` |
| `primary` haciendo 9 trabajos sin nombre | 50 call sites | ✅ sistema de roles `ui/theme/PapColor.kt` |
| Nada ejecutaba "un token, una historia" | — | ✅ `ColorGuardrailTest.no two colour stories share a hex` |
| `colorScheme.tertiary` en feature code | — | ✅ limpio (sólo el comentario de `Color.kt:51`) |
| Literales `Color(0x…)` en `presentation/` | — | ✅ limpio (0) |
| `Color.White/Black` en marcadores e ilustración | `PaparcarMapMarkers`, `VehicleCarGeometry`, `VehicleColorSelector` | ✅ exento (fijos sobre teselas / contraste computado) |

### Lo que la fase 2 enseñó (y no estaba en el plan)

- **Maximizar la separación no es diseñar.** El optimizador sin atar llevó el verde de marca claro a
  `#034C1C`, casi negro. Hizo falta una correa de *reconocibilidad*: la marca sigue siendo la marca.
- **Cada rol necesita el suelo de contraste de SU trabajo, no uno genérico.** Exigirle a la plaza
  fresca 4.5:1 *como texto* —cuando nunca es texto, sino un relleno que lo lleva— estrangulaba el
  tema claro y devolvía un verde-gris de C\*=21: el mismo «apagado, muerto» que hundió la v1.
- **Un guardarraíl de igualdad exacta no protege de esto.** El bug medía ΔE00 = 0.00, pero un futuro
  ΔE00 de 2 sería igual de indistinguible y pasaría. Por eso el suelo nuevo es perceptual.
- **`MyVehicleMarker` está muerto en producción** — sólo lo alcanzan previews y el Dev Catalog.

### Fuera de alcance — follow-ups

- `PapCarBlueLight` vs `PapLiveMap`: ΔE00 **10.0**, ambos sobre el mapa (tag de coche BT vs traza y
  pin en-route). Anatomías distintas lo salvan hoy; merece su propio ticket.
- `PapNeutralOutlineLight` 2.94:1 y `PapGreenOutline` 2.24:1 — bordes por debajo de 3:1.
- `PapRed` 4.39:1 sobre modales.
- Rampas de superficie (`PapInk` / `PapAzure`): **no auditadas** en este ticket.
- **Etiqueta de fiabilidad como badge RELLENO** en vez de texto de color: es la única forma de
  tener el lima vivo Y 4.5:1 (relleno vivo + texto tinta = 8.07:1). Hoy la rampa clara va a
  2.34 / 2.85 / 4.50 : 1 como texto — deuda aceptada por el user en device, no despiste.
- `MyVehicleMarker` no tiene call site en producción (sólo previews + Dev Catalog): borrarlo, o
  cablearlo a `vehicleIdentityColor` si se quiere recuperar. Hoy es el único sitio donde un pin de
  vehículo lleva el verde de MARCA, y sólo se salva porque nadie lo ve.
- Migrar los 50 call sites de `colorScheme.primary` a los roles de `PapColor`. Los roles existen y
  están documentados, pero **el código sigue leyendo `primary` directamente**: hasta que se migren,
  el sistema de roles describe la intención sin forzarla, y no hay guardarraíl que lo exija.
