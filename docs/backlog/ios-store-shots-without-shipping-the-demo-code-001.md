# IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001 · Ficha y capturas del App Store, sin que el modo demo viaje en el .ipa

**Estado:** 🔵 En progreso · rama `feature/IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001` ·
worktree `../PaparcarKMP-storeshots`

⚠️ **Parte de `feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001`, no de master**: `iosApp/fastlane/`
(Fastfile, Appfile, Matchfile, firma de Release) nace en esa rama y aún no está mergeada. Mergear
aquella ANTES que esta, o esta se lleva por delante ficheros que master no conoce.

## Problema

La ficha del App Store no existe. Hoy `iosApp/fastlane` solo sabe firmar y subir binarios —
`docs/release/IOS-FASTLANE-SETUP.md` lo dice explícitamente: *«Sin lanes de metadata ni de tag/CI
por ahora»*. Sin nombre, subtítulo, descripción, keywords ni capturas no se puede enviar a revisión.

La ficha de Play sí existe y está versionada en `docs/release/play-listing/paste/play-*.txt`, en los
**9 idiomas** del proyecto. Es la fuente de verdad del copy: es lo que se publicó y es lo que revisa
el user. La web de Play es un espejo de eso, no al revés.

## Doctrina violada

Ninguna de detección. Dos del proyecto:
- *La web es un consumidor más de la verdad del producto*: la ficha del App Store es otro consumidor
  del mismo copy, y hasta hoy no tenía dónde vivir dentro del repo.
- La regla del mock (*«no añadir pantalla/estado/flujo sin actualizar el sistema de pruebas mock»*)
  está escrita para Android (`app/src/mock/`). iOS no tiene flavors: las capturas necesitan su propio
  camino a datos deterministas, y ese camino es justo el que Apple vigila.

## ⛔ El requisito que manda sobre el diseño: guideline 2.3.1

El user ya fue **rechazado por Apple** en otra app por bifurcar el código para generar capturas:
App Review lo leyó como *funcionalidad oculta*. Por tanto el criterio no es «que no se active en
producción», es **que no exista en el binario de producción**:

1. **El bundle de tests no viaja**: un target de UI tests produce un `.xctest` que Xcode no empaqueta
   en el `.ipa` del archive. `SnapshotHelper.swift` y `ScreenshotUITests.swift` viven ahí.
2. **Swift del app target**: todo el enganche del modo demo va entre `#if DEBUG`, que en Release no
   llega ni al compilador.
3. **Kotlin no tiene preprocesador**, así que el modo demo NO puede ser un `if (isDebugBuild)`: eso
   deja el código dentro del framework. Va en un **source set que Gradle solo añade cuando se pide
   por propiedad**, de modo que el Release ni lo compila.
4. **Se verifica, no se supone**: tras un build de Release, comprobar con `nm`/`strings` que los
   símbolos del modo demo y de los fakes no están en el binario del framework, y que el `.ipa` no
   contiene el bundle de tests.

## Decisiones del user (18-09-2026)

- Solo **App Store** en esta tarea. Play se sigue pegando a mano desde `play-listing/paste/`.
- Ficha en los **9 idiomas** de Play.
- Capturas con **UI tests + modo demo** (opción de Fliksy), con la exclusión de arriba.
- **Solo iPhone**: `TARGETED_DEVICE_FAMILY` pasa de `"1,2"` a `"1"`. Quita la obligación de
  mantener capturas de iPad y que App Review juzgue la app en una pantalla para la que no se diseñó.

## Referencia

`~/Documents/Companies/AppToLast/KMP/FamilyFilmKmp/iosApp/fastlane` (Fliksy): `Snapfile`,
`metadata/<locale>/*.txt`, `screenshots/<locale>/`, lane `screenshots` y lane `upload_store_assets`.
Su modo demo entra por argumento de lanzamiento (`-FFADemoMode YES`) y cambia el datasource por uno
falso. Aquí hay que llegar al mismo sitio SIN dejar rastro en Release.

## Criterio de éxito

- `fastlane ios screenshots` genera las capturas de iPhone 6.9" en los 9 idiomas, sin red ni cuenta real.
- `fastlane ios upload_metadata` sube ficha y capturas sin binario ni envío a revisión.
- Un build de Release **no contiene** símbolos del modo demo ni de los fakes, comprobado con `nm`.
- El `.ipa` no contiene ningún `.xctest`.
- La ficha del App Store no inventa nada: cada campo sale de `play-listing/paste/` o está marcado
  como pendiente de que lo apruebe el user.

## Consumidores auditados

(pendiente)

---

## 18-09 · Lo que se encontró al empezar: la fuga ya existía

`nm` sobre el framework de Release del **build 2 de TestFlight** (el subido esa madrugada):

- **161 clases `Fake*` exportadas** a Objective-C + `MockScenario` + 112 cadenas con esos nombres.
- Vía de entrada: `MockMainViewController.kt` e `IosMockModule.kt` vivían en `shared/src/iosMain`.
  Kotlin/Native exporta la API pública del módulo a `ComposeApp.h`, **un símbolo exportado es raíz
  de DCE**, y esa raíz mantenía vivos los 19 ficheros de fakes de `commonMain` a través del DCE y
  del LTO.
- **Ningún Swift los llamaba**: código muerto que se publicaba igual.

O sea que el ticket no era «añadir capturas sin ensuciar», era «sacar lo que ya estaba dentro».
Decidido con el user: módulo aparte (no propiedad de Gradle) + guard de importaciones.

## Diseño final — y por qué NO es el que se planeó

El plan era `:shared` enlazando `:mockkit` solo con `-Ppaparcar.screenshots=true`. **Imposible**, y
lo dice Gradle (medido con `--dry-run`, antes de construir nada):

```
Circular dependency between the following tasks:
:mockkit:compileKotlinIosSimulatorArm64  ←→  :shared:compileKotlinIosSimulatorArm64
```

`:mockkit` depende de `:shared`, así que `:shared` no puede depender de vuelta **ni detrás de un
flag**. Eso convierte la garantía en estructural: meter el grafo mock en el framework que se publica
no está prohibido, **no compila**.

Solución: **`:mockkit` publica su propio framework**, con el mismo `baseName = "ComposeApp"`, que
lleva `:shared` entero (un framework estático embebe sus dependencias) más el grafo mock. Quién lo
construye lo decide `PAPARCAR_FRAMEWORK_PROJECT` en el Run Script de Xcode, y solo la lane
`screenshots` lo pone.

### Tres exclusiones que fallan por separado

1. **Bundle de tests** — un `.xctest` nunca se empaqueta en el `.ipa` del archive.
2. **Swift** — el enganche va tras `#if DEBUG && PAPARCAR_SCREENSHOTS`. `DEBUG` deja fuera Release;
   `PAPARCAR_SCREENSHOTS` deja fuera incluso el Debug normal de un desarrollador.
3. **Kotlin** — `MockMainViewController` solo existe en el framework de `:mockkit`. Un build normal
   embebe el de `:shared`, donde el símbolo no existe: aunque alguien compilara la rama Swift, no
   enlazaría.

## Falsificación del guard — con un resultado mejor del esperado

- Inyectar `import com.rndeveloper.paparcar.fakes.MockScenario` en `DetectionModule.kt` (commonMain):
  **el build no llega al guard, no compila**. Producción no puede ver el paquete.
- Intentar la violación que *sí* compilaría —añadir `implementation(project(":mockkit"))` a
  `commonMain`—: **Gradle la rechaza por ciclo**.
- Queda `MockGraphGuardrailTest` como red para lo único que sigue siendo posible: mover un fichero
  de sitio. Su primera ejecución ya cazó algo real —un KDoc de `DetectionModule` que nombra
  `MockMainViewController`—, y por eso ahora ignora comentarios: un guard que no distingue una
  referencia de una mención enseña a borrar la explicación en vez de la dependencia.

## Estado de la verificación

| Comprobación | Resultado |
|---|---|
| `:app:compileMockDebugKotlin` (flavor mock con `:mockkit`) | ✅ |
| `:shared:compileDebugUnitTestKotlinAndroid` (tests ven los fakes) | ✅ sin ciclo |
| `:shared:compileKotlinIosSimulatorArm64` | ✅ |
| `:mockkit:linkDebugFrameworkIosSimulatorArm64` | ✅ 1m53s |
| `MockGraphGuardrailTest` (3 tests, con `--rerun-tasks`) | ✅ + falsificado |
| `nm` sobre el Release: el grafo mock fuera | ✅ **82 → 6** símbolos `ComposeAppFake*` |
| `testAll` completo | ⏳ corriendo |
| `fastlane ios screenshots` en simulador | ⏳ pendiente |

## Pendiente de decisión del user

- **`subtitle` (30) y `keywords` (100) × 9 idiomas**: el App Store los tiene y Play no. No se
  derivan de nada, así que no se inventan. La lane no los sube si no existen (deliver ignora el
  campo ausente), pero la ficha queda incompleta hasta que se escriban.
- **`release-notes-v1.3.0.txt` no tiene rumano**: 8 de 9 idiomas. Afecta también a Play.

## Deuda que deja el ticket

- **Cero `testTag` en todo el proyecto**, así que `ScreenshotUITests` navega las pestañas por
  COORDENADA (barra fija de 3, tercios). Si entra una cuarta pestaña, dispara la pestaña equivocada
  **en silencio**. El arreglo son tres `Modifier.testTag` en `AppBottomNavigation` y consultarlos
  desde el test.
- `presentation/preview/Fake{Data,Parking,Spots}.kt` siguen en `commonMain`: son datos de `@Preview`,
  no un grafo de ejecución, y sacarlos obligaría a mover también las previews. Menor riesgo de 2.3.1,
  pero son símbolos `Fake*` en el binario. Decidir aparte.

## 18-09 · La medición que cierra el problema

`nm` sobre `shared/build/bin/iosArm64/releaseFramework/ComposeApp.framework/ComposeApp`, recién
enlazado con estos cambios, contra el mismo binario de anoche:

| | build 2 de TestFlight | ahora |
|---|---|---|
| `ComposeAppFake*` | **82** | **6** |
| `MockScenario` | presente | 0 |
| `MockMainViewController` | presente | 0 |
| `iosMockModule` | presente | 0 |
| cadenas con nombres de fakes | 112 | 0 |

Los 6 restantes son `FakeData`, `FakeParkingKt` y `FakeSpotsKt` (3 clases × clase+metaclase): los
datos de `@Preview` de `presentation/preview/`. No son un grafo de ejecución alternativo —no hay
modo demo que activar con ellos—, pero llevan un nombre que un revisor que corra `strings` lee como
código de pruebas. **Follow-up barato: renombrarlos a `PreviewData`/`PreviewParking`/`PreviewSpots`.**
Las demás coincidencias con "fake" en el binario son de terceros: RevenueCat (`FakeASIdManager`,
`FakeTrackingManager`) y Skia (`useStrokeForFakeBold`, `dng_space_fakeRGB`).
