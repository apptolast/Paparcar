# PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001 · Hay un gate que cobra por las rutas en la nube y el paywall no lo nombra

**Estado:** ✅ Done · mergeado a master por squash · ⏳ **sin ver en mano**: falta la captura de las
seis filas, que sale del Dev Catalog. Rama
`feature/PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001-route-cloud-benefit` ·
worktree `../Paparcar-paywall-route-cloud` · abierto el 2026-09-24 desde master `5fa88de7c`
**Origen:** pregunta del user — *«¿tenemos el paywall actualizado?»*. La auditoría contra el código
salió limpia en todo menos en esto.

## Problema

`SettingsViewModel.kt:210-216` abre el paywall cuando un usuario free enciende *«Save your routes
to your account»* (`PaywallReason.ROUTE_CLOUD`, gate soft de
[PRIV-A-TRACE-IS-NOT-A-STAT-001]). Es un gate real: cobra por una función concreta y observable.

La lista **«What Plus adds»** del paywall tiene **cinco filas** —`PLUS_BENEFITS` en
`PaywallScreen.kt:510-571`— y **ninguna la nombra**:

| Fila | Qué vende |
|---|---|
| `paywall_benefit_history` | historial completo + cifras, proyecciones, sitios y horas |
| `paywall_benefit_alerts` | plazas en tus zonas · precio de gasolineras · resumen del mes |
| `paywall_benefit_zones` | zonas sin límite |
| `paywall_benefit_vehicles` | todos tus coches, también en el widget |
| `paywall_benefit_shared_car` | un coche, hasta cinco conductores |

Lo único que se dice de las rutas es `paywall_reason_route_cloud` («Keeping your routes in your
account is part of Plus»), y esa línea:

1. **solo la ve quien entra justo por esa puerta** — el resto de entradas (Ajustes → Plus es
   `GENERAL`, y son 8 call sites, el grupo más grande después de `STATS`) no la muestra jamás;
2. **desaparece en cuanto la tienda responde** — es el motivo de una sola línea, no el pitch.

Y mientras tanto **la web sí la vende**: `web/content/es.json:216` (y su espejo EN) lista *«Tus
rutas guardadas en tu cuenta, no solo en este móvil»* como una de las siete cosas de la columna
Plus. O sea: `paparcar.com` promete siete, la pantalla que cobra enumera cinco, y la que falta es
la misma que tiene un interruptor esperando en Ajustes.

## Doctrina violada

- **La web es un consumidor más de la verdad del producto**
  [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001] — aquí al revés de lo habitual: la web está bien y
  **la app es la que se quedó atrás**. La regla no dice que la web copie a la app, dice que las dos
  digan lo mismo.
- **Sistemas, no parches**: el invariante que falta no es «falta una fila», es *todo motivo de
  paywall que cobra tiene que estar vendido en el pitch*. Hoy esa correspondencia no vive en
  ningún sitio, no se declara y no se comprueba: se sostiene sobre que alguien se acuerde. Por eso
  se pudo romper sin conflicto, en un ticket (`ddb448e46`) que ni siquiera tocaba el paywall.

## Señales / datos disponibles

- `PaywallReason` tiene 10 entradas; 9 son gates y `GENERAL` es una oferta, no un límite.
- `PLUS_BENEFITS` es una lista literal en `commonMain`, sin ninguna referencia a `PaywallReason`.
- Resto de la auditoría del 24-09, todo correcto y sin tocar: límites free del copy = `FREE_*`
  (7 / 1 / 2) = `FREE_LIMITS` de `web/lib/config.mjs`; «cinco conductores» = dueño +
  `SHARED_CAR_MAX_MEMBERS`; 36 keys `paywall_*` en los 9 locales; `−%1$d%` sin el `%%` roto
  [COPY-PERCENT-IS-NOT-ESCAPED-001]; ningún gate tocado desde el último repaso de copy
  (`git log 8dd5a6c35..master -- domain/entitlement/` vacío).

## Diseño

**El invariante:** *cada `PaywallReason` que es un gate está vendido por alguna fila del pitch.*
Se escribe UNA vez, en el sitio donde ya vive el pitch, y se comprueba.

1. **`PlusBenefit` declara qué motivos vende** — un campo `sells: Set<PaywallReason>` en la clase
   de `PaywallScreen.kt`. No es metadato decorativo: es lo que convierte «me acordé» en algo que
   una máquina puede leer. Las agrupaciones que ya existen quedan escritas en vez de implícitas
   (la fila de historial vende `HISTORY` + `STATS` porque son **un** gate,
   `FREE_HISTORY_WINDOW_DAYS`; la de avisos vende `ZONE_ALERTS` + `FUEL_ALERTS` + `MONTH_RECAP`).
2. **Fila nueva, la sexta**, con `Icons.Rounded.Route` — el mismo glifo que ya usa su línea de
   motivo en `PaywallScreen.kt:410`, para que quien entre por esa puerta reconozca la fila:
   - `paywall_benefit_routes` — «Your routes, saved in your account»
   - `paywall_benefit_routes_note` — «Free: only on the phone that recorded them»
   El `note` sigue la regla de la clase: donde free tiene una versión menor de lo mismo, se dice
   cuál es; nunca «No incluido».
   **Va en segundo lugar, detrás del historial**: el orden de la lista es el eje de la doctrina
   —memoria, vigilancia, capacidad— y esto es memoria («recuerda por ti»), no una alerta.
3. **`PLUS_BENEFITS` pasa a `internal`** y nace `PaywallBenefitsCoverageTest` en `commonTest`:
   `PaywallReason.entries - GENERAL ⊆ ∪ sells`. Un motivo nuevo sin fila que lo venda deja de
   compilar la promesa y rompe el test — que es exactamente lo que no pasó en septiembre.
4. **Los 9 locales** en esta misma tarea, EN de base [I18N-…-ONLY-001].
5. **La web no se toca**: ya lo dice, en ES y EN, y con la palabra correcta. Se verifica que sigue
   en paridad y punto.

⛔ **Lo que NO se hace:** ni un `when(reason)` que elija filas, ni una fila condicional al motivo
de entrada. El pitch es el mismo para todos; lo que cambia con el motivo es la línea de arriba,
que ya existe.

## Criterio de éxito

- [x] `PaywallBenefitsCoverageTest` falla si se añade un `PaywallReason` sin venderlo, y pasa ahora.
- [x] La lista del paywall nombra las rutas en la nube, se entre por donde se entre.
- [x] `paywall_benefit_routes` + `_note` en los 9 locales (`LocaleParityGuardrailTest` verde).
- [x] `./gradlew testAll` verde · `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin`.
- [ ] Captura del paywall con las seis filas en la descripción de la PR
      [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001] — el cambio se ve, así que se enseña. Sale del Dev
      Catalog, que ya tiene las variantes del paywall (`StateGalleryScreen.kt:2895`): no hace falta
      estado nuevo en la galería, porque esto no añade un estado, añade una fila.

## Resultado

**El guard se falsificó antes de darlo por bueno** [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED]. Con
la fila de rutas vendiendo `setOf(ZONES, GENERAL)` en vez de `ROUTE_CLOUD`, los **tres** tests
caen a la vez y cada uno por su motivo:

```
PaywallBenefitsCoverageTest > should_sell_every_gate_when_the_paywall_lists_what_plus_adds FAILED
PaywallBenefitsCoverageTest > should_own_each_gate_once_when_two_rows_could_claim_it FAILED
PaywallBenefitsCoverageTest > should_not_sell_a_general_entrance_when_it_is_an_offer_and_not_a_limit FAILED
```

Cambios:

| Fichero | Qué |
|---|---|
| `presentation/paywall/PaywallScreen.kt` | `PlusBenefit` gana `sells: Set<PaywallReason>`; clase y lista pasan a `internal`; los 5 motivos agrupados quedan escritos; **fila nueva** de rutas con `Icons.Rounded.Route`, en 2.º lugar |
| `commonTest/…/PaywallBenefitsCoverageTest.kt` | nuevo: cobertura, propiedad única y `GENERAL` exento |
| `composeResources/values*/strings.xml` ×9 | `paywall_benefit_routes` + `_note` |

⚠️ El `note` dice **dónde** guarda free las rutas, no que no las tenga: un free las graba y las
dibuja igual, solo que viven en un móvil y una reinstalación se las lleva. Escribirlo como
ausencia habría sido la celda «No incluido» que la propia clase prohíbe — y además sería falso.

## Consumidores auditados

Los 10 `PaywallReason`, contra la fila que los vende:

| Motivo | Call sites | Vendido por | Estado |
|---|---|---|---|
| `HISTORY` | 4 | fila historial | ✅ |
| `STATS` | 12 | fila historial (mismo gate) | ✅ |
| `ZONE_ALERTS` | 5 | fila avisos | ✅ |
| `FUEL_ALERTS` | 2 | fila avisos | ✅ |
| `MONTH_RECAP` | 6 | fila avisos (`_note`, «when the month closes») | ✅ |
| `ZONES` | 5 | fila zonas | ✅ |
| `VEHICLES` | 5 | fila coches | ✅ |
| `SHARED_CAR` | 2 | fila coche compartido | ✅ |
| `ROUTE_CLOUD` | 3 | **nadie** | 🔴 lo arregla este ticket |
| `GENERAL` | 8 | — | ✅ exento: es una entrada, no un límite |

Otras superficies que enumeran lo que da Plus, revisadas contra la lista nueva:

| Superficie | Nombra las rutas | Acción |
|---|---|---|
| `web/content/{es,en}.json` → columna Plus | ✅ sí | ninguna |
| `settings_sync_routes` + `_desc` (la fila del gate) | ✅ es la fila misma | ninguna |
| `PlusStatsRow` / pie del historial / chips de periodo | no aplica (venden el periodo) | ninguna |
| Ficha de Play | fuera de alcance — vive en `docs/play/`, no en el build | ver abajo |

## Follow-up

- La ficha de Play enumera lo que incluye Plus y **no** está cubierta por ningún guard. Queda
  fuera de esta tarea (no es código, y su copy está sin commitear —
  `project_play_listing_copy_001`). Si al revisarla falta la línea de rutas, entra por ahí.
