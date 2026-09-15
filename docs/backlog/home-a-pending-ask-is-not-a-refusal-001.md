# HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001 · Home no avisa de la falta de ubicación mientras la pregunta sigue en pantalla

**Estado:** ✅ **Done** — verificado con `:shared:testDebugUnitTest` + `:app:compileProdDebugKotlin`
+ `:app:compileMockDebugKotlin` con `--rerun-tasks` (**2.979 tests, 0 fallos**) y
`:app:assembleMockDebug`. ⏳ Falta verlo en mano: instalación limpia en device y primera entrada
real, con «Permitir» y con «No permitir».

## Problema

Petición del user: *«cuando iniciamos home y aparece el diálogo de permiso en primer plano, por
detrás se ven ya los warning "sin ubicación"»*.

Primera entrada a Home, `askFineLocationOnFirstEntry` (`HomeViewModel.kt:222`) lanza el diálogo de
sistema **sobre el mapa**, y detrás de ese diálogo la app ya está contestando la pregunta que el
usuario tiene delante sin responder:

- la **píldora ámbar «Sin ubicación»** (`HomeLocationPill`, alimentada por
  `HomeFabsSlice.locationMissing` = `homeHasNoLocation(...)`, `HomeSlices.kt:457`) es cierta desde el
  primer frame: sin permiso concedido, no hay permiso;
- y la **fila ámbar «Activa la ubicación · Paparcar no funciona sin ella»**
  (`DetectionStory.BlockedCore`), que **sí se ve**, porque el checklist guiado **abre la hoja de
  golpe** en el primer frame en que existe (`HomeSheetPositioning.kt:523-535`, el `snapTo` de
  `firstStepsCue`). O sea: detrás del diálogo hay una hoja expandida con un aviso ámbar sobre el
  permiso que se está pidiendo en ese mismo instante.

El user propuso tapar Home con una plantilla borrosa. **No es el arreglo**, y conviene dejarlo
escrito para no volver a proponerlo:

1. Contradice la razón por la que se pregunta ahí. El KDoc de `askFineLocationOnFirstEntry` lo dice:
   *«the map behind the dialog is its own context, so there is no permissions screen in the
   cold-start chain any more»* [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]. La pantalla de
   permisos se quitó **para que detrás del diálogo se viera lo que se gana concediendo**; un
   esqueleto borroso devuelve exactamente lo que esa decisión eliminó.
2. Android ya oscurece el fondo del diálogo de runtime: un scrim nuestro encima es doble oscurecido.
3. `Modifier.blur` es **API 31+** (usa `RenderEffect`). Con `minSdk 26`, en Android 8–11 no se
   aplicaría y se vería nítido igual — el remedio no sería ni consistente entre dispositivos.

El problema no es de acabado visual, es de **secuencia**: avisamos de la falta de un permiso que
acabamos de pedir y que todavía nadie nos ha negado.

## Doctrina violada

- **[ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]** — «sin diálogos de culpa». Un aviso ámbar
  detrás de la propia pregunta es un diálogo de culpa emitido antes de que haya culpa.
- **`PapColor.attention`** significa «algo PENDIENTE que puedes arreglar»
  [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]. Mientras el diálogo está en pantalla no hay
  nada que arreglar: el remedio ya está delante del usuario, en su propio botón «Permitir». El aviso
  no es falso, es **prematuro**, y un aviso cuyo remedio ya está en pantalla es ruido.
- **Causa + consecuencia + remedio** (copy): la píldora ofrece un remedio —«toca para abrir los
  permisos»— que duplica el diálogo abierto encima. Dos superficies pidiendo lo mismo a la vez.

La otra mitad de esta conversación **ya está bien resuelta y no se toca**: si el usuario dice que
no, `resolveFirstEntryAnswer` (`HomeViewModel.kt:246`) incrementa `locationWarningCue` y la hoja se
abre sola sobre el aviso. El momento de avisar ya existe y es *después* de la respuesta; lo que
sobra es el aviso *antes*.

## Señales / datos disponibles

Todo lo necesario ya existe, repartido entre el ViewModel y las preferencias:

| Señal | Dónde | Qué dice |
|---|---|---|
| `AppPreferences.hasRequestedLocationPermission` | pref persistida, estampada en `HomeViewModel.kt:225` **antes** de lanzar el diálogo | si la pregunta de primera entrada ya se hizo alguna vez |
| `awaitingFirstEntryAnswer` | `var` privada del VM (`:258`) | la pregunta está en el aire, sin respuesta leída |
| `firstEntryDialogShown` | `var` privada del VM (`:259`) | el diálogo llegó a tapar el mapa (paréntesis de `SetMapForeground`) |
| `HomeState.hasCorePermissions` | ya en estado, reactivo | `== hasLocationPermission` (`AppPermissionState.kt:31`) |

⚠️ La pref **no basta por sí sola**: se estampa *antes* de lanzar el diálogo (línea 225), así que
durante toda la ventana del diálogo ya vale `true`. Y el latch tampoco basta solo: entre el primer
frame de Home y el `SetMapForeground(true)` que dispara la pregunta pasa el arranque del mapa, y en
esa ventana la píldora ya habla. Hacen falta **los dos términos**.

## Diseño

**El invariante, en una frase:** *Home no afirma la ausencia de un permiso mientras esa pregunta
está a punto de hacerse o sigue sin respuesta.*

### Un veredicto nuevo, junto al que ya existe

`homeHasNoLocation(...)` sigue siendo **el HECHO** y no se toca. Lo que se añade en
`LocationAvailability.kt` es el permiso para DECIRLO:

```kotlin
fun homeAwaitsLocationAnswer(
    hasCorePermissions: Boolean,
    locationEverAsked: Boolean,
    locationAnswerInFlight: Boolean,
): Boolean = !hasCorePermissions && (!locationEverAsked || locationAnswerInFlight)
```

Los tres casos que produce, y por qué cada término está:

| Permiso | ¿Preguntado? | ¿En el aire? | Veredicto | Por qué |
|---|---|---|---|---|
| concedido | — | — | **habla** | sin el primer término, un GPS apagado con permiso concedido (la otra mitad de `homeHasNoLocation`) se callaría **para siempre**, porque a ese usuario no se le preguntó nunca |
| denegado | no | no | **calla** | la pregunta es inminente: se dispara en el primer `SetMapForeground(true)` |
| denegado | sí | sí | **calla** | el diálogo está tapando el mapa ahora mismo |
| denegado | sí | no | **habla** | ya contestaron que no — este es el caso que el aviso existe para servir |

El caso «concedido y luego revocado en Ajustes» vuelve solo al carril correcto: al volver a Home la
pregunta se vuelve a disparar (la pref sigue en `false` porque aquel usuario nunca fue preguntado),
así que hay silencio breve y después diálogo. Consistente.

### Dos campos de estado, un solo escritor cada uno

`HomeState` gana los dos términos **en crudo** —no el veredicto ya masticado— para que el cálculo
viva en un único sitio y ninguna superficie pueda deletrearlo distinto:

- `locationEverAsked` — espejo de la pref: se lee en `init` y se pone a `true` en el mismo sitio
  donde se estampa (`askFineLocationOnFirstEntry`).
- `locationAnswerInFlight` — **es** el `awaitingFirstEntryAnswer` privado, subido al estado. No es
  una copia: el `var` desaparece y el VM pasa a leer el estado, así que no pueden discrepar.

El valor por defecto (`false`, `false`) hace que el primer frame —antes de que `init` lea la pref—
**calle** si no hay permiso. Es el lado seguro: mejor un frame mudo que un frame acusando.

### Las dos superficies que hablan de más, y la que NO se toca

| Superficie | Cambio |
|---|---|
| Píldora del mapa (`HomeFabsSlice.locationMissing`) | `homeHasNoLocation(...) && !homeAwaitsLocationAnswer(...)` |
| Fila ámbar (`resolveDetectionStory` → `BlockedCore`) | parámetro `locationAskPending`: mientras es `true`, `BlockedCore` devuelve `Hidden` |
| **Cámara** (`HomeScreen.kt:1031`, volar al coche / a la zona sin fix) | **sin cambio, a propósito** |

La cámara lee el HECHO, no el permiso para decirlo, y es justo lo que debe seguir haciendo: volar al
coche aparcado detrás del diálogo es **la mejor versión** del contexto que el diálogo necesita. Si
también esperase, Home abriría en (0, 0) detrás de la pregunta [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001].
Esa asimetría —el mapa actúa, la copy calla— es el ticket entero en una línea.

### Por qué la fila se apaga en la proyección y no con un `if` en el composable

`resolveDetectionStory` es «qué cuenta Home ahora mismo», con su precedencia probada. Arbitrar
dentro de la superficie es exactamente el error que [DET-ASK-STATE-001] volvió a corregir: la cadena
declararía un orden y la pantalla pintaría otro, y solo uno de los dos sería testeable. Es el mismo
mecanismo que ya usan `firstStepsOwns` y `carCardSaysTheWatch` — una fila que **se aparta porque
otra superficie está diciendo eso mismo**; aquí la otra superficie es el diálogo del sistema.

### Descubierto implementando · el silencio se levanta al VOLVER, no al leer la respuesta

`resolveFirstEntryAnswer` exigía el paréntesis completo (`awaitingFirstEntryAnswer` **y**
`firstEntryDialogShown`): si un OEM no pausa la pantalla al mostrar el diálogo, el paréntesis nunca
se cierra. Eso antes costaba la auto-apertura de la hoja —una cortesía—; con el silencio colgando
del mismo latch habría costado **el aviso entero durante toda la sesión**, que es la app enmudeciendo
sobre lo único sin lo que no funciona.

Así que se separan las dos consecuencias: volver a primer plano **siempre** apaga
`locationAnswerInFlight` (el diálogo ya no está, se contestara lo que se contestara), y el
`locationWarningCue` sigue exigiendo la prueba de que el diálogo llegó a tapar el mapa. Tiene su
testigo.

### Descubierto implementando · un default que calla vuelve ambiguo todo fixture viejo

Las variantes de galería y el preview que enseñan «sin permiso de ubicación» construían
`HomeState(hasCorePermissions = false, …)` y nada más — con el veredicto nuevo eso ya no es «denegó»,
es «aún no ha contestado», así que enseñaban el SILENCIO en vez del aviso que existen para enseñar.
Pasan a **declarar `locationEverAsked = true`**, igual que los tests. No es relleno: es el fixture
diciendo en qué momento de la conversación está, que es justo lo que este ticket introduce.

## Criterio de éxito

- Primera entrada sin permiso: detrás del diálogo se ve Home entero —mapa, tira de coches,
  carriles, checklist— y **callado**: ni píldora ámbar ni fila ámbar.
- «No permitir» → la píldora aparece, la fila ámbar aparece y la hoja se abre sobre ella
  (`locationWarningCue`, sin cambios).
- «Permitir» → no llega a existir aviso ninguno, y salta el pitch de detección de siempre.
- Segunda entrada con el permiso ya denegado: el aviso sale **de inmediato**, sin esperar a nada.
- Permiso concedido y **GPS apagado**: la píldora habla igual (el caso que el primer término
  protege).
- Detrás del diálogo, con coche aparcado o zona guardada, la cámara **sí** vuela — sin cambio.
- ✅ Tests: `LocationAvailabilityTest` (los cuatro casos de la tabla, incluido el testigo del primer
  término), `DetectionStoryTest` (la fila se aparta con la pregunta en el aire, vuelve al contestar,
  y **ninguna otra fila** se aparta), `HomeSlicesTest` (la píldora callada en las dos ventanas y
  hablando con los servicios apagados), `HomeViewModelTest` (el ciclo completo sobre el paréntesis de
  `SetMapForeground`, la segunda entrada ya denegada, y el OEM que no pausa).
- ✅ Galería mock: «Sheet · pregunta de ubicación EN EL AIRE (sin aviso)», al lado de las dos que
  enseñan el aviso ya contestado; `:app:assembleMockDebug` verde.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `HomeFabsSlice.locationMissing` → `HomeLocationPill` | ✅ gateado |
| `resolveDetectionStory` `BlockedCore` → fila ámbar | ✅ gateado vía parámetro |
| `HomeScreen` cámara (`locationUnavailable`) | ✅ exento a propósito — lee el hecho, no la voz |
| `locationWarningCue` (auto-apertura de la hoja) | ✅ sin cambio — ya es posterior a la respuesta |
| `BrowsePeek` | ✅ exento — perdió su badge en [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] |
| `FirstStepsCard` / `FirstStep` | ✅ exento — no hay paso de ubicación en el checklist |
| `HomeSheetContent` gates (`showPersonalBlocks`, carriles, tarjeta de avisar) | ✅ sin cambio — enseñan o deshabilitan contenido, no acusan de un permiso |
| `home_spots_unasked` (carril sin centro) | ✅ sin cambio — habla de no haber preguntado por un sitio, no del permiso |
| `awaitingFirstEntryAnswer` (var privada) | ✅ **borrada** — sube a `HomeState.locationAnswerInFlight` |
| `firstEntryDialogShown` (var privada) | ✅ sigue privada — nadie fuera del VM la lee, y ahora solo gobierna el cue |
| Galería: «sin permiso, con coche» / «sin coche» / «plazas sin preguntar» | ✅ declaran `locationEverAsked = true` |
| `HomeSheetPreviews` «sin permiso de ubicación» | ✅ lo mismo, en paridad con la galería |
| Tests que construían `HomeState(hasCorePermissions = false)` para ver el aviso | ✅ reescritos, no borrados: declaran la respuesta ya dada |
