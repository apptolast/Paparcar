# TEST-AN-ORPHANED-FIELD-TRACE-STILL-LOOKS-LIKE-COVERAGE-001

> **Estado:** implementado 2026-09-01 · rama `test/TEST-AN-ORPHANED-FIELD-TRACE-STILL-LOOKS-LIKE-COVERAGE-001-guardrail` (base `3adb08ae`)
> **Origen:** el hueco que dejé escrito al cerrar la Pieza 7 en
> `DET-THE-TWO-FPS-THAT-CAUSED-THE-REDESIGN-BECOME-REPLAYS-001`: *«un `Trace_*.kt` que se quede sin
> test que lo lea no lo detecta nada hoy — vale para los 16 anteriores igual que para estos 2»*.

---

## 1. El defecto

Los traces de `domain/detection/coordinator/replay/` **no son tests: son PRUEBAS**. Cada uno es un
stream que un móvil real grabó una noche real, transcrito una vez desde un `parkdiag` por cable y
guardado para siempre. La doctrina que los creó lo dice: *every field bug becomes a permanent
fixture: record the trace, **replay it**, assert the corrected outcome — the regression can never
silently return.*

La palabra que trabaja en esa frase es **replay**. Un trace que nadie reproduce no afirma nada. Pero
sigue en la carpeta, sigue saliendo en un `ls`, y **el corpus se cita como un NÚMERO** — *«los 16
replays de campo»*, *«2.072 tests, incluidos los 18 replays»* — en mensajes de commit, en
`PARKING-DETECTION.md` y en memoria. Un huérfano no deja sólo de proteger: **sigue contando como
protección**. Es la misma forma que `UI-TYPE-SYSTEM-HYGIENE-001` (una exención sobre un componente
que ninguna pantalla renderizaba — *«no es una excepción, es un agujero»*) y que
`I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001`.

Borrar el test que lee un fixture de 1.100 líneas es un diff de una línea, y hasta hoy **nada en el
repo lo notaba**.

## 2. La unidad es el STREAM, no el fichero

18 ficheros declaran **21** valores `List<TraceEvent>`, y los sobrantes son justo las mitades
interesantes:

| stream extra | qué es |
|---|---|
| `TraceEnamorados001.eventsWithoutRecovery` | la variante peor-caso del mismo trace |
| `TraceSupermarket001.wander` | el tramo donde el ancla NO debe derivar |
| `TRACE_CASA_GAP_ANCHOR_3008_QUIET_TAIL` | lo único que permite que corra un timeout de 900 s |

Una regla por FICHERO dejaría morir cualquiera de esos tres dentro de un fichero que sigue
pareciendo usado.

## 3. Qué cuenta como «leído»

Una referencia **desde un fichero de test distinto del que lo declara**, en código y **no en prosa**.

- **Cualificada** para los miembros de `object`: se busca `TraceSupermarket001.wander`, no `wander`.
  Buscar `wander`/`events`/`park` a secas casa con 60 ficheros y daría por leído cualquier trace
  miembro pasara lo que pasara.
- **Sin comentarios**: se quitan bloque, línea entera y **cola de línea**. Un trace citado sólo por
  un enlace KDoc es exactamente un huérfano con coartada.

⚠️ **Dónde se planta la regla, dicho y no insinuado**: comprueba que un fichero de test menciona el
stream; **no sigue la cadena hasta un `@Test`**. Un helper de fixtures que lea un trace y al que ya
no llame nadie la satisfaría. Ese salto no es gratis de verificar y el caso no se ha dado nunca aquí.

## 4. Falsación — cinco, todas vistas en rojo

⛔ El guardarraíl salió **verde el primer día** (0 huérfanos de 21), que es justo el estado en el que
un test de prohibición no vale nada hasta verlo fallar.

| # | qué se neutralizó | qué se puso rojo |
|---|---|---|
| 1 | se quita el uso de `TRACE_CALLE_GAVIA_001` (top-level) | lo reporta |
| 2 | se quita el uso de `TraceSupermarket001.wander` (miembro) | lo reporta — la cualificación funciona |
| 3 | ambas referencias sobreviven **sólo dentro de un comentario** | los reporta igual |
| 4 | se renombra la ruta del paquete en `GuardrailScope` | salta el testigo de población: 0 < 9, **los dos tests en rojo** |
| 5 | un trace pierde su tipo explícito (`val X = buildList {…}`) | el test del PARSER en rojo; el de huérfanos sigue verde, correctamente |

📌 **La falsación 3 encontró un defecto real en mi primera versión.** `stripComments` sólo quitaba
comentarios de línea ENTERA, y el caso natural al editar es dejar `// was TRACE_CALLE_GAVIA_001`
**detrás del código**, en la misma línea. Con esa versión el huérfano pasaba: la falsación 1 y la 3
eran el mismo experimento y salió **verde**. Se arregló cortando también la cola (`(?<!:)//…`, el
`(?<!:)` para no partir un `https://`), y cortar no puede esconder un uso real: un uso vive ANTES del
`//`, y uno que viva después está comentado por definición.

## 5. Por qué el segundo test existe

`fieldTraceFiles()` ya se niega a devolver menos de 9 ficheros, así que un paquete movido no puede
dejar la regla pasando sobre una lista vacía. Pero hay otra cosa que puede irse a cero **sin mover un
solo fichero**: el **PARSE**. Los streams se encuentran casando `val <name>: List<TraceEvent>`, y un
fixture escrito de otra forma —tipo inferido, función que devuelve la lista— es **invisible para el
matcher pareciendo normal en la carpeta**. Entonces la regla informa de cero huérfanos sobre cero
traces. El segundo test exige que cada uno de los 18 ficheros aporte al menos un stream reconocido.

## 6. Lo que NO hace, y su ticket

Un trace puede reproducirse y **no afirmar nada sobre dónde acabó el pin**: sus constantes de
ground-truth se declaran y nadie las lee. Hay **6 así hoy** (`TraceCameliasOppo001.REAL_CAR_LAT/LON`
y `FIELD_PIN_LAT/LON`, `PARAFARMACIA_2908_FIELD_PIN_LAT/LON`). No es este defecto y no se arregla con
esta regla: → `test-a-trace-whose-ground-truth-is-never-asserted-001`.

## 7. Verificación

- **2.080 tests, 0 fallos**. Población medida: **18 ficheros · 21 streams · 234 ficheros de test**.
- Sólo se toca `androidUnitTest/architecture/` (el guardarraíl nuevo + dos poblaciones en
  `GuardrailScope`). Cero producción, cero UI, cero strings.
