# UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001 · seis maneras de dibujar una caja, y un verde haciendo siete trabajos

**Estado:** ✅ Done — en master, en el MISMO commit que
`HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001` (el hash vive en `MEMORY.md`).

## Problema

El user, viendo el sheet en device: *"demasiados tipos de componentes distintos y colores"*. Correcto,
pero la causa no estaba donde parecía. Contado sobre los ficheros de `sections/sheet/`:

| Bloque | `Surface` crudos | bordes propios | `PapListItem` | veredicto |
|---|---|---|---|---|
| `HomeDetectionSurface` | 2 | 2 | 0 | anatomía propia |
| `HomeParkingRow` (card de vehículo) | 2 | 2 | 0 | anatomía propia |
| `HomeSpotRows` | 1 | 0 | 4 | mezcla |
| `HomeFuelStationRows` | 0 | 0 | 2 | canónico |
| `HomePublicParkingRows` | 0 | 0 | 2 | canónico |

**Los dos bloques MÁS prominentes eran justo los dos que no usaban el esqueleto que `CLAUDE.md`
declara obligatorio** (`PapListItem` en `PapOutlinedCard` con `PapIconTile`, [UI-LIST-ITEM-001]).
Los carriles recién hechos sí. La regla existía y funcionaba; faltaba aplicarla hacia arriba.

**El color no era un problema de paleta.** Comprobado en `PapColor.kt`: `action`, `selected`,
`brandData` y `actionText` resuelven **todos** a `colorScheme.primary`. No había cuatro verdes —
había uno repartido en siete papeles a la vez en la misma pantalla: CTA relleno, píldora, nombre del
coche, pestaña activa, icono de chip, cifra de precio y barra de acento. Cuando todo lo importante es
verde, el verde deja de dirigir la mirada y **el CTA principal desaparece entre sus imitaciones**.

Y `PapShapes` declaraba **siete radios**, tres de ellos (14 · 16 · 18) a 2dp unos de otros: nadie los
distingue, pero cada componente nuevo tenía que elegir, y esa moneda al aire se lee después como
inconsistencia.

## Doctrina violada

- [UI-LIST-ITEM-001] — un solo esqueleto de fila. Lo incumplían los dos bloques principales.
- [UI-COLOR-DOCTRINE-001] — verde = marca y acción, e identidad de coche vigilado. Los otros cinco
  usos lo habían cogido prestado.
- **Sistemas, no parches** — cada contenedor se justificó solo al entrar; nadie miró el conjunto.

## Diseño

1. **Una fila.** `HomeDetectionSurface` pasa a `PapOutlinedCard` + `PapListItem` + `PapIconTile`.
2. **El énfasis es una VARIABLE, no un contenedor.** Dos niveles: teñido (pide acción ahora) y
   neutro (cuenta algo). **Se retira la barra de acento de 4dp** — no decía "importante", decía
   "esta la escribimos en agosto".
3. **Presupuesto de verde.** `PapIconTile` pasa a ser **neutro por defecto** (era
   `primaryContainer` + `primary`, así que todo el que usaba el tile canónico salía verde); los
   iconos leading de `PaparcarFilterChip` heredan el color del chip en vez de ir siempre en verde;
   la pestaña activa se dice con **etiqueta `onSurface` + indicador verde**, no con todo verde.
4. **El ámbar solo caduca.** El contador de zona a 0 era ámbar: una alerta sobre un hecho tranquilo
   del que el usuario no tiene culpa ni remedio. Pasa a neutro; el ámbar queda para la rampa de
   frescura de plazas.
5. **Cuatro radios**: `row 12` · `card 16` · `sheet 20` · `chip 999`. Se retiran `cardSmall`,
   `cardLarge` y `button`, con barrido de sus 12 usos.
6. **El estado vacío deja de ser una caja.** El bloque con borde discontinuo + ilustración + título +
   subtítulo + CTA propio (~150 dp) pasa a **una línea**. El CTA de avisar, que vivía dentro de esa
   caja y por eso se suprimía la tarjeta de abajo, ahora se ofrece **siempre** desde esa tarjeta —
   antes desaparecía justo cuando más falta hacía.

## Lo que la auditoría descubrió que estaba MAL en mi propia propuesta

⛔ **La decisión más visible que propuse —"el nombre del coche deja de ser verde"— no hacía falta:
el nombre ya iba en `onSurface`.** Lo verde junto al nombre es el GLIFO de vigilancia, que es
exactamente donde la doctrina lo quiere. Me lo hizo creer un comentario en
`VehicleIdentityHeader` que decía *"the identity colour paints the NAME itself"* y que había
sobrevivido al cambio que lo desmintió, 50 líneas por encima del código que pinta el nombre neutro.
Corregido el comentario, que era el bug real. **Un comentario que miente cuesta una auditoría entera.**

## Criterio de éxito

- Un solo lenguaje de tarjeta en el sheet + filas. ✅ visto en device (Pixel 8 Pro, 10-09).
- Como mucho un verde RELLENO por pantalla, y que sea la acción. ✅
- Suite en verde con `--rerun-tasks` (los guardarraíles Konsist no corren si no se fuerza). ✅

## Consumidores auditados

| Sitio | Cambio | Estado |
|---|---|---|
| `PapShapes` + 12 usos | 7 radios → 4 | ✅ |
| `PapIconTile` (18 call sites) | default neutro | ✅ por defecto, sin tocar los call sites |
| `PaparcarFilterChip` | icono leading hereda el color del chip | ✅ |
| `HomeDetectionSurface` | esqueleto canónico, sin barra de acento | ✅ |
| `PapSheet` · contador de zona | ámbar → neutro cuando es 0 | ✅ |
| `HomeNearbyLensRow` | etiqueta `onSurface`, indicador verde | ✅ |
| `HomeSpotRows` · vacíos | caja discontinua → línea | ✅ |
| `HomeSheetContent` | el CTA de avisar ya no se suprime en vacío | ✅ |
| `HomeSheetPreviews` | firma de `HomeEmptySpots` | ✅ |
| `VehicleIdentityHeader` | comentario que mentía | ✅ |

## Pendiente

- `PapEmptyStateCard` sigue existiendo para otras pantallas: decidir si se unifica con la línea o se
  queda para vacíos de pantalla completa (aquí ya no se usa).
- Barrer el mismo criterio fuera de Home (Ajustes y la ficha de Vehículos usan tiles que ahora salen
  neutros por defecto — verificar que ninguno perdió un verde que SÍ significaba acción).
