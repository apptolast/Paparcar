# DET-STAGE-RESPONSE-TIMEOUT-001 · P3.4 — la rama más grande era, sobre todo, una línea de traza

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-RESPONSE-TIMEOUT-001-p3-4` ·
worktree `../Paparcar-stage-4`

Paso **P3.4**, el más grande de los diez según el plan (~+140/−120). Sigue a `2489305a` (P3.3).

## Qué mueve

[DET-RECONCILE-001] Al usuario se le preguntó y no contestó: la sesión **guarda, no descarta**.

El prompt solo sale tras un viaje real, una parada real y una señal de vehicle-exit, así que el
aparcamiento casi con seguridad ocurrió y lo único que falta es un toque humano. Tirarlo le cuesta al
usuario su coche —incidente de campo 2026-07-06, Redmi: un aparcamiento real perdido por una
notificación que nadie vio— mientras que guardarlo mal cuesta un toque de corrección.

## Lo que destapó la mudanza: cuánto de la rama más grande era decisión

Sus ~110 líneas se descomponen así:

| | |
|---|---|
| la puerta del timeout | 2 líneas |
| **el veredicto** | 1 llamada, a un caso de uso que ya existía |
| el dispatch de 3 ramas | ~35 líneas, casi todo efectos |
| **la traza de `parkdiag`** | **12 líneas con quince valores interpolados** |

La precedencia de siete vías que vivía aquí inline —no-drive → unpinned → egress-mismatch → gap →
walk-entered → vehicular-egress → exact— **ya se había ido** a `EvaluateUnattendedParkingSaveUseCase`,
que es por lo que la conducción de 25,6 min completamente medida del Redmi dejó de acabar sin ningún
pin [DET-WALK-ENTERED-ANCHOR-ZONE-001].

Así que la etapa es delgada por construcción, y la traza que emite pasa a ser una función con nombre
cuyo único trabajo es ser **palabra por palabra** lo que era.

## Dos efectos se quedan compuestos, y por la misma razón las dos veces

Lo que pasa cuando el guardado **FALLA** es una propiedad de ejecutarlo, no una segunda decisión que
la etapa pudiera tomar sin saber si la primera funcionó:

- **`SaveZone`** cae de vuelta al *ask* que nombra su propia razón, para que el usuario reciba la
  oferta en vez de silencio.
- **`SaveUnattended`** termina la sesión incluso si un guard lo degrada a otro prompt más: el usuario
  ya ignoró uno durante la ventana entera, y terminar es la única salida que no hace bucle
  [BUG-STUCK-SESSION].

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes.

**1.629 tests**, 0 fallos. `assembleMockDebug` ✅.

**Cuatro de diez etapas movidas.** Siguiente: **P3.5**, `PreDriveSkipStage` — la más pequeña de
todas (dos líneas: sin conducción, nada que decidir), que es buen sitio para comprobar que el andamio
ya no necesita más correcciones.
