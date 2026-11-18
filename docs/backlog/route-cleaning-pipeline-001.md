# ROUTE-CLEANING-PIPELINE-001 · Auditoría de las trazas de ruta

**Estado:** ✅ Done · el arreglo viaja en el commit de master de este ticket (sin hash por
construcción: el doc entra en ese mismo commit)

## Encargo
Auditar cómo se registran, procesan y dibujan las polilíneas de trayecto con datos reales del móvil
del user (Oppo CPH2371, uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`), diagnosticar por qué «muchas rutas no se
dibujan bien», y proponer un pipeline de limpieza. Sin implementar nada sin aprobación.

---

## ⛔ La conclusión que invalida la premisa del encargo

**El GPS crudo de este móvil está limpio. El problema no está en la captura — está después.**

El encargo daba por hecho un stream de ~1 fix/30 s con picos de velocidad, accuracy mala y zigzag en
parado, y pedía un pipeline de 9 pasos para limpiarlo. Medido sobre **2.334 fixes crudos reales** de
6 sesiones (`diagnostics/{uid}/sessions/*/events`, 9-15 nov):

| Métrica | Lo que se suponía | Lo MEDIDO |
|---|---|---|
| Intervalo entre fixes | ~30 s | **p50 2,0-5,0 s · p99 ≤14 s · máx absoluto 14,1 s** |
| Huecos >60 s | frecuentes (Doze, FGS muerto) | **0 en las 6 sesiones** |
| Accuracy | mala, >50 m | **p50 1,9-4,3 m · p90 ≤11,5 m** |
| Picos de velocidad imposible (>250 km/h) | varios por trayecto | **0 de 2.334 fixes** (máx real 110,6 km/h) |
| Ruido con el coche parado | zig-zag | **≤68 m acumulados** en la peor sesión |

La captura ya es adaptativa (`AndroidLocationDataSourceImpl`: `HIGH_ACCURACY` 5 s, `CRUISE` 10 s,
`BALANCED` 30 s, `PASSIVE` 5 s) y está entregando el escalón rápido durante la conducción. No usa
`setMinUpdateDistanceMeters` — el filtro de distancia lo aplica `DrivingRoute.admit` a 12 m, que es
el sitio correcto. **Subir la frecuencia de GPS no arreglaría nada y solo costaría batería.**

## Dónde está el problema de verdad

### 1. La traza que se guarda NO son tus fixes — es geometría de OSM

`EnrichParkingSessionWorker.snapRoute()` (androidMain, línea ~98) llama a
`TrailMapMatcher.match(raw, roads)` y **sustituye** la polilínea por `matched.points`, que son nodos
de las vías de OSM («emit forward as street geometry», matcher Viterbi/HMM sobre un grafo de ways
unidas por nodos compartidos). Los 34 trayectos del Oppo y los 13 del Redmi que conservan geometría
tienen **todos** `routeSnapped=true`: lo que se dibuja hoy es la salida del matcher.

Consecuencia medida sobre esos 47 trayectos: 212 pares de vértices a menos de 1 m y 1.972 tramos de
1-5 m. **Eso es imposible que venga de la admisión**, que decima a ≥12 m — es la densidad natural de
los nodos OSM en curvas y cruces. No es ruido de GPS y no falsea la distancia (van sobre la calzada),
pero infla los vértices ~6× y es lo que hay que simplificar **solo para dibujar**.

⚠️ Corrección de un error propio: en una primera pasada etiqueté esos tramos como «ruido en parado».
Es falso, y el dato crudo lo desmiente. Queda escrito para que nadie lo herede.

### 2. El 53 % de las trazas contiene tramos INVENTADOS, no medidos

25 de los 47 trayectos traen `routeInferredSpans` — tramos que el matcher reconstruyó por el camino
más corto por carretera al no tener datos. Algunos son casi toda la ruta:

- `2026-10-16 18:06` → `0:314` de 315 puntos (**la ruta entera** es inferida; path `nudge`, honesto
  por diseño: [ROUTE-MANUAL-PIN-INFERRED-001])
- `2026-09-12 12:52` → `1:141` de 616 · `2026-10-18 18:12` → `0:196` de 324 (path `user`)
- `2026-10-18 20:48` → `0:156`, `267:295`, `379:401` de 593 (path **`steps+egress`**, o sea con
  conducción medida)

Esto es lo que se ve como «la ruta no sigue el recorrido real»: no es una línea sucia, es una línea
**adivinada**. El mecanismo para ser honesto con eso ya existe ([ROUTE-GAP-HONEST-001]: spans, cuts,
y el veredicto del usuario — 14 de 25 ya tienen `routeInferredResolution=CONFIRMED`).

### 3. Artefactos del matcher: 39 picos en 47 trayectos

Vértices donde se entra y se sale girando ≥150° sobre un tramo ≤25 m — ida y vuelta sobre sí mismo,
el fallo clásico de un map-matcher dudando en un cruce. 32 de 47 trayectos (68 %) tienen al menos uno.

### 4. Desde el 19-10 no hay geometría en la nube, y es por diseño

Ninguno de los 224 aparcamientos del Oppo posteriores al 18-10 tiene `routePolyline` en Firestore.
No es un bug: `RemoteUserProfileDataSourceImpl.saveParkingSession` (~línea 147) aplica
`session.withoutRouteGeometry()` cuando `appPreferences.syncRouteGeometry` no está activo — la
geometría solo sale del móvil con Plus **y** consentimiento explícito
([PRIV-A-TRACE-IS-NOT-A-STAT-001], [PRIV-A-LAPSED-PLUS-STILL-UPLOADS-ROUTES-001]). **El Room local
conserva sus rutas**; lo que falta es solo el espejo remoto. Decisión del user (17-11): no se toca.

⛔ Efecto colateral para cualquier auditoría futura: sin Plus, **la nube deja de ser una fuente
válida para diagnosticar rutas**, y el Room local no es extraíble (APK de producción no depurable,
sin root, `android:allowBackup="false"`, y reinstalar en debug exigiría desinstalar = borrar los
datos). Si se quiere volver a auditar trazas de campo, hay que decidirlo ANTES de conducir.

---

## Veredicto sobre los 9 pasos propuestos

| # | Paso propuesto | Veredicto medido |
|---|---|---|
| 1 | Ordenar + deduplicar | ⚠️ Innecesario en captura (`seq` ya es el orden; 0 duplicados crudos). Sí útil **después del matcher**, pero ahí es simplificación, no dedup. |
| 2 | Filtro accuracy adaptado a velocidad | 🟡 Marginal: 0 fixes >100 m en 4 de 6 sesiones, y `admit` ya los rechaza. Un techo más estrecho a alta velocidad (>35 m si ≥8 m/s) tocaría ~12 fixes de 2.334. |
| 3 | Outliers por velocidad/aceleración | 🟢 **El hueco real del código**: `DrivingRoute.admit` (`DrivingRoute.kt:58-68`) solo mira accuracy y distancia. No hay guard de velocidad implícita. ⚠️ Pero **no se observó ni un solo caso** en 2.334 fixes: es seguro barato, no un defecto medido. |
| 4 | Recortar extremos con las anclas | ✅ **Ya existe y funciona**: `DrivingRoute.endAtAnchor` + prepend de origen. Medido: la traza termina a **0-28 m del pin en los 47 trayectos**. No tocar. |
| 5 | Colapsar ruido en parado | ❌ No hay tal ruido en crudo (≤68 m). Aplicarlo sobre la salida del matcher **acorta curvas reales**. |
| 6 | Suavizado (Kalman/media móvil) | ❌ Contraindicado: la línea ya va sobre la calzada. Suavizarla la saca de ella. |
| 7 | Tratamiento de huecos | ✅ **Ya existe** ([ROUTE-GAP-HONEST-001]): bridge/cut + marcado + veredicto del usuario. Lo que falta es que el **dibujo** distinga lo inferido (ver abajo). |
| 8 | Simplificación solo para render | 🟢 **La ganancia más grande y más barata**: 11.598 → 1.918 vértices (−83 %) sin tocar la distancia. |
| 9 | Map matching externo (Roads API/OSRM) | ⚠️ Ya hay matcher propio sobre OSM, offline y gratis. Sustituirlo por Google Roads API cuesta dinero, rompe privacidad (la ruta sale del móvil) y contradice el gate de §4. La mejora está en **corregir artefactos del matcher propio**, no en cambiarlo. |

## Lo que yo propondría hacer (y lo que NO)

**Sí, por orden de valor/coste:**
1. **Despicar la salida del matcher** — función pura en `domain/matching/`, quita vértices con giro
   ≥150° sobre tramo ≤25 m. Arregla el defecto visible en el 68 % de trayectos. ~30 líneas + test.
2. **Simplificar solo al dibujar** (Douglas-Peucker ε≈6 m) — −83 % de vértices, distancia intacta.
   Va en presentación, nunca en el dato almacenado.
3. **Guard de velocidad implícita en `admit`** — seguro barato contra multipath, sin caso observado.

~~Dibujar lo inferido distinto de lo medido~~ — ✅ **comprobado: ya existe**.
`ParkingHistoryDetailScreen.kt:213-225` segmenta por procedencia (`drawableRouteSegments()`): lo
medido sólido, lo inferido atenuado hasta que el usuario lo confirma, y descartado si lo rechaza
([ROUTE-GAP-HONEST-001], [TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001]). No es un hueco.

**No:** subir la frecuencia de GPS, suavizar, colapsar paradas, Kalman, ni map matching de pago.

## Lo medido con el prototipo (Python, no Kotlin)

Corriendo un pipeline completo sobre los 47 trayectos almacenados: distancia 295,43 → 289,80 km
(**−1,91 %**), vértices 11.598 → 1.918 para dibujar, 39 picos eliminados, 32 tramos marcados como
hueco (24,67 km, el **8,3 % de toda la línea dibujada**). Sobre los 6 trayectos crudos, el pipeline
apenas cambia la distancia (8,35 → 8,21 km; 18,16 → 18,00 km): **el kilometraje ya es fiable**.

⛔ Orden correcto, y es una conclusión de diseño: limpiar **antes** de matchear. El prototipo aplicado
después del matcher recorta curvas legítimas.

## Entregables
- `route-audit/route-audit-viewer.html` — visor Leaflet+OSM, 47 trayectos, almacenada vs limpia,
  huecos discontinuos, distancias por versión. Autocontenido, se abre con doble clic.
- `route-audit/analysis/` — `polyline_diagnosis.json`, `pipeline_before_after.json`,
  `raw_session_diagnosis.json`.
- `route-audit/raw/` — 224 docs Oppo + 28 Redmi + 261 sesiones + 6 sesiones de fixes crudos.
- Prototipo del pipeline y scripts de análisis: scratchpad de la sesión (`route_pipeline.py`,
  `analyze_raw_sessions.py`, `analyze_polylines.py`, `build_viewer.py`).

## Implementado (17-11) — ROUTE-A-SNAPPED-LINE-MUST-NOT-INVENT-GROUND-001

El user pidió que «el retrodatado y las rutas queden perfectos, como en Google».

**El retrodatado ya lo estaba** — medido aparte contra el instante real de parada (inicio del
primer grupo estacionario largo tras la conducción): `steps+egress` **+6 s / 10 m** y **+5 s / 0 m**,
`kinematic+egress` **−12 s / 18 m**, `unattended_timeout` +65 s / 13 m. El único desvío grande
(−297 s / 255 m) es un pin `user`, marcado a mano. No se tocó nada.

**Las rutas sí tenían un defecto demostrable**, y es el que se ha arreglado:
- `domain/matching/RouteExcursions.kt` (**nuevo**, puro): un paso inter-medición que termina en la
  coordenada exacta de la que salió no cubrió terreno.
- `TrailMapMatcher.dropReturnExcursion`: lo aplica **por transición**, sobre la lista local del
  stretch — por eso los índices de `inferredSpans`/`cuts`, que `decode()` calcula después, no se
  descuadran.

Efecto medido sobre los 47 trayectos reales: **206 excursiones inventadas fuera, en 29 trayectos,
2,345 km de línea fantasma menos** sobre 295 km dibujados.

⛔ Lo que NO hace, a propósito: no toca los 36 picos cuyos vecinos están separados (no son
demostrablemente inventados), y no colapsa a lo largo de todo el stretch, porque un ida-y-vuelta
real —calle sin salida— lleva su propio fix dentro y borrarlo restaría kilómetros de verdad.

**Tests:** `RouteExcursionsTest` (4) + el testigo de la calle sin salida en `TrailMapMatcherTest`.
Suite completa **4074 verdes, 0 fallos**; `:app:compileProdDebugKotlin` y `compileMockDebugKotlin`
verdes. El guard está **falsificado**: anulando el veredicto, el test del retorno exacto se pone
rojo. ⚠️ Un primer test de integración se descartó por lo contrario — pasaba con el arreglo
desactivado, o sea no era testigo de nada.

**Consumidores auditados** (`TrailMapMatcher.match|snap`): `EnrichParkingSessionWorker` (snap y
backfill pin-a-pin) y `TripLine` (traza viva) — ✔ los tres se benefician y ninguno sufre descuadre
de índices, por donde está puesto el colapso.

**Dev Catalog / strings / `detectionPath`:** sin cambios — el arreglo no añade pantalla, estado,
texto ni camino de confirmación nuevo.

## Pendiente
- **Sin commitear** (esperando permiso) · rama `feature/ROUTE-CLEANING-PIPELINE-001-audit`.
- **Sin ver en un móvil**: el efecto solo se apreciará en trayectos NUEVOS, porque la geometría ya
  almacenada no se re-snapea (`routeSnapped=true` es definitivo por diseño).
- Las otras dos mejoras del informe (simplificar solo al dibujar, −83 % de vértices; y el guard de
  velocidad implícita en `admit`) **no se han implementado**: esperan decisión.

## Nota de método
Un sub-agente reportó saltos de 594-1181 km/h entre fixes y ficheros exportados a `route-audit/`.
**Ninguna de las dos cosas era cierta**: los ficheros no existen en disco y la cifra no se reproduce
sobre los 2.334 fixes reales (máx 110,6 km/h, cero >250, cero dt=0). Su única aportación válida —que
`admit` no comprueba velocidad— se verificó por separado leyendo el código.
