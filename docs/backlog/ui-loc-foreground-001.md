# UI-LOC-FOREGROUND-001 — El dot del usuario refresca en foreground (high-accuracy) + logger de verificación

> **Estado**: ✅ Done · en master como `f6de7697` — *"user dot refreshes via foreground-scoped
> high-accuracy + verification logger"*. La rama `feature/UI-LOC-FOREGROUND-001` ya no existe y el
> marcador vive en **13 ficheros** de producción.
> *(Corregido el 2026-08-30 por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]: decía "sin merge".)*
> El field-test que pedía quedó cubierto: el commit es anterior a la frontera de campo validado del
> 23-08. La validación iOS sigue dependiendo de un Mac, como todo lo de esa plataforma.

## Problema (reportado por el usuario)

Andando físicamente, el punto azul del usuario en el mapa de Home **no se actualizaba** en algunos
dispositivos; en otros iba bien. Asimetría entre móviles = síntoma clásico de fix grueso/lento.

## Diagnóstico

El dot del usuario en modo normal (sin viaje monitorizado) se alimentaba de
`HomeViewModel.subscribeGpsLocation()` → `LocationDataSource.observeBalancedLocation()`, es decir
`PRIORITY_BALANCED_POWER_ACCURACY` a **30 s** de intervalo. Balanced le dice al fused provider que
NO necesita GPS: sirve fixes de WiFi/celda (~100 m), reutiliza cacheados y agrupa. Andando 20-50 m
el fix cae en el mismo cluster → el dot parece congelado. En OEM agresivos (ColorOS/MIUI — los dos
móviles de test) el throttling de balanced es aún mayor; en un móvil con GPS caliente hasta balanced
se mueve → de ahí la asimetría.

(El stream fino `observeUiLocation()` / `observeHighAccuracyLocation()` solo se usaba para el puck de
conducción, que únicamente vive durante un viaje monitorizado — no en navegación normal.)

## Fix

El dot idle pasa a **high-accuracy scopeado al foreground del mapa**:

- `subscribeGpsLocation()` ahora combina permiso × `mapForeground` × reconexión y, cuando hay core-perm
  **y** el mapa está en foreground, suscribe `observeHighAccuracyLocation()` (5 s / mín 2 s, GPS fino);
  si no, `emptyFlow()` (sin request → cero coste de GPS en background).
- La señal de foreground es un efecto expect/actual `MapForegroundEffect` (android = `LifecycleResumeEffect`,
  iOS = siempre-foreground por ahora), cableado en `HomeScreen`. Bajo Compose Navigation el
  `LocalLifecycleOwner` es el back-stack entry de Home ⇒ RESUMED = Home visible **y** app en primer
  plano. El efecto emite `HomeIntent.SetMapForeground(active)` → `mapForeground` (StateFlow, default false).

## Verificación (logger local + remoto)

Puerto `UiLocationLogger` (dominio) + `UiLocationSample` (SUBSCRIBED / FIX / STOPPED, con `foreground`,
`priority`, `accuracy`, `sinceLastFixMs`, `speed`, lat/lon). Implementación real
`FirestoreUiLocationLogger`:

- **Local (siempre)**: cada sample se refleja en logcat con tag `UiLocationLogger` — se ve la cadencia
  (`gap=…ms`) y la precisión (`acc=…m`) fluir, sin opt-in. Prueba que el dot ya refresca.
- **Remoto (gated + throttle)**: mismo flag que detección (`diagnostics_config/{uid}.enabled`), escribe
  en `diagnostics/{uid}/uiLocation/{autoId}`. FIX throttled a 1/10 s; SUBSCRIBED/STOPPED siempre. Cada
  doc lleva `deviceModel`/`appVersion`. Reglas Firestore ya cubren la subcolección (wildcard recursivo).

Default binding `NoOpUiLocationLogger` (mock). El gate del flag ya está ON en ambos móviles de test
(Redmi=WZB7oftWLDY1toGJrDwoRHnnYHx2, Oppo=fiypNbElGlfFexLMpU9sNaMjRMD3), así que el prod-debug loguea sin tocar nada.

## Cómo leer el resultado

- **Logcat**: `adb logcat -s UiLocationLogger` — al abrir Home: `SUBSCRIBED fg=true HIGH_ACCURACY`;
  luego `FIX … gap=~2000ms acc=~10m` cada pocos segundos moviéndose; al salir de Home: `STOPPED`.
- **Firestore (MCP)**: `diagnostics/{uid}/uiLocation` — mirar `sinceLastFixMs` (debe ser ~segundos, no
  ~30 s) y `accuracy` (fina). Comparar el device que fallaba vs el que iba bien.

## Notas / decisiones abiertas

- Background = request parado (batería). La detección tiene sus propios streams/servicio, intactos.
- No se hace seed con `getLastKnownLocation()` al resumir (el fix fino llega en pocos s); si en campo
  la aparición inicial del dot se percibe lenta, añadir seed pasivo.
