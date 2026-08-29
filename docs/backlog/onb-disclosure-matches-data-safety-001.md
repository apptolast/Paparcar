# ONB-DISCLOSURE-MATCHES-DATA-SAFETY-001 · El aviso de ubicación del onboarding debe decir lo que Play exige (y nada falso)

**Estado:** ✅ Done (29-08-2026) — 2 keys × 9 locales; flujo verificado en emulador (pág. 3 →
permisos → diálogo, en ese orden). El vídeo de la Permissions Declaration se graba al subir al
Console (guion abajo).

## Problema
Play exige, para `ACCESS_BACKGROUND_LOCATION`, un *prominent disclosure* EN LA APP antes del
diálogo de permiso, con la idea literal «recoge ubicación **incluso cuando la app está cerrada o
no está en uso**» — y que no contradiga el formulario Data Safety. Estado actual (verificado
28-08):
- `permissions_perm_background_desc`: «even when the app is **not open**» — cerca, pero no clava
  la fórmula que los revisores buscan («closed or not in use»).
- `onboarding_page3_subtitle`: «…your location … **never shared with third parties**» —
  técnicamente falso (Firebase/Google procesan esos datos como encargados) y contradice el Data
  Safety y la política publicada. Es justo el tipo de inconsistencia que dispara un rechazo.

## Doctrina violada
Copy sin mecánica interna pero SIN mentir: causa + consecuencia + remedio. La promesa «never
shared» es una mentira amable — y política, formulario y app tienen que contar la misma historia
(PLAY-PRIVACY-POLICY-001).

## Señales / datos disponibles
- La política publicada ya usa la fórmula correcta (callout §2.2): reutilizar ese wording.
- Guía de respuestas: `docs/legal/DATA-SAFETY-FORM.md`.

## Diseño
Solo strings (los 9 locales en la misma tarea):
1. `permissions_perm_background_desc` (y hermanos del flujo de permisos que describan background)
   → «…even when the app is closed or not in use».
2. `onboarding_page3_subtitle` → quitar «never shared with third parties»; decir la verdad corta:
   la ubicación se usa para detectar aparcamientos y no se vende ni se usa para publicidad.
Sin cambios de lógica ni de pantallas → sin impacto en Dev Catalog (verificar paridad de
`*Previews.kt` igualmente).

## Criterio de éxito
El flujo real (onboarding pág. 3 → pantalla de permisos → diálogo del sistema) muestra el aviso
con la fórmula exigida ANTES de pedir el permiso; con ese flujo se graba el **vídeo** para la
Permissions Declaration del Play Console. Ninguna key falta en ningún locale
(`assembleProdDebug` + arranque).

## Vídeo de la Permissions Declaration (se graba al subir al Console, no en esta tarea)
Play Console → **App content → Sensitive app permissions → Location permissions** pide, al declarar
`ACCESS_BACKGROUND_LOCATION`, un **enlace de YouTube** (vale unlisted) a un screen-recording del
flujo real. Guion (~30-45 s, en inglés, build prod en un device físico):
1. Onboarding pág. 3 «Why we need your permissions» — pausa ~3 s para que el disclosure se lea.
2. «Set up permissions» → pantalla de permisos, visible la fila «Background location — …closed or
   not in use» — pausa.
3. «Grant permissions» → diálogo del sistema → «While using the app».
4. Paso a background: la guía de la app → ajustes del sistema → **«Allow all the time»** (en
   Android 11+ el diálogo no lo ofrece; hay que enseñar el paso por Ajustes).
5. Volver a la app y enseñar la función que lo justifica: Home con la detección activa/centinela.
El mismo vídeo (o uno hermano) sirve para la declaración de `FOREGROUND_SERVICE_LOCATION`
(targetSdk 34+): debe verse la notificación del FGS durante la detección.

## Consumidores auditados
Grep de «shared» / «third» / «sell» / «advertis» / «privacy» / «location data» / «not open» /
«background» sobre `values/strings.xml` (base EN) el 29-08-2026:
- ✅ `onboarding_page3_subtitle` — reescrita: fórmula literal «even when the app is closed or not
  in use» + verdad corta («never sold or used for advertising»); la promesa «never shared with
  third parties» eliminada. Es la misma historia que el callout §2.2 de la política publicada.
- ✅ `permissions_perm_background_desc` — «not open» → «closed or not in use» (fórmula exacta).
- ⚪ Exentos (hablan de restricciones de batería/OS, no prometen nada sobre los datos):
  `permissions_perm_battery_desc`, `permissions_perm_battery_oem_hint`,
  `permissions_reliability_reduced_callout`, `permissions_perm_autostart`,
  `permissions_perm_oem_battery*`, `permissions_btn_allow_background`,
  `permissions_bg_guide_title`, `settings_detection_reliability_reduced_desc`,
  `settings_detection_battery_desc`, `home_det_watch_interrupted_sub`.
- ⚪ Exentos («shared» con otro significado — plazas de la comunidad, no datos a terceros):
  `home_parking_delete_confirm_body`, `home_zone_private_hint`, `vehicle_stats_spots_shared`.
- ⚪ `permissions_perm_location_desc` / `permissions_perm_location_services_desc` — describen el
  permiso foreground sin promesas falsas; sin cambio.
- Flujo verificado en código: `OnboardingScreen.kt:225` (pág. 3) y `PermissionsContent.kt:247`
  muestran ambos textos ANTES del diálogo del sistema — el vídeo de la Permissions Declaration se
  graba sobre ese flujo.
