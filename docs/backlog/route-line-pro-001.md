# ROUTE-LINE-PRO-001 — La línea de la ruta ES la geometría de la calle (HMM Newson–Krumm)

**Estado:** ✅ EN MASTER `4cabe9d8` (ff-only 2026-08-07, rama borrada); 13/13 tests matcher +
suite prod + compile mock verdes; ⏳ device/field-test (el APK de campo del 06-08 lleva la v3 —
regenerar desde master para probar la v4).

## El salto conceptual respecto a v3

v1–v3 corregían los FIXES y los unían con cuerdas: la línea dibujada seguía siendo "puntos GPS
arreglados". El estándar de la industria (OSRM `match`, Valhalla/Meili, las APIs comerciales de
map-matching) hace otra cosa: decide la RUTA más probable por el grafo de calles y dibuja la
geometría de la propia calle. La diferencia se ve en cada curva: con v3, dos fixes a ambos lados
de una esquina se unían con una recta que cortaba la manzana (el gap-fill v2 solo ruteaba huecos
>60 m); con v4 la línea gira EN la esquina siempre, porque cada transición entre puntos va ruteada
por el grafo.

## La decisión — v4 del `TrailMapMatcher`: HMM completo (Newson & Krumm 2009), mismo seam único

- **Decimación previa** (`MATCH_SPACING_METERS` 25 m): se casan puntos espaciados — una transición
  solo discrimina calles cuando el paso medido es grande frente al ruido GPS — y la geometría
  ruteada entre ellos restaura todo el detalle de calle que la decimación saltó.
- **Candidatos** = proyecciones del fix sobre los edges a ≤`MAX_SNAP_METERS` (60 m), máx 4,
  dedupe a 5 m (edges adyacentes del mismo way no expulsan alternativas reales).
- **Viterbi** minimiza emisión (gaussiana, `EMISSION_SIGMA_METERS` 10 — trazas de móvil urbanas,
  más ruidosas que los 4.07 m del paper) + transición (exponencial sobre
  |distancia ruteada − distancia recta|, `TRANSITION_BETA_METERS` 3, el default de Valhalla).
  Una paralela conectada solo por los extremos exige un rodeo de ~900 m para un paso medido de
  ~48 m → transición imposible/carísima → no roba el fix ruidoso. Cubre y jubila el truco v3
  (transición con paso en línea recta).
- **Ruteo real por el grafo**: `RoadGraph` (ways unidos por nodos OSM compartidos, edges
  explícitos) + Dijkstra acotado (bound = 3× recta + 120 m de holgura para esquinas en pasos
  cortos), UN Dijkstra por endpoint de edge del layer anterior compartido entre pares. La salida
  concatena los caminos ganadores: la línea sigue curvas, esquinas y rotondas aunque todos los
  fixes vayan desviados. Como Viterbi es global, cada re-match con fixes nuevos puede re-decidir
  tramos anteriores: la línea se auto-corrige.
- **Honestidad intacta** (fallo asimétrico aplicado al visual): sin calle a ≤60 m → fix crudo
  (rachas interiores ≤2 se descartan como pincho; rachas largas y extremos — el origen retrodatado
  puede estar en un parking — se conservan); sin camino plausible entre candidatos (grafo
  desconectado o rodeo >bound) → ruptura del HMM y cuerda recta honesta, nunca un desvío inventado.

Todo visual/presentación: `HomeTripController` llama al mismo `snap(points, roads)`; la evidencia
de detección, `TripTrailImpl` forense y la decimación `MapTrail` no se tocan.

## Validación

- `TrailMapMatcherTest` 13 tests: los 10 de v1–v3 pasan sin cambios (mismo contrato observable) +
  3 nuevos de v4: esquina dibujada entre fixes a ~40 m (paso que el gap-fill v2 nunca ruteó),
  decimación de fixes densos con la línea aún cubriendo el tramo, y la paralela CONECTADA que no
  roba el fix ruidoso (el caso que v3 ganaba por desconexión y v4 gana por coste de transición).
- Suite prod completa + `assembleMockDebug` verdes.
- ⏳ Device/field-test: mismos tramos de las capturas del 30-07; verificar que las esquinas se
  dibujan giradas y no cortadas.

## Ficheros

- `domain/matching/TrailMapMatcher.kt` — v4 completo (decimación + candidatos + Viterbi +
  `RoadGraph` con Dijkstra acotado + `MinHeap` propio, commonMain sin java.util).
- `commonTest/domain/matching/TrailMapMatcherTest.kt` — 3 tests nuevos + secciones reencuadradas.
- `docs/detection/PARKING-DETECTION.md` — changelog (misma tarea).

## Relacionados

- ROUTE-SNAP-001 (v1, per-point) · DET-ROUTE-ORIGIN-001 (v2, gap-fill A*) ·
  ROUTE-LINE-CLEAN-001 (v3, Viterbi con transición recta) — los tres subsumidos por v4.
- ROUTE-SMOOTH-002 (spline solo del trail crudo; el matched se dibuja directo — sin cambios).
