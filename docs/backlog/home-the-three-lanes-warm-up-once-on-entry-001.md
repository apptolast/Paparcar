# HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001 · Al entrar en Home se calientan los tres carriles, una vez

**Estado:** ✅ Done · en master (14-09-2026)

## Regla vigente — HOME-ONE-TAB-ANSWERS-ONE-INTENTION-001

La pestaña decide qué carril se muestra, pero no qué datos se cargan. Tras asentarse la cámara,
`queueComplementRefresh` actualiza parkings y gasolineras en secuencia, priorizando el carril visible;
al cambiar de pestaña también encola ambos. La precarga inicial sigue siendo de una vez, y las plazas
comunitarias mantienen su suscripción de Firestore.

Seguimiento [PARKINGS-SLOW-FUEL-MUST-NOT-BLOCK-MAP-001](parkings-slow-fuel-must-not-block-map-001.md):
se conserva esa secuencia, pero los jobs de peticiones distintas no comparten un mutex sobre los
use cases completos. Al cambiar de zona, la API de parkings puede responder aunque siga pendiente
el combustible del lugar anterior. Las llamadas reales a Overpass siguen pasando por su cuota
compartida, con el mismo límite y tiempo máximo de espera.

## Problema

Pulsar Gasolineras o Parkings por primera vez enseñaba un spinner. La respuesta tardaba lo que
tardase Overpass —medido entre 1,4 s y más de 8 s según el día— cuando ese tiempo se podía haber
gastado mientras el usuario miraba el mapa.

## Decisión original de este ticket

La regla adoptada originalmente era:

> *Un sujeto que no está a la vista no descarga NADA.*

En aquella implementación se estrechó a:

> *Un sujeto que no está a la vista no descarga nada **cuando la cámara se mueve**.*

Esta fue la regla de la implementación original. La sección «Regla vigente» de arriba la sustituye:
ambos complementos siguen ahora los asentamientos de cámara aunque su carril esté oculto.

En el diseño original, ese matiz protegía el coste de los paneos al completar solo la carga
inicial. La regla vigente se actualizó después: cada asentamiento refresca ambos complementos, con
peticiones secuenciales e idempotentes para no repetir consultas dentro de la misma zona.

⛔ El testigo de aquella regla se actualizó después con la decisión vigente: los tests
`should_refreshAllComplementStores_whenCameraMovesWhileSpotsAreSelected` y
`should_refreshFuelWhenItsLaneIsHidden_butIgnoreSmallCameraMoves` comprueban que ambos complementos
siguen a la cámara aunque estén ocultos.

## Diseño

- `NearbyTrigger.WARM_UP` — mismas condiciones de refresco que `ENTER`, pero identifica una carga
  previa en los registros. Si un intento anterior falló y dejó el ancla vacía, puede volver a pedir
  ese dato pendiente.
- `warmUpHiddenLanes()` — guardado por `hasWarmedUpLanes`, así que la precarga inicial es **una vez por
  Home**, nunca por fix. Se dispara con el primer fix de GPS. Si no hay ubicación, el asentamiento de
  cámara carga los dos complementos mediante `scheduleNearbyRefresh`, tanto si están visibles como si no.
- **Secuencial, y no es cuestión de estilo.** El presupuesto de Overpass es UNA pregunta a la vez
  (`OverpassQuota`) porque el servidor concede 2 por IP y una IP es un operador entero de usuarios.
  Lanzar los dos carriles a la vez metería al segundo en una cola que abandona a los 10 s — y un
  calentamiento que falla de forma fiable es **peor que ninguno**, porque además deja al carril
  creyendo que ya preguntó. Por eso el bucle hace `join()` entre uno y otro.
- **Las plazas no están aquí**: vienen de Firestore y ya las carga la lente por defecto. De los
  «tres carriles», solo dos tenían algo que calentar.

## Criterio de éxito

- Al entrar, los dos carriles ocultos tienen filas sin que nadie los haya abierto.
- Abrir un carril después del calentamiento **no lanza ninguna petición** y no enseña spinner — el
  objetivo del ticket, dicho como lo vive el usuario.
- Cinco fixes seguidos no calientan cinco veces.
- Mientras la petición de un carril está en vuelo, el otro **no ha preguntado todavía**.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `scheduleNearbyRefresh` | «solo pregunta el carril a la vista» | ✅ sustituido: refresca ambos complementos al asentarse la cámara |
| `NearbyTrigger.FIX` | nunca reintenta un fallo | ✅ intacto — el calentamiento es primera pregunta, no reintento |
| `refreshNearby` | tres disparadores | ✅ el cuarto reutiliza la ruta de `ENTER` sin ramas nuevas |
| Ancla fijada en el INTENTO | evita preguntar dos veces por el mismo suelo | ✅ es justo lo que hace que abrir el carril tras calentar salga gratis |
| `HomeViewModelTest` (5 tests) | la primera pregunta del carril era la del usuario | ✅ ver abajo |

### Los cinco tests que se movieron, y por qué cada uno

Ninguno se relajó: a todos se les movió la línea base porque **la primera pregunta de un carril ya no
es la del usuario abriéndolo, es la de la precarga**.

| Test | Qué hizo falta |
|---|---|
| `should_refreshAllComplementStores_whenCameraMovesWhileSpotsAreSelected` | actualizado: el paneo refresca ambos complementos aunque siga seleccionada la pestaña de plazas |
| `should_refreshFuelWhenItsLaneIsHidden_butIgnoreSmallCameraMoves` | verifica que un paneo grande actualiza combustible aunque esté oculto, y que uno corto no duplica la petición |
| `should_askAgainInTheSamePlace_when_theFirstAskFailed` | el fallo se arma ANTES del primer fix y se repite al abrir, para que el paneo siga siendo el reintento que el test mide |
| `should_retryOnTheNextCameraMove_butNeverOnAFix_when_theAskFailed` | ídem, con una pregunta más por delante |
| `should_answerForTheLatestPlace_when_theCameraSettlesWhileAnAskIsInFlight` | el gate se arma antes del primer fix: la petición que se queda en vuelo es ahora la de la precarga |

⚠️ Se añadió `holdNextUntil` al spy de gasolineras, que solo lo tenía el de parkings — es lo que
permite mirar qué pasa **mientras** una petición vuela, y sin eso la secuencialidad no era
demostrable.

## Fuera de alcance

- El geocode que bloquea la petición de parkings sin que la fuente lo use — anotado en el ticket del
  cupo.
- Calentar también al volver a Home desde otra pestaña: hoy es una vez por vida del ViewModel. Si en
  campo se nota, es su propio ticket.

## Rebase

14-09-2026, punta de la cadena, rebasada sobre master `84a1577b` con `--update-refs`.
**Sin conflictos**, pese a que master traía `HOME-WITHOUT-LOCATION-STILL-ANSWERS-001`, que toca el
mismo `HomeViewModel`. Suite (2.801 tests) y ambos flavors verdes **después** del rebase — que es la
única comprobación que vale, porque un rebase limpio compila mal a menudo.
