# PARK-RETRACTED-BACKFILL-MUST-LEAVE-NO-PIN-001 · la plaza que la app misma se desdice sigue en el histórico

> ⚠️ **Cruzado con el rediseño (30-08): INTACTO, y destapa la única laguna estructural.** Las siete
> piezas son PREVENTIVAS; ninguna puede mover, marcar ni retirar un pin ya escrito. Es el **único caso
> de uso vivo** de la candidata a Pieza 8 (pin provisional hasta el fin del viaje) — el pin a 142 m del
> 30-08 dejó de necesitarla al medirse que su reposo bueno existía 10 min ANTES de plantar.
> Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.2.

**Estado:** ✅ **CERRADO el 31-08**, absorbido por `PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001`
(`docs/backlog/park-a-refuted-pin-leaves-the-history-001.md`). Las respuestas a las dos preguntas que
este doc dejaba abiertas: **se marca, no se borra** —la razón ya estaba escrita en `SpotStatus`— y el
filtro vive en las **cuatro lecturas de histórico de la DAO**, no en cada consumidor. El barrido
encontró además un **segundo caso** (52 s, 30-08, Calle del Verdugo) y una **segunda puerta**: el
botón *«No, cancelar»*, que tenía el mismo defecto y cuyo KDoc llevaba el `TODO-REVERT-P1` pidiéndolo.
**Origen:** follow-up deliberado de `DET-BACKFILL-CANNOT-PIN-A-MOVING-FIX-001` (field 27-08).
Aquel evita **crear** el pin fantasma; éste limpia el que ya se creó por cualquier otra vía.

## Problema

Field 2026-08-27, Oppo. El backfill plantó el pin `724befda` a las 12:29:18 y **63 segundos después
la propia app procesó su salida**:

```
12:29:18  Backfill: ✓ backfilled parking at 36.6027462,-6.2568375 (reliability=0.5)
12:29:36  ExitWitness: ⚑ EXIT emitted geof=724befda   (la valla del pin recién nacido)
12:30:21  Depart: attempt=2 geof=724befda speed=16.297134km/h → Confirmed
12:30:21  ClearActiveParkingSessionWorker: ■ SUCCESS session=724befda
```

`ClearActiveParkingSessionWorker` cierra la sesión (`isActive:false`) pero **el registro se queda en
`users/{uid}/parkingHistory`**, sin dirección (el enriquecimiento no llegó a resolver nada útil) y
con `detectionPath = safety_net_backfill`. Para el user es una fila más en su histórico de
aparcamientos, indistinguible de uno real — que es exactamente como lo reportó: *«un FALSO
POSITIVAZO en Dia · Calle Ronda del Puerto 15»*.

## Doctrina violada

*Mejor un falso negativo que un falso positivo.* Un aparcamiento del que la app **ya ha concluido que
nunca se ocupó** no es un dato incierto que convenga conservar: es una afirmación que ella misma ha
retirado. Conservarla es afirmar algo que ya sabemos falso.

## Señales / datos disponibles

- La ventana entre creación y salida confirmada es medible: `timestamp` del pin vs el momento del
  `Depart → Confirmed`. El 27-08 fueron **63 s**.
- `detectionPath` distingue el origen (`safety_net_backfill`, fiabilidad 0,5) de un confirm por
  conducción medida (0,9). Una plaza deducida y retirada no tiene el mismo estatus que una vivida.
- Ya existe maquinaria de retractación (`RetractDeducedDepartureUseCase`), pero opera sobre el
  **spot comunitario**, no sobre el registro del histórico — y en este log falla 44 veces con
  *«retract failed for spot=a786c135 — the short TTL still bounds it»*.

## Diseño (a decidir)

Pregunta abierta: ¿se **borra** el registro o se **marca**? Borrar es más limpio para el user pero
pierde la traza para diagnóstico; marcar conserva la arqueología pero exige que el histórico sepa
filtrar. Probablemente lo segundo, con el filtro en la consulta del histórico y el registro visible
sólo en diagnóstico. **No decidir esto sin mirar antes cómo lo lee la UI del histórico.**

⚠️ Ojo con el alcance: la retractación **no** debe alcanzar a un pin que el user haya confirmado a
mano ni a uno con conducción medida; sólo a los deducidos que la propia app desmiente.

## Criterio de éxito

- Un pin de backfill cuya salida se confirma dentro de una ventana corta deja de aparecer en el
  histórico del user.
- Un pin normal cuya salida se confirma horas después **sigue** en el histórico (es el caso sano).
- Un pin manual nunca se toca.

## Consumidores auditados

Pendiente — se hará al abrir la rama.
