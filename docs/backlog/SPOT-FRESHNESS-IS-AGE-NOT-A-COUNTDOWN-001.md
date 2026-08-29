# SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001 · La frescura de una plaza es su EDAD, no lo que le queda de vida

**Estado:** ✅ Done · mergeado en master (29-08-2026) · ⏳ sin ver en device

## Problema

Tres cosas distintas se están contando como si fueran una, y dos de ellas se contradicen en la
misma fila de la lista.

**1 · El chip miente sobre lo que sabe.** `TTLIndicator` muestra *cuánto le queda a la plaza para
que el barrido la borre* (`expiresAt - now`) con un icono de cronómetro. Pero **nada mide si la
plaza sigue libre**: no existe ningún flujo de "ya está ocupada" en el dominio — `enRouteCount`
cuenta quién va de camino, y el único mecanismo que elimina una plaza es la expiración. O sea que
`expiresAt` es nuestra fecha de recolección de basura, y la estamos presentando al usuario como si
fuera información sobre la plaza. Es mecánica interna filtrada al copy.

**2 · Hay DOS rampas de frescura con entradas distintas, y se contradicen.** Ambas pintan
verde/ámbar/rojo sobre la misma plaza:

| | Entrada | Umbrales |
|---|---|---|
| `SpotReliabilityUiState.toReliabilityUiState()` → puck, marcador de mapa, eyebrow del peek | `confidence`, que ya decae **proporcionalmente** al TTL (`decayedConfidence`, `timeFactor = remaining / total`) | 0.75 / 0.55 |
| `TTLIndicator` → el chip de la fila | minutos **absolutos** restantes | 10 min / 3 min |

Para una plaza automática (TTL 2 h, sin votos, `confidence` 1.0):

| Edad | Puck / marcador | Chip |
|---|---|---|
| ≤ 30 min | 🟢 HIGH | 🟢 verde |
| 54 min | 🔴 **LOW** | 🟢 **verde** |
| 1 h 50 | 🔴 LOW | 🟡 ámbar |

**A los 54 minutos el marcador ya está en rojo y el chip de la misma plaza sigue en verde.** Para
una manual (TTL 15 min) la contradicción es la simétrica: a los 4 min el puck está en ámbar y el
chip aún en verde.

**3 · Los 15 min de la manual codifican CONFIANZA, no caducidad.** Una plaza libre se ocupa igual
de rápido la haya visto un coche saliendo o una persona. `MANUAL_SPOT_TTL_MS = 15 min` frente a
`AUTO_SPOT_TTL_MS = 2 h` no describe dos velocidades de decaimiento distintas: describe cuánto nos
fiamos del que informa. Y la confianza ya tiene sus propios canales (`confidence`, `SpotStatus`,
el badge de reporte manual).

**4 · Además, con 15 min la manual es invisible.** En arranque en frío, con poca densidad de
usuarios, el coste de un mapa vacío es mayor que el de una plaza vieja: el usuario prefiere ver
actividad y juzgar él por el tiempo transcurrido. Una TTL corta no protege de nada — sólo borra
la única señal que teníamos.

## Doctrina violada

- **Un número, un trabajo.** `expiresAt` hace de recolector de basura *y* de indicador al usuario.
  `confidence` mezcla votos de la comunidad *con* el paso del tiempo, y su resultado se llama
  "the freshness RAMP" en el propio doc de `SpotReliabilityUiState`. Es el mismo patrón que ya
  corregimos en tipografía (el peso pertenece al rol) y en color (el estado se escribe, no se tiñe).
- **Sistemas, no parches** — dos rampas paralelas sobre el mismo concepto es exactamente el
  invariante duplicado que la regla prohíbe. Se arregla en UN sitio.
- **No copy al usuario con mecánica interna** (CLAUDE.md): "quedan 12 min" es nuestra deadline de
  Firestore, no un hecho sobre la plaza.
- **Fallo asimétrico / ante la duda se pregunta**: el equivalente aquí es *ante la duda se muestra
  con su edad*, no se oculta ni se promete.

## Señales / datos disponibles

- `Spot.location.timestamp` — cuándo se publicó. Es la entrada real de la rampa.
- `Spot.expiresAt` — se queda **exactamente como está**, como TTL de barrido (Room
  `SpotDao:49`, filtros en `SpotRepositoryImpl:58/96/104`). Deja de tener presencia en UI.
- `Spot.confidence` + `acceptCount`/`rejectCount` — confianza de la comunidad.
- `SpotStatus` (`CONFIRMED` / `PROVISIONAL` / `RETRACTED`) — la duda sobre si la salida ocurrió.
- `Spot.isManualReport` — procedencia, ya resuelta con un glifo (badge de persona), no con color.
- `home_peek_spot_age_now` / `_min` / `_hour` — **ya existen en los 9 locales** y el peek ya los
  pinta. La edad ya se muestra; sólo que junto a una cuenta atrás que la contradice.

## Diseño

**Un solo eje de frescura, con una sola entrada: la EDAD. Todo lo demás (procedencia, duda,
votos) tiene ya su propio canal y no vuelve a entrar en la rampa.**

### D1 · TTL única de 2 h

`SpotTtlPolicy.MANUAL_SPOT_TTL_MS` desaparece: `ttlMsForType()` devuelve `AUTO_SPOT_TTL_MS` para
todo lo que no sea provisional. La TTL vuelve a ser sólo lo que dice su nombre — cuándo se barre el
documento — y deja de codificar confianza.

`PROVISIONAL_SPOT_TTL_MS = 12 min` **no se toca**: no es frescura, es radio de explosión de un
posible fantasma, y su propia documentación lo dice (*"the floor, not the plan"*).

Consecuencia buscada: al haber una sola TTL real, *proporcional* y *absoluto* colapsan en la misma
función y la rampa se define directamente en minutos de edad — sin división, y sin que se estire
de forma perversa sobre los 12 min de una provisional (que se pintaría verde durante 9 minutos
justo cuando más duda hay).

### D2 · La rampa se calcula una vez, sobre la edad

`toReliabilityUiState()` conserva su enum (HIGH/MEDIUM/LOW ya significa verde/ámbar/rojo y su doc
ya se declara "the freshness RAMP") pero **cambia de entrada**: de `confidence` a la edad. Los
umbrales viven junto a `SpotTtlPolicy` en `domain/`, testeables en `commonTest`:

| Edad | Nivel | Lectura |
|---|---|---|
| ≤ 10 min | 🟢 HIGH | recién liberada |
| ≤ 30 min | 🟡 MEDIUM | puede seguir |
| > 30 min | 🔴 LOW | probablemente ya no está — pero sigue en el mapa hasta las 2 h |

Rojo deja de significar "va a expirar" y pasa a significar "poco probable". Números de primera
hipótesis: los calibra el campo.

### D3 · El chip deja de ser un cronómetro y pasa a ser la edad

`TTLIndicator` pierde sus umbrales propios (`TTL_CRITICAL_THRESHOLD_MINUTES`,
`TTL_WARNING_THRESHOLD_MINUTES`) y su cálculo de restante. Recibe la edad y el nivel ya resuelto de
D2 — deja de tener opinión propia sobre el color. Renombrado a lo que muestra (`SpotAgeIndicator`).

Copy: se reutilizan las keys de edad que ya existen (`home_peek_spot_age_*`), así que **no hace
falta traducir nada nuevo en 9 locales**. `spot_indicator_ttl_minutes` y
`spot_indicator_ttl_under_minute` se retiran; `spot_indicator_ttl_expired` también — con el barrido
activo ninguna plaza llega a verse expirada, era un estado inalcanzable.

### D4 · El peek deja de decir las dos cosas

`SpotPeek` calcula hoy `ttlMinutes` **y** `spotAgeMin`, y le pasa el primero a
`FiabilityIndicator(expiresInMin = …)`. Se queda sólo la edad; `remainingMinutes()` se borra.

### D5 · `timeFactor` sale de `decayedConfidence` — DECIDIDO (user, 29-08)

`confidence = communityConfidence * timeFactor` contaba el tiempo dos veces en cuanto la rampa pasó
a ser la edad, y mantenía el defecto de origen (votos y reloj en un solo Float). `decayedConfidence`
pasa a llamarse `communityConfidence` y deja de recibir `reportedAt`/`expiresAt`/`nowMs`.

Efecto lateral bueno: **el mapper deja de necesitar reloj** y vuelve a ser función pura del DTO
(fuera el `Clock` y el `@file:OptIn(ExperimentalTime)`).

Efecto lateral que hay que mirar: al dejar la rampa de leer `confidence`, **los votos de la
comunidad se quedan sin ningún consumidor visible**. Estaba oculto detrás de la mezcla; separarlo lo
puso a la vista. No se resuelve aquí (teñir NO es la respuesta correcta a "esta plaza está
ocupada") → `SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001`, que absorbe también el follow-up de
"marcar plaza como ocupada": son el mismo problema.

### D6 · Umbrales — DECIDIDOS (user, 29-08)

🟢 ≤ 10 min · 🟡 ≤ 30 min · 🔴 > 30 min, sobre una vida de 2 h.

## Criterio de éxito

- Una plaza de 60 min de edad se ve **del mismo color** en el marcador, en el puck de la fila y en
  su chip. Hoy no.
- El chip dice "hace 12 min", nunca "quedan 12 min".
- Una plaza manual y una automática de la misma edad se ven igual de frescas; se distinguen por el
  glifo de procedencia, no por el color ni por la duración.
- Ninguna plaza desaparece del mapa antes de 2 h (salvo provisional retractada).
- Tests: umbrales de la rampa por edad, `ttlMsForType` unificada, y ausencia de la vía de cuenta
  atrás.
- Galería mock: variantes de plaza recién liberada / 20 min / 1 h, para ver la rampa entera sin
  esperar.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `ui/components/SpotIndicators.kt` `TTLIndicator` | cuenta atrás + umbrales propios | ✅ → `SpotAgeIndicator(ageMs, freshness)`, sin opinión propia sobre el color |
| `presentation/util/SpotReliabilityUiState.kt` | rampa desde `confidence` | ✅ borrado → `SpotFreshness` (dominio) + `SpotFreshnessUi.kt` |
| `data/mapper/SpotDtoMapper.kt` `decayedConfidence` | `timeFactor` proporcional al TTL | ✅ → `communityConfidence()`, sin reloj (D5) |
| `presentation/.../peek/SpotPeek.kt` | `remainingMinutes` + doble reloj | ✅ borrado `remainingMinutes()`; un solo `nowMs` alimenta nivel y edad |
| `presentation/.../peek/PeekShared.kt` `FiabilityIndicator` | `expiresInMin` + "Caduca en N min" | ✅ parámetro y texto fuera; `FIABILITY_EXPIRY_WARN_MIN` muerto, borrado |
| `presentation/.../sheet/components/HomeSpotRows.kt` | `TTLIndicator(expiresAtMs)` + 2 llamadas a la rampa | ✅ un reloj y un nivel por fila, compartidos por puck y chip |
| `ui/components/PaparcarMapView.kt` | marcadores en `remember` **sin** clave de tiempo | ✅ clave `freshnessMinute` — si no, el pin se congelaba con el color de la composición |
| `domain/model/SpotTtlPolicy.kt` | manual = 15 min; `ttlMsForType(type, …)` | ✅ TTL única; el parámetro `type` retirado por no influir ya en el resultado |
| `androidMain/.../ReportSpotWorker.kt` · `iosMain/.../IosReportSpotScheduler.kt` | `ttlMsForType(spotType, provisional)` | ✅ ambos a `ttlMs(provisional)` |
| `ui/components/PaparcarMapMarkers.kt` · `ReliabilityMeter.kt` · `ui/theme/SpotStateColors.kt` | tipaban el enum viejo | ✅ heredan la rampa nueva, sin cambio de comportamiento |
| `data/repository/SpotRepositoryImpl.kt` · `SpotDao.kt` | `expiresAt` como barrido | ✅ exento — sigue siendo su trabajo, y ahora el ÚNICO |
| `SpotStatus` / `RETRACTION_GRACE_MS` · `PROVISIONAL_SPOT_TTL_MS` | retractación / radio de explosión | ✅ exentos por diseño, intactos |
| `presentation/preview/FakeData.kt` | plazas de 2/4/6/8/15/40 min | ✅ ya cubrían la rampa entera; el chip ahora se ve en previews (antes `expiresAt = 0` lo ocultaba) |
| `app/src/mock/.../StateGalleryScreen.kt` | sin variantes de frescura | ✅ 3 variantes de peek (verde 4 min / ámbar 15 / rojo 40) |
| Los 9 `strings.xml` | 11 keys de cuenta atrás y fiabilidad | ✅ retiradas y sustituidas por 10 keys de edad/frescura en los 9 locales |
| `domain/usecase/spot/SendSpotSignalUseCase.kt` | votos → `confidence` → rampa | ⚠️ **queda sin consumidor visible** → `SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001` |

## Verificación

- `:shared:testDebugUnitTest` — **1.777 tests, 0 fallos** (`--rerun-tasks`), incluyendo
  `SpotFreshnessTest` (9 casos: fronteras exactas, reloj adelantado, timestamp ausente) y los 2
  casos nuevos del mapper que fijan el desacoplo votos/reloj.
- `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` — verdes.
- Barrido final: cero referencias vivas a `TTLIndicator`, `SpotReliabilityUiState`,
  `toReliabilityUiState`, `MANUAL_SPOT_TTL_MS`, `ttlMsForType`, `decayedConfidence`,
  `expiresInMin`, `spot_indicator_ttl_*`, `home_peek_spot_expires`, `home_spot_reliability_*`.
- ⏳ **Sin ver en device.** Falta `/run` y mirar la fila, el peek y el mapa en mano.

## Follow-ups fuera de alcance

- 🎫 **`SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001`** (creado) — "Ya no está" no le hace nada a la
  plaza. Absorbe el follow-up de "marcar plaza como ocupada": son el mismo problema visto desde el
  botón y desde el dato.
- **Decaimiento por demanda de la zona.** La velocidad real a la que se ocupa una plaza depende de
  la densidad, no del método de detección. Requiere datos que aún no tenemos.
- **Desplazamiento de origen de la manual.** Un reporte humano puede llevar ya minutos libre cuando
  se envía (`t=0` de la app ≠ `t=0` real), así que podría arrancar la curva ya en ámbar. Discutido
  y aparcado: es defendible que eso ya lo dice el badge de procedencia.
