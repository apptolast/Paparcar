# DET-A-BT-CONNECT-IS-A-DEPARTURE-001 · el carril Bluetooth no tiene puerta de salida

**Estado:** 📋 Abierto · sin rama · descubierto el 14-09-2026 durante
[DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001](det-an-exit-is-dated-by-the-fix-that-crossed-it-001.md)

## Problema

La estrategia Bluetooth es la determinista, la que llamamos "automática", y **no publica ni una
plaza**. Lo dice su propio detector: `BluetoothParkingDetector:227` — *"nothing community-facing is
published at confirm time (spots publish at departure, with their own guards)"*. Y la salida no la
recoge nadie en ese carril:

`BluetoothConnectionReceiver:155-192`, en `ACTION_ACL_CONNECTED`, hace tres cosas —
`BtConnectionStore.recordConnected` (instante + fix de origen), `markConnected` y un `startService`.
`BluetoothDetectionService.handleConnected` solo cancela el job de desconexión pendiente y se apaga.
No cierra sesión, no quita geocerca, no publica, **no data nada**.

Consecuencia: toda salida de un usuario BT se procesa por el carril del Coordinator (geocerca +
safety net). Un usuario con detección "automática" depende para sus plazas exactamente del carril
probabilístico que la suya debería haber sustituido.

## Doctrina

No la viola: *el evento NOMINA, solo el movimiento MEDIDO confirma*. Un CONNECT no prueba por sí solo
que el coche se haya movido, y publicar con él sin más sería un falso positivo (te metes en el coche,
enciendes la radio y te bajas). Lo que falta es la escalera — **el CONNECT arma, y el movimiento
medido confirma**, igual que hace el DISCONNECT en la otra dirección.

Y hay una asimetría difícil de justificar: el DISCONNECT del mismo MAC sí monta una escalera entera
(debounce, caza de fix, prueba de viaje medida, vigilancia de la caminata). El CONNECT, que es el
gemelo exacto y el instante en que la plaza se está liberando de verdad, no monta ninguna.

## Señales disponibles

- **`BtConnectionStore.recordConnected`** ya persiste `System.currentTimeMillis()` y el fix cacheado
  del momento del enganche. Es un instante **medido** de "el usuario se metió en ESTE coche", y es
  justo lo que `departedAtMs` pide desde
  [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001]. Hoy solo lo leen el gate de identidad del
  safety net ([DET-BT-IDENTITY-GATE-001]) y `parkedSessionIsContradictedByBtConnect`.
- `EvaluateBtParkUseCase.evaluateRideDisplacement` ya sabe medir si el coche fue a algún sitio entre
  connect y disconnect — la mitad de confirmación ya existe, escrita y testeada.

## Diseño (esbozo, sin cerrar)

El CONNECT del coche emparejado despacha la salida de la sesión activa de ESE vehículo por la
maquinaria que ya existe (`DepartureDetectionWorker` → `RunDepartureCheckUseCase`), con
`departedAtMs` = el instante del CONNECT. Lo que decide si publica sigue siendo el mismo gate de
frescura y la misma escalera de prueba; lo único que aporta este ticket es **que la señal llegue**,
y que llegue con su hora medida en vez de con la del despertar que la descubra dos horas después.

⚠️ No mezclar carriles: esto no mete señales BT en el scoring del Coordinator. Es el carril BT
usando la puerta común de salida, igual que hoy usa `ConfirmParkingUseCase` como puerta común de
entrada.

## Datos de campo

Fernando (usuario real, Skoda emparejado): **6 de 43** pins en 8 días son `nudge_bt_no_ride` — el
carril BT ni siquiera llega a colocar el pin en esos casos (el engagement no alcanza
`btMinRideDurationMs`, o no había fix de origen: *"no cached fix at connect — this ride will not be
measurable"*). Ninguno de sus pins tiene `detectionPath = "bt"`.

## Relacionado

- `DET-A-BT-DRIVE-LEAVES-NO-TRACE` y `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION`, ambos sin decidir.
- ⛔ `"bt"` está excluido **a propósito** de `PIN_TO_PIN_ELIGIBLE_PATHS` — comprobar por qué antes de
  tocar nada aquí.
