# MOCK-A-SHOT-SESSION-MUST-REACH-EVERY-SCREEN-001 · una sesión de captura tiene que poder llegar a cualquier pantalla

**Estado:** ✅ Done · rama `chore/MOCK-A-SHOT-SESSION-MUST-REACH-EVERY-SCREEN-001-registration-shot` · worktree `../Paparcar-shot-registration`

## Problema

Grabando el vídeo del Shipaton hacía falta el **alta de vehículo**, la primera pantalla que ve
quien se instala la app. No hay `ShotPreset` que llegue a ella. Se puede alcanzar por el botón
del Dev Catalog «Registro de vehículo (sin coche)» — pero entrando por ahí la grabación sale
con los **chips DEV encima de la pantalla**, porque `DevRoot:109` sólo los apaga cuando
`shotSession` es cierto, y `shotSession` sólo es cierto si se entró por `--es shot`.

Resultado medido: toma `registro-v1.mp4` con el badge «DEV» y el interruptor de tema pintados
sobre la columna izquierda del formulario. Inservible para publicar.

## Doctrina violada

El KDoc de `ShotPresets` dice para qué existe el fichero:

> *the only way that is reproducible is if the SCENARIO is chosen by the launch intent instead
> of by tapping a list whose rows move*

Y en `DevRoot`: *«a Play capture cannot show dev chrome»*. Las dos frases describen el mismo
sistema — **una sesión de captura es limpia y se pide por intent** — y ese sistema tenía un
agujero: una pantalla del arranque a la que el intent no llegaba. El botón del catálogo no lo
tapa, porque el botón es justo la vía que ensucia la captura.

No es «falta un preset»: es que la lista de presets se escribió para los **8 frames de la ficha
de Play** y nunca se revisó contra «todas las pantallas que alguien pueda necesitar grabar».

## Señales / datos disponibles

- `MockScenario.Session.LoggedInNoVehicle` ya enruta a `VehicleRegistration` — el estado existe,
  sólo no era alcanzable por intent.
- `DevMainActivity` ya lee `--es shot` y pone `shotSession = true`; no hay nada que construir
  ahí.

## El segundo agujero, encontrado al grabar: R8

Con el preset puesto, el vídeo seguía grabándose en **debug**, y el user lo notó en pantalla:
«la de home cuando pones fuel stations al clickar no cambia el tap, tarda un poco». Un build
de depuración no da la fluidez que hay que enseñar.

Pero el flavor mock **no se podía grabar en release**, y `app/build.gradle.kts:164-177` ya lo
tenía escrito desde el 25-09: R8 renombra las constantes del enum —`Enum.name` es un nombre de
campo como cualquier otro—, así que `ShotPreset.from("map")` devuelve `null` para todas y la
sesión **no falla: graba lo que hubiera en pantalla**. La salida de entonces fue `-PmockAsProd`,
que firma el *debug* con la llave de release.

Es el MISMO invariante que este ticket: una sesión de captura tiene que llegar a la pantalla
que se le pide. Bajo R8 no llegaba a ninguna. Va aquí, no en un ticket aparte.

```proguard
-keep enum com.rndeveloper.paparcar.dev.ShotPreset { *; }
```

⛔ El nombre de cada constante es un **dato** que cruza el límite del proceso por
`--es shot <key>`, no un símbolo interno. Confundir las dos cosas es exactamente lo que hace
R8, y por qué el fallo era mudo.

Medido tras la regla: `assembleMockRelease` → `flags=0x0` (no depurable), presets honrados, y
el mapa pinta con el paquete propio `.mock` desde que el user añadió el par
(`com.rndeveloper.paparcar.mock`, SHA-1 `45:40:9F:…:BF:97`) a la clave de Maps — con lo que
`-PmockAsProd`, que hacía pasar el mock por producción, deja de hacer falta para grabar.

## Diseño

Un valor más en el enum y su rama en `applyTo`, más la regla de R8 de arriba. El mecanismo ya
estaba; le faltaban una entrada y sobrevivir a la ofuscación.

```kotlin
ShotPreset.Registration -> {
    scenario.session.value = MockScenario.Session.LoggedInNoVehicle
}
```

Se graba con:

```
adb shell am start -n com.rndeveloper.paparcar.mock/com.rndeveloper.paparcar.DevMainActivity \
  --es shot registration
```

## Criterio de éxito

- ✅ La pantalla de alta de vehículo se graba **sin un solo píxel de chrome DEV**.
- ✅ `assembleMockRelease` arranca en el preset pedido (antes caía en Home con el catálogo).
- ✅ `./gradlew testAll` en verde · `:app:assembleMockDebug` y `:app:assembleMockRelease`
  compilan e instalan.

## Consumidores auditados

`ShotPreset` se consume en tres sitios, y ninguno enumera exhaustivamente:

| Sitio | Veredicto |
|---|---|
| `ShotPreset.from(key)` | **cubierto** — resuelve por nombre sobre `entries`, el valor nuevo entra solo |
| `applyTo()` | **cerrado** — `when` exhaustivo sobre el enum: el compilador exige la rama nueva, que es la razón de que sea un `when` sin `else` |
| `DevMainActivity` | **cubierto** — sólo llama a `from()` + `applyTo()` |
| `app/proguard-rules.pro` | **cerrado** — la regla `-keep` sobre el enum; la clase vive en `:mockkit` y no entra en prod, así que la regla no afecta al binario de producción |

⚠️ **Ramas apiladas**: esta rama sale de `master`, igual que
MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001, y las dos tocan `:mockkit` sin solaparse en
ningún fichero. Se mergean en cualquier orden; la segunda rebasa sobre master antes de entrar.
