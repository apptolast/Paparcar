# UI-ZONE-ALERTS-ARE-CONFIGURED-IN-SETTINGS-001 · la campana sale de la cabecera de Home: los avisos se configuran en Ajustes

**Estado:** ✅ Done — en master

## Problema

Decisión del user (09-09-2026): **habrá más tipos de aviso**, y todos se configurarán desde Ajustes.
Una campana en la cabecera del mapa que sólo gobierna UNA clase de aviso —el de plaza libre en tus
zonas— deja de tener sentido en cuanto haya una segunda: o crece hasta ser un segundo centro de
ajustes flotando sobre el mapa, o miente por omisión sobre los avisos que no gobierna.

No es un bug: es que el sitio correcto de ese control ya existe.

## Doctrina violada

Ninguna — es una decisión de producto. Lo que sí manda doctrina es **cómo** se quita:
`⛔ al quitar un botón, borrar limpio` — nada de dejar el intent, el estado y el diálogo huérfanos
"por si vuelve". Un `zoneAlertsDialogOpen` que ya nadie pone a `true` es exactamente el `isLoading`
muerto que se vio en `UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001`: código que parece
vivo y no lo está.

## Señales / datos disponibles

Verificado antes de borrar nada — **la función no se queda sin puerta**, tiene dos:

1. **Interruptor maestro**, ya en Ajustes → Notificaciones: `settings_notif_zone_alerts` con su
   `PapSwitchRow` y su soft-gate al paywall (`SettingsIntent.ToggleZoneAlertsNotif`,
   `SettingsViewModel.kt:158`). Es el que decide si los avisos de zona suenan.
2. **Elección por zona**, ya en el modal de cada zona (el lápiz del chip): `ZoneNotifyToggle` →
   `HomeIntent.SetZoneNotify` (`AddingZonePeek.kt:172`), que persiste `Zone.notifyOnSpot` por el
   MISMO camino que usaba el diálogo (`saveOrUpdateZone`).

O sea que el diálogo de la campana era una **tercera** superficie para un flag que ya se escribe
desde donde vive la zona. Quitarlo no resta capacidad: resta un duplicado.

## Diseño

Borrado completo de la vía, no ocultación:

- fuera el `MapCircleFab` de la campana en `HeaderZoneChips` y el parámetro `onZoneAlertsClick` que
  lo alimenta (dos niveles de `HomeHeaderSection`)
- fuera `ZoneAlertsDialog.kt` entero, su render en `HomeScreen` y su entrada en la galería mock
- fuera los tres intents (`OpenZoneAlerts`, `CloseZoneAlerts`, `ToggleZoneAlert`), su despacho, el
  handler `toggleZoneAlert()` y el campo de estado `zoneAlertsDialogOpen`
- fuera las 4 keys `home_zone_alerts_*` en los 9 locales

**Lo que NO se toca**: `Zone.notifyOnSpot` (el dato sigue siendo per-zona y sigue teniendo su
interruptor), el gate de Plus, ni `PaywallReason.ZONE_ALERTS` — que lo siguen usando el maestro de
Ajustes y el toggle del modal de zona.

## Criterio de éxito

1. La cabecera de Home ya no muestra la campana; la fila de chips arranca directamente en las zonas
   y termina en el "+".
2. Los avisos de zona se siguen pudiendo encender/apagar: maestro en Ajustes, por-zona en el lápiz.
3. Cero referencias muertas: `grep -rn "ZoneAlerts"` fuera de `settings/` y `paywall/` no devuelve nada.
4. `:shared:testDebugUnitTest` + `assembleMockDebug` + `compileProdDebugKotlin` verdes.

## Consumidores auditados

`grep -rn "ZoneAlerts\|onZoneAlertsClick\|showZoneAlerts\|ToggleZoneAlert"` sobre `shared/src` + `app/src`:

| Sitio | Qué era | Acción |
|---|---|---|
| `HomeHeaderSection.kt:75,124,148,162` | param + item campana | 🗑 borrado |
| `HomeScreen.kt:62,208,1005` | import, render del diálogo, callback | 🗑 borrado |
| `ZoneAlertsDialog.kt` | el diálogo entero | 🗑 fichero borrado |
| `HomeIntent.kt:149-151` | 3 intents | 🗑 borrados |
| `HomeViewModel.kt:329-331,702-709,723-743` | despacho + handlers + `toggleZoneAlert()` | 🗑 borrados |
| `HomeState.kt:260` | `zoneAlertsDialogOpen` | 🗑 borrado |
| `HomeViewModelTest.kt:1804-1857` | 3 tests del diálogo | 🗑 borrados con su vía |
| `StateGalleryScreen.kt:112,1685,1688` | 2 variantes en la galería | 🗑 borradas |
| strings `home_zone_alerts_*` ×4 ×9 locales | copy del diálogo | 🗑 borradas |
| `SettingsScreen/ViewModel/Intent` · `PaywallScreen` | maestro + paywall | ✅ **intactos** — son el destino, no la víctima |
| `AddingZonePeek` · `SetZoneNotify` · `Zone.notifyOnSpot` | el por-zona | ✅ **intacto** |
| `galleryZones` (`StateGalleryScreen.kt:340`) | fixture que SÓLO alimentaba al diálogo | 🗑 borrado — quedó sin un solo uso |

### El test que no se podía borrar sin más

`should_writeTheZoneFlagThroughTheFormPath_when_plusTogglesFromTheDialog` era **la única** prueba
en toda la suite de que `notifyOnSpot` llega al repositorio (`grep notifyOnSpot` en
`HomeViewModelTest` daba exactamente una aserción). Borrarlo con su intent habría dejado el flag
sin testigo, y el escenario —"un Plus enciende la alerta de UNA zona y queda escrita"— **sigue
existiendo**: sólo cambió de puerta. Reescrito sobre la puerta que queda:
`should_writeTheZoneAlertFlag_when_plusEnablesItInTheZoneForm`
(`EnterEditZoneMode` → `SetZoneNotify(true)` → `ConfirmAddZone`).

## Auditoría de Ajustes — ¿están todas las alertas que hacen falta?

Petición del user en el mismo turno. Inventario de TODO lo que la app puede notificar
(`AppNotificationManager`, 14 entradas) contra lo que Ajustes gobierna:

| Aviso | ¿Silenciable? | Dónde se gobierna |
|---|---|---|
| `showParkingSaved` — "aparcamiento guardado" | sí, informativa | ✅ Ajustes → `settings_notif_parking` (leído en `AppNotificationManagerImpl.kt:111`) |
| `showZoneSpotAlert` — plaza libre en tu zona | sí, Plus | ✅ Ajustes → `settings_notif_zone_alerts` (maestro, leído en `ObserveZoneSpotAlertsUseCase.kt:88`) **+** `Zone.notifyOnSpot` por zona, en el modal de la zona |
| `showParkingConfirmation` · `showParkingSavedConfirm` · `showStillParkedPrompt` · `showMarkParkingNudge` · `showFirstParkNudge` | **no** | Son PREGUNTAS. Doctrina de fallo asimétrico: ante la duda se pregunta. Un interruptor que las calle convierte un falso negativo en silencio permanente |
| `showPermissionRevoked` · `showBackgroundReliabilityWarning` · `showConfirmationFailed` | **no** | Avisos de SALUD: si se silencian, la detección muere sin que nadie se entere |
| FGS de detección/centinela (`DETECTION_CHANNEL`, `SENTRY_CHANNEL`) | **no** | Los exige Android para un foreground service |
| `showDebug` (`DEBUG_CHANNEL`) | — | Sólo builds debug |

**Conclusión: Ajustes ya tiene los dos únicos interruptores que deben existir hoy**, y ambos están
verificados como LEÍDOS (no son preferencias muertas — que fue el bug de
`SETTINGS-AUDIT-REMEDIATION-001`). Quitar la campana no deja ninguna alerta sin gobierno.

**Lo único que Ajustes NO tiene es la lista POR ZONA** (qué zonas avisan). Hoy vive en el modal de
cada zona, que es un sitio legítimo — la zona se configura donde se edita la zona. Si al llegar el
segundo tipo de aviso se quiere centralizar también eso, es una pantalla nueva
(Ajustes → Avisos), y va en su propio ticket: **decisión del user, aún sin tomar**.

## Ejecutado

- Campana + `onZoneAlertsClick` fuera de `HomeHeaderSection` (dos niveles) y de `HomeScreen`
- `ZoneAlertsDialog.kt` borrado; 3 intents, `zoneAlertsDialogOpen`, `toggleZoneAlert()` y el
  despacho, borrados
- 2 variantes de galería + `galleryZones` borrados; 4 keys × 9 locales borradas
- 3 tests borrados, 1 reescrito sobre la puerta superviviente

### Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` + `:shared:testDebugUnitTest`
  → **BUILD SUCCESSFUL**
- `grep -rn "ZoneAlerts"` fuera de `settings/` y `paywall/` → **cero**
- ⏳ falta verlo en mano
