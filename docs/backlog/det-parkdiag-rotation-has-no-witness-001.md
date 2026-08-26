# DET-PARKDIAG-ROTATION-HAS-NO-WITNESS-001 · la retención del parkdiag no tenía quien la vigilara

**Estado:** ✅ Done — 5 tests en `androidUnitTest`, los 5 verificados en rojo contra mutaciones
deliberadas de `rotate()`. Sin cambios en producción.

## Problema

`DET-PARKDIAG-KEEP-MORE-HISTORY-001` (`a3127690`) pasó el `parkdiag` de 1 a 5 rotaciones porque dos
veces en una semana (campo 24-08 y 25-08) la evidencia del incidente que se estaba investigando ya
se había caído por el final. Entró **sin un solo test**.

El cambio no es una constante: es un bucle de renombrados que desplaza generaciones. Puede perder
la dirección, saltarse una generación o pisar una encima de otra. Y **todo el bloque de fichero de
`performLog` vive dentro de un `runCatching`**, así que una rotación rota no lanza — simplemente
guarda menos historia de la que promete, en silencio, hasta el día que se necesite.

Es decir: el modo de fallo del arreglo es exactamente el fallo que el arreglo venía a terminar, y
ningún otro test de la suite lo notaría.

## Doctrina violada

Ninguna regla de detección: `FileAntilog` no decide nada, es instrumentación. Lo que rompe es la
condición previa de todo el trabajo de campo — *"tras un viaje raro, ¿tenemos datos para saber qué
pasó, sin adivinar?"*. Una retención sin testigo es una respuesta que no se puede dar por buena.

## Señales / datos disponibles

Todo lo necesario ya está inyectado en el constructor: `maxBytes` y `keptRotations` son parámetros,
no constantes escondidas. Con `maxBytes = 1` la rotación deja de ser aproximada y pasa a ser exacta
— el tamaño se comprueba **antes** de escribir, así que la primera entrada cae en un fichero vacío
y cada entrada posterior rota primero. N entradas ⇒ N-1 rotaciones ⇒ una entrada por generación,
que es lo que permite afirmar *qué* hay en *cada* slot y no sólo cuántos ficheros existen.

## Diseño

Test de comportamiento observable sobre el sistema de ficheros real de Robolectric
(`androidUnitTest`, que ya tiene Robolectric y `androidx.test.core`), sin tocar el estado global de
Napier: `Antilog.log(...)` es público, `performLog` es `protected`. Cinco testigos:

1. **No rota por debajo del umbral** — el testigo negativo. Sin él, una implementación que rotara
   en cada línea pasaría todos los demás.
2. **El activo se convierte en `.1`** y el fichero activo arranca vacío, no acumulando.
3. **Las generaciones se desplazan en orden** — `.1 .2 .3` caminando hacia atrás en el tiempo, y
   nada aparece en `.4`/`.5`. Este es el que caza una dirección de bucle invertida: recorriendo
   ascendente, el mismo fichero se arrastraría hasta la generación más alta dejando huecos.
4. **Sólo se descarta la más vieja** — 5 rotaciones en 3 slots dejan las 3 más recientes y las dos
   primeras entradas desaparecen. Afirma las dos mitades: qué queda **y** qué se cae.
5. **El `parkdiag.log.old` heredado sobrevive intacto** — promesa explícita del commit anterior
   (borrarlo en silencio sería el mismo error en miniatura). Se comprueba que ni se borra ni se
   escribe dentro de él.

Sin cambios en producción: `FileAntilog.kt` no se toca.

## Criterio de éxito

- Los 5 tests verdes sobre el código de hoy → ✅ **1.662 tests, 0 fallos** (1.657 previos + 5).
- **Verificados en rojo**: un test que no se ha visto fallar no es un testigo. Cinco mutaciones
  deliberadas sobre `rotate()`, revirtiendo el fichero entre cada una:

| # | Mutación | Tests que la cazan |
|---|---|---|
| A | Bucle ascendente (`1..keptRotations-1`) en vez de `downTo` | shift-down · discard-oldest |
| C | El bucle se salta la primera generación (`downTo 2`) | shift-down · discard-oldest |
| D | Rotar en cada línea (sin comprobar `maxBytes`) | below-threshold |
| E | Borrar el `parkdiag.log.old` heredado al rotar | legacy-untouched |
| F | No renombrar el fichero activo a `.1` | move-to-first · shift-down · discard-oldest |

Cada uno de los 5 tests falla en al menos una mutación. Ninguna mutación pasó desapercibida.

### Hallazgo: hay una línea de `rotate()` que ningún test puede vigilar

La sexta mutación que probé — **quitar el `File(dir, "$BASE_NAME.$keptRotations").delete()`** — no
la caza nadie, y es correcto que no la cace: `rename` **sobrescribe el destino** tanto en POSIX
(Android) como en el JVM de la máquina de desarrollo, así que borrar la generación más vieja antes
del desplazamiento no cambia nada observable. Es código defensivo redundante, no un invariante.

Se deja como está — cuesta una llamada y protege de un `rename` que no sobrescriba — pero queda
escrito aquí para que nadie lo lea como "cubierto por tests". No lo está, y no puede estarlo sin
inventarse un filesystem falso, que sería un testigo del mock y no del comportamiento.

## Consumidores auditados

`FileAntilog` tiene un único call site (`PaparcarApp.kt:38`, `Napier.base(FileAntilog(this))`) y
ningún otro código lee las generaciones — se leen desde `adb`, y esos comandos ya quedaron barridos
en los tres bloques de `diagnostics/README.md` y en los dos KDoc del propio fichero por
`DET-PARKDIAG-KEEP-MORE-HISTORY-001`. Nada nuevo que cerrar aquí.
