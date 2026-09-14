# TRIP-A-JOURNEY-IS-A-CHAIN-OF-TRIPS-001 · Un viaje largo es una cadena de trayectos, y el historial debería contarlo así

**Estado:** 🔵 Abierto — diseño de UX pendiente de decidir con el user. Sin rama.
**Abierto:** 2026-09-14 (fase 7 del pivote; separado de TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001
a propósito: la métrica era mecánica, esto es editorial)

## Problema

Madrid→Valencia con repostaje y comida son TRES trayectos en el historial — y los tres son
correctos: cada parada con disconnect + alejamiento ES un aparcamiento, y el detector tiene razón
(decisión de la sesión 14-09: *no se resuelve el conflicto — no existe; la semántica de "viaje" es
una PROYECCIÓN de presentación*). Lo que falta es que el historial cuente la historia entera:
"ayer: viaje a Valencia (3 etapas, 402 km)" en vez de tres filas sueltas.

## Principios ya decididos (14-09)

- El detector NO se toca. Cero estado nuevo, cero campos nuevos: la cadena es una función pura
  `List<UserParking> → List<Journey>` sobre lo ya persistido.
- **Trayecto** = unidad de medida (inter-parking, lo que ya existe). **Viaje** = unidad de
  presentación: cadena de trayectos del mismo vehículo con parada < umbral (~45–60 min,
  constante nombrada calibrable).
- Peaje/atasco (sin disconnect) → mismo trayecto, ya cubierto. Repostaje corto que confirma park →
  la cadena lo absorbe como etapa.

## Por decidir con el user (por eso está parado)

1. **Dónde se enseña**: ¿agrupación con cabecera en la lista del Historial? ¿solo en el detalle?
   ¿una fila-resumen colapsable con las etapas dentro?
2. **Qué agrega el viaje**: km sumados de etapas medidas (con la honestidad de
   UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL), duración total, ¿coste?
3. **Umbral de parada** (45 vs 60 min) y si una parada nocturna (>N h) corta SIEMPRE.
4. Interacción con el chart semanal y las stats (¿los viajes cuentan como 1 o como N?).

## Criterio de éxito (provisional)

Función pura testeada + la superficie de UI que se decida, con galería/mock en paridad y strings
en 9 locales — todo en la misma tarea cuando se abra.
