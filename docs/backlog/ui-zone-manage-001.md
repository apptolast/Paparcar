# UI-ZONE-MANAGE-001 · Gestionar la zona desde su modal: chip con lápiz, nombre al final, modal aligerado

**Estado:** 🔵 En progreso · rama `feature/UI-ZONE-MANAGE-001-zone-chip-edit` · worktree `../Paparcar-zone-manage`

## Problema
El chip de zona expone **borrar** (×) como única acción visible sobre el mapa: irreversible, a un
toque, sobre una diana de 14dp, y la acción que sí se usa a diario (retocar el radio, el nombre o
la privacidad) está escondida en un long-press que nadie descubre. Además el modal de zona nace
"cargado": campo de texto + cabecera "Icono" + fila de iconos + slider de radio + toggle de
privacidad, todo apilado en el peek, mientras que sus hermanos (señalar plaza, posicionar
aparcamiento) son banner + una fila de elección + el botón.

Y el encuadre: al entrar en modo zona la cámara se queda al zoom de navegación (15f), donde un
radio de 500 m no cabe en pantalla — se ajusta un círculo que no se ve entero.

## Doctrina violada
- **UI-SHEET-001 / UI-LIST-ITEM-001** — un solo molde por familia de modal. El modal de zona era el
  único que metía un formulario completo en el `content` en vez de banner + chips + acción.
- **Copy sin redundancia** — la cabecera "Icono" sobre una fila de iconos nombra lo que ya se ve.
- **Fallo asimétrico** aplicado a la UI: la acción destructiva no puede ser la más accesible.

## Diseño
El sistema es **"la zona se gestiona en SU modal"**, igual que el aparcamiento se gestiona en el
suyo (corregir / re-aparcar / borrar con confirmación). El chip sobre el mapa queda con dos gestos
y ninguno destructivo: **toque = volar allí**, **lápiz = abrir su modal**.

1. **Chip** (`HomeZoneChips.kt`): trailing `Icons.Rounded.Edit` → `onEdit`. Desaparecen la × y el
   long-press (dos vías para lo mismo dejan de existir: una acción, un gesto visible).
2. **Modal** (`AddingZonePeek.kt`): adopta la anatomía de `ReportPeek`/`AddingParkingPeek` —
   `banner` (qué hacer con el mapa) + `chips` (fila de iconos, **sin** cabecera) + `content` (radio
   + privada) + `actions`. En edición aparece **Eliminar zona** (outlined rojo) con
   `PapAlertDialog` destructivo, exactamente como "Borrar registro" del parking.
3. **Nombre al final** — el `OutlinedTextField` sale del peek y vive en el diálogo de confirmación
   que abre "Guardar zona": el nombre es lo último que se decide, cuando el sitio y el radio ya
   están puestos. Requiere una ranura `content` (+ `primaryEnabled`) en `PapAlertDialog`, que hasta
   hoy solo aceptaba título + cuerpo: se añade en EL componente, no un diálogo paralelo.
4. **Zoom de zona** — el zoom lo decide la UI, no el ViewModel: `HomeEffect.MoveCameraTo` gana un
   `frame: CameraFrame` (`Navigate` = 15f · `ZoneEditing` = 14f) y HomeScreen lo traduce. Entrar en
   modo zona (alta o edición) encuadra a `ZoneEditing`; el toque en el chip sigue en `Navigate`.

## Criterio de éxito
- El chip no puede borrar nada. Borrar exige entrar al modal + confirmar.
- El modal de zona cabe sin scroll y lee como el de señalar plaza.
- Guardar pide el nombre en un diálogo; con nombre en blanco el confirmar está deshabilitado.
- Al abrir alta/edición de zona se ve el círculo entero de radio 500 m.
- `testProdDebugUnitTest` verde + galería mock con las variantes alta/edición.

## Verificación
- `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` ✅ · `assembleMockDebug` ✅
- `testProdDebugUnitTest`: **1126** tests, 0 fallos (1122 previos + 4 nuevos en `HomeViewModelTest`:
  encuadre `ZoneEditing` al entrar en alta y en edición, y el cierre del modal al borrar la zona que
  se edita — con su caso negativo, borrar OTRA zona no lo cierra).
- APK `prod-debug` instalado en el Redmi (`5f8991cb`), sha256 `0690595a…` verificado contra el
  fichero local. En el Oppo falla por firma (beta02 de otro origen), como en tickets previos.
- **Flujo completo recorrido en el Redmi** (14-08, capturas + `uiautomator dump`): alta desde el "+"
  → modal nuevo (banner, iconos sin cabecera, radio, privada, "Guardar zona") entero sin scroll →
  diálogo "Ponle nombre a la zona" con foco y teclado automáticos y guardar deshabilitado en blanco
  → zona creada → chip **con lápiz** → lápiz → "EDITAR ZONA" con "Eliminar zona" en rojo →
  confirmación destructiva → borrado, modal cerrado solo y chip fuera del mapa.
- Corregido sobre la marcha: el placeholder `Nombre (ej. Casa, Trabajo)` ocupaba **2 líneas** dentro
  del diálogo (más estrecho que el sheet) e inflaba el campo → acortado a `Ej. Casa, Trabajo` en los
  9 locales, que además deja de repetir "Nombre" (ya lo dice el título del diálogo).
- ⚠️ La 2ª instalación en el Redmi **no prendió**: `pm path` daba el hash nuevo pero el proceso
  cargaba otro `base.apk` (`/proc/PID/maps`), así que la pantalla mostraba la UI anterior. Un
  `force-stop` + `install -r` + relanzar lo arregló. Anotado en la memoria de referencia de MIUI.
- ⏳ Pendiente: el encuadre a 14f con un radio de 500 m (probado con el radio por defecto, 250 m).

## Consumidores auditados
| Sitio | Qué asumía | Estado |
|---|---|---|
| `HomeHeaderSection.HeaderZoneChips` | chip con `onDelete` + `onLongPress` | migrado a `onEdit` |
| `HomeScreen.HomeFloatingHeader` | cableaba `onDeleteZone` | borrado (`DeleteZone` ahora sale del modal) |
| `HomeViewModel.enterEditZoneMode` | emitía `MoveCameraTo` sin zoom | emite `frame = ZoneEditing` |
| `HomeViewModel.EnterAddZoneMode` | no movía cámara | emite `frame = ZoneEditing` |
| `HomeIntent.DeleteZone` | lo disparaba el chip | lo dispara el footer del modal |
| `strings.xml` ×9 | `home_zone_icon_section` | key retirada; nuevas keys de banner/diálogos |
| `StateGalleryScreen` | solo variante "add zona" | + variante "editar zona (con borrar)" |
