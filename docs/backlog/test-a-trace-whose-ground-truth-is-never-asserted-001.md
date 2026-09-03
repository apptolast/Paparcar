# TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001

> **Estado:** ✅ **Cerrado 2026-09-01 — la REGLA se refuta, y la auditoría destapó un defecto real** ·
> rama `test/TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001-audit` (base `f58e9d64`)
> **Origen:** medición lateral de `TEST-AN-ORPHANED-FIELD-TRACE-STILL-LOOKS-LIKE-COVERAGE-001`.
> **Sale de aquí:** `DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001`.

---

## 1. La pregunta

Un replay puede correr entero y **no afirmar nada sobre dónde acabó el pin**. El trace declara su
ground-truth —dónde estaba el coche, dónde plantó el build de campo— y nadie lee esas constantes. Lo
que queda es un test que comprueba que la app no revienta con ese stream, con la apariencia de un
test que comprueba que acierta.

La spec exigía **clasificar caso por caso antes de escribir regla alguna**, y advertía que podía
cerrarse refutada. Se ha cumplido lo uno y lo otro — pero no como esperaba.

## 2. Lo medido: son **7**, no 6

⚠️ **Corrección de la cifra que dio pie al ticket.** Con la medición hecha bien (referencias
CUALIFICADAS y **sólo en código**, sin comentarios) salen **7** constantes que nadie lee fuera de su
fichero, no 6. La séptima, `PARAFARMACIA_2908_BOARDING_TRUE_TIME_MS`, se me escapó porque **sí** está
citada… en un comentario. Que es justo la coartada que el ticket hermano existe para no aceptar.

Las otras 12 «no usadas» del primer barrido eran ruido: `t0`/`T0`/`recoveryFixes` se usan **dentro**
de su propio fichero para construir el stream.

## 3. La clasificación, que es el entregable

| grupo | constantes | veredicto |
|---|---|---|
| `TraceCameliasOppo001.REAL_CAR_LAT/LON` + `FIELD_PIN_LAT/LON` | 4 | 🔴 **escondían un defecto** |
| `PARAFARMACIA_2908_FIELD_PIN_LAT/LON` | 2 | 🟢 contexto legítimo |
| `PARAFARMACIA_2908_BOARDING_TRUE_TIME_MS` | 1 | 🟢 entrada deliberadamente NO inyectada |

### 🔴 Camelias-Oppo — no era contexto

**Cómo se decidió: midiendo, no razonando.** Sonda: mismo stream, el usuario contesta *"Sí"*. Master
guarda un **pin EXACTO** a `< 1 m` del `FIELD_PIN` del fixture — **37 m del coche real**. Segunda
sonda: mismo stream, nadie contesta. Master guarda una **ZONA de 60 m** en la misma coordenada, que
**sí cubre** los 37 m.

Misma ancla, misma duda, dos puertas, dos formas. → `DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001`.

El ground-truth ya no es documentación: lo leen los dos tests nuevos.

### 🟢 Parafarmacia — sí es contexto

Ahí **el coche no se movió**. Un *"Sí"* es una premisa falsa: el usuario estaría equivocado, no la
app. El pin de campo está en el fichero para que el lector sepa **qué se evitó**, y el veredicto
entero del test es *0 pines · 1 pregunta* — no hay posición que afirmar.

⚠️ **Considerado y descartado, dicho para que nadie lo repita**: sí, un *"Sí"* ahí planta un pin
exacto a 58 m del coche sobre una sesión sin conducción medida. No se persigue porque
`DET-ASSERTION-OUTRANKS-INFERENCE-001` dice que la palabra del usuario manda, y el ancla es la mejor
posición que la sesión tiene. Inventar un test sobre una respuesta que nadie daría sería exactamente
lo que esta spec prohibía.

### 🟢 `BOARDING_TRUE_TIME_MS` — no es ground-truth

Es una **entrada que el test NO inyecta a propósito** (el `reset()` de `invoke()` borra el sello del
AR entregado 2,7 s antes), con su comentario explicándolo. Su trabajo es que el comentario tenga a
qué apuntar. **Que la lea sólo la prosa es lo correcto aquí.**

## 4. Veredicto sobre la REGLA: **refutada**

**No se escribe guardarraíl.** De 7 constantes, 3 de 3 grupos son legítimos y el cuarto no era un
problema de constantes sino de detección. Una regla del tipo *«toda constante de un trace debe
aparecer en un assert»* habría exigido inventar aserciones sobre una respuesta que nadie da
(parafarmacia) y sobre una entrada que existe para no usarse (`BOARDING_TRUE_TIME_MS`) — es decir,
habría empujado a callar el guardarraíl con ruido. Es peor que la ausencia, y estaba escrito en la
spec antes de medir.

📌 **Lo que sí queda es el método**: cuando una constante de ground-truth no la lee nadie, la
pregunta no es *«¿le pongo un assert?»* sino **«¿qué escenario de este trace no está ejercitado?»**.
En Camelias-Oppo el escenario faltante era el toque del usuario, y ahí estaba el defecto.

## 5. Verificación

- 2 tests nuevos, **2 falsaciones** vistas en rojo:
  1. `doubt = 0` en la rama `WALK_ENTERED_ANCHOR` del desatendido → el veredicto cae a `Ask` y **se
     pierde el aparcamiento** (el primer test en rojo).
  2. puerta de `shapeFor` forzada abierta → el *"Sí"* guarda **60,0 m** y el segundo test cae: es la
     prueba de que el defecto está exactamente en esa condición.
- Suite completa en verde. Sólo `commonTest` + un `val` de cola en `Trace_CameliasOppo001`. Cero
  producción.
