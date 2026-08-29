# UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001 · La app decía hablar con una voz, y Material seguía con la vieja

**Estado:** ✅ **Done** (29-08) · rama `bugfix/UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001-md3-scale`

## Problema

Adoptada Plus Jakarta Sans en las tres voces [UI-TYPE-FAMILY-CANDIDATES-001], **dos sitios de
producción seguían pintando las familias retiradas**, y ninguno lo decía:

1. **`rememberAppTypography()`** — la escala MD3 completa seguía resuelta a Outfit + Inter, y
   `PaparcarTheme` se la pasa a `MaterialTheme`. La usa **todo componente de Material que no recibe
   un rol de Paparcar**: los labels del bottom nav (`Inicio` / `Vehículos` / `Ajustes`), los labels
   y placeholders de `OutlinedTextField` (los campos del login), cualquier `Button` cuyo `Text` no
   lleve `style`.
2. **`PaparcarMapMarkers`** ×4 — las etiquetas dibujadas en canvas resolvían `rememberOutfitFontFamily()`
   por su cuenta.

Lo destapó una pregunta del user: *"debería estar ya solamente Jakarta, ¿no?"*. La respuesta era que
no, y no había forma de saberlo mirando: son textos cortos, en familias de anchura parecida.

## Doctrina violada

- **`CLAUDE.md` § Tipografía** — el sistema promete que la familia es propiedad del ROL. Se cumplía
  para todo lo que pasa por `PaparcarType`, pero la escala que Material usa por debajo quedaba
  clavada a dos familias concretas. El sistema tenía una puerta trasera.
- **`feedback_systems_not_patches`** — cambiar de familia tocaba un sitio y dejaba otros dos
  sirviendo la anterior.

## Diseño

`rememberAppTypography(fonts: PapFontSet = defaultFontSet())`: la escala MD3 se sirve **del mismo
font set que los roles**, y `PaparcarTheme` le pasa el que ya resolvió. Los marcadores leen la
familia de marca del tema (`PaparcarType.current.cardTitle.fontFamily`) en lugar de construirla.

Con eso, cambiar de familia vuelve a ser un solo sitio — incluido lo que pinta Material.

## Criterio de éxito

1. ✅ `:shared:testDebugUnitTest`, `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin`.
2. ✅ **Medido, no supuesto**: el label `Vehículos` del bottom nav pasa de **149 px a 145 px** de
   ancho tras el cambio. Es poco porque Inter y Jakarta miden parecido a ese tamaño — que es
   justamente por lo que el fallo era invisible a ojo.
3. ✅ **Modo claro barrido en el Redmi**: Home, peek de zona aproximada, permisos y registro. Sin
   desbordes ni problemas de contraste.

## Consumidores auditados

`grep` de `rememberOutfitFontFamily|rememberInterFontFamily|rememberBarlowCondensedFontFamily` en
`commonMain`:
- `ui/theme/Typography.kt` — las tres factorías (definición) y `rememberAppTypography` ✅ arreglado.
- `ui/theme/PapFontSet.kt` — `legacyFontSet()` y la voz Cifra de `jakartaFontSet()`: legítimo, son
  los sets del laboratorio.
- `ui/components/PaparcarMapMarkers.kt` ×4 ✅ arreglado.
- Ningún otro sitio de producción resuelve una familia.

## Lo que sigue pendiente

- **Oppo** y los **9 idiomas** con las etiquetas de dos palabras de las stats.
- `UI-TYPE-RETIRE-THE-OLD-FAMILIES-001`: 2,14 MB de fuentes sin usar en el APK. Este ticket es
  requisito suyo — hasta ahora, borrar Outfit e Inter habría roto la escala MD3.
