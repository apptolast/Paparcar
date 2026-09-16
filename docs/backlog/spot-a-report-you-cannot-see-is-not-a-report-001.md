# SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001 · Avisar de una plaza a 40 km no estaba prohibido: no estaba ni medido

**Estado:** ✅ Done — en master (squash, 16-09-2026) · ⏳ falta verlo en device
**Origen:** user, 16-09-2026 — *«cuando reportamos plaza lejos de nuestra ubicación, no mostramos
nada al usuario para indicar que no puede»*.

## Problema

Dos cosas, una gorda y una de una línea.

**1 · No es que no lo dijéramos: es que sí se podía.** Se recorrió el camino entero
`ReportPeek → ConfirmReportSpot → ReportManualSpotUseCase → ReportSpotReleasedUseCase → ReportSpotWorker
→ Firestore` y **no hay ninguna comparación entre el fix del usuario y el punto del pin**. Lo único
que se comprueba:

| Paso | Fichero | Qué valida |
|---|---|---|
| Card de la sheet | `HomeSheetContent.kt:616` | `slice.userGpsPoint != null` — ¿HAY fix?, no ¿dónde? |
| Botón del peek | `ReportPeek.kt:77` | cámara quieta + no hay envío en vuelo |
| VM | `HomeViewModel.kt:1151-1152` | modo `Reporting`, reentrada, y que exista pin |
| Use case | `ReportManualSpotUseCase.kt:31-50` | carrocería del vehículo activo — nada geográfico |
| Worker | `ReportSpotWorker.kt:51,98` | `lat/lon` no NaN + TTL en cola |
| Reglas | `firestore.rules:16-20` | `reportedBy == request.auth.uid` |

El pin del modo reporte es el CENTRO DE CÁMARA (`updatePinDuringMode`, `HomeViewModel.kt:559`), así
que paneando el mapa se publica una plaza en otra ciudad, con tu UID, y les sale a los conductores de
allí. El usuario creía que la app se lo impedía en silencio; la app se lo permitía en silencio.

**2 · La razón de la card se corta a media frase.** `HomeSpotRows.kt:387`:
`subtitleMaxLines = if (enabled) 1 else 2` — «Una plaza libre que has visto y quieres compartir.»
entra en una línea sólo en pantallas anchas; en el resto muere en «…».

## Doctrina violada

- **Fallo asimétrico: mejor falso negativo que falso positivo.** Una plaza fantasma a 40 km es
  exactamente el «viaje en balde» que la doctrina de detección prohíbe plantar — y aquí lo plantaba
  un humano, sin que ningún guard mirase.
- **Copy con causa + consecuencia + remedio.** Un control que no se puede usar tiene que DECIR por
  qué; el proyecto ya lo escribió para esta misma card sin GPS
  ([HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]) y el modo reporte se quedó sin su mitad.
- **Sistemas, no parches.** La app YA tiene este invariante escrito para los votos
  (`SpotVotePolicy.MAX_VOTE_DISTANCE_METERS = 50.0`, `SpotVote.kt:49`) con el razonamiento entero:
  *sólo quien está allí puede hablar de esa plaza*. Reportar es la MISMA afirmación —y más cara,
  porque crea el pin en vez de retirarlo— y no tenía regla.

## Señales / datos disponibles

Todo, y ya en el mismo estado: `HomeState.userGpsPoint` (el fix) y `pinCameraLat/Lon` (el pin), que
es exactamente el par que `submitSpotSignal` (`HomeViewModel.kt:1182-1190`) ya mide para el voto.
No hace falta telemetría nueva ni un permiso nuevo.

## Diseño

**El número vive en UN sitio y es de dominio.** `domain/model/SpotReport.kt` (hermano de
`SpotVote.kt`, misma carpeta y mismo porqué):

```kotlin
object SpotReportPolicy {
    const val MAX_REPORT_DISTANCE_METERS = 200.0
    fun gateFor(distanceMeters: Double?): SpotReportGate
}
enum class SpotReportGate { ALLOWED, NO_FIX, TOO_FAR }
```

- **200 m, no los 50 del voto** (decisión del user, 16-09): votar es certificar lo que tienes
  delante; avisar de una plaza incluye la que acabas de ver al pasar y la que dejas mientras te
  alejas andando. 50 m mataría el caso normal; 500 m sólo frenaría el «otra ciudad».
- **Es un GATE, no un predicado escondido**: su resultado cambia lo que ve el usuario (botón y
  frase), así que es citable. Tres valores porque hay **dos frases distintas**: «no sé dónde estás»
  no es «estás lejos», y decir una por la otra es mentir.
- **Se mide donde viven las dos puntas**: `HomeState.reportGate()` en `HomeStateTransitions.kt`,
  junto a `pinCoordinates()`, que ya es el guard compartido de los tres modos-pin. Así ningún call
  site puede saltárselo por olvidar pasar la distancia — el mismo argumento escrito en el voto.
- **Feedback en vivo** (decisión del user): mientras paneas, el banner del peek cambia a la causa y
  el remedio, y «Confirmar plaza aquí» se apaga. Mismo patrón que el gate de GPS que ya existe en la
  card. El `when` del VM se queda igualmente como última valla: entre que la cámara se posa y pulsas,
  el fix puede moverse → `PaparcarError.Spot.TooFarToReport` y snackbar.
- **La distancia del copy NO se escribe a mano**: va por `formatDistance(…, LocalDistanceUnit.current)`
  como `%1$s`, para no repetir el bug de los cuatro «km» de
  [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001] (ticket hermano, abierto).
- `PaparcarError.Spot` nace aquí: la familia `Parking` no vale — **plaza no es aparcamiento**
  ([COPY-SPOT-IS-NOT-A-PARKING-001]), y meter el error de lo comunitario en el sealed de lo tuyo
  volvería a confundir los dos conceptos justo en el sitio donde el vocabulario ya se separó.

**La card (problema 2)**: `subtitleMaxLines = 2` también habilitada. La frase cabe entera en dos
líneas en el ancho más estrecho que soportamos, y la card ya reservaba dos para su versión
deshabilitada — la altura no es nueva.

## Criterio de éxito

- Con el mapa paneado más allá de 200 m del fix, «Confirmar plaza aquí» está apagado y el peek dice
  por qué y cómo arreglarlo; al volver, se enciende solo.
- `confirmReportSpot()` rechaza y avisa aunque se le llame lejos (test de VM, sin pasar por la UI).
- `SpotReportPolicy` con su test: null → `NO_FIX`, 200.0 → `ALLOWED` (el límite entra), 200.1 →
  `TOO_FAR`.
- La descripción de la card se lee entera en un móvil estrecho.
- 9 locales al día · `LocaleParityGuardrailTest` y `ComposeResourcesEscapeGuardrailTest` verdes.
- Galería de estados con las dos variantes nuevas del peek (permitido / demasiado lejos).

## Lado iOS

**Nada que abrir**: todo lo de este ticket vive en `commonMain` — la política
(`domain/model/SpotReport.kt`), la medida (`HomeStateTransitions`), el gate del VM, el peek y las
nueve traducciones. iOS hereda el gate entero el día que compile la app; no hay una sola línea en
`androidMain` ni un permiso o API de plataforma involucrado.

## Estado de la verificación (16-09-2026)

- `./gradlew testAll` ✅ · `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` ✅
- Guards forzados con `--rerun-tasks` y **con testigo** (28 tests, 0 fallos):
  `LocaleParityGuardrailTest` (5) · `ComposeResourcesEscapeGuardrailTest` (3) ·
  `TypographyGuardrailTest` (5) · `ColorGuardrailTest` (8) · `ImportGuardrailTest` (1) ·
  `SpotReportPolicyTest` (6).
- `HomeViewModelTest` 157 tests, 0 fallos, con los tres nuevos ejecutándose por nombre.
- ⚠️ **Tres tests del reporte publicaban SIN fix**: `EnterReportMode(40.0, -3.7)` con
  `userGpsPoint` null y el fake del fix a 46 km. Pasaban porque no había gate. Ahora entran por
  `enterReportModeAtUser()`, que emite permisos + ubicación y pone el pin donde está el usuario —
  es decir, la suite tampoco estaba probando el camino de publicación que creía probar.
- ⏳ Pendiente: verlo en device (banner, botón apagado y la card a dos líneas).

## Consumidores auditados

`grep` de todo lo que publica una plaza manual o asume que el pin puede estar donde sea:

| Sitio | Veredicto |
|---|---|
| `HomeViewModel.confirmReportSpot()` | **cerrado** — gate añadido |
| `ReportPeek` (botón + banner) | **cerrado** — apagado + razón en vivo |
| `HomeSheetContent.kt:616` (card sin fix) | **cubierto** — su gate es anterior (¿hay fix?) y sigue siendo correcto; el de distancia no puede vivir ahí porque aún no hay pin |
| `ReportManualSpotUseCase` / `ReportSpotReleasedUseCase` | **exentos** — un solo consumidor (`HomeViewModel`, verificado por grep); publican lo que la detección confirma y lo que el gate ya aprobó. Meter ahí el fix del usuario obligaría a inyectar ubicación en el camino de la detección automática, que no tiene esta pregunta |
| `reportTestSpot()` (`HomeViewModel.kt:1207`) | **exento** — `if (!isDebugBuild) return`, es el botón de laboratorio |
| Publicación automática al salir (detección) | **exento** — ahí la plaza la libera TU coche, medida por el propio motor; el usuario no elige el punto |
| `SpotVotePolicy` (50 m) | **intacto** — otra pregunta, otro número, y el doc de `SpotVote.kt` ya explica por qué no se comparten |
