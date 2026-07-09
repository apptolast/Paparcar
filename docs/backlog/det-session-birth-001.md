# DET-SESSION-BIRTH-001 · Regresión de salida 2026-07-08 — evidencia anclada al nacimiento de la sesión

**Origen:** field-test 2026-07-08 tarde+noche (Redmi + Oppo, viajes casa→playa→casa y casa→cine→copas→casa).
Tres fallos de campo con una misma causa raíz y dos huecos estructurales. Auditoría completa de
fiabilidad de triggers en los logs parkdiag de ambos devices (17:55 → 01:31).

## Datos de fiabilidad medidos (24 h de campo)

| Trigger | Redmi (MIUI) | Oppo (ColorOS) | Veredicto |
|---|---|---|---|
| Geofence EXIT | 5 entregas: 203–4684 m de su valla | 4 entregas: 187–5773 m | Dispara SIEMPRE, pero 5/9 llegan >300 m (inútiles como evento en vivo) |
| AR ENTER/EXIT | 38+6 eventos; re-entregas rancias (lag mediana 15 min, hasta 109 min) pero los reales llegan frescos (2–8 s) | 10+5 eventos, lag mediana 4 s, cero rancios | **La señal más completa en ambos** — narró todos los viajes. Trampa: re-entregas → comparar SIEMPRE trueTime |
| WorkManager | ticks toda la noche, huecos ≤38 min | 7 ticks en 7,5 h; congelado 91 y 95 min seguidos | En ColorOS NO es trigger, es conserje tardío. Los receivers SÍ corren durante los freezes |
| Sig-motion | 28 disparos | 39 disparos | Despertador fiable, discriminador nulo (anda=conduce) |
| GPS one-shot | 69 fixes, 28 con speed=0; conduciendo: **100 % speed=0** (acc 21–64) | 51 fixes, 25 TIMEOUT; el decisivo (20:41) speed=0 acc=37 | **Un fix one-shot ve POSICIÓN, jamás VELOCIDAD** (caché fused). La distancia sí es fiable |
| Step counter | congelado en ~50 todo el día + TIMEOUTs | funciona (87→3402) con timeouts esporádicos | En Redmi mudo Y venenoso (delta 0 conduciendo) |
| Coordinator vivo | 4/4 aparcamientos clavados (0.9) | — | El ÚNICO componente con 100 % de acierto. Solo falla quien lo arma |

## Los 3 bugs (misma raíz: evidencia sin filtro de nacimiento)

1. **Fall-through con embarque PRE-sesión (Redmi 18:52→18:54).** Parking correcto guardado; MIUI
   re-entrega el ENTER del viaje de IDA (trueTime 17 min); EXIT andando a 286 m; 4 intentos
   Inconclusive (gate OK) pero el fall-through solo miraba `lastVehicleEnteredAt != null` (el
   comentario decía "after parking was confirmed"; el código no lo comprobaba) → parking borrado +
   plaza fantasma + pin en la playa por timeout. El pre-arm (`VerifyDepartureEvidence`) tenía el
   mismo agujero (`dep=verified_enter` → seed).
2. **Despacho preconfirmed sin handoff (Oppo 20:41).** El evaluador detectó la vuelta en marcha
   (embarque ≥ seal), borró la sesión de la playa… y nadie escuchaba al aparcar (AR EXIT 20:46).
   `if (!preconfirmed)` dejaba el arranque del tracking solo para el camino live.
3. **Trail sin cota temporal (Oppo 23:18).** La prueba-por-trail de F2 casó migas del viaje de la
   TARDE contra la sesión manual de las 21:33 → salida fechada 18:31 ("stale age=286min" ✓ del log)
   y **backfill en la miga de las 18:47 = pin en la escalera de la playa**, con el usuario a 4 km.

Y el hallazgo estructural del Redmi en el cine: 4 despertares con posición buena (d=920→1470 m) y
TODAS las pruebas vetadas a la vez (fixes speed=0, contador mudo, punto-cero de pasos borrado por un
TIMEOUT en el re-seal de 21:14, ancla caducada por reloj a las 21:57) — mientras EXIT 21:42 + ENTER
21:43 + distancia creciente contaban la historia completa y el cerebro no tenía esa prueba.

## Los cambios (invariantes, no parches)

- **[DET-SESSION-BIRTH-001]** *Una evidencia solo es admisible para la sesión S si su trueTime ≥
  nacimiento de S* (`session.location.timestamp`). Aplicado en los 4 consumidores: evaluador
  (boarding + exit), `DetectParkingDepartureUseCase` (bindea la sesión que ya cargaba;
  `Inconclusive(admissibleBoarding)`), fall-through de `RunDepartureCheckUseCase` (vía la decisión),
  `VerifyDepartureEvidenceUseCase` (param `sessionStartMs`). Mata bugs 1 y 3 y los EXIT/ENTER
  zombis (4 AM del 08-07).
- **[DET-CONJUNCTION-001]** El EXIT rancio se PERSISTE como hecho (`exit_delivered_<geofenceId>` en
  prefs del safety-net, escrito por el triage del service) y el evaluador gana la prueba de
  conjunción: `exit admisible ∧ boarding admisible ∧ |Δ| ≤ exitEnterPairWindowMs (5 min)` con
  contador mudo → despacho preconfirmed fechado al embarque, SIN ancla (la valla ES la mitad
  posicional). Campo: Redmi 73 s, Oppo 3 m 55 s. Andar-y-bus rompe el emparejamiento (la valla se
  rompe minutos antes del bus); el contador vivo sigue mandando sobre todo.
- **[DET-ARRIVAL-HANDOFF-001]** Todo despacho termina en exactamente uno: backfill (pasos acotados
  Y fix acc ≤ 50 — un fix acc=300 plantaba pines en marcha) O tracking vivo O, si el FGS se
  deniega, prompt "¿sigues aparcado?". Nunca en silencio. Mata bug 2.
- **Gate far con margen de precisión:** lejos = `d − acc > 300 m` (el fix acc=100 de las 04:18 ya
  no llega a "far"; el acc=64 legítimo a 1222 m sí).
- **Fuente `ar-exit`:** IN_VEHICLE EXIT encola check (los receivers sobrevivieron a TODOS los
  freezes de ColorOS; ambas llegadas al cine se anunciaron por AR EXIT en <2 min y nadie escuchó).
- **F2 de DET-BREADCRUMBS REVERTIDO** (prueba-por-trail y `backfillAt`/stopPoint fuera): la
  colocación de llegada era del coordinator y funcionaba; además el trail NUNCA ve migas
  conduciendo (speed=0 en one-shots) — era peso muerto que solo disparó una vez, y mal. F1 (la
  grabadora `TripTrail`) se queda: es forense puro y fue lo que permitió reconstruir la noche.
  **F3 descartado con datos**: un listener pasivo recibiría los mismos fixes speed=0 y en ColorOS
  nada corre para consumirlos.

## Replays fijados en tests (números reales de los logs)

- Redmi 18:52: ENTER pre-sesión → fall-through Dismissed, parking sobrevive, 0 spots (Run+Detect+Verify).
- Redmi 21:42–21:45 cine: conjunción EXIT∧ENTER a 72 s, fix acc=64 → despacho fechado al embarque.
- Bus-tras-andar: |Δ|=18 min → None; pasos vivos que dicen "andado" anulan la conjunción → None.
- EXIT/ENTER pre-sesión en la conjunción → None (valla envenenada de vida anterior).
- Salida rancia (5 h) → limpia SIN publicar (sesión anterior al exit, como en la realidad).

Suite completa verde · prod + mock compilan.
