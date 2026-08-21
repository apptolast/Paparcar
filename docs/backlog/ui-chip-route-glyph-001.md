# UI-CHIP-ROUTE-GLYPH-001 · "En ruta" deja de ser una palabra suelta y pasa a ser una ruta que se dibuja

**Estado:** ✅ Done — en master (squash 22-08-2026)

## Problema

En los chips de vehículo de Home, el pie de la tarjeta es la línea que dice qué pasa con el coche.
Aparcado se lee de un vistazo — **icono de ubicación + calle**. En ruta, no:

| Variante | Aparcado | En ruta (hoy) |
|---|---|---|
| `HomeVehicleChip` (2+ coches) | 📍 + dirección | **nada**: solo el texto "En ruta" latiendo |
| `HomeVehicleCard` (1 coche) | 📍 + "Aparcado en …" | 📍 **el mismo pin de aparcado** + "En ruta" |

Dos síntomas distintos del mismo hueco:

1. El chip compacto pierde el icono por completo mientras conduce, así que la fila cambia de
   anatomía (icono+texto → texto solo) justo en el estado que más quiere mirarse.
2. La card de 1 coche enseña el **pin de ubicación** conduciendo. Un pin es un sitio, y en ruta no
   hay sitio todavía: dice exactamente lo contrario de lo que pasa.

Palabras del user: *"cuando estamos en ruta aparece «En ruta» muy soso"*.

## Doctrina violada

`UI-COLOR-DOCTRINE-001` — *"El estado nunca tiñe; un viaje en curso se nota por MOVIMIENTO"*. El
proyecto ya tiene ese vocabulario en el chip (`DrivingRadarHalo` tras el glifo del coche, pulso de
alfa en las palabras de estado), pero **se detiene en la fila de identidad y no llega al pie**,
que es la fila con el hecho accionable. En ruta el pie se queda mudo o, peor, miente con un pin.

No hay invariante de detección implicado: esto es puramente presentación.

## Diseño

Un glifo nuevo, **`DrivingRouteGlyph`**, vive junto al resto del vocabulario de movimiento en
`ui/components/VehicleStatusIndicators.kt` — que es donde ya viven `DrivingRadarHalo`,
`rememberDrivingStatePulse` y `UnmarkedParkingIcon`. Un solo sitio, dos consumidores.

**Qué dibuja:** una ruta corta en S de origen a destino. El trazo se dibuja progresivamente de
punta a punta con un punto en la cabeza que avanza, sobre un "fantasma" del camino completo a alfa
baja para que la forma siga siendo legible en el frame 0 y en las previews estáticas. Al llegar al
final se reinicia.

**Por qué Canvas y no un vector Material:** ningún icono de Material expresa un trazo que se
*recorta* progresivamente, y un `VectorDrawable` tampoco. Mismo motivo por el que
`UnmarkedParkingIcon` ya es Canvas (un VectorDrawable no sabe hacer trazo discontinuo) — precedente
establecido en este mismo fichero. Se implementa con `Path` + `PathMeasure.getSegment()` en
commonMain.

**Color:** el glifo lleva la identidad del vehículo (`accent`), exactamente como el pin de aparcado
al que sustituye. Las palabras de estado siguen en `onSurface` con su pulso. El estado no tiñe
nada. [UI-COLOR-DOCTRINE-001]

**Dónde se enchufa** (`presentation/home/sections/sheet/components/HomeParkingRow.kt`):

- `HomeVehicleChip` → la rama `isDriving` pasa de `Text` suelto a `DrivingRouteGlyph` + `Text`,
  recuperando la anatomía icono+texto que ya tienen las otras dos ramas del pie.
- `HomeVehicleCard` → la caja tonal del pie deja de resolver `session != null || isDriving` al mismo
  `LocationOn`: conducir gana prioridad y pinta la ruta; el pin queda para el coche aparcado de
  verdad. Es el mismo orden de prioridad que ya usaba el texto de al lado.

## Criterio de éxito

- Con 2+ coches y uno conduciendo, el chip de ese coche muestra ruta animada + "En ruta"; los otros
  siguen con su pin + calle, sin cambio.
- Con 1 solo coche conduciendo, la card muestra la ruta animada en la caja tonal, **no** el pin.
- Ningún coche aparcado ni sin marcar cambia de aspecto.
- Sin strings nuevos: la copy "En ruta" / "Aparcando…" no se toca.
- `ColorGuardrailTest` y `TypographyGuardrailTest` (Konsist) siguen verdes; suite completa verde.

## Consumidores auditados

`grep` de todo el que pinta el estado de conducción de un vehículo:

| Sitio | Qué hace en ruta | Veredicto |
|---|---|---|
| `HomeParkingRow.HomeVehicleChip` | texto suelto | **cerrado** — gana el glifo |
| `HomeParkingRow.HomeVehicleCard` | pin de aparcado | **cerrado** — gana el glifo |
| `peek/BrowsePeek.kt` | pill "EN RUTA" en el peek, con su propio lenguaje | **exento** — otro
  componente, otra anatomía (pill, no fila icono+texto); fuera del alcance que pidió el user |
| `VehicleIdentityHeader` / `DrivingRadarHalo` | halo tras el glifo del coche | **exento** — es la
  fila de identidad, no el pie; sigue igual y no se duplica (el halo son anillos, la ruta es trazo) |
| `presentation/vehicles/*` (ficha del garaje) | no pinta fase de conducción en el pie | **exento** |

## Sistema de pruebas mock

- `HomeSheetPreviews.kt` ya tenía preview de conducción para **las dos** variantes (card en claro,
  chip en claro) — ambas pasan a mostrar el glifo sin tocar el fichero.
- `StateGalleryScreen.kt` tenía variante de 1 coche conduciendo, pero **ninguna de 2+ chips con uno
  en ruta**, que es justo el caso que el user pidió cubrir → se añade.

## Adjudicado en device: ¿también en el peek?

El peek de Browse también dice "EN RUTA" (`BrowsePeek.kt:105`), así que se evaluó meterle el mismo
glifo. **NO** — y el motivo no es de coste, es de anatomía:

- Ahí "EN RUTA" **no es una fila icono+texto**: es el *eyebrow*, una sola cadena en versalitas
  (`"TOYOTA COROLLA · EN RUTA"`) dentro de `PapListItem.overline`, con el nombre resaltado. No hay
  hueco de icono que rellenar; habría que abrir la API de `PapSheet` (usada por browse, parking,
  spot, add-parking, add-spot y zona) o meter un `InlineTextContent` de 1,8 dp de trazo dentro de
  un texto de ~11 sp, donde sería un borrón.
- El slot de icono del peek **ya está ocupado y bien**: el tile lead de 46 dp con el glifo del coche.
  En el chip el hueco estaba vacío o mentía; aquí no.
- Y sobre todo: el eyebrow **aparcado** dice "· APARCADO" y **tampoco lleva pin**. Poner el glifo
  sólo en la rama de conducción reintroduciría, del revés, justo la asimetría que este ticket quita
  del chip.

**Lo que sí aparece al mirarlo**: el peek en ruta no tiene NINGÚN movimiento — `PapSheetLead.Vehicle`
no recibe `isDriving`, así que no hay halo, y la palabra de fase no late. El mismo viaje se ve vivo
en el chip y muerto en el peek. El arreglo coherente no es la ruta, es el **halo en el tile lead** +
el pulso en la palabra de fase: la misma pareja que ya usa el chip (fila de identidad → halo; fila
de estado → ruta), aplicada a la anatomía que le toca. Queda fuera de este ticket porque toca la API
de `PapSheet`; si se hace, va en su propio ticket.

## Verificación

- `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` ✅
- `testProdDebugUnitTest` ✅ **1333 tests**, incluidos `ColorGuardrailTest`, `TypographyGuardrailTest`
  y `DividerGuardrailTest` (Konsist)
- ✅ `mockDebug` en los dos móviles (Oppo + Redmi), Dev Catalog → galería → la variante nueva. La
  animación avanza y el trazo se lee.
- Los dos números salen de **mirarlo en device**, no de la maqueta: el fantasma a 0,22 **desaparecía**
  sobre `surfaceContainerHigh` en oscuro (el glifo parpadeaba vacío una vez por ciclo) → 0,35; y el
  trazo a 1,6 dp pesaba menos que el pin macizo del chip aparcado de al lado → 1,8 dp.

## Follow-up abierto (fuera de alcance)

`docs/backlog/ui-peek-driving-has-no-motion-001.md` — el peek en ruta no tiene ningún movimiento.

## Notas

- Sin cambios de dominio, ni de detección, ni de strings: no toca `PARKING-DETECTION.md`.
- `DrivingRouteGlyph` reutiliza sus `Path`/`PathMeasure` entre frames (`remember` + `rewind`) — a
  60 fps un glifo de 15 dp no tiene por qué asignar memoria en cada dibujado.
