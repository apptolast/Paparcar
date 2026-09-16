# SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001 · el grupo nace sin código, nadie con coche puede entrar, y lo que se ve está dicho dos veces

**Estado:** ✅ Done · rama `feature/SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001-invite` · worktree `../Paparcar-invite`

## Problema

Tres cosas que salieron al mirar la feature en device el 16-09-2026, después de arreglar el recorte
y la unión del historial. Las dos primeras son bugs de flujo: **hoy no se puede formar un grupo.**

### 1 · El grupo nace sin código

`SharedCarRepositoryImpl.shareVehicle()` crea el `SharedCar` y nada más. El código solo existe
cuando alguien pulsa «Código nuevo» (`createInvite`, línea 160). Así que compartes —cuyo único
propósito es invitar— y la card dice «Sin código». El user: *«no veo el código, no aparece»*.

### 2 · Quien ya tiene coche no puede unirse a ninguno

La puerta para teclear el código existe **solo** en el garaje vacío (`VehiclesScreen.kt:229`, dentro
de `EmptyVehicleState`). El caso normal —ya tengo mi coche, mi pareja me invita al suyo— no tiene
entrada por ninguna parte de la app. El KDoc de `EmptyVehicleState` explica por qué está ahí («una
persona invitada aterriza en esta pantalla»), y esa razón es buena; lo que falta es la otra puerta.

### 3 · «Dónde está el coche» se dice dos veces, y se contradice

La hero card lee `vehicleWithStats.lastSession` —la sesión **local**— y la card «Dónde está el
coche» lee el **espejo**. Si lo aparcó Marta, tu móvil no tiene esa sesión local: arriba lees tu
aparcamiento viejo y abajo el de Marta. Dos respuestas distintas a la misma pregunta, a dos dedos.

`SharedCarSection` ya intenta evitar el duplicado (solo pinta la card si `parked.authorUid !=
currentUid`), pero eso resuelve la repetición y **no** la contradicción: justo cuando lo aparcó otro
—el único caso en que la card aparece— es cuando las dos superficies discrepan.

Y para un **miembro** recién llegado la hero card no tiene nada que decir: no tiene sesiones propias
de ese coche.

### 4 · El diálogo de consentimiento miente sobre las rutas

`shared_car_not_shared_body`, el cuerpo del diálogo que se lee **antes** de abrir el grupo, promete:

> «Las rutas personales no se comparten nunca.»

Es falso. `shareRoutesInGroup` nace **encendido** (decisión del 15-09: apagado por defecto haría que
la función pareciera rota el día que se estrena), el espejo recibe la geometría y el grupo la dibuja.
El propio interruptor de la sección lo dice a dos pantallas de distancia: *«El grupo ve el recorrido
de tus viajes»*.

Que la frase falsa esté precisamente en la pantalla donde se da el consentimiento es lo que la hace
grave: es el único momento en que el usuario decide con esa información delante.

## Doctrina violada

- **Una pregunta, una superficie.** «Dónde está el coche» tiene hoy dos respuestas posibles y
  ninguna autoridad declarada.
- **Regla editorial de navegación** (`CLAUDE.md`): lo mío-permanente vive en Vehículos. La puerta
  para unirse a un coche es exactamente eso, y no está.
- Nada de esto rompe una regla escrita de estilo; el resto es diseño acordado con el user (16-09).

## Diseño

### A · El grupo nace utilizable

`shareVehicle()` genera el primer invite en el mismo acto. El botón «Código nuevo» sigue existiendo
para regenerarlo, matando el anterior — la política de un solo código vivo por coche no cambia.

Y al confirmar, **la hoja se abre sola** (user, 16-09). Compartir existe para invitar: si la acción
termina dejándote en la página con el código guardado tras una fila que hay que descubrir, la mitad
útil del gesto queda sin entregar. Se abre solo al COMPARTIR, no al unirse — quien entra con un
código no necesita otro. Va en el camino de ÉXITO del `Result`: si compartir falla, se lee el error,
no una hoja vacía encima.

### A.bis · La sección se transforma, y la hoja llega después

El bloque de la sección se sustituye por otro de **altura distinta**, y sin transición eso es un
salto que empuja todo lo de debajo. `AnimatedContent` con `SizeTransform(clip = false)` sobre los
tokens de `PapMotion` — fundido, no deslizamiento: las dos caras son la misma cosa en dos estados,
y un slide diría que llegas a otro sitio.

⛔ **La hoja espera a que esa transición acabe** (user, 16-09). Abriéndola en el mismo frame, la
página cambiaba a compartida DETRÁS de ella: el usuario pedía algo y no llegaba a verlo ocurrir. El
retardo es exactamente `PapMotion.Medium`, escrito como constante en el ViewModel en vez de
importado, porque `PapMotion` arrastra el runtime de animación de Compose y un ViewModel no tiene
por qué depender de él.

### B · La puerta de unirse va en el «+»

El «+» de la fila de pills (`PaparcarAddChip`, `VehiclesScreen.kt:492`) hoy navega directo a
registrar. Pasa a abrir un `PapAlertDialog` de dos acciones: **«Añadir un coche mío»** (primaria) y
**«Unirme con un código»** (secundaria outlined).

⛔ No una hoja: para dos opciones una hoja es desproporcionada, y el diálogo es el molde que la app
ya usa. ⛔ No en Ajustes: unirse a un coche no es configurar. El «+» es adonde uno ya va cuando
quiere «otro coche en la app», así que no se inventa superficie nueva.

`EmptyVehicleState` conserva su enlace: quien aterriza con el garaje vacío no debería tener que
encontrar un «+».

### C · Una sola superficie dice dónde está el coche

Se **borra** la card «Dónde está el coche». Su único dato que la hero card no tenía —quién lo
dejó— pasa a la propia hero card como «· lo aparcó Marta», junto al resto del estado.

Con eso la hero card pasa a leer **el aparcamiento del coche**, no el tuyo: si el espejo tiene una
sesión más reciente que la local, manda el espejo. Eso arregla la contradicción **y** resuelve de
paso al miembro recién llegado, que deja de ver una card muda.

### D · La página se queda con lo que se mira; la modal con lo que se gestiona

- **Fuera el encabezado «COCHE COMPARTIDO»** (`PapSectionHeader`). Con el resto mudado, encabezaría
  una sola fila; y encima de la hero card competiría con «Mis vehículos» y las pills — tres niveles
  de cabecera antes del contenido. Que el coche es compartido ya lo dicen el glifo de grupo junto al
  nombre y la propia fila de miembros.
- **A la modal**: la card del código (solo el dueño la ve, como hasta ahora) y el interruptor
  «Mostrar mis rutas en este coche».
- La card del código se **compacta** al formato del artifact: título en `rowTitle`, código y «Código
  nuevo» en la MISMA línea, cuerpo en `caption`. Hoy respira de más para lo que dice.
- **Se quedan en la página**: la fila «En este coche · Marta, Tú» (con su chevron a la modal) y los
  chips «Todo el grupo / Solo yo», porque gobiernan las cifras y el historial de debajo — sacarlos
  los alejaría de lo que mandan.
- ⛔ «Dónde está el coche» **no** se muda a la modal: si ya está en la hero card, meterlo ahí sería
  la misma frase por tercera vez.

### F · El consentimiento — resuelto en master, no aquí

⚠️ **Este punto se descarta: llegó tarde.** Mientras esta rama estaba abierta, master arregló el
mismo copy en `0fdf4514` [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001] y **mejor**, porque no se
quedó en el texto: metió la PREGUNTA de rutas en el diálogo de compartir y en el de unirse, naciendo
en NO. Así que ya no hace falta advertir de un default incómodo — no hay default.

Mi versión decía «…y también el recorrido de cada viaje. Puedes dejar de compartir los tuyos cuando
quieras», que describía un opt-out **que dejó de ser verdad** con ese commit. En el rebase se
conserva la frase de master en los 9 locales.

⛔ Lección del rebase: `git checkout --ours` sobre un `strings.xml` se lleva el fichero **entero**, y
con él mis 5 claves nuevas y mis 3 borrados — silenciosamente. Hubo que reaplicarlos clave a clave y
verificar los nueve ficheros uno por uno antes de dar el conflicto por resuelto.

### E · La acción que acaba con algo, separada

El KDoc de `SharedCarMembersSheet` justifica que salir/disolver vivan ahí *«lejos del cuerpo de la
página, porque una acción irreversible no se pone al lado de un interruptor»*. Al mudar el
interruptor de rutas DENTRO, esa frase deja de ser verdad. Así que la acción destructiva se separa
con un divisor al fondo de la hoja y estrena **confirmación**, que hoy no tiene: «dejar de
compartir» disuelve el grupo de cuatro personas sin preguntar.

## Criterio de éxito

- Compartir un coche deja un código visible en el mismo acto, sin pulsar nada más.
- Con coches en el garaje, el «+» ofrece unirse con un código, y el código funciona.
- «Dónde está el coche» aparece en UN sitio, y dice quién lo dejó cuando no fuiste tú.
- Un miembro recién llegado ve en la hero card el último aparcamiento del coche, si lo hay.
- La página del coche compartido: hero card, fila de miembros, chips. Nada más.
- La modal: miembros, código (solo dueño), interruptor de rutas, y abajo separada la salida con
  confirmación.
- Strings nuevos en los 9 locales · galería mock al día · `testAll` verde.

## G · El hueco del código no puede cambiar de tamaño

Al pedir un código nuevo la card se estiraba y daba un salto feo (user, 16-09). **La causa no era la
falta de un loading**: `shared_car_code_none` («Sin código») se pintaba con `statNumber`, la voz
**CIFRA**, que por doctrina es para *una cifra que protagoniza su bloque*
[UI-TYPE-TWO-VOICES-ONE-ROW-001]. Durante la regeneración el código desaparece un instante, entra
ese texto a 25 sp, no cabe junto a «Código nuevo», parte en dos líneas y estira la card. Un shimmer
encima solo lo habría tapado.

Tres cosas, en este orden de importancia:

1. El hueco **reserva su altura** (`CODE_SLOT_HEIGHT_DP`), así que sus tres caras —código, shimmer,
   ausencia— miden lo mismo y la card no puede cambiar de tamaño.
2. «Sin código» baja a `body`/`onSurfaceVariant`: es una ausencia, no una cifra.
3. Shimmer con `PapShimmerBox` + `PapShimmerBlockScale`, los del esqueleto del historial.

⛔ **El shimmer no podía colgar de `sharedWorking`.** Ese booleano lo comparten todas las acciones del
grupo, así que el código se habría puesto a brillar al tocar el interruptor de rutas — exactamente el
fallo que el KDoc de `PapDialogAction` describe («el booleano es el bug: decía *algo está corriendo*,
así que el diálogo pintaba el spinner en el botón equivocado»). El estado pasa a guardar QUÉ acción
está en vuelo (`SharedCarBusy`), y `sharedWorking` se deriva de ella — ningún call site se rompe.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `SharedCarRepositoryImpl.createInvite()` | refactorizado a `issueInvite()`, ahora compartido con `shareVehicle()`. Un solo sitio emite código. |
| `SharedCarRepositoryImpl.shareVehicle()` | **emite el primer código**. El `SharedCar` devuelto y la fila de Room llevan ya `activeInviteCode`. |
| `VehiclesScreen` · `PaparcarAddChip` (fila de pills) | pasa de `AddVehicle` a `ShowAddChoice(true)`. |
| `EmptyVehicleState` · «Unirme a un coche compartido» | **exento** — sigue directo al diálogo del código. Quien aterriza con el garaje vacío no debe tener que encontrar un «+». |
| `VehiclesViewModel.AddVehicle` | cierra `showAddChoice` antes de navegar o de abrir el paywall, nunca detrás. |
| `VehiclesViewModel.ShowJoinDialog` | cierra `showAddChoice` al abrir: ⛔ nunca modal sobre modal. |
| `VehiclesViewModel.ConfirmGroupExit` | cierra la hoja al abrir el diálogo, por lo mismo. |
| `VehicleStateLine` / `VehicleHeroCard` | leen `carWhereabouts(...)` en vez de `vehicleWithStats.lastSession`. Es la única autoridad sobre dónde está el coche. |
| `SharedCarSection` | pierde `vehicle`, `onCreateInvite` y `onSetShareRoutes`: ya no pinta nada de eso. |
| `SharedCarMembersSheet` | gana código, rutas, aviso de emparejamiento y la salida separada con confirmación. |
| `shared_car_where_title` / `_empty` / `_left` | **borrados de los 9 locales** — se quedaron sin lector al borrar la card, y `LocaleParityGuardrailTest` los cazó por nombre. |
| `shared_car_where_parked_by` | se conserva: lo reusa la hero card para el «lo aparcó Marta». |
| `sharedAction(...)` (6 call sites) | cada uno nombra su acción (`Invite`, `Routes`, `Membership`, `Sharing`). |
| `StateGalleryScreen` · grupo «Coche compartido» | reescrito: 3 variantes de sección + 4 de hoja (incluidas «sin código» y el shimmer) + 2 del diálogo de unirse. |
| `RunMonthRecapUseCase`, panel de cifras | **exentos** — no tocan ninguna de estas superficies. |

## Verificación

`./gradlew testAll` verde · ambos flavors compilan · **19 tests** en `SharedCarRepositoryImplTest`
(2 nuevos: el código nace, y **sirve para entrar**) · **8** en `SharedCarWhereaboutsTest`.

Instalado en el **Oppo** (`prodRelease` firmado, hash device↔local verificado en cada vuelta). ⚠️ El
`adb install` normal se caía a mitad de los 12 MB toda la tarde mientras los comandos cortos pasaban;
entró por `adb push` + `pm install` desde el propio aparato.

## iOS

Nada que abrir: todo vive en `commonMain` (repositorio, ViewModel, Compose Multiplatform). iOS lo
hereda sin código propio.
