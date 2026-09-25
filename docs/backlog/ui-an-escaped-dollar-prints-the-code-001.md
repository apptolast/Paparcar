# UI-AN-ESCAPED-DOLLAR-PRINTS-THE-CODE-001 · Un dólar escapado imprime el código en pantalla

**Estado:** ✅ Done · mergeado a master en squash

## Problema

La pantalla de **alta y edición de vehículo** pinta código Kotlin literal donde debería ir un
rótulo. Visto en el Pixel 8 el 25-09-2026 grabando material para el vídeo de Shipaton, en
`mockDebug` sobre master:

```
${title.uppercase()} · ${stringResource(Res.string.common_optional).uppercase()}
```

Sale **tres veces** en «Edit vehicle» — sobre *Name*, sobre *Pair Bluetooth* y sobre *Show brand and
model on free spots*. No es un fallo de traducción ni de recursos: es la cadena que el composable
construye.

La causa, en `ui/components/PapSectionHeader.kt:55`:

```kotlin
"${'$'}{title.uppercase()} · ${'$'}{stringResource(Res.string.common_optional).uppercase()}"
```

`${'$'}` es el idioma de Kotlin para emitir un **dólar literal**. Es lo que se usa en plantillas de
Gradle o al escribir código que genera código, donde hay que impedir la interpolación. Aquí la
interpolación era justo lo que se quería, así que la cadena se imprime tal cual.

Y no está solo. El barrido encontró **tres sitios rotos de usuario**, de un mismo commit:

| Sitio | Qué se lee | Qué debería |
|---|---|---|
| `PapSectionHeader.kt:55` | `${title.uppercase()} · ${stringResource(…)}` | `NOMBRE · OPCIONAL` |
| `VehicleRegistrationState.kt:142` | `${band.lowMeters.oneDecimal()} – ${band.highMeters…}` | `1,4 – 1,6` |
| `VehicleRegistrationState.kt:163` | `${tenths / 10},${tenths % 10}` | `1,5` |

Los tres nacieron en `a0f57bf1a` (`VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001`). Y hay **ocho más** en
`MineturStationsSourceTest`, de otro commit distinto (`b67968dc3`): ahí el daño es que el mensaje de
un test que falla dice `asked: $asked` en vez de la lista que se pidió — un fallo que se explica a
sí mismo mal justo cuando hace falta.

## Doctrina violada

Ninguna regla escrita lo prohibía, y por eso ha entrado dos veces desde commits que no se parecen en
nada. La regla que faltaba:

> **`${'$'}` sólo es legítimo dentro de un *raw string*.** En una cadena normal, Kotlin ya ofrece
> `\$` para un dólar literal, así que `${'$'}` ahí es siempre una de dos cosas: una interpolación
> que alguien rompió sin querer, o una forma retorcida de escribir `\$`. En un *raw string*
> (`"""…"""`) es distinto: Kotlin **no** admite `\$` y `${'$'}` es la única salida — por eso el
> patrón del guard de distancias lo usa y está bien.

Es la misma familia que [COPY-APOSTROPHE-IS-NOT-ESCAPED-001] y [COPY-PERCENT-IS-NOT-ESCAPED-001]:
**un escape que produce texto literal donde se esperaba otra cosa**, y que nadie ve hasta que está
en pantalla. Aquellas dos acabaron teniendo su guard
([COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001]); ésta no tenía ninguno.

## Señales / datos disponibles

- El bug es **estático**: se ve leyendo el árbol, no hace falta ejecutar nada. Un `grep` lo
  encuentra entero en un segundo, que es exactamente por qué merece un guard y no una revisión.
- El árbol tiene hoy 14 apariciones: **11 son el fallo**, 1 es legítima (raw string en
  `DistanceUnitCopyGuardrailTest`) y 2 son KDoc de esa misma que la explican.
- `ComposeResourcesEscapeGuardrailTest` ya vigila la familia, pero **sobre los XML de
  `composeResources`**. Este fallo vive en Kotlin, así que aquel guard es ciego a él por
  construcción — no por descuido.

## Diseño

**El arreglo son los 11 sitios**, no sólo el que se vio en pantalla: cerrar la vía donde mordió
dejaría vivos los otros diez.

**El sistema es un guard nuevo**, hermano del de Compose Resources y con la misma forma: recorre el
árbol Kotlin y falla si encuentra `${'$'}` **fuera de un raw string**. Vive en `i18n/` junto al otro
porque la pregunta es la misma —*¿este escape produce lo que el lector espera?*— y así los dos
escapes rotos del proyecto se leen juntos.

La exención no es una lista de ficheros sino una **condición estructural**: dentro de `"""` el idioma
es obligatorio. Eso lo hace verificable en vez de arbitrario, y sobrevive a que alguien añada mañana
otro raw string legítimo.

## Criterio de éxito

- «Edit vehicle» y el alta de vehículo muestran `NAME · OPTIONAL`, no la plantilla.
- La pista de altura muestra `1,4 – 1,6 m`, no su expresión.
- El guard nuevo **falsificado**: con cualquiera de los 11 sitios sin arreglar, falla y nombra
  fichero y línea.
- `./gradlew testAll` en verde.
- Verificado en device, no sólo en test: la pantalla se mira.

## Verificado el 25-09-2026

- Guard **falsificado**: devuelto `PapSectionHeader.kt` a su estado roto,
  `EscapedDollarGuardrailTest` falla y nombra fichero y línea. Restaurado.
- `./gradlew testAll` → **BUILD SUCCESSFUL**.
- En el Pixel 8, «Edit vehicle» se lee **`NICKNAME AND PLATE · OPTIONAL`**,
  **`AUTOMATIC DETECTION · OPTIONAL`** y **`PRIVACY · OPTIONAL`**. La pantalla, no sólo el test.

## Consumidores auditados

`grep` de `${'$'}` sobre `shared/src` y `app/src`, clasificado uno a uno:

| Sitio | Veredicto |
|---|---|
| `ui/components/PapSectionHeader.kt:55` | **cerrado** — el que se vio en pantalla |
| `presentation/vehicleregistration/VehicleRegistrationState.kt:142` | **cerrado** — pista de altura |
| `presentation/vehicleregistration/VehicleRegistrationState.kt:163` | **cerrado** — `oneDecimal()` |
| `commonTest/…/MineturStationsSourceTest.kt` ×8 | **cerrado** — mensajes de assert que no decían el valor |
| `androidUnitTest/…/DistanceUnitCopyGuardrailTest.kt:132` | **exento** — raw string; ahí `${'$'}` es la ÚNICA forma y su KDoc ya lo explica |
| `androidUnitTest/…/DistanceUnitCopyGuardrailTest.kt:115-116` | **exento** — KDoc que documenta la anterior |
