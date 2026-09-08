# FREE-LIMITS-001 · Tres límites free, tres funciones puras, un paywall con motivo

**Estado:** ✅ Done — mergeado a master (2026-09-08). ⏳ User: revisar el copy del dashboard RC si
la tienda enseña comparativa propia (la in-app ya dice 7 días · 1 coche · 2 zonas).

Fase **F1 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)). Ejecuta la decisión de
producto del 07-09 que supersede a MONEY-001: free = historial **7 días** · **1 coche propio** ·
**2 zonas**.

## Problema

MONEY-001 dejó free con historial de 30 días y coches/zonas ilimitados. La revisión del 07-09
estrechó la oferta free — y hoy el código (y la COMPARATIVA del paywall, que promete "Vehicles and
zones are unlimited on both") dicen lo viejo. Un paywall cuya tabla contradice los límites reales
es peor que no tener límites.

## Doctrina violada

Ninguna previa; las reglas de MONEY-001 que este ticket DEBE conservar: gating **soft** (la acción
dispara el paywall, nunca se oculta el botón); **nada se borra** al perder Plus (lo creado se
conserva, se gatea lo NUEVO); cada límite vive en **UN** sitio de `domain/entitlement/`.

## Señales / datos disponibles

Verificado en master `ddea7808` al abrir:
- `HistoryEntitlementGate.FREE_HISTORY_WINDOW_DAYS = 30` — consumidores ya canalizados (pie del
  historial + stepper del detalle); su test usa edades literales 1/29/31/200 que asumen 30.
- Chokepoints únicos: `VehiclesIntent.AddVehicle` (FAB y pill mandan el MISMO intent, un solo
  `sendEffect`) · `HomeViewModel.confirmAddZone` (creación = `editingZoneId == null`;
  `toggleZoneAlert` siempre edita → nunca gateado por el límite).
- Los 3 efectos `NavigateToPaywall` son `data object` (Home, Vehicles, Settings) y `Routes.PAYWALL`
  no lleva argumentos.
- El paywall es PROPIO desde `3ec0088a` (la comparativa vive en `PaywallScreen`, no en la plantilla
  RC): `paywall_free_history` = "The last 30 days" y `paywall_unlimited_note` = "unlimited on both"
  — **ambos quedan falsos** con F1.
- A1 dejó `AnalyticsTracker` — el `reason` del paywall tiene sink desde el día uno.

## Diseño

- **Gates** en `domain/entitlement/`: `FREE_HISTORY_WINDOW_DAYS = 7` ·
  `VehicleEntitlementGate.canAddOwnVehicle(ownedCount, isPlus)` con `FREE_OWN_VEHICLE_LIMIT = 1`
  ("propio" hoy = todos; F8 refinará con `ownerUid`) ·
  `ZoneEntitlementGate.canCreateZone(zoneCount, isPlus)` con `FREE_ZONE_LIMIT = 2`.
  Editar/borrar/detectar con lo YA creado: siempre libre, aunque esté por encima del límite.
- **`PaywallReason`** (enum en `domain/entitlement/`): `HISTORY, VEHICLES, ZONES, STATS,
  ZONE_ALERTS, GENERAL` — GENERAL se añade sobre el spec para las entradas que no son un gate
  (fila Plus de Ajustes): un motivo inventado sería mentir en analytics.
- **Gating**: `VehiclesIntent.AddVehicle` → si `!canAddOwnVehicle` → `NavigateToPaywall(VEHICLES)`;
  `confirmAddZone` → si es creación y `!canCreateZone` → `NavigateToPaywall(ZONES)` sin persistir
  y sin tocar `isSavingZone`. Los 3 efectos pasan a `data class NavigateToPaywall(reason)`; los
  callers existentes estampan el suyo (historial → HISTORY, campana/toggle → ZONE_ALERTS, fila
  Plus → GENERAL).
- **Ruta**: `paywall?reason={name}` (default GENERAL, tolerante a nombres desconocidos).
  `PaywallViewModel` gana `reason` + `AnalyticsTracker?` y emite **`paywall_shown(reason)`** una
  vez en init — el embudo de conversión nace con los límites, no después.
- **Comparativa del paywall**: "The last 30 days" → "The last 7 days"; `paywall_unlimited_note`
  se BORRA limpio (×9 y su `Text`) y en su lugar la tabla gana dos filas pareadas por icono:
  coches (Free "Your car" / Plus "All your cars") y zonas (Free "2 zones" / Plus "Unlimited
  zones"). Marco sin tienda: bajo la línea honesta, una caption con el MOTIVO
  (`paywall_reason_*` ×5; GENERAL no añade línea).
- **Strings ×9**: 1 cambiada + 4 filas nuevas + 5 motivos; `paywall_unlimited_note` eliminada de
  los 9. (Los `*_limit_free_hint` del spec se DESCARTAN: el paywall con motivo ya dice eso mismo
  en el único momento en que se lee — dos strings muertas menos.)
- **Dev Catalog**: variantes nuevas del paywall (sin tienda + motivo) y paridad con Previews.
- **Tests**: gates ×3 casos (bajo/en el límite/plus) · VehiclesViewModel: add con 1 coche free →
  paywall, con Plus → registro, con 0 coches → registro · HomeViewModel: 3.ª zona free → paywall
  sin persistir; edición con 3 zonas → guarda · PaywallViewModel: emite `paywall_shown` con su
  reason · HistoryEntitlementGateTest re-basado en edades relativas a la constante.

## Criterio de éxito

Free con 1 coche que pulsa añadir ve el paywall (el botón nunca desaparece); la 3.ª zona no se
guarda y explica por qué; el historial free enseña 7 días y el pie cuenta el resto; la comparativa
del paywall dice EXACTAMENTE los límites que el código aplica; cada apertura del paywall queda
contada con su motivo. Suite + Konsist + paridad ×9 + assembles + klib iOS en verde.

## Consumidores auditados

- `FREE_HISTORY_WINDOW_DAYS` → gate + su test (edades literales) — no hay más citas en código ✔
- `paywall_unlimited_note` → un solo `Text` en `PaywallScreen` + 9 strings.xml → borrado limpio ✔
- `NavigateToPaywall` → 3 pantallas × (screen `is` match + App.kt lambda) → todos con reason ✔
- `SaveOrUpdateZoneUseCase` → `confirmAddZone` (gateado en creación) y `toggleZoneAlert` (edición,
  libre) — ningún otro caller ✔
- Galería/Previews del paywall → firma de `PaywallContent` con default para no romper; variantes
  nuevas en la misma tarea ✔
