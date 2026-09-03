# UI-HOME-MUST-NOT-OFFER-WHAT-IT-CANNOT-DO-YET-001 · Los botones del tutorial aceptan toques antes de que Home tenga los datos para cumplirlos

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)

## Problema

Pedido por el user el 03-09: *«hasta que Home no esté totalmente cargado no deberíamos poder usar
ninguna funcionalidad, o sea, poner los botones de tutoriales inactivos o algo así»*.

Home se rellena por partes durante los primeros segundos: preferencias, vehículos, permisos y —el
último en llegar— el fix de GPS. Un botón pulsado antes de que su dato aterrice **no falla de forma
ruidosa**, hace algo peor:

| CTA | Sin fix de GPS |
|---|---|
| «Marcar aparcamiento» | entra en modo pin **sin centro**: `EnterAddParkingMode(initialGps = null)` deja `pinCameraLat/Lon` vacíos y el confirm se va por `reportPinMissing()` |
| «Avisar de una plaza» | `EnterReportMode(lat = cameraLat ?: userGps ?: 0.0)` → **`0.0, 0.0`**, el Golfo de Guinea, basura en el mapa de la comunidad |

## Doctrina violada

- **Una superficie no ofrece lo que no puede cumplir.** Es la misma familia que
  `UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001` (un foco que pedía arrastrar e impedía
  arrastrar) y que los CTA que mentían del checklist: lo que la UI ofrece y lo que puede hacer tienen
  que coincidir en todo momento, no solo cuando todo ha cargado.
- Y toca lo de siempre: el pin ES el centro de la cámara
  [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001]. Un modo pin sin centro es un
  aparcamiento a punto de marcarse en cualquier sitio.

## Señales / datos disponibles

⛔ **`HomeState.isLoading` NO sirve para esto, y usarlo habría sido un desastre.** No significa "Home
cargado": es solo del GPS, y se arma así —

```kotlin
.flatMapLatest { (hasCore, foreground) -> if (hasCore && foreground) …location() else emptyFlow() }
.onStart { updateState { copy(isLoading = true) } }
```

Sin permisos CORE el flujo es **vacío**, así que `isLoading` se queda en `true` **para siempre**. Un
gate global colgado de esa señal dejaría la app entera inutilizable justo para el usuario que niega
permisos. Por eso el gate NO es global.

Lo que sí es fiable: `userGpsPoint != null` — hay posición o no la hay.

## Diseño

**Cada CTA espera SU dato, no un "cargando" global.** El paso declara qué necesita
(`FirstStepCopy.needsLocation`), igual que ya declara si pide un permiso (`asksForPermission`), y la
tarjeta apaga solo esos botones mientras no haya posición.

- `MARK_PARKING` y la cara REPORT_ONE de `FIND_SPOT` → `needsLocation = true`.
- El resto no: activar la detección, reforzar la vigilancia o ver plazas no necesitan un fix para
  responder a un toque.

**Deshabilitado, no escondido.** Un botón que desaparece y vuelve se lee como un fallo, y el paso
tiene que seguir diciendo qué te va a pedir. La deshabilitación dura lo que tarda el primer fix.

## Criterio de éxito

- Con la app recién abierta y sin fix, el CTA de marcar aparcamiento y el de avisar están apagados, y
  se encienden solos en cuanto entra la posición.
- Ningún otro botón del checklist se apaga: negar permisos no puede dejar el tutorial muerto.
- Tests verdes y los dos flavors compilando.

## Consumidores auditados

- `FirstStepsCard` — único consumidor de `FirstStepCopy`; recibe `hasLocation` del slice.
- El CTA «Report a free spot» del final de la sheet **no se toca en este ticket**: entra por el mismo
  `EnterReportMode` y conserva el fallback a `0.0, 0.0`. Queda anotado abajo.

## Follow-up detectado, no arreglado aquí

⛔ **`EnterReportMode` cae a `0.0, 0.0`** cuando no hay ni cámara ni GPS. Este ticket cierra la vía
del checklist, pero el fallback sigue vivo para el CTA del final de la sheet. Un reporte en el Golfo
de Guinea es basura publicada en el mapa de la comunidad, así que el fallback debería ser "no entrar
en modo reporte" y no una coordenada inventada. Merece su propio ticket.
