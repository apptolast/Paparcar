# UI-A-SHEET-THAT-ASKS-FOR-THE-MAP-MUST-NOT-EAT-IT-001 · los dos interruptores de la zona bajan al diálogo de guardar

**Estado:** ✅ Done — en master · ⏳ pendiente de verse en device

## Problema

La hoja de alta de zona pide *«Centra el mapa en tu zona habitual · Ajusta el radio hasta que la
cubra entera»* y acto seguido se come el mapa que hay que centrar.

Medido sobre la captura del user (14-09-2026, device de 1080×2400 @ density 3 ⇒ 360×768 dp):

| bloque | dp |
|---|---:|
| cabecera (lead + eyebrow + título + ×) | 82 |
| banner «Centra el mapa…» | 64 |
| fila de iconos | 52 |
| RADIO + slider | 64 |
| **`ZonePrivacyToggle` + `ZoneNotifyToggle` + sus spacers** | **88** |
| botón + aire inferior | 80 |
| **total hoja** | **462 dp (60% de la pantalla)** |
| **mapa visible** | **222 dp (29%)** |

## Doctrina violada

Ninguna regla escrita, pero sí la lectura que sostiene el reparto hoja/diálogo de
[UI-ZONE-MANAGE-001](ui-zone-manage-001.md): *el nombre no está en la hoja porque es la ÚLTIMA
decisión, y se pregunta en el diálogo de confirmación una vez el sitio y el radio ya están puestos.*

Esa frase no habla del nombre, habla del **criterio**: la hoja es lo que se decide MIRANDO el mapa
(dónde · qué icono · cuánto radio), y el diálogo es el paso de guardar — lo que define a la zona
guardada. Privacidad y aviso son lo segundo y estaban en lo primero, pagando 88 dp de mapa.

## Señales / datos disponibles

Las dos cosas que podían tumbar el movimiento, comprobadas ANTES de tocar código:

1. **El paywall del aviso** (`HomeViewModel.kt:1294`): activarlo en free manda
   `HomeEffect.NavigateToPaywall(ZONE_ALERTS)` → `onOpenPaywall` en `HomeScreen.kt:259`, que es una
   **pantalla completa**, no un modal. ⇒ no hay modal sobre modal.
   ⛔ Pero `naming` era un `remember` normal: al volver del paywall el diálogo no estaba. Con los
   interruptores dentro, eso pasa de rareza a agujero del flujo ⇒ `rememberSaveable`.
2. **El teclado**: `PapAlertDialog` ya inserta por IME y hace scroll de su columna
   (`PapAlertDialog.kt:169-173`, de `SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001`) ⇒ el diálogo
   puede crecer sin esconder ni el campo ni las acciones.

## Diseño

`AddingZonePeek` deja en la hoja **sólo lo acoplado al mapa** y `ZoneNameDialog` pasa a ser lo que
ya era de verdad: el paso de guardar, con las tres cosas que definen la zona.

```
HOJA                              DIÁLOGO (paso de guardar)
  banner · iconos · RADIO           nombre  ── divisor ──  🔒 privada · 🔔 aviso
  [ Guardar zona ] ───────────────► [ Guardar zona ]
```

- `naming` → `rememberSaveable`: el viaje al paywall y la vuelta conservan el diálogo, y el
  formulario ya vivía en el VM.
- Las dos filas se mueven **tal cual** (mismo icono de 18 dp, mismo copy, mismos intents): esto es
  una reubicación, no un rediseño de la fila.
- **Cero strings nuevos** — las tres keys ya existen en los 9 locales.

## Criterio de éxito

- La hoja baja de 462 a ~374 dp; el mapa pasa de 222 a ~310 dp (+40%) en el mismo device.
- Privacidad y aviso siguen siendo alcanzables en alta y en edición, con los mismos intents.
- Activar el aviso en free abre el paywall y, al volver, **el diálogo sigue ahí** con lo escrito.
- `:shared:testDebugUnitTest` + `compileProdDebugKotlin`/`compileMockDebugKotlin` en verde.

## Consumidores auditados

- `HomePeekHandle.kt:172` — único consumidor de `AddingZonePeek`. No cambia su firma ⇒ cerrado.
- `StateGalleryScreen.kt` / `DevCatalogScreen.kt` — `AddingZonePeek` **no** está en la galería hoy y
  este ticket no crea pantalla, estado ni ruta nueva ⇒ exento (no aplica la regla de Dev Catalog).
- `HomeIntent.SetZoneIsPrivate` / `SetZoneNotify` — mismos dos emisores, sólo cambia desde qué
  composable se emiten ⇒ cubierto.
- `SettingsViewModel.ToggleZoneAlertsNotif` (interruptor maestro de Ajustes) — intacto, sigue siendo
  la otra puerta descrita en
  [UI-ZONE-ALERTS-ARE-CONFIGURED-IN-SETTINGS-001](ui-zone-alerts-are-configured-in-settings-001.md).

## Fuera de alcance (deliberado)

- **Canonizar las dos filas contra `PapSwitchRow`** (regla `UI-LIST-ITEM-001`): el tile de 40 dp de
  `PapIconTile` engorda cada fila dentro de un diálogo estrecho. Se mueven como están; canonizarlas
  es un cambio de aspecto, no de sitio.
- **El banner a una línea** (−18 dp más) y el título del diálogo, que sigue hablando sólo del nombre
  («Ponle nombre a la zona») aunque el diálogo ahora guarde tres decisiones. Retocarlo son 2 keys ×
  9 locales; queda a decisión del user.
- **Dónde vive a la larga la elección por zona del aviso** — sigue abierta en
  [UI-WHERE-DOES-THE-PER-ZONE-ALERT-LIST-LIVE-001](ui-where-does-the-per-zone-alert-list-live-001.md);
  este ticket no la decide, sólo cambia de superficie dentro del mismo flujo de la zona.
