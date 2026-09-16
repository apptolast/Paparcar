# SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001 · el coche que se va también es noticia, y el copiloto no es un segundo viaje

**Estado:** ✅ Done · rama `feature/SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001` · worktree `../Paparcar-leaves-news`

Las dos cosas que `SHARED-A-PARKING-TELLS-THE-GROUP-001` dejó fuera a propósito, hechas ahora que
el canal de push del grupo existe.

## Problema

**1 · La salida no se contaba.** El espejo escribe `isParked = false` cuando el coche se va, y el
fan-out lo tiraba con `reason: 'not-parked'`. Para el grupo eso son dos cosas perdidas: la noticia
(«el coche ya no está donde te dije») y, sobre todo, **la valla**. La geocerca compartida la tienen
plantada todos MENOS quien aparcó; cuando el coche se va, esa valla se queda sobre un sitio vacío
hasta la siguiente pasada del janitor. Y una valla sin coche debajo es un trigger de detección
esperando a que su dueño pase andando por esa calle.

**2 · El copiloto contaba el viaje otra vez.** Dos personas del grupo en el mismo coche llevan dos
móviles con Paparcar. Los dos detectan la misma parada, los dos escriben el espejo, y el grupo
acaba con **dos aparcamientos donde hubo uno**: el doble de kilómetros, el doble de tiempo buscando
plaza, el doble de plazas liberadas. Encima, el que no escribió recibe un aviso de un viaje en el
que iba sentado.

## Doctrina

- Nada cambia en detección: la valla compartida sigue siendo de **UBICACIÓN** — no arma, no nomina y
  no confirma. **No hay `detectionPath` nuevo**, porque ningún camino de confirmación nace aquí.
- **Fallo asimétrico, aplicado a la tarjeta**: ante la duda de si esto era mi propio viaje, se calla
  la NOTIFICACIÓN. Nunca la valla — una duda no puede dejar una geocerca donde ya no hay coche.
- La sesión personal sigue siendo la fuente de verdad. El dedupe **solo** silencia el espejo.

## Diseño

### El predicado, en un sitio

`domain/sharedcar/SharedCarSameParking.kt` — `isSameParking(mine, theirs)`. Predicado y no caso de
uso porque lo preguntan **dos** veredictos distintos (si espejar lo que acabo de aparcar, y si
enseñar una tarjeta que habla de mi propio viaje), que es exactamente el patrón de
`domain/detection/`: función pura de nivel superior, directamente testeable.

⛔ **Se comparan anclas, no confirmaciones.** Los dos móviles confirman con minutos de diferencia
—uno por Bluetooth en segundos, el otro por pasos y egress— pero los dos fijan el ancla al final de
la conducción, que es el mismo instante físico. Comparar cuándo lo supo cada móvil habría dejado al
carril lento sin dedupar nunca, que es justo el caso que más duele. Por eso la ventana es de **3
min sobre el ancla** y no los 60 s que decía el plan de F8: 60 s era la cifra correcta para el reloj
equivocado.

El radio (150 m) es el freno débil a propósito: el mismo coche no se puede aparcar dos veces de
verdad en tres minutos, así que el radio está para perdonar un fix malo, no para distinguir dos
aparcamientos.

### Quién lo pregunta

| Sitio | Qué decide | Qué pasa si acierta |
|---|---|---|
| `SharedCarMirrorWriter.onSessionSaved` | ¿el grupo ya tiene este frenazo? | no se espeja: un aparcamiento por frenazo, kilómetros contados una vez |
| `PaparcarMessagingService.onSharedCarParked` | ¿el aviso habla del viaje que acabo de hacer? | no se muestra la tarjeta — la valla se mueve igual |

Gana **quien llega primero**, y no se intenta adivinar quién conducía: ningún dato que tengamos
distingue al conductor del copiloto (el Coordinator mide el viaje igual desde los dos asientos), así
que inventar un criterio sería inventar un dato. La atribución del autor puede salir cambiada; las
cifras, no.

### La salida

`departureVerdict(before, after)` en `sharedCarFanout.ts`, con el tipo de push `shared_car_left`.

⛔ **La salida es una TRANSICIÓN, no un estado**: `isParked: false` es el valor en reposo de un coche
que nadie ha movido desde ayer, así que cualquier escritura posterior del documento —un cambio de
nombre, una sincronización que aterriza tarde— volvería a anunciar una salida de anteayer.

⛔ **El autor es quien lo había aparcado**, y eso no es un atajo: el espejo solo lo pone en «se ha
ido» el móvil cuya propia sesión está mostrando el estado. Que otro se lleve el coche no escribe
este documento — escribe un aparcamiento nuevo cuando para, que es el otro veredicto.

La tarjeta **no abre el mapa en el punto**, a diferencia de la de aparcar: la noticia es justo que
ese punto ya no vale. Id de notificación propio (`2013`), para que enterarse de que el coche se va
no borre de la barra la tarjeta que dice dónde está.

## Criterio de éxito

- ✅ 25 tests verdes en `functions/` (2 nuevos de salida) + `SharedCarSameParkingTest` (7 casos,
  incluidos los dos bordes: el carril lento que SÍ se junta y los 330 m que NO).
- ✅ `testAll` verde, guardarraíles Konsist forzados con `--rerun-tasks`.
- ⏳ **Campo, dos cuentas**: (a) A aparca y luego se lleva el coche → B recibe las dos tarjetas y se
  queda **sin** valla compartida; (b) A y B van juntos en el coche → el grupo registra **un** solo
  aparcamiento y B no ve tarjeta.

## Consumidores auditados

`grep -rn "isParked" --include=*.kt shared/src` + `functions/src`:

| Sitio | Estado |
|---|---|
| `EvaluateSharedCarWatchUseCase` | **cerrado sin tocar** — ya trataba `!isParked` como «nada que vigilar»; lo que cambia es que ahora se entera a tiempo |
| `SharedCarMirrorWriter.onSessionClosed` | **cerrado** — sigue siendo el único sitio que pone el espejo en «se ha ido», y sigue comprobando que el estado es SU sesión |
| `sharedCarStateOf` | **cubierto** — deriva `isParked` de `isActive` de la sesión; un guardado offline reproducido llega cerrado y no anuncia salida (lo prueba el test `another-parking`) |
| `SharedCarFenceReconciler` | **cubierto por convergencia** — lo llama el janitor, y el push solo adelanta esa llamada |
| `SharedCarMapper` → sección de Vehículos | **cerrado sin tocar** — ya pinta «no aparcado» desde el mismo campo; el push solo hace que ese campo llegue antes |
| `RemoteSharedCarDataSourceImpl` (deserializador manual) | **cerrado** — `isParked` ya estaba en la paridad DTO⇄manual; el campo nuevo de este ticket (`parkedAtMs`) también estaba |
| `verdictFor` (zona) | **exento** — otra colección, otro fan-out |

## Lo que sigue fuera

- **Anunciar que OTRO se lleva el coche.** Hoy el espejo solo registra la salida cuando se lo lleva
  quien lo aparcó. Que Antonio coja el coche que dejó Marta no escribe nada hasta que Antonio para:
  hacerlo visible exige una señal nueva en detección (conducción medida de un coche del grupo), y
  eso es la etapa 2 con su gate de campo.
- **Tope de miembros en servidor** y **degradar geocerca/Coordinator a sugerencia** en coches
  compartidos: etapa 2 de `VEH-SHARED-001`.
- **Nota de implementación**: cuando el espejo se calla por eco, las llamadas posteriores de esa
  sesión (`onSessionClosed`, `onAddressResolved`) apuntan a un documento que no existe y fallan
  dentro de su `guard`, con un warn. Es ruido conocido y no cuesta nada: el estado del grupo lo
  sostiene el móvil que sí espejó.
