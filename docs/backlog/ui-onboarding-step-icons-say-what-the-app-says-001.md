# UI-ONBOARDING-STEP-ICONS-SAY-WHAT-THE-APP-SAYS-001 · Los tres pasos de "Cómo funciona" llevan glifos que en el resto de la app significan otra cosa

**Estado:** ✅ Done — en master

## Problema

La página 2 del onboarding (`onboarding_page2_title` = "How it works") enseña las tres frases que
definen el producto, cada una con un icono. Los tres glifos están cogidos por parecido semántico
con el texto ("parking → una P", "spot → un pin"), no por el significado que ya tienen dentro de la
app. Resultado medido en device (captura del 08-09-2026): los pasos 2 y 3 llevan glifos cruzados.

| Paso | Copy | Icono hoy | Qué dice ese glifo en el RESTO de la app |
|---|---|---|---|
| 1 | *We detect driving* | `Icons.Rounded.DirectionsCar` | **tu coche / "ir a mi aparcamiento"** — `HomeMapFab.kt:69`, `MapControlButtons.kt:69`, `HomeDetectionSurface.kt:176`. No dice "circulando". |
| 2 | *We detect parking* | `PaparcarIcons.ParkingPlace` (= `LocalParking`, la P) | **POI de parking público** — `PaparcarIcons.kt:292`, único dueño legítimo del glifo. |
| 3 | *We share the spot* | `Icons.Rounded.LocationOn` | **el pin de TU aparcamiento** — `HomeParkingRow.kt:167,270`. |

Es decir: el paso que habla de **lo tuyo** (tu aparcamiento) lleva la señalética de un parking
**público**, y el paso que habla de **lo de la comunidad** (la plaza que liberas) lleva el pin de
**lo tuyo**. Es el mismo cruce que `COPY-SPOT-IS-NOT-A-PARKING-001` arregló en el texto y dejó
intacto en los glifos.

## Doctrina violada

1. **La regla escrita de la "P"**, en dos comentarios del propio código:
   - `HomeDetectionSurface.kt:173` — *"The car, not a parking 'P': the app's whole visual vocabulary
     for 'your car' is the car glyph. **A P would be the first place in the app where parking is
     drawn as signage**."*
   - `MapControlButtons.kt:38` — *"⛔ The recenter button is NOT a 'P': `Icons.Rounded.LocalParking`
     is already spoken for as `PaparcarIcons.ParkingPlace`, the POI category for a public car park."*

   El onboarding **era** esa primera excepción que ambos comentarios daban por inexistente.

2. **`COPY-SPOT-IS-NOT-A-PARKING-001`** — PLAZA es de la comunidad, APARCAMIENTO es tuyo. La regla
   se escribió para el copy, pero un glifo también nombra: aquí el icono contradice a la frase que
   acompaña.

3. **`UI-COLOR-DOCTRINE-001` / nivel 2 de iconos** — un glifo Material tiene UN significado en toda
   la app; elegirlo por su parecido con la palabra del título es lo que produce dos significados
   para el mismo dibujo.

## Por qué sobrevivió al barrido anterior

`UI-HISTORY-MAP-FABS-MUST-MATCH-HOME-001` hizo este mismo barrido hace días y concluyó:

> `LocalParking` sólo se usa en un sitio como glifo directo (`grep -rn "Icons.Rounded.LocalParking"`
> → 1 hit en `MapControlButtons.kt`, más su alias legítimo en `PaparcarIcons.ParkingPlace`).

El grep buscaba el **símbolo literal**. El onboarding no lo usa: usa `PaparcarIcons.ParkingPlace`,
que es precisamente para lo que existe el namespace de iconos. Un barrido sobre un glifo con alias
tiene que buscar **las dos formas** — lección aplicable a cualquier futuro barrido de
`PaparcarIcons.*`.

## Señales / datos disponibles

Vocabulario vivo, leído del código (no del catálogo Material):

| Concepto | Glifo que ya lleva | Dónde lo ve el usuario |
|---|---|---|
| Este coche está circulando ahora | `Icons.Rounded.Navigation` | `HomeDetectionSurface.kt:277` (`DetectionStory.Driving`) |
| Tu coche / tu aparcamiento | `Icons.Rounded.DirectionsCar` | FAB de Home, FAB del historial, fila de pregunta de detección |
| Publicar/avisar una plaza a la comunidad | `Icons.Rounded.Campaign` | `HomeReleaseDialog.kt:37` (¡el diálogo de liberar plaza!), `HomeSpotRows.kt:322,395` |
| Detección activa / inactiva | `Sensors` / `SensorsOff` | Ajustes, Permisos, `FirstStepExplainer` |
| POI parking público | `LocalParking` (la P) | `PlaceCategory.PARKING` |

## Diseño

Una **rotación**, no tres elecciones nuevas: cada paso pasa a llevar el glifo que ese concepto ya
lleva en la app.

| Paso | Antes | Ahora | Razón |
|---|---|---|---|
| 1 · We detect driving | `DirectionsCar` | **`Navigation`** | El glifo de `DetectionStory.Driving`: el usuario lo verá en Home la primera vez que conduzca. El coche quieto no dice "conduciendo". |
| 2 · We detect parking | `ParkingPlace` (P) | **`DirectionsCar`** | Tu aparcamiento se dibuja con tu coche en toda la app. Deja de haber señalética. |
| 3 · We share the spot | `LocationOn` | **`Campaign`** | El megáfono es el glifo de "avisar de una plaza" y el del diálogo que libera la tuya al irte: es literalmente la pantalla que este paso promete. |

Lee como progresión: **la flecha se mueve → el coche descansa → el megáfono avisa**. Y la P queda
otra vez con un solo dueño.

Alternativas descartadas:
- **Paso 1 con `Sensors`**: el héroe de la propia página ya es un coche con ondas de radar; repetir
  el radar dos veces en la misma pantalla no añade nada, y `Navigation` completa la historia.
- **Paso 3 con `PaparcarIcons.ParkingBay`**: está documentado como "marcador de plaza comunitaria",
  pero **no lo usa nadie** (`grep` → sólo su declaración; los marcadores son canvas). No es
  vocabulario que el usuario reconozca; `Campaign` sí lo ha visto.

Sin cambios de copy, de strings ni de layout: sólo tres `ImageVector` y sus imports.

## Criterio de éxito

- La página 2 del onboarding muestra flecha / coche / megáfono, y cada glifo coincide con el que el
  usuario encontrará después en Home para ese mismo concepto.
- `Icons.Rounded.LocalParking` y su alias `PaparcarIcons.ParkingPlace` quedan usados **sólo** por
  `PlaceCategory.PARKING` en código de producción.
- Compila `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` y la suite sigue verde.

## Consumidores auditados

`grep -rn "LocalParking\|ParkingPlace"` — **las dos formas**, literal y alias:

| Sitio | Qué es | Veredicto |
|---|---|---|
| `PaparcarIcons.kt:87,292` (`ParkingPlace` → `PlaceCategory.PARKING`) | dueño legítimo del glifo | ✅ único uso en producción tras este ticket |
| `OnboardingScreen.kt:186` | tu aparcamiento dibujado como señal de parking público | ✅ cerrado por este ticket |
| `MapControlButtons.kt:38` | comentario que prohíbe la P | ✅ ya cerrado por `UI-HISTORY-MAP-FABS-MUST-MATCH-HOME-001` |
| `HomeDetectionSurface.kt:173` | comentario que prohíbe la P | ✅ ya cerrado por `DET-ASK-STATE-001` |
| `DetectionReadinessDesignPreviews.kt:134,156`, `PeekTimeRowOptionsPreviews.kt:173,249` | `@Preview` de exploración de diseño, `androidMain` | ⚪ exentos: no se renderizan en la app |
| `PaparcarMapMarkersPreviews.kt:175`, `StateGalleryScreen.kt:581` (`ZoneCenterPin`) | icono de muestra de un pin de zona | ⚪ exentos: `zoneIconFor` (`ZoneIconMap.kt`) **no expone** `LocalParking`, así que ninguna zona real puede llevar esa P |

`grep -rn "Icons.Rounded.LocationOn"` — el pin sigue significando "sitio exacto" en
`HomeParkingRow` y `HomeGpsAccuracyBanner`; el onboarding era el único sitio donde nombraba a la
comunidad. ✅ cerrado.

## Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` + `:shared:testDebugUnitTest` → verde.
- **En device (08-09-2026, emulador, flavor `mock`, preset "Onboarding" del Dev Catalog):** el coche
  y el megáfono se leen limpios a 36 dp. La flecha del paso 1 **no se pudo dar por vista**: el chip
  `DEV` de `DevRoot.kt:114` cae justo encima y tapa ~60% del glifo. Ese chip no existe en prod, pero
  lo verificado es "está la flecha", no "la flecha se lee bien" — queda pendiente de verla en un
  build `prodDebug`. No se pudo instalar prod en el emulador: lleva `com.rndeveloper.paparcar` 1.0.0
  firmado con `7853cf40` (build de Play) → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, y no se desinstala
  nada por cuenta propia.

## Fuera de alcance

- La página 2 no tiene estado ni routing: no toca Dev Catalog (`StateGalleryScreen` no tiene grupo
  de onboarding y este ticket no añade pantalla ni estado nuevo).
- `PaparcarIcons.ParkingBay` / `SpotPin` / `SpotParkingP` están declarados y **sin usar** desde que
  los marcadores pasaron a canvas. Es código muerto, pero borrarlo es otra tarea.
