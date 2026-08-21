# DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001 · La precisión del fix dice dónde está el CUERPO, no dónde está el COCHE

**Estado:** ✅ Done · en master vía squash · **1356 tests verdes** · `prod` + `mock` compilan ·
⏳ campo · 🔁 deja un follow-up abierto (la UI no dibuja el radio)

## Problema

Field 2026-08-21 23:50:47, Oppo, sesión `1787348798966`:

```
⑊ honest close: aborted_no_movement → closed_approximate_pin
  (trip_proven; pinDist=991m steps=206/530) [DET-HONEST-CLOSE-001]
```

Tras un hueco ciego de **7 min 43 s** (ver `DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001`), el honest
close plantó el coche **dentro de la casa del user**, a ~80 m del bordillo donde su amigo le había
dejado. Con fiabilidad 0,5 y su nudge, sí — pero como un **punto**, no como un área.

Y lo plantó ahí porque el fix era buenísimo: `accuracy = 3,644 m`.

## Doctrina violada

**«Fallo asimétrico: ante la duda se PREGUNTA, nunca se planta una plaza fantasma.»** Aquí no había
duda que preguntar porque el evaluador **creía no tenerla**: confundió dos magnitudes distintas.

- `abortFix.accuracy` = **cómo de bien sabemos dónde está el TELÉFONO**.
- El artefacto responde **dónde está el COCHE**.

Entre las dos está la caminata que hizo la persona después de bajarse — invisible para la calidad
del GPS, y el error entero. El pin no era impreciso: estaba **seguro de la cosa equivocada**.

## Señales / datos disponibles

Ninguna nueva. La escalera ya calcula todo lo que hace falta y lo tiraba:

| Dato | En el caso de campo |
|---|---|
| `stepsSinceStalePin` | 206 |
| `requiredSteps` (lo que la distancia exigía) | 530 |
| `config.strideMeters` | 0,75 m |
| `abortFix.accuracy` | 3,644 m |

## Diseño

Un solo sitio: `EvaluateHonestCloseUseCase.artifactFor(abortFix, steps)`, que sustituye las **dos**
copias de la decisión pin-vs-zona (la rama `trip_proven` y la de `session_measured_driving`, que
tenían el mismo código duplicado).

```
duda = max(accuracy_del_fix, pasos_contados × zancada)
duda ≤ minGpsAccuracyForDriving (50 m)  → PIN
si no                                    → ZONA de radio duda, acotada a [60 m, 250 m]
```

### Por qué los pasos son la cota correcta, y por qué se auto-escala

Los pasos contados son lo ÚNICO que este evaluador tiene sobre esa caminata — y tienen exactamente
la forma correcta, por cómo funciona la prueba de viaje: **a este peldaño sólo se LLEGA porque los
pasos se quedaron muy por debajo de lo que la distancia exigía**. Así que la duda que producen es
pequeña justo cuando el aborto siguió de cerca al aparcamiento, y grande justo cuando no.

- **Caso de campo:** 206 × 0,75 = **154 m**. Una zona que **contiene el coche** (error real ~80 m),
  donde antes había una mentira de 3,6 m.
- **Casos para los que se escribió la escalera** (Camelias hop, D2 return): abortan minutos después
  del aparcamiento real, cuentan un puñado de pasos → 15-20 m de duda → **siguen siendo pin**. Los
  22 tests que ya existían pasan sin tocar una aserción.

Suelo y techo son los mismos que la zona del unattended (`honestCloseMinZoneRadiusMeters` = 60 m,
`unattendedZoneMaxRadiusMeters` = 250 m): por debajo del suelo un «área» es un punto con ceremonia;
por encima del techo pinta medio barrio y deja de significar nada. Es el mismo trato que
`DET-GAP-ANCHOR-ZONE-001` hizo con un hueco de GPS — la misma duda medida con otro testigo.

**Contador mudo (`null`)** → no ofrece cota → decide el fix, exactamente como antes. El único
peldaño que puede llegar con `steps == null` es el de conducción medida, que nunca dependió del
contador.

### Lo que NO cambia

La geocerca del artefacto sigue saliendo de `config.geofenceRadiusFor(size, accuracy)`, así que una
zona ancha **no** registra una valla gigante. La fiabilidad sigue siendo 0,5 y el nudge sigue
saliendo.

## Consumidores auditados

Barrido de quién más responde «¿cómo de bien sé dónde está el coche?» con la precisión de un fix:

| Sitio | Clasificación |
|---|---|
| `EvaluateHonestCloseUseCase` rama `trip_proven` + rama `session_measured_driving` | **cerrado** — eran la misma decisión duplicada; ahora una función |
| `EvaluateSafetyNetCheckUseCase:314` (`fix.accuracy <= minGpsAccuracyForDriving`) | **exento con razón** — pregunta «¿es este fix creíble para razonar?», que sí es calidad de fix. Y su backfill **ya** acota la caminata: exige `trustedStepsSinceAnchor <= maxBoardingSteps` antes de colocar nada. Precedente que confirma la doctrina |
| `EvaluateUnattendedParkingSaveUseCase` (`zoneOrAsk` + `doubtMeters`) | **cubierto por convergencia** — ya razonaba con radio de duda; este ticket alinea el honest close con él |
| `CoordinatorParkingDetector` (5 sitios), `EvaluateBtParkUseCase`, `EvaluateShortHopDriveProofUseCase`, `VerifyDepartureEvidenceUseCase` | **exentos** — todos preguntan «¿es creíble la VELOCIDAD de este fix?», otra pregunta |
| `RunHonestCloseUseCase` / `CoordinatorDetectionService.maybeRunHonestClose` | **cubiertos** — ya propagan `zoneRadiusMeters` al confirm y a la telemetría; sin cambios |

## Follow-up que este ticket NO cierra

⚠️ **La UI todavía no dibuja el radio.** `UserParking.zoneRadiusMeters` / `isApproximate` sólo se
leen desde tests: no hay ningún consumidor en `presentation/` ni en `ui/`. O sea, hoy la diferencia
entre un pin y una zona de 154 m es honesta **en los datos y en el diagnóstico**, pero al user le
sigue apareciendo un marcador igual. Hasta que eso se pinte, el remedio práctico para él sigue
siendo el nudge.

→ Ticket propio: `docs/backlog/ui-approximate-parking-draws-its-doubt-001.md`.

## Criterio de éxito

- Test que replica la sesión `1787348798966` con sus números medidos y exige una zona que **alcance
  el punto de bajada** (≥ 80 m).
- Los 22 tests previos verdes sin tocar aserciones — la precisión de los casos buenos no se toca.
- Test de techo (4.000 pasos → 250 m, no un kilómetro) y de contador mudo (cae al fix).
- En campo: una vuelta a casa andando desde donde te dejan deja **un área**, no un punto en el salón.
