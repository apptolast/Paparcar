# DET-A-DEPARTURE-HAS-NO-PROVENANCE-001 · sabemos quién puso cada pin y no quién detectó cada marcha

**Estado:** 📋 Abierto · sin rama · descubierto el 14-09-2026 durante
[DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001](det-every-trigger-reports-what-it-produced-001.md)

## Problema

Esta app consigue **dos** cosas: coloca un pin (aparcamiento) y libera una plaza (marcha). La
primera lleva procedencia desde [DET-PIN-PROVENANCE-001] — `detectionPath` (cómo se confirmó) y
`armEvidence` (qué la armó), persistidos en cada `UserParking` y legibles en Firestore. **La segunda
no lleva ninguna.**

Se ve al intentar contarla. Al cablear la mitad de salida de la estadística de productividad, el
único par de etiquetas disponible en la sesión resultó describir **la llegada**, no la marcha:
estamparlo en `AnalyticsEvent.SpotReleased` habría etiquetado la plaza liberada con la procedencia
del viaje que la ocupó. Una cifra falsa es peor que ninguna, así que ese lado se quedó fuera y esta
ficha es la deuda.

La consecuencia va más allá de la métrica: hoy, ante una plaza fantasma en el mapa, **no hay campo
que diga qué la publicó**. Hay que triangular desde los logs de diagnóstico de un usuario opted-in,
que es justo la situación que `detectionPath` existe para evitar del lado del pin — y la que
[DET-PIN-PROVENANCE-001] ya declaró inaceptable una vez ("un pin sin provenance no es
diagnosticable en remoto", lección del 20-07).

## Doctrina

**⛔ Todo pin persiste su `detectionPath` + `armEvidence`.** La regla está escrita para el pin, y la
plaza publicada es igual de visible para desconocidos — más, porque la ve gente que no es su dueño.
La regla no se amplió a la marcha porque la marcha no tenía dónde guardarlo: `Spot` es un documento
comunitario, no la sesión.

## La materia prima existe, dispersa

Todo lo que haría falta ya se calcula y se tira:

- **`DepartureProof`** (`Witnessed` / `Deduced`) — el grado de prueba, decidido en
  `RunDepartureCheckUseCase` y pasado a `ProcessConfirmedDepartureUseCase`.
- **`preconfirmed`** — si el reconcile del safety net ya había probado la marcha (presupuesto de
  pasos / boarding AR / física peatonal).
- **El trigger que despachó** — geocerca (borde o lejana), AR mid-trip, safety net, "Me voy" del
  usuario, promoción de una deducción.
- **`departedAtMs`** — desde
  [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] (`488d6982`), el instante MEDIDO de la
  marcha o null. Es, además, el primer campo de este grupo que ya viaja por toda la cadena.

Cinco caminos distintos acaban publicando, y el documento publicado no distingue ninguno.

## Diseño (esbozo, sin cerrar)

Un `departurePath` con el mismo estatuto que `detectionPath`, decidido en un sitio y estampado en
los dos consumidores que lo necesitan:

- **En `UserParking` al cerrar la sesión** — junto a `endedAtMs` y `publishedSpot`, que ya se
  escriben ahí. Es lo que hace diagnosticable el historial del propio usuario.
- **En el `Spot` publicado** — es lo que hace diagnosticable la plaza fantasma sin cable.
- **En `AnalyticsEvent.SpotReleased`** — cierra la mitad que este ticket dejó abierta y completa la
  pregunta del user: *con qué sistemas conseguimos detección*, en sus dos sentidos.

⚠️ Decidir antes de escribir código: si `departurePath` es **un** campo o **dos** (el par
camino+prueba, como el pin). El pin necesitó dos porque armar y confirmar son momentos distintos;
en la marcha, `DepartureProof` y el trigger podrían ser exactamente ese mismo par —
conviene mirarlo antes que decidirlo.

⚠️ Tocar `Spot` es superficie de serialización: DTO ⇄ Entity ⇄ dominio end-to-end, con la paridad de
deserializadores que ya vigila `androidUnitTest`. No es un campo gratis.

## Criterio de éxito

- Una plaza del mapa dice, en su propio documento, qué la publicó — sin diagnósticos encendidos.
- `spot_released` se puede desglosar por sistema, igual que `parking_session_started`.
- Las cinco vías de publicación son distinguibles en una traza de campo.
