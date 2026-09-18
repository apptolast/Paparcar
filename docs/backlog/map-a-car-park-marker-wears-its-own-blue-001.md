# MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001 · El parking público se dibuja azul, con su P blanca

**Estado:** 🔵 En revisión · rama `feature/MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001` ·
worktree `../PaparcarKMP-issue30` · cierra la **issue #30**

## Problema

Pedido por el product owner (#30): el marcador de parking público debe leerse como un parking de
los de toda la vida — **fondo azul y P blanca** — manteniendo la forma de marcador. Y decidir si se
usa un azul del tema o uno nuevo.

Hoy es cuerpo papel + borde verde + P verde: comparte la silueta del puck de plaza y se distingue
solo por no estar relleno. En el mapa, junto a plazas verdes/ámbar/rojas, un parking es una cosa
más de la familia en vez de un TIPO distinto.

## Doctrina violada

Ninguna, pero roza dos y por eso el cambio no es «pintarlo de azul»:

1. **Un color significa UNA cosa** (`UI-COLOR-DOCTRINE-001`). Los dos azules del mapa ya están
   ocupados: `papCarBlue` = coche vigilado por Bluetooth, `PapLiveMap` = movimiento que no es tuyo.
   Reutilizar cualquiera haría que el mismo tono dijese dos cosas en la misma pantalla.
2. **El relleno del puck es la rampa de frescura** de una plaza (verde → ámbar → rojo). El KDoc del
   marcador lo decía explícitamente: no se rellenaba para no convertir cada parking en lo que parece
   una plaza recién liberada.

## Diseño

- **Token propio**: `PapCarParkBlueDark` (#3E8FD4) / `PapCarParkBlueLight` (#00468F), resueltos por
  tema en el rol nuevo `PapColor.carPark`, con `PapColor.onCarPark` = blanco para la P.
- **El azul resuelve el punto 2 mejor que el borde**: el azul **no está en la rampa de frescura**,
  así que no hay forma de confundirlo con un nivel. El relleno pasa a decir de QUIÉN es la cosa.
- **Marcador**: puck relleno de azul + P blanca + hairline de `surface` (un tercio del grosor del
  borde anterior) para que no se funda con las teselas oscuras ni con el papel.
- **Fila**: `HomePublicParkingRows` pasa de `actionText` (verde) a `carPark`. El pin y su fila son
  la misma cosa y el propio KDoc del marcador lo exigía.

## Consumidores auditados

| Sitio | Qué hace | Decisión |
|---|---|---|
| `PaparcarMapMarkers.PublicParkingMarker` | el marcador | ✅ azul relleno + P blanca |
| `HomePublicParkingRows` | tile de la fila | ✅ pasa a `PapColor.carPark` |
| `StateGalleryScreen` (Dev Catalog) | ya muestra ambos estados | ✅ sin cambios, refleja el color nuevo |
| `HomeNearbyLensRow` (LensTab) | pestaña de lente | ❌ su color dice SELECCIONADA, no qué entidad es |
| `BrowsePeek` (NearbyCounter) | contador de la lente | ❌ igual: es mobiliario del contador |
| `PublicParkingPeek` (`PapSheetLead.GenericIcon`) | tile del peek | ⚠️ **queda verde** — ver Preguntas |
| `FirstStepExplainer`, `OnboardingScreen` | explicación de onboarding | ❌ ilustran la P, no el color del mapa |

## Criterio de éxito

- El parking se lee como parking a tamaño de mapa, y no se confunde con una plaza libre.
- El pin y su fila van del mismo color.
- No se confunde en device con el coche vigilado por BT ni con el movimiento ajeno.

## Preguntas (para la revisión)

1. **El tile del peek se queda verde.** `PapSheetLead.GenericIcon` pinta con `primary` y lo comparten
   todos los peeks; teñirlo exige un parámetro nuevo en un componente compartido. ¿Lo hago en otro PR
   o entra aquí?
2. **El azul está apretado** (87/765 de `PapCarBlueDark` en oscuro). Lo que los separa en uso es qué
   los lleva: puck relleno vs glifo de vehículo vs traza. Si en el Oppo se leen como un solo azul,
   el plan B es un navy fijo en ambos temas, como ya hace `PapLiveMap`. Hace falta tu ojo en device.
3. Los marcadores de este fichero **no tienen `@Preview`** (ninguno de los ~15): se ven por el Dev
   Catalog. No he roto esa convención por uno solo.
