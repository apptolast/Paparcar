# HOME-THE-CARD-ANSWERS-THE-BADGE-WARNS-001

**Estado:** ✅ Done · master `c3d873b64`

> La card de un coche **contesta**; el badge **avisa**. Todo lo de abajo sale de esa frontera, y
> todo lo de abajo se vio MIRANDO la galería de estados en el emulador (23-09-2026), no leyendo.

Continúa [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] (`45bc3ad95`), que mudó a la card *todos* los
estados de un coche. Enseñados los casos uno a uno, el user corta por otro sitio y mejor:

- **Card** → las tres PREGUNTAS de ese coche: ¿has aparcado? · ¿dónde lo dejaste? · ¿dónde está tu
  coche? Se contestan en un toque y la card es la superficie que lo nombra.
- **Badge** → la SALUD DEL SISTEMA: permiso de ubicación, detección apagada, vigilancia frágil o
  parada. *«Son avisos que repercuten a más vehículos … y dejar ese badge ahí obliga al usuario a
  pulsarlo cuando a lo mejor no quiere.»* Un permiso, un ajuste y **un** servicio en primer plano
  sirven a todos los coches; metidos en la card tapan la información de UNO con un botón a ancho
  completo.

`isVehicleNews()` pasa a llamarse **`isTheCarsQuestion()`**: el nombre viejo decía «de quién habla»
y la frontera es «qué se le pide al usuario».

---

## Lo que se arregló, en orden de gravedad

### 1. «Voy conduciendo» no hacía nada — y la detección SÍ se armaba

Reporte de campo (Fernando). Leído en el código, el arme era correcto: `MANUAL` está exento del gate
de estrategia y del periodo de silencio, y además lo limpia; y una sesión sin conducción medida se
cae sola (`aborted_no_movement`). Lo que no pasaba era nada **en pantalla**:

`drivingMeta` salía del **puck**, el puck nace del stream de ubicación, y hasta el primer fix la card
volvía a decir «Sin marcar» — lo mismo que decía ANTES de pulsar, sólo que ya sin los botones. En un
móvil que tarda en dar el primer fix (interior, GPS frío, ubicación aproximada de iOS) eso son
decenas de segundos, o nunca.

Nace `resolveDrivingMeta(puck, readiness, vehicles)`: **el viaje lo declara la DETECCIÓN**
(`Monitoring` existe en el mismo turno del arme), el puck sólo añade DÓNDE. El puck manda cuando
existe —trae el coche que de verdad salió y la fase congelada del `Candidate`—, y sin él se nombra al
coche que el carril vigila. Se llama desde las tres escrituras que pueden cambiar la respuesta (puck,
disponibilidad, flota), porque el dato es función de las tres. `monitoredVehicle` sube a
`model/DrivingMetaSource.kt`: desde hoy lo preguntan dos sitios, y dos copias de esa elección es como
el mapa dibuja un coche mientras la hoja nombra otro.

### 2. «Añade tu coche» encima de dos coches

`HomeState.detectionReadiness` nace en `Disabled(NO_VEHICLE)`, así que bastaba con no pasarla para
que la proyección contradijera a la tira que ella misma recibe — media galería lo hacía. En
producción ambas salen de la misma lista, pero **la función tenía la lista delante y decía lo
contrario igual**: con cards, `NoVehicle` ya no se dice.

### 3. El arranque en frío no existía en ninguna superficie

`AwaitingFirstPark` se suprimía por `carCardSaysTheWatch` «porque la card ya lo dice», y la card sólo
decía la FRASE, sin los dos botones. O sea que **«Marcar mi aparcamiento» y «Voy conduciendo» no se
pintaban ni aquí ni ahí**. Quitada la supresión; la card recibe la historia entera con sus CTAs.

### 4. Un raíl vertical, dos significados [UI-AN-ACCENT-RAIL-MEANS-ONE-THING-001]

La franja de 4 dp decía el MÉTODO de vigilancia en la card y la GRAVEDAD en el badge — y desde el
ticket anterior se ven **a la vez**, una encima de otra, con colores distintos que no quieren decir
lo mismo. El user lo leyó como gravedad y preguntó por qué su coche en ruta no llevaba el tono del
viaje: exactamente el malentendido que produce un símbolo con dos sentidos.

**Se retira de la card**, y no se tiñe por estado, por tres razones en este orden:

1. Teñir el raíl por estado rompe dos reglas escritas: el estado se ESCRIBE y se anima, nunca se
   tiñe [UI-COLOR-DOCTRINE-001], y tu propio viaje lleva la identidad de vigilancia de SU coche
   porque el azul de movimiento es el de los viajes AJENOS [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001].
   (La cápsula «No voy en mi coche» ya iba en la identidad del coche.)
2. En la card no informaba: identidad ya la llevan el glifo, el icono junto al nombre y —conduciendo—
   el marco entero. Era la cuarta copia del mismo dato.
3. En el badge es el ÚNICO portador de su significado.

Resultado: **un raíl vertical en Home quiere decir siempre «esto es un aviso, y así de serio es»**.

### 5. La × , sólo donde hay algo que callar [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]

Nace `DetectionNotice` (5 avisos) + `notice()` + `silencedBy()`. La frontera **no es nueva**: es la
misma de `isTheCarsQuestion()`, y un testigo la ata — *o lo hospeda la card (y se contesta), o es un
aviso (y se calla)*. Como `notice()` devuelve null para las tres preguntas, el llamante no tiene qué
pasarle a la ×: la imposibilidad es estructural, no un comentario.

⛔ **El silencio NO se persiste.** Un permiso que falta deja la app rota; guardarlo en disco
convertiría un «ahora no» en un «nunca más» y Paparcar se quedaría callada sobre su propia avería.
Al volver a abrir, el aviso vuelve — porque el problema sigue ahí.

### 6. Dos defectos de dibujo, vistos en el device

- **20 dp de hueco** entre la fila de respuestas y «No voy en mi coche», contra los 12 que separan el
  texto de sus botones: la fila empotrada seguía poniendo el padding inferior de cuando era tarjeta.
  El aire lo pone el huésped, que es quien sabe qué viene después.
- **Los metros detrás del reloj** («hace 34 min · 180 m»). La distancia es un hecho del LUGAR, y la
  anatomía ya lo había decidido: pin = lugar, reloj = tiempo, un icono una cosa
  [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]. Se muda a la línea del pin **como pieza
  propia**, no concatenada: la calle se elide y los metros no. Muere
  `home_vehicle_card_parked_meta` en los 9 locales.

---

## Galería

Siete variantes nuevas y **tres borradas**: las de permiso / detección apagada / vigilancia frágil
enseñaban ya exactamente la fila suelta que el grupo «Home · detección» cataloga, y tres variantes
que enseñan lo mismo no cubren más — se leen como tres casos distintos y hay que abrir las tres para
descubrir que no lo son. Queda la de vigilancia INTERRUMPIDA como contraste: badge arriba, card
intacta debajo.

Catálogo visual de los 14 casos (A1–C3), con los colores y la letra de la app:
`Desktop/paparcar-card-states/estados-card-coche.html`.

## Verificación

`testAll` verde (3708) · guards de Konsist con `--rerun-tasks` verdes · iOS compila
(`:shared:compileKotlinIosSimulatorArm64`) · mock y prod compilan.

⏳ **Sin ver en mano**: los estados nuevos se han mirado en el AVD Pixel 8 por la galería; falta un
viaje real que confirme que «Voy conduciendo» ahora se nota en el instante del arme.
