# UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001 · Un aparcamiento aproximado se guarda como área pero se dibuja como punto

**Estado:** 🔵 Abierto, sin código · descubierto durante `DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001`

## Problema

El dominio distingue desde hace tiempo entre un pin exacto y un **área**:

```kotlin
// UserParking.kt
val zoneRadiusMeters: Float? = null
val isApproximate: Boolean get() = zoneRadiusMeters != null
```

Lo escriben al menos tres caminos — `closed_approximate_zone` (honest close), la zona del
unattended-timeout, y las zonas de `DET-GAP-ANCHOR-ZONE-001` / `DET-WALK-ENTERED-ANCHOR-ZONE-001`.
El KDoc del propio campo dice *«rendered as a circle, never a…»*.

**Pero nadie lo lee.** `grep -rn "isApproximate\|zoneRadiusMeters" composeApp/src` fuera de dominio
y datos → **sólo tests**. Ni `presentation/` ni `ui/` lo consultan.

Consecuencia: una sesión guardada como área de 154 m y una guardada como punto de 3 m **se pintan
igual** — mismo marcador, misma card. El trabajo de ser honestos sobre la duda se queda en Firestore.

## Doctrina violada

**«Fallo asimétrico: ante la duda se PREGUNTA.»** Preguntamos (el nudge sale), pero además
**afirmamos**: el mapa sigue diciendo «tu coche está AQUÍ» con precisión de metro. La mitad honesta
del contrato no llega al usuario.

## Señales / datos disponibles

Todo. `UserParking.zoneRadiusMeters` viaja ya a Room y a Firestore (los mappers lo cubren) y llega
intacto a `HomeState.activeSessions`.

## Diseño (esbozo, sin decidir)

- **Mapa**: círculo de `zoneRadiusMeters` bajo el marcador del vehículo. Ya hay precedente de
  círculo — las zonas privadas (`addingZoneRadius`) se dibujan así.
- **Marcador**: el glifo del coche no debería leerse igual cuando es una suposición. Ojo con
  ⛔ UI-COLOR-DOCTRINE-001: *el estado nunca se tiñe*, así que la distinción va por forma/borde, no
  por color.
- **Peek / card**: una línea de copy con causa + consecuencia + remedio y **sin mecánica interna**
  ("no pudimos seguir el final del trayecto · tu coche está en algún punto de esta zona · tócala
  para colocarlo"). → strings nuevos en los **9 locales**.
- **Dev Catalog**: variante de galería con una sesión aproximada, más el escenario mock.

## Criterio de éxito

- Una sesión con `zoneRadiusMeters != null` se ve distinta de una exacta, en mapa y en peek.
- El copy no menciona pasos, zancadas, huecos de GPS ni honest close.
- Galería + `assembleMockDebug` en sync, strings en los 9 locales.
