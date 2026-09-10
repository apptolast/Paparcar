# HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001 · el bloque fijo se lee en el orden que su propia ficha dibujó, y la cabecera responde del carril que miras

**Estado:** ✅ Done — en master (el hash vive en `MEMORY.md`: este doc viaja DENTRO de su propio
commit, así que por construcción no puede citarlo).

⚠️ **Apilada sobre `ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001`**, no sobre master: las dos tocan
`HomeSheetContent`, `HomeState`, `HomeSlices` y `HomeViewModel`, y sacarla de master obligaba a
resolver el mismo conflicto dos veces. Se mergea DESPUÉS de aquella.

## Problema

Tres cosas que el user vio en device el 10-09-2026, sobre la Home que dejó
`HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001`.

### 1 · La superficie de detección quedó DEBAJO de los coches, contra lo que decía su propia ficha

El diseño acordado está escrito en `home-a-section-must-not-sit-behind-anothers-list-001.md`:

```
├─ BLOQUE FIJO · lo MÍO ahora ────────────────┤  nunca se mueve, nunca se pliega
│  first steps · superficie de detección      │
│  TUS VEHÍCULOS + fila de coches             │
```

La detección va **encima**. Lo que se mergeó en `b967323b` hace lo contrario y lo justifica en un
comentario nuevo — *«Primero, porque es el SUJETO de esta pantalla; la detección de abajo es algo que
se le hace a él»* — que contradice a la ficha que ese mismo commit dio por cumplida.

⛔ Y el fichero se contradice **a sí mismo**: la línea 91 sigue diciendo *«Detection story surface —
under the address header, above vehicles»*, un comentario de `d575d83b` que el reorden dejó mintiendo.
Es el mismo patrón que ya costó una auditoría en `UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001`: *un
comentario que miente cuesta más caro que ninguno*.

**Cuál de los dos órdenes es el bueno**: el de la ficha. La tarjeta de vigilancia es lo que responde
«¿está pasando algo con mi coche AHORA?», y esa es la pregunta con la que se abre Home. La tira de
coches es un índice para navegar; el aviso sobre el coche vigilado no puede aparecer después del
índice que lo lista.

### 2 · El rótulo «TUS VEHÍCULOS» sobra

Hoy sale solo con 2+ coches (con uno se retira, porque la card ya lleva su nombre). La regla es
consistente, pero la conclusión se queda corta: la tira **también** lleva el nombre en cada card, así
que el rótulo repite en mayúsculas lo que ya se lee, y encima empuja hacia abajo el carril.

### 3 · Con el sheet abierto, la cabecera cuenta PLAZAS mientras el carril cuenta PARKINGS

Medido en device: en el carril *Parkings* (4 resultados), la cabecera seguía diciendo **«TU ZONA»** con
la cápsula verde **«6 LIBRES»**. Dos cifras de dos sujetos distintos, a 300 dp una de otra.

Es exactamente `A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001` otra vez —*el mapa pintaba 6 parkings y la
lista decía que no había ninguno*— aplicado ahora a la CABECERA: la lección de aquel ticket fue
**mirar qué ancla usa la cabecera de la pantalla**, y aquí usa el sujeto equivocado.

⛔ **Plegado NO cambia**: el peek cerrado ya enseña TU VEHÍCULO (el viaje vivo, y si no, el coche
aparcado) y ese es su trabajo — con el sheet cerrado es la única voz que tiene Home. La cabecera de
zona solo existe con el sheet ABIERTO (`showZoneHeader`), y es esa la que pasa a seguir al carril.

## Doctrina en juego

- `A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001` — una sección responde de lo que estás mirando.
- ⛔ `FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` — **un `0` en un sujeto que nadie preguntó no es
  un veredicto sobre el mundo**. La fila de pestañas ya lo respeta (solo enseña cifra si la hay); la
  cabecera nueva tiene que respetarlo igual.
- `UI-COLOR-DOCTRINE-001` + el presupuesto de verde — la cápsula de plazas es verde porque las plazas
  son el producto. Un complemento **no** hereda ese verde.
- ⛔ Un comentario que miente se borra o se arregla, no se rodea.

## Diseño

**1 · Orden.** La superficie de detección sube por encima del bloque de vehículos, como dibuja la
ficha. Los dos comentarios se arreglan: el que mentía desde `d575d83b` y el que justificaba el orden
invertido.

**2 · Rótulo.** Muere el `item("vehicles_header")` entero, y con él las plurals
`home_vehicles_section_header` en los 9 locales — una key muerta la caza `LocaleParityGuardrailTest`.

**3 · La cabecera sigue al carril.** Solo con el sheet abierto:

| carril | eyebrow | lead |
|---|---|---|
| `SPOTS` | TU ZONA | cápsula de plazas (verde si hay) — **sin cambios** |
| `PARKINGS` | PARKINGS CERCA | cifra si ya se preguntó, si no la marca `P` — neutro |
| `FUEL` | GASOLINERAS CERCA | cifra si ya se preguntó, si no el surtidor — neutro |

- El título (la calle o el POI de la cámara) **no cambia**: sigue siendo dónde estás mirando, que es
  verdad para los tres sujetos.
- La cifra de un complemento es `Int?`, no `Int`: `null` = nadie ha preguntado → se enseña la marca
  del sujeto en vez de un número. Un `0` solo puede salir de una respuesta real.
- Neutro, no verde: el verde de la cápsula dice «esto es el producto». Repartirlo entre los tres
  sujetos es justo lo que `UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001` acaba de quitar de esta misma
  pantalla.

## Criterio de éxito

- Abrir Home: la tarjeta de vigilancia se lee ANTES que la tira de coches.
- Con 3 coches no hay rótulo «TUS VEHÍCULOS» y el carril sube.
- En el carril de Parkings, la cabecera no dice ni «TU ZONA» ni una cifra de plazas.
- Entrar en un complemento por primera vez (sin respuesta aún) **no** enseña un `0`.
- Plegado sigue enseñando tu vehículo, con viaje vivo o aparcado.

## Consumidores auditados

| sitio | qué asumía | cómo queda |
|---|---|---|
| comentario `── 0.` de `HomeSheetContent` | «above vehicles», desde `d575d83b` | **cerrado**: dice el orden real y por qué se corrigió |
| comentario `── 1. TU COCHE` | que los coches iban primero | **cerrado**: reescrito, y explica por qué el razonamiento se quedó a medias |
| `home_vehicles_section_header` (plurals ×9) | tenía lector | **cerrado**: retiradas de los 9 locales + el import. Una key sin lector la caza `LocaleParityGuardrailTest`, que sale verde |
| `PapSheetLead` | solo tenía contador VERDE (`SpotCounter`) | **cerrado**: `NearbyCounter(count: Int?, icon)`, neutro y con dos caras |
| `BrowsePeek` colapsado | su sujeto es tu coche | **exento a propósito**: no se toca. Con el sheet cerrado el peek es la única voz de Home |
| `subject_public_parkings` / `subject_fuel_stations` | solo las leía la fila de pestañas | **reutilizadas**: cabecera y pestaña dicen la MISMA palabra, sin 18 strings nuevos |
| galería mock | no cubría la cabecera por carril | **cerrado**: 2 variantes — con cifra y **sin preguntar** (la que ejercita el `null`) |
| `HomeSlicesTest` | no vigilaba el contador del carril | **cerrado**: 4 tests, incluido el que separa `null` de `0` |

## Verificación

- `./gradlew :shared:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL** (guards Konsist y
  paridad de locales incluidos) · `:app:assembleProdDebug` + `:app:assembleMockDebug` OK.
- ✅ **VISTO EN DEVICE** (emulador Pixel 8 Pro, API 37, 10-09-2026), en la app real con el escenario
  «Home todo OK», hash del APK verificado en el device (`77698d43…`):
  - La tarjeta «Watching your Mi Seat» se lee **encima** de la tira de coches, y el carril sube.
  - No hay rótulo «YOUR VEHICLES» con tres coches.
  - En el carril de Parkings la cabecera dice **«CAR PARKS · 4 · Calle Real»** con la cápsula
    **neutra**: ni «TU ZONA» ni la cifra de plazas ni el verde.
  - Plegando el sheet con el carril en Parkings, el peek vuelve a **«TOYOTA COROLLA · PARKED»**.
- ⏳ Sin ver la cara «sin preguntar» de la cabecera en la app real (los fakes del flavor mock
  responden al entrar); sí está en la galería y en un test.
