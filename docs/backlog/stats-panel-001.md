# STATS-PANEL-001 · El panel que explica la cifra (F4b)

**Estado:** ✅ Done · rama `feature/STATS-UI-001-one-scope-per-surface`

Segunda mitad de la **F4 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)). La primera
mitad —la ficha reconstruida en cuatro zonas— está en [`stats-ui-001.md`](stats-ui-001.md).

> ⚠️ **Comparte rama y commit con STATS-UI-001 a propósito**: el panel es el destino del «Ver
> desglose completo» de aquella tarjeta, así que separarlos daría un primer commit que enlaza a una
> pantalla inexistente. Un ticket = un commit sigue cumpliéndose: el ticket es **F4**, y estas dos
> fichas son sus dos mitades, que no se pueden partir limpio.

## Problema

La tarjeta de cifras de la ficha enseña el periodo seleccionado y poco más. Faltan (a) el sitio
donde un usuario Plus ve TODO —comparativa, gráfico y cifras juntas, con selector de coche—, y
(b) el destino del CTA: hoy en free la fila Plus abre el paywall, y en Plus no hay adónde ir.

Además hay una cifra que nace y nadie ve nacer: cuando el usuario cierra un viaje, las
estadísticas cambian **mientras la pantalla está abierta** y el cambio ocurre en silencio.

## Doctrina violada

- Ninguna nueva. Las que hereda: una superficie, un alcance ([`stats-ui-001.md`](stats-ui-001.md));
  cifra bajo umbral = ausente, nunca "0"; gating soft (Plus levanta el velo, no añade pantallas);
  el estado se escribe y se anima, nunca se tiñe.
- **Regla nueva que este ticket estrena:** *una cifra se anima porque HA CAMBIADO, no porque
  aparezca.* Un contador que sube cada vez que el tile entra en pantalla es decoración, y encima
  miente: dice «esto acaba de pasar» en un scroll.

## Diseño

### El panel
- `presentation/stats/` MVI completo (`StatsScreen`, `StatsState`, `StatsIntent`, `StatsEffect`,
  `StatsViewModel`) + `Routes.STATS`.
- Selector de periodo: los MISMOS chips de `HistoryFilterBar` (la autoridad de ventanas sigue
  siendo `VehicleHistoryCalculator.windowStartMs`; el panel no decide fechas).
- Selector de coche: sólo si hay más de uno.
- Contenido: gráfico de actividad (el `ActivityCard` que ya existe) + la rejilla completa de tiles
  con su ▲/▼ contra el periodo anterior.
- **Free entra** y ve la primera cifra entera, el resto con la misma redacción de la ficha, y el
  CTA. No se le cierra la puerta: se le enseña la habitación.

### El contador que sube
- `PapStatTile` pasa a aceptar el **número** además del texto: un texto ya formateado no se puede
  interpolar, y ese es el único motivo por el que hoy no se puede animar.
- Anima **sólo cuando el valor cambia estando visible**. Al entrar, la cifra ya está puesta.
- ⛔ **Sin guarda de "movimiento reducido", y a propósito.** Se escribió una y se retiró: la app no
  tiene ese puerto (no existe API común en CMP; en Android sería `ANIMATOR_DURATION_SCALE`, en iOS
  `UIAccessibility`), y el gráfico de al lado lleva animando 800 ms desde siempre sin guarda. Un
  ajuste que respetara este contador e ignorara el resto de la pantalla es peor que no tenerlo:
  promete una accesibilidad que no se cumple. Si se hace, se hace para TODAS las animaciones →
  ticket propio.

## Criterio de éxito

- Test: el panel free y el Plus sobre el MISMO dataset dan la misma rejilla, distinta visibilidad.
- Test: cambiar de periodo en el panel re-scopea las cifras (misma prueba que en la ficha).
- Test del contador: no anima en la primera composición; anima cuando el valor cambia.
- Strings en los 9 locales · galería + escenarios mock · Konsist · assembles · klib iOS.

## Lo que se descubrió al mirarlo en device (08-09-2026)

Tres agujeros que NO eran de este código, sino de los datos de prueba, y que hacían parecer rota la
pantalla. Los tres estaban ahí desde antes:

1. **Ningún coche del mock tenía aparcamientos dentro de la ventana free.** Las sesiones se
   generaban cada 11-12 días, así que la más reciente caía a 11 días y los 7 días del plan free
   quedaban SIEMPRE vacíos: cada ficha abría en «no hay nada». Ahora los perfiles son cuatro
   estados distintos a propósito — Seat = datos densos y recientes, Corolla = Bluetooth (sin ruta,
   así que km/CO₂/gasto desaparecen honestamente), **moto = VACÍA de verdad** (la ilustración de
   estado vacío no tenía dónde verse), Transit = caso medio.
2. **La hora de todas las sesiones era la misma**, porque se restaban días exactos: «hora punta»
   devolvía la hora a la que se hubiera lanzado el emulador. Ahora la hora se dispersa con una moda.
3. **Los coches de `FakeData` no tenían combustible** (son anteriores a VEH-FUEL-001), y sin
   combustible el agregador no puede convertir kilómetros en litros ni en CO₂: la tarjeta salía sin
   sus dos cifras principales en TODA la galería. Los cuatro tienen ya su motorización.

⚠️ Y una distinción que el user señaló y conviene dejar escrita: **«historial oculto por Plus» y
«sin historial» son dos estados distintos** y se parecen en pantalla. El primero (12 aparcamientos,
todos fuera de la ventana) muestra el pie «tienes N anteriores»; el segundo, la ilustración. Que en
el mock no hubiera ningún coche en el segundo estado es lo que hizo que el primero pareciera un bug.

## Verificación (08-09-2026)

- Suite completa verde. Testigo: `StatsViewModelTest` tests="9" failures="0" (coche de apertura,
  fallback al activo, la elección del usuario sobrevive a una re-emisión, selector oculto con un
  solo coche, re-scoping por periodo, lifetime completo en free, periodo vacío, paywall con
  `reason = STATS`). `KoinModuleVerifyTest` verde con el VM nuevo y su parámetro nullable.
- `assembleProdDebug`, `assembleMockDebug` y `compileKotlinIosSimulatorArm64` en verde. ⛔ El klib
  de iOS cazó `arguments?.getString(...)`: es API de Android y el grafo es commonMain — se lee con
  `read { getStringOrNull(...) }` como el resto de rutas.
- **En device**: ficha free con la tarjeta «Last 7 days» (primera cifra entera, resto redactado),
  moto con la ilustración de vacío y «Not parked yet», y el panel con su gráfico + las seis cifras
  + el pie «All time».

## Consumidores auditados

- `VehicleStatsBlock` — gana `onOpenPanel`, y la fila sólo se pinta con Plus (free lleva la fila
  metálica; nunca las dos). En el panel va a null: una tarjeta no enlaza consigo misma ✔
- `PapStatTile` — el parámetro `animatedNumber` es OPCIONAL, así que sus dos usuarios siguen
  compilando sin tocarlos; sólo cuentan las cifras que cuentan (gasto, CO₂, plazas cedidas) ✔
- `HistoryFilterBar` y `ActivityCard` — reutilizados tal cual: el panel no duplica ni chips ni
  gráfico, y las barras las construye la MISMA `buildActivityBuckets` ✔
- `Routes.STATS` + grafo — ruta nueva con `vehicleId` opcional, leído de forma multiplataforma ✔
- `KoinModuleVerifyTest` — verde con el VM nuevo (`viewModel { params -> … params.getOrNull() }`) ✔
- `FakeUserParkingRepository` / `FakeData` — cuatro perfiles y motorización; ver sección anterior ✔
