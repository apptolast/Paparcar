# FUEL-A-NEARBY-ALERT-ARMS-WITH-THE-CAR-NOT-THE-TAB-001 · el aviso de gasolineras se arma con el coche, no con haber abierto la pestaña

**Estado:** ✅ Done · rama `feature/FUEL-A-NEARBY-ALERT-ARMS-WITH-THE-CAR-NOT-THE-TAB-001` ·
worktree `../Paparcar-fuel-arm-on-vehicle` · `testAll` en verde

## Problema
El carril free del aviso de precios de gasolinera ("tu alrededor") existía desde
`FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`, pero no se estaba usando: en 14 días de GA4 hay **1** evento
`fuel_station_follow_toggled` y **0** `fuel_price_move_shown`/`fuel_cheaper_nearby_shown` en todo el
histórico desde el 08-09. La causa: el aviso sólo se armaba si el usuario había abierto alguna vez
el complemento de gasolineras (`NearbyComplement.FUEL` introducido) o seguía una gasolinera (Plus).
Casi nadie lo había hecho, así que el reloj diario (`FuelWatchScheduler`) nunca llegaba a instalarse
y, aunque lo hiciera, `RunFuelPriceWatchUseCase` se auto-saltaba con `"never-asked-about-fuel"`.

## Doctrina violada / cambiada
No rompe ninguna doctrina del proyecto; **cambia una decisión de producto anterior y documentada**
(el freno de `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`), con autorización explícita del user el 25-11.
La razón del freno original seguía siendo real (coste de feed diario para cuentas que nunca
mostraron interés) y se asume conscientemente: ahora el feed se baja a diario para toda cuenta con
un vehículo activo que compra en un surtidor, no sólo para quien tocó la pestaña.

## Señales / datos disponibles
- GA4 (`pap-26.analytics_524957242`): 0 eventos de aviso de gasolinera mostrado en todo el
  histórico — confirma que el gate nunca se abría para casi nadie.
- 86 cuentas en `users/`, la inmensa mayoría con `defaultVehicleId` (coche ya registrado antes de
  llegar a permisos/Home) y `fuelType` declarado en el alta — dato que ya existe sin preguntar nada
  nuevo.

## Diseño
Dos gates, ambos cambiados, ambos con la misma regla nueva:
- **`RunFuelPriceWatchUseCase.invoke()`** (`shared/.../domain/usecase/fuel/RunFuelPriceWatchUseCase.kt`):
  el freno `hasAskedAboutFuel` se sustituye por `carBurnsAtAPump = fuel != FuelType.ELECTRIC`. Un
  eléctrico sigue sin tener nada que "tu alrededor" le cuente (no compra en un surtidor —
  `FuelType.pumpProduct` ya lo decía), así que sin ninguna seguida (carril Plus) sigue saltándose,
  ahora con el motivo `"electric-no-pump"`.
- **`FuelWatchScheduler.start()`** (`app/.../fuel/FuelWatchScheduler.kt`): el stream que decide si
  instalar el periodic añade `vehicleRepository.observeActiveVehicle()` como tercera fuente; se arma
  si hay algo seguido, **o** el complemento fue introducido (se mantiene, no hace daño), **o** el
  coche activo compra en un surtidor.
- `notifyFuelPrices` (switch manual en Ajustes, default `true`) sigue siendo el freno final que el
  usuario controla.

## Criterio de éxito
- Un usuario con coche de combustión y que **nunca** ha abierto el complemento de gasolineras recibe
  el aviso de "tu alrededor" en cuanto haya una serie de dos días que comparar — sin tocar la
  pestaña. Cubierto por
  `should_tellAFreeDriverAboutACheaperPumpNearby_when_theyNeverOpenedTheFuelTab`.
- Un eléctrico sin ninguna seguida sigue sin recibir nada
  (`should_skip_when_theCarIsElectricAndNothingIsFollowed`), y si sigue una estación, el carril Plus
  sigue funcionando (`should_stillWatchTheFollowedLane_when_theDriverOwnsAnElectricCar`).

## Consumidores auditados
- `RunFuelPriceWatchUseCaseTest` — reescritos los dos tests que dependían de `askedAboutFuel`
  (ya no existe como señal) y añadido el test que prueba el pedido real del ticket.
- `FuelWatchScheduler` — único call site en `PaparcarApp.kt`, actualizado (cuarto `get()` para
  `VehicleRepository`, ya bindeado en el grafo de Koin porque `RunFuelPriceWatchUseCase` ya lo usaba).
- Dev Catalog: no aplica — no es pantalla/estado/flujo nuevo, es un gate de un worker en background.
  `assembleMockDebug`/`compileMockDebugKotlin` verificado, no se tocó nada de `app/src/mock/`.
- Web / ficha de Play: no aplica — no cambia ningún límite del plan, feature nombrada ni número
  público; el comportamiento interno de cuándo se arma un aviso no es una promesa de cara afuera.
