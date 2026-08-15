# ROUTE-FIX-ACCURACY-001 · Los fixes de mala precisión ensucian la ruta dibujada

**Estado:** ✅ Done · master `214850cf` (ff-only tras rebase sobre `00450b83`, 15-08-2026) · rama y worktree borrados

## Problema
Field 14-08 (El Puerto): las rutas guardadas hacen bucles y zigzags fuera de vía en tramo urbano
(Oppo 19:40 pin `7237ca50` — lazos en C. Federico Rubio; Redmi 19:38 pin `3a54bdbf` — muñones y
dobles vueltas cerca de Palacios). La sesión del Redmi `1786731078455` muestra la causa en crudo:
fixes con accuracy **98, 115, 129, 150 y 157 m** intercalados con fixes de 5–15 m, más wander
estacionario a v=0. `drivingRouteStore.append` (tap del stream en `CoordinatorDetectionService`
~L1251) traga todo sin filtro, y `TrailMapMatcher` los rutea fielmente por calles adyacentes con
σ constante de 10 m — un fix a 150 m de error pesa igual que uno a 5 m.

## Doctrina violada
*Solo el movimiento MEDIDO se dibuja* — un fix de acc 150 m apenas mide nada, pero hoy arrastra la
línea con la misma autoridad que uno bueno. Corolario del user (15-08): descartar está bien cuando
hay redundancia, pero **con pocos fixes hay que conservar algo** — el descarte binario no vale.

## Señales / datos disponibles
- `GpsPoint` ya persiste `accuracy` en el buffer de ruta (verificado en `driving_route.xml` de
  ambos devices) y llega intacto al matcher.
- El matcher es Newson-Krumm: la ponderación por σ por-medición es parte canónica del algoritmo.

## Diseño (sistema, no parche)
Dos capas, ambas puras y testeables:
1. **Ingesta — `DrivingRoute.append`**: descartar solo la basura absoluta
   (`accuracy > HOPELESS_ACCURACY_M = 100`), y NUNCA si el buffer va a quedarse sin puntos frescos
   (el primer punto de un trip siempre entra). Regla en el objeto puro, no en el servicio.
2. **Matching — `TrailMapMatcher`**: `EMISSION_SIGMA_METERS` deja de ser constante: σ efectiva por
   medición = `max(10, accuracy)` (clamp superior al radio de snap). Un fix impreciso puede matchear
   cualquier candidato cercano con coste casi plano → deja de decidir la calle cuando hay vecinos
   precisos, y sigue anclando el tramo cuando es lo único que hay. La decimación elige, entre los
   puntos del mismo tramo de 25 m, el de mejor accuracy (hoy se queda el primero que cruza el
   umbral).

## Criterio de éxito
- ✅ Test `should keep an imprecise drifted run on the followed street…` — **verificado rojo con σ
  constante** (falló exactamente en el escenario de campo) y verde con σ por punto.
- ✅ Test `should still draw the line when every fix is imprecise` — con pocos fixes se conserva algo.
- ✅ Test `should let the sharper fix of a spacing bucket represent it in decimation`.
- ✅ Tests de gate de ingesta en `DrivingRouteTest` (rechazo >100 m, sin origen basura, acepta =100 m).
- ⏳ Campo: repetir tramo urbano centro (Federico Rubio / Palacios) sin lazos fuera de vía.

## Consumidores auditados (grep 15-08)
- `DrivingRoute.append` ← solo `DrivingRouteStoreImpl` (tap del servicio). ✔ cubierto.
- `TrailMapMatcher.snap` ← `EnrichParkingSessionWorker` (snap one-shot post-park) y dibujo en vivo.
  Ambos pasan `List<GpsPoint>` con accuracy real → cubiertos por el mismo cambio. ✔
- `EMISSION_SIGMA_METERS` / `sigmaFor` / `HOPELESS_ACCURACY_METERS`: **cero usos fuera** de
  `TrailMapMatcher` y `DrivingRoute` (grep sin resultados). ✔
- Ranking por capa (`candidatesFor().sortBy { emissionCost() }`): σ compartida por todos los
  candidatos de un fix → orden intacto. Penalización minor-way absoluta a propósito: un fix que no
  discrimina prefiere la vía real sobre un service way. ✔ (tests ROUTE-QUALITY-001 en verde)

## Estado build
1136 tests ✔ · `compileProdDebug` + `compileMockDebug` ✔ (15-08). Sin galería/escenarios nuevos:
no hay pantalla ni estado nuevo (cambio puro de dominio).
