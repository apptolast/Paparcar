# MONEY-FOUNDERS-PROMO-REQUIRES-A-NEW-ACCOUNT-001 · El cupo de Fundadores lo gastaba cualquier cuenta, no sólo la que llega por la campaña

**Estado:** ✅ Done · `startsAt` = el instante del despliegue de `grantFoundersPlus`/
`releaseFoundersSlot` (decisión del user, 22-11)

## Problema

`MONEY-FOUNDERS-PLUS-PROMO-001` concede Plus a las 2.000 primeras cuentas que llegan a Home durante
la campaña — pero "primeras" sólo significaba "primera vez que ESE uid reclama", sin mirar si la
cuenta es nueva. Medido en producción el 22-11: al probar la cadena con `rndeveloper11501@gmail.com`
(cuenta habitual, creada el `2026-02-23` según Firebase Auth — `createdAt: 1788463584492`), el
servidor concedió Plus igual que a una instalación nueva, y `promos/founders.granted` pasó de 0 a 1.

Con el rollout a producción en el 100% (decisión del user), cualquier cuenta YA existente que abra
la app tras la actualización a 1.7.0 gasta una de las 2.000 plazas — no sólo las instalaciones de
Black Friday. Con la base de usuarios activos de Paparcar, el cupo puede agotarse por gente que ya
tenías antes de la campaña, antes de que una sola instalación nueva lo vea.

## Doctrina violada

Ninguna regla escrita del repo — es una decisión de producto no tomada hasta ahora (ninguna regla
de `CLAUDE.md` dice "cuenta nueva"). Decisión del user (22-11): un regalo de Black Friday es una
herramienta de adquisición, y tiene que premiar a quien llega POR la campaña, no a quien ya estaba.

## Señales / datos disponibles

- `pap-26/(default)/promos/founders`: `{ active: true, cap: 2000, granted: 1, durationMonths: 3,
  endsAt: 2026-11-30T23:00:00Z }` — el `granted: 1` es la prueba de campo del bug, no un test.
- `grantFoundersPlus`/`releaseFoundersSlot` ya desplegadas (21-11) y confirmado que la clave
  `REVENUECAT_SECRET_KEY` v4 funciona de verdad (grant real a la cuenta de prueba).
- `functions/src/foundersPromo.ts`: `decideReservation` y `processFoundersClaim` no miraban la
  edad de la cuenta, sólo si ya existía (`accountExists`).
- `firestore.rules` → `foundersClaims.isPromoOpen()` no comprueba edad de cuenta y no tiene por
  qué: las reglas son el filtro barato (propiedad + ventana), el veredicto completo vive en la
  función, como ya hacía el cupo y la ventana.

## Diseño

El sistema ya separaba "¿puede reservar?" (`decideReservation`, función pura) de "¿existe la
cuenta?" (una llamada a Admin Auth antes de la transacción). La edad de la cuenta entra por el
mismo sitio que la existencia, sin añadir una llamada nueva:

- `foundersAccountExists(uid): Promise<boolean>` → `foundersAccountCreatedAtMillis(uid):
  Promise<number | null>` (un solo `getAuth().getUser(uid)`, lee `metadata.creationTime` en vez de
  sólo comprobar que no lanza `auth/user-not-found`).
- `PromoConfig` gana `startsAtMillis: number | null`, hermano de `endsAtMillis`, leído de un campo
  nuevo `promos/founders.startsAt` (mismo patrón que `endsAt`: lo escribe el user a mano cuando
  decide que la campaña empieza a contar, no se infiere).
- `decideReservation` gana un cuarto parámetro `accountCreatedAtMillis: number` y, tras comprobar
  cupo y ventana, devuelve `'notNew'` si `accountCreatedAtMillis < promo.startsAtMillis`. Un
  `startsAtMillis` ausente o `>= endsAtMillis` también falla cerrado (`'inactive'`), igual que el
  resto de la configuración.
- `'notNew'` es un `ClaimOutcome` terminal más: no reserva plaza, no llama a RevenueCat, marca el
  uid (no se vuelve a preguntar), igual que `'closed'`/`'full'`/`'inactive'`.
- Cliente (Kotlin): `FirestoreFoundersPromoRepository` mapea `"notNew"` a `FoundersClaimOutcome
  .NotGranted`, en la misma lista que los otros tres. `ClaimFoundersPromoUseCase` no cambia: ya
  trata cualquier `NotGranted` igual.

## Criterio de éxito

- [x] `decideReservation` deniega una cuenta anterior a `startsAtMillis` con `'notNew'`, y sigue
      resolviendo cupo/ventana primero si eso es lo que de verdad bloquea.
- [x] Una configuración sin `startsAt`, o con `startsAt >= endsAt`, falla cerrada (`'inactive'`),
      no abierta.
- [x] `processFoundersClaim`: una cuenta vieja no reserva plaza, no llama a la tienda, y queda
      marcada para no reintentar.
- [x] El smoke de emulador (`npm run test:emulated`) sigue en verde con una cuenta creada en el
      momento del test y un `startsAt` anterior.
- [x] `FirestoreFoundersPromoRepository` reconoce `"notNew"` sin lanzar `Unrecognized founders
      claim outcome`.
- [x] `npm test` en `functions/` (103 unitarios + smoke de emulador) y `./gradlew testAll
      :app:compileProdDebugKotlin :app:compileMockDebugKotlin` en verde.
- [x] **Decisión del user (22-11)**: `startsAt` = el instante en que se despliega el código, no
      una fecha elegida a mano. Cualquier cuenta creada desde ese instante cuenta como nueva; la
      cuenta de prueba de `MONEY-FOUNDERS-PLUS-PROMO-001` quedó concedida ANTES del gate y no se
      toca (una vez `grant.status == 'granted'`, `decideReservation` ni mira la edad de la cuenta).
- [x] Desplegado: `firebase deploy --only
      functions:grantFoundersPlus,functions:releaseFoundersSlot --force` (acotado para no tocar
      las otras cuatro funciones de producción) + `startsAt` escrito en `promos/founders`.
- [ ] `firestore.rules` **no cambia** — la edad de cuenta es una validación de servidor, no de
      regla, a propósito.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `functions/src/foundersPromo.ts` (`decideReservation`, `processFoundersClaim`) | **implementa** |
| `functions/src/index.ts` (`readPromo`, `foundersAccountCreatedAtMillis`, `grantFoundersPlus`) | **implementa** |
| `functions/src/foundersPromo.test.ts` | **implementa** — 7 tests nuevos/ajustados para `notNew` |
| `functions/src/test/foundersPromo.smoke.test.ts` | **implementa** — `startsAt` añadido al fixture |
| `firestore.rules` / `firestore-rules-tests` | **exento, a propósito** — la regla sigue siendo el filtro barato (propiedad + ventana); la edad de cuenta es del servidor |
| `FirestoreFoundersPromoRepository.kt` | **implementa** — `"notNew"` en la lista de `NotGranted` |
| `ClaimFoundersPromoUseCase.kt` | **exento** — ya trata cualquier `NotGranted` igual, no distingue motivo |
| web / ficha de Play | **exentos** — la promo no está publicada en ninguna de las dos |
