# Vehicles — multi-marker + naming sprint — 2026-05-19

Seven tickets agreed with the user on 2026-05-19. Triggers:

1. The Home map only renders **one** parked-car marker even when the user has multiple vehicles with active sessions.
2. The current parked marker (green teardrop) collides visually with `HIGH` reliability spot markers (also green teardrop).
3. Vehicle registration captures `brand` + `model` as free text and offers no way to give the car a friendly name.
4. The "active session" abstraction is overloaded: non-BT vehicles need it (the coordinator only tracks one car), BT-paired vehicles don't (BT connectivity itself signals parked-or-not, per device). Treating both the same way blocks multi-marker because non-BT can never have >1 active session.

The set splits into three themes that can ship independently:

- **Active-session invariant** (VEH-PARK-STATE-001) — domain rule change, prerequisite to the markers bundle.
- **Markers** (VEH-MARKERS-001..003) — UI / state work consuming the new domain rule.
- **Naming + registration** (VEH-NAME-001, VEH-REG-001, VEH-MODAL-001) — touches the `Vehicle` model and the registration flow.

## Status legend
✅ **Done** — merged to master (commit/branch noted).
🔵 **Branch ready** — work complete on its branch, awaiting review/merge.
⚪ **Pending** — not started.
🟡 **Blocked** — waiting on the user (design call, product call, etc.).

---

## 1. `feature/VEH-PARK-STATE-001-active-session-non-bt-only` — ⚪ Pending

**Priority:** High — prerequisite for VEH-MARKERS-001. Without this rule, "multi-marker" can't model the BT case correctly.
**Effort:** Medium — touches detection strategies, ConfirmParkingUseCase, mapper, and the marker data flow.

**Rule (agreed 2026-05-19):**
- A `UserParking` row with `isActive = true` can only ever exist for a vehicle whose `bluetoothDeviceId == null`. The coordinator-driven strategy can track exactly one car at a time, so this is naturally an at-most-one invariant.
- BT-paired vehicles **never** produce `isActive = true` rows. Their sessions are written as completed (`isActive = false`) with `detectionMethod = BLUETOOTH` for history. The signal "car X is parked right now" is derived at runtime from BT connectivity state, not from the DB flag.

**Why:** Treating both detection sources the same way (everyone gets `isActive=true`) forced a single-active-session model and blocked multi-marker. Decoupling them lets each BT car carry its own parked/unparked state via its own Bluetooth connection, independent of the coordinator's single-track limit.

**Where:**
- `androidMain/.../BluetoothDetectionStrategy.kt` — on disconnect, currently calls `ConfirmParkingUseCase` which writes `isActive=true`. Change so BT sessions are written `isActive=false` (or call a new `RecordBluetoothParkingUseCase`).
- `CoordinatorDetectionStrategy.kt` / `ParkingDetectionCoordinator.kt` — unchanged (still writes `isActive=true` for the single tracked car).
- `domain/usecase/parking/ConfirmParkingUseCase.kt` — split or parameterise so callers say which kind of session they're writing.
- `domain/usecase/parking/DetectDepartureUseCase.kt` — currently flips `isActive` to false on departure for the coordinator path. For the BT path there's nothing to flip (rows already false); the departure semantic for BT is "BT just reconnected".
- New: `domain/usecase/parking/ObserveParkedVehiclesUseCase.kt` — fuses two sources into a `List<ParkedVehicleView>`:
  - **Non-BT branch:** `UserParkingRepository.observeActiveSession()` → 0 or 1 view.
  - **BT branch:** for each `Vehicle` with `bluetoothDeviceId != null`, observe its current BT connectivity (via `BluetoothStateProvider` or equivalent). When disconnected AND there's a recent session row, emit a `ParkedVehicleView(vehicleId, latestSession.location, BLUETOOTH)`.
  - Merge both branches into one flow.
- New: `domain/model/ParkedVehicleView.kt` — DTO with `vehicleId`, `location`, `detectionMethod`, `sessionId?`. Pure-Kotlin in `commonMain`.

**Marker consumer:** `HomeViewModel` switches from `userParking: UserParking?` to `parkedVehicles: List<ParkedVehicleView>` driven by `ObserveParkedVehiclesUseCase`. This is what VEH-MARKERS-001 will iterate over.

**Privacy / migration:**
- No DB migration strictly needed — `isActive` column already exists. But there may be legacy `isActive=true` rows on BT-paired vehicles from before this rule. A one-shot startup cleanup: `UPDATE parking_sessions SET is_active = 0 WHERE vehicle_id IN (SELECT id FROM vehicles WHERE bluetooth_device_id IS NOT NULL)`. Document as a one-time guard in `AppDatabase` callback or in the bootstrap flow.
- Firestore: same backfill consideration if BT sessions ever sync (most likely not, since BT pairing is on-device only — verify in `ParkingHistoryDto`).

**Docs:**
- Update `docs/detection/PARKING-DETECTION.md` per the "document parking-detection changes" feedback rule. State the invariant explicitly: "Active sessions are coordinator-owned, BT parked-state is BT-owned".

**Multi-BT scope question:** today's `BluetoothDetectionStrategy` is built around one paired device (the default vehicle's). Tracking BT state for N paired vehicles needs N listeners. **Defer if needed** — for v1, only the default vehicle's BT may be tracked; secondary BT cars degrade to "no live marker until you switch them to default". Document this limitation in the ticket and revisit if user pushes back.

---

## 2. `feature/VEH-MARKERS-001-multi-parking-markers` — ⚪ Pending

**Priority:** High — without this, vehicles 2..N are invisible on the map.
**Effort:** Small (after VEH-PARK-STATE-001 ships).
**Depends on:** VEH-PARK-STATE-001 (consumes its `ObserveParkedVehiclesUseCase`).

**Where:**
- `presentation/home/HomeState.kt:42` — `val userParking: UserParking? = null` (single value).
- `presentation/home/HomeViewModel.kt` — wires `observeActiveSession()` into the single field.
- `ui/components/PaparcarMapMarkers.kt:95-170` — `MyVehicleMarker()`.
- `presentation/home/HomeMap*.kt` (or wherever markers are placed) — single marker call site.

**What:** Render N parked-car markers, one per `ParkedVehicleView` (combined non-BT active session + BT-disconnected vehicles, per VEH-PARK-STATE-001).

**Fix:**
- Replace `HomeState.userParking: UserParking?` with `parkedVehicles: List<ParkedVehicleView> = emptyList()`. Keep the existing `userParking: UserParking?` derivation as a computed property if other call sites still need "is my main car parked?" — drop it once those callers migrate.
- `selectedItemId` for parked cars becomes `"__parking__:<vehicleId>"`. Update `isParkingSelected` helper to match a prefix, and add `selectedParkedVehicle: ParkedVehicleView?` derived from the list (similar shape to the existing `selectedSpot`).
- Map render: iterate `parkedVehicles` and place one badge marker per row (see VEH-MARKERS-002 for the new shape).
- Peek/sheet implications: with N parked cars, the "parking row" becomes a section with N rows (one per vehicle), each tappable. Card title comes from VEH-MODAL-001's `displayName` helper.

**Migration concern:** existing `HomeState.PARKING_ITEM_ID = "__parking__"` sentinel needs to encode the vehicleId. Don't break `selectedSpot` (lives at `HomeState.kt:91-101`).

---

## 3. `feature/VEH-MARKERS-002-parked-badge-shape` — ⚪ Pending

**Priority:** High — visual clarity. Without it, mis coches look like HIGH-reliability spots.
**Effort:** Small.

**Where:** `ui/components/PaparcarMapMarkers.kt:95-170` — `MyVehicleMarker()` teardrop.

**What:** Move the parked-car marker away from the spot-teardrop family so it never gets confused with `FreeSpotMarker(reliability=HIGH)`. Adopt a **circular badge** (~48 dp) with:
- Solid fill (forest dark) + per-vehicle accent ring (1.5–2 dp) — color comes from VEH-MARKERS-003.
- Car glyph in the centre, reusing the simplified path from `drawCarIcon()` (lines 691-726) or upgrading to the `VehicleSize.icon` silhouette so the shape hints at the vehicle.
- Ground shadow ellipse kept for depth parity with spot markers.
- Selection halo (white + dark strokes) reusable as-is.

**Why not just change colour?** Two vehicles of the same colour would collide; daltónicos + zoom-out both fail. Shape carries the spot-vs-parking signal, colour carries the inter-vehicle signal. (See "Razonamiento" in the conversation that opened this ticket.)

**Coordination:** Ship together with VEH-MARKERS-001 or right after — shipping shape alone (single marker) gives a worse UX than today because the green-teardrop affinity for "my car" is what users already learned.

---

## 4. `feature/VEH-MARKERS-003-per-vehicle-accent-color` — ⚪ Pending

**Priority:** Medium — needed to differentiate vehicle 1 from vehicle 2 visually. Useless without VEH-MARKERS-001.
**Effort:** Trivial (palette utility + lookup at render time).

**Where:** New `ui/icons/VehicleAccentPalette.kt` (or extend `PaparcarColors`). Consumed by the badge marker from VEH-MARKERS-002.

**What:** Assign each vehicle an accent colour without storing it in the model. Algorithm:

```kotlin
private val PALETTE = listOf(
    Indigo500, Teal500, Orange500, Magenta500, Cyan500, Amber500,
)
fun Vehicle.accentColor(allVehicles: List<Vehicle>): Color {
    val idx = allVehicles.sortedBy { it.id }.indexOf(this).coerceAtLeast(0)
    return PALETTE[idx % PALETTE.size]
}
```

Use `.sortedBy { it.id }` (or createdAt if we ever add it) so the assignment is **stable** across recompositions and Room/Firestore reads. Don't sort by `isDefault` or anything mutable — the colour must not shift when the user changes which car is the active one.

**Defer:** letting the user override the colour in vehicle registration / vehicle settings. Add `colorHex: String?` to `Vehicle` then, with a fallback to the palette. Filed as a follow-up not in this sprint.

---

## 5. `feature/VEH-NAME-001-optional-vehicle-name` — ⚪ Pending

**Priority:** High — unlocks the "Coche 1 / Coche 2" default the user asked for and removes the ugly "Vehículo sin nombre" empty state.
**Effort:** Medium — touches model + Room migration + Firestore mapper + form + display sites.

**Field semantics (agreed 2026-05-19):**
- `name` is **conditionally required**: required only when **both** `brand` and `model` are empty. If the user fills brand and/or model, name stays optional.
- The field placeholder is dynamic: **`"Coche N"`** where `N = existingVehicles.count() + 1`. That same value is what gets persisted-or-derived if the user submits everything blank — keeping placeholder, default, and display fallback consistent.

**Where:**
- `domain/model/Vehicle.kt:13-28` — add `val name: String? = null`.
- `data/database/entity/VehicleEntity.kt` — add `name: String?` column.
- `data/database/AppDatabase.kt` — bump version + add a non-destructive migration (or destructive fallback, consistent with how we handled the zones table bump).
- `data/mapper/VehicleMapper.kt` — wire `name` in both directions.
- `data/firestore/dto/VehicleDto.kt` — add `name: String?` and round-trip mappers.
- `presentation/vehicle/VehicleRegistrationState.kt` — add `name: String = ""`, plus a derived `defaultNamePlaceholder: String` computed from `existingVehicleCount + 1`.
- `presentation/vehicle/VehicleRegistrationViewModel.kt` — inject `existingVehicleCount` (one-shot read from `VehicleRepository.observeVehicles().first().size` at `init`); expose it in state. Add validation:

  ```kotlin
  val canSubmit: Boolean
      get() = sizeCategory != null &&
              (name.isNotBlank() || brand.isNotBlank() || model.isNotBlank())
  ```

  On submit, if all three are blank but the user *somehow* bypassed validation (shouldn't happen with `canSubmit` gating the CTA), persist the placeholder `"Coche $defaultNamePlaceholderIndex"` as the explicit `name` — so the saved row is unambiguous and the displayName helper has zero work to do.
- `presentation/vehicle/VehicleRegistrationScreen.kt` (lines ~141+) — `OutlinedTextField` for name with `placeholder = { Text(state.defaultNamePlaceholder) }`. Order: **name first**, then brand/model, then size. Mark the field as required-when-empty via `isError = state.brand.isBlank() && state.model.isBlank() && state.name.isBlank() && state.hasInteractedWithForm` (avoid showing error on first paint).
- New helper `Vehicle.displayName(allVehicles: List<Vehicle>): String` (e.g. in `domain/model/VehicleExtensions.kt`) implementing the priority:

  ```
  name?.takeIf { it.isNotBlank() }
    ?: listOfNotNull(brand, model).joinToString(" ").takeIf { it.isNotBlank() }
    ?: stringResource(Res.string.vehicle_default_name_format, allVehicles.sortedBy { it.id }.indexOf(this) + 1)
  ```

  The "Coche N" terminal fallback only matters for legacy rows that predate this ticket (no name, no brand, no model). New rows always get an explicit `name` thanks to the submit-time persistence rule above.
- `composeResources/values/strings.xml` — new key `vehicle_default_name_format` = `"Coche %d"` (ES) / `"Car %d"` (EN) / parity in IT/PT/FR/DE/NL/PL/RO. Also a new key `vehicle_registration_name_label` and `vehicle_registration_name_hint_required` ("Pon un nombre o rellena marca y modelo") for the error state.
- All current callsites of the old `joinToString(" ").ifBlank { my_car_unnamed_vehicle }` pattern (VehiclePageContent.kt:161-163 and any others) replaced with `vehicle.displayName(state.vehicles)`.
- Retire the `my_car_unnamed_vehicle` string everywhere once unreferenced; it should disappear from all 9 locale files.

**Privacy note:** The current `Vehicle` privacy comment says only `brand`/`model` are shared on spots when `showBrandModelOnSpot=true`. Decide explicitly whether `name` is **private always** (recommended — it's a personal label) and document it in the KDoc.

---

## 6. `feature/VEH-REG-001-brand-model-dropdowns` — ⚪ Pending

**Priority:** Medium — usability win, not blocking.
**Effort:** Medium — needs a brand/model catalog file and dependent-dropdown logic.

**Where:**
- New `presentation/vehicle/data/VehicleCatalog.kt` — `Map<String, List<String>>` of brand → models. Start with ~30 popular brands (Toyota, Honda, BMW, Mercedes, Audi, VW, Seat, Renault, Peugeot, Citroën, Ford, Opel, Hyundai, Kia, Nissan, Mazda, Fiat, Skoda, Dacia, Cupra, Tesla, Volvo, Mini, Jeep, Suzuki, Mitsubishi, Lexus, DS, Land Rover, Porsche). Models: top ~5–10 per brand. **Out of scope:** scraping a complete catalog — that's an over-engineering trap. The "Other" option covers everything else.
- `presentation/vehicle/VehicleRegistrationScreen.kt` — replace the two `OutlinedTextField` instances for brand/model with two `ExposedDropdownMenuBox`. Brand dropdown shows catalog keys + `"Otra…"`. Model dropdown is enabled only when a brand is selected and shows `catalog[brand].orEmpty() + "Otro…"`. Selecting `Otro…` in either reveals a free-text `OutlinedTextField` below — covers brands/models not in the catalog and the "I have a weird import" edge case.
- `presentation/vehicle/VehicleRegistrationViewModel.kt` — new intents: `SelectBrand(String?)`, `SelectModel(String?)`, `SetCustomBrand(String)`, `SetCustomModel(String)`. State adds `availableBrands`, `availableModels`, `isBrandOther`, `isModelOther`.
- **Coordination with VEH-NAME-001 validation:** the dropdowns must hook into `canSubmit` correctly — clearing brand/model (going back to `Otra… → empty`) should re-evaluate the "name required" rule and re-enable the error state on the name field if applicable.

**Watch out for:** the `ExposedDropdownMenu` extension lives on `ExposedDropdownMenuBoxScope` — don't prefix it with `androidx.compose.material3.` (regression we already hit in Settings).

---

## 7. `feature/VEH-MODAL-001-show-vehicle-name-in-modals` — ⚪ Pending

**Priority:** Medium — completes the naming UX. Cheap follow-up to VEH-NAME-001.
**Effort:** Small.

**Where every car composable renders today** (audit findings):
- **VehicleHeroCard** (`presentation/vehicle/VehiclePageContent.kt:152-230`) — already shows brand+model fallback; swap to `vehicle.displayName(allVehicles)`.
- **Home parking peek state** — `HomePeekHandle` family. Currently shows the silhouette + status; add `Text(vehicle.displayName(state.vehicles), style = titleMedium)` above the action row.
- **Home parking sheet row** — same treatment as the peek.
- **Selected parking marker label** — when a parked car is selected on the map, surface the name in the peek so the user knows *which* car they're looking at, especially once VEH-MARKERS-001 ships and there are multiple.
- **AddParkingPin / AddingParking peek** (from the deferred ADD-PARKING-PIN backlog) — show the active vehicle's name when entering that mode.

**Note:** This ticket assumes VEH-NAME-001 has shipped (otherwise `displayName` doesn't exist). If we want VEH-MODAL-001 to ship earlier, downgrade the call to `listOfNotNull(brand, model).joinToString(" ").ifBlank { stringResource(my_car_unnamed_vehicle) }` and refactor when VEH-NAME-001 lands.

---

## Suggested sprint order

1. **VEH-PARK-STATE-001** first, standalone — domain-rule + detection-strategy change. Deserves its own smoke test on a BT-paired device.
2. **VEH-MARKERS-001 + VEH-MARKERS-002 + VEH-MARKERS-003** as a single branch (`feature/VEH-MARKERS-multi-and-shape`). Depend on #1's `ObserveParkedVehiclesUseCase`. Interdependent enough that splitting commits per ticket is fine but splitting branches creates a worse intermediate UX.
3. **VEH-NAME-001** standalone — model change, deserves its own branch + smoke test.
4. **VEH-REG-001** + **VEH-MODAL-001** on a second naming branch — they reinforce each other and share the registration screen.

Total estimate: ~4–6 working sessions.
