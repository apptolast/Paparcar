# UI-ICON-TILE-BORDER-001 · Contorno de los iconos sobre fondo neutro

**Estado:** ✅ Implementado en master

## Problema
En las capturas de Home, el fondo del recuadro de los iconos se confunde con la superficie de la tarjeta.

## Diseño
Reutilizar `outlineSubtle` de `ui/theme/Borders.kt`. El contorno de 1 dp sigue la forma del recuadro y toma su gris del esquema activo.

## Consumidores y criterio de éxito
Distinguir los recuadros en claro y oscuro en las tarjetas de plazas, parkings y gasolineras de Ahora, la lista de gasolineras y el recuadro de identidad del vehículo en Home y Vehículos. La revisión en el Pixel 8 mostró otro recuadro neutro para el vehículo en la cabecera del mapa (`PapSheet`). La cabecera y Vehículos comparten el recuadro de 56 dp con borde `outlineSubtle` y 4 dp de margen interior. El margen limita también la anchura de la ilustración, cuyo dibujo es más ancho que su altura nominal. Los parkings de la lista con fondo azul y los marcadores sin recuadro ya tienen su propio tratamiento visual.

## Validación
`git diff --check` sin incidencias. Compilaron `:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin` excluyendo los dos pasos `process*GoogleServices` porque falta `google-services.json` en el worktree. Tras el ajuste de tamaño, pasaron `:app:assembleMockDebug` y `:app:compileProdDebugKotlin` con las mismas exclusiones. APK mock instalado en el Pixel 8 y comprobada en claro la cabecera redimensionada. Pendiente la comprobación visual en oscuro.
