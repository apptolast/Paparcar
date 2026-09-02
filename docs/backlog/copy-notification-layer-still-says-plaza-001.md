# COPY-NOTIFICATION-LAYER-STILL-SAYS-PLAZA-001 · La capa de notificaciones nunca se barrió

**Estado:** ✅ Done (2026-09-02) · sin código, solo `app/src/main/res`

## Problema

[COPY-SPOT-IS-NOT-A-PARKING-001] separó los dos conceptos —**plaza** es de la comunidad,
**aparcamiento** es tuyo— y prohíbe literalmente la frase *«Marcar mi plaza»*. El barrido tocó
`shared/src/commonMain/composeResources/`, pero **`app/src/main/res/` es la otra superficie de
strings** ([I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001] ya avisó de que son dos) y se
quedó fuera. Las notificaciones siguen usando la palabra de lo ajeno para lo propio:

| Key | EN | ES | Debería decir |
|---|---|---|---|
| `notif_first_park_nudge_action` | Mark my spot | **Marcar mi plaza** | *Mark parking* / *Marcar aparcamiento* |
| `notif_mark_parking_action` | Mark my spot | **Marcar mi plaza** | idem |
| `notif_mark_parking_text` | …Mark the spot so you don't lose it. | …Marca la plaza para no perderla. | el aparcamiento, no la plaza |
| `notif_confirmation_failed_text` | Could not save your parking spot. | No se pudo guardar tu plaza. | tu aparcamiento |

Las tres primeras son especialmente visibles: son botones que el usuario pulsa para marcar **su**
coche, con la palabra que en el resto de la app significa *hueco libre de otro*.

⚠️ No confundir con los usos correctos del mismo fichero: *«free your spot for the community»*
(`notif_detection_explainer`, `notif_first_park_nudge_text`, `notif_sentry_text`) sí habla de la
plaza que liberas al irte, y se queda como está.

## Por qué no entró en su ticket de origen

`DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001` reescribió `notif_still_parked_text` en los 9 locales y
añadió `notif_action_mark_parking` ya con el vocabulario correcto, pero su invariante es otro (cuándo
puede anunciarse una plaza). Mezclar un barrido de vocabulario habría metido dos historias en un
mismo commit.

## Alcance

4 keys × 9 locales en `app/src/main/res/`. Cero código. ⚠️ Aquí el apóstrofo **sí** se escapa (`\'`):
son recursos Android, no Compose Resources — [COPY-APOSTROPHE-IS-NOT-ESCAPED-001] aplica sólo a los
segundos, y `notif_mark_parking_text` lleva uno en EN.

## Criterio de éxito

`grep -rn "my spot\|mi plaza" app/src/main/res/` no devuelve nada, y `LocaleParityGuardrailTest`
sigue verde.

## Cierre (2026-09-02)

**El censo del doc (4 keys) estaba corto: eran 5.** La criba de «spot» restantes destapó
`notif_confirmation_text` — *«Shall we confirm the spot?»* / *«¿Confirmamos la plaza?»* — que
pregunta por TU aparcamiento con la palabra comunitaria, en los 9 locales. Cuarta vez que «auditar
antes de implementar» amplía el alcance.

- 5 keys × 9 locales barridas al vocabulario de la tabla de CLAUDE.md, reutilizando literal el
  wording del key ya correcto (`notif_action_mark_parking`) para los botones. Apóstrofos: aquí SÍ
  escapados (`\'`) — recursos Android, no Compose Resources.
- Los usos correctos del mismo fichero quedan intactos (la plaza que LIBERAS: `notif_detection_explainer`,
  `notif_first_park_nudge_text`, `notif_sentry_text`, `channel_upload_desc`).
- Fuera de alcance a propósito: `channel_detection_name` («Spot Detection», metadato de canal en
  Ajustes de Android) — borde, no es un botón que el user pulse sobre lo suyo.
- Verificado: grep del criterio limpio · `LocaleParityGuardrailTest` **5/5 con `--rerun-tasks`**
  (testigo de ejecución comprobado en el XML) · `processProdDebugResources` +
  `processMockDebugResources` en verde.
