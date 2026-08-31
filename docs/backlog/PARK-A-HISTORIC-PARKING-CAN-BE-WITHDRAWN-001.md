# PARK-A-HISTORIC-PARKING-CAN-BE-WITHDRAWN-001 · Quitar del historial un aparcamiento que no debería estar

**Estado:** ✅ Done — mergeado a master el 31-08-2026
**Pendiente de device:** ⏳ el botón y su diálogo sin probar en mano.
**Abierto:** 31-08-2026 · sobre master `748648fc`

## Problema

Un pin equivocado que ya se cerró se queda en el historial para siempre. El usuario no tiene ninguna
forma de decir *"esto no fue un aparcamiento"* una vez pasada la notificación de confirmación: el
único camino que existe es "No, cancelar" sobre la sesión VIVA, y desaparece con ella.

El propio `RevertParkingUseCase` lo dejó anotado al cerrarse (`RevertParkingUseCase.kt:112-113`):
*"si el usuario ve la sesión ahí porque un paso falló, la limpieza manual desde la pantalla de
historial es el fallback"* — una pantalla que nunca tuvo ese botón.

## Decisión de alcance (user, 31-08)

**Sólo borrar. No se edita el histórico.** Se ofrecieron las tres opciones y el user eligió la
primera: mover el pin de una sesión cerrada reescribiría una MEDICIÓN — `detectionPath` y
`armEvidence` seguirían describiendo dónde creyó el detector que estaba el coche — y este historial
se usa como corpus de field-test. Editar corrompe la evidencia; retirar no.

## Doctrina aplicada

- **Retirada, no borrado.** `retractParkingSession` ya existe desde `PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001`
  (`d010b8c0`): estampa `retractedAtMs`, la fila **sobrevive** para el diagnóstico y desaparece de las
  cuatro lecturas que alimentan el historial. Es la respuesta que `SpotStatus` ya había escrito para la
  plaza comunitaria — *un documento borrado simplemente deja de llegar, y se lleva la explicación con él*.
- **`DET-ASSERTION-OUTRANKS-INFERENCE-001`**: nada de lo que la app mide supera la palabra del usuario.
  No se consulta ninguna política: si dice que fuera, fuera.
- **Ningún caso de uso nuevo** [DET-VERDICT-NOT-PREDICATE-001]: no hay decisión que tomar, es una
  llamada al repositorio. Va donde ya vive su gemela `ResolveInferredRoute`, en el `handleIntent` del
  ViewModel. ⛔ Y **NO se reutiliza `RevertParkingUseCase`**, que es lo que parecía tocar: está escrito
  para la sesión VIVA y llama a `clearActiveParkingSession(..., publishedSpot = false)`, lo que sobre
  una sesión ya cerrada **reescribiría `endedAt` y borraría el hecho de haber cedido una plaza** —
  corrompiendo la métrica de plazas cedidas del coche.

## Diseño

1. `ParkingHistoryIntent.WithdrawParking(sessionId)` → el VM llama a `retractParkingSession` y emite
   `ParkingHistoryEffect.Withdrawn`; la pantalla se cierra (ya no hay nada que leer ahí).
2. **Sólo sesiones CERRADAS.** `ParkingHistoryState.canWithdraw` exige `isActive == false`: retirar una
   sesión viva la escondería del historial mientras la detección la sigue vigilando (geofence armada,
   plaza pendiente de publicar). Terminar una sesión viva es un desmontaje que ya posee Home.
3. Acción secundaria en el pie de la ficha (`PapFooterButtonStyle.Outlined`, bajo "Cómo llegar") +
   confirmación con `PapAlertDialog` en `PapDialogAccent.Destructive` — el mismo componente y el mismo
   `home_release_dialog_cancel` que usa el borrado de zonas.
4. Copy en los 9 locales, con la voz del borrado de zonas (causa + consecuencia + qué NO cambia):
   *"Desaparece de tu historial y del mapa. La plaza que ya compartiste con la comunidad se queda como
   estaba."* [COPY-SPOT-IS-NOT-A-PARKING-001: lo tuyo es APARCAMIENTO, lo de la comunidad es PLAZA.]

## Hallazgo colateral — el fake de tests no escondía lo retirado

El primer test falló con `expected:<[s2]> but was:<[s2, s1]>`: `FakeUserParkingRepository` (commonTest)
**estampaba `retractedAtMs` y seguía sirviendo la fila**, al revés que Room, cuyas cuatro lecturas de
historial llevan `retractedAtMs IS NULL` (`UserParkingDao.kt:40-61`). Con ese doble, una pantalla que
mostrara aparcamientos retirados habría pasado todos los tests — el invariante de `d010b8c0` no tenía
quien lo comprobara. Corregido: las cuatro lecturas del fake filtran, y las de DIAGNÓSTICO siguen
viéndolo todo, igual que producción. La suite entera (2047 tests) pasa con el fake corregido, así que
ningún test dependía de la conducta falsa.

## Criterio de éxito

- En una sesión cerrada aparece "Quitar del historial"; confirmar la retira y cierra la pantalla.
- En una sesión VIVA el botón no existe.
- La fila sigue en Room con su `retractedAtMs` (el diagnóstico la lee).
- Falsado de hecho: con el fake antiguo (que no escondía lo retirado) el test **falla**.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `RevertParkingUseCase` | ⛔ NO reutilizable aquí (rompería `publishedSpot`/`endedAt`); su nota pedía este botón |
| `UserParkingRepository.retractParkingSession` | ✅ ya existe, ya sincroniza |
| `FakeUserParkingRepository` (commonTest) | ⛔ no modelaba el filtro → corregido |
| `FakeUserParkingRepository` (commonMain/mock) | ✅ ya filtraba desde `d010b8c0` |
| `HistoryDetailSheet` | 🔧 acción nueva, opcional (`onWithdraw` nulo = sesión viva) |
| Galería mock | ✅ variante "Cerrada · con quitar del historial" |
