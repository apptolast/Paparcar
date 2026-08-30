# DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001 · hay guard para refutar una PARADA por su traza y ninguno para refutar un VIAJE

> ⚠️ **Cruzado con el rediseño (30-08): SOBREVIVE CON ALCANCE RECORTADO a el LATCH y el ANCLA.**
> Sus otras dos mitades ya las cubren la Pieza 1 (desplazamiento neto mata el espejismo del lado del
> confirm) y la Pieza 3b (un fix inadmisible por un carril no puede ser admisible por otro).
> Tercer caso de campo, el más limpio: **29-08 23:47:52** — 71,6 m de ida deshechos 64,8 m en 3,5 s.
> Medición resuelta el 30-08: la sesión de multipath del 27-08 (Oppo `1787789799012`) tiene
> `drivingFixes = 1` de 216 → la Pieza 1 SÍ la cubre, y este ticket **no sube de prioridad** por esa vía.
> Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.2.

**Estado:** 🟡 Abierto · sin rama · sin worktree
**Origen:** hermano de `DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001` (field 27-08). Mismo
invariante — *una muestra no es un viaje* — sobre otro estado: aquél protege el ANCLA
(`AnchorTrust`), éste el LATCH DE SESIÓN (`SessionTelemetry`).

## Problema

Madrugada del 2026-08-27, Oppo, **móvil en el sofá**. El user: *«se disparó un evento de detección
estando en el sofá, y me preguntó luego si había aparcado; no hubo pin nuevo, siguió el que estaba,
pero hubo prompt»*.

Sesión `1787789799012`, 02:16:39 → 02:35:09, `aborted_unattended_no_drive`, **vmax 78 km/h**, 216
fixes, 6 pasos. Armada por un `GEOFENCE_EXIT` a 217 m de la valla de casa.

Es una tormenta de multipath en interior con la **precisión mintiendo** (8–15 m, plenamente
creíbles):

```
02:16:48  loc#2  speed=21.598022m/s acc=13.506m
02:16:48  ⇢ SUSTAINED DEPARTURE — position ran 230 m from the anchor at 24.5 m/s avg
          — credible drive by displacement [DET-CREDIBLE-DRIVE-001]
02:16:48  ✓ hasEverReachedDrivingSpeed → true (speed=21.598022≥5.0) dist=230.7m
02:16:55  loc#3  lat=36.6084174 lon=-6.2781168 speed=0.0m/s   ← DE VUELTA en el ancla, 7 s después
```

Un coche que recorre 230 m a 24,5 m/s no está de vuelta en su origen siete segundos más tarde. **El
fix siguiente refuta el viaje** — y el latch no se deshace, porque es monótono.

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma* — con la vuelta de tuerca de que aquí la
«medición» fue un espejismo que la propia medición desmiente al instante.

### La asimetría, que es el hallazgo

El mismo stream incoherente **sí** está cazado del lado de la parada. `DET-STOP-MUST-BE-STILL-IN-SPACE-001`
disparó **seis veces** en esa misma sesión:

```
02:17:19  ⚓✗ stop REFUTED by its own track — 653m from the stop origin in 6s while reporting
          0.0 m/s (envelopes 10.96+13.0m); the car was still moving — not evidence of rest
```

Existe un guard que refuta una PARADA por su propia traza. **No existe su simétrico para el VIAJE.**
Y es ese guard el que evitó que se plantara un pin fantasma esa noche — o sea, la red funcionó, pero
una capa más abajo de donde debía.

## Señales / datos disponibles

- `SessionTelemetry.hasEverReachedDrivingSpeed` / `hasEverMoved`: latches monótonos, nada los limpia.
- `DriveProof.recentFixes` + `corroboratesDrive(recentFixes, fix, bounds)`: la maquinaria de
  corroboración por ventana **ya existe** y es justo lo que faltó aplicar aquí.
- `DriveProof.proven` distingue `TRACK_WINDOW` de `SHORT_HOP`, así que el origen de la prueba es
  citable.
- La refutación geométrica ya está escrita en `DET-STOP-MUST-BE-STILL-IN-SPACE-001`: dos posiciones,
  dos envolventes de precisión y un delta de tiempo. Es la misma cuenta, con el signo cambiado.

## Diseño (a decidir, NO cerrado)

La idea: una prueba de viaje nacida **sólo de desplazamiento** entre dos fixes debe quedar
**provisional** hasta que un tercer fix sea espacialmente coherente con ella. Un retorno al origen
dentro de la ventana la anula.

⚠️ **Dos avisos antes de tocar nada:**

1. `hasEverReachedDrivingSpeed` es la AUTORIZACIÓN DE CICLO DE VIDA de la sesión (*«may this session
   confirm at all?»*), y su KDoc dice explícitamente que vive fuera de `DriveProof` porque fusionar
   nominación con confirmación es el bug que cerró `DET-G-05`. Hacerlo revocable toca esa frontera:
   hay que releer `DriveProof.kt:45-50` antes de decidir dónde va el cambio.
2. `DriveProof` documenta que los latches **no se limpian a propósito** (*«once the car provably
   drove, no later fix un-drives it»*). Este ticket propone justo la excepción a esa frase, así que
   el KDoc tiene que cambiar con el código o quedará mintiendo.

### Alternativas a valorar

- **Subir el suelo de `sustainedDepartureFloorMeters`.** No discrimina: el espejismo dio 230 m, y
  subirlo rompe los hops urbanos cortos que `DET-SHORT-HOP-PROOF-001` existe para cazar.
- **Desconfiar de la precisión declarada.** Tentador (la accuracy mintió: 13 m mientras saltaba
  653 m en 6 s) pero es exactamente lo que `corroboratesDrive` ya resuelve por geometría, sin tener
  que adivinar cuándo miente el chipset.

## Evidencia nueva — field 28-08 (Redmi, FP dentro de casa)

Segunda aparición del mismo espejismo, esta vez mordiendo el ANCLA además del latch. Sesión
`1787871129368`, 01:11:04, usuario ya en el sofá:

```
01:11:04  ⊘ ignoring driving-speed fix with poor accuracy (speed=11.4 acc=81.835 > 50.0)
01:11:04  ⇢ SUSTAINED DEPARTURE — position ran 4205 m from the anchor at 5.3 m/s avg
          — credible drive by displacement [DET-CREDIBLE-DRIVE-001]
```

El MISMO fix, en el MISMO beat: rechazado como conducción por la puerta de accuracy y aceptado
como conducción por desplazamiento. La distancia era real porque el ancla estaba mal (congelada
mid-route — ver `det-refuted-stillness-cannot-mature-an-anchor-001`, la causa raíz de esa noche),
pero lo que este ticket retrata es independiente de esa raíz: **un único sample resolvió CAR y
limpió ancla congelada + stop + fase Notified + odómetro walk-in**, saltándose la doctrina de
`DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001` porque el carril `sustainedDeparture` puntúa en
`effectiveDriving` sin exigir racha (`pinnedAnchorRealDriveFixes` solo gobierna el carril Doppler).
Dos huecos concretos que el diseño debe cubrir además del latch:

- El gate de «moving» de `sustainedDepartureFromAnchor` (`fix.speed < movingBarMps → null`) confía
  en el Doppler declarado de un fix cuya accuracy acaba de suspender la vara de conducción. El KDoc
  dice «believes no single fix, not its speed field» — y el único testigo de movimiento aquí fue
  exactamente ese campo.
- Un ancla PINNED (descanso presenciado) no debería poder ser anulada por UN sample de ningún
  carril: la racha que ya existe para `isRealDrive` debe cubrir también el veredicto por
  desplazamiento.

## Criterio de éxito

- Replay del stream real de la madrugada del 27-08 (216 fixes, disponible en el `parkdiag` del Oppo):
  la sesión **no** debe latchear un viaje, y por tanto no debe llegar a `aborted_unattended_no_drive`
  ni lanzar prompt a las 2 de la mañana.
- Replay del Redmi 28-08 (sesión `1787871129368`), CON el fix de
  `det-refuted-stillness-cannot-mature-an-anchor-001` desactivado para aislar este guard: el
  espejismo de la 01:11:04 no debe limpiar un ancla congelada por sí solo.
- Regresión: un viaje corto real (el caso `SHORT_HOP`, field 2026-08-14) sigue probándose.
- Regresión: el guard hermano de la parada sigue disparando donde ya disparaba.

## Consumidores auditados

Pendiente — se hará al abrir la rama. `hasEverReachedDrivingSpeed` tiene muchos lectores y el
barrido es la mitad del trabajo de este ticket.
