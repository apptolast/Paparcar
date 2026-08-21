# DET-BT-DISCONNECT-WITHOUT-RIDE-001 · Un enganche Bluetooth prueba PRESENCIA, no conducción

**Estado:** ✅ Done — en master (squash 2026-08-21, suite verde: 1333 tests). ⏳ Falta **campo**:
que el Kamiq deje de generar pines cuando lo traiga otra persona, y que un trayecto real del user
siga plantando el suyo. Follow-up abierto:
[DET-BT-BOARDING-ANCHOR-001](det-bt-boarding-anchor-001.md).

## Problema (field 2026-08-21, Oppo + Skoda Kamiq — coche sin conducir en días)

El user no había conducido el Kamiq desde hacía días. A las 14:24 local le apareció un aparcamiento
guardado en Calle Góndola 7 con su coche. Reconstrucción (`files/parkdiag.log` del Oppo +
`diagnostics/fiypNbElGlfFexLMpU9sNaMjRMD3`):

| Hora local | Evento | Dato |
|---|---|---|
| 14:08:40.2 | `BTReceiver ▶ BT CONNECTED` | `50:26:EF:16:1D:C0` → vehicle=`abf6c516` (Kamiq) |
| 14:08:53.9 | `BTReceiver ▶ BT DISCONNECTED` | **13,7 s después** |
| 14:09:24.3 | Debounce de 30 s superado | nadie reconectó |
| 14:09:35.9 | `Got parking fix (36.6083668, -6.2781084) acc=16.9m` | candidato aceptado **por estar quieto** |
| 14:24:35.9 | `Walk-away watch expired after 15 min` | → `ConfirmParking reliability=0.85 path=bt_timeout` |
| 15:23:17 | `SafetyNet: sigues junto al coche (d=9m)` | el sistema sabía que nunca se alejó |
| 15:23:21 | `GEOFENCE_EXIT geof=3b3856f9 d=191m` | la valla del pin arma el Coordinator → `aborted_no_movement` |

**Corroboración fuera de la app.** `dumpsys bluetooth_manager`, `A2dpStateMachine` de
`50:26:EF:16:1D:C0` — **7 registros en total, los 7 de hoy**:

```
14:08:41.520  Disconnected → Connecting
14:08:42.445  Connecting   → CONNECTED    (códec SBC negociado)
14:08:53.023  Connected    → Disconnected
```

11,5 s de A2DP con negociación de códec: la conexión fue **real**. Y la **única** al Kamiq en días
(el otro dispositivo BT del móvil acumula 86 registros desde el 19-08; el Kamiq, 7).

**Causa física:** el coche lo trajo otra persona (el padre del user, con quien comparte el Kamiq).
La firma —engancha limpio y cae de golpe 11 s después— es la de un módulo que se apaga al quitar el
contacto, no la de un teléfono que se aleja.

## El giro: la posición era CORRECTA

El primer diagnóstico dijo "plaza fantasma". Es falso: **el pin cayó bien**. El coche está ahí. Lo
que es falso no es *dónde*, sino *quién* — la app registró una sesión de aparcamiento del user con
un coche que no conducía, al 0.85 y con geocerca, como si la hubiera hecho él.

Y el user señaló lo que el análisis inicial no vio: si él hubiera aparcado el Kamiq ayer en X y su
padre lo hubiera movido hoy a Y, **sin este evento la app seguiría enseñando X con toda confianza**.
El evento no inventó nada: corrigió un dato caducado.

Eso obliga a corregir la aplicación de la doctrina:

> *Mejor falso negativo que falso positivo* se escribió para las **plazas comunitarias**: no
> publicar es gratis, publicar basura es caro. Para *"¿dónde está mi coche?"* **no actualizar NO es
> gratis** — deja al user mirando la posición de ayer. Ahí el asimétrico se invierte: una zona
> aproximada de hace un minuto vale más que un punto exacto de hace un día.

## Qué prueba realmente un enganche BT

El enganche solo ocurre si el coche está a unos metros del teléfono. Prueba exactamente esto:

> **Mi coche estuvo a menos de ~30 m de mí en el instante T.**

No prueba que el user condujera. No prueba que el coche aparcara. Pero **prueba presencia**, y la
presencia refuta por sí sola cualquier posición anterior lejana. Hoy el sistema usa esa señal para
lo que no sirve (fabricar una sesión de aparcamiento) e **ignora** lo que sí prueba.

Queda una sola pregunta abierta, y no la contesta ningún umbral de reloj:

> **¿Se quedó, o solo pasaba?**

Contraejemplo con la MISMA huella que la de hoy: el padre pasa por delante de casa (semáforo, dejar
a alguien), engancha 11 s y sigue 3 km hasta aparcar. Enganche corto, disconnect junto a casa, user
quieto. La app diría "tu coche está en tu portal" y el coche está a 3 km.

**Prueba de permanencia (determinista):** si dos minutos después del desenganche la MAC del coche
**sigue siendo visible en un escaneo**, está aparcado ahí; si desapareció, se fue. Sin GPS, sin
umbrales. Existe `AndroidBluetoothScanner` — pendiente verificar si sirve tal cual o hay que
ampliarlo.

## Doctrina violada

1. *El evento NOMINA, solo el movimiento MEDIDO confirma.* `BluetoothParkingDetector` no comprueba
   en ningún punto que el coche se haya movido: debounce → un fix quieto → 15 min sin caminar 30 m →
   guarda. Es la **única vía de confirmación del sistema sin conducción medida**. Y la cadena está
   invertida: `evaluateCandidateFix` acepta el candidato **precisamente porque estás quieto**.
2. *Todo pin persiste `detectionPath` + `armEvidence`* [DET-PIN-PROVENANCE-001]. El pin salió con
   `armEvidence = null`: la vía BT no estampa cómo se armó, así que el diagnóstico necesitó sacar el
   log por cable en vez de leerse en Firestore.

## Diseño — implementado

**Decisiones del user (21-08):** umbral 90 s · la corrección de posición SÍ se quiere ("quiero que
la app diga dónde lo ha dejado") · la nueva marca SUSTITUYE a la anterior · y, sobre las tres
opciones de borde, **"la opción 3 es la buena": preguntar en vez de registrar por su cuenta.**

Esa elección resultó ser la más sólida de las tres por una razón que no estaba sobre la mesa cuando
se planteó — ver §Por qué la permanencia no habría funcionado.

### Gate de enganche — antes del debounce, no en la rama del timeout

`EvaluateBtParkUseCase.evaluateEngagement(connectedAtMs, disconnectedAtMs)` clasifica el enganche
ANTES de que se muestree nada:

| Veredicto | Cuándo | Qué hace |
|---|---|---|
| `Ride(ms)` | `90 s ≤ duración ≤ 12 h` | sigue el flujo de siempre (debounce → fix → walk-away / timeout-save) |
| `ProximityOnly(ms)` | duración < 90 s | **nudge** y vuelve — sin FGS, sin GPS, sin pin |
| `Unknown` | sin marca · marca futura (cambio de reloj) · marca rancia (> 12 h) | **nudge** y vuelve |

Va antes del debounce y no dentro de la rama del timeout por dos motivos: cubre **las dos** vías BT
(`bt` 0.95 con walk-away y `bt_timeout` 0.85 sin él — con 13 s de enganche y un paseo de 30 m a la
panadería el código viejo también pinchaba, y al 0.95), y ahorra los 15 min de FGS + GPS que hoy se
gastaban para acabar plantando un pin equivocado.

**Techo de 12 h:** un force-stop de OEM hace que la app pierda broadcasts hasta que algo la reabra.
Un CONNECT perdido con un DISCONNECT posterior calcularía un "viaje" de varios días y devolvería la
conducta vieja por la puerta de atrás.

### La nominación reutiliza el nudge que ya existe

`showMarkParkingNudge(source = "bt_no_ride", vehicleId = …)`: persiste `PendingParkNudge` (banner en
Home que sobrevive a dormirse, [DET-NUDGE-PERSIST-001]) y deep-linka al modo manual, donde el user
coloca o acepta la posición. **Sin strings nuevos** — el `source` es solo diagnóstico, no se mapea a
texto de UI (verificado por grep).

Esto resuelve el Eje 1 sin inventar posición: el enganche prueba que el coche está cerca, pero el
fix disponible está en el TELÉFONO, y el teléfono no iba en el coche. Quien sabe dónde quedó es el
user, y responder le cuesta un toque.

### Provenance

Los dos confirms BT estampan `ArmEvidence.BtRide(engagementMs)` → label `bt_ride`. La vía BT no
estampaba **nada** (`armEvidence = null` en el pin de campo), y por eso este diagnóstico necesitó
sacar el log del móvil por cable. Verdicto remoto nuevo: `bt_no_ride_ask`.

## Por qué la permanencia no habría funcionado (y por eso la opción 3 gana)

La idea era escanear a los ~2 min: si la MAC sigue visible, el coche está aparcado ahí; si no, se
fue. Es conceptualmente correcta y **habría fallado en este coche**: la firma del desenganche —
engancha limpio y cae de golpe a los 11,5 s— es la de un **módulo que se apaga con el contacto**. Un
coche apagado no responde a un escaneo, así que "aparcado a 9 m" y "a tres kilómetros" son el mismo
silencio. Además `AndroidBluetoothScanner` hoy solo lee `bondedDevices` (emparejados), que no dice
nada sobre rango: habría habido que construir discovery desde cero para una prueba que este coche no
puede pasar.

## Decisión de publicación

No hace falta código: un enganche corto ya no crea sesión, así que no hay nada que publicar. Si el
user responde al nudge, el pin es suyo y se comporta como cualquier pin manual.

Queda anotado el argumento por si se retoma el registro automático de posición: la salida se dispara
con el `GEOFENCE_EXIT`, que mide el movimiento **del teléfono**. En una sesión que condujo el user
eso vale (salir él ≈ salir el coche); en un registro nacido de presencia ajena no —
**el user se va andando, el coche se queda, y se publicaría una plaza OCUPADA**.

## Señales / datos disponibles (todo esto YA existe, nadie lo lee)

- `BtConnectionStore.lastConnectedAt(vehicleId)` — timestamp del `ACL_CONNECTED`, en
  SharedPreferences desde [DET-BT-IDENTITY-GATE-001]. Sobrevive a los kills de OEM (receiver de
  manifiesto). **El detector no lo consulta.**
- `UserParking.zoneRadiusMeters` / `isApproximate` — el artefacto del Eje 1, ya modelado, ya en
  Room (v14), ya renderizado como círculo.
- `AndroidBluetoothScanner` — candidato para la prueba de permanencia.
- `ConfirmParkingUseCase` tiene el guard de repark implausible sobre `tripMaxSpeedMps`, pero la vía
  BT le pasa `null` → el guard no muerde (ver §Consumidores).

## Criterio de éxito

- [x] Un enganche < 90 s **no** produce sesión de aparcamiento por ninguna de las dos vías BT.
- [x] Ese mismo enganche **sí** pregunta dónde quedó el coche, con banner que sobrevive a dormirse.
- [x] Un aparcamiento en casa tras un trayecto real sigue produciendo su pin `bt_timeout` a 0.85
      ([DET-BT-TIMEOUT-SAVE-001] no regresiona — 1333 tests verdes, ninguna aserción tocada).
- [x] Todo pin BT llega a Firestore con `armEvidence` no nulo.
- [ ] **Campo**: cuando el padre trae el Kamiq, la app pregunta en vez de afirmar que lo aparcó
      el user.
- [ ] **Campo**: un trayecto real del user con el Kamiq sigue plantando su pin.

## Consumidores auditados

| Consumidor | Clasificación |
|---|---|
| `BluetoothDetectionService:157` — único caller de `detectParking` | **cerrado**: firma ampliada, lee `lastConnectedAt` y lo pasa. El Context se queda fuera del núcleo de decisión. |
| `evaluateCandidateFix` / `evaluateWalkAway` (las dos vías BT) | **cerrado por convergencia**: el gate va antes del debounce, así que ninguna de las dos se alcanza sin `Ride`. |
| `ConfirmParkingUseCase:182` — guard de repark vía `ArmEvidence.isVerifiedLabel` | **exento con razón**: `bt_ride` NO es label verificado, pero el guard exige `tripMaxSpeedMps != null` y la vía BT pasa `null`. Conducta idéntica antes y después; el pin pasa de `armEvidence=null` a `"bt_ride"` sin cruzar ninguna rama nueva. |
| `ParkingSafetyNetWorker:225` — otro lector de `lastConnectedAt` | **exento con razón**: lee la marca como prueba de identidad [DET-BT-IDENTITY-GATE-001]; este ticket no la escribe ni la borra, solo la lee también. |
| `AndroidBluetoothScanner:49` — `BtConnectionStore.connectedVehicleIds` | **exento con razón**: estado vivo de conexión para el resolver de estrategia, ortogonal a la duración. |
| Otras 3 fuentes de `showMarkParkingNudge` (coordinator, safety net, honest close) | **exento con razón**: `source` es solo diagnóstico, no se mapea a texto de UI (verificado por grep) → sin colisión y sin strings nuevos. |
| `when` exhaustivos sobre `ArmEvidence` | **cerrado**: `persistLabel` es el único; ampliado. Compila prod y mock. |

## Hallazgo colateral (a adjudicar, NO parte de este ticket)

`ProcessConfirmedDepartureUseCase` decide publicar con
`publishSpot && session.privateZoneId == null` — **no mira `zoneRadiusMeters`**. Es decir: hoy una
zona aproximada (`closed_approximate_pin`, honest-close) publica plaza a la comunidad con
coordenadas que la propia app ha declarado imprecisas. El user tiene 3 pines
`closed_approximate_pin` en el histórico reciente. Falta determinar si es intencional o un bug
latente; si es bug, es de la familia "Release sin gate `privateZoneId`" ya anotada en
`docs/detection/11-bugs-encontrados.md`.

## Follow-ups deliberadamente fuera de alcance

1. **Distinguir "aparcó a mi lado" de "pasó por mi lado"** en enganches POR ENCIMA de los 90 s. Las
   dos vías candidatas son el ancla de embarque (estampar la posición en el `ACL_CONNECTED` y exigir
   desplazamiento del coche entre las dos puntas) y la prueba de permanencia (descartada para este
   coche, ver arriba). El ancla es la que sigue viva.
2. **Sembrar el estado de conexión al arrancar el proceso** (preguntar a Android qué emparejados
   están conectados) para que `Unknown` sea rareza en vez de caso corriente tras un force-stop.

## Notas de campo relacionadas

- Cascada conocida: el pin registra geocerca (105 m) y su `GEOFENCE_EXIT` arma el Coordinator al
  salir de casa a pie. Mismo patrón que el field 19-08 de madrugada
  [DET-UNWITNESSED-DISPLACEMENT-001].
- El "segundo FP" que el user recordaba de ayer **no existe**: el único pin del Kamiq del 20-08 es
  `2cc9cc76`, `detectionPath=manual`, fiabilidad 1.0, puesto por él a las 00:11 local. Verificado
  contra `parkdiag.log` (cubre desde el 18-08) y contra `parkingHistory`.
