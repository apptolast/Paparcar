# UI-SPOT-CLOCKS-NEVER-READ-ZERO-001 · los relojes de una plaza nunca dicen "0"

**Estado:** ✅ Done · mergeado a master (squash)

## Problema
Barrido posterior a UI-JUST-PARKED-READS-AS-NOW-001 (`b4bdd286`). Dos restos del mismo defecto
"contador a cero", ambos en la plaza comunitaria:

1. **El "Publicada ahora mismo" del peek de plaza es código muerto.** `ageMinutes`
   (`SpotPeek.kt`) devuelve `null` cuando la edad es 0 min, así que durante el primer minuto la
   línea de edad no se pinta y la rama `< 1 min` añadida en el ticket anterior nunca ejecuta. Una
   plaza recién publicada es la MÁS valiosa (rampa de frescura) y es justo la que no dice nada.
2. **El badge de TTL del marcador puede decir "0 min".** `TTLIndicator` (`SpotIndicators.kt`)
   hace floor con `coerceAtLeast(0)` y solo pasa a "caducada" cuando el tiempo llega a cero:
   durante el último minuto de vida muestra "0 min" en rojo.

## Doctrina violada
Ninguna regla dura; misma calidad de copy que el ticket padre: un contador a cero no informa —
el borde inferior de un reloj se dice como lo diría una persona ("ahora mismo", "<1 min").

## Diseño
- `ageMinutes` devuelve `0` en vez de `null` para edad < 1 min (el `null` queda solo para
  timestamp inválido o reloj adelantado). Con eso la rama `home_peek_spot_age_now` ya mergeada
  cobra vida sin más cambios.
- `TTLIndicator`: rama nueva para "queda menos de 1 min y no ha caducado" → string
  `spot_indicator_ttl_under_minute` ("<1 min"), mismo formato que su hermano "%1$d min".
  El color ya era rojo en ese tramo (umbral crítico ≤3 min) — solo cambia el texto.

## Criterio de éxito
- Plaza recién publicada → el peek muestra "Publicada ahora mismo"; al minuto, "Publicada hace 1 min".
- Marcador con <60 s de vida → badge "<1 min" en rojo; al expirar, "Caducada".

## Consumidores auditados
- `ageMinutes` es privada de `SpotPeek.kt` con un único call site (la línea de edad) → cerrado.
- `remainingMinutes` (peek, "Expira en X min") se oculta bajo 1 min — no imprime 0 → exento
  (que desaparezca en el último minuto es discutible, pero no es este defecto).
- `relativeTimeText` / compactos / walk / drive / countdown mm:ss → ya correctos (ticket padre).
