# WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001 · El widget en ruta también tiene un verbo

**Estado:** ✅ Done · master `d5696b774` · cierra la **issue #53**

## Problema

Pedido por el product owner (#53): *«Incluir botones en widget para cuando está el vehículo en ruta,
por si el usuario quiere revertir la detección o realizar alguna otra interacción.»*

El estado `Driving` del widget no tenía ninguno, y con un argumento escrito: *«este estado no tiene
sesión, así que los dos verbos de `Parked` —navegar, ver ficha— no apuntan a nada»*.

## Diseño

El argumento sigue siendo cierto **para esos dos**: sin pin no hay a dónde navegar ni ficha que
abrir. Lo que no se seguía de ahí es que no hubiera NINGÚN verbo. **El que falta no mira al pasado,
mira al presente: «ya he aparcado»** — justo lo que el conductor sabe y la app todavía no.

- Mismo botón callado que los de `Parked`: icono sin relleno, a la derecha, `CircleIconButton`.
- **Mismo destino que la notificación** de «¿sigues aparcado?» (`EXTRA_START_ADD_PARKING`), no un
  camino nuevo: el widget ofrece lo que la app ya sabe hacer.
- `fromDetection = false`, como la notificación: este pin lo pone una persona, no lo nomina una
  detección, y su procedencia tiene que decirlo. [DET-NUDGE-PIN-PROVENANCE-001]
- String nuevo en los 9 locales de `app/src/main/res` (el widget usa recursos Android, no Compose
  Resources — y ahí el apóstrofo SÍ se escapa).

## Lo que NO he hecho, y por qué quiero que lo decidas

**«Revertir la detección» durante el viaje.** Es la otra mitad de la issue y no la he tocado porque
no es un botón, es una decisión:

1. Durante un viaje **no hay nada que revertir todavía** — el revert existe sobre un aparcamiento ya
   guardado. Lo que tendría sentido aquí es **«no estoy conduciendo»**, que MATA la detección de este
   viaje.
2. Y eso es un **comando en segundo plano**, no abrir la app. Los tres botones del widget hoy son
   `actionStartActivity`: abren la app en un sitio. Un comando exige un `ActionCallback` que hable
   con el servicio, y equivocarse ahí es perder una detección de verdad.
3. Encima ya existe un verbo parecido en la notificación (`notif_action_stop_detection`), así que
   hay que decidir si es el mismo o uno distinto — «paro la detección» no es «este viaje no era un
   viaje».

## Criterio de éxito

- Conduciendo, el widget ofrece «Marcar aparcamiento» y abre el marcado manual.
- El pin que sale de ahí se registra como manual, no como detectado.

## Segunda vuelta (review del user, 20-09): el verbo de revertir, y por qué no hacía falta inventarlo

> «Necesitaríamos ese botón para muchos casos, sobre todo en detección activa. Tenemos uno que es
> parar la detección, **pero no sabemos porqué**. Si se monta en el coche de un amigo que le ha
> recogido junto a su coche, debería poder revertir. Reflejar ese botón en UI (card del vehículo),
> notificación y widget, **deberían ir simétricos**.»

### El análisis que pediste: cuándo quiere un conductor parar o revertir

Son **tres momentos distintos y tres verbos distintos**, y confundirlos es lo que hace que ninguno
se entienda:

| Momento | Lo que el usuario quiere decir | Qué existe hoy |
|---|---|---|
| **Durante el viaje** | «esto no es un viaje mío» — el amigo que le recoge, el bus, ir de copiloto | `ACTION_USER_STOP` |
| **Con el pin ya puesto** | «ahí no he aparcado yo» | `ACTION_PARKING_REVERT` («No, cancelar» en la notificación de guardado) |
| **Para siempre** | «no vigiles este coche» | el interruptor de Ajustes |

Lo que pedías es el **primero**, y aquí está el hallazgo: **ya existe y hace exactamente lo
correcto**. `handleUserStop` cierra el viaje **sin guardar plaza**, cancela el job y estampa
`UserStopStore`, un periodo de silencio en el que los avisos automáticos no vuelven a armar. Es la
versión manual de lo que la app se hace a sí misma cuando detecta que vas en bici
[DET-BIKE-NOT-A-CAR-001].

### Entonces, ¿qué falla? **La palabra, no la mecánica**

El botón decía **«Parar» / «Stop»**: eso nombra *lo que hace la app*, no *por qué lo pulsarías*. Por
eso «no sabemos porqué». La consecuencia ya estaba bien contada —el snackbar dice «Este viaje no se
guardará como aparcamiento; pulsa «Voy conduciendo» cuando quieras volver»—; lo que faltaba era que
la etiqueta nombrara la **causa**.

Pasa a **«No voy en mi coche»**, que es literalmente el caso que describes y cubre igual el bus, el
taxi y el copiloto. Es la negación de la frase que la app acaba de afirmar («Estás conduciendo el
X»), así que se lee sin pensar. [COPY-NO-INTERNALS-001]

### Simetría: las tres superficies, la misma frase y el mismo intent

| Superficie | Antes | Ahora |
|---|---|---|
| Card del vehículo en detección | «Parar» | «No voy en mi coche» |
| Notificación | «Stop detection» | «No voy en mi coche» |
| **Widget** | **no existía** | **«No voy en mi coche»** |

⛔ Las tres despachan **el mismo `ACTION_USER_STOP`**. No hay un verbo nuevo ni un camino nuevo: si
hubiera creado uno propio para el widget, serían cuatro definiciones de «parar», que es la clase de
cosa que este proyecto paga cara — y que acaba de pagar en `DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001`.

### El widget: un COMANDO, no una pantalla

Los otros tres verbos del widget abren la app, porque los tres terminan donde el usuario sigue
decidiendo. Éste no: el conductor ya sabe que no va en su coche y lo único que quiere es que la app
deje de seguirle. Mandarlo a Home a pulsar otra vez sería cobrarle dos gestos por una decisión, con
la app estorbando justo cuando va en el coche de otro.

Así que es un `ActionCallback` (`StopTripFromWidgetAction`), con dos decisiones explicadas en su
KDoc:
- ⛔ **`startService`, no `startForegroundService`**: este botón sólo se dibuja en `Driving`, y ese
  estado existe porque hay un viaje en curso — el servicio está vivo y en primer plano. Promoverlo
  otra vez sería pedirle un `startForeground()` a un servicio al que le pedimos que pare, y una
  promesa incumplida ahí es un **ANR**.
- El `runCatching` no oculta un fallo: si el servicio murió entre el pintado y el dedo, **no hay
  nada que parar**, que es la misma respuesta que el servicio da a un tap rancio.
- Repinta el widget al terminar: un botón que sigue ahí después de pulsarlo se lee como que no ha
  pasado nada.

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin :app:compileProdDebugKotlin` → BUILD SUCCESSFUL.
Strings en los **9 locales** de los dos juegos de recursos (`LocaleParityGuardrailTest` verde).

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
El widget es de las superficies que **hay que ver sí o sí**: dos botones de acción en un 3×1 es
justo donde el ancho se acaba.
