# ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001 · el código dice gratis y los interruptores cobran

**Estado:** 📋 Abierto (27-09-2026) · sin rama. Follow-up deliberado de
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

## Decisión que hace falta antes de tocar nada

⛔ **No es una tarea de limpieza, es una pregunta de producto:** ¿el aviso de zona es Plus (lo que hace
hoy) o free (lo que dice el código)? Las dos salidas son legítimas y opuestas:

- **Es Plus** → el KDoc vuelve a decir la verdad, el gate del stream se restaura (defensa en
  profundidad: hoy el stream emitiría para un free si alguien tocara un interruptor) y los dos tests
  se vuelven a invertir.
- **Es free** → caen los dos gates de los interruptores, y entonces hay que mirar el pitch del paywall
  (`paywall_reason_zone_alerts`, `paywall_benefit_alerts`, `settings_plus_subtitle_free`) **y la ficha
  de Play y la web**, que hoy lo venden como Plus en los 9 idiomas.
  [PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001]

## Consumidores a auditar

`paywall_reason_zone_alerts` · `paywall_benefit_alerts` · `settings_plus_subtitle_free` ·
`settings_notif_zone_alerts_desc` · `LISTING-v1.3.0.md` (bloque PAPARCAR PLUS, 9 locales) ·
`web/content/{es,en}.json` · `docs/backlog/plus-alerts-and-tiers-001.md`.
