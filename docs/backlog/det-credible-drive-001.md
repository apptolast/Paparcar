# DET-CREDIBLE-DRIVE-001 — Primitivo "conducción creíble": velocidad+accuracy O velocidad+desplazamiento

> **Estado**: NÚCLEO IMPLEMENTADO 2026-07-16 en la rama `feature/DET-ANCHOR-EGRESS-001-egress-born-at-anchor`
> (encadenado tras el techo/Regla A por decisión del user). Implementado: salida sostenida desde el
> ancla (`isSustainedDepartureFromAnchor` — descongela por desplazamiento corroborado), banda
> ambigua muda no prueba COCHE (el odómetro de caminata sobrevive), taint de ancla walk-entered
> (`anchorWalkEntered` → Prompt en el evaluador + nudge en el save desatendido). Validado en replay:
> Enamorados CONFIRMA en la llegada real (~13 m del coche) y Camelias-Oppo degrada a Prompt.
> DIFERIDO (sin datos de campo que lo pidan aún): tocar `credibleSpeedFix`/maxSpeed,
> `hasJustReachedSpeed` y `VerifyDepartureEvidenceUseCase`; y el reposition-burst con contador
> mudo (residual documentado — exige acc≤15 ×3 consecutivos).
> Origen: FP Enamorados (auditoría 2026-07-15) — el unfreeze se perdió por 2 m de accuracy.
> Decidido con el user 2026-07-16. Prioridad: **P0**.

## Problema (evidencia de campo)

El gate actual es un acantilado binario aplicado igual a todas las velocidades:
`isRealDrive = speed ≥ 5 m/s AND acc ≤ 50` (`CoordinatorParkingDetector.kt:1290`, y el mismo
patrón en `credibleSpeedFix`/maxSpeed L550 e `isDriving` L1283).

Sesión D3 `1784131878857` (15-07): ancla congelada en un semáforo; el tramo final del viaje dio
**36,4 km/h con acc=52** (falla por 2 m) y 20,2 km/h con acc=69 — MIUI en movimiento entrega
acc 50-220 con gaps de 33-79 s. El ancla no se descongeló nunca y el pin salió a 1,11 km.

Pero eliminar el gate de accuracy sin más reabre fantasmas reales: los Doppler de andar rápido
daban 2,5-3,6 m/s (campo 04-07) y un fix acc=100 "inventó" una salida (DET-EXIT-TRUST, 08-07).
La velocidad GPS es Doppler: fiable casi siempre, no incondicionalmente.

## Doctrina

*Una afirmación de velocidad se cree si la corrobora la precisión O el desplazamiento.* Un
fantasma Doppler no se desplaza; una posición cacheada no se desplaza; un coche a 36 km/h se
desplaza cientos de metros entre fixes. La corroboración por desplazamiento es evidencia
independiente del accuracy por-fix.

## Diseño

Redefinir el primitivo en UN sitio (función pura, config-driven), consumido por todos los que
hoy repiten el patrón speed+acc:

```
credibleDrive(prev, curr) =
    (curr.speed ≥ bar AND curr.acc ≤ minGpsAccuracyForDriving)          // regla actual
 OR (curr.speed ≥ bar AND prev != null
     AND dist(prev, curr) > prev.acc + curr.acc + margen                 // se movió de verdad
     AND dist(prev, curr) ≈ velocidadMedia × Δt (tolerancia config))     // coherente con lo declarado
```

Consumidores a migrar (sin cambiar su semántica, solo el primitivo):
- `isRealDrive` / `effectiveDriving` (unfreeze del ancla, L1283-1320) — el caso Enamorados.
- `credibleSpeedFix` → `maxSpeedMps` (L550): el peak de sesión también perdió esos fixes, lo que
  degrada `sessionSawDriving` y la política de evidencia débil.
- `hasJustReachedSpeed` (hasEverReachedDrivingSpeed) y el resume-bar del hold DET-C-02.
- El evaluador de salida (`VerifyDepartureEvidenceUseCase`) SOLO si el replay confirma que no
  relaja el caso acc=100 del 08-07 — el desplazamiento coherente entre 2+ fixes debe seguir
  rechazándolo (una teleportación única no tiene Δt/velocidad coherentes).

Guardarraíles:
- Exigir 2 fixes (el primitivo con `prev == null` = regla actual estricta): una teleportación
  aislada nunca corrobora.
- La tolerancia de coherencia es config (`companion object`/`ParkingDetectionConfig`), no inline.

## Hallazgo añadido 2026-07-16 — el lavado de la banda ambigua (contador mudo)

Traza D2 Camelias ruta-2 (`1784131860665`, fixture `TraceCameliasOppo001`), ground truth del
user corroborado por el AR EXIT del Redmi: coche real en ~36.597877,-6.250989; pin a 37 m,
dentro de la casa. Cadena: tras un reposicionamiento real (fix creíble 5,15 m/s), el usuario
volvió andando a la casa con el contador de pasos MUDO (cero eventos STEP durante la caminata) y
el columpio de recuperación del GPS dio fixes de 2,5-4,9 m/s (¡ida de 68 m y vuelta en 18 s —
imposible a pie!). El discriminador persona/coche de la banda ambigua, ciego sin pasos
("el desplazamiento desborda cero pasos → COCHE"), clasificó la caminata como conducción y
PUSO A CERO `walkFixesSinceDriving` → la parada en la puerta de casa leyó como drive-entered →
el ancla CONGELÓ en el peatón. El arrastre-a-casa sobrevive por este agujero.

Cierre requerido en este ticket: **la banda ambigua (2,5-5 m/s) no puede probar COCHE con el
contador mudo** — un fix ambiguo sin corroboración (ni pasos que comparar, ni desplazamiento
coherente ida-sin-vuelta) no debe resolver CAR ni poner a cero el odómetro de walk-fixes. La
trayectoria out-and-back falla la coherencia de desplazamiento por construcción.

## Validación

Replay: (a) Enamorados `1784131878857` → el ancla se descongela en el tramo 36 km/h/acc52 y el
pin nace en Camelias; (b) fantasmas 04-07 (2,5-3,6 m/s andando) → siguen sin contar como
conducción (no cumplen bar de velocidad ni desplazamiento coherente); (c) DET-EXIT-TRUST 08-07
(acc=100 teleport) → sigue rechazado; (d) trazas sanas → maxSpeed/confirms idénticos o mejores;
(e) `TraceCameliasOppo001` → el freeze en la puerta de casa queda VETADO (odómetro no lavado) y
la sesión degrada a prompt en vez de confirmar a 37 m del coche (flip del test de caracterización
`camelias_oppo_001_…`).

## Criterio de éxito

Nunca más un viaje real perdido por accuracy nominal con velocidad y desplazamiento coherentes;
cero regresión en los tres incidentes-fantasma fijados; y el pin de Camelias-Oppo deja de nacer
en la puerta de la casa (prompt, no pin, hasta que un ancla de verdad exista).
