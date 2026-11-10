# TEST-MASTER-REDS-BLOCK-THE-1-6-0-CUT-001 · Tres tests rojos en master impedían cortar la 1.6.0

**Estado:** ✅ Done

## Problema
`tools/release/cut.py` corre `testAll --rerun-tasks` en su fase `suite`, y master llevaba tres
fallos heredados desde `f84ade96a` / `25eb3ac1e` (anotados en `home-live-address-readable-001.md`,
`home-parking-questions-keep-the-map-001.md` y `play-shots-mobility-carousel-001.md`):

1. `LocaleParityGuardrailTest` — `app/src/main/res/values/strings.xml · notif_bt_saved_text`
   declarada en los 9 locales y sin consumidor.
2. `HomeSlicesTest.should_openOnSpots_andPaintNoComplementOnTheMap_when_nobodyHasChosenALens` —
   afirmaba que el carril de plazas no pinta parkings.
3. `HomeSlicesTest.should_keepTheStoreReadable_when_itsLayerIsNotPainted` — usaba el carril de
   plazas como testigo de «capa de parkings no pintada».

## Doctrina violada
Ninguna de producto. Los tests 2 y 3 se quedaron atrás de un cambio deliberado: desde `25eb3ac1e`
Aparcar (carril `SPOTS`) pinta los parkings públicos como su segundo bloque
(`HomeState.showsParkingsOnMap`). El 1 era una string escrita para su sitio y nunca conectada.

## Diseño
- **String**: `f84ade96a` la escribió para la notificación de aparcamiento guardado por Bluetooth
  («Guardado automáticamente…»), pero `showSavedParking` usaba para los dos carriles el texto
  genérico. Se conecta: `bluetooth = true` → `notif_bt_saved_text`. No se borra, porque el carril
  BT es justo el que se guarda sin preguntar y su texto lo dice. iOS no tiene carril BT, así que no
  hace falta espejo en `composeResources`.
- **Tests**: se ajustan al comportamiento vigente, no el código. El testigo de «capa de parkings
  no pintada» pasa al carril `FUEL`, el único que no la pinta; la asimetría tienda/capa que el test
  protege sigue intacta.

## Criterio de éxito
`./gradlew testAll --rerun-tasks` en verde.

## Consumidores auditados
- `showsParkingsOnMap`: `toMapSlice()` y los dos tests de `HomeSlicesTest`; el de
  `should_paintOnlyTheLanesOwnComplement_when_theLensMoves` ya era coherente.
- `notif_bt_saved_text`: único consumidor `AppNotificationManagerImpl.showSavedParking`.
