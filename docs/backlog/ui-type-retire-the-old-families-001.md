# UI-TYPE-RETIRE-THE-OLD-FAMILIES-001 · El APK carga cinco familias para usar una

**Estado:** 🟡 Abierto, sin rama · follow-up de `UI-TYPE-FAMILY-CANDIDATES-001` (master `e819c3ff`)

## Problema

Adoptada Plus Jakarta Sans en las tres voces, `composeResources/font/` sigue conteniendo:

| Fichero | Peso | ¿Se usa en producción? |
|---|---|---|
| `plus_jakarta_sans_variable.ttf` | 0,17 MB | **Sí** — las tres voces |
| `outfit_*.ttf` (5) | 0,26 MB | No |
| `inter_variable.ttf` | 0,84 MB | No |
| `barlow_condensed_*.ttf` (4) | 0,41 MB | No |
| `archivo_variable.ttf` | 0,63 MB | No |

**2,14 MB de fuentes que nadie pinta**, embarcadas en cada instalación. Compose Resources no
distingue por flavor, así que lo que usa el laboratorio del `mock` viaja también en `prod`.

Siguen ahí a propósito: son lo que el selector del Dev Catalog compara, y mientras la adopción no
esté confirmada en más pantallas conviene poder volver atrás en el device sin recompilar.

## Qué hay que hacer, cuando la adopción se dé por buena

1. Borrar los `.ttf` que no se usen y sus `rememberXxxFontFamily()` en `ui/theme/Typography.kt`.
2. Retirar del `mock`: `DevFontChoice`, el selector de `DevCatalogScreen`, el provider de `DevRoot`,
   `TypographyCandidateFamilies.kt` y el bloque de candidatas de `TypographyLabScreen`.
3. Decidir qué queda de `PapFontSet`: con una sola familia el override deja de tener consumidores,
   pero **`figureCapHeightEm` / `figureAscentEm` / `figureDescentEm` se quedan** — son lo que
   mantiene centrados el icono de las stats y el contador del sheet, y su valor depende de la
   fuente. No volver a cablearlos en una pantalla.
4. `rememberAppTypography()` (la escala MD3) también apunta a Outfit e Inter: hay que repasarla o
   Material seguirá pidiendo fuentes que ya no están.

## Antes de cerrar la adopción

- Ver en device **onboarding, permisos, registro de vehículo y los peeks del mapa**, que no se han
  mirado con la familia nueva.
- **Modo claro** y **Oppo**.
- Los 9 idiomas con las etiquetas de dos palabras: alemán (`Sitzungen gesamt`) y neerlandés
  (`plekken gedeeld`) son los candidatos a desbordar una celda de un tercio de card.
