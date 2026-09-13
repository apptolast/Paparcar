# FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001 · El tutorial señala dónde está cada función, no la abre por el usuario

**Estado:** 🟡 En revisión · rama `feature/FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001` (rebasada sobre
master tras mergear #24) · GitHub issue
[#17](https://github.com/apptolast/Paparcar/issues/17)

## Problema
Issue #17: en cada paso del tutorial hay que llevar al usuario hasta la funcionalidad (scroll, o si no
hay scroll alguna forma de indicar dónde está) y poder completar el tutorial sin usarla, salvo los
pasos obligatorios —aparcar el vehículo y la zona privada—, que deben quedar muy claros. Para
parkings/gasolineras: ver, scrollear y marcar la pestaña. Para avisar de una plaza: scrollear al
botón, no abrir la modal directamente.

Antes:
- «Avisar de una plaza» abría el modo reporte directamente (`RequestReportMode`).
- «Verlo» (parkings y gasolineras) SELECCIONABA el carril de parkings por el usuario.
- «Añadir zona privada» (#16) abría el formulario de zona.
- «Saltar» cerraba el checklist entero aunque faltaran los pasos obligatorios, y nada decía cuáles lo eran.

## Doctrina
- [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]: nada bloquea la app.
- [UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001]: el resaltado no captura toques.
- [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]: el carril se ENTRA; ahora lo entra el usuario.
- [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]: un CTA hace lo que dice su etiqueta —
  por eso los que ahora señalan se llaman «Muéstrame dónde».

## Diseño
- `FirstStepGuide` (presentación): `SPOTS_LANE`, `REPORT_SPOT_CARD`, `NEARBY_COMPLEMENT_TABS`,
  `ADD_ZONE_CHIP`. `HomeSheetAction.GuideTo(target)` sustituye a `RevealNearbyLane` y a
  `RequestAddZone`: en `HomeSheetSection` expande y hace scroll al objetivo (pestañas por su key; la
  tarjeta de avisar es el último item del carril de plazas) o pliega el sheet para dejar ver la
  cabecera flotante (chip de añadir zona, que además se desplaza a la vista y se abre en invitación).
- `Modifier.papGuideHighlight(active, shape)`: contorno verde que late, solo dibujo. Estado de UI en
  `HomeContent` (`firstStepGuide`), 6 s o hasta salir de Browse.
- Mapeo de pasos: FIND_SPOT (ver) → pestaña de plazas; FIND_SPOT (avisar) → tarjeta «Avisar de una
  plaza»; EXPLORE_NEARBY → pestañas de parkings y gasolineras resaltadas sin seleccionarlas;
  PRIVATE_ZONE → chip de añadir zona (al tocarlo, la zona empieza privada, #16). MARK_PARKING sigue
  abriendo el modo de marcar: es el paso obligatorio y el propio acto.
- Completar sin usar: los pasos de conocimiento ya se cierran al pulsar su CTA, así que señalar basta;
  los de permisos tienen «Aún no»; la zona privada tiene «No tengo garaje». No hacía falta un «Saltar
  paso» nuevo.
- Obligatorios: `FirstStep.isRequired` (MARK_PARKING, PRIVATE_ZONE) → el «Saltar» del checklist se
  oculta mientras quede uno (`FirstStepsProgress.hasPendingRequired`). Sin badge: la revisión lo
  retiró («se sobreentiende que hay que hacerlo» y ocupaba media fila).
- La auto-apertura del checklist (`firstStepsCue`) no rebobina la lista mientras hay una guía activa:
  el mismo toque que señala completa el paso y cambia el cue.
- Strings ×9: `first_steps_show_where`; se retiran `first_steps_spot_report_cta`
  y `first_steps_zone_cta` (sin lectores).
- Galería: «Guía del tutorial · pestañas de parkings y gasolineras» y «… tarjeta de avisar de una plaza».

## Criterio de éxito
- «Muéstrame dónde» en avisar de una plaza: el sheet se abre en el carril de plazas, baja hasta la
  tarjeta y la resalta; no se abre el modo reporte; el paso queda hecho.
- En parkings y gasolineras: scroll a las pestañas, resaltadas, sin cambiar de carril.
- En zona privada: el sheet se pliega, la cabecera muestra el chip abierto y resaltado.
- Con MARK_PARKING o PRIVATE_ZONE pendientes: sin «Saltar».
- `FirstStepsTest`: qué pasos son obligatorios y cuándo se retiene el «Saltar».

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `HomeSheetAction.RevealNearbyLane` / `RequestAddZone` | sustituidos por `GuideTo` |
| `HomeSheetContent.onStartStep` | FIND_SPOT, EXPLORE_NEARBY, PRIVATE_ZONE señalan |
| `HomeNearbyLensRow`, `HomeReportSpotCard`, `AddZoneChip`, `HomeHeaderSection` | parámetro de resaltado con default |
| `SheetTransitionEffects` auto-apertura del checklist | se aparta con guía activa |
| `SheetMotion` | `animateToPeek` nuevo |
| `FirstStepsCard` (cabecera, copy) | «Saltar» retenido, CTAs «Muéstrame dónde» |
| `RequestReportMode` (tarjeta de avisar) | sin cambio — sigue abriendo el reporte al tocar la tarjeta |

## Decisiones de la revisión (13-09-2026)
1. **Sin badge «OBLIGATORIO»**: se sobreentiende y se comía media fila. Retirado (y su string ×9).
2. «Saltar» oculto mientras falten los obligatorios, resaltado de 6 s y marcar aparcamiento directo:
   sin objeciones en la revisión, se quedan así.
