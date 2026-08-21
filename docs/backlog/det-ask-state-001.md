# DET-ASK-STATE-001 — la pregunta de detección es un ESTADO de Home, no solo una notificación

**Estado:** ✅ Done (2026-08-22) — mergeado a master por squash. ⏳ Queda el **viaje de campo**.
**Rebases 2026-08-22:** dos, sobre `6cb72a73` y luego sobre `f42e393b` (DET-CLOSE-ZONE-WHEN-THE-BODY-
WALKED-001), **sin un solo conflicto** pese a que master tocó entremedias `BrowsePeek.kt`,
`StateGalleryScreen.kt` y el mismo `PARKING-DETECTION.md` (UI-CHIP-ROUTE-GLYPH-001,
UI-PEEK-DRIVING-HAS-NO-MOTION-001, UI-PROVISIONAL-SPOT-…). En `BrowsePeek` conviven el eyebrow de
la pregunta y el pulso de "viaje vivo" de UI-PEEK-DRIVING-HAS-NO-MOTION-001, verificado a mano.
**1.378 tests verdes** tras el último rebase; prod y mock compilan.
⚠️ `--rerun-tasks` **corrompe la caché de KSP** en este repo (`FileNotFoundException` en
`kspCaches/…/symbols`) y deja la suite en rojo sin que la rama tenga nada. Se cura con
`rm -rf composeApp/build/kspCaches`. No es la rama.
**Spec original:** 2026-07-26 (§ "Diseño previo" al final — la fontanería que proponía queda
**sustituida**, ver §2; el diseño de producto se mantiene tal cual).
**Origen:** field-test 2026-07-25/26 (el prompt de las 00:35 y el nudge de las 00:50 solo existieron
como notificaciones; abrir la app durante la ventana no mostraba la pregunta por ningún sitio) +
observación de diseño del user 2026-07-26: *"debería pertenecer al mismo flujo de estados"*.
Confirmado 2026-08-21: **badge, no modal** (ver §6).

## Problema

Hoy hay TRES superficies de detección en Home que se arbitran de forma implícita:

1. `DetectionUiState` (DET-READY-001h) — la máquina de estados real del sheet, proyectada a
   `DetectionStory` por `resolveDetectionStory`, cuyo KDoc declara una precedencia fija.
2. La fila `pendingParkNudge` (DET-NUDGE-PERSIST-001) — **arbitrada por un `if` dentro del
   composable** (`HomeDetectionSurface.kt:133`), es decir, fuera de la precedencia que el propio
   `resolveDetectionStory` dice tener. Precedencia partida en dos sitios, y la mitad sin test.
3. El prompt "¿Has aparcado?" — **no existe en la UI de la app**: solo `ConfirmationPhase.Notified`
   dentro del coordinator + la notificación del sistema. Con la ventana abierta, el sheet enseña
   `Driving` ("siguiendo el viaje"), que oculta la verdad accionable: *responde*.

Consecuencia de campo: el prompt ignorado degrada a timeout aunque el usuario abra la app durante
los 15 minutos — la pregunta era invisible in-app.

## Doctrina violada

- **Fallo asimétrico** (CLAUDE.md): "ante la duda se PREGUNTA". Preguntar por un canal que el
  usuario no está mirando no es preguntar; el 25-07 se durmió sobre la pregunta y perdió la plaza.
- **Sistemas, no parches**: dos "asks" (prompt y nudge) con dos mecanismos de supervivencia
  distintos y dos sitios de arbitraje. El nudge ya tiene el suyo resuelto
  [DET-NUDGE-PERSIST-001] — el prompt debe usar **ese mismo sistema**, no inventar otro.

## Señales / datos disponibles

El descubrimiento que cambia el diseño respecto a la spec de julio:
**la ventana de pregunta ya tiene un dueño y es un canal de un solo hueco.**

`PARKING_CONFIRMATION_NOTIFICATION_ID` (2002) admite exactamente tres operaciones, todas a través
del puerto `AppNotificationManager`:

| operación | qué significa | sitios |
|---|---|---|
| `showParkingConfirmation(score, vehicleName)` | **abre** la pregunta | 3 (`NotifyParkingConfirmationUseCase`, implausible-repark, weak-evidence) |
| `showParkingSavedConfirm(...)` | la muta a "aparcado + revertir" → **cierra** | 1 |
| `dismiss(2002)` | **cierra** (respuesta, confirm, timeout, user-stop, revert, `finally` de sesión) | 11 |

No hace falta que el coordinator señale nada: **la ventana está abierta si y solo si la última
operación sobre 2002 fue `showParkingConfirmation`**. Los tests ya modelaban esto sin decirlo —
`FakeAppNotificationManager` guarda un *"ordered log of operations targeting
PARKING_CONFIRMATION_NOTIFICATION_ID"*.

## Diseño

### 1 · La durabilidad va donde ya va la del nudge: en el adaptador

`PendingPromptWindow` (commonMain, hermano de `PendingParkNudge.kt`) + un hueco único en
`AppPreferences`. Se escribe en **UN** sitio y se borra en **UNO**, ambos en
`AppNotificationManagerImpl`:

- `showParkingConfirmation` → `setPendingPromptWindow(...)` (persistir ANTES de postear, dentro de
  `runCatching`: un fallo de persistencia nunca suprime la notificación, igual que el nudge).
- `showParkingSavedConfirm` y `dismiss(2002)` → `clearPendingPromptWindow()`.

Los 15 sitios de la tabla convergen solos. **Cero cambios en el coordinator.**

### 2 · Quitarse la notificación de encima NO es responder

⛔ **Invariante de producto.** Hay dos formas de hacer desaparecer la tarjeta sin contestar, y las
dos **no ejecutan una sola línea de código nuestro**, a propósito:

- **deslizarla** — en este canal NO hay `setDeleteIntent`, deliberadamente;
- **pincharla** — abre la app y `setAutoCancel(true)` la retira.

En ambos casos el hueco sigue escrito y **el badge sigue preguntando**. El caso del tap es el camino
principal del ticket: el usuario aterriza en Home con la pregunta aún pendiente, que es exactamente
lo que el 25-07 no ocurría. Un `setDeleteIntent` futuro que limpiara la ventana parecería
limpieza y sería **borrar la funcionalidad** — hay un guardrail que lo impide
(`PromptWindowGuardrailTest`).

Lo único que cierra una pregunta sin responder es **el plazo**, y no es un número nuevo:

```kotlin
fun isPromptWindowOpen(window: PendingPromptWindow?, nowMs: Long, timeoutMs: Long): Boolean
```
`timeoutMs = config.confirmationResponseTimeoutMs` (15 min). Pasado ese plazo el coordinator ya ha
emitido su propio veredicto (`evaluateUnattendedParkingSave`: o planta el pin con fiabilidad baja, o
degrada al nudge — que entonces coge el relevo en el badge como `PendingAsk`), así que la fila
estaría ofreciendo una respuesta que ya nadie escucha. Edad negativa (reloj hacia atrás) → cerrada.
Misma forma que `shouldShowParkNudgeBanner`.

Esto cubre también la muerte de proceso con la ventana abierta — y ahí la supervivencia es igual de
correcta: la notificación sobrevive al kill y sus botones siguen funcionando.

### 3 · La precedencia vuelve a UN sitio: `resolveDetectionStory`

Se borra el `if` del composable y las dos preguntas entran en la función pura que ya declara ser la
precedencia de la superficie:

```
BlockedCore → AwaitingAnswer → PendingAsk → NoVehicle → Inactive → AwaitingFirstPark
            → Driving → Watching → Hidden
```

- `AwaitingAnswer` gana a `PendingAsk`: una pregunta viva y con plazo manda sobre una vieja.
- Ambas ceden ante `BlockedCore` (sin ubicación la app apenas funciona) — la regla que el `if`
  actual ya aplicaba al nudge, ahora escrita donde se puede testear.
- `DetectionUiState` / `DetectionReadiness` **no se tocan**: siguen respondiendo "¿puede detectar y
  qué está haciendo?". Un "ask" pendiente no es una readiness, es una acción del usuario sobre una
  sesión — misma categoría que el nudge, que tampoco vive ahí.
- Corolario: `isDetectionWorking` no necesita cambio. Con la ventana abierta el estado de fondo es
  `Monitoring`, que ya cuenta como working.

### 4 · Responder desde la app entra por la MISMA puerta que la notificación

`ManualParkingDetection.answerPrompt(parked: Boolean)` (el puerto que ya usan "Voy conduciendo" y
"Parar detección") → `ACTION_PARKING_CONFIRMED` / `ACTION_PARKING_DENIED` →
`CoordinatorDetectionService` → `onUserConfirmedParking()` / `onUserDeniedParking()`.

Son las mismas acciones que dispara `ParkingConfirmationReceiver`, así que:
- la idempotencia ya está resuelta en el intake único [DET-INTAKE-001];
- **ambos hooks ya llaman a `dismiss(2002)`** → responder desde el badge cierra la notificación de
  la bandeja y el hueco persistido, sin una sola línea extra.

⚠️ **Consecuencia asumida:** las dos superficies son **indistinguibles en telemetría** — es el mismo
veredicto de la misma autoridad, y separarlas exigiría romper la puerta única. Para validar en campo
que la vía in-app funciona: responder desde Home **sin tocar la bandeja** y ver que la tarjeta de la
notificación se transforma sola.

**Copy y peso de los botones (ajustado en device, 22-08):** el "sí" repite literal el de la
notificación (`notif_action_yes_parked`). El "no" NO puede: la notificación dice "No, no he
aparcado" y en la fila los dos botones van a mitad de ancho con `maxLines = 1`. El primer intento,
"Sigo conduciendo", **se cortaba ya en español** ("Sigo conducien…") — no hacía falta llegar al
alemán. Queda **"Aún no"**: es la respuesta a la pregunta que hace el título, no una descripción del
estado, así que además de caber en los 9 locales dice exactamente lo que el botón significa.

Y el "no" pierde el tono de marca: `secondaryIsDecline = true` lo pinta en `surfaceContainerHighest`
/ `onSurfaceVariant`. Verde es acción, y declinar no es la acción que ofrecemos — con las dos
píldoras en verde las dos respuestas pesaban casi igual. El flag nombra la SEMÁNTICA, no el color:
la otra fila de dos CTAs (el cold start "Marcar mi sitio" / "Voy conduciendo") ofrece dos acciones
reales, ninguna es rechazo de la otra, y ahí el tono compartido sigue siendo correcto.

**Icono:** `DirectionsCar`, no `LocalParking`. La fila va del coche que estamos siguiendo, y el
glifo del coche es el vocabulario que la app ya usa para "tu coche"; una "P" habría sido el primer
sitio de la app donde el aparcamiento se dibuja como señal de tráfico.

### 5 bis · El sheet se abre SOLO, una vez por pregunta (decisión 22-08 en device)

Con la pregunta abierta, el sheet se anima al ancla `expandedOffsetPx` — la misma a la que lo lleva
un tap y a la que ya se auto-abre `spotListExpanded`, así que la fila (que es el primer item) queda
entera en pantalla con margen, sobre un ancla REAL: después se arrastra y engancha como siempre.

Hacerlo aquí y no dejarlo en manos del eyebrow: la respuesta es lo único que la app necesita y tiene
plazo; obligar a descubrirla y arrastrar sería el mismo *"preguntamos donde no estabas mirando"* que
este ticket viene a terminar.

⛔ **Va cazado por el `shownAtMs` de la pregunta, no por un booleano.** Se dispara una vez **por
pregunta**: bajar el sheet TIENE que quedarse bajado. Un auto-abrir que reintentara dejaría al
usuario atrapado bajo una fila que ya decidió posponer — y posponer es una respuesta legítima a algo
que dura 15 minutos. Con la pregunta pospuesta, el eyebrow (§5) sigue diciéndolo desde el peek.

En modos de pin o con un item seleccionado la geometría capa `expandedOffsetPx` en el peek, así que
el efecto se resuelve como no-op en vez de secuestrar esas superficies.

Verificado en device (Redmi, 22-08): entra → se abre sola con el badge entero; arrastrar abajo →
se queda abajo y no rebota; el peek sigue preguntando.

### 5 · El sheet CERRADO ya tenía su voz: el eyebrow

`DetectionStory` documenta que *"el peek plegado mantiene su propio eyebrow de fase — esa es la voz
del sheet CERRADO"*. Con la ventana abierta, la palabra de fase pasa de "APARCANDO…" a la pregunta.
Un string nuevo dentro del formato que ya existe, sin auto-expandir el sheet ni inventar un cuarto
sitio donde mirar.

`AwaitingAnswer` es además la única story que se renderiza **con un spot seleccionado**: el resto
se ocultan para no mover el índice de scroll de la lista de plazas, pero es la única que tiene plazo.

### 6 · Por qué badge y no modal (decisión 21-08)

- El esqueleto `ActionRow` ya soporta dos CTAs apiladas a ancho completo — cero componente nuevo.
- Una modal sería una CUARTA superficie que arbitrar, justo lo que este ticket viene a fundir; y con
  la respuesta llegando también por la notificación habría que cerrarla desde fuera, con su carrera.
- La duda es NUESTRA, no del usuario: si abre la app para buscar plaza, no se le cobra un peaje
  modal por un fallo de nuestra detección.

## Estado de implementación (21-08)

Código completo, **1.355 tests verdes** (1.322 + 33), `assembleMockDebug` y `compileProdDebug` OK.
Cubierto: veredicto de caducidad (swipe · reloj hacia atrás · muerte de proceso), tabla de
precedencia completa —incluida la regla del nudge, que **nunca había tenido test** por vivir dentro
de un composable—, las dos respuestas sin colapsar en un solo comando, y un guardrail Konsist que
exige que toda función que postee en el canal 2002 mueva también el hueco (con conteo esperado, para
que no pase de vacío) y que nadie fuera de los adaptadores cancele una notificación a mano.

Dev Catalog en sync: 3 variantes nuevas en la galería (`AwaitingAnswer` con y sin coche, `PendingAsk`)
y el preset **"¿Has aparcado? — pregunta abierta durante un viaje"**, que reproduce el caso del 25-07
(viaje vivo + prompt posteado) sobre el Home real.

⏳ Pendiente: verlo en device y el viaje de campo.

## Criterio de éxito

- Con el prompt posteado, abrir la app enseña la pregunta con sus dos botones (sheet abierto) y la
  palabra de fase en el eyebrow (sheet cerrado).
- Responder desde el badge produce exactamente el mismo resultado que responder desde la
  notificación, y la notificación desaparece de la bandeja.
- Pasados los 15 minutos sin responder, la pregunta desaparece sola de la app (aunque el proceso
  haya muerto en medio).
- Deslizar la notificación no deja pregunta zombi en el badge.
- Campo: un prompt ignorado que el usuario resuelve DENTRO de la app, sin haber tocado la bandeja.

## Consumidores auditados

| consumidor | clasificación |
|---|---|
| 3 sitios de `showParkingConfirmation` | **cubiertos por convergencia** (el adaptador es el único punto de posteo) |
| 11 sitios de `dismiss(2002)` | **cubiertos por convergencia** (el adaptador es el único `cancel`) |
| `showParkingSavedConfirm` (morph sobre 2002) | **cerrado** — limpia el hueco explícitamente |
| swipe / tap del usuario sobre la notificación | **cubierto: la pregunta SIGUE viva** (§2) — silenciar la bandeja no responde |
| muerte de proceso con ventana abierta | **cubierto: sigue viva** (§2) — la notificación sobrevive y sus botones funcionan |
| plazo de respuesta agotado | **cerrado** — único cierre sin respuesta; el coordinator ya emitió veredicto (§2) |
| `if (showParkNudge && story != BlockedCore)` en el composable | **cerrado** — borrado, absorbido por `resolveDetectionStory` |
| `HomeState.pendingParkNudge` / `showParkNudge` en slices | **cerrado** — el nudge viaja dentro de la story |
| `IosAppNotificationManagerImpl` | **exento con razón** — iOS fase 0 sin detección viva; misma asimetría que ya tiene `showMarkParkingNudge`, que tampoco persiste ahí |
| `DetectionUiState.isDetectionWorking` | **exento con razón** — el estado de fondo sigue siendo `Monitoring` (§3) |

## Fuera de alcance (explícito)

- **Degradar una ventana huérfana a nudge** (`prompt_orphaned`, §2 de la spec de julio): sería un
  segundo mecanismo para lo que ya cubren el honest-close y el safety net. Si el proceso murió, la
  pregunta es inrespondible y la plaza la recuperan ellos; añadir aquí una tercera fuente de nudge
  contradice "sistemas, no parches". La caducidad basta para que el badge no mienta.
- **Cuenta atrás visible** de la ventana: es mecánica interna, y el copy no la lleva.
- **Tocar la notificación** (copy y acciones quedan igual; solo deja de ser la única superficie).
- El círculo de la zona aproximada en el mapa (DET-HONEST-CLOSE-001).
- La mecánica del timeout/candados (DET-FROZEN-COUNTER-001).

## Riesgos

- Doble respuesta (badge y notificación a la vez) → cubierto por el intake único; se verifica con
  test de idempotencia.
- MIUI/ColorOS pueden congelar el proceso con la ventana abierta → la caducidad la apaga sin
  intervención.

---

## Diseño previo (spec 2026-07-26) — sustituido por §1–§5

Proponía: `promptWindow: StateFlow<PromptWindow?>` en `DetectionRuntime`, señalado desde el
coordinator vía port, abierto en los cuatro carriles que loguean `PROMPT_SHOWN` y cerrado en cinco
sitios distintos, más `AwaitingAnswer`/`PendingAsk` dentro de `DetectionReadiness`.

Se descarta porque exigía tocar cuatro carriles del coordinator y mantener a mano una lista de
cierres, cuando el canal de notificación 2002 ya es un hueco único con dos verbos: el invariante
cabe en dos líneas de un adaptador y no puede desincronizarse de lo que el usuario ve en la bandeja.
