# PRIV-A-TRACE-IS-NOT-A-STAT-001 · Una traza no es una estadística: la geometría de la ruta se queda en el móvil salvo consentimiento Plus

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Pendiente del USER: pegar el delta del §Anexo en el Data Safety de Play y la política (§retención).
**Verificado:** suite completa verde (`RouteGeometryPrivacyTest` 1/1 · `UserParkingReconcileTest`
13/13, +2 — el sync-back NO borra la ruta local · `SettingsViewModelTest` 58/58, +2 — free→paywall,
Plus→consentimiento, retirar siempre permitido) · locale-parity con los 9 · verify mock · compiles
mock/prod/iosSimulatorArm64 OK.
**Abierto:** 2026-09-14 · desde master `6622f8d9` · fase 8 del pivote — la que bloqueaba el sync

## Problema

Con el pivote, `routePolyline` pasó de "una ruta por aparcamiento, a veces" a **historial de
movilidad continuo y preciso** — la categoría de dato personal más sensible que maneja la app — y
hoy sube a Firestore para TODOS los usuarios en cada push de sesión. La decisión del user (sesión
14-09) ya está tomada: *local-first; a Firestore agregados; el polyline solo bajo la memoria de
Plus, con consentimiento explícito y retención declarada*. Falta ejecutarla.

**Distinción que da nombre al ticket**: `routeDistanceMeters` y `searchTimeMs` son ESTADÍSTICAS
(un número); `routePolyline`/`routeInferredSpans` son la TRAZA (por dónde fuiste). Las primeras
siguen subiendo — son las cifras del producto y no reconstruyen tu recorrido; la segunda no sube
sin consentimiento.

## Doctrina violada

La promesa local-first del plan de fases (aprobada por el user) — y de rebote el pendiente de Play
del 10-09: el Data Safety debe declarar lo que de verdad sale del dispositivo.

## Hallazgo crítico del reconocimiento

⛔ **El reconcile NO preserva la geometría**: `reconcileParkingSessions.onTakeRemote` copia el doc
remoto entero y solo salva campo a campo una lista blanca (`zoneRadiusMeters`,
`routeDistanceMeters`…). Un usuario sin consentimiento haría push de su sesión SIN polyline (doc
remoto más nuevo) y el siguiente `syncFromRemote` **le borraría la ruta local** — local-first
destruyendo lo local. La preservación `r ?: l` de los 4 campos de geometría es parte inseparable
de este ticket.

## Diseño

**El invariante vive en UNA puerta**: todo push de sesión converge en
`RemoteUserProfileDataSourceImpl.saveParkingSession` (worker de guardado, drainer de pendientes,
drainer iOS, y las 3 mutaciones in-place vía `enqueueSaveNewParkingSession`) — verificado por grep.

1. **`ParkingHistoryDto.withoutRouteGeometry()`** (pura, junto al DTO, testeada): anula
   `routePolyline`, `routeInferredSpans`, `routeInferredResolution` y deja `routeSnapped=false`
   (= "sin ruta", la forma legacy/BT ya documentada). Distancia y búsqueda quedan — son cifras.
2. **Choke point**: `saveParkingSession` la aplica cuando `!appPreferences.syncRouteGeometry`.
   `AppPreferences.syncRouteGeometry` (default **false**, user-scoped → se limpia al sign-out) en
   las 3 impls (DataStore Android, iOS, mock).
3. **Reconcile**: los 4 campos de geometría entran a la lista blanca `r ?: l`. Matiz documentado:
   un REJECTED multi-dispositivo podría resucitar en el device B — borde de borde, anotado.
4. **Retención pasiva de lo ya subido**: el `set()` sin merge reescribe el doc entero, así que
   cada push futuro de un usuario sin consentimiento **limpia su polyline en la nube** de esa
   sesión. Barrido masivo de docs antiguos: FUERA de alcance (decisión abierta para el user, base
   de usuarios de 1.0 minúscula; la política lo declara).
5. **El toggle (Ajustes)**, calcado del patrón zone-alerts (`ToggleZoneAlertsNotif`): soft gate —
   activarlo sin Plus abre el paywall (`PaywallReason.ROUTE_CLOUD`, motivo propio en los 9
   locales); desactivar siempre se puede; `checked = isPlus && syncRouteGeometry`. Fila con copy
   causa+consecuencia sin jerga: "Guardar tus rutas en tu cuenta / Tus trayectos se guardan solo
   en este móvil. Actívalo para verlos también en tus otros dispositivos".
6. **Delta de Data Safety** (para el pendiente de Play del user), en §Anexo.

## Criterio de éxito

- Test de la pura (`withoutRouteGeometry`) · test de reconcile (remoto sin geometría NO borra la
  local) · test del VM (sin Plus → paywall, con Plus → set) · suite completa + compiles + iOS.
- Con el toggle OFF: un push de sesión llega a Firestore sin polyline y con distancia/búsqueda.
- Data Safety delta listo para pegar.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `saveParkingSession` (única puerta de push de sesión) | cerrado — strip en la puerta |
| `updateParkingSessionAddressAndPlace` (push parcial) | exento: no toca geometría |
| Lectura remota (`toParkingHistoryDto` del snapshot) | sin cambio: restaura lo que exista (consentido) |
| `reconcileParkingSessions` | cerrado — lista blanca `r ?: l` con los 4 campos |
| `EnrichParkingSessionWorker` (snap escribe local + enqueue push) | cubierto por la puerta |
| iOS drainer | cubierto por la puerta (impl común) |
| `PaywallScreen` `when(reason)` exhaustivo | cerrado — rama ROUTE_CLOUD + string ×9 |
| `clearUserScopedState` | cerrado — el consentimiento es del USER, se limpia |
| Dev Catalog | fila nueva en Ajustes = variante existente del screen; galería sin grupo nuevo |

## Anexo · Delta de Data Safety / política (para el user)

- **Ubicación → precisa**: sigue "recogida" (detección). En "compartida/almacenada en servidores":
  la posición del aparcamiento y **agregados de trayecto (distancia, duración de búsqueda)** — la
  **traza de ruta solo si el usuario activa la copia en la nube (opt-in, Plus)**; puede
  desactivarse y es borrable con la cuenta.
- Política §retención: "las rutas detalladas de tus trayectos se almacenan únicamente en tu
  dispositivo, salvo que actives 'Guardar tus rutas en tu cuenta'; al desactivarlo dejan de
  copiarse nuevas rutas".
