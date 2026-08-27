# UI-ONBOARDING-BUTTON-ICONS-EARN-THEIR-PLACE-001 · Pre-Home buttons drop their icons — an icon must earn its place

**Estado:** ✅ Done (2026-08-28) — en master; compila prod+mock, 1.708 tests verdes. El hash vive en `MEMORY.md`.

## Problema
Los botones del flujo pre-Home (`Splash → Auth → Onboarding → Permissions → GpsDisclaimer →
VehicleSizeExplainer → VehicleRegistration → Home`) llevan icono casi todos: `Security` en el CTA
del onboarding, `BatteryFull`/`ArrowForward`/`Settings`/`GpsFixed`/`Sensors`/`Schedule` en el footer
de permisos, `GpsFixed` en el disclaimer, `Check` en el explainer de tallas y en el guardado del
vehículo… El texto ya dice lo que hace el botón; el icono no añade información, añade ruido.

## Doctrina violada
Ninguna regla escrita — al revés: el KDoc de `PapPrimaryButton` (`PapButton.kt`) consagra el icono
como "the DEFAULT" y `PapAlertDialog` lo hace **obligatorio** en su CTA primario
(`primaryLeadingIcon: ImageVector`). La doctrina vigente es la que produce el ruido. Este ticket la
invierte: **el botón es texto por defecto; un icono tiene que ganarse el sitio.**

## Doctrina nueva — cuándo un icono SÍ se gana el sitio
1. **Acción destructiva** que pide un beat extra de atención (`Delete` en el confirm de borrado).
2. **Identidad de proveedor** (logos de social login — viven en BaseLogin, fuera de este repo).
3. Nada más. Ni "refuerzo semántico", ni flechas de avance, ni el icono del permiso repetido en su
   botón. La duda se resuelve SIN icono.

## Diseño
El invariante vive en los componentes compartidos, no en los call sites:
- `PapButton.kt` — KDoc de `PapPrimaryButton` reescrito (texto-por-defecto + criterios). Se borra
  `PapTextButton` (icono obligatorio, **cero call sites** — componente muerto).
- `PapAlertDialog.kt` — `primaryLeadingIcon` pasa de obligatorio a `ImageVector? = null`.
- `PapFooterButton.kt` — KDoc alineado con la doctrina.
Barrido de call sites pre-Home: se quita el icono en todos salvo los exentos por criterio 1/2.

## Barrido de consumidores — veredicto por call site
| Call site | Icono | Veredicto |
|---|---|---|
| `OnboardingScreen.kt` CTA "Set up" | `Security` | ❌ fuera |
| `PermissionsContent.kt:388` allow-background | `BatteryFull` | ❌ fuera |
| `PermissionsContent.kt:393` TextButton continue | `ArrowForward` manual | ❌ fuera (queda `TextButton` de texto) |
| `PermissionsContent.kt:411` footer continue | `ArrowForward` | ❌ fuera |
| `PermissionsContent.kt:439` CTA principal (4 estados) | `Settings`/`LocationOn`/`GpsFixed`/`Sensors` | ❌ fuera — el label por estado se queda |
| `PermissionsContent.kt:449` TextButton core-only | `Schedule` manual | ❌ fuera |
| `PermissionsContent.kt` diálogo bg-guide, CTA | `Settings` | ❌ fuera (el hero del diálogo se queda — es ilustración, no botón) |
| `PermissionsContent.kt` diálogo skip, CTAs | `Sensors` + `Schedule` | ❌ fuera |
| `GpsDisclaimerScreen.kt:45` confirm | `GpsFixed` | ❌ fuera |
| `VehicleSizeExplainerScreen.kt:123` CTA | `Check` | ❌ fuera |
| `VehicleRegistrationScreen.kt:707` save | `Check` | ❌ fuera |
| `VehicleRegistrationScreen.kt` diálogo BT, CTA | `Bluetooth` | ❌ fuera (el hero ya es `Bluetooth` — redundancia doble) |
| `CarbodyInfoCard.kt` pill "change" | `Edit` | ❌ fuera |
| `VehicleRegistrationScreen.kt` delete row + confirm | `Delete` | ✅ SE QUEDA — criterio 1 (destructivo; además solo aparece editando, post-Home) |
| Social login (BaseLogin) | logos | ✅ SE QUEDA — criterio 2, y es repo ajeno |
| `PaparcarAuthSlots.kt:304` submit · Onboarding "Next" | (ya sin icono) | ya cumplían |
| Leading icons de campos de texto, heros de diálogo, glifos de `PermissionRow`, chips selectores | — | exentos: no son botones — iconografía Nivel 2/3 de filas, campos e ilustración |

**Post-Home (fuera de alcance de este ticket, la doctrina nueva los alcanzará):** call sites con
icono de `PapPrimaryButton`/`PapFooterButton`/`PapAlertDialog`/`PaparcarBottomActionBar` en Home,
peek, ajustes, BT config (`BluetoothConfigScreen.kt:149`)… — follow-up si el user quiere extenderlo.

## Criterio de éxito
- Ningún botón pre-Home renderiza icono salvo los dos exentos.
- `PapAlertDialog` compila con CTA primario sin icono; call sites post-Home intactos.
- `PapTextButton` eliminado; compila prod + mock; tests verdes.

## Consumidores auditados
Inventario exhaustivo (agente Explore, 2026-08-28) sobre `presentation/` + `ui/components/` del
flujo pre-Home; tabla de arriba. `PapTextButton`: grep con cero usos en todo el repo.
