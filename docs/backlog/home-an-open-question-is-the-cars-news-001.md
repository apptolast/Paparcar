# HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001 · la pregunta abierta la cuenta la card del coche, no una tarjeta aparte

**Estado:** ✅ Done · issue **#129** · `testAll` verde · iOS compila · mock y prod compilan ·
testigo falsificado · ⚠️ **sin captura del caso de un coche** (ver abajo)

## Problema

En la captura de la issue, con un solo coche y la pregunta abierta, la hoja apila **dos tarjetas
sobre el mismo Ford Focus, pegadas y contradiciéndose**:

```
┌ ¿Has aparcado el Ford Focus?            ← tarjeta de vigilancia (AwaitingAnswer)
│  Calle Jesús de los Milagros 30 · 18:44
│  [ Aún no ]            [ Sí, he aparca… ]
└
  TU VEHÍCULO
┌ ⊙ Ford Focus                       ›     ← card del coche
│  ⤳ En ruta · Calle Jesús de los Milagros 30
│  [        No voy en mi coche        ]
└
```

Una dice «te pregunto si has aparcado», la otra afirma «En ruta» — sobre el mismo coche, en el
mismo sitio, con dos juegos de botones. El usuario lee dos noticias donde hay una.

## Doctrina — esto no es una excepción, es el paso que faltaba

La regla ya está escrita y **ya se aplicó al viaje**:

> *«A LIVE TRIP has no story here any more: the vehicle card already tells it whole… Two surfaces
> telling the same news is the duplication this projection exists to prevent.»*
> — KDoc de `DetectionStory` [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]

Y el mecanismo de «si la card ya lo dice, esta fila se aparta» existe (`carCardSaysTheWatch`), con
una exclusión explícita:

> *«⛔ Solo se aparta la fila calmada. Un aviso frágil, una vigilancia muerta, **una pregunta
> abierta** o un coche sin marcar dicen algo que la card NO dice, y esos se quedan enteros.»*

Eso era cierto **mientras la card no supiera preguntar**. Decisión del user (22-09): que sepa. La
pregunta pasa a ser la noticia de ese coche, sustituye a la línea «En ruta», y se funde con la card
—**sin su propio tile/badge**, porque la card ya lleva el glifo del coche—. El estado «En ruta»
sin pregunta sigue contándolo el peek, que es la voz de la hoja cerrada.

## Diseño

**Una noticia, una superficie — y la superficie es la card del coche cuando la card puede decirlo.**

1. `HomeVehicleCard` acepta la pregunta abierta: sustituye la línea de estado por el título y el
   sitio/hora que YA usa la fila de vigilancia —las mismas keys, verbatim— y ofrece las dos mismas
   respuestas. Cero strings nuevos: el tray, el peek, la fila y ahora la card dicen la misma frase
   porque leen la misma key.
2. `resolveDetectionStory` gana `carCardAsks`: con la card llevando la pregunta, `AwaitingAnswer`
   devuelve `Hidden`. Es el mismo mecanismo que `carCardSaysTheWatch`, extendido a lo que antes
   estaba excluido, y sigue siendo una decisión **pura y testeable**.
3. **Quién puede llevarla se calcula UNA vez** en `HomeSheetContent` y se pasa a los dos sitios. Dos
   lugares evaluando «¿quién cuenta la pregunta?» es exactamente cómo se consigue que no la cuente
   nadie.

### ⛔ Sólo con UN coche, y no es pereza de alcance

Con dos o más, la tira de coches son `HomeVehicleChip` dentro de un `LazyRow`: un chip estrecho que
puede estar **fuera de pantalla**. Una pregunta con dos respuestas no cabe ahí, y una pregunta que
el usuario tiene que descubrir haciendo scroll horizontal es peor que la tarjeta que sustituye.
Además `PendingPromptWindow` identifica el coche **por NOMBRE** (`vehicleName`), no por id: con un
solo coche no hay ambigüedad; con varios, emparejar por nombre sería adivinar.

Así que con 2+ coches la fila de vigilancia se queda entera, como hoy. Es la misma condición que el
propio KDoc del viaje ya exigía: *«a trip can only be narrated when that card is on screen»*.

## Criterio de éxito

- Con un coche y pregunta abierta: **una sola** tarjeta, la del coche, con la pregunta dentro y sin
  tile propio; la fila de vigilancia no se dibuja.
- Las dos respuestas hacen exactamente lo mismo que hacían (mismos intents).
- Con dos o más coches, nada cambia.
- Sin pregunta, la card vuelve a su línea de estado y el peek sigue contando el viaje.

## Segunda vuelta (user, 22-09) — y un error propio

### ⛔⛔ Las dos negativas NO son la misma, y por poco se pierde la buena

Al fundir la pregunta quité la cápsula «No voy en mi coche» razonando que «Aún no» ya la decía.
**Falso**, y de la peor manera: son dos mecánicas distintas.

| Botón | Camino | Qué hace |
|---|---|---|
| «Aún no» | `ACTION_PARKING_DENIED` → `onUserDeniedParking()` | descarta el aviso y **mantiene la sesión viva** (`keepingAuthorization()`) |
| «No voy en mi coche» | `ACTION_USER_STOP` → `handleUserStop()` | **cierra el viaje sin guardar plaza**, suelta el confirm retenido y abre el **periodo de silencio** |

Y la diferencia muerde justo en el caso que la trajo: **un viaje falso positivo en casa**. Ahí «Aún
no» es una mentira que el usuario se ve obligado a decir —no estaba conduciendo— y encima deja vivo
el viaje fantasma. La única salida honesta es la que además calla los avisos automáticos un rato, y
yo la había borrado. Restaurada.

### La copia: «Aún no» → «Sigo conduciendo» (9 locales)

«Aún no» es un aplazamiento temporal, y la pregunta no va de tiempo: va de si ese viaje existe. El
nuevo label **dice su mecánica** —la sesión sigue viva— y, por contraste, deja claro que la cápsula
de abajo es otra cosa. Decisión del user sobre tres opciones; se descartó «Cancelar» por no decir
QUÉ se cancela.

EN `Still driving` · ES `Sigo conduciendo` · IT `Sto ancora guidando` · PT `Ainda a conduzir` ·
FR `Je roule encore` · DE `Fahre noch` · NL `Nog onderweg` · PL `Nadal jadę` · RO `Încă conduc`.

Guards de copia (paridad de notificación, paridad de locales, escapes) verdes con `--rerun-tasks`.

## Tercera vuelta (user, 22-09) — la regla entera

> *«su card debería gestionar todos los estados que tenemos sobre un vehículo dentro de su propia
> card; solamente en caso de no tener coche registrado debería aparecer el badge de estado, ya que
> no existe ninguno que podamos gestionar.»*

Elegida la versión radical (de tres opciones): **con un coche registrado el badge no sale nunca**.
Se mudan a la card los seis estados —pregunta con plazo, aviso sin plazo, «¿dónde está tu coche?»,
vigilancia frágil, vigilancia interrumpida, detección apagada y permiso CORE— y el badge queda
sólo para `NoVehicle`, que es literalmente «no tienes coche»: no hay card a la que mudarlo.

### Cómo, sin duplicar una sola palabra

⛔ El camino corto era reescribir esas filas dentro de la card. Es exactamente cómo se llega a que
la hoja y la card digan cosas distintas del mismo coche — el defecto que este ticket viene a quitar.
En vez de eso:

1. `ActionRow` gana **`embedded`**: el mismo contenido pierde su contenedor, su barra de acento y su
   tile cuando lo hospeda la card (que ya tiene los tres). El `when (story)` de ocho ramas, con sus
   textos y sus CTAs, **se evalúa una sola vez**.
2. La card gana **un hueco**, no diez callbacks: `status: (@Composable () -> Unit)?`. No sabe nada de
   detección; la hoja lo rellena con la misma fila en modo `embedded`.
3. **La decisión de quién hospeda sale del proyector.** `resolveDetectionStory` vuelve a contestar
   sólo QUÉ se cuenta; QUIÉN lo pinta lo decide la hoja con `isVehicleNews()` + «¿hay UNA card a
   ancho completo?». Atar una proyección pura a la anchura de un contenedor habría sido el error de
   diseño detrás del error de diseño.
4. `AskMetaLines` —la versión que escribí en la primera vuelta— **se borra**: era el segundo
   renderizador que este mismo doc prohíbe.

## Verificación

- `DetectionStoryTest` ×3, y el primero **falsificado**: quitado el apartarse, cae. Los otros dos
  fijan las dos mitades que un arreglo descuidado rompería — que sin card la pregunta **no**
  desaparece, y que el aviso SIN plazo (`PendingAsk`) no se aparta de rebote, porque la card no lo
  cuenta.
- Galería de estados: dos variantes nuevas, la del coche único (la card pregunta) y la de 2+ (la
  fila pregunta). La pregunta abierta **no estaba** en la galería hasta ahora.
- En emulador, con el preset «¿Has aparcado? — pregunta abierta durante un viaje»: ese escenario
  lleva DOS coches, así que enseña el caso que NO cambia — la fila sigue preguntando entera, sin
  duplicar nada. ✅

⚠️ **Lo que falta enseñar**: la card de UN solo coche con la pregunta dentro, que es el cambio. El
preset del catálogo no cubre ese caso y la variante de galería está en una lista larga que no
conseguí alcanzar navegando a ciegas por adb. El APK mock está instalado en el emulador: se ve en
*Abrir galería de estados* → «Sheet · 1 coche + pregunta abierta». **Antes de mergear conviene
mirarla**, porque una PR con resultado visual se enseña.
[PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `resolveDetectionStory` | la pregunta nunca se aparta | ✅ se aparta **sólo** si la card la lleva |
| `HomeDetectionSurface` (`AwaitingAnswer`) | es el único que pregunta en la hoja | ✅ sigue siéndolo con 2+ coches |
| Peek (eyebrow «¿HAS APARCADO?») | voz de la hoja CERRADA | ✅ intacto, es otra superficie |
| Notificación de bandeja | mismas dos respuestas | ✅ mismos intents, mismas keys |
| `HomeVehicleChip` (2+ coches) | no pregunta | ✅ sin cambios, por diseño |
| Strings | una frase por key | ✅ **cero keys nuevas**, se reusan las de la fila |

---

## La OTRA mitad de #129, todavía abierta (23-09)

El issue pide dos cosas y esta rama sólo cierra la primera:

> *«Propon mejoras para el diseño de la card de vehiculo en home, despues reflexiona tambien para
> la de vehiclesscreen»*

### 🔴 Lo que enseña la captura de la card APARCADA, y no es tipografía

Las dos capturas del issue están tomadas **con un minuto de diferencia** (18:59 y 19:00), en el
mismo sitio y con el mismo coche. Y dicen esto:

| Captura | Estado | Qué escribe la card |
|---|---|---|
| 18:59 | conduciendo | «En ruta · **Calle Jesús de los Milagros 30**» |
| 19:00 | aparcado, hace 27 min | «**Aparcado**» · «hace 27 min · a 6 m de ti» |

La card aparcada **no dice dónde está el coche**. Un minuto antes, la misma card, en la misma calle,
sabía nombrarla.

No es un fallo de diseño de la card: `parkedTitle` (`HomeParkingRow.kt:670`) ya quiere decir
«Aparcado en X» y cae a la etiqueta pelada sólo cuando `parkedName(session)` es null —
es decir, **cuando la sesión no tiene dirección resuelta**. Con 27 minutos de vida, un
`ParkingEnrichmentScheduler` que corre en cuanto hay red, y la calle demostrablemente resoluble en
ese mismo punto, eso es un fallo del enriquecimiento, no de la presentación.

Y muerde justo donde más importa: **la única pregunta que una card de coche aparcado tiene que
responder es dónde está el coche.** La distancia («a 6 m de ti») sólo sirve cuando ya estás al lado.

**Siguiente paso:** ticket propio de investigación — ¿por qué la sesión de las 18:33 seguía sin
`address`/`placeName` a las 19:00, si la línea del viaje sí resolvía esa misma calle? Sospechosos
por orden: el scheduler no se encoló al confirmar, se encoló y falló en silencio, o la card lee un
campo distinto del que rellena el enriquecimiento. Es medible contra la sesión real del usuario.

### 🟡 Y la reflexión sobre la card de VehiclesScreen: sin empezar

`VehiclePageContent.kt`. No se ha tocado en esta rama y el issue la pide explícitamente. La
pregunta a responder ahí es distinta de la de Home: Home es el AHORA y tiene que caber de un
vistazo; la ficha de Vehículos es lo MÍO y permanente (tamaño, combustible, BT, grupo, historial).
Antes de proponer nada conviene mirarla en device, porque no hay captura suya en el issue —
y **#118 ya se decidió una vez sin ver su captura**.

### ⚠️ Pendiente de esta rama (no cambia): la captura del caso de un coche

Sigue sin enseñarse la card de UN solo coche con la pregunta dentro, que es lo que cambia esta
rama. [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]
