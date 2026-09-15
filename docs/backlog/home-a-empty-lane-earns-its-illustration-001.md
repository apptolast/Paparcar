# HOME-A-EMPTY-LANE-EARNS-ITS-ILLUSTRATION-001 · las tres lanes vacías comparten ilustración

**Estado:** ✅ Done · rama `feature/HOME-A-EMPTY-LANE-EARNS-ITS-ILLUSTRATION-001-parkings-spots` · worktree `../Paparcar-empty-illustrations`

## Problema
El carril de gasolineras ya tenía ilustración propia (`EmptyFuelStationsIllustration`) para «No hay
gasolineras a menos de 5 km.», pero parkings (`HomeParkingsLane`) solo mostraba texto (`LaneNote`) y
spots (`HomeSpotRows.HomeEmptySpots`) también, pese a que `EmptySpotsIllustration` ya existía en
`PaparcarIllustrations.kt` como código muerto — se había quitado deliberadamente el 10-09
([UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]) junto con un bloque pesado (borde discontinuo +
título + subtítulo + CTA propio).

## Doctrina violada
Ninguna nueva; se respeta [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001] (una línea, no una caja) al
reintroducir la ilustración de spots: solo imagen + caption, sin borde/título/CTA — el mismo
esqueleto mínimo que ya usaba gasolina, no el bloque que se quitó.

## Diseño
- **Parkings** — nueva `EmptyParkingsIllustration` en `ui/illustrations/PaparcarIllustrations.kt`:
  edificio de parking (tejado + fachada con dos bandas + entrada) + barrera de acceso diagonal en
  verde de marca + el mismo anillo punteado de radio buscado + coche en miniatura, mismo viewBox
  140×120 y mismos helpers (`drawShadowEllipse`, `miniCar`, `viewBox`) que sus hermanas. Envuelta en
  `HomeParkingsLane.kt` por un nuevo `EmptyParkings()` privado, gemelo de `EmptyFuelStations()`, con
  las mismas dimensiones **112×96dp**.
- **Spots** — `HomeSpotRows.HomeEmptySpots` ahora ramifica: `unasked=true` (nadie ha preguntado
  todavía — no es un veredicto sobre el mundo) sigue en texto solo, igual que las frases-que-no-son-
  veredicto de gasolina/parkings (`tooFar`, `missingFuelType`); el veredicto real de "vacío" muestra
  `EmptySpotsIllustration` ya existente, con las mismas **112×96dp**. Limpiadas las constantes
  muertas (`EMPTY_ICON_ALPHA`, `EMPTY_SUBTITLE_ALPHA`, `EMPTY_DASH_*`, y el tamaño 180×154 que nunca
  se aplicaba) que eran remanente del bloque pesado retirado.
- Las tres lanes comparten ahora el mismo footprint visual: ilustración 112×96dp + caption, sin
  reintroducir el bloque con borde/título/CTA que la doctrina prohíbe.

## Criterio de éxito
- Verificado en device (Redmi, Dev Catalog mock, tema oscuro): las tres ilustraciones renderizan
  correctamente con sus textos («No hay gasolineras…», «No hay parkings públicos…», «Aún no hay
  plazas reportadas»).
- `:shared:testDebugUnitTest` verde (incluye guardrails Konsist de tipografía/imports).
- `:app:compileMockDebugKotlin` y `:app:compileProdDebugKotlin` verdes.

## Consumidores auditados
- `HomeEmptySpots(unasked = ...)` — único call site en `HomeSheetContent.kt:581`, firma sin cambios.
- `EMPTY_ILLUSTRATION_W/H` en `HomeSpotRows.kt` — solo usadas dentro del propio fichero, sin otros
  consumidores (confirmado por grep antes de tocarlas).
- No hay `*Previews.kt` dedicados a `HomeParkingsLane`/`HomeFuelLane` a nivel de componente; los
  existentes de `HomeEmptySpots` (`HomeSheetPreviews.kt`) siguen funcionando sin cambios porque
  llaman al composable sin pasar `unasked` (por tanto ejercitan la rama con ilustración).
