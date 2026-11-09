# MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001 · la medición de anuncios pide su propio sí, y el registro deja de pedir uno que no hacía falta

**Estado:** ✅ Done · mergeado en master · rama `feature/MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001` · worktree `../Paparcar-adconsent`
⚠️ **Apilada** sobre `chore/PRIVACY-POLICY-MATCHES-DATA-FLOWS-001` (`94039de9a`), porque las dos tocan el
§2.10 de la política. Se mergea DESPUÉS de esa, o se squashean juntas.

## Problema

Desde el login rehecho (`b99e0536b`, #140), el SDK de Meta se encendía con `hasAcceptedLegalConsent`, la
marca que ponía la casilla «Acepto la Política de privacidad» del **registro con email**:

1. **No era un consentimiento válido para publicidad.** Iba atado a crear la cuenta (no *específico*) y
   sin marcarla no te podías registrar (no *libre*). Leer el identificador de publicidad para medir
   anuncios es el equivalente móvil de una cookie de marketing: exige un sí aparte, rechazable igual de
   fácil, y que la app funcione igual sin él (RGPD art. 6.1.a y 7 + art. 22.2 LSSI / ePrivacy 5.3).
2. **Dejaba fuera a todo el que entra con Google**, que nunca obtenía la marca → la atribución de Meta
   medía sólo una fracción de las instalaciones, justo cuando el user necesita saber qué anuncio trae
   gente (pérdida de usuarios, mercado flojo).
3. **La casilla en sí sobraba**: la política de privacidad se INFORMA, no se firma. Google ya mostraba
   un aviso con enlace; el registro con email pedía un tick para lo mismo.

## Doctrina

- Sistemas, no parches: el consentimiento vive en UN sitio (`AppPreferences.adMeasurementConsent`) y lo
  escribe UNA puerta (`SetAdMeasurementConsentUseCase`), usada por el diálogo y por Ajustes.
- CLAUDE.md, regla (b) de Meta: «nada sale antes del consentimiento». Se mantiene; cambia QUÉ
  consentimiento.

## Diseño

| Pieza | Qué |
|---|---|
| `domain/analytics/AdMeasurement.kt` | `AdMeasurementConsent { UNASKED, GRANTED, DENIED }` — tres estados porque «nunca preguntado» ≠ «dijo que no». `AdMeasurementLane` (isAvailable + onConsentChanged), `NoAdMeasurementLane`, `shouldAskAdMeasurementConsent()` |
| `SetAdMeasurementConsentUseCase` | escribe la preferencia PRIMERO (el tracker la relee en cada evento) y luego mueve el carril |
| `MetaAnalyticsTracker` | ahora también es el `AdMeasurementLane` de Android: el objeto que manda es el que se calla. `isAvailable = MetaAdAttribution.isConfigured` (sin claves → ni pregunta ni fila) |
| `MetaAdAttribution.deactivate()` | nuevo: apaga autolog + recogida del GAID al revocar, sin esperar al próximo arranque |
| `DataModule` | `AdMeasurementLane` = el tracker si es un lane, si no `NoAdMeasurementLane` (iOS y mock) |
| Preferencia | `ad_measurement_consent` (string), **por instalación** como una cookie: no se borra al cerrar sesión. `legal_consent_accepted` se retira **sin migrar**: no era un sí válido, así que todo el mundo vuelve a ser preguntado |
| `AdMeasurementConsentDialog` | `PapAlertDialog`, «Permitir» (primario) y «No permitir» (contorno, mismo ancho). Sin salida por toque fuera: descartar no es ni sí ni no y volvería a preguntar = acoso. Se muestra sólo en una pestaña principal (`BOTTOM_NAV_ROUTES`), nunca sobre onboarding/permisos/alta de vehículo |
| Ajustes | fila `PapSwitchRow` «Medición de anuncios» bajo «Política de privacidad», sólo si hay carril. Sin gate de Plus, simétrica |
| Registro | el slot `termsCheckbox` de BaseLogin se auto-satisface (`LaunchedEffect`) y pinta el MISMO aviso con enlace que Google (`auth_privacy_notice`). BaseLogin no se toca. `auth_consent_accept` borrada en los 9 locales |

## Strings (9 locales)
`ads_consent_title`, `ads_consent_body`, `ads_consent_allow`, `ads_consent_deny`,
`settings_ads_measurement`, `settings_ads_measurement_desc`. Vocabulario: *aparcamiento* (lo tuyo) vs
*plaza* (la comunidad), según CLAUDE.md.

## Publicado
- Política §2.10 (EN/ES): «nada de esto ocurre si no lo permites», dónde se cambia. §4 fila de Meta.
  §7 base jurídica: consentimiento + art. 22.2 LSSI para leer el identificador.
- Web `trust.points[3]` (es/en) + rebuild.
- `DATA-SAFETY-FORM.md`: las filas de Meta pasan a **opcionales**.

## iOS
Sin carril (`NoAdMeasurementLane`): no pregunta y no hay fila. Cuando iOS se anuncie, la puerta es
**ATT** primero (ver MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001 §iOS); este consentimiento sería
la segunda.

## Criterio de éxito
- Tests: `SetAdMeasurementConsentUseCaseTest`, `AppViewModelTest` (pregunta / no carril / sí / no
  nunca re-pregunta / fallo no atrapa), `SettingsViewModelTest` (fila y revocación),
  `SplashViewModelTest` (el «no» sobrevive al cierre de sesión).
- Device: instalación limpia → login con Google → Home → sale la pregunta una vez; «No permitir» →
  no vuelve; Ajustes → fila apagada; encenderla → Events Manager recibe `fb_mobile_activate_app`.

## Consumidores auditados
`grep hasAcceptedLegalConsent|setLegalConsentAccepted|AcceptLegalConsent|onLegalConsentAccepted` → 0 en
código. Cubiertos: `AppPreferences` + Android/iOS/2 fakes, `AppState/Intent/ViewModel`, `App.kt`,
`PaparcarAuthSlots` + previews + galería, `PaparcarApp` (arranque), `MetaAnalyticsTracker`,
`AdAttribution.kt` KDoc, `SettingsViewModel/State/Intent/Screen`, DI (Domain/Data/Presentation).
⏳ CLAUDE.md regla (b) cita `hasAcceptedLegalConsent`: actualizada en la misma tarea.

## ⏳ Fuera del código (del user)
Meta for Developers (paquete + key hashes de Play App Signing), Events Manager en device, cuenta
publicitaria vinculada, Play Console en el corte de la 1.6.0.
