# HOME-AN-UNMARKED-CAR-HAS-NO-ADDRESS-001 · un coche sin marcar no tiene dirección, y el peek le prestaba la de la cámara

**Estado:** ✅ Done · mergeado en master en su propio commit (squash) ·
⏳ pendiente de verlo en device — la captura que pide [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]

## Problema
Con la hoja plegada y un coche **sin aparcamiento marcado**, `BrowsePeek` dibujaba:

```
[glifo coche]  NIRO · SIN MARCAR
               Calle Mayor 12                        ← título
               Desliza ↑ para marcar tu aparcamiento
```

Es la **misma anatomía exacta** que la rama de coche APARCADO que vive 20 líneas más arriba en el
mismo fichero: mismo `PapSheetLead.Vehicle`, mismo eyebrow `nombre · estado`, mismo slot de título.
Lo único que separa «aparcado» de «sin marcar» son dos palabras en caps de 11 sp. El slot grande,
que en la rama hermana **es dónde está el coche**, aquí lo rellenaba otra cosa — y se leía como la
dirección del coche.

Segundo defecto, encima del primero: ese título salía de `cameraInfo`
(`slice.cameraAddressAndPlace` ← `GeocodeUpdate.CameraAddress`, `HomeViewModel.kt:1735`), el
geocode **de la cámara**. O sea que **paneando el mapa se reescribía la calle bajo el nombre del
coche**: el coche «se movía» sin que nadie lo tocara.

## Doctrina violada
*Si el sujeto de la cabecera es un COCHE, el título nombra al coche — nunca a la cámara.*

No es una regla nueva: es la tercera reincidencia de la misma familia, y las otras dos ya están
arregladas en este mismo fichero.

| Rama | Sujeto | Título | Estado |
|---|---|---|---|
| viaje vivo | el coche que conduces | `drivingPlace` — geocode DEL COCHE | ✅ [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001] |
| aparcado | el coche aparcado | `parking.address` — la SESIÓN, estática | ✅ [UI-SHEET-004] («un coche aparcado en dos sitios») |
| **sin marcar** | el coche sin sesión | **`cameraInfo`** | 🔴 este ticket |
| carril / zona | el carril | `cameraInfo` | ✅ correcto: su sujeto ES la cámara |

Un coche sin marcar **no tiene un «dónde»** — eso es justo lo que le pasa. Por eso no puede pedirlo
prestado: la única dirección disponible es la de otro sujeto.

## Diseño
El título deja de nombrar un lugar y dice el ESTADO; el eyebrow suelta el estado (que ahora vive en
el título) y se queda con el sujeto; el sub reescribe el hint para que las tres líneas no digan lo
mismo tres veces.

```
[glifo coche]  KIA NIRO                              ← eyebrow: el sujeto
               Sin aparcamiento marcado              ← título: el estado
               Desliza ↑ para marcar dónde has aparcado   ← sub: el gesto
```

**El invariante se vuelve estructural, no un comentario.** Las tres ramas de sujeto-coche salen a
composables privados propios (`DrivingCarHeader`, `ParkedCarHeader`, `UnmarkedCarHeader`) cuya
firma **no incluye `cameraInfo`**: `BrowsePeek` conserva ese parámetro sólo para la rama del carril,
que es su dueña legítima. Volver a colar la cámara bajo un nombre de coche exige ampliar una firma a
propósito, no olvidarse de un comentario. Es el mismo mecanismo que ya separó `drivingPlace` de la
cámara, llevado a las tres.

Vocabulario [COPY-SPOT-IS-NOT-A-PARKING-001]: lo que falta por marcar es **TU APARCAMIENTO**, nunca
una plaza. El sub dice el verbo de lo tuyo («aparcar»), no el de lo comunitario («avisar de una
plaza»), que es justo el hint hermano del mismo peek.

## Strings
- **Nueva** `home_peek_parking_unmarked_title` → los 9 locales.
- **Reescrita** `home_browse_hint_swipe_mark_parking` (call site ÚNICO, verificado por grep) → los 9
  locales. El texto viejo repetía «aparcamiento» a dos líneas del título nuevo.
- `home_vehicle_chip_unmarked` y `home_vehicle_chip_mark_parking` **dejan de usarse aquí** (el
  estado ya no va en el eyebrow, y el título ya no tiene fallback de cámara que cubrir). Ambas
  siguen vivas en `HomeParkingRow.kt`, que es de donde salieron.

Sin género gramatical en ninguna traducción: el sujeto puede ser una moto, y «dónde lo has dejado»
sólo vale para un coche. Por eso el verbo va impersonal o en 2.ª persona.

## Criterio de éxito
- Con la hoja plegada y un coche sin marcar, **ninguna calle** aparece bajo el nombre del coche.
- **Panear el mapa no cambia una sola letra** de esa cabecera.
- La cabecera del carril (sujeto = cámara) sigue titulando con la calle, y sigue cambiando al panear.
- Galería mock: la variante «cerrado sobre carril vacío, coche SIN aparcar» enseña el estado nuevo.

**Testigo, falsificado:** borrada la key nueva de `values-ro`,
`LocaleParityGuardrailTest > every locale declares the same keys as the English base` **FALLA**
(`LocaleParityGuardrailTest.kt:47`, `BUILD FAILED`); restaurada, verde. O sea que el guard SÍ mira
esta key, no pasa de largo.

**Testigo:** el invariante lo sostiene una FIRMA (los tres composables de coche no pueden ver
`cameraInfo`), no un test — no hay lógica pura nueva que testear, el cambio es copy + estructura de
Compose. Lo que sí queda vigilado por suite: `LocaleParityGuardrailTest` (la key nueva en los 9) y
`ComposeResourcesEscapeGuardrailTest` (apóstrofos crudos en FR/IT/EN).

## Consumidores auditados
| Sitio | Veredicto |
|---|---|
| `BrowsePeek.kt` rama sin marcar | 🔧 **el bug** — reescrita |
| `BrowsePeek.kt` ramas viaje / aparcado | ✅ ya cumplían; salen a su propio composable para que siga siendo cierto |
| `BrowsePeek.kt` rama carril / zona | ✅ exenta: su sujeto ES la cámara, conserva `cameraInfo` |
| `AddingParkingPeek` · `ReportPeek` · `AddingZonePeek` (`settlingCameraTitle()`) | ✅ exentos: su sujeto es el PIN que estás colocando, que literalmente es el punto de la cámara |
| `HomeParkingRow.kt` (chips de vehículo) | ✅ no titula con dirección; sigue usando las dos keys que el peek suelta |
| `StateGalleryScreen.kt` | ✅ ya tenía la variante; se verifica que sigue representativa |
| `BrowseHeaderSubjectTest` | ✅ decide QUIÉN habla, no QUÉ dice: intacto |
| iOS | ✅ `commonMain` puro, sin hueco que escribir |
| `paparcar.com` | ✅ no dispara: ninguna feature que la web nombre cambia |

## Hecho
- `BrowsePeek.kt` partido: `DrivingCarHeader` · `ParkedCarHeader` · `UnmarkedCarHeader`, ninguno con
  `cameraInfo` en su firma. La rama del carril lo conserva, que es su sujeto.
- `UnmarkedCarHeader` reescrito: eyebrow = nombre del coche, título = estado, sub = gesto.
- 9 locales: key nueva + hint reescrito (el viejo repetía «aparcamiento» a dos líneas del título).
- Dos comentarios que ya mentían, corregidos de paso: la rama de viaje vivo seguía diciendo que su
  dirección venía «via the camera geocode» (falso desde [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]), y la
  de sin-marcar razonaba sobre un fallback de título que este ticket elimina.

## Verificado
- `./gradlew testAll` ✅ · `:app:compileProdDebugKotlin` ✅ · `:app:compileMockDebugKotlin` ✅
- Guards forzados con `--rerun-tasks` (salen UP-TO-DATE si no): LocaleParity, ComposeResourcesEscape,
  Typography, ColorGuardrail ✅
- ⏳ **Sin ver en device.** [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001] pide captura del estado final:
  galería mock → «PapSheet · cerrado sobre carril vacío, coche SIN aparcar».
