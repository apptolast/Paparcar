# DET-A-SESSION-ROLLUP-MUST-USE-THE-NUMBERS-THE-VERDICT-USED-001 · el resumen de sesión anunciaba una cifra que ninguna decisión usó

**Estado:** ✅ Done (2026-09-03) · sale del sub-hallazgo «el resumen de sesión miente» de
[DET-COARSE-FIX-DRIVE-PROOF-001](det-coarse-fix-drive-proof-001.md), que sigue abierto por su mitad
grande (admitir rachas de fixes degradados).

## Problema

Field 16-08 (Samsung SM-A536B, sesión `1786873042480`): la cabecera del diagnóstico decía

```
vmax 80km/h · drive 44/602fix     …junto a…     outcome=aborted_unattended_no_drive
```

Las dos cosas eran ciertas y el par era ilegible. El fix de 80 km/h traía **180 m de accuracy**, así
que `credibleSpeedFix` fue `false` y ninguna decisión lo vio nunca. El resumen publicaba el número
**crudo**; la decisión leía el **admisible**. Reconciliarlos a mano costó un diagnóstico entero — y
el resumen es lo primero que se lee (lo dice la skill `field-test`).

No era cosmético y tampoco era nuevo: el mismo `HumanPoweredRideTest` ya lo tenía escrito —
*«the summary said vmax 40 km/h, but its best CREDIBLE fix was 21,3 — **the peak is a rumour**»*.

## Doctrina violada

*Un diagnóstico se lee para explicar un fallo.* Una cabecera que contradice su propio veredicto no
es un dato impreciso: es una pista falsa en el sitio donde más caro sale seguirla.

## Lo que ya estaba hecho y lo que faltaba

`DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001` ya había cerrado **la mitad del conteo**:
`drivingFixes` (crudo) convive con `credibleDrivingFixes` (con puerta), ambos se publican y el
resumen imprime `drive 44/602fix (cred 7)`. Lo que quedó sin cerrar es **el pico**, que es
justamente el número que un humano cita.

## Diseño

- `SessionRollup` sale de ser una clase privada del `FirestoreDetectionEventLogger` (con su
  aritmética repartida entre dos métodos) y pasa a ser una pieza **pura y testeable** del mismo
  paquete. Firestore no hace falta para responder *«¿el resumen concuerda con el veredicto?»*; solo
  hacen falta estas cifras — por eso la pregunta no se había podido hacer nunca.
- Nace `credibleMaxSpeedKmh`: el pico con la MISMA puerta de accuracy que aplica el detector.
- **Se guardan e imprimen los dos**, igual que el conteo: el pico crudo describe lo que el receptor
  AFIRMÓ (y uno desbocado es en sí mismo un síntoma que se quiere ver), el creíble es al que se
  puede sujetar cualquier veredicto. Quitar cualquiera de los dos cambia un punto ciego por otro.
- ⚠️ Un fix **sin accuracy** cuenta como no creíble: un fix que no sabe decir cuánto puede
  equivocarse es exactamente lo que la puerta existe para frenar.

Formato nuevo: `vmax 80km/h (cred 49) · drive 44/602fix (cred 7)`. Campo nuevo en el DTO de sesión,
`credibleMaxSpeedKmh`, junto al que ya había.

## Criterio de éxito

- Ningún resumen vuelve a mostrar un `vmax` que la decisión no usó **sin el admisible al lado** ✅
- La aritmética del resumen es testeable sin Firestore ✅ (7 tests; antes, cero)

## Medido

Suite **2.161/0** (+7). **Falsación**: quitando la puerta de accuracy del pico creíble caen 3/7.
