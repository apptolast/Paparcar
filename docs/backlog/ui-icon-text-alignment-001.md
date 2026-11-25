# UI-ICON-TEXT-ALIGNMENT-001 · Centrar el texto de una línea con su icono

**Estado:** ✅ Done · 2026-11-25

## Problema

Una etiqueta que cabe en una línea queda arriba de su icono en las filas del paywall y en otras filas que se alinean arriba para admitir texto largo. En Home algunas filas eligen la alineación según el máximo de líneas permitido, aunque el texto quepa en una.

## Diseño y criterio de éxito

La fila usa el resultado de medir el texto con el espacio disponible. Una línea comparte el centro vertical con el icono. Si el texto ocupa varias líneas, el bloque conserva su alineación superior y el icono mantiene el desplazamiento que ya tuviera. Una segunda pieza de texto debajo del título conserva la alineación superior del bloque. Los límites de líneas, la elipsis y las acciones de las filas siguen su contrato actual.

## Consumidores auditados

- Paywall, bienvenida de Plus y aviso de fin de promoción comparten las filas de beneficios. El motivo del paywall y el aviso del plan gratuito siguen la misma regla.
- Los banners informativos de las hojas y las filas de metadatos de Home, los peeks y la ficha del vehículo admiten texto que puede envolver.
- `PapListItem` y las filas ya centradas satisfacen el caso de una línea. El consentimiento y el explicador de tamaños siempre presentan título y descripción. La ficha de identidad del vehículo ya centra cuando no muestra metadatos debajo del nombre.

## Validación

La prueba de Compose en Robolectric mide la misma etiqueta con dos anchos y comprueba el centrado y la alineación superior. El paywall en español se comprobó en el emulador Pixel 8 con `mockDebug`, con filas de una y de dos líneas. Hay vistas previas de los beneficios en español y en alemán con letra ampliada.
