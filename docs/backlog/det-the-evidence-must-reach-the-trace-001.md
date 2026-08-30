# DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001 · el veredicto que decide tiene que poder leerse en el log

**Estado:** 🔵 En progreso · rama `feature/DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001-evidence-trace`
· worktree `../Paparcar-evidence-trace` · apilada sobre `DET-NO-CLOCK-PLANTS-A-PIN-001`

## Problema

Diagnosticar la noche del 29→30-08 obligó a **re-derivar a mano, desde 6.464 líneas de fixes crudos**,
el número que las rutas de confirm obedecen. Ni el `parkdiag` ni Firestore lo emitían.

Y hay algo peor, encontrado al ir a añadirlo: **el campo remoto `drivingFixes` no significa lo que
todo el mundo cree que significa.**

```kotlin
if (kmh >= DRIVING_SPEED_KMH) r.drivingFixes++      // FirestoreDetectionEventLogger:250
```

**No tiene puerta de precisión, y nunca la tuvo.** Cuenta todo fix por encima de la barra de
velocidad, con la accuracy que sea. `DriveProof.credibleFixCount` —el contador que SÍ leen todas las
decisiones— exige además `acc <= minGpsAccuracyForDriving`.

La diferencia no es cosmética. En la sesión de casa del 30-08:

| contador | valor |
|---|---|
| `drivingFixes` (remoto, sin puerta) | **44** |
| `credibleFixCount` (el que decide) | **7** |

37 fixes descartados por precisión, la noche del agujero de GPS. **Ese es el origen del error del
§6.1 del rediseño**: no fue un fallo de conteo a mano, fue leer un campo cuyo nombre promete una cosa
y mide otra. Un umbral empírico sacado de 148 sesiones se calculó sobre la definición equivocada.

## Doctrina violada

*Si su resultado no se puede citar en un diagnóstico, no es un caso de uso* — la regla del proyecto
para los veredictos, aplicada a la telemetría: **un veredicto que no se puede leer en una traza es un
veredicto que ningún field test puede comprobar.**

## Diseño

1. **`DrivingEvidence.trace()`** — un token compacto con el veredicto, sus cifras y, cuando se queda
   corto, QUÉ barra falló: `WEAK(fixes=1; only 1 credible driving fix(es), bar is 2)`.
2. **Local**: `evidence=` viaja en **cada línea de estado** del `parkdiag`. Los dos booleanos de ciclo
   de vida se quedan — responden otra pregunta (*¿puede seguir viva?*), y borrarlos escondería
   justamente la distinción que este trabajo ha costado establecer.
3. **Remoto**: campo nuevo `credibleDrivingFixes` en el rollup de sesión, **junto a** `drivingFixes`.
   - ⛔ `drivingFixes` **no se corrige en su sitio**, a propósito: cambiar su definición dejaría las
     sesiones viejas y las nuevas incomparables sin que nada lo dijera — el mismo error, otra vez.
   - El summary legible imprime los dos: `drive 44/376fix (cred 7)`.
4. El logger recibe la `ParkingDetectionConfig` inyectada, para que la barra de precisión sea **la
   del detector** y no una copia privada. La barra de velocidad ya estaba duplicada aquí como
   constante en km/h.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `FirestoreDetectionEventLogger.accumulate` | **cerrado** — cuenta las dos |
| `DetectionEventDto` | **cerrado** — campo nuevo |
| línea de estado del `parkdiag` | **cerrado** |
| `buildSummary` (espejo en logcat) | **cerrado** — imprime las dos |
| **análisis históricos que citen `drivingFixes`** | ⚠️ **quedan sospechosos**: §6.1 del rediseño ya corregido; cualquier otro que aparezca hay que recontarlo con `credibleDrivingFixes` |

## Pendiente en este mismo ticket

- [ ] El veredicto en el **rollup remoto** (hoy sólo el contador). Necesita que `SessionEnded` lo
      transporte, que es plumbing de evento — se hace aquí, no en otro sitio.
- [ ] `zoneRadiusMeters` / `isApproximate` **no llegan a Firestore** (`ParkingSessionMapper.kt:111`,
      local-only por diseño). Una zona de 250 m se ve en remoto como un pin exacto.

## Criterio de éxito

- Una traza de campo permite leer el veredicto sin recontar fixes a mano.
- `credibleDrivingFixes` y `drivingFixes` conviven y se distinguen en el summary.
