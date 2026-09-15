# UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001 · La ✕ de la cabecera de Browse no cierra nada

**Estado:** ✅ Done — en master. Verificado con `:app:compileProdDebugKotlin` +
`:app:compileMockDebugKotlin` y la suite completa (2.968 tests, 0 fallos) en local; el CI de la
organización sigue caído por facturación de Actions, así que un PR rojo no diría nada.

## Problema

En Home, con la hoja en Browse (sin pin seleccionado, sin modo pin), la cabecera pinta una **✕ de
cerrar a la derecha**. Pulsarla no hace **nada**: no cierra la hoja, no colapsa el peek, no
deselecciona. Visto por el user en device.

Un botón de cerrar en la esquina de la cabecera es la promesa más literal que hay en una UI. Aquí
promete una salida que no existe — y encima en el ÚNICO estado que no tiene nada de lo que salir:
Browse es el estado base de Home, el sitio al que vuelven todos los demás.

## Causa

`PapSheet` declara dos defaults que, juntos, dibujan un botón muerto:

```kotlin
onDismiss: () -> Unit = {},                              // PapSheet.kt:100 — no-op
trailing: PapSheetTrailing? = PapSheetTrailing.Dismiss,  // PapSheet.kt:120 — pinta la ✕
```

Es decir: **el default de `PapSheet` es pintar una ✕ que no hace nada**. Todos los peeks que
cierran algo (spot, parking, car park, gasolinera, adding-parking, adding-zone, report) pasan su
`onDismiss` y por eso allí la ✕ sí funciona; el default solo se nota en quien NO lo pasa.

`BrowsePeek` no lo pasaba, pero tampoco enseñaba la ✕: hasta el 14-09 sus tres ramas (coche en
ruta · coche aparcado · zona/carril) pasaban `trailing = locationWarning`, que era **`null` cuando
había fix** y un badge «SIN UBICACIÓN» cuando no. El trailing estaba ocupado, así que el default
nunca llegaba a aplicarse.

`c476e74d` (14-09, [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]) mudó ese aviso a la píldora del
mapa y borró las tres líneas `trailing = locationWarning`. Al quedarse sin el parámetro, las tres
llamadas heredaron el default `Dismiss` → apareció la ✕, cableada al `onDismiss = {}` por defecto.

Es una regresión que el borrado no podía ver: quitar un parámetro no enseña qué valor pasa a ocupar
su sitio.

## Doctrina violada

- **«Al quitar un botón, borrar limpio»** (`feedback_no_silent_overload`): aquí pasó lo contrario —
  al quitar el badge apareció OTRO control en su hueco, sin que nadie lo pidiera.
- **Copy/controles sin mecánica interna**: la ✕ dice «esto se cierra». Si no se cierra, miente.
- **Sistemas, no parches**: el parche es `trailing = null` ×3. La causa es el DEFAULT, y ese default
  sigue armado para la próxima cabecera que se escriba.

## Diseño — la ✕ no puede existir sin acción

Hoy la ✕ se decide en **dos** parámetros independientes (`trailing` dice si se dibuja, `onDismiss`
dice qué hace) y ninguno obliga al otro. Cualquier combinación compila, incluida la única que no
tiene sentido: dibujar sin acción.

El invariante es uno solo — **una ✕ existe si y solo si alguien dio un cierre** — así que se escribe
en UN sitio, el tipo:

```kotlin
onDismiss: (() -> Unit)? = null,   // null ⇒ esta hoja no se cierra ⇒ no hay ✕
```

y `trailing` / `PapSheetTrailing` desaparecen. `PapSheetTrailing` ya solo tenía `Dismiss` desde que
`c476e74d` borró `Warning`: un sealed interface de un miembro que duplica la pregunta que
`onDismiss` ya responde.

Con eso, el fallo deja de ser un bug y pasa a ser **inexpresable**: no hay forma de pedir la ✕ sin
pasar la lambda que la hace funcionar. Ese es el testigo — no hay test de Compose en el repo
(`createComposeRule` no aparece en ninguna suite), y un guard de Konsist sobre "pasa siempre
`trailing`" sería más frágil que el propio tipo.

## Consumidores auditados

| Call site | Antes | Después |
|---|---|---|
| `SpotPeek:116` | `onDismiss = { SelectItem(null) }` + default `Dismiss` | igual — ✕ viva |
| `ParkingPeek:84` | ídem | igual — ✕ viva |
| `PublicParkingPeek:74` | ídem | igual — ✕ viva |
| `FuelStationPeek:96` | ídem | igual — ✕ viva |
| `ReportPeek:50` | `onDismiss = { ExitReportMode }` | igual — ✕ viva |
| `AddingParkingPeek:136` | `onDismiss = { ExitAddParkingMode }` | igual — ✕ viva |
| `AddingZonePeek:151` | `onDismiss = { ExitAddZoneMode }` | igual — ✕ viva |
| `BrowsePeek:152 / :219 / :279` | sin `onDismiss` → **✕ muerta** | sin `onDismiss` → **sin ✕** ✅ |
| `ParkingHistoryDetailScreen:666` | `trailing = null` explícito (es una pantalla, sale por el back) | sin `onDismiss` → sin ✕, mismo resultado sin el parámetro |

Los `onDismiss` de `AddingZonePeek:214/220/265` y `AddingParkingPeek:237` son de diálogos de
confirmación, no de `PapSheet` — fuera de alcance.

## Criterio de éxito

- En Browse (las tres ramas: conduciendo, coche aparcado, zona/carril) **no hay ✕**.
- Los siete peeks que cierran algo conservan la suya y sigue cerrando.
- El detalle de historial sigue sin ✕ (su salida es el back de la top bar).
- Ya no es posible escribir una `PapSheet` con ✕ y sin cierre: no compila.
- Suite verde + `assembleMockDebug`/`compileProdDebugKotlin`.

## Fuera de alcance

Nada. Ninguna pantalla nueva, ningún estado nuevo → no toca galería de estados ni `MockScenario`
(la cabecera de Browse ya está representada y el cambio es una ausencia, no una variante).
