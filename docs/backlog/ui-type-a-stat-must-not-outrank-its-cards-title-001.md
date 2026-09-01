# UI-TYPE-A-STAT-MUST-NOT-OUTRANK-ITS-CARDS-TITLE-001 · Las cifras de la hero card gritan más que el nombre del coche

**Estado:** ✅ Done · rama `refactor/UI-TYPE-A-STAT-MUST-NOT-OUTRANK-ITS-CARDS-TITLE-001-statscale` · worktree `../Paparcar-uipolish`
**Cerrado:** 01-09-2026 — `statNumber` 25→20sp (lineHeight 20, tracking −0.4), `statLabel` 13→12sp
(lineHeight 15). Horquilla ALTA elegida: 20sp queda entre `counter` (21) y `cardTitle` (18), la
cifra sigue liderando su celda frente al label pero deja de doblar en presencia al nombre del
coche; la variante 16-17sp se descartó por comprimirse contra el label. El icono (17dp) NO se toca:
pasa del 68% al 85% de la altura del dígito y su anclaje a cap-height es proporcional
[UI-STAT-ICON-CENTERS-ON-DIGITS-001]. `counter`/`chartValue` sin tocar (no se vio incoherencia).
Juzgado en device (Pixel 8 Pro, mock) en el run de cierre.
**Origen:** revisión visual durante el run de `UI-SEVEN-STRAYS-FROM-THE-CANON-001` (captura de
Vehículos con la hero card del Seat)

## Problema

En la hero card de Vehículos, la fila de stats (sesiones · última · plazas cedidas) domina la
tarjeta: **tres cifras a 25sp Bold** (`statNumber`) contra **un título a 18sp Bold** (`cardTitle`,
el NOMBRE del coche) — cada cifra es ~40% mayor que el título, y son tres. El sujeto de la card es
el coche; las stats son datos de apoyo, y hoy la jerarquía está invertida. El icono de cada stat se
percibe pequeño, pero no por su tamaño absoluto: está anclado a la banda del dígito
(`UI-STAT-ICON-CENTERS-ON-DIGITS-001`) y es la cifra gigante de al lado la que lo encoge por
contraste.

## Doctrina implicada

- `UI-TYPE-TWO-VOICES-ONE-ROW-001`: el tamaño es propiedad del ROL. El fix es retocar
  `statNumber`/`statLabel` en `PaparcarType.kt`, **nunca** un override inline (lo caza
  `TypographyGuardrailTest`).
- La voz CIFRA se distingue de MARCA «por tamaño, peso y caja recortada» — por eso la propuesta
  literal del user (cifra = tamaño del título) se matiza: **un escalón POR DEBAJO del título, no al
  nivel**, para que el nombre lidere y la cifra siga protagonizando su propio bloque frente a su
  label.

## Señales / datos

- `cardTitle` = 18sp Bold · `statNumber` = 25sp Bold, `lineHeight` 25, tracking −0.5 ·
  `statLabel` = 13sp SemiBold caps · icono `STAT_ICON_DP` anclado a `figureCapHeightEm`.
- **`statNumber`/`statLabel` tienen UN solo consumidor**: `VehiclePageContent.StatCell`
  (verificado por grep el 01-09). Retocar el rol no tiene colaterales.
- ⛔ **No relitigar el 27-08**: aquella prueba en device fue de COLOR (fila en neutro → revocada,
  las stats se quedaron verde marca). Este ticket es TAMAÑO; el color no se toca.

## Diseño propuesto (números a juzgar en device)

- `statNumber` 25 → **~20sp** (por debajo de `cardTitle` 18? — no: 20sp queda POR ENCIMA de 18.
  Decidir en device entre 19-21sp si debe quedar justo bajo el título o ligeramente sobre él;
  la intención del user es que no robe protagonismo, la doctrina pide que la cifra siga liderando
  SU bloque). Ajustar `lineHeight`/tracking en proporción.
- `statLabel` 13 → ~11-12sp, mismas caps.
- El icono NO se toca: su anclaje a cap-height lo reequilibra solo al encoger el dígito. Verificar
  que la mecánica de `alignBy` sobrevive al nuevo tamaño (es proporcional, debería).
- Revisar de paso `counter`/`counterUnit` y `chartValue` SÓLO si en device se ve incoherencia — no
  arrastrarlos por simetría sin mirarlos.

## Criterio de éxito

- En device (claro y oscuro): el nombre del coche es lo primero que se lee en la card; las stats se
  leen como apoyo, sin perder su lectura de bloque (cifra > label).
- Cero `fontSize` inline nuevos; los guardarraíles de tipografía en verde.
- Los tres StatCell mantienen la misma altura con labels a 2 líneas (`IntrinsicSize.Min` intacto).
- Previews + galería mock de la hero card reflejan el cambio en la MISMA tarea.
