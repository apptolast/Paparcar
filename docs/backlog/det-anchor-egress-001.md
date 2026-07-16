# DET-ANCHOR-EGRESS-001 — El egress nace del ancla (cerca refina, lejos invalida)

> **Estado**: ESPECIFICADO — pendiente de rama `feature/DET-ANCHOR-EGRESS-001-egress-born-at-anchor`.
> Origen: FP grave de la auditoría 2026-07-15 (pin a 1,11 km) + pins-dentro-del-edificio desde el
> cambio de régimen DET-ANCHOR-FREEZE-001 (master 12-07). Decidido con el user 2026-07-16.
> Prioridad: **P0** — el más quirúrgico y el de mayor daño actual; primero de la tanda.

## Problema (evidencia de campo)

Dos síntomas del mismo defecto — el desplazamiento de egress tiene suelo pero **no tiene techo**,
y el pin congelado ya no puede refinarse con el paseo:

- **FP Enamorados** (D3 `1784131878857`, 15-07 18:27 local): el ancla congeló en un semáforo a
  1,11 km del aparcamiento real (Camino de los Enamorados); el unfreeze nunca llegó (gate de
  accuracy, ver DET-CREDIBLE-DRIVE-001); llegada real a Camelias + paseo genuino →
  `confirmed_kinematic+egress` 0.85 **sobre el ancla del semáforo**. `hasEgressDisplacement`
  solo comprueba estar A MÁS DE X metros del ancla: a 1,11 km daba positivo trivialmente.
  steps+egress habría confirmado igual de mal — el defecto es del ancla, no del camino.
- **Pins dentro del edificio** (p. ej. D2 Camelias ruta-2 `1784131860665`: ancla acc=3 m y aun
  así 6-10 m dentro del polígono): desde DET-ANCHOR-FREEZE/SHORT-TRIP el pin es el mejor fix de
  la parada de llegada SENTADO EN EL COCHE, y el refinamiento muere en el primer paso
  (`sameStopPreEgress`, stepCount==0). Los fixes de llegada en calle estrecha traen sesgo de
  reflexión con accuracy reportada optimista. Antes del 12-07 el pin reflejaba los fixes del
  inicio del paseo (puerta del coche) — mejores en este caso, pero era el mecanismo que
  arrastraba pins a casa. Hay que recuperar lo primero sin reabrir lo segundo.

## Doctrina

*El paseo de egress empieza físicamente en la puerta del coche.* Ese hecho da UNA regla con dos
caras: si el egress nace dentro del alcance andable del ancla, sus primeros fixes REFINAN el pin;
si nace fuera, el ancla es de una parada intermedia y NO puede haber pin → prompt.

## Diseño

Sobre el ancla PINNED (locked o frozen), en el punto de confirmación (todas las rutas de confirm:
steps+egress, kinematic+egress, vehicleExit+window+egress, saves desatendidos):

1. **Techo (invalida)**: presupuesto andable = pasos×zancada (o fixes cinemáticos×paso
   equivalente con contador mudo) + accuracy del ancla + accuracy del fix + margen — la
   maquinaria de `movementOutrunsSteps` ya calcula esto. Si el inicio del paseo de egress queda
   FUERA del presupuesto respecto al ancla → inconsistencia ancla↔egreso → **Prompt, nunca pin**
   (degradación honesta, coherente con fallo asimétrico). Mata el FP de 1,11 km.
2. **Refinamiento acotado (refina)**: si el egress nace DENTRO del presupuesto, los primeros 1-3
   fixes del paseo con accuracy de pin pueden afinar la posición del ancla, acotados al
   presupuesto de zancadas — recupera los aparcamientos buenos del Oppo (pin en el bordillo, no
   en el salón) sin reabrir el arrastre: el presupuesto impide que la caminata larga mueva nada.
3. El ancla congelada conserva su AUTORIDAD (la sesión vio al coche descansar); lo que se ajusta
   con el egress es su POSICIÓN, dentro del margen físico.

## Relación con otros tickets

- DET-CREDIBLE-DRIVE-001 arregla el unfreeze aguas arriba (el ancla no debería quedarse en el
  semáforo); este ticket es el **backstop en la confirmación** — defensa por invariante, no por
  parche: aunque el ancla llegue mal, el pin no se planta.
- SNAP-TO-PARK-001 (Overpass/edificios) queda como refinamiento POSTERIOR encima de esto, no
  como prerequisito: la mayoría de los pins-en-fachada se corrigen ya con el peldaño 2.

## Validación

Replay: (a) Enamorados `1784131878857` → Prompt, cero pin automático; (b) D2 Camelias ruta-2
`1784131860665` → pin refinado hacia el inicio del paseo (fuera del polígono, ≤ presupuesto);
(c) trazas sanas de la auditoría (Covirán D2/D3, Góndola/Fragata) → confirmación intacta;
(d) traza arrastre-a-casa del 10-07 (fixture existente) → sigue sin arrastre.

## Criterio de éxito

Nunca más un pin automático a >presupuesto-andable del coche; y el caso Camelias-Oppo reproducido
acaba con el pin en la calle, no dentro de la casa.
