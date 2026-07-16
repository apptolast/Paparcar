# DET-HONEST-CLOSE-001 — Cierre honesto de sesión (zona aproximada + prompt, nunca silencio)

> **Estado**: ESPECIFICADO — pendiente de rama `feature/DET-HONEST-CLOSE-001-honest-session-close`.
> Origen: auditoría rutas 2026-07-14/15 (El Puerto). Decidido con el user 2026-07-16.
> Prioridad: **P0** (junto a DET-ANCHOR-EGRESS-001 y DET-CREDIBLE-DRIVE-001).

## Problema (evidencia de campo)

Los dos aborts que dominan el campo terminan **en silencio total**, descartando evidencia de
salida verificada que la sesión ya tenía:

- **FN Camelias ruta-1** (D3 `1784056795594`, D2 `1784056956093`, 14-07 21:19 local): hop de
  ~300 m en ~2 min. El EXIT de la valla de Melgarejo se entregó con el fix **ya en Camelias**
  (exitLoc=36.59766,-6.25054 acc=24, d=264 m) — el viaje terminó antes de armar. La sesión nació
  viendo a un peatón: 23 pasos → `aborted_false_enter` **silencioso**, pese a `dep=self_observed`
  (el propio fix probó que el coche dejó la valla). Pin viejo rancio, coche sin pin, cero prompts.
- **FN regreso D2** (`1784081508556`, 15-07 04:11 local): EXIT tardío de la valla de Rosa de los
  Vientos (d=1099) entregado con el usuario **a pie/quieto** a 1,1 km (48 fixes acc 2,4-2,9, v=0).
  El abort `no_movement` fue CORRECTO (el coche no se había movido), pero **quemó el único
  nominador**: GMS quedó "outside" y no re-dispara; AR ENTER no llegó (ColorOS, 4 AM) → el viaje
  real a casa, minutos después, no armó NADA.

Los caminos que hoy preguntan casi nunca corren: `showMarkParkingNudge` solo existe en el
unattended-timeout (exige prompt previo — imposible si abortó a los 8 pasos) y en el
stale-pending del worker (solo si el proceso murió). La safety-net clasifica lejos+parado+pasos
como `None` por diseño ("never nag parked-and-away on foot"). Y el worker periódico se difiere
horas bajo Doze — por eso los prompts "aparecen al abrir la app".

## Doctrina

*Ninguna sesión armada con evidencia de salida verificada puede terminar sin exactamente una de
dos cosas: pin (conducción medida + ancla consistente) o prompt.* El prompt/artefacto se emite
**en el momento del abort, desde el FGS vivo** — jamás diferido a un worker que Doze retiene.
Extiende el contrato never-silent [feedback_detection_contract] al detector.

## Diseño — escalera al abortar (false_enter / no_movement) con `dep` verificado

El momento de decisión es el que ya existe: `aborted_false_enter` (8 pasos = andando probado) o
`aborted_no_movement` (4 min sin conducción) — no se añade timer nuevo; esos dos ya distinguen
parada-de-tráfico de viaje-terminado. Aplica a **ambos disparadores** (GEOFENCE_EXIT y
AR_VEHICLE_ENTER con dep verificado).

**Gate previo obligatorio — prueba de viaje** (el contraejemplo es el regreso de D2: el exitLoc
fresco estaba donde el PEATÓN, no donde el coche): la distancia valla-vieja→zona-nueva debe
DESBORDAR el presupuesto de pasos desde el aparcamiento anterior (mismo lenguaje que
`EvaluateSafetyNetCheckUseCase`: step budget, conjunción AR, física peatonal). La distancia sola
no distingue "conduje hasta aquí" de "vine andando" (BUG-WALK-DEPART-001).

1. **Prueba de viaje + ancla acotada** (centro de zona con accuracy de pin y lo andado desde el
   arm cabe en el presupuesto de zancadas — mismas condiciones que `backfillBounded` del
   reconcile) → liberar el pin viejo + **pin aproximado rel 0.5** + valla + notificación
   accionable "confirma/ajusta" con deep-link. Rel 0.5 ya garantiza que nunca se publica a la
   comunidad.
2. **Prueba de viaje sin ancla de calidad** → liberar el viejo (el reconcile lo autoriza) +
   **sesión-zona**: sesión `UserParking` con flag `approximate` — en UI es un ÁREA, no un punto;
   con valla registrada → la cadena de la siguiente salida no se rompe nunca + prompt para
   afinar. Si el usuario no responde, la zona se queda (valla sin pin exacto): si vuelve a
   conducir, el siguiente aparcamiento no se pierde.
3. **Sin prueba de viaje** (lo andado explica la distancia) → el coche NO se ha movido: silencio
   correcto, pin viejo intacto. Protege el recado a pie (no nagging).

**Centro de la zona**: combinación de exitLoc del trigger y primeros fixes del FGS, ponderada por
accuracy; radio = cubre ambos + presupuesto andado desde el arm. La sesión-zona es una sesión
normal del ciclo de vida (no un artefacto paralelo): janitor, TTL y safety-net la cubren igual.

## Reuso

- `EvaluateSafetyNetCheckUseCase` / lógica `backfillBounded` (el peldaño 1 ES el backfill del
  reconcile ejecutado at-abort en vez de esperar al worker de 15 min).
- `showMarkParkingNudge` / `showStillParkedPrompt` (canal ACTION high-importance ya correcto).
- Copy: causa+consecuencia+remedio, sin mecánica interna [feedback_no_internals_in_user_copy].
- Diagnóstico: evento nuevo de cierre (qué peldaño decidió y por qué) + evento `Released`
  (hoy los releases no dejan traza).

## Validación

Replay harness — fijar ANTES de tocar nada las 3 trazas de la auditoría:
(a) hop Camelias D3 `1784056795594` → debe acabar en peldaño 1 o 2 (zona en Camelias, Melgarejo
liberado, prompt visible), (b) regreso D2 `1784081508556` → peldaño 3 (silencio, pin de Rosa
intacto), (c) Enamorados D3 `1784131878857` → sin cambios aquí (lo cubre DET-ANCHOR-EGRESS-001),
verificar no-regresión. Field-test ambos móviles con hop corto deliberado.

## Criterio de éxito

Un hop de 2 minutos nunca vuelve a costar "pin viejo + coche perdido + silencio": o hay zona
aproximada con valla, o hay prompt inmediato — y la siguiente salida SIEMPRE tiene nominador.
