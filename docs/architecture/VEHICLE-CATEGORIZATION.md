# Vehicle Categorization — Bidimensional Size + Carbody

**Ticket:** VEHICLE-CATEGORIZATION-001
**Shipped:** 2026-06-08
**Status:** ✅ Active

## Motivation

The original `VehicleSize` enum (MOTO/SMALL/MEDIUM/LARGE/VAN) collapsed length and width/height into a single linear scale. That left no room to express, for example, that a *long sedan* and a *wide SUV* both belong in different bays even though they share a length category — or that an MPV's height matters more than its footprint when picking a garage.

Now every CAR is tagged on two independent axes:

```
VehicleType (qué es) ─┬─ CAR ──────────► CarbodyType (10) ──► VehicleSize (length, derived)
                     └─ MOTORCYCLE / SCOOTER / BIKE ──► VehicleSize = MOTORCYCLE
```

## Model

### `VehicleSize` (5 length tiers)

| Value | Length range | Geofence radius |
|---|---|---|
| `MOTORCYCLE` | n/a | 60 m |
| `MICRO_SMALL` | < 4.10 m | 80 m |
| `MEDIUM_SUV` | 4.11–4.55 m | 80 m |
| `LARGE_SEDAN` | 4.56–5.00 m | 100 m |
| `VAN_HIGH` | > 5.00 m OR height > 1.82 m | 120 m |

### `CarbodyType` (10 body shapes)

| Body | sizeCategory | Min plaza width | Notes |
|---|---|---|---|
| `HATCHBACK_SMALL` | MICRO_SMALL | 2.20 m | – |
| `SUV_SMALL` | MICRO_SMALL | 2.20 m | – |
| `HATCHBACK_MEDIUM` | MEDIUM_SUV | 2.20 m | – |
| `SUV_MEDIUM` | MEDIUM_SUV | 2.40 m | Wide — surface WIDE_CAR alert |
| `SEDAN` | LARGE_SEDAN | 2.20 m | Long — surface LONG_CAR alert |
| `FAMILY_LONG` | LARGE_SEDAN | 2.20 m | Long — surface LONG_CAR alert |
| `SUV_LARGE` | LARGE_SEDAN | 2.40 m | Wide |
| `VAN_LIGHT` | VAN_HIGH | 2.20 m | requiresHighCeiling — HIGH_CEILING alert |
| `VAN_COMMERCIAL` | VAN_HIGH | 2.50 m | requiresHighCeiling + extra wide |
| `PICKUP` | VAN_HIGH | 2.50 m | requiresHighCeiling + extra wide |

`VehicleParkingRules` is built by `CarbodyType.getParkingRules()` and exposes `minPlazaWidthMeters`, `requiresHighCeiling` and a `ParkingAlertKey` enum that maps to translated copy in the UI layer (CarbodyLabels.kt).

## Inference

`VehicleCatalog.inferBodyType(brand, model)` runs:

1. **Exact-match** — a single **source-of-truth** table, `Map<String, List<Pair<String, CarbodyType>>>`, covering a **global** brand set (~69 brands, mainstream EU + US + Asia + premium + EV newcomers). Both the dropdown model list (`modelsFor`) and the inference read from this one table, so they can never drift — `VehicleCatalogTest` enforces that every offered model carries a body type.
2. **Pattern fallback** — keyword `contains` over the lowercased `"$brand $model"` string with rules ordered from most specific to most general. Lets us still classify *unknown* brands ("hilux" → PICKUP) or custom user inputs. Keywords are deliberately multi-character, model-distinct tokens (never bare digits or 2-letter fragments) so they don't false-positive on unrelated names.

`inferSize(brand, model)` is the convenience that drops the body dimension and returns just the size.

The registration ViewModel re-runs inference on `SelectBrand`, `SetCustomBrand`, `SelectModel`, `SetCustomModel` (the free-text intents now also pin `vehicleType = CAR` for parity with the dropdown intents, so the carbody card — not the non-car badge — surfaces). Manual override via `SetCarbody(body)` flips `isCarbodyManualOverride = true` and disables auto re-inference until brand/model change.

**Free-text default (VEH-FREETEXT-001).** When a CAR's typed brand/model matches neither the catalog nor any pattern, `inferBodyType` returns null and the ViewModel falls back to `DEFAULT_CAR_CARBODY = HATCHBACK_MEDIUM`. This guarantees the form is never blocked: the user always sees a carbody card with a "change" affordance and can refine the body (and thus size) via `CarbodyManualPicker`. The default is an *auto* fallback (`isCarbodyManualOverride = false`), so picking from the catalog or the manual picker still overrides it.

## Persistence

Both `sizeCategory` and `carbodyType` are stored as the enum name (String) on:

- **Room** — VehicleEntity, UserParkingEntity, SpotEntity (carbodyType nullable on all three).
- **Firestore DTOs** — VehicleDto.carbodyType (empty string when null), SpotDto.carbodyType (nullable), ParkingHistoryDto.carbodyType (nullable).

Room schema bumped to **v6** at the same time. `fallbackToDestructiveMigration(dropAllTables = true)` is active on both Android and iOS so no explicit migration is shipped — Firestore is the durable source of truth.

## Spot Fit (peek tri-state)

`computeSpotFit(spot, vehicle)` returns one of:

| State | Rule | UI |
|---|---|---|
| `UNKNOWN` | No vehicle or no spot size | Grey neutral chip |
| `OPTIMAL` | Same carbodyType on both sides | Green "Ideal para tu X" |
| `FITS` | Same size AND user width ≤ spot width, OR user smaller than spot | Amber "Apto — plaza para X" |
| `DOES_NOT_FIT` | Same size but user wider than spot, OR user larger than spot | Red "Muy justo para tu X" |

A secondary "Liberada por [icon] [carbody label]" subline appears underneath whenever `spot.carbodyType != null`, so the user sees what kind of vehicle freed the bay even when no fit comparison is meaningful.

## UI surfaces

- **`VehicleRegistrationScreen`** — `CarbodyInfoCard` replaces the legacy `VehicleSizeSelector`. Shows the inferred body + icon + a parking-advice chip (red when `requiresHighCeiling`). "Cambiar" button opens `CarbodyManualPicker` (10 options grouped under their size header).
- **`HomePeekHandle`** — `SpotFitRow` replaces the binary `CompatibilityRow`.
- **`HomeSizeFilterBar`** — still filters by `VehicleSize` (5 chips + "All"). Carbody filters are NOT exposed in the discovery feed.
- **`PaparcarMapMarkers`** — `VehicleBadgeMarker` still uses the `VehicleSize.icon` ImageVector fallback. Switching the marker to the `CarbodyType.icon` drawable is tracked separately (depends on the SVG asset drop in Fase 7).

## Strings

20 new keys added in EN + ES:

- `home_peek_spot_fit_optimal/fits/does_not_fit`, `home_peek_spot_left_by`
- `carbody_hatchback_small … carbody_pickup` (10)
- `parking_alert_high_ceiling / long_car / wide_car / standard`
- `vehicle_registration_carbody_section / auto_label / manual_label / change / picker_title / picker_dismiss`

The 7 secondary locales (de, fr, it, pt, nl, pl, ro) carry the EN copy as fallback per `feedback_i18n_all_locales`.

## Tests

- `CarbodyTypeRulesTest` — width + ceiling matrix per body.
- `VehicleInferencePatternTest` — fallback regex (hilux, kangoo, transporter, avant…).
- `SpotFitTest` — 7 cases covering each tri-state branch + UNKNOWN.
- `VehicleCatalogTest` — exact-match + pattern fallback assertions adapted.
- `VehicleRegistrationViewModelTest` — `SetCarbody` intent replaces legacy `SetSize`.

448 unit tests green.

## Open follow-ups

- **ICON-SVG-001** — replace the 10 `ic_car_*.xml` placeholders with proper Lucide/Material SVGs.
- **MARKERS-CARBODY-001** — use `CarbodyType.icon` (drawable) in `VehicleBadgeMarker` once SVGs are in place; will require converting the marker `Icon` call to a `painterResource` flow.
- **VehicleSizeExplainer-rewrite** — current explainer screen still references the legacy mental model; refresh copy + examples to the bidimensional taxonomy.
