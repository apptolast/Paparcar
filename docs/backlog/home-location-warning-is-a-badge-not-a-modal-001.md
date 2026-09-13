# HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001 · Sin ubicación, Home avisa con un badge — no con un bloqueo rojo

**Estado:** 🟡 En revisión · PR desde rama `bugfix/HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001` ·
GitHub issue [#5](https://github.com/apptolast/Paparcar/issues/5)

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

## Criterio de éxito
- ✅ Visto en emulador (Pixel 8 Pro, flavor mock, preset «GPS apagado (Home · BlockedCore)»): peek
  normal con badge «NO LOCATION», pastilla de arrastre, sheet expandible con la fila ámbar «Turn on
  location» y la lista; tocar el badge abre la pantalla de permisos. Cero `FATAL` en logcat.
- Primera entrada a Home sin permiso: no aparece el bloqueo rojo; detrás del diálogo de sistema se
  ve el peek normal con el badge ámbar.
- Denegado o GPS apagado: sheet arrastrable, lista visible, badge en la cabecera y fila ámbar en la
  lista; tocar badge o «Activar» abre permisos.
- Al conceder / encender GPS el badge desaparece solo (readiness reactiva).
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

## Preguntas abiertas (para la revisión)
1. El badge sale solo cuando la readiness es `BlockedCore`, que exige coche registrado + detección
   encendida (igual que el bloqueo que sustituye). ¿Debe salir también sin coche o con la detección
   apagada, cuando falta la ubicación igualmente? Implica exponer `isLocationServicesEnabled` en
   `HomeState`.
2. ¿Mantener la fila ámbar en la lista además del badge, o dejar solo el badge?
