# IDENTITY-ICONS-001 — Refactor de identidad visual (iconos + gráficos)

> Rama: `feature/IDENTITY-ICONS-001-identity-refactor` (desde `master @ 0480467`)
> Fecha: 2026-06-29
> Brief original: `C:\Users\rndev\Documents\Paparcar\2026\Icons claude design and bolt\` (prompt + assets)

## Objetivo
Dar identidad propia y coherente a la app con un sistema de iconos en **3 niveles**,
reutilizando el estilo ya creado (verde de marca, formas redondeadas, Fredoka).

## Sistema de iconos — LA REGLA (a documentar en `CLAUDE.md`, tarea 0)
- **Nivel 1 · Sistema → Material Symbols Rounded.** Plumbing UI: nav, ajustes, buscar,
  cerrar/atrás, editar, chevron, calendario, filtros, capas. Tint = `onSurfaceVariant`.
- **Nivel 2 · Iconos UI → Material Symbols Rounded con `tint`.** Incluye POI/categorías.
  No creamos glifos custom.
- **Nivel 3 · Ilustración/marcadores → SVG→VectorDrawable** (relleno de marca, multicolor,
  no tintar). Hero, onboarding, empty states, marcadores, vehículos, fiabilidad.
- Regla mental: *plumbing de UI → Material; concepto de Paparcar → vector propio.*

---

## ESTADO DE PARTIDA (qué hay ya en master)
- **Tarea A (marcadores) ✅ YA EN MASTER** vía MAP-ICONS-V2 (`437f3d6`, `79cc667`):
  iso tag del coche, pucks de plaza con P de Fredoka, driving puck, location dot/active,
  center pins, claro+oscuro. **No se rehace.** Esta rama parte de ahí.
- Drawables vehículo viven en `composeApp/src/commonMain/composeResources/drawable/`
  (`ic_car_*.xml` + variante `_dark`). Misma carpeta y convención `_dark` para lo nuevo.

## ASSETS NUEVOS A IMPORTAR (externos, convertir SVG→VectorDrawable)
Origen: `…\Icons claude design and bolt\illustrations\`
- `reliability/` → `meter-{alta,media,baja}.svg`, `shield-{high,low}.svg` (+ `dark/`).
  Colores: alta `#009F5E`, media `#E08200`, baja `#E0322F`.
- `illustrations/` → `automation.svg`, `location-alert.svg`, `empty-spots.svg` (+ `dark/`).
- `glyph-p/` → P de Fredoka (ya cubierta por MAP-ICONS-V2 en marcadores; reutilizar para lista/ficha).

---

## TAREAS (orden propuesto)

### T0 · Documentar la regla de 3 niveles en `CLAUDE.md`  ✅ HECHO (2026-06-29)
Añadida `### ⛔ Iconos — sistema de 3 niveles` en REGLAS DE CÓDIGO OBLIGATORIAS.

### T1 · Importar assets Nivel 3 (ilustraciones)  ✅ HECHO (2026-06-29)
**Decisión (desvío del plan original):** NO VectorDrawable. Las ilustraciones usan
`stroke-dasharray` (anillo radar en location-alert, recuadro punteado en empty-spots) y
VectorDrawable de Android NO soporta trazos discontinuos. Se dibujan como **painters de
Compose Canvas en commonMain** (dash vía `PathEffect`, dark por parámetro, iOS-ready).
- Nuevo: `ui/illustrations/PaparcarIllustrations.kt` →
  `AutomationIllustration`, `LocationAlertIllustration`, `EmptySpotsIllustration`
  (cada uno `@Composable (modifier, dark = isSystemInDarkTheme())`).
- Coche de marca factorizado en helper `miniCar()` (compartido por 2 ilustraciones).
- viewBox 140x120 escalado con aspecto + centrado vía helper `viewBox {}`.
- Build `:composeApp:compileMockDebugKotlinAndroid` verde. **Pendiente: validar render en
  device claro/oscuro** (los painters están sin usar todavía → se cablean en T5).
- Reliability (meter+shield) NO entra aquí: el meter es trivial (Row de barras) y va con su
  componente en T2.

### T2 · Componente único `ReliabilityMeter(level, pct)`  (Tarea D)  ✅ COMPONENTE HECHO (2026-06-29)
- Nuevo `ui/components/ReliabilityMeter.kt`: **Row de 5 segmentos** (decisión confirmada por
  usuario sobre VectorDrawable). Theme-aware reutilizando `SpotReliabilityUiState.stateColors()`
  (mismos tokens que badges/marcadores; cero hex hardcoded). Constantes en `private const`.
  - HIGH→5 verde, MEDIUM→3 ámbar, LOW→1 rojo, MANUAL→5 azul. `pct` (0..1) afina relleno; no aplica a MANUAL.
  - Track = `onSurface.copy(alpha=0.15f)`. `contentDescription` opcional (a11y).
- Preview en `androidMain/.../ui/components/IdentityIconsPreviews.kt` (meter 4 niveles +
  pct=0.6 + las 3 ilustraciones de T1, claro/oscuro). Build android verde.
- **Modelo:** `ParkingConfidence` (sealed NotYet/Low/Medium/High) y `Spot.confidence:Float`.
  Hay DOS enums casi idénticos `SpotReliabilityLevel` y `SpotReliabilityUiState` (smell preexistente).
- **PENDIENTE (parte de Tarea D, se hace junto a T3):** reemplazar las 3 representaciones actuales
  por `ReliabilityMeter` en sus call sites (texto suelto / barra / escudo %). Sitios candidatos:
  `ParkingSpotItem.kt`, `HomeSpotRows.kt`, `HomePeekHandle.kt`, ficha vehículo
  (`HistoryInsightsCard.kt`/`VehiclePageContent.kt`). Ficha usa `ParkingReliabilityLevel`
  (Confirmed/High/Auto) → mapear a meter al cablear.

### T3 · Coherencia de la "P" y color de estado en lista/ficha  (Tarea B) + collapse enums  ✅ PASE 1 (2026-06-29)
**Hallazgo:** la lista REAL del sheet (`HomeSpotRow`) YA usaba glyph-P + `stateColors()` (verde/
ámbar/rojo/azul), ya coherente con el mapa. La "lista de círculos planos viejos" era
`ParkingSpotItem` — **código muerto** (cero usos en prod).
- **Collapse enums ✅:** borrado `SpotReliabilityLevel.kt` (solo lo usaba una sobrecarga en
  `SpotStateColors.kt`); queda `SpotReliabilityUiState` como único. Quitada la sobrecarga+import.
- **Dead code ✅:** borrados `ParkingSpotItem.kt` + `SpotUiState.kt` + `ParkingSpotItemPreview.kt`
  (trío huérfano de la lista vieja). Strings `spot_item_type_*` quedan huérfanos (inertes, no borrados).
- **Ficha ✅ (cablear meter):** `VehiclePageContent.kt` — el stat de fiabilidad (escudo `GppGood`
  + `"85%"`) ahora muestra `ReliabilityMeter` debajo del %, vía slot opcional `belowValue` en
  `StatMiniCard` (no afecta a las otras 2 cards). Mapeo `pct→nivel` (75/55) + `pct/100` para relleno.
  **DECISIÓN ABIERTA:** es *aditivo* (% + meter); D pedía *reemplazar*. ¿Quitar el "%"?
- Build android verde.
- **PENDIENTE (visual, a revisar contigo):** ¿`HomeSpotRow` cambia su etiqueta UPPERCASE por el
  meter? ¿Misma cura en `HistoryInsightsCard.kt` (otro `"$it%"`)? ¿`HomePeekHandle` fill-bar?

### T4 · POI / categorías sin emoji  (Tarea C)  ✅ NÚCLEO (2026-06-29)
- **Emoji eliminado:** `PlaceCategory` ya no lleva `emoji: String` (enum plano). Quitado de los
  2 builders de texto: `AddressAndPlace.displayLine` y `locationDisplayText` (quitado tb param
  `showEmoji`) → ahora devuelven solo el nombre del POI.
- **Mapeo Rounded:** la extensión `PlaceCategory.icon` (ya existía en `PaparcarIcons.kt`) pasó de
  `Icons.Filled.*` → `Icons.Rounded.*` (13 categorías). Vive en capa UI (domain sigue puro).
- **Render:** el peek (`HomePeekHandle`) ya pintaba el icono en slot. Añadido el icono Rounded en
  slot a la lista principal `HomeSpotRow` (antes del nombre, solo si hay `placeInfo`).
- Build android verde.
- **PENDIENTE (text-only, sin icono aún):** `HistoryTimeline` (historial) y `ParkingLocationScreen`
  (detalle, ya tiene icono de vehículo hero). ¿Añadir icono de categoría inline también ahí?
- **Limpieza menor:** revisar emoji hardcoded en previews (`HomeSheetPreviews.kt`).

### T5 · Onboarding / permisos con ilustración de marca  (Tarea E)  ✅ 2/3 (2026-06-29)
- **Automatiza ✅:** `PermissionsRationaleScreen` (su título ES literalmente "Automate your parking")
  — hero `Icons.Outlined.Lock` → `AutomationIllustration` (132×113).
- **Empty state ✅:** `HomeEmptySpots` — `Icons.Outlined.LocationOn` → `EmptySpotsIllustration` (120×103).
- **LocationAlert ⚠️ SIN CABLEAR:** no existe pantalla dedicada "Activa la ubicación" (es una fila
  dentro de `PermissionsContent`, cuyo hero de marca `ic_shield_3d` NO se toca). `LocationAlertIllustration`
  queda definida sin sitio. **Decisión pendiente:** ¿crear hard-gate de ubicación o descartarla?
- Build android verde.

### T6 · Color de acción de sistema  (Tarea F)  ✅ AUDITADO (2026-06-29)
- **Resultado: el código YA cumple.** Auditados todos los usos de `error`/`PapRed`: solo aparecen
  en gate de permisos (`HomeLocationBlockedState`), destructivo (`SectionHeaderDanger`,
  delete-vehicle, `PapAlertDialog.Destructive`), error de formulario (`PapTextField`), estado
  baja/caduca (`PapBadge` LOW, `SpotIndicators` TTL) y banners de alerta (GPS/conectividad/detección).
  Ningún CTA normal usa rojo; el primario es verde (`Theme.primary = PapGreen`). Sin cambios de código.
- **Regla documentada** en CLAUDE.md (`### ⛔ Color de acción`).

### T7 · Layout P0 — nombres de vehículo  (Tarea G)  ✅ (2026-06-29)
- **Causa:** en la hero card de la ficha (`VehiclePageContent`) el nombre (`maxLines=1`) compartía
  fila horizontal con la columna derecha (editar + pill ancha) → se estrujaba a "S…"/"Ford F…".
- **Fix:** reestructurada la card → glyph + columna `weight(1f)` con **nombre en su línea (maxLines=2)**
  y debajo una fila **tipo + pill de estado**; editar al extremo derecho. El nombre ya nunca se corta
  a 1–2 letras. `VehicleTabPill` (selector) NO truncaba (la píldora crece con el nombre) → sin tocar.
- Build android verde.

---

## Ajustes post-validación en device (2026-06-29)
Tras lanzar la app en emulador y revisar con el usuario:
- **#1 Peek de plaza ✅** — `FiabilityIndicator` (HomePeekHandle) usaba siempre verde (mostraba
  verde en plazas MANUAL). Ahora usa el `ReliabilityMeter` por estado (nuevo param `fillWidth`
  para barras a lo ancho). Validado en device: plaza MANUAL → meter **azul**. Borrado código muerto
  (`SpotPeekPalette.fillRatio`, `FILL_*`, consts de segmentos).
- **#2 Ficha → REVERTIDO ✅** — el medidor rompía con el resto de métricas numéricas; el stat de
  fiabilidad vuelve a ser solo "82%". Revertido `belowValue`/slot/helper en `VehiclePageContent`.
  (El layout de la hero card T7 SE MANTIENE.) Validado en device.
- **#3** — era lo mismo (mantener "%" en métricas); resuelto por #2. Sin acción.
- **#4 LocationAlert ✅ (código)** — `HomeLocationBlockedState` (la pantalla "Turn on location")
  ahora usa `LocationAlertIllustration` + **grabber** superior (se lee como modal). NO validado en
  device: el build `mock` usa ubicación fake → no entra en `BlockedCore`. Compila.

## Reglas del repo aplicables
- Strings nuevos → `composeResources/values/strings.xml` en **todos** los locales (en, es, it,
  pt, fr, de, nl, pl, ro). Keys en inglés `feature_component_description`.
- Magic numbers → `companion object` privado (umbrales, tamaños de medidor, etc.).
- Ilustraciones Nivel 3 **no se tintan** (multicolor); tema vía carpeta/variante `_dark`.

## Validación
- `./gradlew :composeApp:assembleMockDebug` verde.
- Tests de cualquier UseCase tocado (no se prevén nuevos; es UI).
- Validar en **device claro + oscuro** (los assets traen variante dark).
- iOS no compila en Windows → marcar pendiente de smoke iOS.

## Pendientes que el diseñador puede exportar (sección 4 del brief)
- `location-active` por cada uno de los 10 tipos de vehículo.
- (Variante dark de ilustraciones ya entregada en `illustrations/dark/`.)
- Set de logros/rachas (gamificación) — fuera de alcance de esta rama.

## Decisión abierta a confirmar con el usuario
- ¿Esta rama hace **solo T0–T7** o se trocea (p. ej. T1–T4 ahora, T5–T7 en otra)?
- ¿`ReliabilityMeter` como `Row` de barras (recomendado) o como VectorDrawable `meter-*`?
