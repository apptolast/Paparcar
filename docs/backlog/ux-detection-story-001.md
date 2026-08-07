# UX-DETECTION-STORY-001 — Un solo relato del estado de detección en Home

> **Estado**: ✅ IMPLEMENTADO en la rama `feature/UX-PARK-FLOW-001-park-flow-redesign` (2026-08-06,
> mismo día que la spec, tras las decisiones del user en §3). Pendiente: revisión + device.
> Hijo de UX-PARK-FLOW-001 (= "UXP-a" en `ux-park-flow-001-analysis.md` §5). Cura **C6/H5**.
> Redactado 2026-08-06 sobre `master @ b16a56bc`. VEH-ACTIVE-FENCE-001 ✅ en master → sin bloqueos.

## 1. Problema (C6/H5)

El estado de detección se cuenta hoy en TRES voces sueltas que el usuario tiene que ensamblar:

1. **Pills de acción** (`HomeDetectionSurface` + `DetectionUiState`): "Añade un coche", "Activa la
   detección", "Marcar aparcamiento"… — solo estados que piden acción; los estados "va bien"
   (`Parked`/`Monitoring`/`Silent`) son invisibles aquí.
2. **Fase en el chip del vehículo** (pill efímera "EN RUTA"/"APARCANDO…" dentro del peek).
3. **Banner GPS** (`HomeGpsAccuracyBanner`, ámbar/rojo por precisión).

Consecuencia: no hay respuesta única a "¿qué está haciendo la app AHORA por mí?". El usuario no
distingue "vigilando tu Toyota" de "no estoy vigilando nada porque no has declarado coche" — y esa
distinción es EXACTAMENTE la que el modelo de VEH-ACTIVE-FENCE-001 ya sabe contestar.

## 2. Propuesta — una sola voz, jerarquía fija

Una única superficie de estado en el sheet (evolución de `HomeDetectionSurface`, mismo sitio) que
SIEMPRE resuelve a un relato, con esta precedencia (de más urgente a más silencioso):

| Prioridad | Relato (copy causa+consecuencia, sin mecánica interna) | Estado hoy |
|---|---|---|
| 1 | **Bloqueado** — "Sin ubicación no podemos detectar dónde aparcas" + Arreglar | `BlockedCore` |
| 2 | **Sin coche** — "Añade tu coche para detectar tus aparcamientos" | `NoVehicle` |
| 3 | **Apagada** — "Activa la detección y publicamos tu plaza al irte" | `Inactive` |
| 4 | **Declara** — "¿Conduces? Dinos qué coche para vigilar tu plaza" (+ Marcar aparcamiento) | `AwaitingFirstPark` |
| 5 | **Conduciendo / Aparcando** — fase en curso, con el nombre del coche | chip fase (`DrivingMeta`) |
| 6 | **Vigilando [coche]** — "Tu Kamiq está aparcado; avisaremos al salir" | `Parked`/`Monitoring`/`Silent` (hoy mudos) |

- El **banner GPS** se mantiene aparte (es calidad de señal, no estado de detección). La idea de
  fundirlo como línea secundaria del relato se DIFIERE (no implementada; evaluar en device).
- **Corrección al redactar**: la "pill efímera del chip" del análisis ya no existe como tal — la
  fase vive hoy en el eyebrow del peek PLEGADO (`BrowsePeek`) y en el acento de las cards de
  vehículo. Se MANTIENEN: son la voz del sheet cerrado y la identidad de las cards; este relato es
  la voz del sheet ABIERTO. Mismos strings de fase traducidos → sin deriva de copy.

## 3. Decisiones — RESUELTAS (user, 2026-08-06)

1. **¿Relato visible también en el estado feliz (prioridad 6)?** SÍ pero discreto (una línea, sin
   card llamativa) — responde también a C3 sin abrir otra superficie.
2. **¿"Vigilando" nombra al coche activo o al aparcado?** Al **ACTIVO, siempre** — decisión user:
   es el único vehículo que trabaja por Coordinator detection (el gate de estrategia
   [DET-STRATEGY-GATE-001] solo arma coordinator/centinela bajo COORDINATOR); los coches BT van
   por su carril determinista y no necesitan relato de vigilancia.
3. **Copy exacto**: borradores de §2, pulidos en implementación (causa+consecuencia, sin mecánica).

## 4. Piezas de implementación — HECHAS (06-08, esta rama)

1. ✅ `DetectionStory` (sealed) + `resolveDetectionStory()` en
   `presentation/home/model/DetectionStory.kt` — proyección pura (uiState × drivingMeta ×
   vehicleCards) → relato; precedencia completa testeada en `DetectionStoryTest`.
2. ✅ `DetectionUiState`: nuevo `ArmedBluetooth` (antes BT-armado caía en `Silent` y era
   indistinguible del "nada que decir"); `Silent` queda solo para vehículo no-aparcable.
   `isDetectionWorking` incluye BT; `rendersActionSurface` borrado (sin usos).
3. ✅ `HomeDetectionSurface` renderiza `DetectionStory`: action rows (4) + `StatusLine` discreta
   (Vigilando/Conduciendo/Aparcando, icono primario + título + caption, sin card).
4. ✅ Strings ×7 en los 9 locales (`home_det_watching_*`, `home_det_driving_*`, `home_det_candidate_*`).
5. ✅ Gallery (9 variantes) + `HomeDetectionSurfacePreviews` (paridad).
6. Eyebrow del peek y cards de vehículo: SIN CAMBIOS (voz del sheet plegado, ver §2).
7. `MockScenario` "2 coches, 1 activo aparcado": NO añadido (no afecta routing; la galería cubre
   los estados). Reevaluar si hace falta al probar en device.

## 7. Ajuste visual pendiente (revisión device 06/07-08)

Al user no le acaba de encajar la línea suelta ("Conduciendo tu Toyota Corolla / Detectaremos
dónde aparcas") entre la cabecera y las cards. Diagnóstico compartido por Claude: **el sheet
habla en cards** (cabecera de zona, cards de vehículo, filas de plazas) y la línea desnuda queda
"sin caja", flotando; además en Conduciendo convive con el acento "En ruta" de la card de abajo.
Opciones:
- **A (recomendada)**: mismo esqueleto de card silenciosa que las action rows —
  `surfaceContainerHigh` + borde hairline + barra de acento fina en primary — pero SIN CTA.
  Una sola familia visual; la jerarquía la da la ausencia de botón, no la ausencia de caja.
- **B**: fundir el relato como subtítulo de la card del vehículo activo (mata la redundancia
  pero rompe "una voz en un sitio" y no escala a los chips compactos multi-coche).
- **C**: dejar la línea desnuda (estado actual) y solo ajustar grid/paddings.
Decisión pendiente del user (ver también `ux-parked-state-001.md` §4.4).

## 5. Qué NO es de este ticket
- Cambios de detección/scoring (nada de androidMain/detection).
- La superficie del vehículo activo en VEHÍCULOS (eso es UXP-b, si sobrevive a la decisión §3.1).
- Unificación notificación/sheet de confirmación (UXP-c; la cuenta atrás C5 ya está hecha en esta rama).
