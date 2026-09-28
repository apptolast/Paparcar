# ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001 · el código dice gratis y los interruptores cobran

**Estado:** ✅ Done (27-09-2026) · rama `bugfix/ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001-restore-the-gate` · worktree `../Paparcar-zone-gate`. Follow-up de
[`zone-an-alert-near-you-is-not-an-alert-in-your-zone-001.md`](zone-an-alert-near-you-is-not-an-alert-in-your-zone-001.md).

## Problema

El aviso de plaza en tu zona **es de pago** — confirmado por el user el 27-09 — y lo hacen cumplir dos
interruptores, los dos con gate blando:

- `HomeViewModel` · `HomeIntent.SetZoneNotify` → paywall si no es Plus.
- `SettingsViewModel` · `SettingsIntent.ToggleZoneAlertsNotif` → paywall si no es Plus.
- Y `Zone.notifyOnSpot` nace en `false`, así que sin pasar por uno de los dos no hay aviso posible.

Pero el código afirma lo contrario en tres sitios, todos escritos en `9af3331097`:

1. El KDoc de `ObserveZoneSpotAlertsUseCase`: *«⛔ Ya no lleva gate de Plus — decisión del user el
   21-09: "no será de pago, al menos al principio"»*.
2. `ObserveZoneSpotAlertsUseCaseTest` · `should_alertAFreeUserToo_because_theGateIsGone`.
3. `AppViewModelTest` / el otro test invertido en la misma tanda.

El gate que se quitó era del **stream**, no de los interruptores, y con los interruptores cerrados el
stream nunca llega a emitir para un usuario free. O sea: **el comportamiento es de pago y la
documentación dice gratis.** Nadie lo nota porque los tests pasan — prueban un camino que el producto
no deja recorrer.

## Doctrina violada

**Una afirmación en un comentario no se ejecuta** — y aquí es peor que un comentario, porque hay dos
tests *verdes* respaldando la afirmación falsa. Es el patrón de #121 (el KDoc que mentía sobre quién
escribe `"user"`), con el agravante de que el siguiente lector razonará sobre un gate que no existe y
podría «arreglar» los interruptores para que cuadren con el KDoc, regalando la única feature de
vigilancia que se vende hoy.

## La decisión del user (27-09)

> **Zona es de pago. Cerca de tu ubicación (el nuevo) no.**

O sea la rama «es Plus»: el gate vuelve al stream, el KDoc dice la verdad y los dos tests se
re-invierten. Ningún copy cambia — la ficha de Play, la web y el paywall ya lo vendían como Plus, así
que lo que estaba desalineado era el código, no el pitch.

## ⛔ No era un arreglo de documentación: había un bug de verdad

Esto es lo que apareció al mirarlo, y es la razón de que el KDoc viejo llamara a este gate *«the pause
mechanism of the downgrade rule»*:

- El opt-in de cada zona (`Zone.notifyOnSpot`) **sobrevive a propósito** a la pérdida de Plus, para que
  recuperarlo reanude las mismas zonas en vez de una lista vacía.
- `ZoneAlertSubscriptionSync` sí gatea en vivo por Plus, pero sólo limpia el **índice del servidor**:
  mata el push, y nada más. Era el único carril que seguía respetando el plan.
- El carril **local** se había quedado sin gate desde el 21-09.

→ A un usuario al que le caduca Plus **le seguían llegando avisos de zona en local**, banner y
notificación del sistema incluidos, indefinidamente. No lo cazó nadie porque hace falta una
suscripción que caduque, y en `FakeEntitlementRepository` eso es una línea que ningún test escribía.

## Y un segundo hallazgo: no hay ningún worker

Tres KDoc y un test hablaban de *«el worker que arranca de cero cada quince minutos»* y de *«the same
function the worker calls»*. **No existe**: `grep -rln zoneSpotsWorthTelling` da dos ficheros (el use
case y la política) y no hay ningún `*ZoneWatch*Worker*`. La mirada periódica de
[ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] se sustituyó por el fanout del servidor
([ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]) y el texto se quedó. Los dos carriles
reales son **el stream local** y **el push** (`PushMessageHandler`), y siguen compartiendo la memoria
durable, que es lo que de verdad importaba del párrafo.

⚠️ Lo que NO se toca, porque es una pregunta de producto y no mía: los vetos de `PROVISIONAL` y
`RELEASING` justificaban su exclusión con *«el worker corre cada 15 min, la plaza ya no existiría»*. Con
entrega por push eso ya no aplica, y un `PROVISIONAL` de 12 minutos **sí** podría contarse a tiempo. El
veto sigue siendo correcto por su otra mitad (no es una plaza libre confirmada), pero si alguien quiere
avisar de provisionales, ése es un ticket con su decisión, no un descuido.

## Qué se cambió

| | |
|---|---|
| `ObserveZoneSpotAlertsUseCase` | vuelve `observeIsPlus` y el `combine`; KDoc reescrito: el gate está vivo, es la pausa por caducidad, y se dice que estuvo fuera del 21-09 al 27-09 y por qué eso fue grave |
| `DomainModule` | la dependencia, por nombre |
| `ObserveZoneSpotAlertsUseCaseTest` | `should_alertAFreeUserToo_because_theGateIsGone` → **`should_stayQuiet_when_theUserIsFree`** · `should_keepAnnouncingAndKeepItsMemory_when_theEntitlementChanges` → **`should_pauseAndResumeWithoutRepeating_when_theEntitlementChanges`** |
| `AppViewModelTest` | el gate en el fixture — su comentario ya prometía «entitlement FREE ⇒ stream vacío», y del 21-09 al 27-09 no era verdad |
| `zone-an-alert-is-delivered-where-you-can-see-it-001.md` | puntero de REVERTIDO. ⛔ No se reescribe: es un registro fechado |

⛔ **El fichero de tests tenía un KDoc describiendo la pausa por degradación y, tres líneas más abajo,
un cuerpo afirmando lo contrario.** Los dos escritos en el mismo commit. Es la forma más pura del
patrón: el texto se dejó porque «explica bien la regla», y la regla ya no era ésa.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `HomeViewModel.SetZoneNotify` · `SettingsViewModel.ToggleZoneAlertsNotif` | ✅ ya abrían el paywall: nunca dejaron de cobrar |
| `ZoneAlertSubscriptionSync` (índice del servidor) | ✅ ya gateaba en vivo por Plus, sin tocar |
| `PushMessageHandler` | ✅ exento a propósito: no menciona Plus ni debe. Su puerta es el índice, y añadir un chequeo local podría tirar un aviso legítimamente pagado si la caché de entitlement va retrasada |
| `functions/src/fanout.ts` | ✅ no gatea por plan y no le toca: sólo manda a quien está en el índice |
| `paywall_reason_zone_alerts` · `paywall_benefit_alerts` · `settings_plus_subtitle_free` | ✅ ya decían Plus |
| Ficha de Play (bloque PLUS, 9 locales) · `web/content/{es,en}.json` | ✅ ya lo vendían como Plus — por una vez el copy tenía razón y el código no |

## Verificación

- Suite completa: ver el resumen del commit.
- **Falsificado**: con `if (true || isPlus)` caen los DOS tests re-invertidos. Antes de este ticket ese
  testigo no existía — los dos tests estaban verdes afirmando lo contrario.
- ⛔ Sin cambios de copy y **sin resultado visual**: no hay captura que enseñar.

## iOS

Por construcción: el gate, el stream y la decisión están en `commonMain`. `ObserveIsPlusUseCase` ya se
resolvía en el grafo de las dos plataformas.
