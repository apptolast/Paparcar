# MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001 · El resumen del mes no tenía interruptor, y por eso no existía para nadie

**Estado:** ✅ Done (2026-09-16) · rama
`feature/MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001-recap-has-a-switch` ·
worktree `../Paparcar-recap-switch` · abierto el 2026-09-16 desde master `bb271e37`
**Origen:** pregunta del user — *«¿estamos anunciando y vendiendo bien el Plus en todas las cifras?
¿informamos de qué es free y qué no?»*.

## Problema

Auditadas las tres superficies, lo que un free **puede ver** está bien etiquetado; lo que **nunca
ve** no se anuncia en ninguna parte de la pantalla donde vive.

### Lo que ya se decía bien, y no se toca

| Dónde | Qué dice |
|---|---|
| Título de la card de cifras | se re-rotula solo a «Últimos 7 días» |
| `PlusStatsRow` | «Elige el periodo» + «Gratis ves tus últimos %1$d días; con Plus, el periodo que elijas» |
| Pie del historial | «Ver todo con Plus» **con el conteo exacto** de lo que queda fuera |
| Chips de periodo | desde [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001], el toque abre el paywall |
| Paywall | `paywall_free_is_free`, y la lista de beneficios ya nombra proyecciones y resumen del mes |

### El hallazgo

**El resumen del mes cerrado no tenía interruptor en Ajustes**, y su ausencia eran DOS defectos
llevando un solo silencio:

1. **Un Plus no podía apagarlo.** Hay tres filas de notificación (aparcamiento detectado, alertas de
   zona, alertas de precio) y el recap no era ninguna: era **el único de los cuatro canales que no
   se podía callar**.
2. **Para un free la función no existía en ninguna superficie**: sin card (el panel sencillamente no
   compone ninguna), **sin notificación** (`RunMonthRecapUseCase` → `Skipped("month-not-fully-visible")`)
   y sin una sola mención. Y eso es precisamente *«la app recuerda por ti»*, el eje que da nombre al
   plan.

**Una fila que no existe no se puede gatear en blando — y el gate blando es la única forma en que
este plan vende.** Ese es el vínculo entre las dos mitades: el mismo hueco produce el fastidio del
que paga y la invisibilidad del que no.

### Lo que NO era el problema

La card de ritmo/proyección también está ausente en free (`paceRows` sale vacío: `statsPeriod` es
`Last7Days`, no tiene cierre que proyectar). **No se vende aparte a propósito**: se DERIVA del
periodo, así que comprando el periodo aparece sola. Venderla por su cuenta sería cobrar dos veces
por lo mismo.

## Doctrina

- *Free pregunta y la app responde · Plus vigila y **recuerda** por ti.*
- **[MONEY-001] gating soft**: el botón nunca se esconde. Una card que no se compone no es un botón
  escondido —no hay nada que pulsar— pero deja al usuario en el mismo sitio. La regla necesitaba su
  otra mitad: **lo que no llega a aparecer se nombra en algún sitio, y ese sitio tiene que ser
  pulsable**.
- **Silenciar nunca se gatea**; encender es el intento que abre el paywall. La asimetría exacta de
  las dos filas vecinas.

## Diseño implementado

1. **Preferencia nueva `notifyMonthRecap`** (interfaz + DataStore Android + iOS + los dos fakes), ON
   por defecto como sus tres hermanas: el recap es un toque al mes, no un canal al que nadie se
   suscribió.
2. **`RunMonthRecapUseCase` la respeta**, y la pregunta **ANTES que el plan**: quien lo silenció ya
   respondió, y tratarlo como una cuenta a la que venderle algo sería no haberle escuchado.
   ⛔ Y el mes **no se marca como contado** al callarlo — volver a encenderlo dentro de la ventana
   tiene que poder contar el mes que se perdió (test propio).
3. **Fila nueva en Ajustes → Notificaciones**, con el mismo gate blando que zona y carburante.
   ⛔ Su descripción dice **qué es el aviso y nada de free-vs-Plus** (decisión del user, 16-09:
   *«yo no especificaría lo que tiene un free a no ser que no se sobreentienda»*), exactamente como
   sus dos vecinas. Quien lo explica es el paywall, en el momento del toque.
4. **`PaywallReason.MONTH_RECAP` propio**, no `STATS`: querer que te CUENTEN cómo fue el mes y
   querer ELEGIR un periodo son dos deseos distintos, y distinguirlos es el trabajo entero del
   embudo — la misma razón por la que `FUEL_ALERTS` no se plegó dentro de `ZONE_ALERTS`.
5. **La `PlusStatsRow` de cifras no cambia**: sigue vendiendo una sola cosa, el periodo, que es lo
   que de verdad gatea ahí. El recap ya tiene su sitio, y además es el sitio donde el usuario espera
   encontrar «la app me avisa».

## Preguntas resueltas por el camino (user, 16-09)

- **¿Hay resumen semanal y anual?** No: sólo existe el del mes (`closedMonthWindow` +
  `MonthRecapWorker` + `StatsMonthRecapCard`). El `PeriodClose` con WEEK/MONTH/YEAR es otra cosa —
  cómo se NOMBRA la fila de proyección, no un resumen.
  - **Semanal: descartado.** 52 toques al año es nag, y como la ventana free ES de 7 días le
    contaría a un free justo lo que ya tiene en pantalla: no hay historia de Plus que vender.
  - **Anual: buen candidato, no ahora.** Una vez al año, cero nag, memoria pura — pero necesita un
    año de datos y la app salió en septiembre de 2026, así que el primero con sentido es enero de
    2028. 📋 Follow-up, no se abre ticket todavía.
- **¿Se muestran las zonas/lugares habituales?** Sí, y sin gate: el tile «Lugar habitual» y la card
  `StatsPlacesCard` (dónde repites y qué cuesta cada sitio). Un free los ve sobre sus 7 días.
- **¿Enunciar en la card lo que el free tiene para siempre?** No. La `PlusStatsRow` ya dice «Gratis
  ves tus últimos 7 días» y el paywall lleva `paywall_free_is_free`; repetirlo en cada card es
  ruido.

## Criterio de éxito

- Un Plus puede apagar el resumen del mes, como puede apagar los otros tres avisos. ✅
- Un free ve la fila, la pulsa y aterriza en el paywall con un motivo que habla del resumen del mes
  y no de periodos. ✅
- Silenciar no requiere plan, ni siquiera este. ✅
- Callarlo no quema el mes: volver a encenderlo dentro de la ventana lo cuenta. ✅
- Paridad ×9 y variantes de galería para las dos caras de la fila. ✅

## Consumidores auditados

| Sitio | Qué asumía | Cierre |
|---|---|---|
| `AppPreferences` + Android + iOS + 2 fakes | el recap no necesitaba interruptor | ✅ `notifyMonthRecap` en los cinco |
| `RunMonthRecapUseCase` | sólo el plan podía callarlo | ✅ gate del usuario, ANTES del plan, sin quemar el mes |
| `SettingsScreen` · Notificaciones | tres canales | ✅ cuatro, el nuevo con el mismo patrón |
| `SettingsViewModel` | dos gates blandos | ✅ tres, misma asimetría |
| `PaywallReason` | `STATS` cubría todo lo de cifras | ✅ `MONTH_RECAP` propio + su `ReasonLine` |
| `PlusStatsRow` | vendía sólo el periodo | ✅ sin cambio, a propósito (ver Diseño §5) |
| `paceRows` | card ausente sin explicación | ✅ exento: se deriva del periodo |
| Dev Catalog · Ajustes | ninguna variante distinguía free de Plus | ✅ dos variantes nuevas |

## Lado iOS

`IosAppPreferences` implementa la preferencia con su propia key en `NSUserDefaults`, mismo default
(ausente = encendido). El worker que la lee es Android-only, como los otros tres canales — el hueco
de iOS es el reparto de notificaciones entero, no esta preferencia.

## Verificación

- `./gradlew testAll` ✅ · `:app:assembleMockDebug` ✅ · `:app:compileProdDebugKotlin` ✅
- **Los 5 tests nuevos SE EJECUTARON** (XML, no UP-TO-DATE): `RunMonthRecapUseCaseTest` 6/6
  (2 nuevos), `SettingsViewModelTest` 61/61 (3 nuevos).
- **Guardarraíles forzados con `--rerun-tasks`**: 51 tests, 0 fallos — incluidos LocaleParity y
  ComposeResourcesEscape, que son los que ven las 3 keys nuevas ×9.
- ⏳ Device.
