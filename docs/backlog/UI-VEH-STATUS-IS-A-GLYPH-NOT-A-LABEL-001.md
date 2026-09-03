# UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001 · el tier de vigilancia se dice con una forma, no con una palabra

**Estado:** ✅ **Done** — verificado en device (Oppo, 03-09, 18:33) · rama
`feature/UI-VEH-BADGE-RIDES-ABOVE-THE-NAME-001-badge-above-name` · worktree
`../Paparcar-badge-above-name`

> Verificado: **2.174 tests verdes** (con `--rerun-tasks`, para que los guardarraíles Konsist se
> ejecuten de verdad) · `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin`.
>
> La rama conserva el nombre del primer diagnóstico (`…BADGE-RIDES-ABOVE-THE-NAME`), que era media
> medida: movía la píldora en vez de quitarla. Ver *Historia* al final.

## Problema

Paparcar tenía **tres** superficies que presentan la identidad de un vehículo, y **dos anatomías**
distintas para decir cómo se le vigila:

| Superficie | Cómo decía el tier | Composable |
|---|---|---|
| Chip de Home (**2+ vehículos**) | glifo + color, **sin palabra** | `HomeVehicleChip` |
| Tarjeta de Home (**1 vehículo**) | píldora tonal `ACTIVO` / `BT` | `VehicleIdentityHeader` |
| Ficha de Vehículos | píldora tonal `ACTIVO` / `BT` | `VehicleIdentityHeader` |

La divergencia tenía un coste medible en la ficha, capturado en device (Oppo, 18:05 del 03-09): la
píldora comparte la línea del título, el nombre lleva `weight(1f, fill = false)`, y por tanto **la
LONGITUD de la palabra de estado decide cuánto ancho le queda al nombre**. Resultado:

- `◉ ACTIVO` + "Oppo Test" (9 caracteres) → **el nombre parte en dos líneas**
- `✱ BT` + "Skoda Kamiq" (12 caracteres) → una línea, con holgura

Es decir: **la tarjeta de un coche cambiaba de alto según su estrategia de detección**, y el nombre
corto era el que se rompía.

## Doctrina violada

`HOME-VEH-REFINE-001` ya lo decía, literalmente, en el docstring del propio componente:
*"Status is colour-only, never a method label"*, y en el del chip: *"No method label, no corner
badge"*. **La píldora era la desviación**, no la regla.

`CARD-ONE-BADGE-001` la introdujo con un motivo real — que el chip de carrocería·tamaño dejara de
competir con el estado — pero resolvió *"el estado es lo único que merece caja"* cuando la lectura
correcta de HOME-VEH-REFINE era *"el estado no necesita caja porque no necesita palabra"*. Y la
palabra, además de robar ancho, repetía lo que el glifo y el color ya decían.

## Señales / datos disponibles

- `VehicleIdentityHeader` es **una sola anatomía** para las dos superficies con píldora
  (`VehiclePageContent.kt:129` y `HomeParkingRow.kt:236`), así que el arreglo entra en un sitio.
- `VehicleWatchLeadingIcon` **ya existía** y ya servía al chip: tres formas distintas (Bluetooth /
  radar / aro hueco) con el color de identidad. No hay que diseñar nada, hay que dejar de duplicar.
- Los strings `vehicle_status_active` · `vehicle_card_detection_bt` · `home_vehicle_status_inactive`
  existen en los 9 locales.

## Diseño

Las tres superficies pasan a la anatomía del chip: **glifo de vigilancia + nombre, en una línea.**

```
[ tile glyph ]  ◉ Oppo Test          ← el glifo lleva el color, el nombre queda en onSurface
                Compacto familiar · Mediano
```

1. `VehicleIdentityHeader` — `VehicleWatchBadge` sale, `VehicleWatchLeadingIcon` entra delante del
   nombre, con el gap del chip (6 dp). El nombre pierde el `weight(1f, fill = false)`.
2. `VehicleWatchLeadingIcon` — gana `contentDescription: String? = null`.
3. `VehicleWatchBadge` — **borrado**. Al servir el glifo a las tres superficies le quedaba un solo
   call site; un componente que nadie renderiza no es un componente (una exención sobre código
   muerto es un agujero, no una exención — `UI-TYPE-SYSTEM-HYGIENE-001`). Con él se van
   `STATUS_BADGE_BG_ALPHA` y el import de `PaparcarType`; `PapBadge` vuelve intacto a master.

### Lo que se pierde, tier por tier — y por qué se puede perder

- **`BT`** → la palabra es la abreviatura del icono. El glifo de Bluetooth es universalmente
  legible: el texto repetía.
- **`ACTIVO`** → aquí la palabra sí decía algo que el glifo no (el radar dice *el método*, la
  palabra decía *que está vigilado*). Pero en la ficha, un vehículo **no** vigilado ya lleva debajo
  la fila a todo el ancho **"Marcar como activo"** (`SetActiveRow`, solo si `isInactive`): la
  consecuencia está escrita, en palabras, a 40 px. La palabra era redundante con la ausencia de esa
  fila.
- **`INACTIVO`** → el riesgo real: un aro gris hueco es *la ausencia de una señal*, y las ausencias
  no se ven. Lo desactivan dos hechos: (a) en la ficha existe esa misma fila "Marcar como activo";
  (b) en `HomeVehicleCard` hay **un solo** vehículo registrado, que es el default por construcción,
  así que el tier `Off` es casi inalcanzable ahí. Y el chip lleva meses así: no se abre un agujero
  nuevo, se cierra el último sitio que no seguía la regla.
- **⛔ Lo que NO se puede perder: la accesibilidad.** Hasta hoy la palabra era lo único que leía
  TalkBack (`VehicleWatchLeadingIcon` pasaba `contentDescription = null`), y verde-vs-gris es justo
  el par que un deuteranope no separa. Por eso el string **cambia de canal, no desaparece**: pasa a
  ser el `contentDescription` del glifo. La forma distingue el tier a la vista, el color lo refuerza,
  y el lector de pantalla dice la palabra. Borrar los strings era la limpieza tentadora y la
  equivocada.

## Criterio de éxito

- Las tres superficies con la MISMA anatomía: glifo + nombre en una línea, sin caja y sin palabra.
- "Oppo Test" en una línea, y las tarjetas de `ACTIVO` y `BT` **al mismo alto**.
- `VehicleWatchBadge` no existe; `PapBadge` idéntico a master.
- Suite verde + `compileMockDebugKotlin` / `compileProdDebugKotlin`, y los guardarraíles Konsist con
  `--rerun-tasks` (un cambio de estilo puede no tocar bytecode → Gradle los daría por UP-TO-DATE).
- Verificado en device (Oppo): ficha con los dos coches.

### Estado de la verificación en device (03-09, 18:33, Oppo)

- ✅ **Tier `Assisted` (verde, radar)**: `◉ Oppo Test` en una línea, glifo delante del nombre, sin
  caja. Visto en mano.
- ✅ **Tier `Bluetooth` (azul)**: la píldora `✱ BT` se vio en mano en la pasada anterior (el glifo y
  el color son los mismos; lo que cambia es que ya no lleva caja ni palabra). **La forma final del
  carril azul no se llegó a ver**: el Skoda Kamiq —el único vehículo con MAC emparejada— lo borró el
  user a mano a mitad de sesión, no hay bug detrás. Alcanzable por el Dev Catalog del flavor `mock`
  o registrando otro vehículo con BT.

> ⚠️ De paso quedó medido algo útil para el próximo que quiera leer la base del device: **sacar
> `paparcar.db` + `-wal` con la app VIVA no da un snapshot fiable** — `vehicles` y
> `parking_sessions` leían 0 filas mientras la pantalla pintaba 1 vehículo y 4 sesiones, porque el
> WAL no se replica bajo la app. Para inspeccionar Room hay que parar el proceso primero.

## Consumidores auditados

`grep -rn "VehicleWatchBadge\|VehicleWatchLeadingIcon\|vehicleWatchPinLabel\|PapBadge(" shared app --include=*.kt`

| Consumidor | Estado |
|---|---|
| `VehicleIdentityHeader` → ficha de Vehículos (`VehiclePageContent.kt:129`) | ✅ migrado al glifo |
| `VehicleIdentityHeader` → tarjeta de Home 1 vehículo (`HomeParkingRow.kt:236`) | ✅ migrado, misma anatomía **a propósito** |
| `HomeVehicleChip` (Home 2+ vehículos) | ⚪ intacto: **era ya el patrón correcto**, es el modelo que copian los otros dos |
| `VehicleWatchBadge` | ✅ borrado — 0 call sites al migrar el último |
| `vehicleWatchPinLabel` | ✅ vive: de texto visible a `contentDescription` |
| `PapBadge` — `PapStatusBadge` + `SpotIndicators.kt:85` | ✅ intactos, fichero revertido a master |
| Strings ×3 en los 9 locales | ✅ siguen en uso (a11y) — no se borra ninguna key |
| Dev Catalog / `StateGalleryScreen` | ⚪ exento: no hay pantalla, estado ni routing nuevos; es un restyle de componentes ya presentes |

## Historia — por qué la rama se llama de otra forma

La primera pasada (`UI-VEH-BADGE-RIDES-ABOVE-THE-NAME-001`) diagnosticó bien el mecanismo — la
longitud del estado le robaba el ancho al nombre — y lo arregló **moviendo** la píldora a su propia
línea encima del nombre, en rol `eyebrow` (11 sp) con un `dense` nuevo en `PapBadge`. Se compiló, se
instaló en el Oppo y **funcionaba**: nombre en una línea, las dos tarjetas al mismo alto.

Lo que enseñó verla en mano fue otra cosa: puesto al lado del chip de 2+ vehículos, el chip **se
leía mejor** — y el chip no tiene ni caja ni palabra. El arreglo bueno no era mover la cosa que
robaba el ancho, era quitarla. El `dense` de `PapBadge` y el rol `eyebrow` se revirtieron enteros:
eran andamio de la media medida.
