# HISTORY-DETAIL-002 — Stepper temporal (‹ pasado / › reciente) + respiro y ritmo del sheet

**Rama:** `feature/HISTORY-DETAIL-002-stepper-direction-spacing`
**Estado:** ✅ Done · mergeada a master (squash) · validada en device (Redmi ambos sentidos, hash-verified; Oppo APK verificado).

## Problema (reportado por el usuario)
1. Las flechas del detalle de aparcamiento histórico estaban **invertidas**: el chevron izquierdo ‹
   saltaba al aparcamiento MÁS RECIENTE y el derecho › al MÁS ANTIGUO. Prueba interna: el botón ‹
   llevaba el content description "Aparcamiento anterior" (anterior en el tiempo = más antiguo) pero
   hacía lo contrario — la etiqueta de accesibilidad y el comportamiento se contradecían.
2. El sheet quedaba justo por abajo (CTA pegado al gesto del sistema) y pedía una revisión general
   de espaciado/composición.

## Razonamiento de la dirección
Dos modelos posibles: (a) *pager de lista* — "siguiente" = siguiente elemento del historial
(más-reciente-primero), que es lo que estaba implementado; (b) *línea temporal* — como calendarios y
extractos: ‹ = pasado, › = hacia hoy. Gana el temporal porque en esta pantalla no hay lista visible
que imponga un orden de lectura (los chevrons flanquean un objeto fechado), y porque al abrir el
aparcamiento más reciente (el caso habitual) lo honesto es que quede activo solo el ‹ ("puedes
volver atrás en el tiempo"). Las content descriptions existentes ya asumían este modelo.

## Solución
### Dirección (rename semántico, sin lógica nueva)
- `ParkingLocationIntent`: `FocusPrevious`/`FocusNext` → **`FocusOlder`/`FocusNewer`**.
- VM: lista newest-first ⇒ `FocusOlder` = stepFocus(+1), `FocusNewer` = stepFocus(−1).
- State: `hasPrevious`/`hasNext` → **`hasNewer`/`hasOlder`** (mismos getters, semántica explícita).
- Sheet: chevron ‹ = `onOlder`/`hasOlder`, chevron › = `onNewer`/`hasNewer`. Los strings
  `parking_detail_prev`/`parking_detail_next` NO cambian: ya decían anterior/siguiente en sentido
  temporal y ahora por fin coinciden con lo que hace el botón (9 locales intactos).

### Page-turn direccional (motion enseña el mapeo)
La ambigüedad lista-vs-tiempo no se elimina eligiendo convención: se disuelve con movimiento.
Al pulsar › el contenido nuevo (hero + fecha + método) entra deslizándose DESDE LA DERECHA; con ‹
desde la izquierda — "los siguientes viven a la derecha" se intuye sin leer nada. `AnimatedContent`
con `PapMotion.emphasized()` (slide horizontal + fade), keyed por **id de sesión** (`contentKey`)
para que un re-emit de Firestore de la misma sesión (geocode tardío) refresque en sitio sin
re-disparar el slide [BUG-PEEK-JITTER-001]; la dirección sale de comparar timestamps. Cabecera y
CTA quedan estáticos; los meta-rows leen `isActive` de la sesión mostrada (correcto durante la
salida de la card vieja).

### Deslizable (gemelo gestual de los chevrons)
La card acepta swipe horizontal: arrastrar a la IZQUIERDA trae el más reciente (= ›), a la DERECHA
el más antiguo (= ‹). `detectHorizontalDragGestures` sobre el Surface del sheet, disparo al soltar
con umbral `SWIPE_TRIGGER_DP = 64` (por encima del slop, cómodo a una mano); los taps (CTA,
chevrons) pasan intactos. El page-turn direccional reproduce el mismo slide, así que dedo y
animación coinciden. Guards/callbacks vía `rememberUpdatedState` (el gesture loop no se reinicia).

### Composición del sheet
- **Drag pill eliminado** (composable + tokens): el sheet no es arrastrable; el pill era una
  afordancia falsa y a alpha 0.12 apenas se veía. Borrado limpio, nada hereda su conducta.
- Padding vertical partido en `SHEET_TOP_PAD = 16` / `SHEET_BOTTOM_PAD = 20` (antes 16/16 con el
  pill ocupando la parte alta): el CTA gana aire sobre la zona de gestos, encima del
  `navigationBarsPadding`.
- Ritmo a rejilla de 4dp: `SECTION_GAP` 9 → 12; `ACTION_TOP_GAP` 30 → 24 (el aire bajo el CTA
  ahora lo aporta el bottom pad, no un gap gigante encima).

## Dev Catalog
`parkingDetailSheet` (galería) renombrado a `hasOlder`/`hasNewer`; variantes de extremos
re-etiquetadas: "más reciente (solo ‹ activo)" / "más antiguo (solo › activo)".

⚠️ En el árbol de master hay un `StateGalleryScreen.kt` **staged sin commitear** (sesión previa)
que también llama a `HistoryDetailSheet` con los nombres viejos — al integrar esta rama habrá que
aplicar el mismo rename allí (la zona es idéntica, aplica limpio).

## Tests
`ParkingLocationViewModelTest`: stepper reescrito en clave temporal — FocusOlder retrocede,
FocusNewer avanza, extremos (`hasNewer` false en el más reciente, `hasOlder` false en el más
antiguo), clamp en el extremo antiguo.

## Device (14-08, madrugada)
Swipe verificado en Redmi con APK comprobado por hash: Bermeja 20 (19:30) ⇄ Estopa 9 (19:51) en
ambos sentidos. ⚠️ Lección: un `adb install` en el Redmi (MIUI) reportó "Success" pero NO reemplazó
el APK (el comportamiento viejo — pill + flechas invertidas — venía de ahí); desde entonces,
verificar con `sha256sum` local vs `pm path`+`sha256sum` en device cuando algo huela a build viejo.

APK prod-debug instalado en Oppo + Redmi. Verificado en pantalla: Oppo — sheet sin pill, chevrons
correctos (ayer + › → aparcamiento actual, › se deshabilita en el extremo reciente); Redmi — mismo
flujo con la animación en vuelo capturada (cabecera ya cambiada, card vieja saliendo) y estado final
correcto (Estopa 9 → Góndola 25 actual, acentos verdes de sesión activa).

## Pendiente
- [ ] Review + commit (con permiso).
- [ ] Field en mano: sensación del page-turn + respiro inferior con gesture nav.
