# UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001 · la rampa de frescura se decide en un sitio, y la regla la vigila un test

**Estado:** ✅ **Done** — mergeado en master (30-08). Refactor sin delta visual: los seis pares de la
píldora son los mismos valores que traía `3d6e24cc`; lo que cambia es **dónde se deciden**.

Verificado: `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` ✅ ·
`:shared:testDebugUnitTest --rerun-tasks` ✅ (0 `<failure>` en toda la suite). El guardarraíl nuevo se
comprobó **por falsación** —vaciando la allowlist falla y nombra exactamente los tres exentos, y
`SpotIndicators` ya no está entre ellos—, porque un test de prohibición que no se ve fallar es un
test que siempre pasa.

## Problema

`UI-SPOT-AGE-PILL-IGNORES-THE-THEME-001` se cerró bien —la píldora «Hace N min» ya tiene sus seis
patas y las seis pasan 4.5:1 (claro 5.38 / 11.05 / 5.00, oscuro 8.34 / 6.82 / 5.14, medidas)— pero
dejó en pie **la estructura que produjo el bug**:

1. **La rampa tiene dos resolvers.** `SpotFreshness.stateColors()` vive en `ui/theme`, y
   `SpotIndicators.kt` se quedó con **su propia sonda de luminancia y dos `when` de tres ramas**,
   importando seis tokens de pata (`PapSpotFreshMuted`, `PapAmberMuted`, `PapRedMuted`,
   `Pap*ContainerLight`…) desde `ui/components`. Una decisión sobre la rampa viviendo donde el
   barrido de la rampa no mira es exactamente lo que dejó la píldora ciega al tema durante meses.
2. **`SpotPeekPalette` arrastra un campo muerto y otro que miente.** `badgeFg` no lo lee nadie, y
   `badgeBg` es en realidad `sc.text` —un color de TEXTO— que pintan el eyebrow, `DistanceRow` y
   `SpotEnRouteRow`. Los nombres son de cuando aquello era un badge relleno. Ya estaba anotado como
   follow-up consciente en `Color.kt:132`.
3. **Nada lo vigila.** `ColorGuardrailTest` comprueba VALORES (un hex una historia, ΔE por hue,
   `tertiary` retirado, literales `Color(0x…)`), nunca **quién lee qué pata**. La lección del ticket
   anterior —«al cambiar QUÉ token lee un sitio, mirar también CÓMO lo elige»— vive solo en prosa,
   que es como vivía la regla «un hex una historia» hasta que cuatro pares de tokens colisionaron.

## Doctrina violada

- **Sistemas, no parches**: el invariante «la rampa se decide en un sitio» estaba escrito en
  `PapColor.kt:34` («Spot freshness → `stateColors()` en `SpotStateColors.kt`») y la píldora lo
  incumplía sin que nada lo notara.
- **`UI-COLOR-DOCTRINE-001`**: el feature layer pide color por ROL. Una pata (`*Muted` / `*Light`) no
  es un rol: es media historia, y elegir entre dos patas a mano es decidir el tema a mano.

## Señales / datos disponibles

- Consumidores de `stateColors()` en todo el repo: `ReliabilityMeter` (`.bg`) y `PeekShared`
  (`.text` y `.on`). Nada más.
- Ficheros de `presentation/` + `ui/components/` que importan una pata hoy: `SpotIndicators.kt`
  (este ticket), `PaparcarMapMarkers.kt` + `PaparcarMapView.kt` (paletas fijas SOBRE TILES, exentas
  por doctrina) y `SettingsScreen.kt` (las muestras de tema, que enseñan el tema CONTRARIO a
  propósito — `UI-THEME-OPTION-SHOWS-ITS-THEME-001`).

## Diseño

**Un resolver, cuatro campos, todos vivos.**

- `SpotStateColors` pasa a `(bg, text, container, onContainer)`: el par de RELLENO, el leg de TEXTO,
  y el par TONAL de la píldora. La píldora deja de tener sonda y `when` propios.
- Se retira `on`: su único lector era `badgeFg`. Si algún día se hace el badge relleno que
  `Color.kt` menciona como follow-up, vuelve en una línea; conservarlo «por si acaso» es la misma
  enfermedad que este ticket persigue, con mejores modales.
- `SpotPeekPalette` pasa a `(accent, label)` — el nombre dice el trabajo: un color que acentúa
  TEXTO, no el fondo de un badge que ya no existe.
- **Guardarraíl nuevo**: `feature code reads roles, never theme legs`. Cualquier fichero de
  `presentation/` o `ui/components/` que referencie `ui.theme.Pap*<Muted|Light|Dark>` falla, salvo
  la allowlist razonada (mapa sobre tiles + muestras de tema). Es la lección del ticket anterior
  convertida en test: un barrido por nombre de token no caza un `when` ciego, pero **prohibir la
  pata sí**, porque un `when` ciego necesita nombrar las dos patas para existir.

## Criterio de éxito

- `SpotIndicators.kt` no importa ni una pata ni consulta la luminancia; la píldora se ve igual en
  ambos temas que en `3d6e24cc` (mismos seis pares de valores).
- `SpotStateColors` y `SpotPeekPalette` no tienen ningún campo sin lector.
- `ColorGuardrailTest` verde, y en rojo si alguien vuelve a importar una pata en el feature layer.

## Consumidores auditados

| Sitio | Qué hacía | Resolución |
|---|---|---|
| `SpotIndicators.SpotAgeIndicator` | sonda + 2 `when` propios, 6 patas importadas | ✅ cerrado — lee `container`/`onContainer` |
| `PeekShared.peekPalette` | `badgeBg = sc.text`, `badgeFg = sc.on` (muerto) | ✅ cerrado — `accent` + `label` |
| `SpotPeek` (eyebrow, `DistanceRow`, `SpotEnRouteRow`) | leían `palette.badgeBg` | ✅ cerrado — `palette.accent` |
| `ReliabilityMeter` | `stateColors().bg` | ✅ intacto — el relleno sigue siendo relleno |
| `SpotPuckIcon` / marcadores | `SpotPalette` fija sobre tiles, no la rampa del tema | ⚪ exento — no pinta sobre nuestra superficie |
| `SettingsScreen` muestras de tema | `PapCardLight` + `PapInk` a la vez | ⚪ exento — enseñar el tema contrario es su trabajo |
| `Color.kt` comentario del follow-up | citaba `badgeBg`/`badgeFg` | ✅ reescrito con los nombres vivos |
