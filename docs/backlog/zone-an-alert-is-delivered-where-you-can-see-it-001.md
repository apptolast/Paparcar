# ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001 · el aviso de plaza nueva

**Estado:** ✅ Done · rama `feature/ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001` · issue #79

## Problema
> «Crear notificación de spot cerca de ti… **también cuando el usuario esté viendo la pantalla**, si
> aparece un spot NUEVO se le notificará también al momento, **pero tiene que ser nuevo**.»

## Lo que YA existía (y por tanto no se ha vuelto a hacer)
Casi todo. El sistema de alertas de zona estaba completo y bien:
`ObserveZoneSpotAlertsUseCase` + el worker de fondo + el fanout de Cloud Functions, con **memoria
durable compartida** (`ZoneWatchRepository`) para que «nuevo» signifique nuevo y una plaza no se
anuncie dos veces por dos rutas. La parte «tiene que ser nuevo» del ticket **ya estaba resuelta**.

Faltaban dos cosas, y son las de esta rama.

## 1 · El aviso salía siempre por notificación del sistema
También con el mapa delante. Y una notificación mientras miras el mapa es un aviso entregado en la
puerta de al lado: obliga a bajar la persiana para leer algo que ya estabas mirando.

**El canal lo elige quién puede verlo** (`alertChannelFor`):

| | canal |
|---|---|
| el mapa está en pantalla | dentro de la app, al momento |
| cualquier otra cosa | notificación del sistema |

⛔ **La pregunta es «¿está el MAPA delante?», no «¿está la app abierta?»**. Con Ajustes o el garaje
en pantalla la app está en primer plano y el usuario no está mirando dónde salen las plazas: ahí la
notificación sigue siendo el canal correcto, porque es la que sobrevive a que cambie de pantalla.

⚠️ **Un solo colector, y por una razón dura**: el stream apunta cada plaza en su registro durable
**antes** de emitirla (`recordLook`), así que dos consumidores compitiendo harían que el segundo no
viera nada — el primero ya la marcó como contada. Por eso el canal se elige dentro del único
colector que hay, y no añadiendo otro.

⛔ **Una sola señal de visibilidad, con un dueño.** Vivía como `MutableStateFlow` privado de
`HomeViewModel`, donde el reparto de avisos —de ámbito de app— no podía verla. Se **mueve** a
`MapVisibility` en vez de copiarse: dos verdades sobre una pregunta se equivocan el día que aparece
una tercera pantalla.

## 2 · El gate de Plus, fuera
Decisión del user el 21-09: *«no será de pago, al menos al principio»*.

⚠️ **Va contra el eje del producto** y hay que decirlo: *free = tú preguntas y la app responde ·
Plus = la app te avisa a ti*. Esto es exactamente «la app te avisa», y era el ejemplo canónico de lo
vendible. Se abre igualmente porque una app de plazas que no avisa de una plaza no demuestra lo que
hace, y nadie paga por una vigilancia que no ha visto funcionar. **Es una decisión de lanzamiento,
no un cambio de doctrina**: el día que se cierre, se cierra en una línea, y la memoria durable de
qué se contó ya sobrevive al cambio.

Dos tests que cifraban el gate **se invierten en vez de borrarse** — dejar escrito lo contrario es
lo que impide que el gate vuelva sin querer con el próximo refactor de entitlements.

## El aviso in-app
⛔ **Flota, no empuja.** El banner de conectividad es el primer hijo de la columna raíz y desplaza la
app: correcto para un estado que DURA. Éste dura cuatro segundos y habla de algo que está justo
debajo — empujar movería lo que el aviso quiere que mires.

⚠️ **No ofrece «Ver», y es deliberado.** Llevar la cámara desde la raíz exigiría un canal raíz → Home
para mover el mapa, y ése es exactamente el segundo dueño de la cámara que
[HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001] acaba de quitar de en medio. El aviso te hace
MIRAR; el mapa ya la está enseñando, porque el pin aparece por la misma suscripción viva que disparó
el aviso.

## Criterio de éxito
`AlertChannelTest`, 5 testigos sobre la decisión y la señal. Más los dos invertidos del stream.

⚠️ **El guard del grafo mock hizo su trabajo**: registrar `MapVisibility` en `DomainModule` no entró
a la primera —un `replace` que no encajó porque el bloque era `factory` y no `single`— y
`MockKoinModuleVerifyTest` lo cazó antes que nadie. Es literalmente el fallo para el que existe
[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001].

3582 tests, 0 fallos con `--rerun-tasks`. Strings en los 9 locales. Galería al día.

## ⛔ Paridad iOS
El ticket pedía «que sea paralelo a iOS». Lo es **por construcción, no por promesa**: la decisión
(`alertChannelFor`), la señal (`MapVisibility`), el stream y su memoria viven todos en `commonMain`.
Lo único de plataforma es quién escribe el corchete de visibilidad y quién pinta la notificación del
sistema — y las dos puertas ya existían en Android y en el hueco de iOS.
