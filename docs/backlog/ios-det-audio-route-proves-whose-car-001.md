# IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001 · En iOS el BT es un SENSOR, no un trigger — y como sensor puede probar de quién es el coche

**Estado:** 🔵 Diseño VALIDADO por el user (22-09): **onboarding B (oportunista) como vía
principal + A (explícita) como respaldo**, sensor **solo A2DP** en v1. Sigue **SIN implementar**
hasta que el user dé el go. Nace como follow-up deliberado de
[IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001] (issue #74 / PR #122, master `a3dd5938e`).

## La frase que gobierna el ticket

> **En Android el BT es un TRIGGER fuerte; en iOS es un SENSOR.**

Esa asimetría no es un matiz: cambia quién despierta, quién arma, qué se puede prometer y hasta
cómo se vincula el coche. Todo lo demás de este doc es desplegar esa frase.

## Android vs iOS — la tabla que hay que entender antes de diseñar nada

| | **Android (lo que ya tenemos)** | **iOS (lo único posible)** |
|---|---|---|
| **Qué es el BT** | Trigger con autoridad: el OS entrega `ACL_CONNECTED/DISCONNECTED` a un receiver de manifiesto | Sensor de solo lectura: `AVAudioSession.currentRoute` + `routeChangeNotification` |
| **¿Despierta la app?** | **SÍ**, incluso con proceso muerto (receiver exento de la restricción FGS-desde-fondo) | **NUNCA.** Suspendida no llega nada y no se reentrega; hay que estar ya vivo (foreground o background con tracking activo) |
| **¿Qué autoridad tiene?** | Arma el carril BT entero: disconnect → fix → ≥30 m → **confirma** (nivel «Automático») | Cero autoridad: solo **evidencia** dentro del carril Coordinator ya despierto. Jamás confirma, jamás arma solo |
| **Identidad** | La MAC viene EN el evento; el phone lista sus emparejados (`bondedDevices`) | No hay lista de emparejados. La identidad solo se **OBSERVA**: el puerto A2DP aparece en la ruta de audio **mientras el coche está conectado**, con UID `MAC-tacl` (no documentado; fallback `portName`) |
| **Vinculación (onboarding)** | En el sofá: abrir la app, elegir el coche de la lista de emparejados | **Hay que estar en el coche** (BT del coche conectado al iPhone) **con el proceso vivo** — no hay otra forma de ver el puerto |
| **Permiso** | `BLUETOOTH_CONNECT` runtime | **Ninguno.** Leer la ruta de audio no pide permiso (la lectura por CoreBluetooth sí lo pedía — y se retiró en #74 porque no desbloqueaba nada) |
| **Fiabilidad de lectura** | Evento fiable del OS | Oportunista: desde iOS 16 `currentRoute` puede salir **vacío en background** → «coincide» arma fuerte, «no aparece» **no prueba nada** (asimétrica por diseño) |
| **Promesa al usuario** | Nivel **Automático** | El tier sigue siendo **Asistido**; esto lo hace mejor, no lo renombra |

## Qué compra (si se implementa)

1. **Pertenencia del embarque**: al armar el Coordinator (AR IN_VEHICLE / geofence exit) con el
   proceso ya vivo, leer la ruta: puerto A2DP cuyo UID coincide con `Vehicle.bluetoothDeviceId`
   ⇒ «voy en MI coche» — el asiento iOS de lo que en Android es
   DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS (bus/taxi no arman; el propio coche arma con más
   confianza). Valor nuevo de `armEvidence`, decidido en use case puro de commonMain.
2. **Ancla del pin**: si el disconnect (`.oldDeviceUnavailable`) ocurre mientras aún seguimos el
   viaje, tenemos el instante exacto del apagado — evidencia de anclaje, no confirmación.
3. **Coche compartido**: una MAC observada en iOS produce el mismo
   `sharedCarBtToken(carId, MAC)` que Android (hallazgo de #74: hoy el token se escribe y no se
   compara — si algún día se cablea, iOS ya cuadraría).

## Qué NO compra nunca (para no volver a discutirlo)

- Ni despertar la app, ni carril determinista, ni nivel «Automático» en iOS.
- Ni una señal dentro del **scoring** del Coordinator: la doctrina de carriles queda intacta —
  esto entra por la escalera de ARMADO (pertenencia), como en Android.

## Qué BT ve el sensor — el alcance decidido (22-09)

El sensor no ve «el Bluetooth»: ve **la ruta de audio**, y cada perfil aparece distinto. En
Android el ACL es de nivel de enlace y dispara con CUALQUIER perfil; en iOS cada perfil tiene su
precio:

| Perfil del coche | ¿Visible? | Precio | Decisión v1 |
|---|---|---|---|
| **A2DP** (música por BT) | ✅ en `currentRoute` mientras esté conectado, suene o no | Ninguno: sin permiso | **ES el sensor** — UID `MAC-tacl` |
| **HFP solo** (coches viejos, solo llamadas) | ⚠️ solo con sesión `playAndRecord` + `allowBluetooth` | **Permiso de MICRÓFONO** + justificación en App Store | ⛔ Fuera: pagar micro por coches viejos rompe el cero-fricción. Esos coches quedan en Coordinator puro, como hoy |
| **CarPlay** (cable o inalámbrico) | ✅ como `.carAudio`, pero **TAPA al A2DP** (el audio va por CarPlay) | Sin MAC — identidad = nombre del coche | 🧊 Anotado como evidencia secundaria futura («estoy en UN coche»), sin identidad comparable |
| Cable USB/AUX | ❌ no es BT | — | Fuera |

⚠️ Consecuencia honesta: un usuario que SIEMPRE usa CarPlay no expone el A2DP → su coche no es
vinculable por MAC en iOS. La feature degrada a Coordinator puro sin mentirle (y el día CarPlay,
`.carAudio` podría ser su evidencia, con su propio diseño).

⚠️ **Confusor conocido: unos auriculares también son A2DP.** El guard es doble: (1) el candidato
de la vía B es el puerto cuya presencia ENMARCA el viaje (aparece al arrancar, desaparece al
aparcar — unos auriculares sobreviven a la caminata); (2) la vinculación SIEMPRE la confirma el
usuario (fallo asimétrico: ante la duda se pregunta, nunca se vincula solo).

## El onboarding — DECIDIDO (user, 22-09): B como vía principal + A como respaldo

Ambas escriben el mismo `Vehicle.bluetoothDeviceId`; ninguna es excluyente:

- **(b) Oportunista, la principal**: durante un viaje que el Coordinator ya siguió, si la ruta
  mostró exactamente UN puerto A2DP que enmarca el viaje, capturarlo y preguntar después
  (nudge): *«¿Este viaje fue en [SKODA BT]? ¿Es tu coche?»*. Cero fricción. Exige diseño de
  copy (⛔ un «sí» no encoge una duda entrada; ⛔ sin mecánica interna en el copy).
- **(a) Explícita, el respaldo**: fila «Vincular el coche» en la página del vehículo (solo iOS):
  *siéntate en el coche con el motor encendido y la app abierta* → la app lee `currentRoute`,
  enseña el `portName` («SKODA BT»), el user confirma. Tapa el hueco de la B: una cuenta
  solo-iOS no tiene nada hasta su primer viaje seguido con puerto visible.
- Si el pairing **ya vino sincronizado desde Android** (`bluetoothDeviceId != null`), no hay
  onboarding: iOS solo observa coincidencias.

## Fragilidad asumida (escrita para el futuro)

- El UID `MAC-tacl` es **comportamiento no documentado**: Apple ya aleatorizó los identifiers de
  CoreBluetooth y puede cerrar este. El diseño debe degradar a `portName` (mismo dato que Android
  muestra al emparejar) sin romper nada — y si un día no hay ni eso, la feature se apaga sola
  sin mentir (el gate es la evidencia, no la promesa).
- Todo uso es **asimétrico**: la ausencia del puerto jamás desarma, jamás desvincula, jamás
  resta confianza (puede ser el agujero de background de iOS 16+, no la ausencia del coche).

## Criterio de éxito (cuando se implemente)

- Valor nuevo de `armEvidence` citable en diagnóstico, decidido en commonMain, con tests de
  fakes; `detectionPath` sin cambios (esto arma, no confirma).
- Onboarding elegido y validado; strings en los 9 locales; Dev Catalog en sync si hay pantalla o
  estado nuevo.
- La promesa visible no cambia: iOS sigue diciendo «Asistido».

## Referencias

- Análisis de las 4 vías con fuentes Apple: `docs/backlog/ios-a-bt-lane-needs-a-real-signal-001.md`
- Doctrina y §1.2 (alcance de plataforma del carril BT): `docs/detection/PARKING-DETECTION.md`
- La mitad Android pendiente del mismo concepto: DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS
