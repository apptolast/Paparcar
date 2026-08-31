# PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001 · un pin dice siempre quién lo puso

**Estado:** ✅ Done · mergeado a master por squash · ⏳ **sin ver en device**

Dos defectos de **procedencia** salidos del field 30-08. Van juntos porque son la misma pregunta —
*¿quién puso este pin?* — rota por los dos lados: uno emite un valor que nadie reconoce, el otro no
actualiza el valor cuando cambia la respuesta.

## Defecto A · `closed_approximate_zone` no tenía tipo

`RunHonestCloseUseCase` emitía ese label desde que ganó la rama de zona, pero `DetectionPath` sólo
declaraba `ClosedApproximatePin`. `ofLabel()` **falla cerrado** (correctamente, es su doctrina) →
devolvía `null` → `ParkingDetectionSource.Unknown`.

Resultado: **la sesión con MÁS duda del día era la única cuya procedencia la app no sabía decir** —
en la pantalla exacta que un usuario abre para preguntar quién puso un pin que no esperaba. Field
30-08, Oppo 23:48: `closed_approximate_zone` con 196,5 m de radio, procedencia `Unknown`.

Lo que mantuvo el agujero callado: **su hermano el pin exacto resolvía bien**, así que la familia
parecía cubierta.

⚠️ De paso: el KDoc de `ClosedApproximatePin` decía *"drew the AREA it was willing to stand behind"*
— la descripción de la ZONA sobre el tipo del PIN. La zona se había olvidado hasta en su propia
documentación.

## Defecto B · arrastrar un pin no cambiaba su procedencia

`SaveManualParkingUseCase.save()` calcula un `detectionPath` y, en la rama de edición
(`editingParkingId != null`), llama a `updateParkingLocation(id, gps)` **descartándolo**. El DAO
sólo tocaba coordenadas, dirección y POI.

Resultado: un pin que el usuario había arrastrado a mano seguía diciendo
`unattended_zone_gap_anchor` con `reliability = 0.5` **y conservando su radio de duda**. El pin
mentía sobre quién lo puso, que es justo lo que `DET-PIN-PROVENANCE-001` existe para evitar. Field
30-08 19:32:44, Redmi.

## Doctrina violada

- `DET-PIN-PROVENANCE-001` — todo pin persiste quién lo colocó, y ese dato es la base del método de
  forensics de campo.
- Fallo asimétrico aplicado a lo que le contamos al usuario: `Unknown` no afirma nada (bien), pero
  aquí el sistema **sí sabía** la respuesta y la perdía por un tipo que faltaba.

## Diseño

**El label se deletrea UNA vez, en el tipo.** Los emisores leen su constante de `DetectionPath`
(`RunHonestCloseUseCase.OUTCOME_APPROXIMATE_*`, `SaveManualParkingUseCase.PATH_*`), así que emitir
un label sin tipo deja de ser posible: no hay un segundo sitio donde escribirlo. Es la misma forma
de defecto que [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001] — dos listas a mano de un solo contrato.

Tipos nuevos:
- **`ClosedApproximateZone`** (`closed_approximate_zone`) — el hermano que faltaba.
- **`UserMovedPin`** (`user_moved`) — deliberadamente **distinto de `manual`**: aquel nació a mano,
  éste nació del detector y fue corregido. Un trazado que lee `manual` no puede distinguir *"la app
  nunca vio este aparcamiento"* de *"la app lo vio y lo puso mal"*, y **sólo el segundo es un fallo
  de detección que perseguir**.

El arrastre reescribe los **tres campos a la vez**, porque responden a la misma pregunta:
`detectionPath` → `user_moved`, `detectionReliability` → 1.0 (ground truth, el mismo valor que un
pin puesto a mano), `zoneRadiusMeters` → **NULL** (la duda era sobre dónde estaba el coche y el
usuario acaba de responderla; dejar el radio dibujaría una diana alrededor de un punto que el
usuario señaló).

## Criterio de éxito

Un testigo que habría fallado **el día que se escribió la rama de zona** — no la lista
comprobándose a sí misma.

## Consumidores auditados

- ✅ `RunHonestCloseUseCase` — emite ambos labels desde el tipo. **Cerrado.**
- ✅ `SaveManualParkingUseCase` — sus 3 paths leen del tipo. La rama MOVE **no** tiene constante
  propia a propósito: no construye pin, delega en `UpdateParkingLocationUseCase`, que es quien posee
  la procedencia de un arrastre. Una constante ahí sería el segundo sitio otra vez.
- ✅ `UserParkingDao.updateLocation` + `UserParkingRepositoryImpl` + `UserParkingRepository` +
  `UpdateParkingLocationUseCase` — la cadena entera pasa path y reliability. **Cerrado.**
- ✅ **Los DOS fakes** (`commonMain/fakes` y `commonTest/fakes`) espejan el UPDATE real, incluido el
  borrado del radio. Un fake que conservara el radio dejaría pasar en verde una conducta que
  producción no tiene.
- ⚪ `UserConfirmStage.kt:79` emite el literal `"user"` inline. **Exento con razón**: es un
  `pathLabel` de decisión del coordinator, no una escritura de `detectionPath` a la sesión; el path
  persistido de esa vía lo pone `SaveManualParkingUseCase.PATH_USER`, que sí lee del tipo. Anotado
  por si un día ese literal pasa a persistirse.
- ⚪ **Sin migración de Room**: el UPDATE toca columnas que ya existían; el esquema no cambia.

## Estado de verificación

- ✅ `:shared:testDebugUnitTest` → **2.036 tests** (2.033 de base tras rebase + 3 nuevos).
- ✅ **Falsación de los DOS guards, en la misma pasada**: (a) sacar `ClosedApproximateZone` de
  `fixedLabelPaths` → `should_giveEveryEmittedLabelAType_not_justTheOnesInTheList` **ROJO**;
  (b) devolver al arrastre el path del detector →
  `should_rewriteProvenanceToUserMoved_when_theUserDragsThePin` **ROJO**.
- ✅ `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin`.
- ⏳ Sin strings nuevos ni pantalla nueva → no toca los 9 locales ni el Dev Catalog. La UI de
  procedencia ya sabe pintar cualquier `DetectionPath`; ahora recibe dos más.
- ⏳ **Los pines ya escritos no se reparan**: un pin arrastrado ANTES de este cambio sigue con la
  procedencia del detector. Sin backfill (pre-beta).

## Interacción conocida con `SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001`

Aquel ticket hizo que el reconcile rescate el radio local cuando el remoto trae null
(`r.zoneRadiusMeters ?: l?.zoneRadiusMeters`). Aquí el arrastre pone el radio a null **a propósito**.

En un solo dispositivo no hay conflicto: tras el arrastre el row local queda `pendingSync=1` con
`updatedAt` fresco, gana el LWW y sube el null; a partir de ahí ambos lados son null.

⚠️ **Caso multi-dispositivo, aceptado y anotado**: si el usuario arrastra en el dispositivo A y el
dispositivo B todavía tiene el radio viejo en Room, al sincronizar B haría `remoto(null) ?: local(250)`
→ **B resucitaría la diana** en un pin que A corrigió a mano. No se da en el banco actual (los dos
móviles usan cuentas distintas). El arreglo, si algún día importa, es distinguir "el remoto no traía
el campo" de "el remoto dice que no hay radio", que hoy son el mismo `null`.
