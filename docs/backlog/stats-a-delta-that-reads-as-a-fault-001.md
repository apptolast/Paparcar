# STATS-A-DELTA-THAT-READS-AS-A-FAULT-001 · «▲ 2200 %» es verdad y parece una avería

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Abierto:** 2026-09-15 · desde master `ef49abdc`

## Problema

Visto en el Redmi al mirar el resumen del mes: **«buscando plaza 46 min ▲ 2200 %»**. La cifra es
correcta —de 2 a 46 minutos son 23 veces más— y aun así el usuario lee un error de la app, porque
nadie habla así: se dice «veintitrés veces más», no «dos mil doscientos por ciento».

Y no es del resumen. Sale de `percentDelta`, que existe desde [STATS-UI-001] y alimenta **todos los
tiles** de la tarjeta de cifras: cualquier magnitud que arranque cerca de cero produce el mismo
número absurdo en cuanto el periodo siguiente tiene datos de verdad — que es exactamente lo que pasa
el segundo mes de uso de la app.

## Doctrina violada

- **Sistemas, no parches**: la regla está escrita **dos veces** — `percentDelta` en
  `VehicleStatsBlock` y `recapDelta` en `StatsMonthRecapCard`, idénticas hasta en el `if` del cero.
  La segunda nació hace unas horas, en F6, copiando a la primera. Un invariante con dos copias es un
  invariante que ya ha empezado a divergir.
- **Copy sin jerga ni mecánica**: un porcentaje de cuatro cifras no informa, alarma.

## Diseño

**Una sola función pura**, `presentation/util/statDelta(current, previous)`, y las dos cards la
llaman. Las negativas que ya tenía se conservan enteras (sin anterior, anterior cero, o movimiento
que redondea a nada → no hay delta).

**Lo nuevo: un crecimiento grande se dice en VECES, no en por ciento.** A partir de
[MULTIPLIER_FROM] veces, el texto pasa a `×3`, `×23`. Por debajo, el porcentaje de siempre.

- Sólo hacia ARRIBA: una bajada nunca pasa de −100 %, así que «96 %» ya se lee bien y no necesita
  otra forma.
- Bajo diez, una decimal (`×2,5`); a partir de ahí, entero — media vez importa cuando son dos y no
  cuando son veintitrés.

## Criterio de éxito

- **Test**: 2 → 46 min da `×23` y no `2200 %`; 100 → 130 sigue dando `30 %`; 46 → 2 da `96 %`.
- **Test**: las tres negativas (sin anterior, cero, cambio nulo) siguen devolviendo null.
- **Test**: el mismo resultado desde la ficha y desde el resumen — porque es la misma función.
- Suite (`testAll`) + guardarraíles + compila mock/prod/iOS.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `VehicleStatsBlock.percentDelta` | 🔧 borrada; llama a la compartida |
| `StatsMonthRecapCard.recapDelta` | 🔧 borrada; llama a la compartida |
| `PapStatDelta` (ui/components) | ✅ sin cambios: sigue llevando DIRECCIÓN + texto ya formateado |
| Cualquier otro delta | ✅ `grep` de `PapStatDelta(` → sólo esos dos sitios lo construían |

## Verificación (15-09-2026)

- `testAll` verde. Testigo: `StatDeltaFormatTest` tests="7" failures="0" — incluye el caso del
  ticket (2 → 46 min = `×23`), que una bajada sigue en porcentaje, el umbral exacto y las tres
  negativas de siempre.
- Guardarraíles con `--rerun-tasks` a cero · mock/prod/iOS compilan.
- **En device (Redmi, mock, sha256 verificado)**: «buscando plaza · 46 min ▲ ×23».

⚠️ **El test cazó un `×3.0`**: el ratio exacto de 3 caía en la rama decimal y pintaba un cero a la
derecha — el mismo número con pinta de roto que este ticket viene a quitar, sólo que más pequeño.
