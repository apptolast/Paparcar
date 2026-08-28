# UI-JUST-PARKED-READS-AS-NOW-001 · "hace 0 min" en los peeks se lee como "ahora mismo"

**Estado:** ✅ Done · mergeado a master (squash)

## Problema
Captura del user (28-08, Redmi): el peek de aparcamiento propio muestra "Aparcado hace 0 min"
durante el primer minuto de la sesión. Un contador a cero no es información — el momento "recién
aparcado" merece leerse como lo diría una persona: "ahora mismo".

## Doctrina violada
Ninguna regla dura; es calidad de copy. Sí hay una incoherencia de sistema: el formateador general
`relativeTimeText` (`TimeStringComposables.kt`) YA resuelve este caso con `relative_time_just_now`
("ahora mismo" / "just now"), pero los peeks calculan sus minutos a mano y se saltan esa rama.

## Señales / datos disponibles
- `ParkingDurationRow` (`ParkingPeek.kt`) — `elapsedMin` puede ser 0 → "Aparcado hace 0 min".
- `SpotPeek` age (`SpotPeek.kt`) — `spotAgeMin` puede ser 0 → "Publicada hace 0 min". Mismo defecto.
- Vocabulario ya establecido en los 9 locales: `relative_time_just_now`.

## Diseño
No se puede reutilizar `relative_time_just_now` a secas porque estos textos llevan el participio
("Aparcado…", "Publicada…") y cada locale ordena la frase a su manera. El invariante queda en los
dos formateadores locales: rama `< 1 min` → string propio "just now" por superficie, redactado con
el mismo idiom que `relative_time_just_now` en cada locale.
- `home_peek_parking_duration_now` — "Parked just now" / "Aparcado ahora mismo"
- `home_peek_spot_age_now` — "Posted just now" / "Publicada ahora mismo"

## Criterio de éxito
Recién aparcado, el peek dice "Aparcado ahora mismo"; al cumplirse el primer minuto pasa a
"Aparcado hace 1 min". Igual para la edad de plaza ("Publicada ahora mismo").

## Consumidores auditados
Grep de todo el que imprime "hace X" con minutos crudos:
- `ParkingPeek.kt` `ParkingDurationRow` → **cubierto** (esta tarea)
- `SpotPeek.kt` edad de plaza → **cubierto** (esta tarea)
- `BrowsePeek.kt` → usa `compactRelativeTimeText` → ya emite "<1m" → exento
- `ConfirmationBottomSheet.kt` `detectionMethodLine` → usa `compactAgo` → exento
- `relativeTimeText` / historial → ya tiene rama `just_now` → exento
