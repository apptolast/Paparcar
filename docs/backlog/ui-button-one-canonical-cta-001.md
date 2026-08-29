# UI-BUTTON-ONE-CANONICAL-CTA-001 · Tres botones de la app no pasan por el botón de la app

**Estado:** 🟡 Abierto, sin rama · follow-up de `UI-TYPE-SYSTEM-HYGIENE-001`

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
