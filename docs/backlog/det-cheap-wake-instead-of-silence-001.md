# DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001 · Un despertar caro no se calla: se abarata

**Estado:** 🔵 Abierto, sin código · **es el agujero residual que `DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001`
dejó a propósito** (✅ master `4d1d6716`) · prometido en `PARKING-DETECTION.md` §2 y en el mensaje de
aquel commit

## Problema

`DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001` puso dos puertas al amortiguador de despertares
(decaimiento de la racha + no amortiguar fuera de toda valla) y cerró los dos incidentes del 21-08.
Pero dejó un caso vivo, escrito en su propio doc:

> Si alguien encadena **3 abortos rápidos** mientras camina **DENTRO** de la valla y acto seguido
> conduce, y la valla **no entrega su EXIT**, seguimos ciegos.

No es teórico: es exactamente lo que le pasó al Oppo esa noche (abortos a 11 m del pin, y la valla
de Covirán nunca entregó su EXIT). Se salvó por el decaimiento — porque sus abortos estaban a 39 min
— pero juntos habrían cegado el móvil igual.

## Doctrina en juego

El amortiguador nació de un problema **real** (13-08: ≈130 sesiones armadas y refutadas en una hora,
una cada ~18 s, durante un paseo junto al coche). Lo que se eligió entonces fue **suprimir el
despertar**. La alternativa que nunca se probó es **abaratarlo**.

Y ahí está el error de encuadre: lo caro de un despertar **no es el sensor** — la moción
significativa es un trigger de hardware, prácticamente gratis. Lo caro es lo que desencadena: una
sesión FGS completa con stream de GPS, hasta 4 min de presupuesto, y un documento de sesión en
Firestore. Apagamos el sensor barato para no pagar la sesión cara.

## Diseño propuesto

Durante el periodo de silencio, el sensor **sigue armado**. Al dispararse:

1. **Un solo fix** (la maquinaria `OneFix` ya existe y la usa la red de seguridad cada 5 min).
2. **Escalar a sesión completa SÓLO si** ese fix es incompatible con «dando vueltas junto al coche»:
   velocidad por encima del techo peatonal (`maxPedestrianSpeedMps`), o desplazamiento fuera del
   radio de la valla.
3. Si no, volver a dormir sin FGS, sin stream, sin documento de sesión.

Coste por despertar: **un fix** en vez de una sesión de 4 min. En la tormenta del 13-08 eso son
~200 fixes en una hora en vez de ~130 sesiones FGS — mucho más barato que hoy, y **nunca ciego**.

### Cabos que hay que atar antes de tocar código

- **Suelo de cadencia.** La moción significativa se re-dispara cada ~18 s durante una caminata; un
  fix cada 18 s durante una hora sigue siendo mucho. Mínimo entre triajes (¿60 s?). A 30 km/h eso
  son 500 m — el viaje no se esconde en esa ventana.
- **Coste real de un fix en frío.** Hay que medirlo: si despertar el GPS cuesta casi lo mismo que la
  sesión, el ticket entero se cae. Es la primera pregunta a responder, no la última.
- **La lotería del espejismo.** El doc del amortiguador dice que *cada despertar es un boleto para
  que un espejismo Doppler de primer fix finja conducción*. Un triaje que sólo LEE un fix y no
  confirma nada no compra boletos — pero la escalada sí, así que el umbral de escalada tiene que
  usar el mismo listón de credibilidad que el resto (`isCredibleDrivingSpeed`), no la velocidad
  cruda.
- **Dónde vive la decisión.** Predicado puro en `domain/detection/` junto a
  `SentryWakeCooldown.kt` — no un `Evaluate*UseCase` [DET-VERDICT-NOT-PREDICATE-001].

## Criterio de éxito

- Replay de la tormenta del 13-08: cero sesiones FGS, y el coste total por debajo del actual.
- Replay del Redmi 22:12 y del Oppo 23:38: el triaje **escala** y el viaje se caza.
- Un paseo real junto al coche no inunda de sesiones ni de documentos en Firestore.

## Dependencia

⛔ **No empezar antes de validar en campo `DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001`.** Si sus dos
puertas bastan en la práctica, este ticket puede quedarse en el cajón mucho tiempo; si vuelve a
haber un FN por cegado, sube a prioridad alta. La decisión la dan los datos del viaje, no el diseño.
