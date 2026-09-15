# HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001 · Sin ubicación, Home avisa con un badge — no con un bloqueo rojo

**Estado:** ✅ **Done — en master** (`68ce566e` el badge, `a09b5fe7` la sheet se abre sobre el aviso
cuando se rechaza la primera petición, `124920dd` sin ubicación la cámara vuela al coche aparcado).
Verificado 15-09; la rama ya no existe. GitHub issue [#5](https://github.com/apptolast/Paparcar/issues/5).

## Problema
Issue #5: «Cuando pidamos permisos de localización en primer plano al iniciar home, que no aparezca
la modal de botón rojo de que no tenemos ubicación activada, debería ser un badge de estado como
warning dentro de la modal».

Con coche registrado y detección encendida, sin permiso de ubicación (o con el GPS apagado) la
readiness es `Blocked(FOREGROUND_LOCATION)` → `DetectionUiState.BlockedCore`, y
`HomePeekHandle` sustituía el sheet ENTERO por `HomeLocationBlockedState`: ilustración, «Activa la
ubicación» y un botón `PapButtonTone.Destructive` rojo. Además `HomeBottomSheet` desactivaba drag y
tap y ocultaba la lista. En la primera entrada eso aparece justo DETRÁS del diálogo de sistema que
`askFineLocationOnFirstEntry` acaba de lanzar: lo primero que dice Home es una alarma roja sobre una
pregunta que el usuario todavía está contestando.

## Doctrina violada
- `PapColor.danger` — «BLOCKED… Never a CTA; `PapRed` in a call to action is banned». Era el único
  uso de `PapButtonTone.Destructive` de la app, en un CTA.
- `PapColor.attention` existe precisamente para «something PENDING and you can fix it: a permission
  not yet granted». Faltar la ubicación es eso, no un fallo.
- [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]: «sin diálogos de culpa, sin pantalla
  bloqueante» — Home sabe vivir sin ubicación.

## Señales / datos disponibles
- `HomePeekSlice.detectionUiState == BlockedCore` — el mismo estado que pintaba el bloqueo y que
  prioriza `DetectionStory.BlockedCore` en la lista. Cubre permiso denegado y GPS apagado
  (`ObserveDetectionReadinessUseCase` mete el GPS off como `FOREGROUND_LOCATION`).
- `HomeSheetAction.OpenCorePermissions` → pantalla de permisos con foco Core (acción ya existente).

## Diseño
Un único predicado en la proyección y dos superficies que lo leen:
- `HomePeekSlice.showsLocationWarning` (= `BlockedCore`). Badge del peek y fila de la lista leen el
  MISMO estado, así que no pueden discrepar.
- **Peek**: fuera el takeover. La pastilla de arrastre, el drag, el tap y la lista vuelven a
  funcionar siempre. `BrowsePeek` gana `onFixLocation`; mientras no es null, sus tres cabeceras
  (coche en ruta, coche aparcado, zona) llevan en el trailing un `PapSheetTrailing.Warning`: badge
  `PapBadge` en `PapColor.attention` + `LocationOff` + «SIN UBICACIÓN» (rol `badge`), pulsable →
  `OpenCorePermissions`.
- `PapSheetTrailing` gana la variante `Warning(label, icon, onClick)` — genérica, por si otro
  «pendiente y arreglable» la necesita; hoy solo la usa Home.
- **Lista**: la fila `DetectionStory.BlockedCore` de `HomeDetectionSurface` pasa de `error` a
  `amber`. Con la lista ya visible, es la que explica («Activa la ubicación · Paparcar no funciona
  sin ella» + «Activar»), sin rojo.
- Se borra `HomeLocationBlockedState.kt`. `LocationAlertIllustration` se queda (Nivel 3, sigue en
  `IdentityIconsPreviews`) por si se reutiliza.
- String nuevo ×9: `home_location_off_badge` (EN «No location» / ES «Sin ubicación»).
- **Si el usuario deniega la primera petición, el sheet se abre** sobre el aviso (revisión del PR:
  «si le da a no permitir ubicación, debería abrirse la modal mostrando el badge»):
  `HomeState.locationWarningCue` (contador) se incrementa al leer un NO, y es la KEY de una
  auto-apertura en `SheetTransitionEffects`, igual que `promptShownAtMs` / `firstStepsCue`: se abre
  una vez por negativa y, si el usuario lo baja, se queda abajo. La respuesta se lee del propio
  paréntesis de primer plano del mapa (`SetMapForeground` false → true alrededor del diálogo), sin
  cambiar la API `expect/actual` del lanzador. Solo con aviso vigente: sin coche no se abre nada.
- Un solo predicado `DetectionUiState.warnsLocationMissing` para badge, fila y auto-apertura.

## Criterio de éxito
- ✅ Visto en emulador (Pixel 8 Pro, mock, preset «Sin permisos», instalación limpia): «Don't allow» en el
  diálogo de sistema → el sheet se abre solo con el badge «NO LOCATION» y la fila ámbar.
- ✅ Visto en emulador (Pixel 8 Pro, flavor mock, preset «GPS apagado (Home · BlockedCore)»): peek
  normal con badge «NO LOCATION», pastilla de arrastre, sheet expandible con la fila ámbar «Turn on
  location» y la lista; tocar el badge abre la pantalla de permisos. Cero `FATAL` en logcat.
- Primera entrada a Home sin permiso: no aparece el bloqueo rojo; detrás del diálogo de sistema se
  ve el peek normal con el badge ámbar.
- Denegado o GPS apagado: sheet arrastrable, lista visible, badge en la cabecera y fila ámbar en la
  lista; tocar badge o «Activar» abre permisos.
- Al conceder / encender GPS el badge desaparece solo (readiness reactiva).
- `HomeViewModelTest`: negativa con coche → `locationWarningCue` 1 (y no se repite al volver del
  segundo plano); sin coche → 0; concedido → 0.
- `HomeSlicesTest`: `showsLocationWarning` true con CORE ausente, false con solo PRODUCER ausente o
  sin bloqueo. Guardarraíles Konsist + `compileProdDebugKotlin` + `compileMockDebugKotlin` verdes.
- Galería: «PapSheet · browse sin ubicación (badge de aviso)» y la variante con coche aparcado; la
  variante «Sin permiso CORE (BlockedCore)» enseña ya la fila ámbar.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `HomePeekHandle` (pill oculta + takeover) | cerrado — pill siempre, sin takeover |
| `HomeBottomSheet` (`isBlocked` drag/tap, `showList`) | cerrado — ya no depende de BlockedCore |
| `HomeLocationBlockedState.kt` | borrado |
| `HomeDetectionSurface` fila BlockedCore | `error` → `amber` |
| `BrowsePeek` (3 cabeceras) | badge vía `onFixLocation` |
| Peeks de selección (plaza, gasolinera, parking, sesión) | exentos — llevan ×/stepper; se vuelve a browse y ahí está el aviso |
| `HomeSheetContent` (`hasCorePermissions` oculta carril/filtros/report) | sin cambio — sin ubicación «cerca» no significa nada; comentario actualizado |
| `DetectionStory` prioridad BlockedCore | sin cambio |
| `docs/backlog/home-flow-analysis.md` («bloquea el sheet entero») | histórico; queda superado por este ticket |
| `DetectionReadinessDesignPreviews` (mock de diseño `DesignState.BlockedCore`) | exento — boceto histórico, no pinta el componente real |

## Decisiones de la revisión (13-09-2026)
1. El aviso **solo sale con coche** (readiness `BlockedCore`): se mantiene el alcance del bloqueo que
   sustituye. Sin coche, Home vive sin ubicación y no avisa.
2. **Se mantiene la fila ámbar** en la lista además del badge.
3. Al denegar la primera petición el sheet se abre mostrando el aviso (ver Diseño).
4. **Sin ubicación y con un coche aparcado, la cámara vuela al coche** (segunda revisión):
   `HomeUiController.centerOnParkingWithoutLocation` — el foco inicial normal espera al primer fix, que
   sin permiso o sin GPS no llega. Consume el mismo one-shot, así que si luego llega la ubicación la
   cámara no salta. `HomeUiControllerTest` cubre las dos cosas.
