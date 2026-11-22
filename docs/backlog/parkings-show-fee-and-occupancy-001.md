# PARKINGS-SHOW-FEE-AND-OCCUPANCY-001 · la ficha dice CUÁNDO se midió la ocupación

**Estado:** ✅ Done · rama `bugfix/PARKINGS-SHOW-FEE-AND-OCCUPANCY-001` · mergeado a master

## Problema

La ficha enseña la ocupación y no dice de cuándo es. Y como la escalera **retira** una cifra al
caducar, eso deja dos situaciones distintas contándose como la misma:

| Situación | Lo que veía el conductor |
|---|---|
| Madrid, cifra fresca | «42 plazas libres» · «De pago» |
| Madrid, cifra rancia | «De pago» |
| Las otras cuatro ciudades, que **no publican ocupación** | «De pago» |

Las dos últimas filas son el defecto: *«no lo sabemos»* y *«lo supimos y ya no vale»* le piden al
conductor cosas distintas —en el primero no hay nada que esperar, en el segundo la cifra vuelve en
minutos— y las dos se dibujaban igual. El invariante 1 de
[`data-providers-001.md`](data-providers-001.md) §F.1 ya lo había decidido y **nadie lo
implementó**: `agedMinutes` viajaba dentro de `ParkingHeadline.FreeSpaces` sin un solo lector.

⚠️ **Lo que esta rama tenía sin commitear quedó superado por master** (`73c6aab5f`,
[PARKINGS-PUT-LIVE-AVAILABILITY-FIRST-001](parkings-put-live-availability-first-001.md)), que
resolvió lo mismo mejor: un resolver ÚNICO (`occupancyLabel`) para fila, cápsula y ficha, en vez de
un `when` copiado dentro del peek. La rama se rehízo sobre master y aquel trabajo se conserva en el
tag `prerebase/PARKINGS-SHOW-FEE-AND-OCCUPANCY-001-wip` (`d4ddf3b98`).

## Diseño

### D.1 · Tres estados en el dominio, con UN solo criterio de frescura

`OccupancyStamp` (`None` · `Current` · `Expired`) en `domain/parkings/ParkingHeadline.kt`, al lado de
la escalera. ⛔ **No reescribe el umbral**: lo LEE de `headline()`. Su test lo falsifica con un dato
sellado por nosotros, que caduca a los 4 min y no a los 10 — si alguien copiase el umbral en el
sello, los dos se separarían justo ahí.

Función pura, no caso de uso, con el precedente explícito de la propia escalera: su resultado no
aparece en un diagnóstico y no mueve un pin [DET-VERDICT-NOT-PREDICATE-001].

### D.2 · Dos formas, y el umbral que las separa ya existía

`occupancyUpdatedLabel(nowMs)` en `PublicParkingLabels.kt`, **al lado de `occupancyLabel`**, porque
es el otro lado de la misma afirmación y los dos tienen que decidirse una sola vez.

```
Current  →  «Actualizado: hace 3 min»
Expired  →  «Última actualización: 19 nov 2026 a las 11:35»
```

⛔ **El umbral NO es una constante nueva: es el de la escalera.** Decisión del user, tras leer la
primera versión (que iba siempre absoluta): *«si está actualizado relativamente poco podemos poner
hace… en vez de la hora»*. Y «relativamente poco» ya tenía definición en este código — es
exactamente **mientras la cifra siga mandando** (`OccupancyStamp.Current`: 10 min sellado por la
fuente, 4 si lo sellamos nosotros). Así que no hay segundo número que elegir ni que mantener en
sync con el primero.

⛔ **Y el salto entre las dos formas es el MISMO evento, no un segundo criterio.** Al cruzar el
umbral la cifra desaparece de la línea de arriba y el sello cambia de forma **a la vez**, porque
las dos cosas dicen lo mismo: este dato ha dejado de mandar. Un umbral propio para el sello
inventaría un estado intermedio que no significa nada.

Por qué cada forma donde va:

- **Reciente → relativo.** Es lo accionable: un lapso de minutos se lee de un vistazo, sin mirar el
  reloj ni restar. Lo pinta `relativeTimeText`, que ya está traducido a los nueve idiomas y **lleva
  su propio tick de 60 s**, así que la etiqueta cuenta sola mientras la ficha está abierta en vez
  de congelarse en lo que leyó al componerse — que es la trampa por la que `formatClockTime` existe.
  ⇒ **cero cadenas nuevas** para esta rama.
- **Caducado → absoluto.** *«The only form that is still true an hour later»*
  [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001]. Aquí el dato puede ser de días — viaja por el CDN
  y por disco — y «hace 3 días» obliga a restar para saber de cuándo es.

⛔ **Dos puntos, no «a las».** `Actualizado: %1$s` y no `Actualizado a las %1$s`, y ahora por dos
razones: el hueco recibe la fecha delante cuando el sello no es de hoy — la segunda forma diría
«Actualizado a las 19 nov 2026 a las 11:35» — y además recibe un lapso, donde «a las» no pega
(«Actualizado a las hace 3 min»).

El formato absoluto lo pone `clockTimeWithDate(epochMs, nowMs)` en `TimeStringComposables.kt`, UNA
función que junta las dos mitades que ya existían (`formatClockTime` + `shortDateText`): juntarlas
en el call site es como nacen dos formatos de fecha en una app. El día se compara por **fecha de
calendario**, no por un umbral de horas — a las 00:05, un sello de las 23:50 es de ayer.

### D.3 · La línea, en el banner que ya existe

La tarifa y el sello **cualifican la misma afirmación**, así que comparten la segunda línea con el
· de siempre en vez de apilar una tercera: el peek no tiene scroll interno y cada línea la paga el
estado más alto a `fontScale` 1,3.

⛔ Y el banner **se dibuja en cuanto haya cualquiera de las dos cosas**. Con el hecho principal como
única condición, un parking que mide, tiene la cifra caducada y no declara tarifa se quedaba sin
banda — o sea, idéntico a uno que no mide, que es el defecto que este ticket cierra.

⛔ Sin teñir, tampoco al caducar: la rampa 🟢🟡🔴 es exclusiva de la caducidad de plazas, y un
parking medido hace 47 min no es una alerta [UI-COLOR-DOCTRINE-001].

## Criterio de éxito

1. **Los tres estados se distinguen en pantalla**, demostrado por captura, no por lectura del código.
2. **El umbral sigue viviendo en un solo sitio**, demostrado por el test del dato sellado por nosotros.
3. **Las otras cuatro ciudades no enseñan un hueco** donde Madrid enseña una fila.
4. Strings en los 9 locales y Dev Catalog con los estados nuevos, en esta misma tarea.
5. `./gradlew testAll --rerun-tasks` verde.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `ParkingHeadline.kt` (dominio) | ✅ gana `OccupancyStamp` + `occupancyStamp()`, sin tocar `headline()` |
| `PublicParkingLabels.kt` | ✅ gana `occupancyUpdatedLabel`, junto a `occupancyLabel` |
| `PublicParkingPeek.kt` | ✅ un solo `now` para las dos lecturas; banner con cualificador compuesto |
| `TimeStringComposables.kt` | ✅ gana `clockTimeWithDate`, que junta dos formatos que ya existían. `relativeTimeText` se reutiliza tal cual |
| `HomePublicParkingRows` · `PaparcarMapView` | ✅ **sin cambios**: una fila y una cápsula tienen UNA ranura, y el sello es del detalle |
| `ParkingHeadlineTest` | ✅ 5 tests nuevos, uno de ellos falsifica el umbral compartido |
| 9 locales | ✅ `common_date_at_time`, `parking_occupancy_updated_at`, `parking_occupancy_updated_last`. La forma relativa **no añade ninguna**: usa las `relative_time_*` que ya estaban |
| Dev Catalog | ✅ 3 variantes nuevas de peek: completo, caducada, sin ocupación en su ciudad. La de «ocupación y tarifa» pasa a 3 min de edad, para que se lea el «hace N min» y no «ahora mismo» |
| `data-providers-001.md` §F.1 | ✅ precisado: decía «hace X min» a secas, y son DOS formas con el umbral de la escalera |
| `docs/backlog/README.md` | ✅ recuento |

## Validación

`./gradlew testAll --rerun-tasks` verde. `:app:assembleProdDebug` y `:app:assembleMockDebug`
compilan — el worktree **sí** tiene `app/google-services.json` (enlazado desde el árbol principal),
así que ya no hace falta el `-x processProdDebugGoogleServices` de la validación anterior.

⛔ **El primer APK mintió.** Un `assembleMockDebug` incremental empaquetó el `:app` nuevo con el
`:shared` viejo: la galería traía las variantes nuevas y la ficha no traía el sello. Se vio al
comparar el sha256 del APK local con el del device, que **no coincidían** pese al `Success` del
`install -r`. Con `--rerun-tasks` los dos hashes coinciden
(`849b30aa7ee185afea7ecc7b982b892f968d055fa4eed0dc05a759351c8616ff`) y la ficha ya se pinta entera.
[BUILD-DEPS-AT-LATEST-STABLE-002]

Leído **en vivo** en el **Pixel 8** (AVD `Pixel_8`, Android 17, 1080×2400), flavor mock, Dev
Catalog — no de la captura, sino del árbol de nodos de `uiautomator`, que da el texto exacto:

| Estado | Variante | Lo que dice la ficha |
|---|---|---|
| Con dato | *Peek de parking (ocupación y tarifa)* | «42 plazas libres» · **«De pago · Actualizado: hace 3 min»** |
| Cero | *Peek de parking (completo)* | «Completo» · «De pago · Actualizado: …» — mismo camino que la fila de arriba (`Current`) |
| Caducado | *Peek de parking (ocupación caducada)* | «De pago» · «Última actualización: 19 nov 2026 a las 11:35» — la cifra desaparece, el sello se queda, y sale la rama con fecha |
| Sin dato | *Peek de parking (sin ocupación en su ciudad)* | «De pago» y nada más — **sin hueco**: el cuerpo sigue con distancia, horario, aforo y gálibo |
| Nada que decir | *Peek de parking (sin nombre, sin datos)* | sin banda ninguna, como antes |

⚠️ **Honestidad sobre las capturas**: *Con dato* se leyó con el build final (el de la forma
relativa). *Caducado*, *Sin dato* y *Nada que decir* se leyeron con el build inmediatamente
anterior, que solo se diferencia en la rama `Current` — las tres pasan por `Expired` o por `None`,
que no cambiaron ni una línea. *Cero* comparte camino con *Con dato*.

⛔ **Las otras cuatro ciudades** (Málaga, Zaragoza, Vigo, Pamplona — de las cinco de la prioridad
acordada, solo Madrid publica ocupación, y solo en 24 parkings): caen en la fila *Sin dato*. El
banner colapsa a la tarifa y no queda ni una línea vacía; si tampoco hay tarifa, no hay banda.

⛔⛔ **Y una trampa del entorno que costó dos diagnósticos falsos**: durante esta tarea **otro
proceso estuvo instalando el flavor mock en el mismo emulador** (builds del árbol principal, que
está en master). Dos veces la ficha apareció sin el sello y pareció un bug del código; las dos
veces era un APK ajeno encima del mío. El testigo que lo caza **no es el sha256** — ese coincide en
el instante posterior al `install -r` y deja de valer al minuto siguiente — sino
`dumpsys package <pkg> | grep lastUpdateTime` **antes y después** de la tanda de capturas: si cambia
y no fuiste tú, lo que hay en pantalla no es tu build.

## Pendiente

- ⏳ Verlo con datos REALES de Madrid en prod (hoy solo comprobado contra el mock).
- 🔵 El sello dice «Actualizado» para los dos sellados, y hoy eso es exacto porque **solo Madrid
  publica**, y Madrid sella su dato. Cuando entre una fuente con `isStampedByUs = true` (Málaga, cuyo
  CSV no trae marca de tiempo), habrá que decidir si la palabra cambia a «Consultado»: ahí la hora
  es **cuándo preguntamos**, no cuándo se midió. No se ha escrito ahora para no traducir a nueve
  idiomas una rama que ninguna ciudad alcanza. El umbral más corto (4 min) ya absorbe esa
  incertidumbre.
