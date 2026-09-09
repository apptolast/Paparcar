# DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001 · el toggle de detección se dice encendido aunque le falten los permisos para funcionar

**Estado:** ✅ Done — en master (09-09-2026)

## Problema

El interruptor maestro de *Ajustes → Detección y permisos* (`settings_auto_detect`) se pinta con
`checked = state.autoDetectParking`, que es **la preferencia y nada más**
(`SettingsScreen.kt:675`). Esa preferencia arranca en `true` por defecto
(`SettingsState.kt:10`, y el mismo default en `AndroidDataStoreAppPreferences`), así que en un
primer arranque —o en cualquier instalación donde el usuario aún no ha concedido los permisos de
producción, o los ha revocado, o tiene el GPS apagado— el toggle aparece **encendido mientras la
detección no puede ejecutarse**.

Dos frases contradictorias en la misma tarjeta, una encima de la otra:

```
[Detección automática]            ●━  ← ENCENDIDO
[⚠ Falta: Ubicación en segundo plano]   [Arreglar]
```

Y la fila dependiente de abajo lo repite: *"Aparcamiento detectado"* sigue habilitada
(`enabled = state.autoDetectParking`, `SettingsScreen.kt:492`) prometiendo una notificación que
nada va a producir.

## Doctrina violada

- **El estado se escribe honestamente** (`⛔ Color — identidad por MÉTODO, estado en texto`): un
  control que afirma "esto está funcionando" cuando no puede funcionar es una afirmación falsa, no
  un matiz visual.
- **Sistemas, no parches**: el predicado "¿la detección puede correr?" **ya existe dos veces** y
  ninguna de las dos es la que mira el interruptor:
  - `ObserveDetectionReadinessUseCase.resolve()` — Home ya es honesto: con la preferencia ON y
    permisos ausentes devuelve `Blocked(missing)`, nunca "listo".
  - `SettingsState.detectionHealthy` — la propia pantalla ya lo calcula… **solo para la fila de
    salud de al lado**, que es la que sí dice la verdad mientras el toggle la contradice.
- **`⛔ No copy al usuario con mecánica interna`** por su cara menos obvia: el usuario no distingue
  "preferencia guardada" de "detección activa". Solo hay un concepto en su cabeza, y el control
  maestro tiene que ser ESE.

## Señales / datos disponibles

Todo el material está ya en el estado de la pantalla; no hace falta ninguna fuente nueva:

- `SettingsState.missingDetectionPermissions` — permisos de detección ausentes (CORE + PRODUCER),
  alimentado reactivamente desde `PermissionManager.permissionState`.
- `SettingsState.isLocationServicesEnabled` — el interruptor de GPS del sistema.
- `SettingsState.detectionHealthy` = ambos en verde.
- `SettingsViewModel.FixDetectionPermissions` — ya sabe **a qué foco** (`Core` / `Producer`) abrir
  el flujo de permisos según cuál es la carencia.
- `HomeIntent.EnableAutoDetection` — el camino canónico de activación desde Home: escribe la
  preferencia a `true` y, si faltan permisos, abre el flujo de permisos.

## Diseño

**El interruptor deja de leer la preferencia y pasa a leer el hecho**, con el mismo patrón que la
fila de al lado en esta misma pantalla ya usaba desde MONEY-001 —
`checked = state.isPlus && state.notifyZoneSpots` (`SettingsScreen.kt:501`): un switch está
encendido solo cuando **lo que promete está realmente en vigor**, y pulsarlo cuando no lo está
abre el flujo que concede lo que falta (allí el paywall; aquí los permisos).

1. **`SettingsState.autoDetectActive`** (derivado, un solo sitio):
   `autoDetectParking && detectionHealthy`. Es lo que consume el switch, su icono
   (`Sensors`/`SensorsOff`) y la fila de notificación dependiente. La preferencia cruda
   `autoDetectParking` sigue existiendo, porque es lo que el usuario **quiere** y hay que
   recordarlo — deja de ser lo que la pantalla **afirma**.
2. **`ToggleAutoDetect(true)` con la detección impedida** pasa a hacer exactamente lo que hace
   `HomeIntent.EnableAutoDetection`: guarda la intención (`setAutoDetectParking(true)`) **y** manda
   `NavigateToPermissions(focus)`. Un toque, el mismo camino que Home, y al volver con el permiso
   concedido el switch ya se enciende solo — sin segunda pulsación.
3. **El foco del flujo de permisos se calcula en UN sitio** (`detectionGapFocus()`), compartido por
   el botón "Arreglar" de la fila de salud y por el toggle. Antes esa decisión vivía suelta dentro
   de `FixDetectionPermissions`; ahora tiene dos consumidores y por tanto nombre propio.
4. Apagar sigue igual: escribe `false` y confirma con el snackbar de deshacer (DET-TOGGLE-002).
   Apagar solo es alcanzable cuando el switch está encendido, es decir, cuando la detección
   realmente corría — que es cuando ese snackbar tiene sentido.

Lo que **no** cambia: la preferencia persistida, el gate del sentry
(`CoordinatorDetectionService` sigue leyendo `appPreferences.autoDetectParking`), y la precedencia
de Home (`TURNED_OFF` antes que `Blocked`). Este ticket no toca detección: cambia lo que Ajustes
**dice** sobre ella.

## Criterio de éxito

- Con la preferencia en `true` y algún permiso de detección ausente (o el GPS apagado), el switch
  se pinta **apagado** y la fila "Aparcamiento detectado" queda bloqueada.
- Pulsarlo hacia ON en ese estado emite `NavigateToPermissions` con el foco correcto (`Core` si
  falta el permiso de ubicación en primer plano o el GPS del sistema; `Producer` si lo que falta es
  del tramo de producción) **y** deja la preferencia en `true`.
- Concedidos los permisos, al volver a Ajustes el switch aparece encendido sin tocarlo.
- Apagarlo con todo concedido sigue emitiendo `DetectionTurnedOff` (snackbar con deshacer).
- Tests en `SettingsViewModelTest` para los cuatro casos; variantes en la galería mock y previews.

**Verificado (09-09-2026):** suite completa `:shared:testDebugUnitTest` en **2523 tests, 0 fallos**
(225 clases), de los cuales 55 en `SettingsViewModelTest` con **8 nuevos** — las cuatro lecturas
del switch (pref∧permisos, pref∧GPS, todo verde, apagado a mano) y los tres destinos del enrutado
(`Core` por ubicación ausente, `Producer` por tramo de producción, `Core` por GPS off), más el que
prueba que apagar **nunca** pide permisos. El test viejo
`should_notEmitEffect_when_autoDetect_toggled_on` codificaba el comportamiento antiguo y **fallaba**
con este cambio (el fake de permisos arranca en todo denegado): se reescribió concediendo permisos
primero, que es el único caso en que encender no debe navegar a ningún sitio.
`:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` en verde. ⏳ Sin ver todavía en device.

## Consumidores auditados

`grep` de todo lo que lee la preferencia de auto-detección:

| Sitio | Lee | Veredicto |
|---|---|---|
| `SettingsScreen.kt:675` switch `checked` | pref cruda | **corregido** → `autoDetectActive` |
| `SettingsScreen.kt:672` icono `Sensors/SensorsOff` | pref cruda | **corregido** → `autoDetectActive` |
| `SettingsScreen.kt:492` `enabled` de "Aparcamiento detectado" | pref cruda | **corregido** → `autoDetectActive` (la notificación que gatea no puede producirse si la detección no corre) |
| `SettingsViewModel.ToggleAutoDetect` | escribe pref | **corregido** → además enruta a permisos al encender impedido |
| `ObserveDetectionReadinessUseCase:108` | pref cruda | **exento y correcto**: la precedencia `TURNED_OFF` antes que `Blocked` es deliberada (DET-TOGGLE-001) — quien apagó a mano no recibe un nag de permisos. Lee INTENCIÓN a propósito. |
| `ObserveDepartureWatchGapUseCase:39` | pref cruda | **exento**: decide si hay hueco de vigilancia; la intención es la pregunta correcta. |
| `SentryLifecycleDecision:40` (`autoDetectEnabled`) | pref cruda | **exento**: gate del sentry, decisión F3 (06-08-2026) — el toggle de Ajustes ES el gate. Sin permisos el sentry ya no puede armar por otras vías. |
| `CoordinatorDetectionService:1490,1569` | pref cruda | **exento**: mismo gate del sentry. |
| `ActivityRecognitionManagerImpl:88` · `BluetoothConnectionReceiver:74` | pref cruda | **exento**: cortan el trigger si el usuario apagó la función; sin permisos esos callbacks no llegan siquiera. |
| `HomeIntent.EnableAutoDetection` | escribe pref + enruta | **es el modelo copiado aquí**, sin cambios. |

Conclusión del barrido: el invariante nuevo ("lo que se AFIRMA es pref ∧ puede-correr") es de
**presentación**, y su único sitio es `SettingsState.autoDetectActive`. Los consumidores de
detección leen INTENCIÓN a propósito y se quedan como están — mezclarlos sería reintroducir por
detrás la confusión que este ticket separa.
