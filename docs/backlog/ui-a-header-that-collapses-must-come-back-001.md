# UI-A-HEADER-THAT-COLLAPSES-MUST-COME-BACK-001 · La cabecera de Vehículos se quedaba retirada

**Estado:** ✅ Done

## Problema

En Vehículos, al scrollear el historial y volver a subir, el título del appbar ("Mis vehículos") y
las pestañas de coche desaparecían y no volvían — la lista quedaba en su inicio (la hero card del
coche visible arriba) pero con un hueco en blanco donde debería estar la cabecera. Reportado por el
usuario con captura (15-09).

Reproducido de forma FIABLE (no con el gesto manual, que en la mayoría de intentos se corrige solo)
entrando directo a la pestaña Vehículos desde el arranque del Dev Catalog mock: la cabecera nace ya
retirada en la primera composición, con la lista asentada exactamente en su primer ítem.

## Doctrina violada

[UI-TOPBAR-COLLAPSE-001] ya documentaba el caso: *"Un salto programático (`animateScrollToItem`) no
pasa por el nested scroll, así que la cabecera quedaría retirada sobre una lista ya en su inicio."*
[UI-SCROLL-TO-TOP-001] cerró ESA vía para un único salto conocido — el botón "volver arriba" — atado
a mano a `expandKey`. Pero la doctrina real es más amplia que ese botón: **"lista en su primer
píxel" debe implicar SIEMPRE "cabecera desplegada"**, venga ese estado de donde venga. Al arreglarlo
solo para el salto del botón, cualquier OTRA vía que deje el offset de la cabecera desincronizado de
la posición real de la lista —una animación de asentamiento de `onPostFling` interrumpida por un
nuevo gesto, un salto inicial de navegación no instrumentado— reabre el mismo hueco sin que nada lo
corrija jamás.

## Causa

`PapCollapsingTopBarScaffold` reconstruye el offset de la cabecera **sumando** los deltas
`consumed.y` que el `NestedScrollConnection` observa del scroll de la lista (`onPostScroll`), y solo
la fuerza a 0 en dos casos explícitos: cambio de página del pager, o el contador `expandHeader` que
solo mueve el botón "volver arriba". No hay ninguna comprobación que compare ese offset RECONSTRUIDO
contra el estado REAL de la lista (`LazyListState`). Si algo deja esa reconstrucción desincronizada
—confirmado empíricamente en el arranque directo del Dev Catalog, donde la cabecera nace colapsada
sin que el usuario haya tocado nada— el hueco queda para siempre: nada vuelve a comprobar "¿la lista
está en su inicio de verdad?".

## Diseño

El invariante se aplica en el sitio donde se conoce la verdad — el propio `LazyListState` de
`HistoryContent`, que es quien sabe si está realmente en su primer ítem — en vez de intentar que el
`NestedScrollConnection` de la cabecera nunca se desincronice (imposible de garantizar para
cualquier futura vía de salto). `HistoryContent` ya recibía `onScrolledToTop` (cableado al
`expandKey` del scaffold desde [UI-SCROLL-TO-TOP-001]); ahora también lo invoca reactivamente
cuando `listState` reporta estar en su primer ítem con offset 0, no solo cuando se pulsa el botón:

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
        .distinctUntilChanged()
        .collect { atTop -> if (atTop) onScrolledToTop() }
}
```

Forzar el despliegue cuando ya está desplegado es idempotente (no hay parpadeo observable), así que
esto cierra la vía sin importar CÓMO se llegó al desajuste — el mismo patrón que ya usa el botón,
extendido al caso general.

## Efecto colateral encontrado y corregido (mismo ticket, a petición del usuario)

Al intentar reproducir en device con el flavor `mock` (Dev Catalog), la app crasheaba al entrar en
CUALQUIER pantalla: `MockModule.kt` no registraba `ZoneWatchRepository` tras el commit de zonas del
15-09 (`f579b782`, [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]) — el Dev Catalog quedó
desincronizado, violando la regla obligatoria de mantenerlo siempre en sync. Corregido con el mismo
patrón que `DrivingRouteStore`: `RoomZoneWatchRepository` real sobre la base in-memory del mock, más
el DAO que faltaba (`zoneWatchDao()`).

Segundo hallazgo, mismo patrón, corregido también a petición del usuario: navegar a Home en el
flavor mock crasheaba porque `MockModule.kt` tampoco registraba `FuelWatchRepository` (commit de
vigilancia de precio, `1cff6f26`, [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]). Mismo
arreglo: `RoomFuelWatchRepository` real + `fuelWatchDao()`. Verificado en device: Home abre y
muestra el coche aparcado sin crash.

Dos gaps encontrados en dos commits distintos en la misma sesión sugiere que **`MockModule.kt`
necesita un barrido dedicado** contra todos los commits recientes de detección/zona/combustible —
fuera del alcance de este ticket, pero digno de un ticket de auditoría propio.

## Criterio de éxito

- ✅ Suite `:shared:testDebugUnitTest` en verde.
- ✅ `:app:compileProdDebugKotlin` sin romperse.
- ✅ Verificado en device (Oppo, flavor mock parcheado): cabecera visible en el arranque directo a
  Vehículos (antes reproducía el bug de forma fiable) y tras 6 ciclos rápidos de scroll abajo/arriba
  consecutivos sin pausa.
- ⏳ Verificación en el flavor prod del usuario (este worktree no puede instalar sobre el Oppo sin
  desinstalar la app real firmada con otra clave — decisión del usuario, no se toca).

## Consumidores auditados

`PapCollapsingTopBarScaffold` + `expandKey` los usan también Ajustes, el formulario de vehículo y la
config Bluetooth ([UI-TOPBAR-COLLAPSE-001]), pero ninguno de esos cuerpos usa `HistoryContent` — el
fix vive en el ÚNICO sitio (`HistoryContent`) que combina lista larga + cabecera colapsable +
`onScrolledToTop`, así que no hace falta barrer más consumidores.
