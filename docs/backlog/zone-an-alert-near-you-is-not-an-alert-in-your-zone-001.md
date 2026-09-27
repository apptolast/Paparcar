# ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001 · el aviso que se pidió no es el que se implementó

**Estado:** 📋 Abierto (27-09-2026) — dicho por el user, verificado en el código. Sin rama todavía.

## Problema

El user pidió un aviso de **plaza cerca de donde estás AHORA**. Lo que se implementó es el aviso de
**plaza dentro de una zona guardada**, con un canal nuevo. Son dos features distintas y se
confundieron al implementarlas; lo dijo el user el 27-09: *«me refería a que te saliera un SPOT CERCA
DE DONDE ESTÁS, NO CERCA DE TU ZONA, son cosas distintas y parece que te liaste al implementarlo»*.

La petición está citada textualmente **dentro** del commit que la implementó, `9af3331097`
[ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001], en el KDoc de `AlertChannel`:

> *«cuando el usuario esté viendo la pantalla, si aparece un spot NUEVO se le notificará también al momento»*

Ahí no hay ninguna zona. Y sin embargo el banner (`PapSpotAlertBanner`, colgado en `App.kt` desde
`AppViewModel`) se alimenta de `ObserveZoneSpotAlertsUseCase`, que emite sólo lo que cae dentro de
una zona con `Zone.notifyOnSpot` encendido. Consecuencia medida en el código:

- `Zone.notifyOnSpot` nace en **`false`** (`Zone.kt:32`).
- Encenderlo abre el **paywall** siendo free, por las dos puertas: `HomeViewModel` `SetZoneNotify`
  y `SettingsViewModel` `ToggleZoneAlertsNotif`.
- → Para un usuario sin zonas, o free, **el banner no salta nunca**. La feature que se pidió no
  existe en ninguna versión, ni en 1.3.0 ni en master.

Un efecto colateral del lío: al implementarlo se quitó el gate de Plus **del stream de zonas**
(`ObserveZoneSpotAlertsUseCase` ya no lee `ObserveIsPlusUseCase`) y se invirtieron los dos tests que
lo cifraban, con el KDoc diciendo *«no será de pago, al menos al principio»*. Pero el aviso de zona
**sí sigue siendo de pago** — lo confirma el user el 27-09, y lo hacen cumplir los dos interruptores.
O sea que el gate quitado era del carril equivocado: no cambia el comportamiento (nadie puede
encenderlo gratis), pero deja un KDoc y dos tests afirmando una regla de producto que es falsa.

## Doctrina violada

- **Una afirmación en un comentario no se ejecuta.** El KDoc de `ObserveZoneSpotAlertsUseCase` dice
  que la alerta ya no es de pago; los dos interruptores dicen que sí. Gana el interruptor.
- **El eje de Plus** (`plus-alerts-and-tiers-001.md`): *free = tú preguntas y la app responde · Plus
  = la app te avisa a ti*. Las dos features caen en lados distintos de ese eje, y por eso mezclarlas
  no es un detalle:
  - *plaza cerca de donde estás, con el mapa delante* → **free**. No hay vigilancia que pagar: el
    mapa ya está abierto, ya tiene tu ubicación y ya está consultando ese terreno. Es la
    demostración de lo que hace la app — y nadie paga por una vigilancia que no ha visto funcionar.
  - *plaza en una zona tuya, con la app cerrada* → **Plus**. Ahí sí trabaja la app mientras no miras:
    índice en servidor, fanout y push.

## Señales / datos disponibles

- `ObserveNearbySpotsUseCase` ya observa el terreno alrededor de un punto
  (`DEFAULT_SEARCH_RADIUS_METERS` = 2 km) y es lo que pinta el mapa: el dato ya está en memoria.
- `MapVisibility` (de `9af3331097`) ya responde «¿está el mapa delante?», que es la condición exacta
  del carril free.
- `alertChannelFor(mapOnScreen)` ya elige canal; para el carril cerca-de-ti sólo existe `IN_APP`.

## Diseño (esbozo — se decide al abrir el ticket)

Un veredicto propio, `EvaluateNearbySpotWorthTellingUseCase`, hermano de `isAlertableInZone` y **no
una rama dentro de él**: la pregunta es distinta (anclada a tu posición, no a un círculo guardado).

⛔ **Dos cosas que no se pueden copiar del carril de zona:**
1. **La memoria.** `ZoneWatchRepository` está keyada por zona, y `recordLook` marca la plaza como
   contada **antes** de emitirla. Un segundo consumidor del mismo stream haría que el primero se
   quedara sin nada (está avisado en el KDoc de `AlertChannel`). El carril cerca-de-ti necesita su
   propio registro de «ya te lo conté», keyado por plaza.
2. **El radio.** `ZONE_ALERT_MIN_RADIUS_METERS` son 500 m *desde un sitio que te importa*. «Cerca de
   donde estás» mientras te mueves es otra cosa, y hay que decidirla con el user: un aviso a 500 m
   de un semáforo no es una plaza para ti.

Preguntas abiertas para el user: ¿salta mientras conduces, o sólo parado/andando? ¿Hay tope de
avisos por rato? ¿Y si la plaza es la que acabas de liberar tú?

## Criterio de éxito

Con el mapa delante, sin Plus y **sin ninguna zona creada**, aparecer una plaza nueva a menos de
`<radio a decidir>` de tu posición enseña el banner una sola vez. Test que hoy no puede pasar.

## Consumidores auditados

Pendiente — se hace al abrir la rama. Entra de salida: el KDoc y los dos tests invertidos de
`ObserveZoneSpotAlertsUseCase`, que afirman que el aviso de zona es gratis.
