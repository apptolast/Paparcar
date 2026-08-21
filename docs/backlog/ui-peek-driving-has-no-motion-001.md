# UI-PEEK-DRIVING-HAS-NO-MOTION-001 · el mismo viaje se ve vivo en el chip y muerto en el peek

**Estado:** ✅ Done — en master (squash 22-08-2026). Detectado al cerrar [UI-CHIP-ROUTE-GLYPH-001].

## Problema

Con un viaje en curso, Home enseña el MISMO hecho en dos superficies a la vez:

- **Chip / card del vehículo** (`HomeParkingRow.kt`): halo radar tras el glifo del coche + el glifo
  de ruta dibujándose + las palabras de estado latiendo. Se ve vivo.
- **Peek de Browse** (`BrowsePeek.kt:98-117`): el eyebrow dice `"TOYOTA COROLLA · EN RUTA"` y **no se
  mueve nada**. `PapSheetLead.Vehicle` no recibe `isDriving`, así que el tile lead no tiene halo, y
  la palabra de fase no late.

Un dedo por encima del chip, el mismo viaje parece parado.

## Doctrina violada

`UI-COLOR-DOCTRINE-001` — *"el estado nunca tiñe; un viaje en curso se nota por MOVIMIENTO"*. Si el
movimiento es lo único que distingue "en ruta" de "aparcado", una superficie sin movimiento no está
contando el estado: lo está escribiendo y ya.

## Diseño propuesto

**No** el glifo de ruta — ya se adjudicó que no encaja ahí y por qué (ver
`ui-chip-route-glyph-001.md` §"¿también en el peek?"): el eyebrow es una cadena en versalitas sin
hueco de icono, y el eyebrow *aparcado* tampoco lleva pin.

Lo que toca es la pareja que ya usa el chip, aplicada a la anatomía del peek:

1. `PapSheetLead.Vehicle` gana **`drivingHaloColor: Color? = null`** → `PapSheetLeadTile` pinta
   `DrivingRadarHalo` tras el pictograma, exactamente como la fila de identidad del chip y con la
   misma proporción glifo↔halo (38 dp sobre tile de 46). No es un `isDriving: Boolean`: el tile
   necesitaría entonces re-derivar el color del método, y el doctrina exige **un solo resolver**
   (`vehicleIdentityColor`), que ya se ejecuta en el call site para el highlight del eyebrow. Pasar
   el color resuelto dice a la vez "está vivo" y "de qué color", sin duplicar la regla.
2. La palabra de fase del eyebrow late con `rememberDrivingStatePulse()`.

**Corrección al plan original**: se estimó que (2) exigiría alfa por tramo en `PapListItem` o
partir el eyebrow en dos spans. **No hace falta nada de eso.** El eyebrow ya pinta el nombre con un
span de color OPACO (`overlineHighlightColor`) sobre un color base (`overlineColor`); basta con
aplicar el pulso al **color base** y el span del nombre lo sobrescribe. El resultado es justo el
pedido: laten "· EN RUTA" / "· APARCANDO…", el nombre no parpadea. Cero cambios en `PapListItem`.

Coste real, ya medido: **una** propiedad nueva con default en `PapSheetLead.Vehicle` y dos líneas
en `BrowsePeek`. Los otros 3 call sites de `Vehicle` (`AddingParkingPeek`, `ParkingPeek`, y la rama
*aparcado* del propio `BrowsePeek`) se quedan con el default → siguen quietos.

## Criterio de éxito

- Conduciendo, el tile lead del peek respira con el mismo halo que el chip.
- Las palabras "EN RUTA" / "APARCANDO…" del eyebrow laten; el nombre del coche NO (sigue en su color
  de identidad, fijo).
- Ningún otro estado del peek (aparcado, spot, zona, add-*) cambia de aspecto ni de altura — la
  altura del header está reservada y el divisor del peek depende de ella. [BUG-PEEK-DIVIDER-ALIGN]
- Variante en la galería mock para el peek en ruta (ya existe "Peek · en ruta gana al aparcado" →
  sirve de banco de pruebas).

## Consumidores auditados

`grep "PapSheetLead.Vehicle("` → 4 call sites:

| Call site | Estado | Veredicto |
|---|---|---|
| `BrowsePeek` rama **driving** | viaje en curso | **cerrado** — recibe `drivingHaloColor` + pulso |
| `BrowsePeek` rama **parked** | coche aparcado | **exento** — default null, sigue quieto |
| `ParkingPeek` | parking seleccionado | **exento** — default null |
| `AddingParkingPeek` | marcando plaza | **exento** — default null |

## Verificación

- `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` ✅
- Altura del header intacta: el halo vive DENTRO del `Box` del glifo (38 dp), el tile sigue midiendo
  46 dp y `papSheetHeaderReservedHeight()` no cambia → el divisor del peek no se mueve.
  [BUG-PEEK-DIVIDER-ALIGN]
- Banco de pruebas: galería mock `Peek · en ruta gana al aparcado` (ya existía) + preview nueva
  `HomePeekHandle: en ruta (halo + pulso, oscuro)`.
