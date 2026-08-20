# UI-MAP-TYPE-TOGGLE-001 · El selector de mapa deja de ser un desplegable de 3 y pasa a un toggle de 2

**Estado:** 🟢 Commiteado en la rama `refactor/UI-MAP-TYPE-TOGGLE-001-terrain-hybrid` (`52b86509`),
**sin mergear a master** · worktree `../Paparcar-map-type-toggle` · 1249 tests verdes ·
`prodDebug` + `mockDebug` compilan · APK `866bff02…c03d48` instalado en Redmi y Oppo · ⏳ probarlo
en el móvil (fundido, pin que no parpadea, gesto durante la transición)

## Problema
El FAB de capas del header de Home abre un popup con una columna de 3 FABs (Terreno · Satélite ·
Híbrido). Satélite (la opción de enmedio) es imagen aérea CRUDA, sin etiquetas de calle: para una
app de aparcamiento no aporta nada que Híbrido no dé mejor (misma imagen + nombres de calle). Tres
opciones para dos usos reales significa dos toques (abrir el popup + elegir) donde basta uno.

## Doctrina violada
Ninguna doctrina de detección. Sí una regla de producto: un control con una opción que nadie va a
elegir es peso muerto. Y el feedback **"al quitar un botón, borrar limpio; no plegar su conducta en
otro control"** — Satélite se va entero (opción, string, constante de preferencia), no se queda
escondido como estado alcanzable.

## Señales / datos disponibles
- `MapTypePicker.kt` — popup + `AnimatedVisibility` + 3 `MapTypeStackEntry` con anillo de selección.
- `HomeViewModel.toMapType()/toPreferenceString()` — persisten el tipo como string en `AppPreferences`.
  El default (`AndroidDataStoreAppPreferences.DEFAULT_MAP_TYPE`, `IosAppPreferences`) ya es `TERRAIN`.
- Usuarios que hoy tengan `"SATELLITE"` guardado en preferencias: hay que decidir a dónde caen.

## Diseño
**Un solo botón que alterna Terreno ⇄ Híbrido.** Sin popup, sin animación de entrada/salida, sin
anillo de selección: el estado se lee en el propio icono.

1. `MapTypePicker.kt` → `MapTypeToggle.kt`. El nombre deja de mentir: ya no elige entre N, alterna
   entre 2. Un `MapCircleFab` que muestra el icono del **mapa actual** — es un distintivo del
   estado, no una previsualización del siguiente; tocarlo cambia al otro:
   - en Terreno → icono `Map`. A zoom de ciudad, terrain se ve como un **plano de calles**: sin
     relieve, sin edificios, solo viario y nombres.
   - en Híbrido → icono `Public` (planeta). Ahí se ve **el mundo real desde arriba**: casas,
     árboles, sombras, con las calles rotuladas encima.
   - contentDescription = `home_cd_map_type` ("Cambiar tipo de mapa"): el icono cuenta el estado,
     la etiqueta cuenta la acción.
   El icono cruza con `Crossfade` (120 ms) para que el cambio se vea, no salte.
2. **Disolución del mapa** (`PaparcarMapView`). El SDK cambia el juego de teselas en un frame: el
   salto terreno→híbrido era un corte seco. Con una sola superficie nativa no se pueden cruzar dos
   mapas, así que el cambio ocurre **debajo de un velo** del color de fondo del propio mapa:
   entra (100 ms) → se cambia el estilo con nada a la vista → 40 ms de espera para que empiecen a
   llegar las teselas nuevas → sale (220 ms, la mitad lenta es la que se lee como transición).
   El estilo que recibe el mapa es `renderedMapType`, que va un fundido por detrás de `config`.
   El velo se dibuja **encima de las teselas pero debajo del pin central** (el pin no parpadea) y
   su alpha se lee en el lambda de `graphicsLayer` (fase de dibujo), así que el fundido **no
   recompone el mapa** — invariante de rendimiento que este fichero cuida en todas partes.
3. `MapType.SATELLITE` deja de ser alcanzable desde la UI. La preferencia heredada `"SATELLITE"`
   **migra a `HYBRID`** en `toMapType()` (misma imagen aérea, además con etiquetas): nadie se queda
   con un mapa que ya no puede cambiar desde el toggle. La constante `MAP_TYPE_SATELLITE` se queda
   *solo* como clave de esa migración, documentada.
4. Strings, en los 9 locales: fuera las tres etiquetas de tipo (`settings_map_type_satellite`,
   `_terrain`, `_hybrid`) — ya no se rotula ninguna opción, porque no hay lista. Se conserva
   `home_cd_map_type` ("Cambiar tipo de mapa") como contentDescription del botón.

## Criterio de éxito
- El header de Home muestra UN botón; tocarlo cambia el mapa en el sitio, sin popup.
- Con `"SATELLITE"` en preferencias, la app arranca en Híbrido y el toggle funciona desde ahí.
- `HomeViewModelTest` verde con los casos reescritos sobre Terreno/Híbrido.
- Compilan `prodDebug` y `mockDebug`.

## Consumidores auditados
| Sitio | Estado |
|---|---|
| `HomeHeaderSection.kt:97` — único call site del picker | ✅ actualizado a `MapTypeToggle` |
| `HomeScreen.kt:856` — `HomeIntent.SetMapType` | ✅ intacto (el intent sigue tomando `MapType`) |
| `HomeViewModel.toMapType()/toPreferenceString()` | ✅ migración SATELLITE→HYBRID |
| `HomeState.mapType` / `PaparcarMapView.mapType` — default `TERRAIN` | ✅ exentos, no cambian |
| `AndroidDataStoreAppPreferences` / `IosAppPreferences` — `DEFAULT_MAP_TYPE = "TERRAIN"` | ✅ exentos |
| `HomeViewModelTest` — 4 tests citaban `SATELLITE` | ✅ reescritos |
| Dev Catalog / `StateGalleryScreen` / `*Previews.kt` | ✅ exentos — no hay pantalla ni estado nuevo; ninguna preview instancia el picker |
