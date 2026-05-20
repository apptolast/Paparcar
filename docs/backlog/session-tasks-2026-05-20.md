# Paparcar — Session tasks — 2026-05-20

Ten tareas originadas en sesión de planificación del 2026-05-20.
Cruzan con trabajo ya realizado en sprints anteriores (indicado con ✅).

## Status legend
✅ **Done** — merged to master
🔵 **Branch ready** — work complete, awaiting review/merge
⚪ **Pending** — not started
🟡 **Blocked** — waiting on user or external dependency

---

## Tarea 01 · Icono y logo — paleta nueva

**Ticket family:** `ICON-LOGO-PALETTE-001..004`
**Backlog detallado:** `docs/backlog/icon-logo-palette-2026-05-19.md`

| Sub-tarea | Estado |
|-----------|--------|
| 001 — Adaptive icon repaint (vector XML) | ✅ Done 2026-05-19 |
| 002 — Legacy mipmap PNGs (mdpi…xxxhdpi, square + round) | ✅ Done 2026-05-20 |
| 003 — Play Store 512×512 hi-res icon | ⚪ Pending — requiere exportar SVG y subir a Play Console (acción manual) |
| 004 — Visual smoke test (light/dark, themed icons, adaptive shapes) | ⚪ Pending — requiere emulador/dispositivo |

---

## Tarea 02 · Chips de filtros en VehicleScreen — lógica conectada

**Ticket:** `VEH-FILTER-CHIPS-001`
**Rama sugerida:** `feature/VEH-FILTER-CHIPS-001-filter-logic`
**Prioridad:** Media | **Esfuerzo:** Pequeño
**Estado:** ✅ Done 2026-05-20 — ya implementado. `HistoryState.activeFilter` vive en `HistoryViewModel`; `HistoryIntent.SetFilter` conectado en `HistoryFilterBar` tanto en `HistoryScreen` como en `VehiclePageContent`. VehiclePager tab persistence también añadido (selectedVehicleIndex en VehiclesViewModel).

**Notas:**
- Primero auditar qué filtros existen ya en la UI antes de definir el sealed `VehicleFilter`.
- Si los chips son Material3 `FilterChip`, el estado `selected` debe derivarse del ViewModel state, no ser local.

---

## Tarea 03 · Extraer composables de chips propios de Paparcar

**Ticket:** `DS-CHIPS-001` (Design System)
**Rama sugerida:** `feature/DS-CHIPS-001-paparcar-chip-components`
**Prioridad:** Media | **Esfuerzo:** Pequeño–Medio
**Estado:** ✅ Done 2026-05-20
**Depende de:** Tarea 02 (para tener una referencia de uso real antes de extraer)

**Dos variantes:**
1. `PaparcarFilterChip(label, icon?, selected, onClick)` — chip textual con estado seleccionado/deseleccionado. Base: M3 `FilterChip` pero con colores, tipografía y shape del tema Paparcar.
2. `PaparcarAddChip(onClick)` — chip con "+" para añadir nuevo contenido. Base: `AssistChip` o `SuggestionChip` con ícono `Icons.Default.Add`.

**Dónde crear:** `ui/components/chips/` (nuevo subdirectorio) o junto al resto de componentes compartidos.

**Requisitos:**
- Respetan `MaterialTheme` de Paparcar — no hardcodear colores.
- Parámetros claros con KDoc mínimo en cada función pública.
- Preview `@Composable` para cada variante (selected / unselected / disabled).

---

## Tarea 04 · Adoptar nuevos chips en HomeScreen y resto de pantallas

**Ticket:** `DS-CHIPS-002`
**Rama sugerida:** `feature/DS-CHIPS-002-adopt-chips-everywhere`
**Prioridad:** Media | **Esfuerzo:** Pequeño
**Estado:** ✅ Done 2026-05-20
**Depende de:** Tarea 03 (DS-CHIPS-001 debe estar mergeado)

**Qué hacer:**
- Sustituir todos los usos de chips M3 inline (FilterChip / AssistChip / SuggestionChip) en:
  - `HomeScreen` — sección Zonas y cualquier otro chip de la modal.
  - `VehiclesScreen` — tras el refactor de Tarea 02.
  - Cualquier otra pantalla donde aplique (Settings, etc.).
- Sin regresiones visuales ni de comportamiento — cada chip reemplazado debe tener el mismo tamaño, estado y acción que el original.

---

## Tarea 05 · Reordenar subcomponentes de la card de vehículo

**Ticket:** `VEH-CARD-LAYOUT-001`
**Rama sugerida:** `feature/VEH-CARD-LAYOUT-001-compact-vehicle-card`
**Prioridad:** Baja | **Esfuerzo:** Trivial
**Estado:** ⚪ Pending

**Qué hacer:**
- Auditar `VehicleHeroCard` / `VehiclePageContent` — identificar qué elementos están desalineados o sobredimensionados.
- Reordenar y realinear para que la card sea más compacta.
- Solo cambios de layout (`padding`, `Arrangement`, `Alignment`, orden de elementos).
- Sin tocar datos, lógica, ViewModel ni strings.

---

## Tarea 06 · Zonas en HomeScreen — eliminar y editar

**Ticket:** `HOME-ZONES-EDIT-001`
**Rama sugerida:** `feature/HOME-ZONES-EDIT-001-delete-edit-zones`
**Prioridad:** Alta | **Esfuerzo:** Medio
**Estado:** ⚪ Pending

**Contexto:** en la modal de Home ya existe la fila de chips de zonas con "añadir zona" funcional. La lógica de eliminar y editar existe en repositorio/ViewModel pero no está expuesta en la UI.

**Qué hacer:**
- Exponer acción "eliminar zona": icono `×` en cada chip de zona (o long-press si encaja mejor con el diseño). Conectar con `ZonesIntent.DeleteZone(zoneId)` (o equivalente existente).
- Exponer acción "editar zona": long-press o ícono de lápiz en el chip → abre el bottom sheet / diálogo de edición existente pre-relleno con los datos de la zona.
- Verificar que `ZonesViewModel` / repositorio ya tienen las funciones — no reimplementar lógica de datos.
- UX: confirmar eliminar si la zona tiene aparcamiento activo vinculado (evitar borrado accidental).

---

## Tarea 07 · SettingsScreen — auditoría completa + tests

**Ticket:** `SETTINGS-AUDIT-001`
**Rama sugerida:** `feature/SETTINGS-AUDIT-001-fix-and-test`
**Prioridad:** Alta | **Esfuerzo:** Grande
**Estado:** ⚪ Pending

**Qué hacer:**
- Auditar cada opción de `SettingsScreen` e identificar cuáles no están operativas.
- Arreglar el cambio de idioma: la lista debe mostrar el nombre del idioma en su idioma nativo (ej. "Deutsch", "Français", "Italiano") no en el idioma de la UI.
- Verificar y corregir el resto de opciones (notificaciones, tema, perfil, logout, etc.).
- Tests: unitarios para `SettingsViewModel` y/o instrumentados para flujos críticos. Seguir el mismo patrón de tests del proyecto (fakes sobre mocks, naming `should_X_when_Y`).

---

## Tarea 08 · Compilación Release + Firebase App Distribution

**Ticket:** `RELEASE-001`
**Rama sugerida:** `chore/RELEASE-001-release-build-and-distribution`
**Prioridad:** Alta | **Esfuerzo:** Medio
**Estado:** ⚪ Pending

**Qué hacer:**
1. Revisar y completar la configuración de firma del APK/AAB (keystore, `signingConfigs` en `build.gradle.kts`).
2. Verificar ProGuard/R8: rules file, que no se rompan clases de dominio ni DTOs de Firestore.
3. Configurar Firebase App Distribution en `composeApp/build.gradle.kts` con grupos de testers.
4. Documentar el proceso en `docs/release/RELEASE-PROCESS.md` o un script `scripts/release.sh`.

**Notas:**
- Keystore: NO commitear el archivo `.jks` ni las credenciales — usar variables de entorno o `local.properties`.
- Verificar que `google-services.json` está configurado para el variant `release`.

---

## Tarea 09 · LoginScreen y RegisterScreen con BaseLogin

**Ticket:** `AUTH-SCREENS-001`
**Rama sugerida:** `feature/AUTH-SCREENS-001-login-register-ui`
**Prioridad:** Alta | **Esfuerzo:** Medio
**Estado:** 🔵 Branch ready

**Contexto:** `BaseLogin` es una librería de autenticación propia, publicada en JitPack desde `Documents/AndroidProjects/BaseLogin/`. Ya integrada en Paparcar (ver memoria `feedback_baselogin_jitpack_flow.md`). Los flujos de auth están conectados pero las pantallas pueden necesitar revisión visual.

**Qué hacer:**
- Analizar la API de `BaseLogin`: composables expuestos, callbacks, flujos email/contraseña y Google.
- Construir (o revisar) `LoginScreen` y `RegisterScreen` usando el tema de Paparcar — colores, tipografía, shape, botones con estilo Paparcar.
- Soportar: email + contraseña y Google Sign-In.
- Analizar la dificultad técnica de Sign In with Apple e indicar qué implicaría (scope: KMP Android+iOS, entitlements, Flow requerido). Documentar el análisis en este mismo ticket o en `docs/architecture/`.

**Notas:**
- Recordar que cambios en BaseLogin requieren publish a JitPack + bump de versión en Paparcar (ver memoria).

---

## Tarea 10 · Correcciones de flujo y UX en HomeScreen

**Ticket:** `HOME-UX-FIXES-001`
**Rama sugerida:** `feature/HOME-UX-FIXES-001-flow-corrections`
**Prioridad:** Alta | **Esfuerzo:** Medio
**Estado:** ⚪ Pending

**Cuatro fixes:**

### 10.1 — Cámara al volver a Home
Al volver a Home desde otra pantalla con un estado activo (vehículo aparcado o spot seleccionado), mover la cámara a la posición del estado activo en lugar de a la ubicación del usuario.
- Probablemente en `HomeViewModel` o en el `LaunchedEffect` de re-entrada a la pantalla.
- Usar "añadir zona" como referencia de flujo correcto si ya implementa algo similar.

### 10.2 — Reseteo de estado al completar acción
Al terminar una acción de estado (`AddingParking`, `MovingParking`, etc.), volver automáticamente a `HomeMode.Default`.
- Revisar qué flows actualmente no hacen el reset y añadirlo como efecto al confirmar la acción.
- "Añadir zona" ya funciona así — usarlo como referencia.

### 10.3 — Sincronización botón tipo de mapa
Verificar que el botón de cambio de tipo de mapa y el estado visual del mapa están siempre sincronizados. Si hay desincronización, identificar la causa (estado local vs ViewModel) y corregir.

### 10.4 — Jerarquía del botón "Report free spot"
Cuando haya un estado activo en la modal, reducir la jerarquía visual del botón "Report free spot": cambiar a estilo secondary/outline. Eliminar el "+" de su etiqueta.
- Derivar la condición de `HomeState` (cualquier estado activo de zona, vehículo, etc.).

---

## Orden de ejecución sugerido

1. **Tarea 02** — chips VehicleScreen (prerequisito de 03)
2. **Tarea 03** — extractar componentes DS chips
3. **Tarea 04** — adoptar chips en todas las pantallas
4. **Tarea 05** — compact vehicle card (trivial, se puede intercalar)
5. **Tarea 06** — zonas edit/delete HomeScreen
6. **Tarea 10** — HomeScreen UX fixes (4 correcciones)
7. **Tarea 07** — Settings audit + tests
8. **Tarea 09** — Login/Register screens
9. **Tarea 08** — Release + App Distribution
10. **Tarea 01** — ✅ ya hecho (pendiente solo 003 Play Store + 004 smoke test)
