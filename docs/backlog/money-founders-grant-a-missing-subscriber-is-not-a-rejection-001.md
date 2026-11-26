# MONEY-FOUNDERS-GRANT-A-MISSING-SUBSCRIBER-IS-NOT-A-REJECTION-001 · un 404 de RevenueCat se trataba como veredicto final, no como "todavía no"

**Estado:** ✅ Done · desplegado 26-11-2026 (`grantFoundersPlus` + `releaseFoundersSlot`, europe-west1)

## Ajuste tras el primer despliegue (mismo día)
El log nuevo confirmó la causa al dedillo — `{"code":7259,"message":"The subscriber was not found."}`
de RevenueCat — y de paso dejó ver que el presupuesto de reintento era demasiado corto:
`grants/XnSubZ8PNVVYfXHCnKarMPv0HU53` agotó sus 5 intentos en **74 segundos** de backoff de
Eventarc y volvió a fallar. Eso no es tiempo suficiente para que el `logIn` (un solo intento, sin
reintento propio — [MONEY-001]) del cliente llegue a completarse. Subido `MAX_GRANT_ATTEMPTS` de
5 a 20 para apoyarse en el backoff exponencial de Eventarc y dar minutos reales, no segundos. Sigue
acotado — un caso que de verdad nunca se resuelve todavía devuelve la plaza, no se queda
`reserved` para siempre.

## Pendiente de producto (NO cerrado por este ticket)
Los 3 usuarios del 26-11 (`jA4PeYwikwMehVRdV2B8QRbXht12`, `Sk4icnM7LNdQKbjvnFHy4PpLAny2`,
`2S6qHSgqLYVWzEFeenCIfNwWStL2`) no se recuperan solos: su `foundersClaims/{uid}.outcome` ya quedó
en `'failed'`, que `decideReservation` trata como terminal. Hace falta, cuando cada uno reabra la
app (dispara su propio `logIn` y los crea en RevenueCat — comprobado hoy, siguen dando 404):
repetir la concesión a mano, o borrar `grants/{uid}` + `foundersClaims/{uid}` para que el sistema
reintente solo.

## Problema
El 26-11 tres usuarios reales (`jA4PeYwikwMehVRdV2B8QRbXht12`, `Sk4icnM7LNdQKbjvnFHy4PpLAny2`,
`2S6qHSgqLYVWzEFeenCIfNwWStL2`) reclamaron el Plus de founders y se quedaron sin él. `grantFoundersPlus`
registró `"founders promo: store rejected grant, slot returned"` con solo `{uid}` — sin status HTTP
ni cuerpo de respuesta, así que no había manera de saber el motivo con lo que quedaba logueado.

Diagnóstico (sesión de hoy, sin ticket previo):
- Los 3 `grants/{uid}` quedaron en `status:'failed'`, `attempts:1`, `possiblyGranted:false` — la
  llamada a RevenueCat falló en el PRIMER intento con un error que `outcomeFor()` clasificó como
  NO retryable (nunca llegó a los 5 intentos de `MAX_GRANT_ATTEMPTS`).
- `mcp__revenuecat__get-customer` (proyecto `proj51f017f1`) devuelve **404 "Could not find customer
  ID associated with this project"** para los 3 uids, incluso horas después.
- Repetir la concesión a mano (`grant-customer-entitlement`) da el **mismo 404** — confirma que
  RevenueCat no conoce a estos uids como subscriber, no es un fallo de producto/entitlement (la
  clave y el entitlement `plus` ya estaban verificados, ver [[project_money_founders_plus_promo_001]]).
- El `logIn(uid)` del cliente (`SplashViewModel.bootstrap`, best-effort, sin reintento si falla —
  [MONEY-001]) es lo único que crea ese subscriber en RevenueCat. El claim en Firestore que dispara
  `grantFoundersPlus` se crea al llegar a Home, SIN esperar confirmación de ese `logIn`. Si el
  `logIn` no ha terminado (o falló una vez, sin red en ese instante) cuando el servidor llama a
  `POST .../entitlements/plus/promotional`, RevenueCat responde 404 porque el subscriber no existe
  TODAVÍA — no es un rechazo permanente.
- 65 claims entre el 22 y el 25-11 ganaron esa carrera sin que nadie lo notara; el 26-11 se perdió
  3 veces. No hay cambio de código ni de despliegue que lo explique — la función sigue en la misma
  revisión (`grantfoundersplus-00002-zep`) desde el 21-11.

## Doctrina violada
Ninguna de detección. Pero sí el principio general del proyecto — **sistemas, no parches**: el
fallo real es un 404 que significa "todavía no", tratado por error como una respuesta definitiva.
Y el logging roto es justo lo que `logger.error` existe para evitar: un log que nunca puede
responder la pregunta que motivó escribirlo.

## Diseño
Dos cambios, ambos en el ÚNICO sitio donde la función habla con la tienda (`callFoundersStore` /
`outcomeFor` en `functions/src/`), no parches dispersos por cada call site:

1. **`outcomeFor()` en `foundersPromo.ts`**: marcar `status === 404` como `retryable: true`
   (`possiblyApplied` se queda en `false` — no se aplicó nada). Esto reutiliza el mecanismo de
   reintento que YA EXISTE (`MAX_GRANT_ATTEMPTS` + `retry: true` del trigger) en vez de construir
   uno nuevo: si el 404 es por "el cliente aún no se ha identificado", unos minutos/horas de
   reintento (gobernados por Eventarc, no por el lease de 60s que es para invocaciones abandonadas)
   le dan tiempo a resolverse solo.
2. **`callFoundersStore()` en `index.ts`**: loguear el status HTTP real y el cuerpo de la respuesta
   cuando la tienda rechaza la llamada (`!outcome.ok`), y el mensaje de error real en el catch de
   red — sin tocar la Authorization header ni la clave. Esto es lo que debería haber estado desde
   el principio: sin esto, el `logger.error` de hoy no podía decir NADA sobre el motivo.

No se toca el flujo del cliente (`SplashViewModel`, `AppViewModel`) en este ticket: eso es un best
-effort documentado y deliberado ([MONEY-001]); el arreglo vive del lado servidor, donde SÍ hay
margen de reintento sin tocar la UX.

## Criterio de éxito
- `outcomeFor(404)` → `retryable: true`, `possiblyApplied: false` (test unitario).
- El log de rechazo lleva `status` + `detail` de la respuesta real; el de fallo de red lleva el
  mensaje del error. Ningún log lleva la clave ni el header `Authorization`.
- Tests de `foundersPromo.test.ts` / `index.test.ts` (los que existan) cubriendo la nueva
  clasificación del 404 y que un 404 no decrementa el contador a la primera.

## Consumidores auditados
- `processFoundersClaim` (único lugar que lee `.retryable`): con 404 retryable, cae en la misma
  rama `'retry'` que un 5xx/408/429 ya usa — no hace falta tocarlo.
- `processFoundersDeletion` (revoke): no lee `.retryable`, solo `.ok` — sin cambio de comportamiento.
- Los 3 usuarios afectados hoy: su `foundersClaims/{uid}` ya quedó en `outcome:'failed'`, así que el
  arreglo no los re-dispara solo. Pendiente de remediación manual cuando reabran la app (su propio
  `logIn` creará el subscriber en RevenueCat) — ver nota en `MEMORY.md` tras cerrar este ticket.

Relacionado: [[project_money_founders_plus_promo_001]]
