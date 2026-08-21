# DET-BT-BOARDING-ANCHOR-001 · Distinguir "aparcó a mi lado" de "pasó por mi lado"

**Estado:** 🔵 Abierto, sin código · follow-up deliberado de
[DET-BT-DISCONNECT-WITHOUT-RIDE-001](det-bt-disconnect-without-ride-001.md)

## El borde que queda abierto

DET-BT-DISCONNECT-WITHOUT-RIDE-001 cerró los enganches Bluetooth **cortos** (< 90 s): ya no
confirman, preguntan. Pero por encima de ese suelo el sistema sigue sin poder separar dos huellas
idénticas:

- el coche **aparca al lado** del teléfono y se apaga → hay plaza, el pin es correcto;
- el coche **pasa cerca** del teléfono y sigue conduciendo → no hay plaza, y el pin marcaría dónde
  está el TELÉFONO, no el coche.

En ambos casos el fix se muestrea junto al teléfono, y el teléfono no iba dentro. Con un enganche
largo (p. ej. alguien que conduce el coche compartido durante 20 min y para un momento delante de
casa) la huella entra en la vía de confirmación tal cual.

## Por qué no se resolvió en el ticket padre

Se evaluaron dos vías y se descartó una **con datos**:

- **Prueba de permanencia** (¿sigue la MAC visible a los ~2 min?) — DESCARTADA para este parque
  móvil. La firma del Kamiq (engancha limpio, cae de golpe a los 11,5 s) es la de un módulo que se
  apaga con el contacto: un coche apagado no responde a un escaneo, así que "aparcado a 9 m" y "a
  tres kilómetros" producen el mismo silencio. Además `AndroidBluetoothScanner` hoy solo lee
  `bondedDevices` (emparejados), que no dice nada sobre rango — habría que construir discovery desde
  cero para una prueba que este coche no puede pasar.
- **Ancla de embarque** — VIVA, es la vía que queda.

## Diseño propuesto (ancla de embarque)

Estampar en el `ACL_CONNECTED`, junto al timestamp que `BtConnectionStore` ya guarda, la POSICIÓN
del teléfono en ese instante. En el `ACL_DISCONNECTED` el detector tiene entonces las dos puntas del
enganche, y su distancia es **desplazamiento del coche**: conducción medida de verdad, sin depender
de ningún umbral de reloj.

- Enganche largo + desplazamiento ⇒ hubo trayecto: confirma como hoy.
- Enganche largo + coche que no se movió ⇒ el módulo estuvo despierto sin ir a ningún sitio:
  nomina, no confirma.
- El suelo de 90 s se queda como primera línea barata (no necesita GPS).

Coste a estudiar: obtener una posición en un `BroadcastReceiver` sin encender el GPS
(`lastKnownLocation` puede bastar y es gratis), y qué hacer cuando esa posición no existe o es
rancia — en cuyo caso el veredicto correcto vuelve a ser preguntar.

## Segundo follow-up (menor, mismo ámbito)

**Sembrar el estado de conexión al arrancar el proceso**: preguntar a Android qué dispositivos
emparejados están conectados en ese momento, para que el veredicto `Unknown` del ticket padre (sin
marca de CONNECT, típicamente tras un force-stop de OEM) sea una rareza en vez de un caso corriente.
Hoy `Unknown` degrada a nudge, que es correcto pero molesta si se repite.

## Precondición

⛔ No empezar hasta que el ticket padre esté validado en campo: hace falta saber si el suelo de 90 s
ya elimina los casos reales antes de gastar trabajo en el borde.
