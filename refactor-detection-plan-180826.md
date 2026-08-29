# PROMPT — Refactor profundo del subsistema de DETECCIÓN (Paparcar)

> Pégalo entero en Claude Code, en la raíz del repo, con el módulo Android/KMP indexado.
> Está diseñado para ejecutarse por fases. **No pases a la siguiente fase sin mi aprobación explícita.**

---

## 0. CONTEXTO Y OBJETIVO

Eres el arquitecto responsable de un subsistema que se ha vuelto inmantenible por acreción:
la detección de aparcamiento de Paparcar (KMP, Android primero). Síntomas actuales:

- `CoordinatorParkingDetector` es un fichero gigante: un `collect` con ~9 niveles de precedencia
  inline, un `ParkingDetectionState` con ~35 campos y un `updateStopTracking` que concentra
  media docena de máquinas de estado distintas (anchor, walk-odometer, egress birth, gap,
  stepless departure, reposition, kinematic egress).
- Más de **40 casos de uso** en el paquete de detección/parking, muchos con nombres parecidos
  y responsabilidades solapadas.
- Muchos **workers** (departure, geofence, reconciliación, diagnóstico…) cuyo reparto de
  responsabilidades ya no está claro.
- Cada bug de campo se ha cerrado con un **parche etiquetado** (`[DET-*]`, `[BUG-*]`,
  `[REFACTOR-*]`, `[LOC-*]`, `[ANCHOR-LOCK-*]`, `[PARKING-*]`, `[VEH-*]`, `[FND-*]`…).
  Los parches funcionan pero han sedimentado: hay condiciones redundantes, flags derivados de
  otros flags, y reglas que probablemente ya se podrían expresar como una sola política.

**Objetivo:** dejar el flujo end-to-end de detección (desde que se arma hasta que se cierra la
sesión) con una estructura **limpia, legible, atomizada y mantenible**, sin perder ni una sola
garantía ganada en campo, y con documentación que me permita volver dentro de 3 meses y entender
el sistema en 20 minutos.

### Restricción número uno (léela dos veces)

Cada etiqueta `[DET-…]`, `[BUG-…]` etc. **codifica un incidente real de campo con fecha y
dispositivo**. Ejemplos que están en el código: el ancla que seguía al peatón hasta la puerta de
casa (Redmi, 2026-07-11), el pin a 1,11 km en un semáforo (2026-07-15), el mirage Doppler dentro
de casa (2026-07-27), el pin en el punto de recogida (Calle Abeto, 2026-07-23), la bici de 4,8 km
en la playa (Samsung, 2026-08-16).

> **Ningún guard se elimina "porque parece redundante". Se elimina solo cuando demuestras, por
> escrito y con el escenario de campo concreto, que otra regla del sistema lo cubre íntegramente.**
> Cualquier simplificación no demostrada es una regresión de producto: el usuario pierde su coche.

---

## 1. REGLAS DE TRABAJO

1. **Fases 1–4 son solo lectura y documentación. No modifiques ni una línea de código de producción.**
2. Usa **subagentes en paralelo** para las tareas de inventario y comparación (una por área).
   Cada subagente devuelve un informe estructurado en markdown; tú los consolidas.
3. Entregables en `docs/detection/` (crea el directorio). Un fichero por entregable, nombrados
   como se indica en cada fase.
4. No inventes. Si un fichero, worker o caso de uso no lo has leído entero, no lo describas.
   Marca explícitamente lo que no has podido verificar.
5. Cuando llegue el momento de tocar código (Fase 6), **un paso = un commit**, con los tests en
   verde en cada commit. Nada de "big bang".
6. Idioma: informes y documentación **en español**; código, KDoc y nombres de símbolos en inglés
   (como está ahora).
7. Prioriza la *legibilidad para un humano que vuelve al código a los 3 meses* por encima de la
   pureza arquitectónica. Si una abstracción elegante hace el flujo más difícil de seguir, no la
   propongas.

---

## 2. FASE 1 — INVENTARIO EXHAUSTIVO (subagentes en paralelo)

Lanza estos subagentes **a la vez**. Cada uno lee todo lo que le corresponde, sin saltarse ficheros.

### Subagente A — Casos de uso
Localiza **todos** los use cases relacionados con detección, parking, geofence, departure,
vehículos y diagnóstico (`domain/usecase/**`, y cualquier otro paquete donde vivan).
Para cada uno produce una fila con:

| Clase | Paquete | LOC | Responsabilidad en **una línea** | Entradas | Salidas | ¿Puro? | Estado (¿mutable?) | Quién lo invoca | Tags `[…]` que menciona | Tests que lo cubren |

Además:
- Marca los que **nadie invoca** (muertos).
- Marca los que se invocan **desde un solo sitio y son triviales** (candidatos a inline).
- Marca los que hacen **más de una cosa** (candidatos a split).

### Subagente B — Coordinator y máquina de estados
Lee `CoordinatorParkingDetector` entero, `ConfirmationPhase`, `DetectionPhase`,
`ParkingDetectionConfig`, `ArmEvidence`, `ParkingDecision*`, `UnattendedParkingSave*`.
Produce:
- **Tabla de campos de `ParkingDetectionState`**: nombre, tipo, quién lo escribe (línea), quién lo
  lee (líneas), invariante que sostiene, tag asociado.
- **Clasificación de cada campo**: ¿es estado primario, derivado, o snapshot de otro campo en un
  instante? (hay varios `*AtCapture` que son snapshots — identifícalos todos).
- **Grafo de dependencias entre campos** (qué campo condiciona a qué otro).
- **Lista ordenada de las ramas de precedencia del `collect`**, con la condición exacta de cada una
  y qué side effects dispara.
- **Descomposición de `updateStopTracking`**: cuántas máquinas de estado independientes conviven
  dentro y cuáles son.

### Subagente C — Workers y entrypoints
Inventaria todos los `Worker`, servicios foreground, `BroadcastReceiver`, `PendingIntent`,
callbacks de geofence/AR/Bluetooth y schedulers. Para cada uno:

| Clase | Qué lo dispara | Qué hace en una línea | Qué escribe (repos/estado) | Con quién compite | Idempotente? | Reintentos | Tags |

Y responde: **¿hay dos actores que puedan armar/confirmar/cerrar una sesión a la vez?**
Dibuja el diagrama de carreras conocidas (el código ya menciona supersession de sesiones,
entregas *stale*, zombies de geofence, swap-race de vehículo activo).

### Subagente D — Diagnóstico local y remoto
Lee `DetectionEventLogger`, `DetectionEvent` y todo lo que escribe a Firestore o a logcat
(`PaparcarLogger`, tags `PARKDIAG/*`). Produce:
- Catálogo de eventos emitidos y desde qué rama.
- **Huecos de observabilidad**: ramas de decisión que no emiten nada (el código ya reconoce dos
  casos históricos de "esto no se veía en forensics"). Lista todas las que quedan.
- Coste: ¿cuántos eventos por sesión típica? ¿algo puede petar cuota/batería?
- Propuesta de **esquema mínimo suficiente** para poder reconstruir una sesión completa desde
  remoto sin acceso al dispositivo.

### Subagente E — Tests y trazas
Inventaria los tests del subsistema. Para cada tag `[DET-*]`/`[BUG-*]` del código, indica si
existe un test que lo fije. Produce la **matriz tag → test → escenario de campo**, con las filas
sin test marcadas en rojo. Busca también fixtures/trazas de campo guardadas en el repo.

### Entregables Fase 1
- `docs/detection/01-inventario-usecases.md`
- `docs/detection/02-estado-coordinator.md`
- `docs/detection/03-workers-entrypoints.md`
- `docs/detection/04-diagnostico.md`
- `docs/detection/05-cobertura-tests.md`

**Para cuando termines: un resumen de 20 líneas máximo con los 5 hallazgos más graves.**

---

## 3. FASE 2 — REGISTRO DE INVARIANTES (el documento más importante)

Este es el artefacto que hace posible el refactor. Antes de mover nada, extrae **una fila por
etiqueta** presente en el código:

| Tag | Incidente de campo (fecha/dispositivo si consta) | Síntoma | Regla que lo cierra | Dónde vive hoy (fichero:línea) | Campos de estado implicados | Test que lo fija | ¿Puede colisionar con otro tag? |

Después:
1. **Agrupa los tags por *clase de fallo*** (p. ej.: "el ancla sigue al peatón", "el pin cae en un
   punto intermedio del trayecto", "movimiento no vehicular tratado como coche", "pérdida total
   del aparcamiento", "doble guardado / sesión zombie").
2. Por cada grupo, responde: **¿son N parches del mismo problema?** Si sí, escribe la **política
   única** que los subsumiría, y demuestra caso por caso que cubre cada escenario original.
3. Marca los tags que hoy son **inalcanzables** (condición imposible por cambios posteriores) o
   **dominados** (otra condición dispara siempre antes). Justifica con el flujo concreto.

Entregable: `docs/detection/06-invariantes.md`

Este documento es el contrato del refactor: **cualquier cambio posterior debe poder mapearse
contra esta tabla.**

---

## 4. FASE 3 — ANÁLISIS DE DUPLICACIÓN Y RUIDO

Con el inventario de la Fase 1 en la mano, compara **clase contra clase, línea a línea**
(usa subagentes: reparto por clusters temáticos).

Produce:

1. **Matriz de similitud entre casos de uso**: para cada par sospechoso, en qué se parecen
   (firma, entradas, lógica interna, sitio de llamada) y veredicto:
   - `FUNDIR` (mismo concepto, dos nombres),
   - `PARAMETRIZAR` (mismo algoritmo, distinto umbral → un use case + config),
   - `MANTENER SEPARADOS` (razón explícita),
   - `ELIMINAR` (muerto o trivialmente inlineable).
2. **Helpers privados del Coordinator que deberían ser use cases puros**: hoy hay una docena de
   funciones privadas que son física pura y perfectamente testeables
   (`movementOutrunsSteps`, `egressExceedsWalkReach`, `escapesAnchorEnvelope`,
   `isSustainedDepartureFromAnchor`, `isCorroboratedVehicleHop`, `corroboratesDrive`,
   `isEgressBornAtAnchor`, `refinedParkLocation`, `isAnchorWalkEntered`, `hasKinematicEgressSignal`…).
   Decide para cada una: extraer a use case puro / agrupar varias en una política cohesiva /
   dejarla dentro. Justifica.
3. **El caso que falta**: identifica lógica que hoy se resuelve con parches dispersos y que
   pediría un concepto de primera clase. Candidatos evidentes a evaluar (confírmalos o
   descártalos con el código delante):
   - una **política única de credibilidad del ancla** (pinned / frozen / locked / walk-entered /
     gap-entered / born-away son hoy seis flags y seis snapshots que responden a *una* pregunta:
     "¿cuánto me creo que el coche está aquí?"),
   - un **clasificador único persona/coche** por fix (hoy la decisión está repartida entre
     `effectiveDriving`, `outruns`, `sustainedDeparture`, `corroboratedMuteHop`,
     `steplessDeparture`, `isRepositionBurst`),
   - una **prueba de conducción única** (`driveProven` / `shortHop` / `credibleDrivingFixes` /
     `pendingMaxSpeedMps` conviven hoy con reglas de promoción entre ellos),
   - un **modelo explícito de "doubt radius"** que unifique confirm exacto, zona aproximada y
     nudge, en vez de tres caminos con reglas propias.
4. **Ruido a eliminar**: logs redundantes, dobles cálculos de haversine sobre los mismos puntos,
   `import` con FQN inline (`com.rndeveloper.paparcar.domain.util.haversineMeters` está repetido
   ~15 veces), constantes duplicadas entre `companion` y `config`.

Entregable: `docs/detection/07-duplicacion.md`

---

## 5. FASE 4 — FLUJO END-TO-END Y ARQUITECTURA OBJETIVO

### 5.1 Reconstruye el flujo real, tal como es hoy
Un documento narrativo + diagrama (mermaid) que recorra:

`ARM` (geofence exit / AR / Bluetooth / manual, con sus evidencias y sus entregas stale)
→ `TRACKING` (stream de fixes, prueba de conducción, atribución de vehículo)
→ `STOP` (apertura de parada, gap, captura y maduración del ancla)
→ `EGRESS` (pasos, egress cinemático, nacimiento del egress, desplazamiento)
→ `DECISIÓN` (fast confirm / candidate / scoring / weak evidence / prompt)
→ `HOLD` (ventana de gracia, revalidación, watchdog)
→ `CONFIRM` (guardado exacto / zona / nudge / degradación a prompt)
→ `CIERRE` (outcome, honest-close, supersession, reset)

Para cada etapa: entradas, salidas, quién es el dueño, qué puede abortarla, qué se registra.

### 5.2 Diseña la arquitectura objetivo
Requisitos que debe cumplir la propuesta:

- **El coordinator no debe superar ~250 líneas.** Su único trabajo: orquestar una lista
  explícita y ordenada de etapas, y ejecutar side effects.
- La **precedencia** (hoy un comentario de 9 puntos en el KDoc) debe ser **código legible**:
  una lista de etapas/reglas evaluadas en orden, no un `if` de 400 líneas. Que el orden se pueda
  leer de un vistazo y testear aisladamente.
- `ParkingDetectionState` **descompuesto en sub-estados cohesivos** (p. ej. `DriveProof`,
  `StopAnchor`, `EgressEvidence`, `SessionTelemetry`, `ConfirmationLifecycle`), cada uno con sus
  propias transiciones y su propio test. Los snapshots `*AtCapture` deben vivir dentro del
  sub-estado del ancla, no sueltos.
- **Toda regla de física/decisión es una función pura testeable**, sin `Flow` ni logging dentro.
- El **logging de diagnóstico se separa de la lógica**: que decidir y contar sean cosas distintas
  (hoy hay ramas donde el log *es* la única forma de saber qué pasó).
- Los **workers** con responsabilidad clara y sin solapes; documenta quién puede armar y quién no.
- **Sin cambios de comportamiento observables** salvo los que propongas explícitamente y yo
  apruebe uno por uno.
- Compatible con el port a iOS (modelo *wake-and-query*): marca qué piezas son platform-agnostic
  y cuáles asumen el modelo push/streaming de Android.

Entregables:
- `docs/detection/08-flujo-e2e.md` (estado actual)
- `docs/detection/09-arquitectura-objetivo.md` (propuesta, con diagrama y árbol de ficheros nuevo)
- Para cada cambio propuesto: **qué invariante de la Fase 2 lo respalda y cómo se preserva.**

**Para aquí y espérame.** Quiero revisar la arquitectura objetivo antes de que toques código.

---

## 6. FASE 5 — PLAN DE EJECUCIÓN

Con la arquitectura aprobada, escribe `docs/detection/10-plan-refactor.md`:

- Secuencia de pasos **pequeños y reversibles**, ordenados por riesgo ascendente.
- Cada paso: qué toca, qué tests se añaden **antes** del cambio, criterio de aceptación,
  cómo revertirlo.
- Empieza siempre por: **red de seguridad de tests sobre el comportamiento actual**
  (characterization tests) usando las trazas de campo disponibles. Nada se mueve hasta que el
  comportamiento actual esté fijado por tests.
- Marca los pasos que son puro *move/rename* (seguros) frente a los que cambian lógica (riesgo).
- Estima cada paso en tamaño de diff, no en horas.

---

## 7. FASE 6 — EJECUCIÓN

Solo cuando yo diga adelante, y paso a paso:

1. Un commit por paso, mensaje que cite el paso del plan y los tags implicados.
2. Tests en verde antes de continuar. Si un test falla, **para y pregúntame** — puede ser que el
   test estuviera fijando un bug.
3. Al final de cada paso, un resumen de 3 líneas: qué movió, qué invariantes verificó, qué queda.
4. **No refactorices y arregles bugs en el mismo commit.** Si encuentras un bug, anótalo en
   `docs/detection/11-bugs-encontrados.md` y sigue.

---

## 8. ENTREGABLE FINAL

Un `docs/detection/README.md` que sea la puerta de entrada al subsistema:

- Diagrama del flujo end-to-end.
- **Una línea por clase**, agrupadas por etapa del flujo (esto es lo que quiero leer cuando vuelva
  al código dentro de tres meses).
- Tabla de invariantes vigentes con su test.
- Glosario de conceptos del dominio: *anchor*, *pinned/frozen/locked*, *egress*, *egress birth*,
  *walk-entered*, *gap-entered*, *drive proof*, *short hop*, *hold*, *unattended save*,
  *honest close*, *supersession*, *arm evidence*.
- Guía "cómo depurar una sesión de campo": qué logs mirar, qué eventos remotos, en qué orden.

---

## 9. EMPIEZA AQUÍ

Ejecuta la **Fase 1** ahora. Antes de lanzar los subagentes, dime en 5 líneas cómo has repartido
el trabajo y qué rutas del repo va a cubrir cada uno, para que yo pueda corregirte si te dejas
algún paquete fuera.