# ONBOARDING-VEHICLE-REQUESTS-001 · Keep the vehicle pre-registration checklist in sync

**Status:** ✅ Done · branch `feature/ONBOARDING-VEHICLE-REQUESTS-001` · worktree `../Paparcar-ONBOARDING-VEHICLE-REQUESTS-001`

## Problema

The pre-registration explainer currently says that only the vehicle size is essential and that brand
and model are optional. The vehicle registration screen now asks for additional vehicle identity,
classification, detection, fuel and privacy information, so the explanation is incomplete and
misleading.

## Doctrina violada

The setup flow must accurately explain the information requested immediately before the registration
form. No detection or domain invariant changes.

## Señales / datos disponibles

`VehicleSizeExplainerScreen` is shown before `VehicleRegistrationScreen`. The registration form
currently contains vehicle type, brand/model, body type and size, colour, nickname, license plate,
GPS and motion detection with optional Bluetooth, privacy/display choice, and conditional fuel,
consumption, price and environmental-label fields.

## Diseño

Update the explainer checklist and its localized copy from the registration form's current sections.
Keep the explainer presentation-only; do not duplicate validation or registration logic.

## Criterio de éxito

The pre-registration screen names every category of information requested by the registration form,
with all nine locales kept in parity, and the app builds successfully.

## Consumidores auditados

- `App.kt` route from the explainer to vehicle registration — covered by the existing screen.
- `VehicleSizeExplainerScreen` checklist — updated with the complete request list, sensor icon,
  general detection copy, Compose label icon, and non-overlapping scrolling layout.
- Vehicle registration string resources in all nine locales — to be updated in sync.

## Validación

- `:app:compileMockDebugKotlin` — passed.
- `:app:assembleProdRelease` — passed.
- Redmi `5f8991cb` — installed with `install -r`; local/device APK SHA256 matched.
- `LocaleParityGuardrailTest` — the changed keys are present in all nine locales; the full test
  class remains blocked by the pre-existing missing `home_nearby_section_header` key in seven
  locales.
