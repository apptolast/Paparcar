# STATS-PLUS-REMEMBERS-AND-REPORTS-001 · El mes que se acaba de cerrar

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `d33fe08c` antes de mergear, sin conflictos. **Cierra el roadmap F0-F6.**
**Abierto:** 2026-09-15 · sobre F5 · **F6 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

Todas las superficies de estadísticas hablan del periodo que el usuario ELIGE con un chip. Ninguna
dice nada por su cuenta, y por eso nadie se entera nunca de cómo le fue el mes: el 1 de octubre el
panel enseña «Este mes» con cero aparcamientos, y septiembre —el mes del que sí había algo que
decir— se queda dentro del filtro «3 meses», mezclado con agosto.

Es exactamente el eje que este proyecto vende como Plus: **free pregunta y la app responde; Plus
recuerda por ti** [PLUS-ALERTS-AND-TIERS-001]. Un resumen de mes cerrado es la app hablando primero.

## Doctrina violada

Ninguna. Las que manda respetar:

- **Una superficie, un alcance** [STATS-UI-001]: esta card NO habla del periodo del filtro, así que
  lo dice en su propio título («Septiembre, cerrado») y no toca ninguna otra cifra de la pantalla.
- **Gating soft y VER nunca se gatea** [MONEY-001]: no hay gate, y no hace falta. La ventana libre
  son 7 días, así que **un mes cerrado no existe para free por construcción** — igual que los
  insights. Nada se esconde, nada se tapa con un velo.
- Cobertura, umbrales y comparabilidad: los de siempre, heredados sin reescribir.

## Diseño

1. **`domain/stats/ClosedMonth.kt`** (puro, con zona horaria inyectable):
   - `closedMonthWindow(nowMs, tz)` → `[inicio, fin)` del mes ANTERIOR;
   - `previousToClosedMonthWindow(...)` → el de antes, que es contra quien se compara;
   - `isRecapFresh(nowMs, tz)` → sólo durante los primeros [RECAP_FRESH_DAYS] del mes nuevo. Un
     resumen de septiembre el 27 de octubre no es un resumen, es un recuerdo — y ocupa el sitio de
     lo que sí está pasando.
2. **El agregado se reutiliza entero**: las sesiones del mes cerrado pasan por
   `ComputePeriodStatsUseCase` con las del mes anterior como `previousSessions`, así que todas las
   cifras, sus umbrales y sus ▲/▼ salen del mismo sitio que las del resto del panel.
3. **La card va ARRIBA del todo**, encima de los insights: es lo único de la pantalla que el usuario
   no ha pedido, y si está es porque acaba de pasar.
4. **Sin notificación en este ticket.** Avisar exige WorkWorker, canal, permiso y una decisión de
   cuándo molestar; eso es su propio ticket y su propio riesgo. Aquí se construye lo que la
   notificación llevaría dentro, que es el orden correcto: primero la pantalla que se abre, después
   el aviso que lleva a ella.

## Criterio de éxito

- **Test**: el 3 de octubre el mes cerrado es septiembre completo; el 27 de octubre ya no se enseña.
- **Test**: en enero, el mes cerrado es diciembre del año anterior (el cambio de año no lo rompe).
- **Test**: sin sesiones en el mes cerrado no hay card.
- Suite + guardarraíles con `--rerun-tasks` + 9 locales + galería (mes cerrado con y sin datos).
- Compila mock, prod e iOS.

## Lo que queda de F6 después de esto

- **La notificación** del resumen (`STATS-PLUS-THE-RECAP-KNOCKS-001`, sin abrir).
- **Tendencia a 12 meses**: el gráfico tiene tope de 6 (`ALL_MONTHS_CAP`), y subirlo es una decisión
  de producto sobre el filtro «Todo», no un número.
- **Insight de vigilancia** («esta zona te cuesta 4 min más que hace un mes»), que necesita comparar
  racimos entre DOS periodos — hoy los racimos se calculan de uno solo.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `ComputePeriodStatsUseCase` | ♻️ se reutiliza tal cual, con otra ventana |
| `VehicleHistoryCalculator` | ✅ exento: el mes cerrado no es un `HistoryFilter`, y meterlo como uno pondría en el chip un periodo que nadie elige |
| `StatsScreen` / `StatsState` / `StatsViewModel` | 🔧 una card más, calculada en la misma resolución |
| Room / DTO / sync | ✅ exentos |

## Desviación sobre el diseño

**El título no nombra el mes.** «Septiembre, cerrado» exigiría doce nombres largos en nueve idiomas
—108 strings— para un titular; y como la card sólo se compone durante la primera semana del mes
nuevo, **«El mes pasado» no tiene ambigüedad ninguna**. El alcance sigue dicho en su propio título,
que es lo que la doctrina pedía.

## Verificación (15-09-2026)

- Suite completa verde. Testigo: `ClosedMonthTest` tests="6" failures="0" — el mes cerrado completo,
  **el cambio de año en enero**, el tramo de comparación pegado sin solaparse, la caducidad tras la
  primera semana y **la zona horaria del usuario** (a las 00:30 del día 1 en Madrid, UTC sigue en el
  mes viejo y el resumen hablaría del mes equivocado).
- Guardarraíles con `--rerun-tasks`: Typography 5 · Color 8 · Divider 1 · Import 1 · LocaleParity 5,
  a cero (2 keys nuevas ×9).
- `assembleMockDebug`, `prodDebug` e `iosSimulatorArm64` en verde.
- Galería: «Resumen · el mes pasado, cerrado».

### En device — **Redmi** (mock, sha256 verificado)

El Oppo se desconectó a mitad; el Redmi aceptó el paquete mock por USB **sin el
`INSTALL_FAILED_USER_RESTRICTED` que MIUI suele dar con paquetes nuevos**, así que ese apunte de la
memoria no es universal.

La card sale arriba del todo: «El mes pasado · 5 aparcamientos 11,4 km · gasto aprox. 1,36 EUR ·
buscando plaza 46 min ▲ 2200 %».

Y un defecto que sólo se ve mirando, corregido: la franja de noche se pintaba **«20–0»**, porque la
etiqueta daba la vuelta al reloj. Un RANGO llega al final del día: «20–24».

## Anotado, no arreglado

**Un delta de 2200 % se lee como una avería**, aunque sea cierto (de 2 a 46 min lo es). Afecta por
igual a los tiles, que usan el mismo `percentDelta` desde STATS-UI-001, así que topar la cifra es un
cambio del contrato de TODA la pantalla y no de esta card — merece su ticket, no un parche aquí.
