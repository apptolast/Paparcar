# TEST-ARMLABEL-IS-FLAKY-BY-CLASS-INIT-RACE-001 · `ArmLabelTest` falla con NPE según el orden de ejecución, no según el código

**Estado:** 🔵 Abierto · sin rama · **medido** el 06-10-2026 mientras se cerraba
[DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001]. Cierra una pregunta que llevaba abierta desde el 03-09.

## Problema

Tres tests de `ArmLabelTest` fallan con `NullPointerException` de forma **intermitente**:

```
should_cover_everyWord_with_anArm_except_theOne_thatHasNoArm
  NPE: Cannot invoke "ArmEvidence.getLabel()" because "it" is null        (ArmLabelTest.kt:135)
should_attestABoarding_only_when_theArmActuallyWitnessedOne
  NPE: Cannot invoke "ArmEvidence.boardingWitnessedAtMs(long)" because "it" is null   (:72)
should_agree_between_theArm_and_itsWord_on_verifiedDeparture
  NPE: Cannot invoke "ArmEvidence.getDriveAuthorization()" because "arm" is null      (:52)
```

Los tres iteran `ArmEvidence.allArms`, y **algún elemento de esa lista es `null`** en tiempo de
ejecución. `allArms` es una lista escrita a mano de `data object`s y `data class`es — ninguno puede
ser null por construcción.

## La medición que lo decide

| Pasada | Qué cambió respecto a la anterior | `ArmLabelTest` |
|---|---|---|
| 1 | — | 🔴 3 fallos |
| 2 | una constante de config | ✅ |
| 3 | lógica de `DriveProof` | 🔴 3 fallos |
| 4 | **sólo comentarios KDoc** | ✅ |
| 5 | **sólo comentarios KDoc** | 🔴 3 fallos |
| 6, 7 | nada (`--rerun-tasks`) | ✅ ✅ |

⛔ **Entre la 4 y la 5 no cambió una sola línea ejecutable.** Eso descarta que lo cause ningún
cambio de lógica: lo que varía es el ORDEN en que las clases se cargan, que depende de qué tests
fallan antes y de cómo Gradle reparte el trabajo.

Ejecutada **sola**, la clase pasa siempre.

## Por qué importa, y por qué no es sólo molesto

Esta es la nota que [[project_det_lone_sample_both_bars_measured_001]] dejó pendiente el 03-09:

> ⚠️ Observación sin explicar, por si alguien repite el experimento: con la variante **B** aparecieron
> además 2 NPE en `ArmLabelTest` (`getLabel()`/`getDriveAuthorization()` sobre un `null`) que no se
> investigaron — mirarlas antes que nada, porque no se ve la relación.

**No había relación.** Dos intentos distintos de tocar la prueba de conducción, separados por un
mes, toparon con el mismo ruido y uno de ellos lo tomó por una consecuencia de su cambio. Un test
que falla por el orden de ejecución no sólo pierde tiempo: **invalida la medición de cualquiera que
esté midiendo otra cosa**, que es exactamente lo que pasó.

## Señales / datos disponibles

`ArmEvidence.kt`: `sealed interface ArmEvidence` (línea 171) con sus `data object`s en 175-289, y un
`companion object` en 423 cuyo `allArms` los referencia:

```kotlin
val allArms: List<ArmEvidence> = listOf(
    Manual, VerifiedBySpeed(...), VerifiedByVehicleEnter(...), Unverified,
    BoardingAtCar(...), ArrivalHandoff, BoardedAwayFromCar, BtRide(...),
    OwnCarAudio(...), InheritedDrive(..., DriveProofSource.TRACK_WINDOW), DepartureFollowed(...),
)
```

Hipótesis (fundada, **no comprobada**): carrera de inicialización de clase en la JVM entre el
`companion object` y los `data object`s anidados. Si un hilo inicializa el companion mientras otro
está inicializando los objects, el primero ve referencias a medio construir — y `data object`s a
medio construir se leen como `null`. Encaja con que dependa del orden y con que la clase sola pase
siempre.

⚠️ Hay un segundo fichero con `companion object` en la línea 148, antes de la interfaz: conviene
mirar si participa.

## Diseño

Sin decidir. Lo obvio es hacer `allArms` perezoso (`by lazy`) o moverlo fuera del companion, pero
**antes hay que confirmar la causa**, no adivinarla: si es una carrera, `by lazy` la estrecha sin
cerrarla. Reproducirla de forma fiable es el primer trabajo del ticket.

## Criterio de éxito

- 20 pasadas consecutivas de `testAll --rerun-tasks` sin un solo NPE en `ArmLabelTest`.
- Y, para que el criterio signifique algo, un testigo que falle con el código actual: una prueba
  que fuerce el orden de carga que lo rompe.

## Consumidores auditados

Pendiente — se hace al abrir la rama.
