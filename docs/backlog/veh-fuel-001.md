# VEH-FUEL-001 · El coche sabe qué quema

**Estado:** ✅ Done — mergeado a master (2026-09-08). Verificado: alta exige combustible y la moto
lo pregunta sin carrocería; el patinete no; PHEV persiste dos consumos; comas del teclado ES
parsean; migración 2→3 con testigo de ida y vuelta; ningún fichero de detección menciona
`FuelType` (grep de cierre); paridad ×9 y deserializador en verde.

Fase **F2 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)).

## Problema

Las estadísticas de F3/F4 quieren decir "este mes ~9 € de gasolina y X kg de CO₂", y hoy el
vehículo no sabe ni qué combustible usa ni cuánto consume. Sin ese dato, el gasto estimado o no
existe o se inventa.

## Doctrina violada

Ninguna. Las que aplican: **cambio de esquema = bump + `Migration` en `ALL_MIGRATIONS`**
[DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] · **campo DTO/Entity → serializers end-to-end** (el
deserializador manual de Firestore es fila a fila; un campo que falte se pierde EN SILENCIO tras el
sync) · ⛔ **ningún evaluador de detección lee `fuelType`** — el combustible no es categoría ni
señal.

## Señales / datos disponibles

Verificado en master `9ac5f741` al abrir:
- `VehicleType` declara sus implicaciones como `when` EXHAUSTIVOS (4 preguntas) — la 5.ª pregunta
  (`burnsFuel`) entra por el mismo patrón: un tipo nuevo no compila sin contestarla.
- Room está en v2 (`PAPARCAR_DB_VERSION`); la cadena de migraciones y su testigo
  (`AppDatabaseV1BaselineTest`, con `CURRENT_IDENTITY_HASH` de los schemas) están vivos. La
  **dirección de F8 ya está decidida** (proyección) → esta migración no arriesga re-keyado.
- `FirestoreDeserializerParityTest` vigila la paridad DTO↔deserializador.
- `VehicleReconcile.onTakeRemote` solo preserva `licensePlate` — los campos nuevos SÍ sincronizan,
  pero un doc remoto legacy trae null y no debe borrar lo local (patrón `zoneRadiusMeters`).
- ⚠️ Los KDoc de `Vehicle.bluetoothDeviceId` y `VehicleEntity.bluetoothDeviceId` dicen "never
  synced" y MIENTEN (DTO+mapper+datasource lo suben desde siempre) — se corrigen aquí (hallazgo de
  la sesión F8 del 08-09).

## Diseño

- **Domain** (Kotlin puro):
  - `FuelType` (6): `GASOLINE, DIESEL, LPG_CNG, HYBRID, PLUG_IN_HYBRID, ELECTRIC`.
  - `EnergyLabel` (5): `ZERO, ECO, C, B, NONE` (nombres DGT; fuera de ES se presenta como
    "etiqueta ambiental" genérica). Hoy solo se guarda; la consumirá SER.
  - `VehicleType.burnsFuel`: CAR y MOTORCYCLE → true; SCOOTER y BIKE → false (sin selector, null).
  - `Vehicle` += `fuelType: FuelType?`, `consumptionPer100: Float?` (override del user; null ⇒
    default), `consumptionElectricPer100: Float?` (solo PHEV: lleva las dos), `energyLabel:
    EnergyLabel?`.
  - `domain/vehicle/FuelDefaults.kt`: consumo por defecto con constantes nombradas — gasolina 6,5 ·
    diésel 5,5 · GLP 8,5 · híbrido 5,0 · PHEV 3,0 l + 12 kWh · EV 17 kWh/100; moto 3,5. La unidad
    es implícita del tipo (l o kWh). Sin validar contra marca/modelo: no hay catálogo fiable.
- **Data**: Room **v3** (`MIGRATION_2_3`: 4 columnas nullable; bump + testigo en el baseline con el
  hash de `3.json`) · `VehicleDto` + deserializador fila a fila + mappers (4 campos × 4
  direcciones) · reconcile: `r.x ?: l?.x` para los 4 (un remoto legacy no borra) · todo sincroniza
  (no es PII); `licensePlate` sigue on-device.
- **UI** (`VehicleRegistration`): sección Combustible visible cuando `burnsFuel` — selector de 6
  con iconos nivel 2 (mapeo en presentación), **obligatorio en alta** (`canSubmit` lo exige);
  consumo como campo numérico con el default de su tipo como placeholder (PHEV enseña dos campos);
  etiqueta ambiental opcional. Edición prefill. Ayuda inline: "Estimamos gasto y emisiones con
  este dato" (causa+consecuencia, sin jerga).
- **Dev Catalog**: vehículos de muestra con cada `FuelType` en los fakes; variantes del formulario
  (coche gasolina, PHEV con dos campos, moto, patinete sin sección) en la galería.
- **Docs**: `CLAUDE.md` §Modelos de datos (`Vehicle`) · `VEHICLE-CATEGORIZATION.md` sección
  "Combustible no es categoría" (no afecta a detección ni `SpotFit`).

## Criterio de éxito

Alta de coche exige combustible y el patinete ni lo pregunta; el consumo por defecto aparece como
placeholder y el override del user gana; un vehículo editado en el móvil A llega al B con su
combustible (paridad de deserializador en verde); la base v2 migra a v3 conservando filas
(baseline); ningún evaluador importa `FuelType` (grep). Suite + Konsist + paridad ×9 + assembles +
klib iOS en verde.

## Consumidores auditados

- Evaluadores/detección → PROHIBIDO leer `fuelType`; verificado por grep al cerrar ✔
- `VehicleCatalog.inferBodyType` → no se toca (el combustible no se infiere: el Clio E-Tech
  demuestra que la validación por modelo miente) ✔
- `vehicleArtOf` / identidad visual → el combustible NO cambia el arte (no es identidad) ✔
- `SpotFit` / geofence radius → siguen leyendo size/carbody, nunca fuel ✔
- Baseline + paridad de deserializador + KoinVerify → adaptados en esta tarea ✔
