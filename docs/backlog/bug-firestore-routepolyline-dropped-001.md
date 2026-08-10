# BUG-FIRESTORE-ROUTEPOLYLINE-DROPPED-001 — `routePolyline` se perdía al LEER de Firestore

> Estado: ✅ RESUELTO 2026-08-10 (en la misma rama de DET-ROUTE-TRACK-001) · Detectado por
> `FirestoreDeserializerParityTest` (rojo → verde).

## Causa REAL (corregida la del análisis inicial)
No era el mapper `toParkingHistoryDto()` de dominio (ese sí asigna `routePolyline`, los 4 mappers de
`ParkingSessionMapper` lo hacen). El guard apuntaba al **deserializer manual**
`DocumentSnapshot.toParkingHistoryDto()` en `RemoteUserProfileDataSourceImpl` (Firestore doc → DTO),
que leía campo a campo con `get<T?>(FIELD_*)` y **no leía `routePolyline`** → al LEER el histórico de
Firestore (dispositivo nuevo / re-sync) la ruta se caía al default null. La ESCRITURA sí lo subía
(serialización automática del `@Serializable ParkingHistoryDto`).

## Fix aplicado
- `RemoteUserProfileDataSourceImpl.toParkingHistoryDto()`: `routePolyline = runCatching {
  get<String?>(FIELD_ROUTE_POLYLINE) }.getOrNull()` (patrón defensivo como `detectionPath`, por docs
  legacy) + constante `FIELD_ROUTE_POLYLINE = "routePolyline"`.
- `FirestoreDeserializerParityTest` → verde.

Lección: es exactamente [[feedback_dto_field_parity]] — al añadir un campo a un DTO hay que auditar
TODOS los serializers Y deserializers end-to-end (mapper Domain↔DTO **y** el deserializer manual del
DocumentSnapshot). El guard lo cazó.

## Bug
El guard `FirestoreDeserializerParityTest` ("every Firestore manual deserializer assigns every property
of its target DTO") falla:

```
Firestore deserializer parity broken — 1 field(s) silently dropped:
  • toParkingHistoryDto() does not assign ParkingHistoryDto.routePolyline
```

La ruta conducida (`routePolyline`) SÍ existe en el modelo de dominio, en Room (`UserParkingEntity`,
schema v16) y en el DTO `ParkingHistoryDto`, y el mapper la asigna en los otros sentidos
(`ParkingSessionMapper` líneas ~45/104/147/180: Entity↔Domain, Domain→Firestore de UserParking,
Firestore→Domain). Pero el mapper **`toParkingHistoryDto()`** (el que escribe el HISTÓRICO a Firestore)
**NO asigna `routePolyline`** → al subir un aparcamiento a la colección de historial, la ruta se pierde
en remoto. Un dispositivo nuevo que lea el histórico desde Firestore no verá la línea de ruta.

Es el patrón exacto que la regla [[feedback_dto_field_parity]] previene (campo añadido a un DTO sin
auditar TODOS los serializers end-to-end). Introducido por el trabajo de ruta persistente
[DET-ROUTE-TRACK-001].

## Fix
- En `ParkingSessionMapper.toParkingHistoryDto()` añadir `routePolyline = routePolyline` (el guard
  dice exactamente qué falta). Verificar de paso el sentido inverso (`ParkingHistoryDto → Domain/Entity`)
  para que la ruta round-trip también al histórico, no solo a la sesión activa.
- `FirestoreDeserializerParityTest` pasa a verde.

## Nota
Independiente del trabajo de esta sesión (whitelist / BT-connected / badge honesto). Detectado porque
al arreglar un import de `assertTrue` en `HomeTripControllerTest` se recompiló toda la commonTest y
el guard corrió. Prioridad: media-alta (pérdida silenciosa de datos en remoto).
