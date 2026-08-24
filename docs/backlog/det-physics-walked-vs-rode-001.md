# DET-PHYSICS-WALKED-VS-RODE-001 · P1.5 — el presupuesto de pasos, uno solo para dos veredictos

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-WALKED-VS-RODE-001-p1-5` ·
worktree `../Paparcar-physics-5`

Paso **P1.5** de la Fase 1, el segundo marcado `L`. Sigue a `731ad2d7` (P1.4). **Primer paso que
cruza dos casos de uso**, no solo el coordinator.

## Qué mueve

*¿El cuerpo anduvo esta distancia o lo llevaron?* El contador de pasos del hardware es el único
testigo que sobrevive al sueño, a la muerte del proceso y al batching del OEM, así que cuando la app
despierta lejos de donde aparcó la pregunta siempre es la misma: andar ese desplazamiento tuvo que
costar ~`distancia / zancada` pasos.

Dos casos de uso la hacen, y los dos KDoc ya se declaraban *«mirror of»* el otro (06 §3-c):

- `EvaluateHonestCloseUseCase` cerrando un abort — ¿se movió el coche o te fuiste andando?
- `EvaluateSafetyNetCheckUseCase` reconciliando — ¿se puede liberar esta plaza sin preguntar?

**Siguen siendo dos veredictos.** Fundir los casos de uso no se sostiene: uno produce nueve razones
tipadas para cerrar un abort, el otro elige entre cure / dispatch / prompt / silencio, con entradas
distintas y en momentos distintos. Lo que comparten es esta aritmética, y solo esta.

## Lo que NO entra, a propósito

Los guards de salud del contador — sello rancio [DET-TRIP-WITNESS-001], contador mudo, contador
congelado [DET-FROZEN-COUNTER-001], origen de sello ausente [DET-STEP-BUDGET-ORIGIN-001]. El doc 06
los llamaba «cláusulas del mismo cálculo», y como descripción es correcta; pero **cada dueño los
resuelve con su vocabulario** (el cierre honesto devuelve una razón tipada por caso, el safety net
enruta a otras pruebas). Meterlos aquí cambiaría esos veredictos en vez de moverlos.

## La equivalencia que sostiene el paso

Los dos "espejos" **no estaban escritos igual**:

```
EvalHC:   steps >= ceil((distancia / zancada) × fracción).toInt()
EvalSNC:  steps <  (distancia / zancada) × fracción          // el Double crudo
```

Para conteos **enteros** son el mismo test: `n ≥ ⌈x⌉ ⟺ n ≥ x` (ida: `n ≥ ⌈x⌉ ≥ x`; vuelta: `n ≥ x`
con `n` entero da `n ≥ ⌈x⌉`). Los dos tipos son `Long`, y ambos construyen **el mismo `Double` con
los mismos operandos**, así que tampoco hay hueco de precisión entre ellos. Unificar sobre la forma
redondeada es **exacto, no aproximado**.

⚠️ Afirmarlo por inspección habría sido una promesa. Hay un **test de barrido** sobre 4.000
distancias × las cinco cuentas alrededor de la barra (>10.000 comparaciones) que compara las dos
formas y falla el día que alguien cambie el redondeo.

## Un detalle de coma flotante que casi me como

Escribiendo el test di por hecho que 150 m a 0,75 m/paso con fracción 0,4 pedía **80** pasos. Pide
**81**: `0.4f` no es 0,4 — el `Float` más cercano es 0,400000005960…, así que la barra geométrica cae
en 80,0000011920929 y redondea hacia arriba.

Es un paso sobre una caminata de 150 m, muy dentro de la tolerancia que la fracción existe para dar,
y **es idéntico en las dos formas que este paso unificó**. Se queda tal cual y con el porqué escrito
en el test: cambiar la aritmética para que caiga en 80 sería un cambio de conducta disfrazado de
limpieza. Mi expectativa estaba mal, no el código.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**, y esta vez con demostración además de con la suite.

## Tests

`WalkedVsRodeTest` (7). Los dos que llevan el peso:

- **El barrido de equivalencia** — la demostración de que unificar era exacto.
- **Por qué el origen es un parámetro**: field 2026-07-22 01:47 (Redmi, Glorieta). El presupuesto se
  midió desde el PIN mientras el contador se había puesto a cero a mitad del egress, ~159 m antes;
  los ~83 m que quedaban costaron ~110 pasos contra los 129 que exigía la distancia al pin. Una
  caminata a casa leída como viaje, y el pin acabó dentro de la casa del usuario. **Los mismos pasos,
  dos orígenes, veredictos opuestos** — en un test.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.494 tests** (1.487 + 7), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1494 - desaparecidos vs base: 0 - nuevos: 40 (P1.1 a P1.5)
```

Quedan P1.6 a P1.10 de la Fase 1, todos `M` o `L` ligeros.
