# FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001 · El tutorial enseña a proteger el garaje con una zona privada

**Estado:** ✅ Done — en master (PR [#24](https://github.com/apptolast/Paparcar/pull/24), aprobado sin cambios) ·
GitHub issue [#16](https://github.com/apptolast/Paparcar/issues/16)

## Problema
Issue #16: falta un paso en el tutorial para explicar que, si el usuario tiene un garaje privado,
debe añadir una zona privada en ese punto del mapa, para que al aparcar ahí no se publique una plaza
libre (su casa, por ejemplo).

La funcionalidad existe (`Zone.isPrivate`, interruptor «Zona privada» al añadir zona;
`ConfirmParkingUseCase` guarda `privateZoneId` y las salidas automáticas no publican), pero nadie la
presenta: el checklist enseña que al irte tu aparcamiento libera una plaza y nunca dice la excepción.

## Doctrina
- [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]: un paso se completa con estado MEDIDO.
- [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]: un paso no puede exigir algo que el
  usuario no tiene — aquí, un garaje. De ahí la segunda respuesta.
- [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]: nada bloquea la app.
- [COPY-SPOT-IS-NOT-A-PARKING-001]: el garaje guarda tu APARCAMIENTO; lo que no se publica es la PLAZA.

## Diseño
- `FirstStep.PRIVATE_ZONE`, **segundo** (tras `MARK_PARKING`): la zona solo protege aparcamientos
  confirmados después de crearla, y el paso siguiente explica que al irte liberas tu sitio — este
  es la excepción, así que va antes. Paso de ACTO (no `completesOnEngage`), obligatorio como el 1.
- Se completa con estado real: `resolveFirstSteps(hasPrivateZone = zones.any { it.isPrivate })`.
- Segunda respuesta «No tengo garaje» (`FirstStepCopy.noAnswer`, botón junto al CTA): se banca en
  `firstStepsDone`, porque es la respuesta a la única pregunta del paso. No es un «Aún no».
- CTA «Añadir zona privada» → `HomeSheetAction.RequestAddZone` → el mismo `EnterAddZoneMode` del
  chip de la cabecera; mientras este paso es el actual, la zona **empieza privada**
  (`addingZoneIsPrivate`), con el interruptor en manos del usuario.
- Explicador propio (título, cuerpo, cómo, nota honesta: aplica a los aparcamientos marcados después).
- Icono `Lock`, el mismo del interruptor «Zona privada».
- Strings ×9. Galería: «Paso 2 · zona privada (garaje)». Preview «First steps · private zone».

## Criterio de éxito
- Con coche marcado y sin zonas privadas, el checklist pide «Protege tu garaje».
- «Añadir zona privada» abre el formulario de zona con «Zona privada» ya activada; al guardarla el
  paso se marca solo.
- «No tengo garaje» cierra el paso y el checklist sigue.
- `FirstStepsTest`: pedido tras el aparcamiento, completado por zona privada o por la respuesta, y
  el checklist no está completo sin él.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `when` sobre `FirstStep` (card, explicador, `HomeSheetContent`) | rama nueva |
| `HomeState.firstSteps` | `hasPrivateZone` |
| `HomeViewModel.EnterAddZoneMode` | zona privada por defecto mientras es el paso actual |
| Persistencia por nombre (DataStore, iOS, `App.kt` explicador) | exentos — leen `entries` por nombre |
| `FirstStepsTest` helper | `hasPrivateZone = true` por defecto; recuentos +1 |
| Galería / previews | default con zona; variante y preview nuevos |
| `ReleaseActiveParkingSessionUseCase` | **no cambiado** — ver pregunta 1 |

## Preguntas que quedaron sin respuesta
El PR se aprobó sin contestarlas; se conserva el comportamiento implementado.
1. **Hueco encontrado**: la liberación MANUAL («Me voy → publicar») no mira `privateZoneId` y publica
   la plaza aunque el aparcamiento esté en zona privada; solo las salidas automáticas lo respetan.
   El copy del paso habla de «al salir» en general. ¿Lo corregimos (no publicar, o avisar en el
   diálogo) en un ticket aparte? → Apuntado en
   [`park-a-manual-release-must-respect-the-private-zone-001.md`](park-a-manual-release-must-respect-the-private-zone-001.md).
2. Una zona creada con el coche ya aparcado dentro no protege ESE aparcamiento (se evalúa al
   confirmar). La nota del explicador lo dice. ¿Queremos re-evaluar la sesión activa al crear la zona?
