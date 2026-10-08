# HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001 · La noticia de un coche va en SU card aunque haya varios

**Estado:** ✅ Done · mergeado a master (squash) el **2026-10-08** · GitHub issue
[#138](https://github.com/apptolast/Paparcar/issues/138) · nace de
[#137](https://github.com/apptolast/Paparcar/issues/137), que queda **aparcado** por tocar la misma
pantalla
> ⛔ **Mergeado SIN el klib de iOS**: `testAll` no se pudo pasar entero porque Windows bloquea una DLL
> que Kotlin/Native extrae (`…\.konan\…\orgjetbrainskotlinbackendkonanenvstubs.dll`, Control de
> aplicaciones). `:shared` y `:app` en verde; el guard que vigilaría `IosAppPreferences` quedó tapado.
> Decisión del user, 08-10.
> ⏳ Sin ver en mano: la variante de **1 coche** de la galería.

## Problema

Con **dos o más coches** y una pregunta abierta, Home pinta dos cosas para un mismo coche: la card
«¿Has aparcado tu Toyota Corolla?» **encima**, y debajo el carril `YOUR VEHICLES` con los demás. La
noticia vive fuera de su coche.

Reportado por el user el 08-10 mirando la pantalla, y capturado en el Dev Catalog, preset *«¿Has
aparcado? — pregunta abierta durante un viaje»* → `~/Pictures/paparcar-138/01_ANTES_segunda-card.png`.

## Doctrina

La regla ya existe y es del user [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] (#129, `45bc3ad95`,
22-09): **la card de un coche gestiona TODOS sus estados**; el badge suelto se queda sólo para
`NoVehicle`. Lo que este ticket retira no es la regla, es **su excepción**.

Y la frontera que NO se toca [23-09]: `isTheCarsQuestion()` separa las **preguntas de un coche**
(card) de la **salud del sistema** —permisos, detección apagada, vigilancia frágil o parada— que se
queda fuera porque repercute a todos los coches y porque dentro de la card sustituye la información
del coche por un botón que empuja a pulsar.

## Señales / datos disponibles — lo medido antes de diseñar

### Hallazgo 1 · El hoist YA existe, y no cubre este caso

`vehiclesRowOrder` (`HomeState.kt`) ya eleva al protagonista:

```kotlin
compareByDescending<VehicleCard> { it.isDriving(drivingVehicleId) }
    .thenBy(nullsLast(parkedPreference.reversed())) { it.session }
    .thenBy { it.vehicle.monitoringStatus().sortRank() }
```

Pero en la captura **el Corolla no sale primero**: los dos primeros del carril son *Second car* y
*Work van*. El predicado es `isDriving`, y **una pregunta abierta no es un viaje confirmado**.

### Hallazgo 2 · La hoja construye el estado y luego lo tira

`HomeSheetContent` construye `cardStatus` y lo pasa a `vehiclesSection`, que lo usa **sólo** en la
rama de un coche:

```kotlin
if (sorted.size == 1) { HomeVehicleCard(..., status = cardStatus, ...) }
else { LazyRow { items(sorted) { HomeVehicleChip(...) } } }   // cardStatus no se usa
```

### Hallazgo 3 · ⛔ El que explica por qué #129 se paró aquí: **la pregunta no sabe de qué coche es**

`DetectionStory` contesta QUÉ, y **ninguna de sus tres preguntas de coche nombra al vehículo**:
`AwaitingAnswer(window)`, `PendingAsk`, `AwaitingFirstPark`. Su KDoc lo dice y tiene razón: *«contesta
QUÉ es la historia, no QUIÉN la pinta»*.

Pero el sujeto tampoco está debajo. `PendingPromptWindow` lleva **`vehicleName`, no `vehicleId`** — un
nombre, puesto ahí *«carried verbatim so the in-app row and the tray can never word the same question
differently»*. Con UN coche el sujeto era trivial y nadie lo echó de menos; con varios **no hay a
quién elevar**, y casar por nombre sería frágil (dos coches pueden llamarse igual, y el nombre se
edita).

Esto es lo que convierte el ticket en un sistema y no en un `if`: **la pregunta tiene que decir de
qué coche habla**, por id, en el mismo sitio donde ya dice su nombre, su calle y su punto.

## Diseño

**El sujeto viaja con la pregunta; el carril eleva al sujeto; el líder, si trae noticia, es una card.**

### Pieza 1 · La pregunta nombra a su coche por ID

`PendingPromptWindow` gana `vehicleId: String?` — cuarto campo de la MISMA familia que `vehicleName`,
`street` y `candidate`, y por la misma razón anti-deriva. Cadena completa, sin saltarse ningún salto
(regla de paridad DTO):

- `AppNotificationManager.showParkingConfirmation(...)` pasa a llevar el id junto al nombre.
- sus llamantes en detección lo pasan desde la sesión.
- `AndroidDataStoreAppPreferences` (clave + read + write) e `IosAppPreferences`.
- fakes de `:shared` y de `:mockkit`.

Compatibilidad hacia atrás: una ventana escrita por un build anterior lee `vehicleId = null`, que es
exactamente lo que es — la misma historia que ya tiene escrita el campo `candidate`.

### Pieza 2 · Un solo sitio que contesta «¿de quién es esta noticia?»

Función pura, testeable, al lado de `isTheCarsQuestion()`: dada la historia y la slice, devuelve el
`vehicleId` sujeto, o null.

- `AwaitingAnswer` → `window.vehicleId`
- `PendingAsk` → `slice.parkNudgeVehicleId`
- `AwaitingFirstPark` → el coche activo
- el resto → null (son salud del sistema: no tienen sujeto, y por eso van al badge)

### Pieza 3 · El carril eleva al sujeto, no al que conduce

`vehiclesRowOrder` recibe el sujeto y lo antepone. Un coche en ruta sigue siendo sujeto (su historia
lo es), así que el orden de hoy no se rompe: se generaliza.

### Pieza 4 · El líder con noticia es una card; los demás, chips

`vehiclesSection` deja de ser «1 → card / 2+ → chips» y pasa a «el líder con noticia → card a ancho
completo + el resto en la tira». `cardHostsTheStory` pierde el `vehicleCards.size == 1`.

⛔ Y el carril se lleva a su inicio cuando aparece la noticia: *«un aviso que hay que descubrir
arrastrando es peor que el badge que sustituiría»* sigue siendo verdad, y con la card fuera de la
tira el riesgo se traslada al scroll de los chips.

## Criterio de éxito

1. Con 3 coches y pregunta abierta, **no hay segunda card**: la pregunta sale dentro de la card del
   coche al que señala, y ese coche es el primero.
2. ⛔ **CORREGIDO: con 1 coche TAMBIÉN cambia, y hay que decirlo.** El criterio original decía
   «nada cambia», y es falso desde la Pieza 5: la fila de identidad se suprime cuando la card tiene
   noticia, **haya uno o cinco coches**. Es consistente con el argumento del user —*«¿para qué poner
   el nombre si ya tenemos la pregunta?»*— y no depende del número de coches, pero no es lo que este
   doc prometió, así que queda escrito en vez de corregido a posteriori.
   ⏳ **Sin ver en mano**: la variante de 1 coche de la galería no se llegó a abrir (la lista es larga
   y navegarla a ciegas por adb costaba más de lo que aportaba). El comportamiento es determinista
   —`status != null` ⇒ sin fila de identidad— y la maqueta resultante es la MISMA que ya se verificó
   con 3 coches, pero verlo sigue pendiente.
3. La salud del sistema (permisos, detección apagada, vigilancia frágil) **sigue en el badge** con
   cualquier número de coches.
4. Galería de estados al día: la variante de 2+ coches ya existía y **decía lo contrario** («la fila
   pregunta»); pasa a «la CARD del coche pregunta» y nace su contraste — una ventana SIN sujeto, que
   sigue cayendo al badge.
5. Tests de la función pura del sujeto y del orden del carril.
6. `./gradlew testAll` en verde · capturas antes/después con 1 y con 3 coches.

## Consumidores auditados

| consumidor | estado |
|---|---|
| `PendingPromptWindow` | ✅ gana `vehicleId`; null = «sin sujeto», igual que `candidate` en builds viejos |
| `AppNotificationManager.showParkingConfirmation` | ✅ lleva el id junto al nombre |
| `NotifyParkingConfirmationUseCase` | ✅ nombre e id salen de **la misma lectura** del coche activo — leerlo dos veces podía dar nombre de uno e id de otro |
| `DetectionEffectExecutor` (2 sitios que publican la pregunta) | ✅ `activeVehicleName()` tiraba el objeto; ahora hay `activeVehicle()` y los dos sitios leen una vez |
| `AndroidDataStoreAppPreferences` | ✅ clave + read + write |
| `IosAppPreferences` | ✅ clave + read + write + clear |
| `IosAppNotificationManagerImpl` | ⚪ acepta el id y **no lo usa**: en iOS la pregunta vive sólo en la bandeja, no escribe ventana. Hueco declarado en el KDoc |
| `FakeAppNotificationManager` (×2: `:shared` y `:mockkit`) | ✅ |
| `NotifyParkingConfirmationUseCaseTest` (4 overrides) | ✅ |
| `vehiclesRowOrder` | ✅ el sujeto es la primera clave; `isDriving` baja a segunda y nada deja de flotar |
| `vehiclesSection` | ✅ la tira de chips se extrae a UNA función que usan las dos maquetas |
| Mock del Dev Catalog | ✅ `MOCK_PROMPT_VEHICLE_ID`, el mismo coche que ya nombraba |

⛔ **Y una corrección a lo que esta issue afirmaba**: dije que el preset de «2+ coches con pregunta
abierta» **no existía**. Existe — es *«¿Has aparcado? — pregunta abierta durante un viaje»*, y tiene
tres coches. Lo que le faltaba no era el preset, era el `vehicleId` en la ventana del fake.

## Pieza 5 · La noticia OCUPA la fila del nombre — decidido en mano por el user (08-10)

La primera versión funcionaba —no había segunda card— pero el user la vio y dijo dos cosas, y las
dos se midieron:

> *«pff se ve gigante»* · *«¿para qué poner Second car si ya tenemos la pregunta? la pregunta y la
> info deberían ir ahí, donde pone Second car»*

| | |
|---|---|
| Card con identidad + divisor + pregunta | **246 dp** = 39 % de la hoja visible |
| Card con la noticia EN la fila del nombre | **178 dp** (−28 %) |

Y el motivo de fondo no era el tamaño: era que **el coche salía nombrado dos veces y de dos
maneras** — «Second car» (su `name`) arriba y «your Toyota Corolla» (`displayName()`) dentro de la
pregunta. En superficies separadas se leían como dos cosas; pegadas, como una contradicción.

Así que con noticia, la card **no dibuja su fila de identidad**: la noticia la ocupa. Es el mismo
argumento con el que este fichero ya había retirado el raíl de color de la card — *«en la card no
informaba de nada nuevo»*.

⛔ **Pero la identidad no se tira: se queda en el GLIFO.** Quitar la fila entera dejó la card sin
nada que dijera de qué coche habla salvo el nombre repetido dentro de la pregunta, y con los chips
de al lado sí llevando glifo. La baldosa se extrae de `VehicleIdentityHeader` a `VehicleGlyphTile` y
entra por el slot `leading` de la propia fila de la noticia — que `embedded` dejaba a null
precisamente porque la card ponía el glifo encima.

⛔ Y entra por el `leading`, **no envuelto en un `Row` por fuera**: lo probé así primero y la baldosa
quedaba centrada contra el bloque entero —a la altura de los botones— mientras el título corría por
encima a todo el ancho. Dos columnas que no se conocen. El esqueleto canónico ya resuelve
tile · título · sublínea. [UI-LIST-ITEM-001]

## 🔵 Lo que queda medido y SIN explicar

El hueco entre las dos cápsulas mide **20,2 dp** y la constante que lo pone (`CTA_ROW_GAP_DP`) vale
**12**. Hay ~8 dp que no salen de ninguna constante del camino. No es de este ticket —ya estaba en
master antes— y es el primer hilo de [#137](https://github.com/apptolast/Paparcar/issues/137).

## 🔴 Lo que destapó la captura, y es decisión del user

Con la pregunta ya DENTRO de la card, el mismo coche se nombra **dos veces y de dos maneras**:
la card titula **«Second car»** (su `name`) y la pregunta dice **«your Toyota Corolla»**
(`displayName()` = marca + modelo). Antes vivían en superficies distintas y se leía como dos cosas;
pegados, se lee como una contradicción.

No lo arreglo por mi cuenta porque el arreglo obvio choca con un invariante que existe a propósito:
`vehicleName` viaja en la ventana *«carried verbatim so the in-app row and the tray can never word
the same question differently»*. En la bandeja **no hay card**, así que ahí la pregunta SÍ tiene que
nombrar al coche. Dentro de la card ya está nombrado.

Tres salidas, y la elige el user:

✅ **RESUELTO por la Pieza 5, y por una cuarta vía que no estaba en la lista**: no se toca el copy
ni se unifica el nombre — **desaparece la fila que lo repetía**. La pregunta sigue diciéndose igual
en la bandeja y en Home (la paridad de `vehicleName` queda intacta) y el coche se nombra una sola
vez, dentro de ella.

## Lado iOS

`IosAppPreferences` implementa `AppPreferences`, así que la Pieza 1 **entra en iOS en esta misma
tarea**. Las piezas 2-4 son `commonMain`, así que viajan solas.

## Capturas

`~/Pictures/paparcar-138/` — el recorrido entero, incluida la versión **descartada**:

| fichero | qué enseña |
|---|---|
| `01_ANTES_segunda-card.png` | el defecto: segunda card arriba y el MISMO coche como chip abajo |
| `02_v1_noticia-en-la-card.png` | primera versión: ya no hay segunda card, pero la card mide **246 dp** y nombra el coche dos veces |
| `03_v2_sin-fila-de-nombre.png` | sin la fila de identidad: **158 dp**, pero la card se queda sin glifo |
| `04_descartada_glifo-mal-alineado.png` | ⛔ **descartada**: el glifo metido en un `Row` por fuera queda centrado contra el bloque, a la altura de los botones |
| `05_FINAL_glifo-pregunta-info.png` | el resultado: glifo · pregunta · info, **178 dp** |
| `cmp_FINAL.png` | antes/después lado a lado |
