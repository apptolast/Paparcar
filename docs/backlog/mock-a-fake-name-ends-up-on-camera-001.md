# MOCK-A-FAKE-NAME-ENDS-UP-ON-CAMERA-001 · Un nombre falso acaba saliendo en cámara

**Estado:** ✅ Done · mergeado a master en squash

## Problema

El vídeo de demo del Shipaton se graba del flavor `mock` (es lo único reproducible: `--es shot
<preset>` monta la app real con los chips DEV apagados, el mismo mecanismo de las 72 capturas de
Play). El vídeo va **en inglés** — las bases lo exigen: *«All submission materials must be in
English»*.

Grabando la escena 08 el 24-09-2026, con la app forzada a `en-IE`, el garaje salió así:

| chip | lo que se lee | idioma |
|---|---|---|
| 1.º | **Mi Seat** | español |
| 2.º | Toyota Corolla | marca de terceros |
| 3.º | **La Moto** | español |

Dos de los tres nombres del garaje están en español en un vídeo en inglés, y el tercero es una
marca registrada. La hoja del coche compartido enseña `displayName = "Seat León"`, que es marca +
modelo usados **como nombre**, y `ownerName = "Tú"`.

No es un defecto de traducción: son **literales hardcodeados en los fakes**, así que no los mueve
ningún locale. Da igual en qué idioma grabes — siempre saldrá «Mi Seat».

## Doctrina violada

CLAUDE.md, *Cosas que NO hacer*: **«No escribir strings en español en el código — EN es siempre la
base.»** La regla se ha leído siempre como «los `stringResource` van en EN», pero estos textos
también los lee un usuario: el flavor `mock` es una app que se instala, se navega y —ahora— se
graba.

El invariante que faltaba escribir, porque sin él no se sabe cuál de los dos textos hay que
traducir y cuál no:

> **Un fake tiene dos clases de texto.** Un **topónimo** —una calle de Cádiz, una gasolinera, un
> parking público, el nombre de una persona— describe algo real y **se queda en su idioma**:
> traducir «Calle San Bartolomé» sería falsear el mundo, y las capturas de Play ya lo enseñan así.
> Una **etiqueta que escribe el usuario** —cómo llamas a tu coche, cómo llamas a tu zona— la
> escribió alguien que habla el idioma de la app, y por tanto **sigue al idioma base del proyecto**.

Con ese corte, «Calle de Ponzano, 14» y «Área de Servicio La Bahía» se quedan como están, y
«Mi Seat», «La Moto», «Furgoneta» y «Garaje» no.

## Señales / datos disponibles

- `ChartMetric.availableChartMetrics()` ya demostró que el mock decide qué se ve en pantalla: un
  fake sin `searchTimeMs` esconde un chip entero. Los fakes **son** la UI del vídeo.
- El fallback marca+modelo (vehículo sin apodo) está cubierto por tres tests unitarios propios en
  `NotifyParkingConfirmationUseCaseTest` — `should pass brand and model…`, `should pass brand
  only…`, `should pass null when both…`. Ponerle nombre a los cuatro vehículos del catálogo **no
  pierde cobertura**: esa rama no se prueba mirando el catálogo, se prueba con su test.
- Ningún test asserta sobre los nombres del catálogo. El único `assertEquals("Toyota Corolla", …)`
  construye su propio `vehicle(brand = "Toyota", model = "Corolla")` y no toca el catálogo.

## Diseño

Renombrar **solo las etiquetas de usuario**, en los cuatro sitios donde viven, y dejar intactos los
topónimos. Los `brand` / `model` internos **no se tocan**: alimentan `VehicleCatalog.inferBodyType()`
y no llegan a pantalla mientras el vehículo tenga apodo.

| Fichero | Antes | Después |
|---|---|---|
| `FakeVehicleRepository` 001 | `Mi Seat` | `My car` |
| `FakeVehicleRepository` 002 | *(sin apodo → «Toyota Corolla»)* | `Second car` |
| `FakeVehicleRepository` 003 | `La Moto` | `My bike` |
| `FakeVehicleRepository` 004 | `Furgoneta` | `Work van` |
| `FakeSharedCarRepository` | `displayName = "Seat León"` | `Family car` |
| `FakeSharedCarRepository` | `ownerName = "Tú"` | `You` |
| `FakeSharedCarRepository` | `SharedCarMember(…, "Tú")` ×2 | `You` |
| `FakeZoneRepository` | `Garaje` | `Home garage` |
| `FakeData.kt` (previews) | los tres apodos | igual que el catálogo |
| `StateGalleryScreen` · `BluetoothConfigPreviews` | `Toyota Corolla` como `vehicleName` ×6 | `Second car` |
| `StateGalleryScreen` | `Garaje` ×3, `Seat León` ×2 | `Home garage`, `Family car` |
| `HomeZoneChipsPreviews` | `Garaje` | `Home garage` |

**Se queda en español, a propósito:** calles (`Calle de Ponzano, 14`, `Calle San Bartolomé, 12`),
gasolineras (`Área de Servicio La Bahía`), parkings públicos, ciudades, y los nombres de persona
(`Alex García`, `Marta`). Son topónimos y antropónimos de un mundo real que está en España.

## Criterio de éxito

- Ni una etiqueta de usuario en español en `fakes/`, `FakeData.kt`, la galería ni los previews —
  verificable por `grep` de las seis cadenas, filtrando comentarios.
- El garaje del vídeo se lee entero en inglés: `My car · Second car · My bike`.
- Ninguna marca de terceros aparece **como nombre** de nada en las pantallas que se graban.
- `./gradlew testAll` en verde, incluido `MockKoinModuleVerifyTest`.
- `assembleMockDebug` compila y el Dev Catalog arranca.

## Verificado el 24-09-2026

- `./gradlew testAll` → **BUILD SUCCESSFUL**, incluido `MockKoinModuleVerifyTest`.
- `./gradlew :app:compileProdDebugKotlin :app:assembleMockDebug` → **BUILD SUCCESSFUL**.
- Instalado en el Pixel 8 con `en-IE`, preset `sharedcar`: el garaje se lee
  **`My car · Second car · My bike`**, la ficha dice `My car`, y la fila del grupo
  `In this car · Marta, You`. Ni una palabra en español, ni una marca usada como nombre.

## Consumidores auditados

`grep -rn "Mi Seat\|La Moto\|Furgoneta\|Toyota Corolla\|Seat León\|Garaje"` sobre `shared/src` y
`app/src`:

| Sitio | Veredicto |
|---|---|
| `fakes/data/repository/FakeVehicleRepository.kt` | **cerrado** — es la fuente del catálogo |
| `fakes/data/repository/FakeSharedCarRepository.kt` | **cerrado** — displayName + ownerName |
| `fakes/data/repository/FakeZoneRepository.kt` | **cerrado** — zona habitual |
| `presentation/preview/FakeData.kt` | **cerrado** — datos de los `@Preview` |
| `app/src/mock/…/StateGalleryScreen.kt` | **cerrado** — 2 variantes de config BT |
| `androidMain/…/BluetoothConfigPreviews.kt` | **cerrado** — 4 previews |
| `fakes/data/repository/FakeUserParkingRepository.kt` | **exento** — cita los ids (`mock_vehicle_00x`) en un comentario, no el apodo |
| `fakes/data/repository/FakeOtherDataSources.kt` | **exento** — constante privada con la MAC del BT, no un nombre |
| `domain/notification/AppNotificationManager.kt` | **exento** — KDoc que documenta el formato `"Toyota Corolla"` del fallback; describe el contrato, no datos de demo |
| `presentation/home/…/HomeParkingRow.kt` | **exento** — `@Preview` con marca+modelo, ilustra el fallback |
| `commonTest/…/NotifyParkingConfirmationUseCaseTest.kt` | **exento** — fixture propia del test del fallback |
| `commonTest/` · 9 ficheros con `"Garaje"` / `"Seat León"` | **exento** — fixtures de test. No se renderizan nunca y renombrarlas es ruido sin lector |
| `fakes/MockScenario.kt:47` | **exento** — KDoc que cita la marca real del `vehicle_001`, que no ha cambiado |

⚠️ **El primer grep se quedó corto.** Buscar solo los cuatro apodos dejó fuera `"Tú"`, `"Garaje"` y
los `"Seat León"` de la galería — seis sitios que también se renderizan. El barrido bueno fue
`grep` de las seis cadenas a la vez, filtrando comentarios, y repetirlo hasta salir vacío.
