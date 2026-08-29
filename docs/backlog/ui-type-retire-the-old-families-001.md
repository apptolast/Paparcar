# UI-TYPE-RETIRE-THE-OLD-FAMILIES-001 · El APK carga cinco familias para usar una

**Estado:** ✅ **Done** (29-08) · rama `chore/UI-TYPE-RETIRE-THE-OLD-FAMILIES-001-one-font`

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


---

## Cómo quedó

**2,30 MB → 0,17 MB** de fuentes en el APK. Borrados los 5 Outfit, Inter, los 4 Barlow y Archivo;
queda `plus_jakarta_sans_variable.ttf`.

Lo que se fue con ellos:
- Las tres factorías `rememberXxxFontFamily()` de `Typography.kt`, y sus imports de recurso.
- `legacyFontSet` / `jakartaFontSet` / `archivoFontSet`, y el override `LocalPapFontSet` — sin
  candidatas que comparar, nadie lo proveía.
- Del `mock`: `DevFontChoice`, `TypographyCandidateFamilies.kt`, `TypographyLabScreen.kt`, el
  selector del catálogo y el provider de `DevRoot`. El laboratorio cumplió su función; su historia
  vive en los docs y en git.

Lo que se queda, como estaba previsto:
- **`figureCapHeightEm` / `figureAscentEm` / `figureDescentEm`.** Son lo que mantiene centrados el
  icono de las stats y el contador del sheet, y dependen de la fuente. No vuelven a una pantalla.
- `PapFontSet` como estructura: sigue diciendo qué letra pone cada voz, aunque hoy las tres apunten
  a la misma. Cambiar de familia sigue siendo un cambio de un sitio.

Dos cosas que salieron al limpiar:
- **`figureOpticalLiftSp` no tenía consumidores**: escribí el helper y luego repetí la fórmula a mano
  en `PapSheet`. Ahora el sheet lo usa, así que la fórmula vive en un sitio.
- **La allowlist de familias del guardrail queda VACÍA.** `PaparcarMapMarkers` era su única entrada
  y ya lee la familia del tema, así que la regla se queda sin puerta de salida.

## Verificado

1. ✅ `:shared:testDebugUnitTest`, `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin`.
2. ✅ `grep` de las familias viejas y sus ficheros en `shared/src` + `app/src` → **cero**.
3. ✅ En el Redmi: Home, peek, Ajustes (dos scrolls), Vehículos e historial. Nada sin fuente, nada
   en la del sistema.
