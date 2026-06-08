# VEHICLE-CATEGORIZATION-001 — Bidimensional size + carbody

**Status:** ✅ Shipped 2026-06-08
**Branch:** `feature/VEHICLE-CATEGORIZATION-001-bidimensional-size-and-body`
**Architecture spec:** [`docs/architecture/VEHICLE-CATEGORIZATION.md`](../architecture/VEHICLE-CATEGORIZATION.md)

## Summary

Replaced the linear `VehicleSize` (MOTO/SMALL/MEDIUM/LARGE/VAN) with a two-axis taxonomy:

- `VehicleSize` (5 length tiers: MOTORCYCLE, MICRO_SMALL, MEDIUM_SUV, LARGE_SEDAN, VAN_HIGH)
- `CarbodyType` (10 body shapes) layered on top, deriving size via `CarbodyType.sizeCategory`

Plus `SpotFit` tri-state badge on the home peek (OPTIMAL / FITS / DOES_NOT_FIT / UNKNOWN) replacing the binary "Apto/no apto".

## Done

- **Phase 1 — Domain**: rename + 3 new domain models (`CarbodyType`, `VehicleParkingRules`, `SpotFit`) + propagation through ConfirmParking, UpdateParkingLocation, ProcessConfirmedDeparture, ReleaseActiveParkingSession, ObserveParkedVehicles, ReportSpotReleased + scheduler/worker chain.
- **Phase 2 — Catalog**: `VehicleCatalog.inferBodyType(brand, model)` with curated exact-match table (~30 brands) + keyword pattern fallback.
- **Phase 3 — Persistence**: `carbodyType` column on VehicleEntity, UserParkingEntity, SpotEntity + their DTO counterparts (VehicleDto, SpotDto, ParkingHistoryDto) + mappers. Room bumped to v6 (destructiveMigration handles).
- **Phase 4 — Presentation MVI**: new `SetCarbody` intent, `isCarbodyManualOverride` state field, inference re-runs on brand/model change, carbody persisted on save.
- **Phase 5 — UI Compose**: `CarbodyInfoCard` + `CarbodyManualPicker` dialog + `SpotFitRow` (replaces `CompatibilityRow`). VehicleRegistrationScreen reworked.
- **Phase 6 — Strings**: 20 new keys in EN + ES + 7 fallback locales.
- **Phase 8 — Tests**: 3 new test classes (CarbodyTypeRulesTest, VehicleInferencePatternTest, SpotFitTest) + updates to VehicleCatalogTest, VehicleRegistrationViewModelTest, SpotDtoMapperTest. 448 unit tests passing.
- **Phase 9 — Docs**: this file + architecture spec + CLAUDE.md update.

## Open follow-ups (sub-tickets)

- **ICON-SVG-001** — 10 `ic_car_*.xml` are placeholders (single generic car silhouette). User to drop Lucide/Material SVGs into `composeApp/src/commonMain/composeResources/drawable/` and rebuild.
- **MARKERS-CARBODY-001** — `VehicleBadgeMarker` still uses the legacy `VehicleSize.icon` ImageVector. Migrate to `CarbodyType.icon` drawable once SVGs land. Requires switching from `Icon(imageVector=…)` to `Icon(painter=painterResource(…))`.
- **VehicleSizeExplainer-rewrite** — the explainer screen copy still uses the legacy five-tier mental model. Refresh examples + copy.
- **i18n-translate-fallbacks** — 7 locales (de/fr/it/pt/nl/pl/ro) carry EN copy. Native translations welcome.

## Notes

- Pre-prod hard reset: user manually wipes Firestore + Room before first install. No data migration shipped.
- `VehicleSizeSelector` and legacy `VehicleSize.icon` extension kept for the moment — `VehiclePageContent` and the home filter bar still use them. Cleanup tracked under MARKERS-CARBODY-001.
