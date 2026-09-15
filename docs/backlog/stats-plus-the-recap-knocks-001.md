# STATS-PLUS-THE-RECAP-KNOCKS-001 · El resumen del mes llama a la puerta

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Abierto:** 2026-09-16 · desde master `cc8d0909` · fleco de
[`stats-plus-remembers-and-reports-001.md`](stats-plus-remembers-and-reports-001.md)

## Problema

F6 dejó la card del mes cerrado arriba del panel — y **sólo la ve quien entre al panel**, que es
justo lo que no hace la gente el 1 de mes. La promesa que se vende es «Plus **recuerda por ti**», y
un resumen que hay que ir a buscar no recuerda nada: espera.

## Doctrina violada

Ninguna. Las que manda respetar:

- **Un caso de uso por VEREDICTO** [DET-VERDICT-NOT-PREDICATE-001]: *«¿toca contar el mes, y con
  qué?»* es un veredicto (cambia lo que el usuario ve) → un caso de uso. El worker sólo pone las
  palabras, como ya hace `FuelPriceWatchWorker` con su veredicto.
- **Una sola puerta de entitlement**: el resumen se calcula sobre el historial **gateado por el
  plan** (`gateHistoryToEntitlement`), igual que la card. ⛔ Nada de un `if (isPlus)` aparte: dos
  sitios decidiendo el plan son dos sitios que se desincronizan — y con la ventana libre de 7 días
  un mes cerrado **no existe** para free, así que el dato ya hace de puerta.
- **Una vez y sólo una**: la app puede despertarse siete días seguidos dentro de la ventana; contar
  el mismo mes siete veces sería un recordatorio convertido en acoso.

## Diseño

1. **`RunMonthRecapUseCase`** (commonMain) — el veredicto, con reloj inyectado:
   - fuera de los primeros [RECAP_FRESH_DAYS] días → `Skipped("stale")`;
   - ya anunciado ese mes (`AppPreferences.lastMonthRecapAnnounced`, `yyyymm`) → `Skipped("said")`;
   - sin cifras que contar en el mes cerrado → `Skipped("nothing")`;
   - si hay algo → devuelve `PeriodStats` del mes cerrado **y apunta el mes como contado**, para que
     un segundo tick del mismo día no pueda repetirlo.
2. **`MonthRecapWorker`** (androidMain) — periódico diario, copiando la forma del de combustible:
   decide nada, traduce el veredicto a una notificación.
3. **`AppNotificationManager.showMonthRecap(parkings, spend, searchTimeMs)`** — valores de DOMINIO;
   las palabras y sus nueve idiomas las pone la implementación, que es donde vive `R.string`.
   Canal: el mismo que zonas y combustible (**no** el de detección, que debe poder silenciarse
   aparte). Al tocar, el panel de estadísticas.
4. **Disparo desde el Dev Catalog**, porque el mes cerrado sólo ocurre siete días al año por doce y
   si no, esto no se puede ver en mano hasta el 1 del mes que viene.

## Criterio de éxito

- **Test**: dentro de la ventana y con cifras → cuenta el mes y lo apunta; el segundo tick del mismo
  mes → `Skipped("said")`; el día 12 → `Skipped("stale")`; sin sesiones → `Skipped("nothing")`.
- **Test**: un mes nuevo vuelve a contarse (el apunte es por `yyyymm`, no un booleano).
- `testAll` + guardarraíles + 9 locales + compila mock/prod/iOS.
- **En device**: la notificación, disparada desde el Dev Catalog.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `ClosedMonth` (F6) | ♻️ la ventana y la frescura salen de ahí; no se reescriben |
| `ComputePeriodStatsUseCase` + `gateHistoryToEntitlement` | ♻️ las cifras y la puerta del plan, las mismas de la card |
| `AppNotificationManager` (+ impl) | 🔧 un método más, con el patrón de `showFuelPriceMove` |
| `AppPreferences` (+ impl DataStore) | 🔧 un `Int?` nuevo: el `yyyymm` ya contado |
| `PaparcarApp` / scheduler | 🔧 encola el periódico diario |
| Dev Catalog | 🔧 botón de disparo |

## Desviación sobre el diseño

**Las puertas salieron del caso de uso a una función pura** (`MonthRecapGate`). Al ir a escribir su
test, los fakes del repositorio son por ESCENARIO y no admiten sembrar sesiones sueltas: probar el
veredicto habría exigido un stub de `UserParkingRepository` entero. Sacar las reglas —ventana, una
vez al mes, hay algo que contar— a una función pura las deja testeables sin levantar nada, que es
como ya viven `SentryWakeCooldown` y compañía, y deja el caso de uso en lo que debe ser: fontanería.
De paso, el tic diario cierra las dos puertas baratas **antes** de tocar ningún repositorio.

## Verificación (16-09-2026)

- `testAll` verde. Testigo: `MonthRecapGateTest` tests="6" failures="0" — la ventana, el segundo tic
  del mismo mes, el mes siguiente que sí se cuenta, el cruce de año y «no hay nada que contar».
- Guardarraíles con `--rerun-tasks` a cero · 5 keys nuevas ×9 en `app/src/main/res` (⛔ ahí el
  apóstrofo VA escapado).
- **iOS cazó el consumidor que faltaba**: `IosAppPreferences` no implementaba el miembro nuevo.
  Por eso el compile de iOS está en la lista y no es una formalidad.
- `assembleMockDebug`, `prodDebug` e `iosSimulatorArm64` en verde.

## ⏳ Lo que NO se ha podido ver

**La notificación no se ha visto en pantalla.** En el Redmi faltaba `POST_NOTIFICATIONS` (paquete
mock recién instalado); concedido por `adb`, el disparo se lanzó, pero el móvil tenía la pantalla
bloqueada y **no se desbloquean móviles ni se tocan sus ajustes**. El botón «Disparar el resumen del
mes pasado» queda en el Dev Catalog justo para eso: un toque y se ve.

Lo que sí está verificado: el veredicto (tests), que el canal y el intent son los mismos que usan
las alertas de zona y combustible, y que todo compila en las tres plataformas.
