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
**Estado:** ✅ Done 2026-05-20

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
**Estado:** 🔵 Parcial 2026-05-20 — delete ✅ (× en chip → HomeIntent.DeleteZone); edit ⚪ bloqueado: SaveZoneUseCase solo crea, no actualiza — requiere UpdateZoneUseCase en dominio primero

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
**Estado:** ✅ Done 2026-05-20

**Auditoría:**
- Idioma nativo en lista: ✅ ya correcto ("English", "Español", "Italiano"…)
- Tema / unidades / idioma: ✅ correctamente persistidos en DataStore; fluyen por AppViewModel → AppState → SettingsScreen como props (arquitectura correcta, no un bug)
- `appVersion` hardcoded "1.0.0": ✅ corregido — añadido `expect/actual appVersion` en Platform.kt (Android: BuildConfig.VERSION_NAME, iOS: NSBundle CFBundleShortVersionString); cargado en SettingsViewModel.loadFromPreferences()
- Tests: ✅ corregido constructor roto en buildVm (faltaba userParkingRepository); añadidos tests para MapType, MasterNotifications, refreshFromPreferences

---

## Tarea 08 · Compilación Release + Firebase App Distribution

**Ticket:** `RELEASE-001`
**Rama:** `chore/RELEASE-001-release-build-and-distribution` (creada 2026-05-22, **uncommitted WIP**)
**Prioridad:** Alta | **Esfuerzo:** Medio
**Estado:** 🔵 In progress — scaffold listo, bloqueado por audit de seguridad pre-beta

**Hecho 2026-05-22 (working tree, sin commit todavía):**
- `gradle/libs.versions.toml`: alias `firebaseAppDistribution` v5.0.0
- `composeApp/build.gradle.kts`: plugin App Distribution + `signingConfigs.release` desde `local.properties` + warning si faltan keys + bump `versionCode=2 / versionName="1.0.0-beta01"` + bloque `firebaseAppDistribution { groups="beta-paparcar", releaseNotesFile=$rootDir/distribution/release-notes.txt }`
- `composeApp/proguard-rules.pro`: keeps completas (Koin/Room/Firestore DTOs/Crashlytics/WorkManager/BaseLogin/kotlinx.serialization/Compose)
- `.gitignore`: `keystore/`, `firebase-service-account*.json`, `google-application-credentials*.json`
- `distribution/release-notes.txt` + `docs/release/RELEASE-PROCESS.md` (runbook completo)
- Verificado via `./gradlew :composeApp:tasks --all`: tareas `appDistributionUpload*` registradas correctamente

**Audit de seguridad (2026-05-22) — P0, resolver antes de subir la beta:**
1. Maps API key hardcoded en `composeApp/src/androidMain/AndroidManifest.xml:83` (`AIzaSyBpOJ6G-...`). Mover a `local.properties` vía `manifestPlaceholders["MAPS_API_KEY"]`.
2. Restringir Maps API key en Google Cloud Console: API → "Maps SDK for Android" only; Application → package `io.apptolast.paparcar` + SHA-1 release.
3. Auditar Firestore Security Rules — toda colección debe filtrar por `request.auth.uid`. Sin esto, la API key Firebase (en historial git) permite leer/escribir.

**Audit — P1 (opcional, valoración del usuario):**
4. Rotar Maps + Firebase API keys en GCP/Firebase Console.
5. `git filter-repo` para limpiar `google-services.json` del historial. Reescribe `origin/master` — sólo merece la pena si el repo se comparte.

**Acciones del usuario antes de retomar (paso 0):**
1. Generar keystore con `keytool` (comando en `docs/release/RELEASE-PROCESS.md §0.1`).
2. Añadir las 4 vars de signing a `local.properties` (`RELEASE_KEYSTORE_FILE`, `_PASSWORD`, `RELEASE_KEY_ALIAS`, `_PASSWORD`).
3. Firebase Console → App Distribution → crear grupo `beta-paparcar` + añadir emails de testers.
4. `npm install -g firebase-tools && firebase login` para que el plugin Gradle pueda subir desde tu máquina.

**Detalle completo:** memoria `project_release001_in_progress.md` + `reference_api_keys_inventory.md`.

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
**Estado:** ✅ Done 2026-05-20

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

---

# Sesión 2026-05-21 — Bug crítico + DS pass

Continuación de la sesión del 2026-05-20. El usuario reportó un bug funcional (idioma) y pidió consolidar bases de diseño antes de seguir saltando entre composables.

## Tarea 11 · BUG-LANG-001 · Cambio de idioma no surte efecto

**Ticket:** `BUG-LANG-001`
**Rama:** `feature/ICON-LOGO-PALETTE-001-dark-theme-repaint` (in-progress; mover si se quiere ticket aparte)
**Prioridad:** Crítica | **Esfuerzo:** Trivial
**Estado:** 🔵 Branch ready 2026-05-21

**Root cause:** `MainActivity` extiende `ComponentActivity`, no `AppCompatActivity`. `AppCompatDelegate.setApplicationLocales()` persiste el locale pero solo auto-recrea Activities que usan `AppCompatDelegate`. Como Compose Multiplatform Resources lee strings desde la `Configuration` del Context en cada composición, sin recreate la UI sigue mostrando los strings del locale anterior.

**Fix:** `LocaleApplier.android.kt` ahora llama `ActivityHolder.getCurrentActivity()?.recreate()` tras `setApplicationLocales`. Guard `current == locales` evita recreate redundante si el usuario re-selecciona el idioma actual. iOS sigue siendo no-op (per-app language en iOS requiere flujo separado con `AppleLanguages` en `NSUserDefaults` + restart, fuera de scope).

## Tarea 12 · DS-BORDERS-001 · Tokens de borde + reducir "neon radioactive"

**Ticket:** `DS-BORDERS-001`
**Prioridad:** Media | **Esfuerzo:** Pequeño
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `ui/theme/Borders.kt` nuevo: `PapBorders.thin (1dp)`, `medium (1.5dp)`, `strong (2dp)` + `DEFAULT_OUTLINE_ALPHA = 0.4f` + helper `outlineSubtle` `BorderStroke`.
- `VehicleHeroCard` ya no usa `cs.primary` neon en el border de la card activa: ahora siempre `outline @ 0.4f`. La señal de "activo" vive en el fondo verde tintado + `ActiveBadge`. Grosor `BorderStroke(PapBorders.thin, …)`.
- Constante muerta `INACTIVE_BORDER_ALPHA` eliminada.

## Tarea 13 · DS-CHIPS-CONSOLIDATE-001 · Una sola base de chip

**Ticket:** `DS-CHIPS-CONSOLIDATE-001`
**Prioridad:** Media | **Esfuerzo:** Pequeño
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `PaparcarFilterChip` reescrito como base única: añadido slot opcional `trailingIcon` + `onTrailingClick` (para el `×` de eliminar zona). Borde unselected `outline @ 0.4f`, selected `+0.2f` — sin `cs.primary` neon.
- `ZoneChip` (HomeZoneChips.kt) ahora es wrapper finísimo sobre `PaparcarFilterChip(trailingIcon = Icons.Outlined.Close, onTrailingClick = onDelete)`. Quitado `BorderStroke(1.dp)` propio + constantes muertas (`CHIP_DELETE_DP`, `CHIP_BORDER_ALPHA`, `DELETE_ICON_ALPHA`, etc.).
- `PaparcarAddChip` permanece como variante con contrato distinto (sin label, solo "+").

## Tarea 14 · DS-TYPO-001 · Jerarquía tipográfica

**Ticket:** `DS-TYPO-001`
**Prioridad:** Media | **Esfuerzo:** Pequeño
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `VehicleHeroCard` (VehiclePageContent.kt:207): nombre de vehículo `titleSmall` (14sp) → `titleMedium` (18sp) Bold. Ahora domina sobre el badge "Vehículo activo" (labelSmall 11sp).
- `WeeklyActivityCard` (HistoryWeeklyChart.kt:86): título "Actividad semanal" `titleSmall` → `titleMedium`. Subido un escalón.
- `WeeklyActivityCard` card surface: `surface` → `surfaceContainerHigh` (consistencia con el nuevo modelo de superficies).
- Pattern uppercase de `ActiveSectionHeader` ("APARCADO ACTUALMENTE") se mantiene como referencia documentada — no se eleva todavía a token global (deferred, ver si lo pide en próxima sesión).

## Tarea 16 · DS-CHIPS-ICON-COLOR-001 · Iconos de chip siempre en primary

**Ticket:** `DS-CHIPS-ICON-COLOR-001`
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `PaparcarFilterChip` tinta `leadingIcon` siempre en `cs.primary` (verde) salvo si `!enabled`. Antes era `contentColor` blanco en unselected → los iconos de zona se perdieron al migrar a la base unificada. Ahora se recupera el verde original como convención para todos los chips con icono.

## Tarea 17 · DS-CHIPS-CONSOLIDATE-002 · Add chip + Vehicle tab pill alineados

**Ticket:** `DS-CHIPS-CONSOLIDATE-002`
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `PaparcarAddChip`: border width `1.5.dp` → `PapBorders.thin`. Mantiene `cs.primary` (afirmativa de "acción") con alpha 0.6f. Documentado por qué difiere de la base neutral.
- `VehicleTabPill` (VehiclesScreen.kt): migrado a tokens DS — quitado `cs.primary` neon en selected; ahora usa `outline @ 0.4/0.6f` igual que `PaparcarFilterChip`. Icono tintado `cs.primary` (verde) para alinear con convención. Unselected bg pasa a `surfaceContainerHigh` (antes Transparent). Constantes muertas (`ADD_PILL_BORDER_ALPHA`, `TAB_INACTIVE_BORDER_ALPHA`, `TAB_INACTIVE_FG_ALPHA`) eliminadas.

## Tarea 18 · VEH-HERO-LAYOUT-001 · Refactor card de vehículo (título sin clipping)

**Ticket:** `VEH-HERO-LAYOUT-001`
**Estado:** 🔵 Branch ready 2026-05-21

**Problema:** La pill "VEHÍCULO ACTIVO" se renderizaba inline en la fila del título y robaba ~40% del ancho — los nombres de marca/modelo largos se cortaban con ellipsis.

**Hecho:** Refactor de `VehicleHeroCard`:
- Fila 1 ahora es `icon | título (weight 1f) | overflow menu` — el título posee toda la fila menos el icono e ícono de menú.
- Fila 2 nueva: `sizeLabel (weight 1f) | ActiveStatusInline / SetActiveButton`. La señal "activo" baja a metadata, donde compite solo con el size label.
- Sustituida la antigua `ActiveBadge` (pill rellena en `cs.primary` con texto onPrimary) por `ActiveStatusInline` — dot verde + texto "VEHÍCULO ACTIVO" en `cs.primary`, mucho más sobrio. La señal "activo" sigue presente vía: bg de la card tintado, icono primary, y este tag.

## Tarea 15 · DS-SURFACES-001 · Background más claro en Vehicle/Settings

**Ticket:** `DS-SURFACES-001`
**Prioridad:** Media | **Esfuerzo:** Trivial
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `VehiclesScreen`: Scaffold `containerColor = surfaceContainer` (alineado con sheet de Home).
- `SettingsScreen`: Scaffold `containerColor = surfaceContainer` + TopAppBar `scrolledContainerColor = surfaceContainer`. Cards internas bumped a `surfaceContainerHigh` (8 surfaces) para preservar la jerarquía de elevación tras subir el fondo.
- `StatCard` y `VehicleHeroCard` inactivo: `surface` → `surfaceContainerHigh` por la misma razón.

---

# Sesión 2026-05-21 (Phase A) — DS unification across Home

Continuación del pase de DS. El usuario pidió: (a) unificar Home al patrón opaco que ya viven V/S, (b) `PaparcarAddChip` siempre círculo perfecto, (c) rediseñar el CTA "Avisar plaza libre", (d) unificar el estilo de section headers (TU COCHE vs "Plazas libres" estaban descoordinados), (e) anclar la tipografía en la de Vehicle screen, (f) groundwork para multi-parking en Home (deferred a Phase B). Toda esta sesión es Phase A — solo capa UI, sin tocar dominio.

## Tarea 19 · DS-SURFACES-002 · Home opaque surfaces

**Ticket:** `DS-SURFACES-002`
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `HomeParkingEmptyCard`: `surfaceVariant @ 0.4f` → `surfaceContainerHigh`. Borde a `PapBorders.thin / DEFAULT_OUTLINE_ALPHA`. Inner icon box `surface` → `surfaceContainer`. Constante muerta `EMPTY_CARD_BG_ALPHA` retirada.
- `HomeZonesEmptyCard`: misma migración (`surfaceVariant @ 0.4f` → `surfaceContainerHigh`, inner `surface` → `surfaceContainer`). Retiradas `EMPTY_CARD_BG_ALPHA`, `EMPTY_CARD_BORDER_ALPHA` y constante muerta `CHIP_CORNER_DP` (residuo del ZoneChip que ahora delega a `PaparcarFilterChip`).
- `HomeEmptySpots` y `HomeEmptyFilteredSpots`: idénticamente migrados — `surfaceContainerHigh` + thin outline. Constante muerta `EMPTY_BG_ALPHA` retirada.
- Tras este pase, todo Home comparte el modelo de superficies opaco con V/S: page `surfaceContainer`, cards `surfaceContainerHigh`, inner boxes `surfaceContainer`.

## Tarea 20 · DS-CHIPS-CONSOLIDATE-003 · `PaparcarAddChip` siempre circular

**Ticket:** `DS-CHIPS-CONSOLIDATE-003`
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `PaparcarAddChip` reescrito: API simplificada a `(onClick, modifier, contentDescription, iconSize, contentPad)`. Eliminados `shape`/`contentPadding` (siempre `CircleShape`, sin alternativa). El diámetro se deriva de `iconSize + 2·contentPad`, manteniendo el círculo perfecto para cualquier tamaño.
- Callsites actualizados: `HomeZoneChips` (16+8+8=32dp para igualar la fila de zone chips); `VehiclesScreen` tab strip mantiene 16+8+8=32dp para alinear con el alto del pill.
- KDoc revisado: explica el sizing model (caller controla peso visual via `iconSize`) y el contrato de igualar el alto de otras chips sumando ambos parámetros.

## Tarea 21 · DS-CTA-REPORT-001 · Rediseño "Avisar plaza libre"

**Ticket:** `DS-CTA-REPORT-001`
**Estado:** 🔵 Branch ready 2026-05-21

**Hecho:**
- `HomeReportSpotCard` antes: `primaryContainer @ 0.55f` bg + `cs.primary @ 0.5f` border (chillón, rompía el sistema de superficies) y pill "Notify the community" al final.
- Ahora: mismo molde que `HomeParkingRow` — `surfaceContainerHigh` + thin neutral outline + icon box `surfaceContainer` con icono `cs.primary` (Campaign) + título `titleSmall / Bold` + subtítulo `labelSmall` + **trailing `+` icon primary** en lugar del chevron del parking row. Sin pill — el "+" solo basta como señal de acción, "Notify the community" era redundante.
- Constantes muertas retiradas: `PRIMARY_CARD_BG_ALPHA`, `PRIMARY_CARD_BORDER_ALPHA`, `EMPTY_BG_ALPHA`, `PRIMARY_CARD_PILL_RADIUS_DP`.
- String `home_report_action` ("Notify the community") eliminada de las 9 locales (`values/` + 8 traducidas).

## Tarea 22 · DS-SECTION-HEADER-001 · `PapSectionHeader` canónico

**Ticket:** `DS-SECTION-HEADER-001`
**Estado:** 🔵 Branch ready 2026-05-21

**Problema:** "TU COCHE" salía en uppercase pero "Plazas libres cerca de ti" no, aun usando ambos el mismo composable local `HomeSectionHeader` — el uppercase venía del callsite. Además, History/Vehicles/Settings cada uno tenía su propia receta de header (mezclando `labelLarge`/`labelMedium`/`labelSmall`, alphas distintos y trackings distintos).

**Hecho:**
- Nuevo `ui/components/PapSectionHeader.kt`: receta canónica `uppercase + labelMedium + ExtraBold + 1sp tracking + onSurfaceVariant`. La transformación uppercase vive dentro del componente — no se delega al caller. Color override opcional para variantes (danger, primary).
- Variante `PapSectionHeaderRow` con slots `leading`/`trailing` para casos como el dot pulsante del header activo en History o un badge de count.
- Migrados:
  - `HomeSheetContent`: ambos headers ("TU COCHE" + "Plazas libres cerca") ahora consistentes. Composable local `HomeSectionHeader` eliminado. Previews android actualizados.
  - `HistoryComponents`: `ActiveSectionHeader` ahora delega a `PapSectionHeaderRow` con `leading = PulsingDot` + `color = cs.primary`. `HistorySectionHeader` (no usado en ningún sitio) eliminado.
  - `SettingsScreen`: `SectionHeaderMuted` y `SectionHeaderDanger` ahora son thin wrappers sobre `PapSectionHeader` con su padding original. Constantes locales (`SECTION_LABEL_ALPHA`, `SECTION_LABEL_TRACKING_SP`) conservadas porque se usan también en el `memberSinceLine` del profile card.

## Tarea 23 · DS-TYPO-002 · Tipografía baseline (auditoría)

**Ticket:** `DS-TYPO-002`
**Estado:** ⚪ Pending (documentación)

**Recomendación de baseline (anclada en Vehicle screen):**
- **TopBar title:** `headlineSmall (24sp) + ExtraBold + tracking -0.5sp` — la firma "Vehicle screen". Aplicar en Settings/History si quieren la misma fuerza.
- **Section headers:** receta canónica de `PapSectionHeader` (labelMedium uppercase + ExtraBold + 1sp + onSurfaceVariant). Color override para variantes.
- **Card titles:** `titleMedium (18sp) + Bold`. Para densas, `bodyMedium (14sp) + SemiBold`.
- **Card metadata:** `bodySmall (12sp) + onSurface @ 0.55f`.
- **Active tags:** `labelSmall (11sp) + ExtraBold uppercase + cs.primary`.

Documentar como código vivo no escala — se ha codificado en `PapSectionHeader` (headers) y `ActiveStatusInline` (active tag). El resto queda como guía mental hasta que aparezca el segundo callsite divergente.

## Tarea 24 · MULTI-PARKING-HOME-001 · Multi-parking en Home (Phase B — deferred)

**Ticket:** `MULTI-PARKING-HOME-001`
**Estado:** ⚪ Pending — explícitamente diferido fuera de Phase A

**Contexto:** el usuario apuntó que un usuario puede tener varios vehículos, cada uno con su sesión de parking activa. Home debería mostrar todos los vehicles parqueados — el activo + cualquier otro con sesión activa. El groundwork de UI ya existe (`HomeState.parkedVehicles: List<ParkedVehicleView>`, `ObserveParkedVehiclesUseCase` devolviendo `Flow<List<ParkedVehicleView>>`) pero el use case está hardcoded para 0..1 elementos.

**Pendiente (Phase B):**
1. Cambiar `UserParkingRepository.observeActiveSession(): Flow<UserParking?>` → `Flow<List<UserParking>>`.
2. Auditar todos los consumidores (Confirm/Release/Geofence/Notification flows) para asegurar que no asumen una única sesión activa.
3. Revisar schema Room — el campo `isActive` admite múltiples filas TRUE, pero el flow actual filtra por una sola.
4. Actualizar Firestore queries (collection `userParkings/{userId}/active`).
5. UI: `HomeSheetContent.parkingSection` ya itera potencialmente sobre múltiples filas — solo hay que cambiar el `state.userParking != null` por `state.parkedVehicles.isNotEmpty()` y `itemsIndexed(state.parkedVehicles)`.

Phase B es un cambio de contrato del dominio + posible migración Room — no mezclarlo en este branch de DS pass.
