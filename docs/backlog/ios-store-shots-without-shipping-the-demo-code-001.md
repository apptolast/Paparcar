# IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001 · Ficha y capturas del App Store, sin que el modo demo viaje en el .ipa

**Estado:** 🟡 **En master** (`6970b4065`, dentro del squash de
IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001) · ⏳ falta un **Mac** para CORRER las capturas y subirlas ·
⛔ la rama `feature/IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001` y el worktree
`../PaparcarKMP-storeshots` que esta línea presentaba como vivos **ya no existen** (verificado el
13-10-2026).

Entregado y medido el 13-10-2026: **92 ficheros** en `iosApp/fastlane/metadata/` —nombre,
subtítulo, descripción, keywords, notas y las tres URLs, por idioma—, el `Snapfile`, y los lanes
`screenshots` y `upload_metadata` del `Fastfile`. Y la mitad que da nombre al ticket, que el modo
demo no viaje en el `.ipa`, vive en el módulo `:mockkit` con `MockGraphGuardrailTest` vigilando que
ninguna fuente publicable importe un fake.

⚠️ El aviso que esta cabecera llevaba —«parte de `IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001`, no de
master; mergear aquella ANTES»— **ya no aplica**: aquella se mergeó, y esto entró con ella.

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

## 18-09 · Primera ejecución de `screenshots`: un esquema de usuario tapaba al compartido

```
xcodebuild: error: Scheme iosApp is not currently configured for the test action.
```

Y el esquema compartido SÍ tenía su `TestableReference` — verificado en el XML. La causa:

- `iosApp.xcodeproj/xcuserdata/<usuario>.xcuserdatad/xcschemes/iosApp.xcscheme` existe, es
  **anterior** al target de tests, y trae `<TestAction>` con la lista de testables **vacía**.
- Un esquema de usuario **tiene prioridad sobre el compartido**, así que `xcodebuild` leía ese.
- Está en `xcuserdata`, que es **gitignored**: no se ve en `git status`, no viaja en el PR y solo
  falla en la máquina que lo tiene. En CI o en otro Mac el compartido habría funcionado a la primera.

Arreglo: inyectar el mismo `<Testables>` en el esquema de usuario (backup en el scratchpad). No se
borra el fichero porque guarda estado personal de Xcode.

⚠️ **Regla para la próxima**: crear un target de tests con la gema `xcodeproj` actualiza el esquema
COMPARTIDO y nada más. Si la máquina tiene esquema de usuario, hay que tocarlo también, y ningún
test ni guardarraíl lo va a cazar — no está versionado.

## 18-09 · `Multiple commands produce Paparcar.swiftmodule`

El target de tests heredaba el `PRODUCT_NAME = Paparcar` declarado a nivel de PROYECTO, así que app y
bundle de tests peleaban por el mismo módulo. Arreglo: `PRODUCT_NAME` y `PRODUCT_MODULE_NAME` a
`$(TARGET_NAME)` en las dos configuraciones de `iosAppUITests`. Un target nuevo **no** hereda nombre
de módulo por accidente solo si se le pone el suyo explícitamente.

## 18-09 · El grafo mock de iOS no tiene quien lo verifique

La app arrancaba y se moría en el primer frame:

```
NoDefinitionFoundException: No definition found for type 'ZoneAlertSubscriptionRepository'
```

`iosMockModule` llevaba **13 puertos menos** que el grafo mock de Android: 3 DAOs (`zoneWatchDao`,
`fuelWatchDao`, `tripRoutePointDao`) y 11 bindings (`ZoneAlertSubscriptionRepository`,
`PushTokenProvider`, `AnalyticsTracker`, `SharedCarRepository`, `SharedCarMirror`,
`FuelStationsSource`, `PublicParkingsSource`, `AverageFuelPricesRepository`,
`RoomZoneWatchRepository`, `RoomFuelWatchRepository`, `RoomDrivingRouteStore`). Se añadieron los 11;
`WidgetPinRequester` y `LocalDiagnosticsLog` se dejan fuera a propósito — sus consumidores los piden
con `getOrNull()`.

⛔ **La causa de raíz sigue abierta**: Android tiene `MockKoinModuleVerifyTest`, que resuelve el
grafo del flavor mock y por eso esto no pasa ahí. **iOS no tiene equivalente**, así que la deriva
solo se nota cuando la app peta en el simulador, a mitad de una tanda de capturas de 40 minutos.
Es el mismo agujero que [TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001] describe para `:app`.
**Follow-up: un verify del grafo de `iosMockModule` que corra en `testAll`.**

## 18-09 · Tres de cinco capturas eran Home, y el test pasaba en verde

La tanda de prueba de un idioma (`languages:es-ES`) salió con `03_Vehicles`, `04_Numbers` y
`05_Settings` mostrando **la pantalla Home** con el nombre equivocado. El test no falló: navegaba las
pestañas por COORDENADA —la debilidad que este mismo doc ya había anotado— y un tap por coordenada
que no cambia de pestaña **no es un error para XCTest**, simplemente captura lo que hubiera.

Arreglo en dos mitades, y las dos hacen falta:
- `Modifier.testTag("nav_home" | "nav_vehicles" | "nav_settings")` en `AppBottomNavigation` (Compose
  Multiplatform los expone como `accessibilityIdentifier` en iOS).
- `select(_:in:)` **exige** el identificador: `XCTAssertTrue(item.waitForExistence(timeout: 10))`.
  Si el tag no está, la tanda muere en el primer frame en vez de producir 27 capturas malas.

> Esto lo evitó una instrucción del user —*«prueba con alguno para comprobar que funciona y luego
> subes todas»*—, no un guardarraíl. Una tanda de un idioma antes de las nueve es ahora el
> procedimiento, y por eso la lane acepta `languages:`.

## 19-09 · Tres cosas que `deliver` necesita y que una app recién creada no tiene

Los tres fallaron con mensajes que no señalan la causa; los tres se midieron contra la API:

1. **`whatsNew` no existe en una primera versión.** *Attribute 'whatsNew' cannot be edited at this
   time* mientras no haya versión publicada. `deliver` lo intentaría en los diez idiomas y moriría
   **después** de subir todo lo demás. La lane sube una COPIA temporal sin `release_notes.txt` y
   deja de hacerlo sola en cuanto haya una versión publicada.
2. **Categorías de la app.** Sin ellas `deliver` muere con un escueto `No data`. Van en
   `metadata/primary_category.txt` + `secondary_category.txt` (NAVIGATION / TRAVEL) y las genera
   `metadata_from_play.py`.
3. **El App Review Detail no se crea solo.** Segundo `No data`, y el backtrace de Ruby lo situó en
   `UploadMetadata#review_attachment_file` → `AppStoreVersion#fetch_app_store_review_detail`. En una
   app que nunca se ha enviado a revisión ese recurso **no existe**, y Spaceship no lo crea. Se creó
   a mano con `post_app_store_review_detail(demoAccountRequired: false)`.

## 19-09 · Los 500 de App Store Connect duplicaron 11 capturas

La subida terminó en verde —*Successfully uploaded all screenshots*— y en ASC había **61**, no 50:
nl-NL +1, pl +1, pt-PT +5 (llegó a 10, el máximo que admite Apple) y ro +4.

Causa: durante la verificación, ASC devolvió **500** al releer los sets; `deliver` lo leyó como
*«no ha llegado»* y volvió a subir el fichero que ya estaba. El fix upstream es
*[deliver] prevent duplicate image uploads* (#30150), que entró en **fastlane 2.239.0** — el repo
corre **2.237.0**.

- Limpieza: borrados los 11 duplicados por la API y reordenado cada set por nombre de fichero.
  Recuento posterior: 50 capturas, 5 por idioma, todas `COMPLETE`, `APP_IPHONE_67`.
- ⛔ **Mientras el Gemfile siga por debajo de 2.239, un `upload_metadata` en verde NO garantiza el
  recuento**: hay que contarlas después. **Follow-up: subir fastlane a ≥2.239.**

## 19-09 · Las capturas NO se versionan

`.gitignore` ya declaraba la intención (*«It is recommended to not store the screenshots in the git
repo»*) con `fastlane/screenshots/**/*.png`, pero ese patrón lleva `/` en medio y por tanto está
anclado a la RAÍZ: el fastlane de este repo vive en `iosApp/`, así que **nunca tapó nada**. Los 27 MB
de PNG estaban entrando al commit. Añadidas las variantes `**/fastlane/screenshots/...`, que es la
forma que ya usaban las líneas de al lado (`**/fastlane/.env`) y la que tiene Fliksy.

Lo que sí se versiona es `metadata/`: lo genera `metadata_from_play.py` desde
`docs/release/play-listing/`, y la lane se niega a subir una carpeta que haya derivado (`--check`).

## Estado final

- Ficha **0.1.0 en `PREPARE_FOR_SUBMISSION`**: 10 idiomas con nombre, subtítulo, keywords, promo,
  descripción y URLs; categorías NAVIGATION/TRAVEL; **50 capturas** (5 × 10 idiomas). Nada enviado a
  revisión — ese botón lo pulsa una persona.
- Exposición de 2.3.1 cerrada con tres exclusiones independientes (bundle de tests · `#if DEBUG &&
  PAPARCAR_SCREENSHOTS` · framework aparte de `:mockkit`).

### Follow-ups abiertos
- Verify del grafo de `iosMockModule` en `testAll` (hoy solo lo cubre Android).
- fastlane ≥ 2.239 para que los 500 de ASC no dupliquen capturas.
- Renombrar `Fake{Data,Parking,Spots}` de `presentation/preview/` a `Preview*`.
- El frame `04_Numbers` se parece demasiado a `03_Vehicles`: decidir si se cambia por otro.
