# UI-SPOT-AGE-PILL-IGNORES-THE-THEME-001 · la píldora de antigüedad se pinta como si siempre fuera de noche

**Estado:** ✅ **Done** — resuelto dentro de `UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001` (29-08).
Se aparcó unas horas por miedo a un conflicto que resultó no existir; al final darle a la píldora
sus tonos por tema fue el arreglo entero, sin rama propia. Los valores y su contraste viven en el
doc de aquel ticket.

## Problema

Las píldoras «Hace 1 min» / «Hace 2 min» de la lista de plazas se pintan con el par del tema
**oscuro** en los dos temas: cama verde muy oscura (`PapSpotFreshMuted` `#0F3B08`) con texto lima
brillante (`PapSpotFresh` `#8FE83C`). En tema claro son dos pastillas negruzcas sobre un sheet
blanco, y no se parecen a nada más de esa pantalla.

Visto en device (Oppo, mock, tema claro, 29-08) al revisar otra cosa.

## Causa

`ui/components/SpotIndicators.kt` elige color **sin mirar el tema**: no tiene la sonda
`isDark` / `SURFACE_DARK_LUMINANCE` que sí usan `SpotStateColors`, `VehicleIdentity`, `papCarBlue` y
el resto del sistema. Elige entre tres tokens fijos y todos son los del tema oscuro.

## Procedencia honesta

**El fallo estructural es anterior** a [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]: antes usaba
`PapGreenMuted` + `PapGreen`, exactamente la misma cama oscura sobre fondo claro. Ese ticket
sustituyó esos tokens por los de plaza (correcto: una plaza no se pinta con el verde de marca) pero
**heredó la ceguera al tema sin darse cuenta** — el barrido miró qué token se usaba, no si el
`when` era theme-aware. Sigue igual de roto, ahora en lima.

Lección: al cambiar QUÉ token lee un sitio, comprobar también **CÓMO** lo elige.

## Diseño

Una línea de sistema, no un parche: darle a `SpotIndicators` la misma sonda de luminancia que usa
todo lo demás, y una pierna clara para cada uno de los tres escalones. Los valores claros ya
existen — `PapSpotFreshLight` / `PapSpotCoolingLight` / `PapSpotExpiringLight` — y los contenedores
claros habrá que elegirlos (hoy sólo existen los oscuros `*Muted`).

Alternativa a considerar antes de implementar: si la píldora siempre debe leerse como un chip
tonal, quizá lo correcto es que consuma `stateColors()` como los demás, en vez de tener su propio
`when` — un resolver menos.

## Criterio de éxito

La píldora de antigüedad en tema claro usa tonos claros, con su texto ≥ 4.5:1 contra su propia cama,
y en oscuro no cambia nada.

## Consumidores auditados

Pendiente. Empezar por `SpotIndicators.kt` y por quién invoca `SpotAgeIndicator`.
