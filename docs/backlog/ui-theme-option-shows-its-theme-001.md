# UI-THEME-OPTION-SHOWS-ITS-THEME-001 · Cada opción de Tema enseña el color del tema que elige

**Estado:** ✅ Done · en master (squash 29-08-2026)

## Problema

En Ajustes → Apariencia, la fila de Tema es un `SettingsSegmentedRow` de tres segmentos
(Claro / Oscuro / Sistema). El único elemento gráfico es el **check** de M3, que aparece
únicamente en el segmento ya seleccionado: es decir, el widget solo sabe decir *"éste"*, y
nunca dice *cómo se va a ver la app* si tocas cualquiera de los otros dos.

Un selector de tema es de los pocos ajustes donde el valor **tiene color propio**: claro es
blanco, oscuro es la tinta de la app, sistema es los dos. Esa información estaba tirada.

## Doctrina violada

Ninguna se rompía — el check de M3 es correcto, solo es pobre. Sí hay doctrina que **acota** la
solución: [UI-COLOR-DOCTRINE-001] prohíbe `Color(0x…)` literal en `presentation/`
(`ColorGuardrailTest`), así que el swatch no puede inventar valores: pinta con los tokens que ya
son *el* blanco de la carta clara y *la* tinta base oscura.

## Señales / datos disponibles

- `ThemeMode` (`domain/preferences/ThemeMode.kt`): `LIGHT`, `DARK`, `SYSTEM`.
- `SettingsSegmentedRow<K>` ya es genérico y lo comparten dos filas: Tema (3 opciones) y
  Unidades (métrico / imperial).
- Tokens existentes: `PapCardLight` (#FFFFFF, superficie de card del tema claro) y `PapInk`
  (#0D1117, `surface` del tema oscuro). Son literalmente las superficies que el usuario verá.

## Diseño

**El slot del icono lo ocupa el COLOR de la opción, no la confirmación de la elegida.**

- El segmento seleccionado ya se lee sin ambigüedad por la receta de la casa (relleno
  `primaryContainer` + borde y texto `primary`), así que el check era redundante: gastaba el
  único hueco gráfico de la fila en repetir lo que el relleno ya dice.
- En su lugar, `ThemeModeSwatch`: círculo con anillo `outline` (para que el blanco sobreviva
  sobre la card blanca y la tinta sobre la superficie oscura) relleno con
  `LIGHT → PapCardLight`, `DARK → PapInk`, `SYSTEM →` mitad y mitad.
- El swatch se ve en **las tres** opciones, no solo en la activa: el objetivo es comparar antes
  de tocar.
- La generalidad se resuelve en el sitio donde vive el widget: `SettingsSegmentedRow` gana un
  parámetro opcional `optionIcon: @Composable ((K) -> Unit)?`. Si no se pasa (fila de Unidades),
  el comportamiento es exactamente el de antes — `SegmentedButtonDefaults.Icon(selected)`. No hay
  fila copiada ni composable paralelo.
- Sin tokens nuevos ⇒ sin fila nueva en `COLOR-SYSTEM.md`; sí queda registrada la decisión de
  que estos dos tokens ganan un segundo uso legítimo (muestrario del tema) en §8.
- Sin strings nuevos: `settings_theme_mode_light/dark/system` ya están en los 9 locales, y el
  swatch es decorativo (`contentDescription` nulo — el label ya nombra la opción).

## Criterio de éxito

- Las tres opciones de Tema muestran su círculo de color; ninguna muestra check.
- La fila de Unidades sigue con el check de M3, sin tocarla.
- El círculo blanco es visible sobre el tema claro y el negro sobre el oscuro (anillo).
- `assembleMockDebug` + `compileProdDebugKotlinAndroid` verdes y `ColorGuardrailTest` en verde
  (nada de `Color(0x…)` en presentación).

## Verificación hecha

- `:composeApp:compileProdDebugKotlinAndroid` + `:composeApp:compileMockDebugKotlinAndroid` ✅
- `:composeApp:testProdDebugUnitTest` completo ✅ (incluye `ColorGuardrailTest`,
  `TypographyGuardrailTest`, `DividerGuardrailTest`)
- ✅ **Visto en el Redmi (2201117TY)** con `assembleMockDebug` (sha256 verificado en device), tema
  oscuro y tema claro, en la app real del mock y en la galería de estados:
  - los tres círculos se distinguen en ambos temas; el anillo hace su trabajo (el blanco sobre la
    card blanca del tema claro, la tinta sobre la superficie oscura);
  - la fila de **Unidades sigue con el check** de M3, sin tocar — se ve en la misma captura;
  - en ES el segmento «Sistema» usa ~180 de sus ~250 unidades de ancho: sobra ~30 % .
- ⏳ **Ancho en PL/RO no medido**: el override de idioma del mock no se aplica en caliente ni
  sobrevive al reinicio (el fake de preferencias es en memoria), así que «Systemowy» / «Întunecat»
  no se pudieron pintar. Por el margen medido en ES deberían caber (+2 caracteres), pero es
  cálculo, no medición.

## Consumidores auditados

`grep -n "SettingsSegmentedRow"` → 2 call sites, ambos en `SettingsScreen.kt`:

| Call site | Trato |
|---|---|
| Fila Tema (Apariencia) | recibe `optionIcon` con el swatch → **cubierto** |
| Fila Unidades (Mapa) | no lo pasa → default M3 intacto → **exento** |

`StateGalleryScreen` / `SettingsPreviews` renderizan `SettingsContent`, así que heredan el
cambio sin tocar nada: no hay pantalla, estado ni routing nuevo.
