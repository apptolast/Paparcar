# SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001 · El default de unidades imperiales sigue al país del dispositivo

**Estado:** ✅ Done — revisado en device (Redmi) en tres rondas: dropdown→segmented, amarillo→verde,
tema a fila segmentada. Tests verdes, prod+mock compilan.
⚠️ El único cambio iosMain (el fallback `objectForKey == null` en `IosAppPreferences`) no se puede
compilar en Windows — validación iOS con el compañero, como IOS-F0.

## Problema
El toggle "Imperial units" de Ajustes defaultea a `false` (métrico) en TODO el mundo:
`AndroidDataStoreAppPreferences.useImperialUnits` → `get(Keys.USE_IMPERIAL_UNITS, false)` y
`IosAppPreferences` → `boolForKey` (false si no existe). Un usuario de EEUU que instala la app ve
"km" y "m" hasta que descubre el toggle. El default debería depender del país del locale del
dispositivo (US, Liberia, Myanmar → imperial), y el toggle queda como override manual.

Agravante: `defaultDistanceUnit()` (expect/actual en `presentation/util/DistanceUnit.kt` +
`.android.kt` + `.ios.kt`) ya sabe exactamente esto — lista `US/LR/MM` incluida — pero es **código
muerto con cero call sites**, y además duplica la lista de países en cada actual.

## Doctrina violada
- «Sistemas, no parches»: el conocimiento "estos países usan imperial" existe duplicado en dos
  actuals y desconectado del sitio donde se decide el default.
- «Al quitar un botón, borrar limpio»: la función muerta se borra o se conecta; no se deja colgando.

## Señales / datos disponibles
- Pipeline actual: `AppPreferences.useImperialUnits` → `AppViewModel` init → `AppState.imperialUnits`
  → `LocalDistanceUnit` (App.kt:160) → `formatDistance`. Con arreglar el default del getter, todo lo
  de aguas abajo (incluido el estado del toggle en Ajustes) hereda el default correcto.
- El default sólo aplica mientras el usuario no toque el toggle: `setUseImperialUnits` escribe la
  key y desde entonces manda lo almacenado.

## Diseño
El invariante — *el default de la preferencia lo dicta el país del locale* — vive en UN sitio:

1. **`LocaleUnits.kt`** (commonMain, package raíz junto a `Platform.kt` — NO en `domain.*`: el
   guardrail `ArchitectureTest` exige domain platform-puro POR PACKAGE en todos los source sets, y
   este fichero importa `androidx.compose.ui.text.intl.Locale`):
   - `fun countryPrefersImperialUnits(countryCode: String): Boolean` — la decisión pura, con la
     lista como única fuente de verdad. Testeable en commonTest.
   - `fun localePrefersImperialUnits()` — lee `Locale.current.region` de Compose Multiplatform
     (API común, usable fuera de composición; delega en `java.util.Locale`/`NSLocale` por target).
     **Sin expect/actual ni librería nueva** — CMP ya está en el classpath.
   - Lista: `US/LR/MM` (el trío no-métrico) **+ `GB`** — Reino Unido es métrico sobre el papel pero
     señaliza carretera en millas; Google/Apple Maps defaultean UK→mi por lo mismo.
2. **Preferencias**: ambos impls usan `localePrefersImperialUnits()` como default cuando la key no
   está escrita. Android: `get(Keys.USE_IMPERIAL_UNITS, localePrefersImperialUnits())`. iOS:
   `objectForKey == null → localePrefersImperialUnits()`.
3. **UI: el control ofrece cada sistema por su NOMBRE** — el switch "Imperial units" obligaba a
   saber leer qué significa apagado. Primera iteración: desplegable (revisada en device 28-08 y
   descartada — con solo DOS opciones esconde la alternativa y cuesta dos taps). Final: **M3
   `SingleChoiceSegmentedButtonRow`** bajo la fila "Unidades de distancia" — ambas opciones
   visibles por nombre ("Kilómetros (km)" / "Millas (mi)"), un tap. Subtítulo concreto: dice DÓNDE
   aplica ("Para plazas, rutas y estadísticas"), no una obviedad. `LanguageDropdownRow` quedó como
   estaba (10 opciones sí justifican dropdown). La preferencia sigue siendo el mismo boolean;
   State/Intent/galería sin cambios de firma. Strings `settings_distance_unit{,_desc,_metric,_imperial}`
   en los 9 locales.
3-ter. **Revisión en device 28-08 (dos rondas):** (a) el segmento activo salía AMARILLO — era el
   `secondaryContainer` por defecto de M3; ahora usa la receta canónica de selección de
   `PaparcarFilterChip` (fill `primaryContainer` + borde/texto `primary`). (b) El bloque de Tema
   (miniaturas claro/oscuro/sistema) desentonaba con el resto de la pantalla → mismo patrón:
   helper privado **`SettingsSegmentedRow<K>`** (PapListItem + segmented control con esos colores)
   compartido por Tema (3 segmentos, icono DarkMode) y Unidades (2 segmentos). `ThemeBlock` +
   `ThemePreview` + constantes `THEME_*` borrados limpios; strings de tema reutilizados tal cual.
3-bis. **`PapListItem`: gap uniforme título→subtítulo** (revisión en device: iban pegados) —
   `TITLE_SUBTITLE_GAP_DP = 4` en el esqueleto compartido, heredado por los 10 consumidores
   (settings, sheet de Home, selectores…), no por fila. [UI-LIST-ITEM-001]
4. **Borrado limpio** del muerto: `defaultDistanceUnit()` expect + 2 actuals (la enum
   `DistanceUnit` y `LocalDistanceUnit` se quedan — sí tienen consumidores).

## Criterio de éxito
- Test unitario de `countryPrefersImperialUnits` (US/LR/MM → true, ES/GB/resto → false,
  case-insensitive, vacío → false).
- Dispositivo con locale es-ES → toggle OFF y "km"; locale en-US → toggle ON y "mi", sin tocar nada.
- El toggle manual sigue mandando una vez tocado.

## Consumidores auditados
`grep useImperialUnits|defaultDistanceUnit|imperialUnits`:
- `AndroidDataStoreAppPreferences` / `IosAppPreferences` → cerrado (default nuevo).
- `AppViewModel:43` (lee getter) + `:148` (escribe) → cubierto vía getter; sin cambios.
- `AppState.imperialUnits = false` (valor pre-carga, un frame de splash) → exento: el VM lo pisa en
  init con el valor real; no hay distancia visible antes.
- `FakeAppPreferences` (commonTest, `initialUseImperialUnits = false`) → exento: los tests deben ser
  deterministas, no seguir el locale de la máquina de CI.
- `FakeOtherDataSources` (fakes mock flavor, `false`) → exento: Dev Catalog determinista.
- `defaultDistanceUnit()` y sus 2 actuals → borrados.
- `SettingsScreen` / `StateGalleryScreen` / `SettingsPreviews` → sólo pintan el boolean del state;
  sin cambios.
