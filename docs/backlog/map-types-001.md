# MAP-TYPES-001 — Rediseño del picker de tipos de mapa (Principal / Conducción / Satélite con calles)

> **Estado**: PENDIENTE — solo spec, sin código. Investigación 2026-07-14 (código + Google Maps
> SDK + competencia). Al arrancar → rama `feature/MAP-TYPES-001-map-type-picker`.

## Problema

El picker actual (`MapTypePicker.kt:58-145`, FAB de capas en el header de Home) ofrece
TERRAIN / SATELLITE / HYBRID. Tres defectos:

1. **Satélite e Híbrido son redundantes entre sí** (híbrido = satélite + calles; satélite puro
   es un subconjunto peor) y **Terrain es irrelevante** para aparcar en ciudad (curvas de nivel).
2. **Bug de base**: el default es `MapType.TERRAIN` (`HomeState.kt:103`) y `MapType.NORMAL` ni
   se ofrece. Los estilos JSON de marca (`MapStyles.kt` — `LIGHT_MAP_STYLE`/`DARK_MAP_STYLE` vía
   `mapStyleOptions`) **solo aplican plenamente sobre el tipo NORMAL (vectorial)**; terrain,
   satellite e hybrid son ráster y el estilizado es limitado o nulo. El "mapa principal con
   estilo Paparcar" está montado sobre el tipo equivocado.
3. En las vistas ráster se pierden el estilo de marca y el dark mode automático: los pines de
   plazas (el contenido de la app) compiten contra la foto aérea.

## Propuesta — picker de 3 entradas

| Entrada | Base técnica | Rol |
|---|---|---|
| **Principal** (default) | `NORMAL` + JSON marca light/dark (`MapStyleMode.AUTO`, ya existe) | Lienzo neutro; los pines protagonistas |
| **Conducción** | `NORMAL` + JSON nuevo alto-contraste | Ir en ruta hacia una plaza: oculta labels de POIs/comercios, engorda jerarquía viaria, sube contraste de nombres de calle (patrón Waze / modo navegación de Google Maps) |
| **Satélite con calles** | `HYBRID`, sin estilo | Única vista aérea: verificar el entorno físico de una plaza publicada (¿vado? ¿esquina? ¿descampado?) |

Se **eliminan** SATELLITE puro y TERRAIN.

## Diseño técnico

1. **Nuevo enum de presentación** (p. ej. `MapSkin { BRAND, DRIVING, AERIAL }`) que resuelve a
   par `(MapType, styleJson?)`. La preferencia persistida en `AppPreferences`
   (`setDefaultMapType`) pasa a guardar este enum, no `MapType` crudo.
2. **Migración de preferencia**: valores viejos persistidos → `TERRAIN→BRAND`,
   `SATELLITE→AERIAL`, `HYBRID→AERIAL`. Sin crash con preferencia legacy.
3. **`MapStyles.kt`**: añadir `DRIVING_MAP_STYLE` / `DRIVING_MAP_STYLE_DARK` (featureType `poi`
   con `visibility: off`, pesos de `road.highway`/`road.arterial` arriba, labels de calle con
   más contraste). Variante dark obligatoria (Conducción se usa de noche).
4. **`MapTypePicker.kt`**: 3 entradas nuevas. Iconos Nivel 1 (Material Rounded):
   `Icons.Rounded.Map` (Principal), `Icons.Rounded.Navigation` (Conducción),
   `Icons.Rounded.SatelliteAlt` (Satélite con calles).
5. **Default**: `HomeState` arranca en BRAND (`NORMAL` + estilo de marca).
6. **iOS (documentar no-paridad)**: Apple Maps no admite JSON → BRAND y DRIVING degradan a
   standard + `mapTheme`; AERIAL → hybrid nativo. No prometer paridad visual en copy/marketing.

## F2 opcional (decisión abierta)

Auto-activar **Conducción** al entrar la fase "En ruta" (canal `Monitoring` /
`DetectionPhase`) si el usuario está en Principal, y restaurar al aparcar. Nunca pisar una
elección manual hecha durante el viaje. Decidir si entra en este ticket o queda fuera.

## Fuera de alcance (backlog aparte, detectado en la investigación)

- **Capa de tráfico** en ruta: `isTrafficEnabled` existe en kmp-maps 0.9.1 pero NO está
  implementado → requiere trabajo en el fork (como LiveMarker).
- **Capa de zonas de aparcamiento** (azul/verde/prohibido, estilo EasyPark): polígonos con
  datos propios sobre el mapa Principal, no un tipo base.
- **Cloud styling con `mapId`**: no soportado por kmp-maps.

## i18n / Dev Catalog / tests

- Strings nuevos `settings_map_type_*` (renombrar keys si el concepto cambia) — mínimo EN+ES.
- Dev Catalog: el picker vive en Home; añadir/actualizar variante en la galería si el picker
  tiene preview propio. No afecta a `MockScenario` (no toca routing).
- Tests: migración de preferencia legacy + resolución `MapSkin → (MapType, styleJson)`.
- Verificar `assembleMockDebug` además de prod.

## Criterio de éxito

- La app arranca en Principal sobre `NORMAL` con estilo de marca aplicado y dark automático.
- El picker muestra las 3 entradas nuevas; una preferencia vieja (p. ej. SATELLITE) migra sin
  crash a su equivalente.
- En Conducción los POIs desaparecen y la jerarquía viaria gana contraste (validar screenshot
  claro + oscuro en device).

## Referencias

- Código: `MapTypePicker.kt:58-145`, `HomeState.kt:103`, `PaparcarMapView.kt:1053-1059` y
  `:1275-1287` (resolución de estilo), `MapStyles.kt:1-46`, kmp-maps `MapEnums.kt:81-86` /
  `MapTypes.kt:59-69` (fork local `AndroidProjects/kmp-maps`).
- Google: JSON styling solo en tipo vectorial (normal); `MapColorScheme` requiere no usar JSON
  styling — seguimos con JSON propio. https://developers.google.com/maps/documentation/android-sdk/configure-map
