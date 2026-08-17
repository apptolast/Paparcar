# DET-BIKE-DEPARTURE-RELEASE-001 · un paseo en bici sigue declarando que el coche se ha ido

**Estado:** ⚪ Abierto, sin código · follow-up deliberado de [DET-BIKE-NOT-A-CAR-001](det-bike-not-a-car-001.md)

## Problema

DET-BIKE-NOT-A-CAR-001 impide que un paseo en bici **plante** nada (ni pin exacto ni zona). No impide
que **suelte** la plaza real al empezar el paseo.

Field 16-08, Samsung `sUGo7EYl16XDtosI8Ei7LFeAo2E2`, sesión `1786878499475`: el arm fue
`GEOFENCE_EXIT (geof=0575e3e8 d=352m acc=12m exitLoc=36.5790416,-6.21865 **dep=verified_speed**)`.
`VerifyDepartureEvidenceUseCase` sella `verified_speed` a partir de `minimumDepartureSpeedKmh` = 10
km/h; una bici lo supera sin esfuerzo. Consecuencia: el coche se marca como "en ruta", la plaza de
Calle Toledo se da por libre y el usuario pierde la referencia de dónde está su coche aunque no se
pinche nada nuevo.

## Doctrina implicada

*Fallo asimétrico.* Soltar una plaza que sigue ocupada es menos grave que plantar una fantasma, pero
sigue siendo una pérdida de dato para el usuario y una plaza fantasma para la comunidad.

## Por qué se dejó fuera de alcance

El veto de AR llega con **hasta ~2 minutos de latencia**; el verdicto de salida es inmediato y
síncrono con el EXIT. No se puede simplemente consultar el latch de bici en `VerifyDepartureEvidence`
porque en ese instante todavía no existe. Hace falta un mecanismo distinto: o bien una
**reconciliación tardía** (cuando llega el `ON_BICYCLE` ENTER, revertir una salida declarada en los
últimos N minutos y restaurar la plaza), o bien retrasar la publicación de la plaza liberada hasta
tener una ventana de AR. Ambas cosas tocan el pipeline de departure/release, que es otro sistema.

Meterlo en el mismo ticket habría mezclado dos invariantes distintos ("qué puede confirmar" y "qué
puede liberar") en un cambio ya amplio.

## Diseño candidato (sin decidir)

- Reutilizar `notifyDepartureConfirmed()` como precedente: ya existe una vía de **upgrade** post-arm.
  Aquí haría falta la simétrica, un **downgrade**: `notifyDepartureRefuted(reason)`.
- Al recibir `ON_BICYCLE` ENTER con una salida declarada hace < `humanPoweredRideMemoryMs`:
  restaurar la sesión anterior como activa y retirar la plaza publicada.
- Cuidado con el caso legítimo bici→estación→coche: la supersesión por `IN_VEHICLE` ENTER posterior
  ya está resuelta en `EvaluateHumanPoweredRideUseCase` y debe reutilizarse, no reimplementarse.

## Criterio de éxito

- Carlos repite el paseo a los Toruños y, al volver, el coche sigue aparcado en Calle Toledo **y**
  la plaza nunca llegó a publicarse a la comunidad.
- Un viaje real en coche sigue liberando la plaza con la latencia de hoy (sin regresión de tiempo).

## Registro

- 2026-08-17 — abierto como follow-up explícito al implementar DET-BIKE-NOT-A-CAR-001.
