# UI-BUTTON-ONE-CANONICAL-CTA-001 · Tres botones de la app no pasan por el botón de la app

**Estado:** ✅ Done (2026-09-02) · ⏳ visto en device pendiente del próximo `/run` (bloqueo de
ubicación, permisos BT, alta de vehículo)

## Problema

`ui/components/PapButton.kt` define el botón canónico (`PapPrimaryButton` / `PapProviderButton`):
padding, forma, spinner de carga y rol `cta`, todo en un sitio. Pero tres call sites siguen
instanciando el `Button` de M3 a mano, cada uno con su propia receta:

| Call site | Qué añade por su cuenta |
|---|---|
| `presentation/home/.../HomeLocationBlockedState.kt:78` | relleno `error` + `onError`, alto fijo 52dp, `shapes.medium` |
| `presentation/bluetooth/BluetoothConfigScreen.kt:273` | nada — es un CTA normal que simplemente no se migró |
| `presentation/vehicleregistration/VehicleRegistrationScreen.kt:474` | botón compacto en el `trailing` de una fila: padding 16/8, `PapShapes.cardSmall` |

*(`ui/components/PapAlertDialog.kt:249` queda exento: ES un componente del sistema y parametriza su
acento a propósito.)*

**No hay deuda tipográfica**: los tres ya pintan su label con el rol `cta`, verificado al cerrar
`UI-TYPE-SYSTEM-HYGIENE-001`. Lo que hay es deuda de componente — el silueteado del botón depende
del call site, así que un cambio de forma o de padding no llega a los tres.

## Diseño

Lo que falta en `PapPrimaryButton` para absorberlos:

1. **Un tono destructivo.** El bloqueo de ubicación es rojo a propósito (bloquea el consumo, no
   informa). Hoy sólo se consigue pasando `colors` a mano. Un parámetro `tone` (`Brand` /
   `Destructive`) resuelto DENTRO del componente mantiene la doctrina de color: el call site pide
   intención, no `colorScheme.error`.
2. **Un tamaño compacto.** El botón del `trailing` de una fila no puede llevar el padding de un CTA
   de pantalla. `size` (`Regular` / `Compact`) con su padding y su forma.

⚠️ Decidir con cuidado cuántos ejes se abren: un `PapPrimaryButton` con cinco parámetros de estilo
deja de ser canónico y vuelve a ser M3 con otro nombre. Si sólo hay un consumidor de cada eje, puede
que la respuesta correcta sea un composable hermano con nombre propio.

## Criterio de éxito

- 0 `Button(` de M3 en `presentation/` (`PapAlertDialog` es la única excepción documentada).
- El alto, la forma y el padding de un CTA se cambian en un fichero.
- Visto en device: bloqueo de ubicación, permisos BT y alta de vehículo.

## Cierre (2026-09-02)

Los dos ejes del diseño, tal cual estaban propuestos — y ninguno más, que era el aviso:

- `PapButtonTone` (`Brand`/`Destructive`) y `PapButtonSize` (`Regular`/`Compact`) resueltos DENTRO
  de `PapPrimaryButton`; el call site pide intención, nunca `colorScheme.error` ni un padding.
  La receta compacta (16/8 + `cardSmall`) y la destructiva viven ahora en `PapButton.kt`.
- Migrados los 3: `HomeLocationBlockedState` (pierde su alto fijo 52dp y su `shapes.medium` — la
  silueta es la del botón de la app), `BluetoothConfigScreen` (swap directo) y el trailing de
  `VehicleRegistrationScreen` (`size = Compact`).
- 🔎 De paso, un bug latente arreglado: el spinner de `isLoading` estaba hardcodeado a `onPrimary`
  — sobre un fill Destructive habría sido invisible. Ahora usa `LocalContentColor`.
- **El criterio es permanente, no un grep**: `ButtonGuardrailTest` (Konsist, población compartida
  de `GuardrailScope` — no puede volver vacía) prohíbe el `Button(` crudo en feature. En su primera
  pasada cazó un sujeto real: `PapFooterButton` — que resultó ser el OTRO botón canónico (footer
  universal, 3 estilos) y entró al allowlist con su razón, junto a `PapButton` y `PapAlertDialog`.
- Suite **2.143/0** (+1 guardarraíl) · `:app:compileMockDebugKotlin` y `compileProdDebugKotlin` en
  verde. Sin strings nuevos, sin estados nuevos (la galería no cambia: mismas pantallas, misma
  variante).
