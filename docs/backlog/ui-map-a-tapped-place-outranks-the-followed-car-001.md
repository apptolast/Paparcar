# UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001 · pedir un sitio manda sobre seguir al coche

**Estado:** ✅ Done · mergeado a master 2026-08-30 (squash) · implementado en
`bugfix/UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001-tap-revokes-follow`, abierto sobre
`fdba2b00`. ⏳ **Sin ver en device**: probarlo de verdad exige un viaje real.

## Problema

Durante un viaje detectado el mapa sigue al coche (`followingDriver`, [FOLLOW-001]). Mientras ese
seguimiento está enganchado, **todo destino que el usuario pide desde fuera de la superficie del mapa
se descarta en silencio**: la cámara no se mueve y no hay ni error ni aviso.

La cadena: cada petición de cámara pasa por `HomeUiController.moveCamera(...)`, que escribe un
`CameraTarget` nuevo. `PaparcarMapView.rememberCameraAnimationState` lo tira a la basura:

```kotlin
LaunchedEffect(cameraTarget, following) {
    if (following || userInteracting) return@LaunchedEffect   // ← el destino muere aquí
```

y, aunque no lo tirase, la rama de abajo (`if (followPose != null) return CameraPosition(puck…)`)
devuelve la posición del coche en cada frame, así que la cámara volvería igualmente.

**Por qué unos taps sí funcionan y otros no.** `HomeMapSection` envuelve el mapa en un
`pointerInput` que llama a `onUserMapGesture()` en el primer `down`, y eso apaga `followingDriver`.
O sea: el follow sólo lo revoca **un dedo sobre el tile del mapa**. Un tap en un marcador funciona
por accidente de esa vía. Todo lo demás cae:

| Lo que el usuario toca | ¿Está sobre el mapa? | Hoy |
|---|---|---|
| Fila de plaza en el sheet (`HomeSheetContent:316`) | ❌ | **la cámara no se mueve** |
| Fila de coche aparcado en el sheet (`HomeSheetContent:216`) | ❌ | **no se mueve** |
| Resultado de búsqueda (`HomeScreen:872`) | ❌ | **no se mueve** |
| Chip de zona → `HomeEffect.MoveCameraTo` (`HomeScreen:801`) | ❌ | **no se mueve** |
| FAB de coche aparcado / FAB de punto medio (`HomeScreen:1002` + `onParkedCar`) | ❌ | **no se mueve** |
| `StepToVehicle` → volar al GPS antes de `AddingParking` (`HomeScreen:935`) | ❌ | **no se mueve** |
| Tap en un marcador de plaza / de coche | ✅ | funciona (vía el observador de puntero) |

El caso que más muerde es el primero: vas conduciendo, el sheet lista las plazas libres cercanas,
tocas una para verla en el mapa — la fila se selecciona, el sheet se expande, el marcador se marca…
y el mapa se queda pegado al coche. La app registró la intención y no la obedeció.

## Doctrina violada

Ninguna de las tres de detección — aquí no se coloca ni se afirma nada. La que se rompe es de
reparto de autoridad, y es la hermana de la de [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]:

> **Seguir al coche es un valor por DEFECTO, no un candado. Un destino que el usuario ha pedido
> expresamente lo revoca.** Y el corolario: *una petición de cámara no se descarta en silencio* — o
> se obedece, o quien la deniega es quien la ha pedido.

Y la causa de fondo, que es lo que hay que arreglar como sistema: **el rango de una petición de
cámara no se puede deducir de dónde estaba el dedo.** Hoy el desempate lo hace un observador de
puntero pegado al tile del mapa, o sea que la regla de producto («lo que pide el usuario manda»)
está implementada como un efecto colateral de la geometría de la pantalla. Cualquier superficie
nueva que quiera mover la cámara — y ya hay seis — nace rota y muda.

## Señales / datos disponibles

- `HomeUiController` ya es el único que escribe `cameraTarget` **y** el único que escribe
  `followingDriver`: las dos verdades ya viven juntas, no hay que inventar un canal.
- `followingDriver` ya se re-engancha por dos vías explícitas: el viaje que empieza
  (`setDriverFollowActive(true)`, disparado una sola vez por `snapshotFlow { puck != null }`) y el
  FAB de localización, que durante un viaje significa «vuelve al coche» (`resumeDriverFollow`) y ya
  se pinta distinto (`followsCar = isDriving`). O sea: **la vuelta atrás ya existe y es visible**.
- Desde [DRIVE-PUCK-NATIVE-001] el puck es un `LiveMarker` permanente: soltar el follow **no** hace
  desaparecer el coche del mapa. Ése era el motivo histórico de esperar a un pan de verdad.
- `moveCamera` tiene hoy DOS clases de llamador mezcladas: peticiones del usuario y encuadres
  automáticos (`centerInitialFocus`, `refocusOnParkingArrival`, y el propio `followDriver`, que se
  llama a sí mismo ~1 Hz). Sin separarlas, revocar el follow en `moveCamera` haría que el
  seguimiento se matara solo en el primer fix.

## Diseño

### 1 · El rango se declara en la puerta, no se deduce del dedo

`HomeUiController` pasa a tener **dos puertas públicas con rango explícito**, sobre un mismo
fontanero privado (`setTarget`, que es el `moveCamera` de hoy: `isProgrammaticMove` + `CameraTarget`
con token nuevo):

| Puerta | Quién entra | Efecto sobre `followingDriver` |
|---|---|---|
| `goToPlace(lat, lon, zoom?)` | los 6 sitios de arriba + los 2 taps de marcador | **lo revoca** |
| `framePlaces(lat1, lon1, lat2, lon2)` | FAB de punto medio (encuadra coche + tú) | **lo revoca** |
| `centerInitialFocus` / `refocusOnParkingArrival` (privadas por dentro) | encuadre automático de arranque | neutro |
| `followDriver` / `resumeDriverFollow` | el propio seguimiento | neutro / lo engancha |

`moveCamera` y `moveCameraToBounds` **dejan de ser públicas**: un nombre que no dice su rango es
justo lo que permite que el próximo call site nazca roto. El nombre es la barandilla.

### 2 · Automático ≠ deliberado (y por eso el encuadre de arranque NO revoca)

`centerInitialFocus` se dispara con el primer fix GPS y, si abres la app en mitad de un viaje, centra
en el usuario — que es centrar en el coche que conduces ([DET-READY-TRIP-OVER-PARKED-001]). Si eso
revocara el follow, la app se auto-desengancharía al arrancar. **La regla es «lo que el usuario ha
pedido», no «todo lo que mueve la cámara»**; los encuadres automáticos siguen siendo neutros.

Exención asumida y explícita: el `selectedSpot` de `centerInitialFocus` (deep-link a una plaza)
también queda neutro. Es un encuadre de arranque, y hacerlo deliberado abriría una carrera con el
enganche del follow por una ruta que nadie ha reportado.

### 3 · El mapa obedece su candado; el candado lo decide el host

`PaparcarMapView` **no** se convierte en una segunda autoridad. Su `if (following) return` se queda:
con el arreglo, cuando llega un destino deliberado `centerDrivingPuck` ya viene en `false` en la
misma recomposición (las dos lecturas salen del mismo controller), así que el guard no llega a
morder. Lo que cambia es el comentario: deja de describirse como una regla propia y pasa a decir
dónde vive el rango. Quitarlo sería peor que dejarlo — un tween corriendo por debajo del candado
deja un destino rancio que salta al desenganchar, que es exactamente lo que ese guard evita.

### 4 · Lo que NO se hace

- **No se re-engancha solo el follow** tras N segundos mirando el sitio pedido. Revocar es
  permanente hasta un acto explícito (FAB de localización, o el viaje siguiente), igual que ya pasa
  con un pan a mano. Un follow que vuelve solo le robaría la cámara al usuario una segunda vez, y
  ésa es la queja original.
- **No se toca el observador de puntero** de `HomeMapSection`: un dedo sobre el mapa sigue pausando
  el follow al instante y con latencia cero. Sigue siendo correcto, sólo deja de ser el único.

## Criterio de éxito

- Viaje vivo + `goToPlace(plaza)` → `followingDriver == false` y `cameraTarget` es la plaza.
- Viaje vivo + `framePlaces(coche, tú)` → follow revocado y target de bounds.
- Viaje vivo + `followDriver(fix)` ×N → el follow **sigue enganchado** (no se auto-mata).
- Viaje vivo + `centerInitialFocus` centrando en el usuario → el follow **sigue enganchado**.
- Tras revocar, `resumeDriverFollow` vuelve a enganchar y mueve la cámara al puck.
- **Verificación por falsación**: quitar la revocación de `goToPlace` tiene que dejar en rojo el
  primer test y **sólo** ése.
- En device: conduciendo, tocar una fila de plaza del sheet mueve el mapa a esa plaza y la deja
  quieta; el FAB de localización (en modo «sigue al coche») devuelve la cámara al coche.

## Consumidores auditados

| Sitio | Qué pide | Clasificación |
|---|---|---|
| `HomeSheetContent:316` fila de plaza → `HomeSheetAction.MoveCamera` | destino deliberado | ⛔ **donde mordió** → `goToPlace` |
| `HomeSheetContent:216` fila de coche aparcado → `MoveCamera` | destino deliberado | ⛔ **donde mordió** → `goToPlace` |
| `HomeScreen:872` resultado de búsqueda | destino deliberado | ⛔ **donde mordió** → `goToPlace` |
| `HomeScreen:801` `HomeEffect.MoveCameraTo` (zona / navegar) | destino deliberado | ⛔ **donde mordió** → `goToPlace` |
| `HomeScreen:1002` FAB de punto medio | encuadre deliberado | ⛔ **donde mordió** → `framePlaces` |
| `HomeScreen:935` `StepToVehicle` → GPS | destino deliberado | ⛔ **donde mordió** → `goToPlace` |
| `HomeScreen:511` tap en marcador de plaza | destino deliberado | ✅ ya funcionaba (por el puntero) → pasa por la puerta igual |
| `HomeScreen:523` tap en marcador de coche (y FAB de coche, que reusa la lambda) | destino deliberado | ✅/⛔ el marcador funcionaba, **el FAB no** → `goToPlace` cierra las dos |
| `HomeScreen:992` FAB de localización sin viaje | destino deliberado | ✅ inocuo (sin viaje no hay follow) → `goToPlace` |
| `HomeUiController` `centerInitialFocus` / `frameParking` | encuadre automático | ✅ **exento con razón** — §2 |
| `HomeUiController.followDriver` | el propio follow | ✅ exento — revocar aquí se auto-mataría |
| `HomeUiController.resumeDriverFollow` | re-enganche explícito | ✅ exento — engancha, no revoca |
| `ParkingHistoryDetailScreen:143` | otra pantalla, sin follow | ✅ exento — no usa `HomeUiController` |
| `PaparcarMapView` guard `if (following …)` | creía ser la autoridad | ⚠️ se queda, con el comentario corregido — §3 |

## Estado de la implementación

| Fichero | Cambio |
|---|---|
| `presentation/home/HomeUiController.kt` | `moveCamera`/`moveCameraToBounds` pasan a privadas (`setTarget`/`setBoundsTarget`) · puertas públicas nuevas `goToPlace` y `framePlaces`, que revocan el follow · `followDriver`/`resumeDriverFollow`/`centerInitialFocus`/`frameParking` pasan por la privada (neutras) · KDoc de `isProgrammaticMove` y de `onUserMapGesture` corregidos (nombraban funciones que ya no existen y presentaban el gesto como el único revocador) |
| `presentation/home/HomeScreen.kt` | los 8 call sites deliberados pasan a `goToPlace` / `framePlaces` (tabla de arriba) |
| `ui/components/PaparcarMapView.kt` | sólo comentario: el guard `if (following …)` deja de leerse como la autoridad del rango y dice dónde vive, y por qué se queda (§3) |
| `commonTest/.../HomeUiControllerTest.kt` | **9 tests nuevos** — no había ninguno para este controller |

**Suite: 1.874 tests, 0 fallos** (1.865 antes). `:app:compileMockDebugKotlin` +
`:app:compileProdDebugKotlin` OK.

### Criterio de éxito — estado

- ✅ viaje vivo + `goToPlace` → follow revocado y target en la plaza · ✅ **y el siguiente fix GPS ya
  no se la lleva de vuelta al coche** (es lo que se siente en la mano) · ✅ `framePlaces` revoca
- ✅ neutros: `followDriver` ×N sigue enganchado · `centerInitialFocus` centrando en el usuario sigue
  enganchado · `refocusOnParkingArrival` sigue enganchado
- ✅ `resumeDriverFollow` tras revocar vuelve a enganchar · ✅ el dedo sobre el mapa sigue revocando
- ✅ tap repetido al MISMO sitio mueve la cámara otra vez (el token sube)
- ✅ **falsación ×5, en dos rondas**: quitar la revocación de `goToPlace` → caen los 2 tests de
  revocación y **sólo** ésos; y en una segunda ronda, colar `followDriver`, el centrado inicial y
  `frameParking` por la puerta deliberada + quitar la revocación de `framePlaces` → caen los 3
  «keepFollowing» + el de `framePlaces`, cada fallo mapeado a su claim y ninguno de más. Los tres
  tests neutros son de PROHIBICIÓN, así que se han visto fallar antes de darlos por buenos.
- ⏳ **En mano, sin ver**: falta conducir y tocar una fila de plaza del sheet (necesita viaje real).

### Sin impacto en

Strings (ninguno nuevo), Dev Catalog / galería de estados (no hay pantalla, estado ni condición de
routing nuevos: es el rango entre dos peticiones de cámara dentro de Home), `detectionPath`, esquema
de datos, `PARKING-DETECTION.md` (no cambia ni una decisión del algoritmo de detección).
