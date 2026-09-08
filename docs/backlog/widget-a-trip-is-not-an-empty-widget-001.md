# WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 · Conduciendo, el widget te pide que marques el coche que estás conduciendo

**Estado:** ✅ Done — en master (squash 08-09-2026)

## Problema

`ParkedWidgetState` (`shared/…/presentation/widget/ParkedWidgetState.kt`) tiene **dos** estados:

```kotlin
sealed class ParkedWidgetState {
    data object Empty : ParkedWidgetState()          // "no hay aparcamiento"
    data class Parked(...) : ParkedWidgetState()     // coche, calle, hora, +N
}
```

No hay un tercero. Y un viaje empieza **liberando** la sesión anterior, así que durante todo el
trayecto `resolveParkedWidgetState` no encuentra sesión activa y devuelve `Empty`. Resultado: el
widget de la pantalla de inicio te invita a *marcar dónde has aparcado* **mientras vas conduciendo**
— justo el único momento en que eso no se puede hacer y no hace falta.

No es un fallo de pintura: es un estado que el modelo no sabe nombrar. El widget dice "no hay nada"
cuando lo que pasa es "hay algo, y va en marcha".

## Doctrina violada

`feedback_no_internals_in_user_copy` en su forma más simple: **causa + consecuencia + remedio**. Un
`Empty` durante un viaje da un remedio ("marca el aparcamiento") para una causa que no existe. Y la
doctrina de detección — *el fallo es asimétrico, ante la duda se pregunta, nunca se afirma de más* —
aquí se rompe por omisión: el widget afirma que no hay coche vigilado cuando lo hay.

## Señales / datos disponibles

- `DetectionPhase` (`domain/detection/DetectionRuntimeState.kt`) ya existe y ya es el vocabulario
  UI-facing del viaje: `Driving` / `Candidate`. Lo empuja el coordinator vía `DetectionPhaseSink`.
- `ParkedWidgetRefresher` (`app/…/widget/`) observa **Room**: `observeActiveSessions()` +
  `observeVehicles()`, `distinctUntilChanged`, y repinta con `updateAll`. **El viaje vivo no está en
  Room**, así que el refresher es ciego a él hoy. Ese es el trabajo real del ticket: llevar la fase
  hasta el refresher, que vive en el mismo proceso que la detección.
- `TripContext.departingVehicleId` da QUÉ coche va en marcha, que es lo que el widget necesita
  nombrar ("Tu Kamiq va en ruta"), sin inventarse el "más reciente".

## Diseño

### D1 · Un tercer estado, y nada más

```kotlin
data class Driving(val vehicleName: String, val isCandidate: Boolean) : ParkedWidgetState()
```

Se resuelve en `resolveParkedWidgetState` (commonMain, testeable) igual que los otros dos: es una
función pura de (sesiones, vehículos, fase). Prioridad: **viaje vivo > aparcado > vacío**, la misma
que ya usan el peek de Home (`UI-BROWSE-DRIVING-OVER-PARKED-001`) y la tira de vehículos. Un coche
en marcha manda sobre otro aparcado, porque es el que está pasando.

Copy: causa + consecuencia, sin remedio porque no hay nada que hacer — *"En ruta · te aviso al
aparcar"*. Strings nuevos → los 9 locales en esta misma tarea.

### D2 · Lo que este ticket NO hace, y por qué

Dos cosas que parecen la continuación natural de `UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001` (que acaba de
poner la dirección en ruta en el chip, la card y el peek) y que aquí **se rechazan con medida**:

**a) Nada de animación.** Glance pinta `RemoteViews`, que no animan: el glifo de ruta que se dibuja
solo (`DrivingRouteGlyph`) y el pulso de la palabra de fase no son portables. Imitarlos exigiría
repintar el widget por frame con la app en segundo plano — lo contrario de la arquitectura que ya
tiene, que repinta **solo cuando cambia el estado** (`ParkedWidgetRefresher`, `distinctUntilChanged`).
Un icono estático de ruta sí; movimiento no.

**b) Nada de dirección en ruta.** `subscribeGpsLocation` está gateado por `mapForeground`
(`HomeViewModel.kt:958-963`): **con la app cerrada — que es el caso del widget — no hay ningún
geocode de usuario corriendo**. Darle calle al widget significa abrir una cola de geocodificación en
segundo plano, con red y batería, durante todo el viaje, para nombrarle una calle a alguien que va
sentado dentro del coche. El coste es real y el beneficio no existe: nadie lee su propia calle en
un widget.

Consecuencia de diseño: el estado `Driving` **no lleva dirección**, y eso no es una carencia — es
lo que hace que cueste dos repintados por viaje (arranque y aparcado) y cero geocodes.

### D3 · Qué se pinta en cada estado — y por qué el viaje no lleva botones

Medido en `ParkedWidget.kt` (no supuesto):

- **`Empty` no pinta ningún icono hoy**: dos textos y nada más (`Empty()`, líneas 104-121). Ni
  botones ni marca.
- **`Parked` es el único que pinta algo**: la marca (`BrandMark`) delante del nombre, y dos
  `CircleIconButton` en el borde derecho — *Cómo llegar* y *Más opciones* (`WIDGET-ACTIONS-002`).

Y los dos botones **no pueden existir en un viaje**, y no es cuestión de gusto: `navigateIntent`
necesita `latitude`/`longitude` y `showParkingIntent` necesita `sessionId`. Un viaje vivo **no tiene
sesión** — se liberó al arrancar. Ofrecer "cómo llegar" al coche en el que vas sentado es, además,
la definición de un botón que no significa nada.

De ahí la regla, que es la que resuelve los tres estados de una vez:

> **El glifo lead es siempre la marca; el estado se ESCRIBE; y un botón solo existe si hay un objeto
> sobre el que actuar.** Solo la sesión aparcada es ese objeto.

| Estado | Glifo lead | Línea 1 | Línea 2 | Botones | Tap en la tarjeta |
|---|---|---|---|---|---|
| `Parked` | marca | `Kamiq  +1 más` | `Calle Mayor 4 · 18:42` | Cómo llegar · Más | mapa en el pin |
| `Driving` **(nuevo)** | marca | `Kamiq · En ruta` | `Te aviso al aparcar` | **ninguno** | abre la app |
| `Empty` | marca **(añadida)** | `Sin coche aparcado` | `Aparca y el widget te dirá dónde` | **ninguno** | abre la app |

Dos decisiones dentro de esa tabla:

1. **Sin icono de ruta en `Driving`.** Sería un segundo glifo compitiendo con la marca en el mismo
   hueco, para decir lo que la línea ya dice con palabras. Un widget no anima (D2), así que el
   dibujo no aportaría el movimiento que en Home sí aporta — solo ruido. El estado se escribe.
2. **La marca entra en `Empty`** — ⚠️ *propuesta, toca un estado que hoy funciona; tirarla no rompe
   nada del resto del ticket*. Hoy `Empty` es el ÚNICO estado sin marca, así que el widget recién
   anclado, antes del primer aparcamiento, es un rectángulo gris anónimo en el launcher: el momento
   exacto en que el usuario tiene que reconocer qué acaba de poner ahí. Con esto, la marca pasa a
   ser constante en los tres estados y deja de ser un adorno del estado `Parked`.

### D4 · Quién es el sujeto: UN resolver, no dos copias

El widget elige coche con `maxByOrNull { it.location.timestamp }` (`ParkedWidgetState.kt:53`). Home
lo elige con `preferredParkingSession(activeSessions, vehicles)`
(`domain/model/PreferredParkingSession.kt`), que es *el aparcamiento más reciente, con el rango de
vigilancia solo como desempate* y que trae por escrito por qué NO se prefiere al que tiene geocerca
ni al mejor vigilado [UI-PREFERRED-SESSION-RECENCY-001].

Coinciden en el caso normal y **aun así hay que unificarlas**: son dos copias de una regla de
dominio que ya es pública, y la que vive en el widget no tiene el desempate ni heredará la próxima
decisión que se tome sobre la otra. Es la misma forma de bug que
`UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001` acaba de cerrar en Home — dos superficies contestando la misma
pregunta por su cuenta.

`resolveParkedWidgetState` pasa a: **viaje vivo** (D1) → si no, `preferredParkingSession(...)` → si
no, `Empty`. Es literalmente la prioridad del peek de Browse, con el mismo código debajo.

### D5 · Y no tiene por qué ser un solo coche

`parked_widget_info.xml` ya declara `resizeMode="horizontal|vertical"` (objetivo 3×1, `minHeight`
40 dp). O sea: **el usuario ya puede estirar el widget a lo alto, y hoy solo gana hueco vacío** — el
contenido pinta una fila sea cual sea el alto, y los demás coches aparcados se colapsan en `+N más`
(`widget_more_parked`). Enseñar varios no es un widget nuevo: es el contenido que falta para los
tamaños que este widget ya acepta.

Forma propuesta (Glance `SizeMode.Responsive`, decidiendo por `LocalSize`):

| Alto | Qué pinta |
|---|---|
| 1 fila (3×1, el de hoy) | solo el sujeto + `+N más` — sin cambios |
| ≥ 2 filas | una fila por coche aparcado, en el orden de `parkedSessionPreference` (el mismo de la tira de Home), con el viaje vivo el primero si lo hay |

Con dos reglas heredadas: las acciones por fila se quedan en *Cómo llegar* (lectura pura) — el
launcher **nunca** sostiene un verbo irreversible de un toque [WIDGET-ACTIONS-002] — y una fila de
viaje no lleva ninguna (D3).

✅ **Aprobado por el user (08-09)** con el argumento que lo hace barato: *"meter 2 coches es Plus, y
tener más de 1 coche es lo mismo"* — desde `FREE-LIMITS-001` el plan gratis es **1 vehículo**, así
que la lista solo puede darse siendo Plus y **el layout corto sigue siendo el que ve todo el mundo**.
Eso acota el riesgo: si un launcher se porta raro con el alto, el 99% de los widgets no se entera.

Cómo quedó implementado:

- `SizeMode.Responsive(COMPACT 180×40, TALL 180×110)` y el alto real se lee de `LocalSize`, no del
  cubo que pedimos.
- `Parked` deja de ser un coche con un contador y pasa a ser **la lista de coches**
  (`cars: List<ParkedCar>`), con `subject` = el primero y `othersCount` derivado. El orden lo pone
  `parkedSessionPreference` — el mismo comparador de la tira de Home — así que la primera fila del
  widget y el primer chip de Home no pueden ser coches distintos.
- `Driving` gana `alsoParked`: conducir un coche no es razón para esconder dónde está el otro. En
  corto manda el viaje; en alto van los dos.
- **Las acciones son por coche**, no del sujeto: `navigateIntent`/`showParkingIntent` toman un
  `ParkedCar`. Con una sola acción "del estado", la segunda fila te habría llevado al primer coche
  — el fallo silencioso clásico de una lista.
- La marca se pinta **solo en la primera fila**: repetirla en cada coche la convertiría en un badge
  del vehículo en vez de la identidad del widget.

### D6 · Fontanería (a decidir al implementar)

El refresher necesita ver la fase. Opciones, por orden de preferencia:

1. Que el `combine` del refresher tome un tercer flow con la fase del runtime de detección (mismo
   proceso, sin persistencia nueva).
2. Si la fase no es observable desde `:app` sin arrastrar dependencias de detección, exponerla en el
   mismo canal que ya usa el servicio para hablar con la UI, no un bus nuevo — la kdoc de
   `TripContext` lo pide explícitamente: *"future per-trip signals extend this type rather than
   adding new buses"*.

**Resuelto al implementar, y el riesgo era el contrario del que anoté.** `DetectionRuntimeState` es
un singleton de Koin que el servicio actualiza y que vive en **este mismo proceso**, así que el
`combine` del refresher toma tres flows más (`isRunning`, `trip`, `phase`) y ya está — sin bus
nuevo, sin persistencia.

Y sobre la muerte de proceso: anoté que el widget podría quedarse clavado en "En ruta" **para
siempre**. Falso, y al revés — el estado es EN MEMORIA, así que un proceso nuevo arranca con
`isRunning = false` y el widget vuelve solo a `Parked`/`Empty`. Lo que sí queda es una ventana
acotada: mientras nadie empuje un update, el launcher sigue enseñando el último render, como mucho
hasta el `updatePeriodMillis` de 30 min. Persistir la fase habría **creado** el bug que yo temía.

## Criterio de éxito

- Con un viaje en curso, el widget dice que el coche va en ruta y **deja de pedir que lo marques**.
- Al aparcar, vuelve a `Parked` con su calle, como hoy.
- Sin viaje, el widget es byte a byte el de hoy (mismo `Empty`, mismo `Parked`).
- Muerte de proceso a mitad de viaje → el widget NO se queda clavado en "En ruta".
- Repintados por viaje: 2 (arranque, aparcado). Ni uno por fix de GPS.
- `ParkedWidgetStateTest` cubre el nuevo estado, incluida la prioridad viaje > aparcado.
- **Con dos coches aparcados, el widget y el peek de Home nombran el MISMO** — un test con dos
  sesiones de igual timestamp y distinta vigilancia lo prueba, que es justo donde hoy divergen.
- Cero `maxByOrNull` de sesiones fuera de `PreferredParkingSession.kt`.
- Strings nuevos en los 9 locales.

## Consumidores auditados

| Sitio | Qué asume | Veredicto |
|---|---|---|
| `resolveParkedWidgetState` | dos estados | **cerrado** — D1 |
| `ParkedWidget` (Glance) | pinta `Empty`/`Parked` | **cerrado** — rama nueva, sin animación |
| `ParkedWidgetRefresher` | el estado solo cambia en Room | **cerrado** — D3 |
| `ParkedWidgetPinner` / `WidgetPinRequester` | anclaje del widget | **exento** — no lee estado |
| `WIDGET-ACTIONS-002` (acciones "me voy" / abrir) | actúan sobre `sessionId` | **exento** — `Driving` no tiene sesión que liberar, así que no ofrece acciones |
| `onb-widget-is-the-last-step-001` | el widget como último paso del onboarding | **a revisar** — que el paso no se explique con un widget en un estado que aún no existía |

## Sistema de pruebas mock

- `StateGalleryScreen.kt`: el widget no tiene variante de galería (no es una pantalla de la app). Si
  al implementar se añade un preview de Glance, va en paridad con los estados del sealed.
- `ParkedWidgetStateTest` es el sitio donde esto se prueba de verdad: es una función pura.

## Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` ✅ · `:shared:testDebugUnitTest` ✅
- 5 tests nuevos en `ParkedWidgetStateTest`: el viaje deja de leerse como vacío · el viaje gana al
  coche aparcado · sin origen conocido no se inventa coche · la fase candidata viaja · al terminar
  el viaje se vuelve al aparcado.
- Y el que prueba D4 de verdad: **dos coches aparcados en el MISMO instante**. Con el
  `maxByOrNull` viejo ganaba el primero de la lista; con la preferencia compartida gana el mejor
  vigilado, igual que en Home. Es el único caso donde las dos reglas divergían.
- ⛔ **Apóstrofo**: el copy inglés llevaba "We'll" y en `app/src/main/res` eso hay que escaparlo
  (al revés que en Compose Resources). Reescrito sin contracción en vez de escapar — una frase que
  no necesita el apóstrofo no puede romperse al copiarla a otro locale.
- D5: 3 tests más — la lista sale en el orden de Home · el otro coche sigue visible mientras uno
  conduce · **cada fila lleva sus propias coordenadas y su propia sesión**, que es lo que impide que
  la segunda fila navegue al primer coche.
- ⏳ En mano: anclar el widget y conducir, para ver las tres caras y que el paso a `Parked` llega.
- ⏳ En mano (D5): estirarlo a lo alto **con dos coches aparcados**, y comprobar el alto en los dos
  móviles — los launchers no reportan el tamaño igual, y eso no se decide en la maqueta.

## Notas

- Nace de `UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001` (Home dice dónde va el coche en ruta). Comparten el
  diagnóstico — *un viaje vivo es un estado con derecho a nombre* — y **no comparten fontanería**:
  el widget no ve los slices de Home.
- No toca detección: lee una fase que ya existe. No toca `PARKING-DETECTION.md`.
