# OVERPASS-THE-QUOTAS-WAIT-IS-PART-OF-THE-WALK-001 · La espera del cupo es parte de lo que cuesta preguntar, y no aparecía en ningún sitio

**Estado:** ✅ Done · en master (14-09-2026)

## Problema

Salió de una auditoría del propio trabajo del día. El cupo de Overpass
[OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001] añadió un coste nuevo a «preguntar a
Overpass» —hasta 10 s esperando turno— y ese término **no apareció en dos sitios donde debía**:

1. **El camino de "no hubo turno" no lo ejecutaba ningún test.** Se escribió ese mismo día y quedó
   sin testigo.
2. **`AddressAndPlaceRepositoryImpl.PHASE2_TIMEOUT_MS` seguía justificándose con un cálculo muerto**:
   *«por encima de `OverpassEndpoints.ALL.size` × 25 s (3 × 25)»*. Ese día dejó de describir el
   código por **tres** motivos a la vez — los espejos pasaron de 3 a 2, la caminata dejó de costar
   N × 25 s para estar acotada por `WALK_CEILING_MS`, y apareció la espera del cupo.

⚠️ **Nada estaba roto**: 80 s > 10 + 30. Pero lo estaba **por casualidad, no por diseño**, y la
casualidad no sobrevive al siguiente cambio de número.

## Doctrina violada

- **⛔ Prohibición sin testigo no es chequeo.** El camino del cupo protege algo caro:
  `AddressAndPlaceRepositoryImpl` **sella la celda 30 días** cuando el POI contesta bien
  (`poiChecked = true`). Si quedarse sin turno se colara como *«pregunté y no hay nada»*, una tanda
  de carriles pisándose enseñaría a la caché que tu propia calle no tiene ningún sitio, durante un
  mes. Hoy devuelve `failure` y está bien — pero eso lo sabía yo leyendo, no el proyecto.
- **⛔ Un número sostenido por una afirmación que dejó de ser cierta.** Es *exactamente* el defecto
  que `OverpassWalkPatience` arregló esa misma mañana (el corte de 8 s justificado con un
  «streaming» que no existe). Reaparecido un piso más arriba, y esta vez lo dejé yo.

## Diseño

**Derivar, no carvar** — la misma respuesta que la de la mañana, aplicada al techo de fase 2:

```
PHASE2_TIMEOUT_MS = OverpassQuota.WAIT_CEILING_MS      (esperar turno)
                  + OverpassWalkPatience.WALK_CEILING_MS (la caminata entera)
                  + PHASE2_SLACK_MS                     (parsear y volver al hilo)
```

Mover cualquiera de las dos piezas mueve el techo solo. La holgura se documenta por lo que **no**
es: margen para que Overpass tarde más, no; eso ya lo acotan las dos de arriba.

⛔ El `companion object` pasa de `private` a `internal`, porque una derivación que el test no puede
leer es una derivación sin testigo. Mismo motivo por el que ya son internal `OverpassWalkPatience` y
`OsmPublicParkingParser.MAX_RESULTS`.

## Criterio de éxito

Tres testigos, **los tres falsificados** (rompiendo lo que vigilan y comprobando que fallan):

| Testigo | Qué pasa si se rompe lo vigilado |
|---|---|
| `should_failWithoutAskingAnybody_when_anotherLaneHoldsTheOnlySlot` | ✅ falla si la denegación se cuela como respuesta buena |
| `should_outlastTheWorstOverpassAskThereIs` | ✅ falla al devolver el techo a 20 s carvados |
| `should_leaveRoomForOurselves_butNotForAnExtraServer` | ✅ falla igual — una holgura del tamaño de otra caminata deja de acotar |

Más `should_answerNormally_when_theSlotComesFreeInTime`: el cupo **retrasa, no condena**.

## Consumidores auditados

| Sitio | Qué asume del coste de preguntar | Estado |
|---|---|---|
| `AddressAndPlaceRepositoryImpl.PHASE2_TIMEOUT_MS` | envolvía la caminata entera | ✅ derivado de sus dos piezas |
| `PHASE1_TIMEOUT_MS` | geocoder local, no Overpass | ✅ intacto, no le afecta |
| `fuelHttpClient` timeouts (25 s) | por PETICIÓN, no por caminata | ✅ intacto: es otra capa |
| `OsmPublicParkingsSource` · `OsmFuelStationsSource` · `OsmStationNames` · `OverpassPlacesDataSource` · `OverpassRoadNetworkDataSource` | ninguno envuelve la llamada en su propio timeout | ✅ solo el de POI lo hacía |
| Barrido `grep withTimeout` sobre los 5 carriles | — | ✅ el de fase 2 era el único |

## Fuera de alcance

El geocode que bloquea la petición de parkings sin que la fuente lo use sigue anotado en
`overpass-a-mirror-walk-must-outlive-the-server-it-asks-001.md`. No entra aquí: es latencia de
arranque, no un techo mal calculado.
