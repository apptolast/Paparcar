# HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001 · Al entrar en Home se calientan los tres carriles, una vez

**Estado:** ✅ Done · en master (14-09-2026)

## Problema

Pulsar Gasolineras o Parkings por primera vez enseñaba un spinner. La respuesta tardaba lo que
tardase Overpass —medido entre 1,4 s y más de 8 s según el día— cuando ese tiempo se podía haber
gastado mientras el usuario miraba el mapa.

## Doctrina que se ESTRECHA (y por qué no se rompe)

La regla vigente era:

> *Un sujeto que no está a la vista no descarga NADA.*

Pasa a ser:

> *Un sujeto que no está a la vista no descarga nada **cuando la cámara se mueve**.*

El matiz es todo. Lo que la regla protegía —que un usuario que solo arrastra el mapa no pague
peticiones por listas que nunca abrirá— **sigue intacto**: el calentamiento es UNO, al entrar, y a
partir de ahí panear le cuesta cero a un carril oculto. Lo que cambia es únicamente la primera carga.

⛔ Su testigo (`should_notRefetch_when_theLaneIsElsewhere`) no se ha borrado: se ha reescrito para
afirmar exactamente la regla estrecha, que es la que ahora rige. Un test que se borra al cambiar el
comportamiento deja de proteger nada; uno que se reescribe sigue montando guardia sobre lo que queda.

## Diseño

- `NearbyTrigger.WARM_UP` — valor propio, con la semántica de `ENTER` (es una primera pregunta, no un
  reintento). Existe separado porque es el único disparador que actúa sobre un carril que **no está
  en pantalla**, y un log que no distingue «el usuario abrió gasolineras» de «calentamos
  gasolineras» no puede explicar una petición que nadie pidió.
- `warmUpHiddenLanes()` — guardado por `hasWarmedUpLanes`, así que es **una vez por Home**, nunca por
  fix. Se dispara desde dos sitios, los dos idempotentes:
  - el primer fix de GPS, que es cuando Home sabe DÓNDE calentar;
  - el asentamiento de cámara, para el usuario que no concedió ubicación y cuya única señal de dónde
    está mirando es el mapa.
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
| `scheduleNearbyRefresh` | «solo pregunta el carril a la vista» | ✅ sigue siendo cierto para el paneo; su KDoc lo dice ahora completo |
| `NearbyTrigger.FIX` | nunca reintenta un fallo | ✅ intacto — el calentamiento es primera pregunta, no reintento |
| `refreshNearby` | tres disparadores | ✅ el cuarto reutiliza la ruta de `ENTER` sin ramas nuevas |
| Ancla fijada en el INTENTO | evita preguntar dos veces por el mismo suelo | ✅ es justo lo que hace que abrir el carril tras calentar salga gratis |
| `HomeViewModelTest` (5 tests) | la primera pregunta del carril era la del usuario | ✅ ver abajo |

### Los cinco tests que se movieron, y por qué cada uno

Ninguno se relajó: a todos se les movió la línea base porque **la primera pregunta de un carril ya no
es la del usuario abriéndolo, es la de la precarga**.

| Test | Qué hizo falta |
|---|---|
| `should_notRefetch_when_theLaneIsElsewhere` | reescrito sobre la regla estrecha: cuenta 1 tras calentar y exige que panear **no** suba de ahí |
| `should_stayQuiet_when_theCameraBarelyMoved_orTheLayerIsOff` | líneas base relativas en vez de absolutas |
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
