# DET-A-HOLE-THE-SPEED-FIELD-DENIES-IS-STILL-A-HOLE-001

> **Estado:** ✅ **Done** — mergeado a master el 01-09-2026 (squash, `b4f1256c`). Rama y worktree borrados.
> **Origen:** la pregunta que dejó abierta `DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001`
> — *«si un bound de walk-in puede quedarse corto, ¿puede quedarse corto también el del hueco?»*.
> Se midió. La respuesta no es la esperada.

---

## 1. La pregunta, y por qué la respuesta es **no… pero**

**La MAGNITUD del bound del hueco NO se queda corta**, y por una razón estructural que lo distingue
del bound del walk-in: su testigo es **completo**. El walk-in mide el trozo de caminata que el GPS
*llegó a ver* (por eso es un bound inferior); el hueco mide un **silencio**, cuyos dos extremos son
fixes conocidos. No hay parte no vista.

Y lo medido lo confirma: en la única traza con ground-truth de ambos lados
(`Trace_CasaGapAnchor3008`) el bound de campo fue **339 m** para un desplazamiento real ancla→coche
de **158 m**. Generoso, no corto.

**Pero la pregunta correcta no era el tamaño del bound, sino si el hueco se reconoce.** Y ahí sí hay
defecto, y es peor que un bound corto: **hay huecos a los que no se les asigna duda ninguna.**

## 2. El defecto

```kotlin
if (previous != null &&
    previous.speed >= config.minimumTripSpeedMps &&   // ← la VELOCIDAD DECLARADA
    holeMs > config.anchorGapMaxFixGapMs
) holeMs else 0L
```

La puerta pregunta por el campo `speed` del fix anterior. Si un stream degradado reporta **0.00 m/s**
mientras el coche circula —lo normal en fixes de red— entonces `gapMs = 0`: la parada que se abre al
otro lado **no queda marcada como gap-entered**, no degrada el confirm, no dibuja zona. No es una
duda pequeña: **es la ausencia de duda**, y con ella un ancla libre para plantarse exacta.

⛔ **Es la MISMA lección que el proyecto ya aprendió y aplicó a una función de distancia.**
`DET-STOP-MUST-BE-STILL-IN-SPACE-001` dejó de creerse el `speed` declarado para **madurar una
parada**; la puerta del hueco seguía creyéndoselo para **abrir una duda**. Y el KDoc de la puerta
razona explícitamente por qué NO exige accuracy (*«el Doppler es creíble a precisiones que no
pasarían la barra de conducción»*) — es decir, se defendió de pedir demasiado, no de que el campo
mintiera **por lo bajo**.

## 3. Lo medido sobre el corpus (18 trazas, 6.403 fixes de replay)

Barrido de todos los pares de fixes consecutivos con hueco > 45 s:

| | huecos |
|---|---|
| la puerta los RECONOCE (velocidad declarada ≥ 5 m/s) | **14** |
| la puerta los PIERDE (declarada < 5 pero suelo medido ≥ 5 m/s) | **3** |

Los tres perdidos, todos en `Trace_Redmi2808RefutedStillness` — la vuelta a casa de noche sobre fixes
de red, la traza cuyo propio ticket se llama *refuted stillness*:

| hueco | declarada | terreno medido | recorrido | envolventes acc | ¿lo caza `isCorroboratedVehicleHop`? |
|---|---|---|---|---|---|
| 76 239 ms | **0,00** | 11,54 m/s | **879 m** | 136+142+10 = 288 m | ✅ |
| 54 051 ms | **0,00** | 11,86 m/s | **641 m** | 129+104+10 = 243 m | ✅ |
| 59 894 ms | **0,00** | 6,95 m/s | **416 m** | 104+98+10 = 212 m | ✅ |

**Los tres los caza el predicado que el repo YA tiene y YA calibró**, con margen amplio (879 contra
288). No hace falta inventar física.

## 4. El arreglo

```kotlin
val cameFromDriving = previous != null &&
    (
        previous.speed >= config.minimumTripSpeedMps ||
            isCorroboratedVehicleHop(previous, location, config)
        )
```

⛔ **`||`, no sustitución, y el orden importa**: la velocidad declarada sigue PRIMERO e intacta,
porque el Doppler es creíble a precisiones que el test de salto nunca pasaría (el caso del KDoc
original: 17 m/s a 44 m de accuracy). Se añade una segunda forma de probar lo mismo, no se cambia la
primera.

⛔ **Sin caso de uso nuevo, sin física nueva**: `isCorroboratedVehicleHop` ya existe, ya está
calibrada por los dos lados (la deceleración de Galeote pasa; el swing de recuperación de Camelias
falla porque sus envolventes se hinchan justo cuando «se mueve») y ya la usa la puerta hermana.

## 5. Barrido de consumidores

`grep -rn "anchorGapMaxFixGapMs\|stopEnteredAfterGapMs\|gapEntered\|newStopGapMs"` (producción):

| sitio | clasificación |
|---|---|
| `StopTracking:148` (la puerta) | **cerrado** — este ticket |
| `StopTracking:155` (rama «la parada ya estaba abierta») | **exento**: conserva el valor ya sellado; el hueco se decide una vez, al abrir |
| `AnchorTrust:48,52,65,221,256` | **cubierto por convergencia**: transportan el valor, no lo deciden |
| `StageInputs:121` → `UnattendedSaveInput.anchorGapMs` | **cubierto**: presenta |
| `UserConfirmStage:184`, `EvaluateUnattendedParkingSaveUseCase:307` | **cubiertos**: consumen la magnitud vía `walkableInsideGapMeters`, sin cambios |

## 6. Tests y falsación

**2.098 tests, 0 fallos** (`:shared:testDebugUnitTest`) · prod + mock compilan.

⚠️ **El corpus NO cambió de veredicto en ninguna traza** — incluida la que expone el defecto. En
`Trace_Redmi2808RefutedStillness` esos huecos son de mitad de ruta y la parada que abren la deshereda
después la guarda de stillness refutada, así que el pin final es el mismo. **Es un defecto LATENTE,
medido pero sin víctima en el corpus**, y por eso su testigo es unitario y no un replay:

- `should_mark_a_gap_when_the_track_proves_driving_the_speed_field_denied` — los números del hueco
  mayor, 1:1. **Falsado**: revirtiendo el `||` se pone rojo, y sólo él.
- `should_not_mark_a_gap_when_the_movement_is_inside_the_noise_that_produced_it` — la otra mitad, y
  la que impide que esto ensanche de más: 60 m de «movimiento» dentro de 160 m de ruido conjunto no
  prueban nada, la parada sigue sin hueco. Es el caso Camelias contra el que se calibró el predicado.

## 7. Riesgo, dicho

Ensanchar esta puerta marca **más** anclas como gap-entered → más preguntas y más zonas en lugar de
pines exactos silenciosos. Es la dirección segura de la doctrina (*mejor FN que FP*), y en el corpus
no cuesta nada; **su coste real en campo no está medido** y sólo se verá en un viaje sobre un stream
degradado.
