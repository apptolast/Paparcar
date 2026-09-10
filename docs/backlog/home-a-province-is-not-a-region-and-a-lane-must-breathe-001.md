# HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001 · el Ministerio contestaba y no le preguntamos, y la Home de browse va apretada

**Estado:** ✅ Done — en master (el hash vive en `MEMORY.md`: este doc viaja DENTRO de su propio
commit, así que por construcción no puede citarlo).

⚠️ **Tercera de la pila**, sobre `HOME-THE-WATCH-COMES-FIRST-…` y esta sobre
`ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001`. Orden de merge: onboarding → watch/header → esta.

Abierta con lo que el user vio en el **prod release en el Oppo** (CPH2371, Android 13) el 10-09-2026.

## 1 · España sin precios — MEDIDO, no deducido

En El Puerto de Santa María el carril enseñó **20 gasolineras sin un solo precio** y la frase
*«Prices are not published in this country»*. España publica precios.

Medido contra el feed real del Ministerio el 10-09-2026, desde este equipo:

| comprobación | resultado |
|---|---|
| `Listados/Municipios/` → «Puerto de Santa María (El)» | existe: `IDMunicipio=1789`, `IDProvincia=11`, `Provincia: CÁDIZ`, `CCAA: Andalucia` |
| `EstacionesTerrestres/FiltroMunicipio/1789` | **200 con precios de ese mismo día, 16:38** — gasolineras de CALLE RIO TAJO con su cifra |

**El Ministerio contestaba. No le preguntamos.** La cadena, en el código:

1. `GetNearbyFuelStationsUseCase` arma la query con `regionName = address?.province ?: address?.region`.
2. En ese punto llegó **«Andalucía»** — que es la COMUNIDAD (lo confirma la propia cabecera del peek,
   «El Puerto de Santa María, Andalucía»), no la provincia.
3. `MineturStationsSource` resuelve la provincia con `SpanishProvinces.idFor(regionName)`, y ese
   listado está indexado por **nombre de PROVINCIA**. «Andalucía» no casa → `IllegalStateException`.
4. `FuelStationsRegistry` hace `recoverCatching` → **fallback a OSM**, que trae estaciones sin precio.
5. `HomeFuelLane` ve `hasPrices == false` y dice *«este país no publica precios»*.

⛔ **Dos fallos, no uno.** El segundo es peor: esa frase es un **veredicto sobre un país** que la app
nunca verificó. Es `FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` otra vez, y aquí muerde más fuerte
porque el usuario ya sabe que en España hay precios: la app no parece rota, parece mentirosa.

### Diseño

- **El municipio es mejor llave que la provincia, y ya lo teníamos.** `address.city` viene relleno
  siempre que hay dirección (la pinta el propio peek); `subAdminArea` no. Se resuelve el municipio en
  el listado del Ministerio, que además trae `IDProvincia` — así que de la ciudad salen las DOS
  llaves. Y `FiltroMunicipio` descarga menos que una provincia entera.
- ⛔ **La normalización de acentos no basta**: el Ministerio escribe el artículo AL FINAL y entre
  paréntesis — «Puerto de Santa María (El)» contra el «El Puerto de Santa María» del geocoder. Sin
  deshacer esa inversión, la llave nueva falla igual que la vieja.
- **Orden de llaves**: municipio → provincia → (solo entonces) fallback. Cada salto se registra.
- **La frase deja de mentir.** «No hay precios publicados en este país» solo se dice cuando la fuente
  NACIONAL contestó sin precios. Si hemos caído al fallback, lo que toca es decir que no se pudieron
  traer los precios oficiales ahora — causa, consecuencia y remedio, sin mecánica interna.

## 2 · El repaso de diseño de la Home en browse

Todo esto es del user, viéndolo en mano:

- **Los espacios no respiran**: falta aire y falta un `PapDivider` entre la card de vehículo y las
  secciones, y otro **debajo** de la fila de tres pestañas para separarla de su contenido.
- **La flecha (chevron) de la card de vehículo** queda pegada arriba; va centrada verticalmente.
- **Los iconos de gasolinera y parking se ven apagados**, y el número del peek también.
- **El marcador de parking público** debería tener la MISMA forma que los spots de usuario,
  manteniendo sus colores.

### Verde: hasta dónde (decidido por el user)

⛔ Esto roza dos tickets recién mergeados —`UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001` (el
presupuesto de verde) y `UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001` (los tiles neutros
etiquetan, no son acciones apagadas)— así que la regla se escribe aquí y se respeta:

> **El verde lo lleva el sujeto ACTIVO, y solo él**: la pestaña en la que estás, el contador del peek
> y los marcadores de esa capa en el mapa. Las filas de la lista siguen neutras.

Sigue habiendo un solo énfasis por pantalla: como mucho una cosa verde a la vez, porque solo hay un
carril activo. Lo que se recupera es que la pantalla no se vea apagada JUSTO donde estás mirando.

### El badge de estado de la card de vehículo

El user echa en falta el diseño anterior. ⚠️ **No lo cambió este trabajo**: fue `b967323b`, que
desmontó la card de dos partes (cabecera + `PapDivider` + fila al pie con la caja de icono teñida) y
la dejó en una sola fila con el chevron en `trailing` — y su propio comentario dice que se hizo «(user,
10-09)».

**Decidido: no se elige a ciegas.** Van las DOS variantes a la galería del Dev Catalog, se instalan y
el user decide en mano. Hasta entonces la app se queda como está.

## Criterio de éxito

- En El Puerto de Santa María, el carril enseña precios del Ministerio.
- Ninguna frase afirma que un país no publica precios salvo que la fuente nacional lo haya dicho.
- La card de vehículo, las secciones y el carril tienen aire y sus divisores.
- El chevron está centrado.
- Hay exactamente UNA cosa verde por pantalla: el sujeto activo.
- El marcador de parking comparte forma con el de spot y conserva sus colores.

## Consumidores auditados

| sitio | qué asumía | cómo queda |
|---|---|---|
| `MineturStationsSource` | la provincia es la única llave | **cerrado**: municipio → provincia → error. El catálogo de municipios solo se descarga si hay pueblo que buscar |
| `MineturStationsSourceTest` (3 tests) | el camino por provincia era el único | **cerrado**: siguen verdes y se les suman 4, incluido el de «Andalucía» |
| `FuelStationsRegistry` | un fallback silencioso | **cerrado**: marca `didFallBack`, que es lo que separa las dos frases |
| `HomeFuelLane` | una sola frase para «sin precios» | **cerrado**: dos, y la nueva va en los 9 locales |
| `VehicleIdentityHeader` | el trailing hereda la alineación de la fila | **cerrado**: se centra por sí mismo; el tile sigue casando con la primera línea |
| `HomeNearbyLensRow` | sin regla inferior, y activa en `onSurface` | **cerrado**: divisor DENTRO del sticky (fuera se quedaría atrás al scrollear) y verde en la activa |
| `PapSheetLead.NearbyCounter` | neutro siempre | **cerrado**: verde con cifra, neutro cuando solo hay marca |
| `PublicParkingMarker` | etiqueta redondeada con «P» | **cerrado**: puck del spot con SUS colores — cuerpo de papel y aro verde, nunca relleno |
| filas de la lista (`PapIconTile`) | — | **exentas a propósito**: ahí el glifo etiqueta su fila y sigue neutro [UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001] |

## Verificación

- `./gradlew :shared:testDebugUnitTest --rerun-tasks` → **2.696 tests, BUILD SUCCESSFUL**;
  `:app:assembleProdDebug` + `:app:assembleMockDebug` OK.
- **prod RELEASE instalado en el Oppo** (CPH2371), hash verificado EN el device (`fcaad4f6…`),
  arranca y se mantiene vivo sin `FATAL`. (El `ClassNotFoundException: IZatManager` del log es de
  otro proceso del sistema, no del nuestro.)
- ⏳ **Falta que el user lo mire en mano**: los precios de El Puerto de Santa María, el aire nuevo,
  el chevron, el verde de la pestaña activa y el marcador de parking.

## 3 · Las dos fusiones (rondas 2 y 3, en device)

El user vio el resultado en el Oppo y salieron dos duplicaciones que no eran de peso visual sino
**editoriales**: dos superficies diciendo la misma frase, pegadas.

| lo que se repetia | como queda |
|---|---|
| «Watching your Alfa Romeo 156 · Parked» encima de «Alfa Romeo 156 · Parked at Calle X» | la fila CALMADA de vigilancia se aparta y su **barra de acento se muda a la card del coche** |
| «¿Donde esta tu coche? · Marcar mi plaza» encima de una card que dice «Not marked» | la fila de arranque en frio se aparta; la card pregunta y trae **su propio boton, por cada coche** |

Se aparta con el mismo mecanismo que ya existia para el checklist (`FirstStepsOwnership`), ahora por
`carCardSaysTheWatch`.

⛔ **Solo se apartan las filas que dicen LO MISMO.** Vigilancia fragil, vigilancia muerta, pregunta
abierta, sin permisos, sin coche: todas dicen algo que la card no dice y siguen enteras con su barra.
Apartar por «parecerse» en vez de por «decir lo mismo» es como se pierde un aviso.

### «Estoy conduciendo» no se borra: se muda

Comprobado antes de tocar nada: `StartDrivingDetection` tenia **un solo punto de entrada en toda la
app** — esa misma fila de arranque en frio. Fusionar sin mudarlo habria borrado en silencio la unica
palanca manual de armado.

Se muda al peek de posicionar aparcamiento, **solo en CREAR** (editando el pin de una sesion que ya
existe, declarar que conduces es una contradiccion, no una salida). Y gana precision: alli el coche
es `targetVehicle`, el que el usuario acaba de elegir; en la fila se disparaba sobre «el activo, o el
primero» — una suposicion.

### Y el boton de la card se quito al mirarlo

La fusion trajo un CTA «Marcar mi aparcamiento» dentro de la card sin marcar. Duro una ronda: su
`onClick` era **la misma lambda** que la de la card, o sea chrome repitiendo el gesto de su propio
contenedor. La fila que sustituye si lo necesitaba, porque era un MENSAJE y a los mensajes no se les
toca; una card de coche es un objeto que se toca y lo dice con su chevron. Y el momento en que un
boton explicito si se gana el sitio —el primer aparcamiento de quien acaba de instalar— lo cubre el
checklist con su paso `MARK_PARKING`.

Lo que SI se queda de aquella ronda: la card pregunta («Marca tu aparcamiento y lo vigilo») en vez de
rotularse con el nombre de un boton («Mark parking» en verde, que es lo que decia antes).

### El titulo de seccion vuelve, y siempre

«Tu vehiculo» / «Tus vehiculos» por `plurals`, con uno o con cinco. Se quito por repetir lo que cada
card ya dice y vuelve porque hace **otro** trabajo del que se le atribuia: no nombra los coches,
SEPARA dos bloques que se leian como uno. Claves recuperadas de `c253ce41`, no reescritas.

### Y lo que se revirtio

Las pestanas vuelven a `onSurface` con el indicador verde: probar el verde entero fue peor en device.
Lo apagado eran **los glifos de las filas** y la cifra del peek, que es donde se arreglo.

## Pendiente

~~El boceto del badge de la card de vehiculo~~ — **ya no hace falta**: el badge que el user echaba en
falta era la **barra de acento** de la superficie de deteccion, que `b967323b` perdio al pasar la card
a `PapOutlinedCard`. Restaurada, y ahora tambien en la card del coche.

⬜ Falta que el user mire la ultima ronda en mano.
