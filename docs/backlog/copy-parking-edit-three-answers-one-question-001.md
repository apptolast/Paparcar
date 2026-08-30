# COPY-PARKING-EDIT-THREE-ANSWERS-ONE-QUESTION-001 · las tres acciones de editar un aparcamiento contestan la misma pregunta, y ninguna la formulaba

**Estado:** ✅ Done · rama `chore/COPY-PARKING-EDIT-THREE-ANSWERS-ONE-QUESTION-001-edit-actions` ·
worktree `../Paparcar-copy-parking-edit`

## Problema

Al editar un aparcamiento (`AddingParkingPeek` en modo `isEditing`) el usuario arrastra el mapa y
se encuentra tres botones apilados:

| Botón | Qué ejecuta de verdad |
|---|---|
| **Corregir ubicación** (filled) | `UpdateParkingLocationUseCase` con el **mismo `sessionId`**: mueve el pin y conserva hora de aparcado, procedencia y fiabilidad; re-registra la geocerca en su sitio |
| **He aparcado en otro sitio** (outlined) | `ConfirmParkingUseCase` con id nuevo: **otro aparcamiento**. Borra la fila anterior y su geocerca huérfana, reinicia el contador, pasa a pin manual (`MANUAL_REPORT` / path `manual`) y corta la detección en curso |
| **Eliminar registro** (rojo) | `ReleaseParking(RECORD_DELETED)`: borra el aparcamiento, no publica plaza y no toca el vehículo activo |

Los tres nombres están escritos en **tres niveles distintos**: uno nombra el efecto sobre el dato
(*ubicación*), otro la causa en primera persona (*he aparcado…*), y el tercero el objeto interno
(*registro*). Y el eje que decide de verdad — **¿es el MISMO aparcamiento o es OTRO?** — no aparece
en ninguna etiqueta.

Descartada en revisión la variante «Aparcar aquí de nuevo»: *de nuevo* se pega a *aquí* y se lee
como «volver a aparcar en el mismo sitio», justo lo contrario del significado.

## Doctrina violada

- **`COPY-SPOT-IS-NOT-A-PARKING-001`** — «registro» no es ninguno de los dos sustantivos del
  producto. Lo que se borra es un **aparcamiento**. Además el diálogo que abre ese mismo botón ya
  lo llamaba «aparcamiento» («¿Borrar este aparcamiento?» / «Se elimina el registro»): el flujo se
  renombraba a sí mismo entre el botón y su confirmación.
- **No copy al usuario con mecánica interna** (CLAUDE.md § Cosas que NO hacer). «Registro» es el
  nombre de la fila de Room, no el de la cosa que el usuario cree tener.

## Señales / datos disponibles

- El tiempo aparcado **es visible** en el peek de la sesión (`ParkingPeek.kt:182-184`,
  `home_peek_parking_duration_*`), así que «corregir mantiene el tiempo / uno nuevo lo pone a cero»
  describe una consecuencia que el usuario puede ver, no un detalle interno.
- El banner de modo edición reutilizaba la línea secundaria de CREAR («Una geocerca te avisará
  cuando te vayas»), que en edición no aporta nada: la geocerca ya existe.

## Diseño

**Un sustantivo y una forma verbal para las tres**, de modo que se lean como tres respuestas a la
misma pregunta:

| | ES | EN |
|---|---|---|
| filled | Corregir ubicación *(sin cambios: la única que no crea nada)* | Correct location |
| outlined | **Marcar aparcamiento nuevo** | Mark new parking |
| rojo | **Eliminar aparcamiento** | Delete parking |

«Marcar aparcamiento» es el verbo que el glosario ya reserva para lo TUYO; «nuevo» dice la
consecuencia sin explicarla.

El desempate va donde le toca — el banner, que en edición pasa a tener línea propia:

> *Corregir mantiene el tiempo aparcado; marcar uno nuevo lo pone a cero.*

Eso obliga a partir `home_add_parking_helper_secondary` en `_create` / `_edit`, con la misma
simetría que ya tenía `home_add_parking_helper_primary_*`.

### El mismo defecto en el traspaso a mapas

La misma acción —abrir la app de mapas— tenía **tres keys**: `home_navigate_to_spot` y
`home_navigate_to_vehicle` (ambas «Directions» / «Cómo llegar») y
`parking_detail_navigate_action` («Navigate to this location») en el detalle del historial. Dos
keys con el mismo texto ya habían **derivado** en dos idiomas —PT `Como chegar` vs `Direções`, RO
`Indicații` vs `Traseu`—, que es exactamente lo que pasa cuando una etiqueta vive en dos sitios.

Se unifican en **`common_directions`**, que es la etiqueta visible de los dos botones (peek de plaza
y detalle de historial). El tercero **no** se fusiona: es el `contentDescription` de un botón de solo
icono, y ahí «Cómo llegar» a secas no dice al lector de pantalla adónde. Pasa a
`home_navigate_to_vehicle_cd` = «Cómo llegar al coche».

Y no se cambia por «Navegar»: el botón lanza `google.navigation:` pero **cae a `geo:`** cuando no hay
Google Maps (`ExternalNavigation.android.kt:27-37`), y ahí no se navega, solo se abre el punto.
«Cómo llegar» es verdad en las dos ramas.

### Copy muerto que se barre en la misma pasada

`home_parking_edit_dialog_title` («Modificar aparcamiento»), `home_parking_edit_dialog_body` y
`home_parking_action_move_location` («Mover ubicación») solo estaban **importados** en
`PapSheet.kt:70-73`, sin un solo uso: un cuarto nombre para la misma acción esperando a resucitar.
Se borran las tres keys en los 9 locales y los imports muertos que quedaron del diálogo retirado.

## Criterio de éxito

- Las tres etiquetas comparten sustantivo (*aparcamiento*) y forma (verbo + objeto); ninguna nombra
  el «registro».
- El banner de edición explica el único eje que decide, y el de creación no cambia.
- `grep -r "menu_repark\|edit_dialog\|action_move_location"` no devuelve copy muerto.
- Las 9 locales tienen exactamente el mismo juego de keys (Compose Resources crashea si falta una).
- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` + `:shared:testDebugUnitTest` en verde.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `AddingParkingPeek.kt` (3 botones + diálogo de borrado) | ✅ único consumidor vivo de las 3 keys |
| `PapSheet.kt:70-75` | ✅ imports muertos retirados (`action_move_location`, `edit_dialog_*`, `menu_delete`, `release_dialog_cancel`) |
| `ParkingHistoryDetailScreen.kt:485` | ✅ pasa a `common_directions` (era el tercer nombre del traspaso a mapas) |
| `SpotPeek.kt:183` | ✅ pasa a `common_directions` |
| `ParkingPeek.kt:129` | ✅ pasa a `home_navigate_to_vehicle_cd`, con destino explícito para el lector de pantalla |
| `StateGalleryScreen.kt` (galería mock) | ✅ pinta `AddingParkingPeek` vía estado; las etiquetas salen de `strings.xml`, sin cambios |
| 9 × `strings.xml` | ✅ mismo juego de keys |

## Resultado

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` + `:shared:testDebugUnitTest` +
  `:app:assembleMockDebug` en verde.
- Barrido de copy muerto: `home_parking_edit_dialog_title/_body`, `home_parking_action_move_location`
  y `home_add_parking_confirm_edit` («Guardar nueva ubicación») fuera de los 9 locales — eran un
  cuarto y un quinto nombre para la misma acción. Con ellos salen 7 imports muertos de `PapSheet.kt`
  (los 5 de strings + `PapAlertDialog` + `PapDialogAccent`), restos del diálogo retirado.
- DE y NL tenían el sustantivo de la sesión partido entre pantallas (`Parken`/`Parkplatz`,
  `parkeren`/`parkeerplek`). Este ticket los unifica **dentro de esta pantalla** y en su diálogo;
  la deriva del glosario en el resto de la app queda sin barrer (no se abre un barrido de glosario
  desde un ticket de tres botones).

## Follow-ups abiertos

- **`I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001`** — hallazgo colateral de la auditoría de
  paridad: `permissions_btn_allow_background` y `permissions_btn_continue` solo existen en EN y ES,
  y se usan en 3 sitios vivos de la pantalla de permisos. **Pre-existente en master**, y el user lo
  abre como ticket aparte: su doc vive en el árbol principal, **fuera de esta rama**, para que este
  commit siga siendo un solo ticket.
