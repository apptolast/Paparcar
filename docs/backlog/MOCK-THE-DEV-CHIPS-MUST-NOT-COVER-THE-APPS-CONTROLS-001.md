# MOCK-THE-DEV-CHIPS-MUST-NOT-COVER-THE-APPS-CONTROLS-001 · El andamio de dev tapaba justo lo que un build de dev existe para mirar

**Estado:** ✅ Done — mergeado a master el 31-08-2026
**Abierto:** 31-08-2026

## Problema

Los dos controles persistentes del flavor `mock` (el botón **DEV** de vuelta al catálogo y el toggle
de tema ☀/🌙) viven en un `Column` anclado a `Alignment.TopEnd` (`DevRoot.kt:98-104`). Arriba a la
derecha es **exactamente donde la app pone sus propios mandos**:

- Home: su `MapTypeToggle` en la cabecera.
- Detalle histórico en el mapa: el `MapTypeToggle` que añadió
  `UI-HISTORY-DETAIL-HAS-THE-MAP-CONTROLS-001` (`dc551cbf`).

Resultado: en el build de dev, el toggle de tipo de mapa **se asomaba por detrás de los chips** y no
se podía pulsar sin arriesgarse a darle al tema. Medido en el emulador al probar `dc551cbf`.

No es un defecto de producción — estos chips no existen fuera de `mock` — pero sí de la herramienta:
el andamio tapaba la superficie que ese mismo build sirve para revisar. Y no lo trajo el ticket nuevo:
el toggle de Home lleva tapado desde que existe.

## Diseño

El cluster se mueva al **borde IZQUIERDO, a media altura** (`Alignment.CenterStart`), que es la única
región que ninguna pantalla usa: mapa en Home y en el detalle, margen en las listas. Se retira el
`statusBarsPadding` (ya no está pegado a la barra de estado) y el KDoc del fichero, que decía
"clustered top-end", deja de mentir.

## Criterio de éxito

- El toggle de tipo de mapa del detalle histórico se ve entero y se pulsa. **Verificado en el
  emulador**: `TERRAIN → HYBRID`, el icono cambia a globo.
- Los chips de dev siguen alcanzables en catálogo, galería y app.
- Nada cambia en `prod`: el fichero es `app/src/mock/`.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `DevRoot.kt` | ⛔ origen (posición + KDoc) → corregido |
| `MapTypeToggle` (Home y detalle) | ✅ dejan de estar tapados; no se tocan |
| `prod` | ✅ intacto por construcción (fuente `mock`) |
