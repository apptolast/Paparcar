# ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001 · el servidor la anuncia y el cliente la veta

**Estado:** ✅ Done (código, tests y docs) · ⏳ sin ver en device · rama `bugfix/ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001-align-the-lanes`
· worktree `../Paparcar-provisional-alert`

## Problema

Los dos carriles del aviso de plaza **contestan distinto a la misma pregunta**, hoy, en master.

| | PROVISIONAL |
|---|---|
| Servidor (`functions/src/fanout.ts:105`) | **se anuncia**, con `isProvisional: true` para que el copy pueda decir que no está confirmada |
| Cliente (`domain/spot/isSpotWorthAnAlert`) | **se veta** |

Y la razón del veto del cliente es exactamente lo que el servidor nombra como aquello que
PROVISIONAL existe para batir:

- Cliente: *«A PROVISIONAL spot lives 12 minutes and the background look runs every 15, so by the time
  that path could tell anyone, the spot is gone.»*
- Servidor: *«PROVISIONAL is announced — it is the freshest thing on the map and the one the old
  **fifteen-minute poll** could never reach.»*

El poll de quince minutos **no existe** desde que el fanout sustituyó a la mirada periódica
([ZONE-THERE-IS-NO-WORKER-AND-FIVE-KDOCS-SAY-THERE-IS-001]). O sea que el veto del cliente se apoya
entero en una cadencia muerta.

**Consecuencia medible hoy:**
- Plaza provisional en tu zona, **app cerrada** → llega el push.
- La misma plaza **mientras miras el mapa** → nada: el stream local la tira.
- Y en el carril nuevo de cerca-de-ti, una provisional a 200 m se descarta en silencio.

Gana el canal peor, que es justo al revés de lo que
[ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] vino a arreglar.

## Doctrina violada

**Sistemas, no parches**, en su forma de invariante duplicado: «¿de qué plazas se avisa?» se decide en
DOS sitios (cliente y servidor) que no pueden compartir código, y nada los compara. El comentario del
servidor lo dice creyéndolo: *«Mismo veredicto que `ZoneSpotAlertPolicy` toma en el cliente: son las
dos mitades de la misma promesa y no pueden discrepar»* — y para RELEASING es verdad, para PROVISIONAL
no. Un comentario que afirma una paridad que no se comprueba.

## Lo que NO cambia: RELEASING

Se queda vetada, y **por una razón, no por dos**. El servidor la deja escrita:

> *«Un embarque NO se anuncia… la plaza vive CUATRO minutos, y **sobre todo el coche sigue dentro** — un
> push diciendo "hay una plaza en tu zona" sería falso en el instante en que suena. Lo que sí se anuncia
> es su PROMOCIÓN.»*

La mitad viva es *el coche sigue dentro*; la noticia real ya va en la **transición** cuando el embarque
se convierte en salida (`verdictFor`, [SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001]).

⛔ **Y la otra mitad hay que corregirla, no repetirla**: los cuatro minutos son el **SUELO, no el plan**
— lo dice `SpotTtlPolicy.RELEASING_SPOT_TTL_MS`: *«Like PROVISIONAL_SPOT_TTL_MS this is the FLOOR, not
the plan — the explicit withdrawal can fail every way a background job fails»*. El camino normal es que
los sensores resuelvan: conducción medida → **promoción**; embarque refutado → `withdrawSpotRelease()`
(dos call sites en `CoordinatorDetectionService`). Los 4 min sólo cubren que no pase ninguna de las dos
— más un techo semántico: pasados cuatro minutos, un embarque ya no es creíblemente una salida en curso
(*«somebody fetching a coat, taking a call, or a head unit that powered up on its own»*). Citar «vive
cuatro minutos» como si fuera una vida esperada es lo que hay que dejar de hacer en los dos lados.

## Diseño, tal como quedó

1. **`isSpotWorthAnAlert` deja de vetar `PROVISIONAL`.** ⛔ Y NO pasa a «devolver por qué» como decía
   el diseño de este doc antes de escribirlo: eso era sobre-ingeniería. El llamante ya tiene el `Spot`,
   así que la duda se lee de él y la función sigue contestando una sola cosa —¿podemos interrumpir?—
   que es lo que la hace compartible por los dos carriles.
2. **`SpotStatus.isUnconfirmedOffer`** — el predicado se iza junto a su hermano `isAvailable`. Era un
   `val` local dentro de `SpotPeek`, y el aviso era su segundo consumidor: dos copias de «¿hay que decir
   que no está confirmada?» es cómo una superficie acaba matizando y la otra prometiendo, sobre la
   misma plaza y en el mismo minuto.
3. **`SpotAlertCue.unconfirmed`** — la duda viaja en el cue, no se deduce en el call site, porque **es
   el único aviso que esa plaza va a tener**: el id es el mismo en toda la publicación, así que la
   memoria de «ya contada» hace que anunciar en PROVISIONAL consuma el aviso y la promoción a CONFIRMED
   ya no vuelva a hablar — que es lo que el servidor dice que quiere.
4. **El call site de la notificación pasa el flag.** `showZoneSpotAlert` ya tenía `isProvisional`, con
   default `false`, y el carril local lo omitía: inofensivo mientras vetaba las deducidas, y una promesa
   falsa en el instante en que dejó de vetarlas.
5. **Cuatro frases, no una plantilla** (`spotAlertText` en `App.kt`): zona/cerca de ti × confirmada/sin
   confirmar. En ocho de los nueve idiomas la duda **cambia el verbo**, no añade una palabra —
   *«acaba de quedar libre»* → *«puede haber quedado libre»*, *«właśnie zwolniło się»* → *«mogło
   zwolnić się»*—, así que una plantilla obligaría a cada traducción a caber en la gramática inglesa.

### ⛔ El guard encontró un bug MÍO en su primera ejecución

La primera versión puso una rama `spot.status == PROVISIONAL -> true` **antes** del veto de plaza
propia. Un `-> true` explícito en una cadena de guardas **cortocircuita todo lo que va detrás**, así
que una plaza deducida **tuya** se te habría anunciado — exactamente el invariante que el user recordó
al abrir el carril de cerca de ti — y una caducada también.

`AlertStatusParityGuardrailTest` lo cazó al primer intento, aunque por el motivo equivocado: su fixture
pasaba el mismo uid como `reportedBy` y como `ownUserId`, así que toda plaza parecía propia. El fixture
estaba mal Y el código estaba mal; el fallo destapó lo segundo.

**La forma correcta de ese `when` es: sólo vetos, y el `else` afirma.** Y la advertencia de que «un
estado nuevo se vuelve alertable con sólo no decir nada» deja de ser un comentario y pasa a tener dos
testigos: el guard de paridad y `SpotAlertEligibilityTest`, que enumera los estados con un `when`
**exhaustivo** — añadir un `SpotStatus` ya no compila hasta que alguien decida qué hace el aviso con él.

## Consumidores auditados

| Decisor / superficie | Estado |
|---|---|
| `functions/src/fanout.ts` | ✅ **no se toca**: era el lado que ya acertaba. Ahora lo vigila `AlertStatusParityGuardrailTest` |
| `isAlertableInZone` (stream de zona) | ✅ hereda el cambio por `isSpotWorthAnAlert`; su test de estados se parte en dos |
| `nearbySpotsWorthTelling` (cerca de ti) | ✅ ídem |
| `PushMessageHandler` | ✅ exento: ya pasaba `isProvisional` desde el payload. Era el único que lo hacía bien |
| `showZoneSpotAlert` (puerto) | ✅ ya tenía el parámetro y el KDoc correcto; lo que faltaba era el call site local |
| `SpotPeek` | ✅ pasa a usar el predicado izado en vez de su `val` local |
| `FakeAppNotificationManager` | ✅ **tiraba el flag**, así que ningún test podía ver si la frase se matizaba. Un fake que descarta un parámetro es un parámetro sin testigo |
| `AppViewModelTest` | ✅ el stream de zona se creaba inline en el `setUp` y era inalcanzable: sus dos entradas pasan a campos |
| `home_spot_unconfirmed_badge` | ⚠️ **importado y sin renderizar** en `HomeSpotRows.kt`, y es su única referencia en todo el repo — el KDoc de `PROVISIONAL` promete *«Shown as unconfirmed»* y la FILA no lo enseña (el peek sí, con `home_spot_unconfirmed_note`). No se toca aquí: decidir si la fila lleva badge es producto, y borrar la key sería borrar la intención |

## Verificación

- `AlertStatusParityGuardrailTest` falsificado devolviendo el veto: caen **él y** el enumerador
  exhaustivo. Y el de la notificación, quitando el `isProvisional` del call site: cae solo.
- `assembleMockDebug` + `compileProdDebugKotlin` OK. Dos variantes nuevas en la galería, **al lado de
  sus confirmadas**, para poder leer las cuatro seguidas.
- 9 locales × 2 keys, sin `'` ni `%%`, con `%1$s` sólo donde hay zona que nombrar.
- Suite completa: ver el resumen del commit. ⚠️ Master avanzó a `33aabb9ef` mientras esto se
  escribía (trabajo del user sobre el ciclo de vida de RELEASING): **toca rebasar y repasar la suite
  antes de mergear**. Comprobado que no se pisan — aquel commit toca `SpotReleaseLifecycle`,
  `Nominate`/`WithdrawSpotRelease`, y esto toca la elegibilidad y el reparto.

## iOS

Por construcción: elegibilidad, predicado, cue y elección de frase están en `commonMain`, y el banner
es el mismo Compose Multiplatform. Lo único de plataforma es la notificación del sistema, cuyo puerto
ya llevaba el flag.
