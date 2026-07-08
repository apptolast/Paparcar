# DET-BREADCRUMBS-001 — el rastro del viaje: el pin cae donde paró el COCHE

## Problema (field 2026-07-07/08)

Cuando la salida se pierde en vivo y la resuelve el reconcile, la posición del aparcamiento nuevo
se estima con **el fix del despertar** (backfill) o con **donde está el teléfono al decidir**
(guardar-al-timeout) — es decir, donde está el USUARIO, no donde paró el COCHE. Incidente
2026-07-08 04:41: sesión guardada en la casa del user con el coche real a ~200 m. El único testigo
de dónde paró el coche es **el viaje mismo**: el último fix a velocidad de conducción antes de la
parada.

## Insight de sistema

**Ya muestreamos el rastro y lo tiramos.** Cada despertar del safety-net (sig-motion dispara cada
pocos segundos DURANTE el movimiento — visto en campo 22:41:44/58/42:07), cada check gated
(AR/ENTER/BT/exit-stale) y cada attempt del departure worker toman UN fix fresco con velocidad y
precisión… y lo descartan tras decidir. Persistirlos es un rastro gratis: cero mecánica nueva de
OS, cero batería extra, sobrevive a kills (disco).

## Diseño

### F1 — Trail persistente (ring buffer en disco)
- Cada fix fresco muestreado por `GetOneLocationUseCase` desde el safety-net / departure worker /
  pre-arm se anexa a un ring buffer en disco: `(lat, lon, speed, accuracy, timestamp, source)`.
- Tamaño acotado (p. ej. últimos 60 puntos) + poda por edad (p. ej. > 12 h fuera). SharedPreferences
  o fichero pequeño en el mismo prefs de anclas; escritura O(1) por tick.
- Solo fixes que pasaron el gate de frescura (los rancios ya se rechazan hoy).

### F2 — El evaluador consume el trail (dos usos, pura lógica testeable)
1. **Colocación del backfill**: en dispatch preconfirmado, el aparcamiento nuevo se coloca en el
   PUNTO DE PARADA del trail — el último fix con velocidad de conducción creíble
   (`isCredibleDrivingSpeed`), o el primer fix del clúster estacionario actual — con fallback al
   fix del despertar cuando el trail no cubre (agujeros por inanición GPS = excusa OS, contrato).
2. **Cuarta prueba de viaje**: trail contiene un fix a velocidad creíble posterior al sello del
   ancla → el desplazamiento fue un viaje (dated a ese fix). Cierra el último silencio del
   evaluador: far+estacionario sin ancla/pasos/AR pero con conducción observada en el rastro.

### F3 (medir antes de construir) — densificador pasivo
`FusedLocationProvider.requestLocationUpdates(PRIORITY_PASSIVE, PendingIntent)` mientras hay
sesión aparcada: recibe los fixes que OTRAS apps ya calculan (Maps navegando, etc.), coste cero,
sobrevive al proceso vía PendingIntent. Limitado por el throttling de background location (pocas
entregas/hora sin apps activas). Solo si el field-test de F1+F2 muestra agujeros sistemáticos.

## Qué NO es
- No es un servicio de tracking continuo (batería/OEM-kill: justo lo que evitamos).
- No sustituye al coordinator en viajes trackeados en vivo (ahí `bestStopLocation` ya es preciso).

## Casos de campo que resuelve
- 04:41 (Oppo): el trail tendría los fixes del viaje del día anterior… no — ese viaje fue sin
  despertares con fix (inanición). Honesto: lo resuelve F3 o un tick con GPS vivo; F1+F2 resuelven
  el caso común (sig-motion despierta durante el viaje con GPS respondiendo, como el 22:41-22:46
  del Redmi/Oppo en el cine: fixes EN el cine minutos después de parar).
- Cine 22:41: backfill habría caído en el fix del cine (correcto) — F2.1 lo formaliza y F2.2
  habría despachado incluso sin ancla.
