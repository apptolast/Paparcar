# 10 — Plan de ejecución del refactor (Fase 5)

> Escrito 2026-08-22 sobre master `8bf6f02b` (CPD 3.162 líneas). Entrada: `09-arquitectura-objetivo.md`
> (árbol destino + §14 adjudicaciones + §15 tabla ejecutable), `07-duplicacion.md` (§2 helpers, §4
> ruido), `06-invariantes.md` (el contrato), `11-bugs-encontrados.md` (lo que NO se arregla aquí).
>
> ⛔ Este plan **no autoriza código**. F6 sigue bloqueada por la validación de campo
> [DET-VERDICT-NOT-PREDICATE-001] y por la aprobación explícita de este documento.

---

## §0 · Las seis reglas que gobiernan cada paso

Si un paso viola una de estas, el paso está mal escrito, no la regla.

1. **Símbolos, nunca líneas.** Un paso dice *«mueve `outrunsPedestrianReach` a `physics/`»*, jamás
   *«mueve CPD:2503-2554»*. Los números de línea de los docs 02/06/08/09 caducan con cada commit de
   detección — en los últimos cinco días master ha movido el CPD cuatro veces. Un plan anclado a
   líneas nace muerto; uno anclado a símbolos sobrevive a la sesión paralela.
2. **Un paso = un commit, con la suite entera en verde.** Nada de "lo arreglo en el siguiente".
3. **Ningún assert se edita.** Los tests existentes pasan tal cual contra el árbol nuevo. Si un
   assert hay que tocarlo, eso **no es un refactor: es un cambio de conducta**. El paso se para y se
   pregunta [09 §12.1].
4. **No se arreglan bugs dentro de un paso de refactor.** Si aparece uno, va a
   `11-bugs-encontrados.md` con ticket propio y el paso sigue [plan maestro §7.4].
5. **Cada paso lleva marca de riesgo**: **`M`** = movimiento puro (mismo código, otra casa) ·
   **`L`** = toca lógica sin cambiar conducta observable · **`C`** = cambia conducta (necesita
   aprobación previa y validación de campo propia).
6. **Cada paso dice cómo se revierte.** Un `git revert` limpio es el criterio por defecto; si un paso
   no se puede revertir solo, está mal cortado y hay que partirlo.

**Regla de oro del orden:** primero lo que no puede romperse, al final lo que sostiene todo. El
`AnchorTrust` es lo último de su fase porque es el mayor y el más cargado de incidentes de campo.

---

## §1 · Puertas de entrada

Ninguna fase arranca sin su puerta.

| Puerta | Qué exige | Estado a 22-08 |
|---|---|---|
| **G1 — Campo** | Los commits de detección pendientes validados con un viaje real | 🔴 **~15 commits sin validar.** Es el bloqueo dominante |
| **G2 — Aprobación** | `09-arquitectura-objetivo.md` y este plan aprobados | 🟠 Pendiente |
| **G3 — Red** | Fase 0 completa: la conducta de hoy fijada por tests | 🔴 Sin empezar |
| **G4 — Árbol quieto** | Sin worktrees de detección con cambios sin commitear | 🔴 `../Paparcar-cheap-wake` en vuelo (toca `SentryWakeCooldown`, el servicio y el config) |

G4 merece énfasis: **el refactor y los tickets de campo compiten por el mismo fichero.** Mientras haya
tickets de detección en vuelo, cada paso de F6 nace con conflicto garantizado. La secuencia sana es
*ticket de campo → merge → paso de refactor → merge*, nunca en paralelo.

---

## §2 · FASE 0 — La red de seguridad

**Ni una línea de producción.** Esta fase existe porque el paso siguiente mueve tres mil líneas de
código cuyo comportamiento correcto está definido por incidentes de campo, no por especificación.

### P0.1 · Test del orden de precedencia, sobre el código de HOY `L`

**El problema:** hoy la precedencia es el orden físico de las ramas dentro del `collect`. No hay
ningún test que diga «el hold gana al user-confirm», y el KDoc que lo documenta **miente**
(02 §4, 08 §10.3). Si el orden se altera al mover, nada lo detecta.

- **Qué toca:** solo `commonTest`. Un fichero nuevo, `StagePrecedenceCharacterizationTest`.
- **Qué hace:** para cada par de ramas que puedan ser aplicables a la vez, un escenario que fuerce
  ambas condiciones y afirme **cuál gana hoy**.

> ✅ **HECHO (2026-08-23, master `3b49bde5` + `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001`).**
> `StagePrecedenceCharacterizationTest`, 4 pares discriminantes verificados por neutralización:
> hold › user-confirm · falso-ENTER › user-confirm · presupuesto › user-confirm ·
> **atribución › user-confirm en el mismo fix** (que es además P0.2).
>
> ⚠️ **Corrección al plan:** de los tres pares que esta lista pedía además, **ninguno se puede
> escribir**, y se demostró uno a uno: `no-movement ↔ candidate` es **inalcanzable** (condiciones
> mutuamente excluyentes — el plan pedía un par imposible); `hold ↔ candidate` es **redundante** con
> hold ↔ vía rápida; y `hold ↔ vía rápida` es **no observable** — con y sin el `return@collect` del
> hold la salida es idéntica, porque alejarse del pin (lo único que re-dispararía la vía rápida) es
> justo lo que hace que el hold se descarte por rancio en vez de asentarse.
> **Sigue vivo y diferido** `response-timeout ↔ fast-confirm`, con su receta en el ticket.
>
> 🔴 **Lo que esto enseña**: van **tres** ramas del hold indistinguibles desde fuera, todas por la
> misma causa — **no emiten evento propio**. La propuesta 3 (P4.2) deja de ser sólo observabilidad de
> campo: **es lo que haría testeable esta zona**. Argumento nuevo para adelantarla.
- **Criterio de aceptación:** cada test se verifica **discriminante** — invirtiendo el orden de las
  dos ramas en local, el test debe fallar. Un test de orden que pasa con cualquier orden no vale
  nada (es exactamente el bug #8: el test que pasa por la razón equivocada).
- **Reversión:** borrar el fichero.
- **Diff:** +250/−0 aprox.

### P0.2 · Test del fall-through `L`

**El problema:** cuando una rama resuelve sin cortar la iteración, las siguientes operan sobre una
foto **parcialmente obsoleta** del estado (02 §7.4). Es un invariante implícito del que nadie ha
escrito nunca un test, y el diseño nuevo lo vuelve explícito (`Handled(stopsIteration = false)`
re-alimenta `newState`). Sin test, la equivalencia no es demostrable.

- **Qué toca:** `commonTest`.
- **Criterio:** un escenario donde una rama descarta un hold y una rama posterior decide en el mismo
  fix; se afirma qué versión del estado ve la segunda **hoy**.
- **Diff:** +80/−0.

### P0.3 · Trazas de campo nuevas `M`

**El problema:** hay **10 trazas** y el subsistema lleva ~15 commits nuevos desde la última. Los dos
tickets del 22-08 lo demostraron sin querer: **los replays mataron tres diseños** que habrían pasado
cualquier revisión humana — contar el fix actual clavaba el pin en el semáforo de `Trace_CalleGavia001`;
reiniciar el reloj de la parada rompía los dos replays de `Trace_Enamorados001`.

- **Qué toca:** `commonTest/.../replay/`. El harness ya existe y es trivial de alimentar: una lista de
  `TraceEvent` con `FIX` / `STEP` / `VEHICLE_EXIT` / `BICYCLE_ENTER`, transcrita del `parkdiag`.
- **Mínimo recomendado:** `Trace_CameliasGondola001` (Oppo, viaje 2 — la parada que se desplaza 122 m)
  y `Trace_GondolaCamelias001` (Redmi, viaje 1 — la cadencia que acusa después del ancla). Ambos
  tickets citan los fixes con hora y coordenadas; el trabajo es transcripción, no investigación.
- **Criterio:** cada traza afirma el desenlace **actual** (que ya es el correcto: ambos bugs están
  arreglados en master). Son red, no denuncia.
- **Bloqueante:** requiere que los datos sigan en los móviles. **Es la única tarea de esta fase que
  no puedo hacer yo.**
- **Diff:** +150/−0 por traza.

> ✅ **HECHO (2026-08-24, `DET-2208-TRIPS-BECOME-REPLAYS-001`).** Los datos seguían en los móviles:
> `parkdiag.log` vivo y sin rotar en los dos, arrancando el 08-22 14:08. `Trace_CameliasGondola001`
> (Oppo, 147 fixes + 106 pasos) y `Trace_GondolaCamelias001` (Redmi, 76 + 106), transcritas 1:1 —
> los conteos cuadran con el `locationCount` que el propio detector anotó al salir, y la base de
> epoch se verificó cruzando los dos aparatos. **1.440 tests**, 13 replays.
>
> ⚠️ **Corrección al plan:** la premisa *«el trabajo es transcripción, no investigación»* era falsa
> en la mitad de los casos. La traza del Oppo sí confirmó su fix limpiamente (neutralizando el guard
> reproduce **la coordenada exacta** del pin de campo). Pero la del Redmi enseñó tres cosas que
> ningún test sintético había dicho:
> 1. El viaje **sigue degradando a pregunta** después del fix — y es correcto: hay un hueco GPS real
>    de **100,5 s** entre el último fix conduciendo y el primero parado. Lo que se arregló no fue el
>    desenlace, fue el **motivo**: `human_powered` → `anchor_gap_entered`. El ticket de la cadencia
>    prometía recuperar el confirm silencioso; **no lo recupera, y no debía**.
> 2. `8bf6f02b` **no mueve la coordenada**, le cuelga un radio. Su efecto observable es
>    `isApproximate`, nunca la latitud.
> 3. Una de mis aserciones **no discriminaba el guard que su comentario citaba**
>    (`DET-CONFIRM-ANCHOR-001`): forzar la otra rama deja la traza byte a byte idéntica, porque el
>    usuario contestó junto al coche. Queda como guard posicional con la limitación escrita dentro.
>
> 🔴 **Lo que esto enseña sobre F6**: dos de los tres fixes del 22-08 tenían en mi cabeza un efecto
> distinto del que tienen en el código. Se descubrió **al replicar el stream real, no al leerlo** —
> que es exactamente el argumento de la red de trazas como precondición de la Fase 1.

### P0.4 · Congelar la línea de salida `M`

- Anotar el hash exacto, el conteo de tests (`testProdDebugUnitTest`) y la lista de nombres de test.
  Cualquier test que desaparezca durante F6 sin justificación escrita es una regresión silenciosa.
- **Diff:** 0 (va en el doc).

> ✅✅ **MARCA DEFINITIVA (2026-08-24): master `2288468e` — 1.454 tests**, 0 fallos, 0 errores
> (`testProdDebugUnitTest --rerun-tasks`, no de caché). **Esta es la que cuenta**: es el commit
> inmediatamente anterior a P1.1, que es lo que este paso pedía.
> La lista completa de nombres vive en **`docs/detection/P0.4-baseline-tests.txt`** — un test que
> desaparezca durante F6 sin justificación escrita se detecta con un `diff`, no con memoria.
>
> ⚙️ **Cómo comprobarlo en cualquier momento de F6:**
> ```
> ./gradlew :composeApp:testProdDebugUnitTest --rerun-tasks
> # regenerar la lista y comparar contra P0.4-baseline-tests.txt
> ```
>
> <details><summary>Primera marca, superada (2026-08-23)</summary>
>
> ✅ master **`3b49bde5`** — **1.421 tests**, 0 fallos, 0 errores
> (`testProdDebugUnitTest --rerun-tasks`, no de caché). 10 replays `Trace_*`.
> ⚠️ **La marca caduca sola**: la sesión paralela mergeó `1a4128d5` el mismo día y la suite ya iba por
> 1.438. **La marca que cuenta es la del commit inmediatamente anterior a P1.1**, y por eso este paso
> se repite justo antes de arrancar la Fase 1 — como P0.5.
> </details>

### P0.5 · Re-anclar las citas de línea `M` — **el último, no el primero**

Repasar 02/06/08/09/11 y re-anclar sus referencias sobre el commit de arranque de F6.

> ⚠️ **Se hace la víspera de P1.1, nunca antes.** Es trabajo mecánico que caduca con el siguiente
> commit de la sesión paralela. Hacerlo pronto es tirarlo.

> ✅ **HECHO (2026-08-24) — pero en su forma barata, y conviene saber por qué.**
> El propósito de este paso no es que cada número sea exacto: es que exista **una base fija** contra
> la que diffear durante F6. Re-numerar a mano ~5 docs (02/06/08/09/11 = 3.500 líneas de citas)
> justo antes de que la Fase 1 mueva esos mismos símbolos es trabajo que se tira dos veces.
>
> Lo que se hace en su lugar: **sellar los docs con su commit base** (`2288468e`) y dejar escrito que
> sus citas `CPD:nnnn` son *«a fecha de»*, no punteros vivos. Cualquier cita se resuelve con
> `git show 2288468e:<fichero>`, que es exacto por construcción y no caduca nunca.
>
> El coste real de las citas desfasadas ya está mitigado por §0.1 del plan: **el plan se escribió en
> símbolos, no en líneas**, precisamente para no depender de esto.

---

## §3 · FASE 1 — Movimientos puros a `physics/`

Riesgo casi nulo, diff grande, reversión trivial. **Todos `M` salvo donde se indique.** El orden
dentro de la fase es indiferente salvo por las dependencias anotadas.

Criterio de aceptación **común a toda la fase**: la suite pasa sin editar un solo assert, y el
símbolo movido conserva su firma y su KDoc con los tags intactos.

| # | Paso | Qué mueve | Marca | Diff |
|---|---|---|---|---|
| P1.1 | `physics/EvidenceAdmissibility.kt` | `isAdmissibleEvidence` — las **4 copias** de `evidencia ≥ sessionStart` colapsan [DET-SESSION-BIRTH-001] | `M` | ~+40/−30 |
| P1.2 | `physics/PedestrianReach.kt` | `outrunsPedestrianReach` — la familia envelope **×4→1** con los 4 juegos de parámetros **nombrados**: `movementOutrunsSteps`, `egressExceedsWalkReach`, `heldConfirmOutrun`, `escapesAnchorEnvelope` | `M` | ~+70/−90 |
| P1.3 | `physics/CredibleMovement.kt` | `isCredibleMovingFix` + el gate de accuracy de conducción (**≥5 copias a mano**) [LOC-002] | `M` | ~+50/−45 |
| P1.4 | `physics/DriveCorroboration.kt` | `corroboratesDrive`, `isCorroboratedVehicleHop`, `isSustainedDepartureFromAnchor` + `pruneRecentFixes` (viaja con su ventana o divergen) | `L` | ~+140/−120 |
| P1.5 | `physics/WalkedVsRode.kt` | El presupuesto de pasos compartido por `EvaluateHonestClose` y `EvaluateSafetyNetCheck` — hoy declarado *"mirror of"* en ambos KDoc | `L` | ~+110/−90 |
| P1.6 | `physics/HonestZoneRadius.kt` | Las **2 implementaciones** del radio de zona (`coerceIn` en EvalHC, `minOf/maxOf` en `approximateZoneRadius`) → una | `M` | ~+40/−20 |
| P1.7 | `physics/FenceContainment.kt` | Las **2 fórmulas idénticas** de `distancia ≤ radio + accuracy` (`isInsideAnyOwnedFence`, `EvaluateArEnterArm`) → una; la tercera (`EvaluateSafetyNetCheck`, sin acolchado) **se queda y se le escribe el porqué** | `L` | ~+45/−25 |
| P1.8 | `physics/SpeedBandClock.kt` · `physics/GapDoubt.kt` | Ya son ficheros puros en su patrón; solo cambian de carpeta | `M` | ~+10/−10 |
| P1.9 | `physics/EffectiveDriving.kt` | El `when` de precedencia persona/coche, **intacto y comentado** — no se aplana ni se fragmenta [07 §3.2] | `M` | ~+80/−70 |
| P1.10 | `physics/SavedParkingShape.kt` | El sealed de forma del guardado: `ExactPin` / `BoundedZone` / `AskUser` / `KeepSilent`. **Solo el tipo**; los veredictos lo adoptan en su paso | `M` | ~+45/−0 |

**Notas de riesgo por paso:**

- **P1.4 y P1.5 son `L`, no `M`**, por una razón concreta: `isSustainedDepartureFromAnchor` y
  `refinedParkLocation` llevan hoy un `PaparcarLogger` **dentro** [07 §2.5]. Al extraerlos el log
  **sube al caller** y la función queda pura de verdad. Eso cambia el *momento* en que se emite una
  línea de log — invisible en conducta, visible en un diff de `parkdiag`. Merece su commit y su
  mención en el mensaje.
- **P1.6 ya no es cambio de conducta.** La adjudicación §14.5 lo clasificaba como `C` porque el
  cierre honesto guardaba zonas sin techo (bug #2). `f42e393b` metió el `coerceIn(suelo, techo)` por
  otro motivo y `8bf6f02b` extrajo `approximateZoneRadius` para los dos llamadores del coordinator.
  **Queda duplicación aritméticamente idéntica**, así que el paso baja de `C` a `M`.
  → **Un paso de riesgo menos que en el checkpoint.**
- **P1.7 es el bug #9 a medias, y a propósito.** Fundir las dos idénticas es conducta-idéntica y cabe
  aquí. Decidir si la tercera *debe* acolcharse es una pregunta de producto → ticket propio. Lo que
  este paso sí hace es **escribir la asimetría**, que es lo que hoy falta: sin comentario, una
  asimetría razonada es indistinguible de una discrepancia.
- **P1.10 no adopta nada.** Introducir el tipo es inocuo; hacer que `EvaluateUnattendedParkingSave`
  y `EvaluateHonestClose` lo devuelvan cambia sus firmas y sus tests, así que va en la fase de
  veredictos, no aquí.

### P1.11 · `physics/SessionOutcome.kt` — el tipado del desenlace `L`

Va al final de la fase porque es el único con trampa.

- **Qué hace:** convierte el `String` del desenlace en un tipo sellado **con la serialización
  byte a byte idéntica** [09 §1.7], y la pertenencia declarada como propiedades: `isConfirmed`,
  `triggersHonestClose`, `extendsSentryStreak`, `resetsSentryStreak`.
- **Por qué importa:** cierra el **bug #3** y, por construcción, el **bug #5** — el desenlace de
  atasco dejó de estar en dos listas *sin que nadie lo decidiera*; con membership declarada la
  exclusión sigue existiendo pero **es una decisión escrita** [§14.4].
- **Tests ANTES:** un test que fije el mapeo `tipo → string` para **todos** los desenlaces vigentes,
  contra las constantes actuales. Y un test que fije la membership actual de cada uno en los tres
  consumidores (prefijo `confirmed_`, igualdad exacta del fold, filtro del cierre honesto).
- **Criterio de aceptación:** los tests de serialización pasan sin tocar; **ningún string cambia**;
  el `when` del amortiguador queda partido en «pertenencia declarada» + «cadencia medida», que hoy
  hay que deducir leyendo.
- **Trampa conocida:** el desenlace `aborted_unattended_human_powered` tiene **dos productores**
  (el response-timeout y el cierre temprano) y la convención de nombre codifica además la
  procedencia. **El tipado no debe intentar arreglar eso** — es el bug #7, y tiene ticket propio.
- **Reversión:** `git revert`; el tipo es aditivo hasta que los tres consumidores lo adoptan, así que
  se puede partir en dos commits si el diff asusta.
- **Diff:** ~+180/−90.

---

## §4 · FASE 2 — Los sub-estados, uno por commit

De menor a mayor. Cada paso: reducer puro + test propio + los replays como red.

Criterio de aceptación **común**: el sub-estado no expone un solo `var`; sus transiciones son
`(estado, entrada) → (estado', notas)`; ninguna lambda de actualización ejecuta side-effects
(cierra la clase de duplicación bajo contención de 07 §4.2, donde logs y haversines se re-ejecutan).

| # | Sub-estado | Qué absorbe | Red de replay | Marca | Diff |
|---|---|---|---|---|---|
| P2.1 | `state/SessionTelemetry.kt` | Origen, latch de nominación, atribución de vehículo, contador de fixes, desenlace, `armEvidence` | CPDTest de seed y de no-filtración entre sesiones | `M` | ~+120/−80 |
| P2.2 | `state/ConfirmationLifecycle.kt` | `ConfirmationPhase` entero + `pendingConfirm` + `completed` + `toDetectionPhase()` | CPDTest del hold | `M` | ~+150/−120 |
| P2.3 | `state/EgressEvidence.kt` | Máquina de pasos (triple gate de conteo), latch de sensor vivo, estampas de AR | CPDTest de pasos | `L` | ~+130/−100 |
| P2.4 | `state/DriveProof.kt` | Los 2 anillos, los 2 relojes de banda, el pico banked, la promoción retroactiva **y `EvaluateShortHopDriveProofUseCase` absorbido** como perfil | `Trace_RedmiLateExitHome001`, `Trace_MotorwayRedmi001` | `L` | ~+230/−190 |
| P2.5 | `state/AnchorTrust.kt` | Ancla, taints, los **5 sellados de snapshot → uno solo**, gap, egress-birth, cinemático, `restMs` | `Trace_CalleGavia001`, `Trace_Supermarket001`, `Trace_Enamorados001`, `Trace_CameliasOppo001` | `L` | ~+330/−280 |
| P2.6 | `state/DetectionSessionState.kt` | La composición de los cinco + **el orden de reducción declarado** | Todos | `L` | ~+90/−40 |

### Notas por paso

**P2.3 — `EgressEvidence` gana una entrada que el diseño no tenía.**
`DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` (22-08) hizo que el clasificador de cadencia consulte
`isAnchorPinned`. El diseño original decía *«AnchorTrust posee el ancla; los pasos se le PRESENTAN,
nunca se le copian»* — unidireccional. **Ahora la dependencia va en los dos sentidos.**
No invalida la frontera: `EgressEvidence.onStepEvent` recibe el estado del ancla **como argumento**,
igual que `AnchorTrust` recibe los pasos. Lo que obliga es a **declarar el orden**, y por eso existe
P2.6. Sin esa declaración, un despiste en el orden de reducción cambia el veredicto de cadencia sin
que nada lo grite.

**P2.4 — `DriveProof` posee el CRITERIO, no solo los campos.**
Es el matiz que el bug #4 obliga a decidir: a los dos anillos y los dos relojes se les sumó un
contador de frescura de pasos, y **todos comparten un mismo criterio de limpieza — solo la conducción
medida los resetea**. La frontera real no es «anillos contra relojes», es *todo lo que la conducción
medida resetea*, y eso incluye estado que hoy vive repartido entre `DriveProof` y `EgressEvidence`.
El paso debe **escribir de quién es ese criterio**, no heredarlo por accidente.

**P2.4 — `hasEverReachedDrivingSpeed` se queda FUERA.** Es autorización de ciclo de vida, no grado de
prueba [07 §3.3]. Meterlo dentro sería fundir nominación con confirmación, que es la doctrina rectora.

**P2.5 — el sellado ×5 → 1 es el corazón del paso.** Hoy la condición de rebind está copiada en cinco
sitios y un campo nuevo tiene que *acordarse* de copiarla una sexta vez. Después es una transición
`rebind()` atómica. Esto no cambia conducta, pero **elimina una clase entera de bug futuro**.

**P2.5 — el fallback de `refinedParkLocation` se resuelve dentro.** Es el cabo que 07 §2.1 dejó
abierto: hoy cae a `bestFix`, que lee la lista de fixes parados (otra máquina). El `rebind` sella
también el `bestFix`, y el cabo se cierra sin decisión de producto.

### P2.5-bis · Unificación de los dos sabores del egress-birth `C`

**Paso propio, separado de P2.5**, porque puede cambiar conducta.

- **Qué hace:** los dos sabores (parado y móvil) pasan a una transición única con los deltas como
  **parámetros nombrados** — incluido `acceptsKinematicWitness`, que es la asimetría del **bug #6**.
- **Regla:** la asimetría **se preserva, no se arregla**. El refactor la hace visible; decidirla
  necesita un replay dirigido o dato de campo.
- **Criterio de aceptación, literal:** `Trace_Enamorados001` y `Trace_CameliasOppo001` sin cambio de
  desenlace. **Cualquier delta observable detiene el paso y vuelve para aprobación** [06 §3-e].
- **Reversión:** `git revert` — P2.5 no depende de este.
- **Diff:** ~+60/−80.

---

## §5 · FASE 3 — Las etapas, en orden inverso

**En orden inverso a propósito**: se mueve primero la última de la lista, para que las de arriba
sigan viendo exactamente lo que esperan mientras tanto.

### P3.0 · El andamio `M`

`stages/SessionStage.kt` con la interfaz, `StageVerdict` (`Skip` / `Handled`) y el sealed
`DetectionEffect`. Más `StageOrderTest`, que fija **la lista literal**. Sin mover ninguna etapa
todavía.

- **Criterio:** `StageOrderTest` debe fallar si se permutan dos entradas de la lista.
- **Diff:** ~+120/−0.

### P3.1 … P3.10 · Una etapa por commit

En este orden de ejecución del plan (que es el inverso del de precedencia):

`ConfidenceScoringStage` → `FastConfirmStage` → `CandidateStage` → `ResponseTimeoutStage` →
`PreDriveSkipStage` → `UserConfirmStage` → `VehicleAttributionStage` → `NoMovementBudgetStage` →
`FalseEnterAbortStage` → `HoldResolutionStage`

**Criterio común:** cada etapa hereda los tests del bloque que muda, sin editarlos, y los tests de
precedencia de P0.1 siguen verdes en cada commit.

**Dos etapas con aviso:**

- **`VehicleAttributionStage` es la única que necesita I/O.** Decide en puro con
  `VehicleFenceOwnershipPolicy.resolveSessionVehicleId` y **pide** el lookup como efecto
  `ResolveVehicle`; el ejecutor re-entra por un entrypoint atómico. Misma conducta, I/O fuera de la
  decisión. Si esto se complica, es señal de que el efecto está mal definido — no de que haya que
  meter el repositorio dentro de la etapa.
- **`UserConfirmStage` gana el derecho a emitir `SaveZone`.**
  `DET-USER-YES-IS-NOT-A-COORDINATE-001` (22-08) hizo que la vía del «sí» pueda guardar zona cuando
  el ancla nació en un hueco. Cuando esta etapa se mueva, debe emitir una `SavedParkingShape`, no un
  punto por defecto. **Es el único sitio del plan donde el refactor cierra estructuralmente un bug de
  omisión**: con la forma como tipo obligatorio, un camino nuevo no puede olvidarse de decidir.

**Diff por etapa:** entre ~+60/−50 (las pequeñas) y ~+140/−120 (`ResponseTimeoutStage`).

### P3.11 · `DetectionEffectExecutor` `L`

El único sitio del núcleo con I/O: confirmar, guardar zona, avisar, degradar a pregunta, notificar.
Absorbe también el `@Volatile` cross-sesión de la notificación, que es estado de notificación y no de
sesión.

- **Criterio:** ninguna etapa importa un repositorio. Verificable con un test de arquitectura
  (Konsist), que es como el proyecto ya enforcea sus otras doctrinas.
- **Diff:** ~+240/−200.

### P3.12 · `DetectionDiagnosticsTap` `L`

El único emisor. Las 15 ramas mudas **dejan de existir como ramas**: cada una pasa a ser una
`DiagnosticNote` con nombre, producida por el reducer que ya decidió. El tap posee además los dedups
que hoy son estado suelto.

- **Ojo:** por defecto el tap **replica exactamente la superficie remota actual**. Ampliar qué se
  emite es P4.2, no este paso. Cero cambio observable aquí.
- **Bonus medible:** el logger es hoy 100 % eager con ~69 llamadas en el bucle caliente; el overload
  perezoso entra con este paso.
- **Diff:** ~+130/−180.

### P3.13 · El orquestador, y el borrado `M`

Se ensambla `CoordinatorParkingDetector` nuevo (~235 líneas: ciclo de vida, bucle, corrutinas
hermanas, entrypoints) y **se borra el fichero viejo**, que a estas alturas está vacío.

- **Criterio:** los 10+ replays y los ~3.350 líneas de test del coordinator pasan sin un assert
  editado. **Este es el criterio de aceptación de toda la Fase 3**, no solo del paso.
- **Diff:** ~+280/−(lo que quede).

> ✅ **HECHO (2026-08-26) — `DET-ORCHESTRATOR-ASSEMBLY-001`.** 1.636 tests, 0 fallos, 18 replays y
> los 4 guardrails verdes, **sin un assert editado**; 0 nombres perdidos contra `P0.4-baseline-tests.txt`.
> El detalle completo vive en `docs/backlog/det-orchestrator-assembly-001.md`.
>
> ⚠️ **Dos correcciones al enunciado, y las dos importan.**
>
> 1. **El fichero viejo NO estaba vacío: tenía 2.344 líneas.** Lo que quedaba no eran restos de las
>    etapas, eran cuatro poblaciones que ninguna fase había reclamado porque ninguna era una etapa —
>    el reducer del fix (`updateStopTracking`, corre ANTES de la precedencia), doce predicados del
>    ancla, el despachador de efectos y los constructores de input. La estimación no falló por poco:
>    falló porque el plan sólo censó *ramas*, y el coordinator también era el dueño por defecto de
>    todo lo que no era una rama.
> 2. **Las ~235 líneas no eran alcanzables y no deberían serlo.** El presupuesto del §4 cuenta
>    sentencias; en este proyecto el comentario es la mitad del fichero por decisión explícita. El
>    orquestador queda en **1.286 líneas** con exactamente el reparto que el §4 enumera (ciclo de
>    vida, bucle, corrutinas hermanas, entrypoints, epílogo) y con el incidente de campo de cada
>    guard dentro. Llegar a 235 exige borrar los porqués, que es lo contrario del objetivo declarado.
>
> 🔴 **Lo que el paso enseña**: el bucle recorre ahora `detectionStageOrder`, y hasta este paso esa
> lista **no era ejecutable** — `StageOrderTest` comparaba la lista con el enum y las dos podían
> estar de acuerdo mientras el bucle hacía otra cosa. Permutar dos entradas ya pone rojos 3 de los 6
> tests de precedencia; antes no habría cambiado nada.

---

## §6 · FASE 4 — Los cambios de conducta

Van al final **porque la casa nueva los vuelve pequeños**. Cada uno con su commit, su test y su
validación de campo propia.

### P4.1 · El watchdog del hold estampa su procedencia `C` — §14.1

- **Hoy:** un hold hambriento de fixes se finaliza por reloj sin re-validar frescura, y el pin
  resultante es **indistinguible** de uno asentado por fix.
- **Propuesto:** sigue confirmando (la decisión no cambia), pero con `detectionPath` propio y
  fiabilidad reducida.
- **Riesgo:** ninguno de FP/FN — cambia lo que se puede *saber* del pin, no si se planta.
- **Diff:** ~+70/−20.

### P4.2 · Telemetría: las ramas mudas cuentan, los fixes no `C` — §14.2 + §14.3

Dos mitades que van juntas porque su balance es lo que las hace aceptables:

1. **Evento `TRIGGER` con disposición** (`armed` / `suppressed_rearm` / `refused_strategy` /
   `refused_permissions` / `not_armable` / `lookup_failed` / `orphan`), más la traza del wake
   suprimido por el amortiguador — **que está pendiente desde la adjudicación y no se hizo**: el
   único intento (`4d1d6716`) dejó un log **local**, o sea la primera rama muda creada *después* de
   decidir que no habría más.
2. **`LOCATION_FIX` baja a flag de replay.** Un viaje escribe hoy 400–950 documentos, de los cuales
   360–900 son fixes. **El neto es MENOS escrituras y más información.**

- **Requisito heredado:** emitir también al **extender** la ventana de atasco, no solo al plegar —
  sin eso, «0 pliegues en 1.359 sesiones» no prueba «la extensión nunca ayudó» [§14.4-bis].
- ⚠️ **Aviso operativo:** el flag de replay debe quedar **activo en los móviles de campo** o se pierde
  la materia prima de las trazas futuras.
- **Diff:** ~+220/−90.

### P4.3 · Las cinco retiradas de sedimento `C` — §14.8

Retirar la etiqueta dominada por el intake (el log se queda), fundir la etiqueta gemela del
drive-proof en su ficha, renombrar la que colisiona en `grep` con la de Bluetooth, convertir la del
congelado corto en parámetro documentado, y retirar el campo vestigial que **nadie lee**.

- **Riesgo FP/FN:** ninguno ejecutable. Cambia el censo de tags y algún log — por eso pide aprobación.
- **Diff:** ~+30/−60.

### Fuera de este plan, aunque §14 lo adjudicara

**La propuesta 6 (núcleo común Release↔Process↔Finalize↔Retract con gate de zona privada) es el fix
del bug #1 y va en ticket propio.** No toca el coordinator ni una línea: es una familia de cuatro
casos de uso que se parametriza por `(sesión, razón, prueba)`. Meterla aquí sería mezclar refactor y
arreglo de bug, que es exactamente lo que la regla 4 prohíbe.

---

## §7 · FASE 5 — La puerta de entrada (F7)

`docs/detection/README.md`: diagrama del flujo, **una línea por clase agrupada por etapa**, tabla de
invariantes vigentes con su test, glosario del dominio (*anchor*, *pinned/frozen/locked*, *egress*,
*egress birth*, *drive proof*, *short hop*, *hold*, *unattended save*, *honest close*, *supersession*,
*arm evidence*) y la guía «cómo depurar una sesión de campo».

Es lo que hace que se pueda volver dentro de tres meses y entender el sistema en veinte minutos —
que era el objetivo declarado del encargo.

---

## §8 · Recuento y forma del plan

| Fase | Pasos | Marca dominante | Diff acumulado aprox. |
|---|---|---|---|
| 0 · Red de seguridad | 5 | solo tests | +480 |
| 1 · Física a `physics/` | 11 | `M` (2 `L`) | +810 / −500 |
| 2 · Sub-estados | 7 | `L` (1 `C`) | +1.110 / −890 |
| 3 · Etapas y ensamblaje | 14 | `M`/`L` | +1.900 / −2.900 |
| 4 · Conducta | 3 | `C` | +320 / −170 |
| 5 · Puerta | 1 | doc | +400 |
| **Total** | **41 pasos** | | **el CPD desaparece** |

**Lectura honesta del total:** el LOC neto baja poco. Lo que baja es el acoplamiento — ningún fichero
nuevo pasa de ~330 líneas y cada uno tiene un dueño de test. El beneficio no se mide en líneas: se
mide en que el bug 148 cueste lo que cuesta su tamaño, no lo que cuesta el fichero donde vive.

---

## §9 · Los cinco riesgos, y qué paso los acota

| # | Riesgo | Acotado por |
|---|---|---|
| 1 | **Semántica de concurrencia**: hoy 3 corrutinas mutan un `StateFlow` + 8 volátiles con lambdas que ejecutan side-effects. Los reducers puros son una mejora, pero cambian *timings* | P0.1-P0.3 (la red) + P2.6 (orden declarado) |
| 2 | **El fall-through implícito** | P0.2, escrito antes de mover el hold |
| 3 | **Orden de etapas alterado en silencio** | P0.1 + `StageOrderTest` (P3.0) |
| 4 | **El canal post-`invoke` del cierre honesto**: si el sellado atómico cambia el instante de visibilidad, podría leer nulos | Test de contrato del epílogo en P2.1 + replays `Trace_CameliasHop001` / `Trace_LateExitOnFoot001` |
| 5 | **Deriva de citas** | P0.5, ejecutado la víspera |

**Lo que este plan NO arregla, dicho una vez más:** las 14 carreras conocidas (sus guards se conservan
tal cual), los bugs #1, #6, #7, #8 (tickets propios) y las colisiones de doctrina, que son decisiones
de producto.

---

## §10 · Qué hay que decidir antes de empezar

1. ~~**¿Se convierten los dos trayectos del 22-08 en trazas?** (P0.3)~~ → ✅ **RESUELTO 24-08, sí**
   (`073f80f7`). Los datos seguían en los móviles. **G3 queda abierta.**
2. **¿F6 arranca antes o después de vaciar la cola de tickets de detección?** La recomendación es
   **después**: G4 no es una formalidad, es la diferencia entre un rebase y una tarde de conflictos.
3. **¿P4.2 entra con el refactor o va antes, como ticket propio?** Argumento para adelantarla: hace
   que cada viaje de validación de campo valga el triple, y la validación de campo es la puerta G1 de
   todo esto. Argumento para dejarla dentro: el tap único (P3.12) hace que emitir sea una línea en vez
   de quince sitios.
   > 🔴 **La Fase 0 inclinó esta decisión hacia ADELANTARLA.** Dos tickets independientes chocaron con
   > lo mismo: hay al menos **tres ramas del hold indistinguibles desde fuera** porque no emiten
   > evento propio, y ningún test de orden se puede escribir sobre ellas. P4.2 dejó de ser
   > "observabilidad de campo" para ser **la precondición de poder testear esa zona** — que es
   > justamente la zona que F6 parte.

---

## §11 · Estado de las cuatro puertas (24-08)

| | puerta | estado |
|---|---|---|
| G1 | validación de campo | ⛔ **la grande** — ~17 commits de detección sin conducir |
| G2 | aprobación de `09` y de este documento | ⛔ pendiente del user |
| G3 | red de tests (Fase 0) | ✅ **ABIERTA** — P0.1 `3b49bde5` · P0.2 `3b49bde5` · P0.3 `073f80f7` · P0.4 hecha |
| G4 | árbol quieto | ⛔ la sesión paralela sigue abriendo tickets sobre `CoordinatorParkingDetector.kt` |

**P0.4 y P0.5 se repiten la víspera de P1.1**, no antes: la marca de tests y las citas de línea
caducan con el siguiente commit de la sesión paralela, y hacerlas pronto es tirarlas.

*Fin del plan. Nada de esto se codifica sin las cuatro puertas.*
