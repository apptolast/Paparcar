# DET-THE-ANCHOR-MUST-PREFER-THE-LONGER-REST-001 · el ancla se quedó con una parada de 4 segundos teniendo una de dos minutos al lado

**Estado:** 🔵 Abierto · sin rama · detectado el 06-10-2026 al diagnosticar
[DET-A-MANEUVERING-CAR-IS-NOT-AN-EGRESS-001], del que se separó por ser otro invariante.

## Problema

Campo 05-10-2026, Oppo, sesión `1791194402036` (viaje Granada → El Puerto). Final del trayecto, con
el coche buscando aparcamiento:

```
15:04:39 → 15:04:43   36.593800,-6.239878   acc 1,57 m   PARADO 4 s     ← el pin cayó AQUÍ
15:04:45 → 15:04:51   rodando a 1,5-3,8 m/s
15:04:53 → 15:05:25   36.593603,-6.239790   acc 1,70 m   PARADO 32 s
15:05:27 → 15:05:29   rodando
15:05:31 → 15:07:29   36.593522,-6.239660   acc 1,50 m   PARADO 118 s   ← el coche descansó AQUÍ
```

El pin se plantó en la parada de **4 segundos**, a ~37 m de la parada de **118 segundos** donde el
coche se quedó de verdad — y con peor precisión (1,57 m frente a 1,50 m) que la que descartó.

⚠️ El falso positivo que lo destapó ya está cerrado por otra vía (la sesión ya no confirma, porque
las fijaciones de maniobra dejaron de contar como caminata). **Esto sigue abierto igualmente**: la
próxima vez que el carril confirme legítimamente tras varias paradas, puede volver a elegir la
corta. El defecto no es el FP, es el criterio de selección.

## Doctrina violada

Ninguna de las tres frases rectoras, directamente — por eso es un ticket aparte y no parte del otro.
Lo que falla es la definición operativa de *dónde está el coche*: el ancla responde «dónde empezó la
parada que el sistema estaba mirando cuando se cumplieron las condiciones», no «dónde descansó el
coche».

## Señales / datos disponibles

- `AnchorTrust.witnessedRestFix` ya existe y elige **por precisión**
  (`stopWindowFixes.minByOrNull { it.accuracy }`), nacido de
  [DET-NO-CLOCK-PLANTS-A-PIN-001] para las anclas entradas por un hueco de GPS.
- `AnchorTrust.stopStartedAt` y `stopEvidenceSince` ya datan cada parada, así que su DURACIÓN es
  computable.
- Hoy no hay ningún criterio que compare paradas CANDIDATAS entre sí por cuánto duraron.

## Diseño

Sin decidir. La línea obvia: cuando el ancla se congela por reposo, preferir la parada más larga que
la ventana presenció, no la primera que cumplió condiciones — con la precisión como desempate y no
como criterio único. Hay que mirar:

- si `stopWindowFixes` sobrevive a la maniobra intermedia (el `onMovingFix` limpia la ventana), con
  lo que el sistema puede no tener las tres paradas en la mano a la vez;
- cómo convive con [DET-ANCHOR-FREEZE-001] y [ANCHOR-LOCK-001], que ya deciden cuándo el ancla deja
  de poder moverse. Una parada posterior más larga tendría que poder REFINAR un ancla congelada, y
  hoy eso está deliberadamente prohibido para que la caminata no arrastre el pin.

⛔ Esa última tensión es el corazón del ticket: la regla que impide que el pin siga al peatón es la
misma que impide que el pin mejore con una parada mejor del COCHE. Separar las dos cosas exige
distinguir «me he movido andando» de «el coche se ha vuelto a parar», que es medible (velocidad de
maniobra entre ambas, ver el ticket hermano) pero no está hecho.

## Criterio de éxito

- Replay del tramo final de `1791194402036`: el ancla acaba en la parada de 118 s, no en la de 4 s.
- Las 16 trazas de campo siguen aterrizando donde aterrizan hoy.

## Consumidores auditados

Pendiente — se hace al abrir la rama.
