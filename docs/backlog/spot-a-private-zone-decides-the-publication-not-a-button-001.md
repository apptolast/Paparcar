# SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001 · La zona privada decide si se publica, y lo decide al PUBLICAR

**Estado:** ✅ Done · rama `feature/SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001-private-zone-gates-publication`

Verificado: `:shared:testDebugUnitTest` **2.799 tests, 0 fallos** (con `--rerun-tasks`, para que los
guardarraíles Konsist no salgan UP-TO-DATE) · `:app:compileProdDebugKotlin` · `:app:assembleMockDebug`.

## Problema

Tres síntomas, un solo invariante mal colocado.

### 1. La liberación manual publica la plaza de tu garaje

`ReleaseActiveParkingSessionUseCase.kt:53-57` decide publicar con dos términos —
`reason.publishesSpot` y `SpotReleasePolicy.shapeFor(...)`— y **nunca lee
`currentSession.privateZoneId`**. Las otras tres puertas a la comunidad sí lo hacen:

| Puerta | Comprueba zona privada |
|---|---|
| `ProcessConfirmedDepartureUseCase.kt:145` | ✅ `session.privateZoneId == null` |
| `FinalizeDeducedDepartureUseCase.kt:114` | ✅ |
| `RetractDeducedDepartureUseCase.kt:99` | ✅ |
| `ReleaseActiveParkingSessionUseCase` | ❌ **no existe el término** |

Repro: aparcar dentro de una zona privada → peek del aparcamiento → «Me voy» → «Liberar plaza».
La plaza del garaje sale a la comunidad. Y ese es el botón **primario** del diálogo.

Ya estaba catalogado como hallazgo sin adjudicar en `docs/detection/07-duplicacion.md:329-335`,
con la incógnita anotada: *«NO VERIFICADO si la UI esconde el botón para sesiones HOME_GEOFENCE —
si no lo hace, es un bug real»*. **Verificado: no lo esconde.** `HomeReleaseDialog` no recibe nada
sobre la zona y `HomeScreen.kt:1089` cablea el primario a `DEPARTURE_PUBLISHED` sin condición.

Y el testigo de por qué sobrevivió: `ReleaseActiveParkingSessionUseCaseTest.kt:253-266` **sí**
tiene tests de «zona»… pero de `zoneRadiusMeters`, el círculo de duda del honest-close. La palabra
*zone* nombra dos cosas distintas en el mismo fichero, así que la suite verde parecía haber mirado.

### 2. Una zona creada con el coche ya aparcado no protege ese aparcamiento

`privateZoneId` se sella **una sola vez**, en `ConfirmParkingUseCase.kt:169-171`, contra
`getPrivateZonesSnapshot()` del instante de aparcar. Nadie lo revisa nunca más. Falla simétrico:

- zona **creada** después de aparcar → la sesión no queda protegida;
- zona **borrada** o pasada a pública después → la sesión sigue callada para siempre, aunque el
  usuario retiró la privacidad;
- zona **movida o reducida** → igual.

### 3. El historial no refleja la zona privada, y encima pierde la estrategia

`ParkingDetectionSource.PrivateZone` (`ParkingDetectionSource.kt:39-40, 62`) **sustituye** a la
estrategia en la fila de detección del detalle (`ParkingHistoryDetailScreen.kt:767-781`). Un
aparcamiento en tu garaje deja de decir si lo detectó el Bluetooth o el asistido — justo el campo
que existe *para* responder eso [UI-HISTORY-IDENTITY-AND-SOURCE-001]. Y como se deriva de
`spotType == HOME_GEOFENCE`, que `ConfirmParkingUseCase.kt:176-185` solo asigna cuando
`spotType == AUTO_DETECTED`, **un pin puesto a mano dentro de una zona privada dice «manual» y no
menciona la zona**, aunque sí lleva `privateZoneId` sellado. Dos campos contestando a la misma
pregunta con respuestas distintas.

Y en ningún caso se dice la consecuencia: que ese aparcamiento **no liberó plaza**.

## Doctrina violada

- **Sistemas, no parches.** El invariante *«una plaza en zona privada no se publica»* vive hoy en
  tres copias y falta en la cuarta. Cerrar solo la cuarta sería el parche.
- **INTENCIÓN ≠ AFIRMACIÓN** [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]. `publishesSpot`
  en `ParkingReleaseReason` nombra una consecuencia que la política puede negar: es la intención
  del usuario, no el veredicto del sistema. Un botón que dice «Liberar plaza» y no libera ninguna
  es la misma mentira que ese ticket quitó del maestro de detección.
- **Un caso de uso por VEREDICTO, nunca por predicado** [DET-VERDICT-NOT-PREDICATE-001]. Esto no
  es un caso de uso nuevo: es un predicado que alimenta a un veredicto que ya existe
  (`SpotReleasePolicy`), con cuatro consumidores. Va dentro de él.
- **Copy: causa + consecuencia + remedio.** Publicar en silencio nada mientras el botón promete una
  plaza es el fallo mudo del §I18N aplicado a un botón.

## Señales / datos disponibles

- `ZoneRepository.getPrivateZonesSnapshot()` ya existe y es una lectura de Room (offline-safe).
  Hoy la usa un solo llamante, `ConfirmParkingUseCase`.
- `ZoneRepository.observeZones()` ya alimenta `HomeState.zones` — el VM de Home ya tiene la lista.
- `haversineMeters` en `domain/util` — puro, ya es el que usa el sello de hoy.
- `SpotReleasePolicy` ya es *la* función que responde «¿con qué forma publica esta sesión?» y ya
  tiene una respuesta «no sale nada» (`Shape.Withheld`), que los cuatro consumidores ya respetan.
- `UserParking.publishedSpot` ya es durable y sincronizado — el historial ya sabe si hubo plaza.

## Diseño

### A · El invariante, en UN sitio: `SpotReleasePolicy`

La pregunta pasa del momento de **aparcar** al momento de **publicar**, que es cuando se hace. Con
eso los tres síntomas caen a la vez y sin pasada de re-evaluación:

```kotlin
sealed interface Shape {
    data class Exact(val confidence: Float) : Shape
    data class Zone(val radiusMeters: Float, val confidence: Float) : Shape
    data class Withheld(val reason: WithholdReason) : Shape   // era `data object`
}
enum class WithholdReason { PRIVATE_ZONE, AREA_TOO_WIDE }

fun privateZoneAt(location: GpsPoint, privateZones: List<Zone>): Zone?
fun shapeFor(location, zoneRadiusMeters, detectionReliability, privateZones): Shape
```

- La zona privada gana al techo de área: las dos retienen, pero el motivo es distinto y lo leen el
  log, el diálogo y el historial. `Withheld` pasa de `data object` a `data class` **a propósito**:
  el compilador señala así los cuatro `== Shape.Withheld` que hay que barrer.
- `privateZoneAt` es el predicado compartido (política + `ConfirmParkingUseCase` + UI). Función
  pura de nivel superior, sin ceremonia de clase inyectada — el patrón de `SentryWakeCooldown`.
- **`privateZoneId` deja de ser un veredicto** y queda como *provenance*: qué zona era al aparcar.
  Su único lector de decisión que sobrevive es el `spotType = HOME_GEOFENCE` de `ConfirmParking`.
  No se borra la columna (migración de Room a cambio de nada).

**Por qué no re-evaluar la sesión activa al crear la zona** (la otra opción que se planteó): sería
una segunda fuente de verdad. Habría que dispararla al crear, editar, mover, redimensionar y
borrar zonas, sobre todas las sesiones activas, y correría contra la primera. Mover la pregunta al
punto donde se hace no necesita ninguna de esas cinco.

### B · Un solo botón: irse es irse

`ParkingReleaseReason` pierde `DEPARTURE_UNPUBLISHED` y se queda en dos valores:

| Antes | Ahora |
|---|---|
| `DEPARTURE_PUBLISHED` (publishesSpot=true) | `DEPARTURE` (`offersSpot = true`) |
| `DEPARTURE_UNPUBLISHED` (publishesSpot=false) | **borrado** |
| `RECORD_DELETED` | igual |

`publishesSpot` → **`offersSpot`**: el usuario OFRECE la plaza; si sale o no lo dice la política.

`DEPARTURE_UNPUBLISHED` era la única forma que tenía el usuario de decir «no compartas esta
plaza» — una decisión **por salida**, que se pregunta en el peor momento (con el coche arrancado) y
hay que volver a tomar cada vez. La decisión correcta es **por sitio** y es durable: eso ya existe
y se llama zona privada. El remedio del copy apunta ahí.

El diálogo (`HomeReleaseDialog`) queda en primario «Me voy» + cancelar, y el cuerpo **dice lo que
va a pasar** en vez de preguntarlo:

- caso normal → «Al irte, tu aparcamiento libera una plaza para la comunidad.»
- zona privada → «Aparcaste en *<zona>*, una zona privada: al irte no se publica ninguna plaza.»

Borrar el aparcamiento sigue donde estaba: `RECORD_DELETED` en el peek de corregir aparcamiento
(`AddingParkingPeek.kt:248`), que es el único botón que dice «borrar» — sin tocar.

⛔ Nada de modal sobre modal, nada de tercer botón. [feedback_no_modal_over_modal]

### C · El historial separa los dos ejes

`spotType` mezcla **quién puso el pin** con **dónde está**. Se separan:

1. `ParkingDetectionSource` pierde `PrivateZone` — no es una fuente. `HOME_GEOFENCE` pasa a
   resolverse por `detectionPath` como cualquier otro pin automático, así que la fila de detección
   por fin dice la estrategia real (Bluetooth / asistido / manual) también en tu garaje.
2. Fila nueva en el detalle, junto a `ApproximateZoneRow`: **«<zona> · zona privada — no se publicó
   plaza»**, resuelta EN VIVO contra las zonas actuales (`privateZoneAt`), así que aparece también
   en un pin manual dentro de la zona, que hoy no la menciona.

Resuelta en vivo y no por el sello porque `privateZoneId` **no se sincroniza** (no está en
`ParkingHistoryDto`): en un móvil nuevo el sello no existe, y las zonas sí.

## Criterio de éxito

- `ReleaseActiveParkingSessionUseCaseTest`: «Me voy» sobre una sesión dentro de una zona privada
  **no** encola `reportSpotReleased` y devuelve `false` (nada que agradecer). Este test es el que
  faltaba.
- Una zona creada DESPUÉS del park protege la sesión activa, sin tocarla.
- Una zona borrada después deja de protegerla.
- El diálogo tiene un botón y su cuerpo nombra la zona cuando la hay.
- El detalle de una sesión en zona privada dice la estrategia **y** la zona.
- `:shared:testDebugUnitTest` verde + `assembleMockDebug` + `compileProdDebugKotlin`.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `ReleaseActiveParkingSessionUseCase` | 🔧 gana el gate (era el hueco) |
| `ProcessConfirmedDepartureUseCase:141,145,183,234` | 🔧 el término suelto pasa a la política |
| `FinalizeDeducedDepartureUseCase:94,113` | 🔧 idem |
| `RetractDeducedDepartureUseCase:99` | 🔧 idem — ⚠️ ver «Limitación» |
| `ConfirmParkingUseCase:169-171` | 🔧 usa `privateZoneAt`; sigue sellando provenance + `HOME_GEOFENCE` |
| `SpotReleasePolicy.Shape.Withheld` (4 comparaciones `==`) | 🔧 pasan a `is` (el compilador las señala) |
| `ParkingReleaseReason` · `HomeScreen:1089-1090` · `HomeReleaseDialog:42-57` · `HomeViewModel` | 🔧 un botón |
| `VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease` | ✅ lee `isDeparture`; `DEPARTURE` lo mantiene |
| `DetectionEventDto:238` (`reason.name`) | ✅ escritura only, sin deserialización al enum — borrar el valor es seguro |
| `AddingParkingPeek:248,257` (`RECORD_DELETED`) | ✅ exento, no se toca |
| `ParkingDetectionSource` · `ParkingHistoryDetailScreen:767-781` | 🔧 separación de ejes |
| `ParkingHistoryViewModel/State` | 🔧 observan zonas |
| `HistoryTimeline` (lista) | ✅ exento: la fila de lista no habla de publicación, no hay nada que contradecir |
| `SpotType.HOME_GEOFENCE` | ✅ se conserva: es el único registro SINCRONIZADO de que el park fue en casa |

## ⚠️ Limitación conocida y deliberada

`RetractDeducedDepartureUseCase` no pregunta «¿es privada?» sino «¿hay documento que retirar?», y
hoy usa la zona privada como *proxy* de «no se publicó nada». Con el gate en vivo el proxy puede
divergir si el conjunto de zonas cambia entre la publicación provisional y la retractación — una
ventana acotada por `PROVISIONAL_SPOT_TTL_MS` (12 min). Dirección del fallo: una plaza publicada
*antes* de que la zona existiera se deja expirar por TTL en vez de retirarse. Acotado y en el lado
barato; cerrarlo de verdad exige un campo durable «esta deducción publicó» = Room v8 + DTO +
serializadores end-to-end, que es una cola desproporcionada para 12 minutos.

## Lo que cambió respecto al diseño inicial

- **`Shape.Withheld` lleva el motivo** (`WithholdReason.PRIVATE_ZONE` / `AREA_TOO_WIDE`) en vez de
  ser un `data object` mudo. Sin él, el log, el copy del diálogo y la fila del historial no podían
  distinguir «no te mando a rastrear un barrio» de «esto es tuyo». Efecto secundario buscado: pasar
  de `data object` a `data class` convierte los cuatro `== Shape.Withheld` en `is`, y el compilador
  enumera el barrido en vez de confiar en un grep.
- **`ZoneRepository` va sin nullable en las cuatro puertas**, contra la costumbre de la zona (todo
  colaborador de alrededor es opcional por comodidad de los dobles de test). Un lector a null
  significaría «no retengas nada»: el bug de vuelta, en silencio, por cualquier construcción nueva
  que olvidara el argumento.
- **`ParkedVehicleSummary.privateZoneId` se borra.** Viajaba a la capa de mapa y no lo leía nadie —
  el último tentáculo del sello hacia la UI, y una invitación a decidir con la respuesta vieja.
- **El fake de zonas del sabor mock era `emptyList()` fijo**, así que la mitad «zona privada» de la
  app era inalcanzable en TODO el set de pruebas: el diálogo nunca podía enseñar su copy y el
  historial nunca su fila. Ahora es scenario-aware (`MockScenario.parkedInPrivateZone` + interruptor
  en el Dev Catalog) y persiste de verdad las zonas creadas dentro de la app.
- **Galería:** la variante «Geocerca de casa · furgoneta» pasa a «Zona privada · asistido ·
  furgoneta» (los dos ejes a la vez) y se añade «Zona privada · pin manual», el caso que no se veía.

## Fuera de alcance (apuntado, no arreglado)

- `ReleaseActiveParkingSessionUseCase` **no resetea `DepartureEventBus`** (ni lo inyecta), mientras
  que `ProcessConfirmedDepartureUseCase:288` sí. Tras una liberación manual el bus conserva el
  ENTER viejo del viaje de llegada. Segundo hallazgo de `07-duplicacion.md:333-335`, sin adjudicar;
  es un bug de detección, no de publicación → ficha propia en
  `docs/backlog/det-a-manual-release-leaves-the-arrival-enter-in-the-bus-001.md`, con las tres
  preguntas que hay que medir antes de tocarlo (añadir el reset «por simetría» sería el parche que
  la doctrina prohíbe).
