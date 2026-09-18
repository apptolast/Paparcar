# NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001 · La tarjeta de guardado deja de preguntar lo ya respondido

**Estado:** 🔵 En revisión · rama `bugfix/NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001` ·
worktree `../PaparcarKMP-issue34` · cierra la **issue #34**

## Problema

Reportado por el product owner (#34): sale «¿Has aparcado?», pulsas **Sí**, y aparece otra
notificación pidiendo confirmar el aparcamiento.

## Qué pasa de verdad (medido en el código, no supuesto)

**No son dos ids.** `showParkingConfirmation` (la pregunta) y `showParkingSavedConfirm` (la tarjeta)
publican las dos en `PARKING_CONFIRMATION_NOTIFICATION_ID` (2002): la tarjeta MORFA la pregunta en
el mismo sitio, tal y como `REFACTOR-300` diseñó.

Son **una pregunta hecha dos veces**, y por dos motivos concretos:

1. La tarjeta reusa literalmente el botón de la pregunta: `R.string.notif_action_confirm` =
   **«Sí, confirmar»**. Acabas de pulsar ese botón y te lo vuelve a ofrecer. Encima el botón no
   confirma nada: va a `ACTION_ACK`, que es un acuse — el aparcamiento ya está guardado.
2. La tarjeta se publica con sonido, así que el móvil vuelve a avisar por algo que el usuario acaba
   de responder. (`setOnlyAlertOnce(true)` no evita el primer aviso de una notificación nueva.)

## Doctrina

Ninguna violada; una reforzada: *ante la duda se PREGUNTA, nunca se planta una plaza fantasma*. Esa
regla justifica la ventana de deshacer, **no** un segundo botón de confirmar. La tarjeta se escribió
para el camino SILENCIOSO (auto-confirm mientras te alejabas), donde sí es lo primero que te enteras.

## Diseño

`showParkingSavedConfirm` gana `userAnswered: Boolean`, que sale de `state.confirmation.userConfirmed`
— la respuesta de la propia sesión, no una conjetura sobre cuál fue la última notificación:

- **`true`** (el usuario dijo que sí): se cae el acuse y se publica **en silencio**. Su respuesta FUE
  el acuse.
- **`false`** (auto-confirm silencioso): se queda como estaba, porque es la primera noticia.
- **El deshacer sobrevive a los dos caminos**: decir «sí, he aparcado» no es decir de QUÉ coche, y
  para eso está «No, cancelar».
- **El acuse deja de mentir**: string nueva `notif_action_ack` («Vale» / «Got it»), en los 9 locales
  y en los DOS árboles de recursos que vigila `NotificationCopyParityTest`.
- **iOS**: en iOS una categoría ES sus botones, así que la variante sin acuse necesita categoría
  propia (`CATEGORY_PARKING_SAVED_REVERT_ONLY`), registrada en el set — sin eso la tarjeta saldría
  sin ningún botón.

## Consumidores auditados

| Llamante de `showParkingSavedConfirm` | Camino | Decisión |
|---|---|---|
| `DetectionEffectExecutor` | confirma la sesión viva | ✅ pasa `state.confirmation.userConfirmed` |
| `ParkingBackfillWorker` | reconciliación tardía, el user no ha respondido nada | ✅ default `false` |

## Criterio de éxito

- Responder «Sí» a la pregunta deja UNA tarjeta, sin botón de confirmar y sin segundo aviso.
- El auto-confirm silencioso sigue mostrando acuse + deshacer.
- El botón de deshacer sigue estando en los dos caminos.

## Verificación

- `should_not_ask_again_when_the_user_already_answered_the_ask` (nuevo) en
  `CoordinatorParkingDetectorTest`: 105 tests del fichero en verde.
