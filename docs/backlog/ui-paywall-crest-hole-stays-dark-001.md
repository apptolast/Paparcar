# UI-PAYWALL-CREST-HOLE-STAYS-DARK-001 · el agujero de la cresta no debe invertir por tema

**Estado:** ✅ Done · mergeada a master por squash

## Problema
`PapPremiumCrest` (`PapPremiumSurface.kt`) pinta el isotipo recortado del disco con
`MaterialTheme.colorScheme.surface` — un color que invierte por tema (claro → casi blanco, oscuro →
casi negro). En tema claro el recorte sale CLARO sobre el disco metálico verde, en vez de oscuro.

## Doctrina violada
`PremiumSweep`/`premiumInk`/`premiumEdge` documentan explícitamente que el metal de Plus es **el
mismo en ambos temas, a propósito** [UI-PLUS-THE-METAL-IS-AN-OBJECT-NOT-A-BACKGROUND-001]: es un
OBJETO que descansa sobre la página, no un fondo que deba seguir el tema. El `tint` del agujero de
`PapPremiumCrest` rompía esa regla al seguir `colorScheme.surface` en vez de un ink fijo.

## Señales / datos disponibles
Lectura del código: `PapPremiumCrest` (líneas 376-390 de `PapPremiumSurface.kt`) usa
`tint = MaterialTheme.colorScheme.surface`; el resto del sistema de metal (`premiumInk`,
`premiumEdge`) ya resuelve a un valor fijo o a una comprobación de luminancia explícita, nunca al
rol de tema crudo.

## Diseño
Cambiar el `tint` del recorte a `premiumInk` (= `PapInk`, oscuro fijo) — el mismo ink que ya usan
`PapPremiumButton`/`PapPremiumSurface` para el texto que vive SOBRE el metal. El disco es verde en
los dos temas; su recorte ahora también es oscuro en los dos temas. No se toca `PapPremiumSurface`,
`PapPremiumMark` ni el resto del acabado — es un cambio de un solo tint en un solo composable.

## Criterio de éxito
`PapPremiumCrest` renderiza con el isotipo recortado oscuro tanto en tema claro como en tema
oscuro. Verificado leyendo `premiumInk` (fijo, no depende de `MaterialTheme`) y comprobando en el
Dev Catalog / paywall real en los dos temas.

✅ Verificado en emulador (sin móviles físicos conectados esta sesión): flavor `mock`, variante
"Paywall · con planes" de la Galería de estados, capturas en tema claro y oscuro — el recorte sale
oscuro en los dos.

## Consumidores auditados
`grep -rn "PapPremiumCrest"` → un solo call site, `PaywallScreen.kt` (`PaywallHeader`). No hay otros
consumidores que dependan del tint anterior.
