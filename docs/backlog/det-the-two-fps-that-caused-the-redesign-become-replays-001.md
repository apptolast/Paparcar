# DET-THE-TWO-FPS-THAT-CAUSED-THE-REDESIGN-BECOME-REPLAYS-001

> **Estado:** ✅ **Done** — mergeado a master el 01-09-2026 (squash, `3adb08ae`). Rama y worktree borrados.
> **Origen:** **Pieza 7** del rediseño, regla 4 — *«replays de campo como regresión, empezando por
> `Trace_Parafarmacia2908` y `Trace_CasaGapAnchor3008`»*. Era lo único que le quedaba abierto: las
> tres reglas de guardarraíl estaban hechas desde el 30-08.
> **Con esto la Pieza 7 queda COMPLETA, y con ella el rediseño entero.**

---

## 1. Por qué estas dos y no otras

El rediseño de detección del 30-08 se escribió **desde estas dos sesiones**. Todo lo demás —
`DrivingEvidence`, el gate de `ArmEvidence`, *ningún reloj planta*, el two-tier sentry — sale de
leerlas. Y hasta hoy existían sólo como **prosa** en `REDESIGN-DETECTION-SYSTEM.md` y como 6 464
líneas de `parkdiag.log` en un cable.

Eso es exactamente lo que la Pieza 7 existe para evitar: *«sin esta pieza, las seis anteriores duran
hasta el próximo fix con prisa»*. Una doctrina defendida sólo por los tests unitarios de las piezas
que produjo está defendida contra las formas que ya se nos ocurrieron. Estos dos ficheros defienden
contra **las formas que el campo produjo**.

## 2. Cómo se sacaron los datos (⚠️ Firestore estaba ciego)

La app se reinstaló el 29-08 a las 21:36 → **uid nuevo sin `diagnostics_config`** → esa noche no hay
NADA en remoto. Todo sale del `parkdiag.log` por cable.

| dato | de dónde |
|---|---|
| fixes | `Coord: ─ loc#N lat= lon= speed= acc= sessionAge=` — el `sessionAge` da el Δ exacto |
| pasos | `✦ step #N` (parado / pre-drive) **y** `♬ step while driving` — los dos son pasos entregados |
| AR | `ARReceiver: ✓ IN_VEHICLE ENTER → bus stamped (trueTime=…)` y `Coord: ✱ onVehicleExit(at=…)` |
| t0 | mediana de `epoch(línea) − sessionAge` sobre todos los `loc#`; dispersión medida **0–5 ms** |

⛔ **El log NUNCA imprime las coordenadas de un pin.** Las tres que hacían falta (la valla que armó
cada sesión, y el pin que el FP 1 reemplazó) se sacaron **trilaterando** las lecturas
`SafetyNet: geof=XXXX: … (d=NNm)` contra el `OneFix` inmediatamente anterior, con mínimos cuadrados
sobre rejilla. **rms 0,24–0,28 m** con 15–30 observaciones cada una. Se validó el método contra un
centro conocido: la valla `825dcb60`, cuyo centro el coordinator SÍ imprime en su línea `SaveZone`
(`36.6098405,-6.2784644`), trilatera a `36.6098385,-6.2784467` — **2 cm de latitud de diferencia**.

Y el corpus se cruzó con lo ya escrito: la tabla del KDoc de `drivingEvidence` afirma que el FP tuvo
**1 fix creíble, 72 m de excursión, 0 ms de banda**; la extracción da **1 fix creíble (7,71 m/s @
16,086 m), 71,6 m de excursión máxima**. La memoria del rediseño afirma que la sesión de casa tiene
**7** `credibleDrivingFixes`; la extracción da **7**.

## 3. FP 1 · `Trace_Parafarmacia2908` — 102 fixes, 19 pasos

**Lo que pasó** (29-08 23:47:44 → 23:56:33, sesión `c6a57fad`): un AR `IN_VEHICLE ENTER` llegó con
**89 019 ms de retraso** con el móvil parado a 38 m de la valla de su propio coche → arma
`enter_at_car`. Entonces **UN** fix trajo velocidad de conducción: loc#2, 7,71 m/s a 16,086 m de
accuracy, 71,6 m fuera de loc#1 — y loc#3, 3,5 s después, ya había **deshecho 64,8 m**, dejando el
móvil a 8,5 m de donde empezó. `hasEverMoved` se quedó en **false** los 102 fixes.

12 pasos parado bastaron para el camino rápido `steps+egress`, y tras el hold de 125 s el build de
campo plantó un pin **exacto** a fiabilidad 0.9, **reemplazando un pin bueno a 58,3 m y borrando su
geocerca**. El coche no se movió en toda la sesión.

**Qué hace master con este stream:** 0 pines, 1 pregunta, `ended`.

⚠️ **La pregunta se queda, y es correcto.** Mi primera aserción decía *«tampoco puede preguntar»* —
me la inventé. La doctrina dice lo contrario: *ante la duda se PREGUNTA*. El coste real del rediseño
en este stream es **una pregunta a medianoche yendo a la farmacia**, y una pregunta sin contestar no
deja pin. El build de campo hizo la MISMA pregunta a las 23:53:48 y plantó igual 2 min 40 s después,
por el camino rápido que este trace ya no abre.

## 4. FP 2 · `Trace_CasaGapAnchor3008` — 376 fixes, 643 pasos, 2 AR EXIT, 3 AR ENTER

**Lo que pasó** (30-08 01:20:28 → 01:49:36, sesión `825dcb60`): el viaje es real y la app lo leyó
bien — 7 fixes creíbles, `hasEverMoved` con 1 105,5 m, 45 021 ms en la banda, una salida sostenida de
495 m a 29,1 m/s. Es un viaje de **dos tramos** (parada de recado que pregunta y sigue conduciendo).

Luego el stream se apagó **198 219 ms**. El último fix antes del hueco iba a 11,81 m/s; el primero de
después es `36.6098405,-6.2784644` — un punto que el coche estaba **pasando**. La parada que se abrió
ahí se marcó GAP-ENTERED y la app se negó a plantar y **preguntó**, que es lo correcto. Nadie
contestó, y 903 022 ms después el guardado desatendido conservó el aparcamiento como una zona de
250 m **centrada en esa misma ancla de hueco**.

Lo que el propio trace mide sobre ese centro: de los **216 fixes posteriores al hueco**, exactamente
**uno** cae a menos de 100 m — y es el ancla misma (media 156 m, máx 162 m). La misma ventana tiene
**18 fixes con accuracy ≤12 m** cuyo centroide es `36.6084284,-6.2782107` con **6,7 m de dispersión**,
un cúmulo que se cerró **más de diez minutos antes** de que el guardado disparara. El centro elegido
está a **158,6 m** de él.

**Qué hace master:** mismo veredicto (`confirmed_unattended_zone_gap_anchor`, área, r=250 m) con el
centro en el reposo presenciado — **a 5,1 m** del centroide del cúmulo, 158,0 m del centro de campo.

### ⚠️ La cola: los 3 fixes que NO son de la sesión

El stream de campo termina en Δ1 743 862 porque el guardado disparó 7 ms después y la cerró. El build
de campo había mostrado su pregunta a Δ840 840; **master la muestra por la misma razón a Δ857 163 —
16,3 s más tarde** — así que sobre el stream grabado la ventana de 900 s cierra **13,3 s después del
último fix que existe**. El veredicto corre sobre un fix; sin fix no corre y el aparcamiento se pierde.

Por eso hay una cola, y por eso **no está inventada**: son los tres `OneFix` que la red de seguridad
tomó a las 01:49:36.813, 01:50:29.866 y 01:55:30.011, del mismo log, todos sobre el cúmulo de reposo.
Vive en un `val` aparte (`TRACE_CASA_GAP_ANCHOR_3008_QUIET_TAIL`): el trace de la sesión sigue siendo
1:1 y un test que necesite el timeout lo dice concatenando.

## 5. Falsación — cada aserción vista en rojo

⛔ Regla del proyecto: *un test de prohibición sin verlo fallar es un test que siempre pasa.* Seis
neutralizaciones, una por aserción, cada una revertida después:

| # | qué se neutralizó | qué se puso rojo |
|---|---|---|
| 1 | `ENTER_AT_CAR` de vuelta al lado que confirma en silencio (`ArmLabel`) | FP 1 planta **1 pin** |
| 2 | las **tres** barras de `drivingEvidence`, con el gate del arm intacto | FP 1 planta **1 pin** |
| 3 | `bestWitnessedCenter` → `center = anchor` | zona a **36 m** del reposo (bar: ≤30) |
| 4 | `DET-STOP-MUST-BE-STILL-IN-SPACE-001` **+** `bestWitnessedCenter` | zona en `36.6098405,-6.2784644` — **el FP de campo, literal**, 158 m |
| 5 | `doubtMeters / 8` | radio **61,9 m** en vez de 250 |
| 6 | `promptRetracted = false` | la secuencia pierde su fila `PROMPT_RETRACTED/drive_resumed` |

📌 **Lo que la falsación enseñó y no estaba escrito en ninguna parte: en el FP 2 hay DOS guardas
moviendo el centro, en cantidades distintas, y ninguna sobra.**

- Con **sólo** `DET-STOP-MUST-BE-STILL-IN-SPACE-001` apagado (y `bestWitnessedCenter` vivo) el test
  **pasa**: el refinamiento solo ya lo salva. El salto de 120 m 1,5 s después del fix de hueco es lo
  que refuta esa parada, y por eso en master el ancla ya **no** es el fix de hueco.
- Con **sólo** `bestWitnessedCenter` apagado se queda **a 36 m**: la guarda sola saca el centro del
  fix de hueco, pero no lo lleva a casa.
- Hacen falta **las dos apagadas** para reproducir el pin de campo. Esa es la razón por la que la
  aserción «≥100 m del centro de campo» **tiene testigo** y no es una afirmación que no puede fallar.

Y en el FP 1, la tabla del KDoc de `drivingEvidence` afirma que *«cada barra mató el falso positivo
por sí sola»*: falsaciones 1 y 2 lo **demuestran sobre el stream** en vez de afirmarlo sobre él.

## 6. Alcance — lo que este ticket NO hace

- **No toca una línea de producción.** Dos ficheros de trace nuevos y dos tests. Todas las guardas
  que hacen falta ya estaban en master; lo que faltaba era el testigo.
- **No añade guardarraíl de población para los traces.** Un `Trace_*.kt` que se quede sin test que lo
  lea no lo detecta nada hoy — vale para los 16 anteriores igual que para estos 2. Es un chequeo
  Konsist barato y es **otro ticket**, no éste.
- **No mide el coste de la pregunta del FP 1 en campo.** Que un `enter_at_car` con podómetro mudo
  pase de pin silencioso a pregunta es un coste aceptado y ya anotado en la Pieza 3b; cuánto se paga
  se ve en un viaje real, no aquí.

## 7. Verificación

- **2 072 tests, 0 fallos** (`:shared:testDebugUnitTest`), incluidos los 18 replays de campo.
- `assembleProdDebug` + `assembleMockDebug` compilan.
- Sin cambios de UI, de strings ni de estado → nada que reflejar en el Dev Catalog ni en los 9 locales.
