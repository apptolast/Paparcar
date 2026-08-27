# SETTINGS-AUDIT-REMEDIATION-001 · Ajustes que mienten: cablear, borrar o tokenizar cada control de Settings

**Estado:** ✅ Done (28-08-2026) — mergeado a master vía squash; los "cambios visuales deliberados"
de abajo quedan como checklist de revisión en device post-merge.

## Problema
La auditoría integral de SettingsScreen (27-08-2026) encontró que la pantalla promete cosas que el
código no cumple, y que es el outlier del sistema de diseño:

1. **Los dos toggles de notificación son decorativos.** `AppNotificationManagerImpl` recibe
   `AppPreferences` inyectado pero jamás consulta `notifyParkingDetected` ni `notifySpotFreed`
   antes de `notify()`. Peor: la notificación de "plaza libre cerca" que el segundo toggle dice
   controlar **no existe en ninguna parte** (no hay FCM ni ningún poster de plazas).
2. **El fallo de borrar cuenta es invisible.** `SettingsScreen` recibe
   `SettingsEffect.ShowError` y lo tira (`/* error handled via state */` — falso): si
   `DeleteAccountUseCase` falla, el usuario solo ve desaparecer el spinner.
3. **Settings está al 0% del sistema de botones** (`RoundedCornerShape(10.dp)` ×4 sin token,
   `44.dp` de alto cuando toda la app usa 48) y sus 4 alphas de texto privadas
   (0.55/0.5/0.3/0.38) están duplicadas en ~16 ficheros más con 3 valores en conflicto.
4. Filas canónicas (`SwitchRow`/`NavRow`/`InfoRow`) encerradas como `private` en Settings →
   `VehicleRegistrationScreen` las reimplementó a mano (switch-row idéntica, icon-tile de 38dp
   circular vs `PapIconTile` 40dp r12). Dos tratamientos destructivos incompatibles. Barra
   inferior de acción clonada verbatim en Bluetooth y VehicleRegistration.
5. Los `Switch` no tienen nombre accesible (TalkBack anuncia "interruptor" sin decir cuál).

## Doctrina violada
- «Cero catch silenciosos» (error handling obligatorio de CLAUDE.md) — punto 2.
- «Sistemas, no parches» + [UI-LIST-ITEM-001] — puntos 3-4: el invariante visual vive copiado en
  N ficheros en vez de en el theme/componente.
- «No copy al usuario que promete lo que el código no hace» (espíritu de
  COPY-SPOT-IS-NOT-A-PARKING-001) — punto 1.

## Señales / datos disponibles
- Grep exhaustivo: `notifyParkingDetected`/`notifySpotFreed` solo se leen en SettingsViewModel,
  fakes e impls de prefs. Ningún poster de notificaciones las consulta.
- `paparcar.app` no resuelve DNS (verificado 27-08) → las filas legales quedan FUERA de este
  ticket (bloqueadas por publicar el dominio; ver "Fuera de alcance").
- Informe de consistencia con fichero:línea de cada divergencia (conversación de la auditoría).

## Diseño
El SISTEMA, no el parche:

- **Notificaciones**: el gate vive en UN choke point — el adapter que postea
  (`AppNotificationManagerImpl.showParkingSaved`), que ya tiene `AppPreferences` inyectado.
  Gobierna SOLO las notificaciones informativas; las preguntas de seguridad
  (`showParkingConfirmation`, `showParkingSavedConfirm`, `showStillParkedPrompt`,
  `showMarkParkingNudge`) NUNCA se silencian desde app — son el mecanismo anti-FP (el usuario
  debe poder revertir un pin). `notifySpotFreed` se borra entero (interface + impls + fakes +
  UI + strings): prometía una feature inexistente. El master OR-derivado muere con él.
- **Alphas**: `PapAlpha` en `ui/theme/` (subtitle 0.55 · muted 0.5 · disabled 0.38 · dim 0.3 ·
  body 0.65) + barrido de TODAS las constantes privadas equivalentes. Sin cambios de valor
  (cero diff visual en el barrido); las divergencias reales (0.65 vs 0.55 en subtítulos de
  card) se conservan como dos tokens distintos y quedan anotadas.
- **Filas**: `PapSwitchRow` / `PapNavRow` / `PapInfoRow` en `ui/components/`, construidas sobre
  `PapListItem`, con semántica accesible de serie (`toggleable` + merge en la switch-row).
  Settings y VehicleRegistration las consumen; se borran las copias a mano.
- **Destructivo**: `PapDangerCard` única (valores canónicos = los de Settings).
- **Barra inferior**: `PapBottomActionBar` única (Surface elev 8 + navigationBarsPadding).
- **Geometría**: `PapShapes.button` (10dp) para el botón in-card; alturas 44→48; Settings adopta
  `PaparcarSpacing` (16→lg, 8→sm, 10→sm).

## Criterio de éxito
- Apagar "Aparcamiento detectado" en Settings suprime la notificación informativa de
  aparcamiento guardado y NO suprime ninguna pregunta. ⚠️ El gate vive en el adapter Android
  (`AppNotificationManagerImpl.showParkingSaved`) — no unit-testeable sin Robolectric; se
  verifica por el barrido de posters (abajo) + en device.
- No queda ninguna referencia a `notifySpotFreed` en el repo. ✅ (grep = 0)
- Fallo de borrado de cuenta → snackbar visible. ✅
- Constantes de alpha de texto barridas → alias de `PapAlpha` (mismo valor, cero diff visual);
  las que quedan con literal son conceptos NO de texto (bordes, barras, mapa) o valores únicos
  (`ACTIVE_META_ALPHA` 0.6, `META_VALUE_ALPHA` 0.7, hero alphas). ✅
- TalkBack: `PapSwitchRow` hace la fila entera `toggleable` con `Role.Switch` (el Switch es
  visual, `onCheckedChange = null`). ✅
- `testProdDebugUnitTest` + `compileMockDebugKotlinAndroid` + `compileProdDebugKotlinAndroid`
  en verde. ✅ (28-08)

## Consumidores auditados
**Posters de notificaciones** (¿a quién gobierna `notifyParkingDetected`?):
- `showParkingSaved` — informativa ("aparcamiento guardado") → **GATED** en el adapter.
  Llamantes: `BluetoothParkingDetector:172,201`, `SaveManualParkingUseCase:92` — ninguno asume
  que la notificación exista (fire-and-forget). ✅
- `showParkingConfirmation` / `showParkingSavedConfirm` / `showStillParkedPrompt` /
  `showMarkParkingNudge` — preguntas/seguridad anti-FP → EXENTAS a propósito.
- `showFirstParkNudge` — nudge de arranque con su propio cap/cooldown → exento (no es
  "aparcamiento detectado").
- `showPermissionRevoked` / `showConfirmationFailed` / `showDebug` / FGS — estado del sistema →
  exentas.
- **iOS** (`IosAppNotificationManagerImpl.showParkingSaved`) — stub sin DI de prefs; gap de
  paridad ANOTADO en el propio fichero, se cablea cuando se levante el stack iOS real (no se
  toca el grafo DI de iOS en este ticket).

**Lectores de `notifySpotFreed`**: solo Settings VM/State/UI, fakes e impls — todos borrados;
la key vieja queda inerte en DataStore/NSUserDefaults (comentado en la impl Android).

**Filas re-implementadas barridas**: `VehicleRegistrationScreen` (switch-row y BT icon-tile →
`PapSwitchRow`/`PapListItem`+`PapIconTile`) · barras inferiores de `BluetoothConfigScreen` y
`VehicleRegistrationScreen` → `PapBottomActionBar` · zona destructiva de ambos →
`PapDangerCard`. Quedan (follow-up, fuera de alcance): `OnboardingStep`,
`DetectionTierStatusCard` (eyebrow), `SetActiveRow`, spine duplicado del SizeExplainer,
cabeceras crudas de `HistoryContent`.

## Cambios visuales deliberados (revisar en device)
- Botones de Settings: 44→48dp de alto (Logout, Eliminar cuenta).
- Zona delete de VehicleRegistration: fondo 0.3→0.15, borde thin@0.4→medium@0.7 (canónico).
- BT row de VehicleRegistration: tile 38dp circular → `PapIconTile` 40dp r12.
- "IMPROVE DETECTION" (MiniHeader): rol `label` → `subsectionHeader` (dense).
- Separación entre tarjetas de Settings: 10dp → 8dp (`PaparcarSpacing.sm`).
- Subtítulo de la danger zone: alpha 0.6 → 0.65 (`PapAlpha.body`).
- Diálogos de arranque (offline/fatal) → molde `PapAlertDialog` con hero y CTA con icono.
- Notificaciones en Settings: master + sub "plaza libre" desaparecen; queda un único switch
  "Aparcamiento detectado" (bloqueado, no falseado, con la detección OFF).

## Decisión añadida (28-08, pedida por el user)
- **Fuera la `SharedPreferencesMigration` legacy** de `AndroidDataStoreAppPreferences`: con la
  flota en Room v1 (reset asumido, pre-beta) ningún device conserva un `paparcar_prefs` de
  SharedPreferences sin migrar, y las instalaciones nuevas nunca tuvieron ese fichero.
- **Los stores de detección NO migran a DataStore**: SharedPreferences síncrono a propósito
  (commit que sobrevive a una muerte de proceso inminente en receivers/workers — campo
  2026-07-18). DataStore escribe async y degradaría esa garantía. Decisión reafirmada, no deuda.

## Fuera de alcance (follow-ups deliberados)
- **Dominio `paparcar.app`** (privacy/licenses/mailto muertos) — bloqueado por publicar el
  dominio; conecta con RELEASE-001. Las URLs no se tocan aquí.
- **AboutLibraries in-app** — toca build config (plugin) → ticket propio.
- **"Reportar un problema" con diagnóstico adjunto** — ticket propio.
- **Spine duplicado** `VehicleSizeExplainerScreen`↔`PermissionTier` y cabeceras crudas de
  `HistoryContent` — ticket propio de convergencia si se quiere.
- **Play Console** (Data safety, URL de borrado de cuenta) — fuera del repo.
