# UI-PEEK-DRIVING-HAS-NO-MOTION-001 · el mismo viaje se ve vivo en el chip y muerto en el peek

**Estado:** 🔵 Abierto — sin rama. Detectado al cerrar [UI-CHIP-ROUTE-GLYPH-001].

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

1. `PapSheetLead.Vehicle` gana un `isDriving: Boolean = false` → `PapSheetLeadTile` pinta
   `DrivingRadarHalo` tras el pictograma, exactamente como la fila de identidad del chip.
2. La palabra de fase del eyebrow late con `rememberDrivingStatePulse()`. Ojo: hoy el eyebrow es UN
   `Text` con `overlineHighlight` para teñir el nombre; el pulso hay que aplicarlo sólo al tramo de
   la fase, así que probablemente exija que `PapListItem` acepte alfa por tramo — o partir el
   eyebrow en dos spans de `AnnotatedString` con alfas distintas.

Coste real: toca la API de `PapSheet`, que usan browse, parking seleccionado, spot seleccionado,
add-parking, add-spot y zona. Por eso NO entró en el ticket del glifo.

## Criterio de éxito

- Conduciendo, el tile lead del peek respira con el mismo halo que el chip.
- Las palabras "EN RUTA" / "APARCANDO…" del eyebrow laten; el nombre del coche NO (sigue en su color
  de identidad, fijo).
- Ningún otro estado del peek (aparcado, spot, zona, add-*) cambia de aspecto ni de altura — la
  altura del header está reservada y el divisor del peek depende de ella. [BUG-PEEK-DIVIDER-ALIGN]
- Variante en la galería mock para el peek en ruta (ya existe "Peek · en ruta gana al aparcado" →
  sirve de banco de pruebas).

## Consumidores a auditar cuando se haga

Todos los `PapSheet(...)` con `lead = PapSheetLead.Vehicle(...)`: `BrowsePeek` (rama driving y rama
parked), y las hojas de parking seleccionado / edit parking. Sólo la rama driving debe animarse.
