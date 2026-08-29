# IOS-DEVCAT-A-GALLERY-THE-SIMULATOR-CAN-OPEN-001 · El Dev Catalog no existe en device iOS

**Estado:** ⚪ Pendiente · sin rama
**Origen:** fila 8 de `ios-a-parity-tail-001.md` — declarada con ticket propio por una decisión de
binario que no es de este ticket.

## Problema
El sistema de pruebas mock (escenarios + galería de estados) es obligatorio para cada pantalla
nueva, pero solo corre en Android: `DevCatalogScreen` (312 LOC), `StateGalleryScreen` (1.986 LOC) y
`DevRoot` viven en `app/src/mock/`. El MODELO ya es multiplataforma — `MockScenario` + fakes en
commonMain, `iosMockModule` y `MockMainViewController` en iosMain — pero ese view controller monta
`App()`, no el catálogo, y desde `ContentView.swift` nadie lo invoca.

## Diseño (lo medido el 07-09)
1. Mover las 3 pantallas a commonMain. Imports a resolver (pocos): `BackHandler` de
   androidx.activity → el `BackHandler` común de CMP; `LocalConfiguration` en `DevRoot` (override
   dark/light) → param explícito; `ViewModelStore*` ya es multiplataforma.
2. `MockMainViewController` pasa a montar `DevRoot(scenario = get())`, espejo de
   `DevMainActivity`.
3. Alcanzable desde Swift: scheme/flag de build que arranque el mock VC (el gemelo del flavor
   `mock` de Android — `:shared` no tiene flavors, de ahí la decisión de abajo).

## La decisión que lo saca del paraguas
Mover ~2.400 LOC de pantallas dev a commonMain las mete en el binario de PRODUCCIÓN de ambas
plataformas (R8 poda en Android release; el stripping de K/N es menos agresivo). Los fakes ya
pagan ese precio a propósito, pero multiplicarlo x10 merece el visto bueno del user, o una
alternativa (source set propio, módulo aparte).

## Criterio de éxito
`MockMainViewController` abre el catálogo en el simulador con los mismos escenarios y galería que
Android; `assembleProdRelease` no crece de forma medible o la alternativa elegida lo evita.
