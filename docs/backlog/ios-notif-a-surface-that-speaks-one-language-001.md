# IOS-NOTIF-A-SURFACE-THAT-SPEAKS-ONE-LANGUAGE-001 · las notificaciones iOS hablan los 9 idiomas

**Estado:** ✅ Done (06-09-2026) · plegado en la rama iOS (PR #3) · ⏳ declarados: veredicto CI
(accessors en Native + simulador) y device en español (compañero). Follow-ups anotados: unificar
Android sobre composeResources (retira el espejo Y su test juntos) · deep-link fino de
«Marcar aparcamiento» al flujo de añadir.

## Problema

`IosAppNotificationManagerImpl` hardcodea toda su copy en inglés (su propio KDoc lo declara
deuda desde junio) mientras Android sirve `notif_*` en 9 locales desde `app/src/main/res`. Y el
gap de paridad real de la auditoría: `showParkingSaved()` en iOS **ignora el ajuste
`notifyParkingDetected`** que Android sí cablea [SETTINGS-AUDIT-REMEDIATION-001] — el usuario
apaga el toggle y iOS le notifica igual.

Segundo desvío detectado al abrir: el prompt still-parked de F2.4 inventó acciones («Still
parked») que **Android no tiene** — sus acciones reales son `notif_action_ive_left` +
`notif_action_mark_parking`. Copy inventada = deriva de producto entre plataformas.

## Diseño

1. **Las 20 keys `notif_*` del subset iOS, ESPEJADAS de `app/src/main/res` a
   `composeResources` en los 9 locales** — misma copy, cero traducción nueva; apóstrofos
   DES-escapados al copiar (`\'` → `'`: Android res escapa, Compose Resources no
   [COPY-APOSTROPHE-IS-NOT-ESCAPED-001]). ⚠️ Duplicación DECLARADA y blindada:
   **Android sigue siendo la fuente viva** (su superficie está field-tested y 1.0.0 publicada —
   no se toca en este ticket); `NotificationCopyParityTest` (androidUnitTest, parsea ambos
   sistemas de ficheros) pone ROJO cualquier deriva, con testigo de población (20 keys × 9
   locales). El follow-up de unificar Android sobre composeResources queda anotado, no absorbido.
2. **iOS impl** lee vía `getString(Res.string.notif_*)` (suspend, con format-args para las
   variantes vehicle/street) — cada post lanza en scope y espera a que las categorías (cuyos
   títulos de acción también son recursos) estén registradas.
3. **Gate**: `if (!appPreferences.notifyParkingDetected) return` en `showParkingSaved` — el mismo
   choke point que Android; las notificaciones del mecanismo anti-FP (confirmación, saved-confirm,
   still-parked) siguen SIN gate por doctrina, igual que allí.
4. **Still-parked vuelve a la copy de Android**: «Me he ido» (→ departure WITNESSED, ya cableado
   en F2.4) + «Marcar aparcamiento» (foreground: abre la app — el deep-link fino al flujo de
   añadir es follow-up). La acción inventada desaparece.

## Criterio de éxito

- Suite JVM (con el test de paridad nuevo) + simulador + `xcodebuild` verdes.
- En un device en español, las notificaciones iOS salen en español (compañero).

## Consumidores auditados

- `AppNotificationManager` (puerto): contrato SIN cambios — los llamadores commonMain
  (coordinator, mesh, honest close…) convergen intactos. Android (`:app`) intacto.
- `FakeAppNotificationManager` (mock/boundary de los verify): sin cambios (defaults del puerto).
- `IosNotificationActionHandler`: `ACTION_STILL_PARKED` (inventada en F2.4) sustituida por
  `ACTION_MARK_PARKING` (la acción real de Android; foreground abre la app — deep-link fino al
  flujo de añadir = follow-up anotado).
- `LocaleParityGuardrailTest` (composeResources 9 locales): las 20 keys aterrizan en los 9 → verde.
- `NotificationCopyParityTest` NUEVO: blinda el espejo Android↔composeResources por key y locale
  (con des-escape normalizado y testigo de población ≥20). Se retira CON la duplicación cuando
  Android migre, nunca por separado.
- Gate `notifyParkingDetected`: mismo choke point que Android; superficies anti-FP sin gate por
  doctrina (comentado en ambos impls).

## Ejecutado (06-09, pendiente de plegar)

20 keys × 9 locales espejadas (apóstrofos des-escapados) · impl iOS reescrito sobre
`getString(Res.string.notif_*)` con categorías await-registradas · gate cableado · acciones del
still-parked en paridad con Android · `NotificationCopyParityTest` 2/2 · suite JVM completa +
verify mock + compiles verdes. El compile de los accessors en Native y el simulador los juzga CI.
