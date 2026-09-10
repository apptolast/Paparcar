# DEV-AN-EDIT-VARIANT-MUST-EDIT-A-CAR-001 · Las variantes «Edición» de la galería pintan una moto

**Estado:** 📋 Abierto · sin rama · detectado el 10-09-2026 durante
`UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001`

## Problema

En la galería de estados (`StateGalleryScreen.kt`, grupo **«Registro de vehículo»**), las tres
variantes de edición construyen su state **sin `vehicleType`**:

```kotlin
Variant("Edición") {
    VehicleRegistrationContent(
        state = VehicleRegistrationState(
            editingVehicleId = "v-edit",
            brand = "Toyota", model = "Corolla",
            sizeCategory = VehicleSize.MEDIUM_SUV,   // ← no dice qué ES
            showBrandModelOnSpot = true,
        ),
    )
}
```

`VehicleRegistrationState.vehicleType` es `null` por defecto, y
`expectsCarbody get() = vehicleType?.hasCarbody == true` → **false**. La pantalla toma la rama
`NonCarSizeBadge` (`VehicleRegistrationScreen.kt:432`) en lugar de `CarbodyInfoCard` (`:416`), y
`NonCarSizeBadge` pinta un glifo de **motocicleta** fijo.

**Visto en device (emulador, 10-09):** un «Toyota Corolla», etiquetado «Medium», con un icono de
moto en su tarjeta de carrocería.

Afecta a `Variant("Edición")`, `Variant("Edición · borrar con historial")` y
`Variant("Edición · aparcado (borrado bloqueado)")`. La variante `Variant("Con color")` **sí** pasa
`vehicleType = VehicleType.CAR` y sale bien — o sea, el arreglo ya está escrito al lado.

## ⚠️ Alcance: SOLO el mock. Producción no puede caer aquí

Comprobado antes de cerrar el ticket padre:

- `Vehicle.vehicleType` (`domain/model/Vehicle.kt:36`) es **no-nulo**, con default `VehicleType.CAR`.
- La carga en edición copia el campo tal cual: `vehicleType = vehicle.vehicleType`
  (`VehicleRegistrationViewModel.kt:268`).

O sea: **ningún coche real llega a la pantalla con el tipo nulo.** El `null` solo existe en
`VehicleRegistrationState.vehicleType` (`:46`), que es nullable a propósito para el alta (aún no se
sabe qué es), y la galería lo construye A MANO saltándose el ViewModel.

Por eso esto no es un bug de usuario: es la **galería mintiendo sobre la pantalla**, que es
justamente lo que la hace peligrosa — se revisa un cambio de UI contra una variante que enseña una
rama que el usuario nunca ve. Prioridad baja, pero real.

## Doctrina violada

`DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001`: una variante que no ejercita su caso es peor que
no tenerla, porque se ve verde. Estas tres dicen enseñar «la pantalla de editar un coche» y lo que
enseñan es la rama de los vehículos SIN carrocería.

Es exactamente la misma familia que el fallo de `d6cd89a8` (una variante cuyo parámetro por defecto
la sacaba de su propio caso, solo visible EN DEVICE).

## Diseño

Poner `vehicleType = VehicleType.CAR` + `carbodyType` en las tres variantes de edición, como ya hace
«Con color». Y valorar si el hueco real está más abajo: que la galería pueda construir un state de
edición sin decir qué se edita es lo que permitió el fallo.

## Criterio de éxito

Las variantes «Edición*» renderizan `CarbodyInfoCard` con el glifo del coche que dicen editar, y
sigue existiendo al menos una variante que ejercite `NonCarSizeBadge` a propósito (hoy la cubre
«Nuevo · moto»).

## Fuera de alcance

`NonCarSizeBadge` hardcodea `VehicleMotorcycle` para *toda* la familia no-CAR (moto, scooter, bici).
Puede estar bien o no; no se investigó, porque el fallo visto es de la galería.
