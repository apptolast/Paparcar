# ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001 · La alerta de zona deja de preguntar y pasa a que se la cuenten

**Estado:** ✅ Done — en master (15-09-2026). Reglas desplegadas; la función, pendiente de que
Google termine de propagar los permisos de Eventarc (rito de la primera función de 2ª gen).

Sucesor planificado de [`zone-an-alert-nobody-is-awake-to-receive-001.md`](zone-an-alert-nobody-is-awake-to-receive-001.md)
y [`zone-a-look-that-reads-the-cache-is-not-a-look-001.md`](zone-a-look-that-reads-the-cache-is-not-a-look-001.md).
Estrena las Cloud Functions del proyecto, o sea que cierra la mitad de
[`infra-an-alert-that-travels-needs-a-backend-001.md`](infra-an-alert-that-travels-needs-a-backend-001.md).

## Problema

La entrega de fondo funciona, pero **pregunta**. Android solo tiene un canal por el que un servidor
puede despertar a una app que no corre —FCM—, así que sin él la única opción es despertarse con un
temporizador y consultar. Eso es lo que hace hoy: 96 miradas al día por zona y usuario, la mayoría
para no encontrar nada.

Tres consecuencias medidas, no supuestas:

| | Hoy (worker) | Con push |
|---|---|---|
| Latencia | hasta 15 min, mucho peor en Doze | segundos |
| Coste | crece con **usuarios que vigilan** | crece con **plazas publicadas** |
| Plazas provisionales (12 min de vida) | **inalcanzables por construcción** | llegan |

Y el coste tiene número: el suelo de WorkManager son 15 min → `96 × zonas × usuarios × documentos`.
Con 2 zonas y ~20 documentos por consulta, las **50 K lecturas/día gratis** de Firestore se agotan
sobre los **13 usuarios Plus**. Con push la cuenta es `plazas publicadas × suscripciones cercanas`, y
las **2 M de invocaciones/mes** gratis dan para 66.000 plazas **al día** antes de pagar un céntimo.

⚠️ Los «~20 documentos» son la suposición que los logs de
`ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001` están para desmentir o confirmar. La decisión de
montar esto no depende de ese número; **cuánta prisa tenía**, sí.

## Doctrina violada

Ninguna: esto no arregla un invariante roto, cambia el transporte. Las reglas que lo gobiernan:

- **Al perder Plus se PAUSA, no se olvida** (`MONEY-001`). Con un servidor de por medio hay una
  trampa nueva: el backend **no sabe quién es Plus** —la titularidad vive en RevenueCat y se lee en
  el cliente—, así que hay que decidir dónde se comprueba sin duplicar la verdad.
- **Cero copy con mecánica interna.** El usuario no se entera de que esto cambió de transporte.
- **Un aviso que no llega no avisa de que no ha llegado**: al retirar el worker, la única vía pasa a
  ser el push, y eso es una decisión de riesgo tomada a propósito (abajo).

## Diseño

### 1 · El índice de suscripciones, y por qué hace falta

La función, ante una plaza nueva, tiene que contestar **«¿quién tiene una zona que contenga este
punto?»**. Hoy es imposible: las zonas viven en `users/{uid}/zones`, subcolecciones privadas, y no
hay forma de preguntar entre usuarios sin recorrerlos todos.

Colección nueva de nivel superior, `zoneAlertSubs/{uid}_{zoneId}`: geohash del centro, lat/lon,
radio de alerta, nombre de zona, uid y los **tokens FCM** de los dispositivos de ese usuario.

⛔ **La escribe el CLIENTE, y eso es a propósito**: estar en el índice ES el permiso. El cliente solo
la mantiene mientras se cumplen las tres condiciones que ya decidían el stream —Plus ∧ interruptor ∧
zona con `notifyOnSpot`— y la borra en cuanto una falla. Así el servidor no tiene que saber de
RevenueCat, y **perder Plus te saca del índice sin tocar el `notifyOnSpot` de ninguna zona**: pausa,
no olvido, exactamente la misma regla de antes en un sitio nuevo.

⚠️ Su punto débil, dicho ahora: un usuario que caduca y **nunca vuelve a abrir la app** se queda en
el índice. Se acota con `expiresAt` en la fila, refrescado en cada arranque; la versión exacta es un
webhook de RevenueCat, y eso es seguimiento, no este ticket.

### 2 · La función

`onDocumentCreated('spots/{spotId}')` en **`europe-west1`** — `pap-26` tiene Firestore en `eur3`, y
una función lejos de su base de datos paga latencia y salida de red por cada disparo.

Consulta el índice por los mismos `geohashQueryBounds` que usa el cliente, recorta por el círculo
real, descarta al autor de la plaza, y envía. ⛔ **La geometría se decide con el mismo criterio que
en la app** — es la tercera copia de esa regla (stream, worker, función) y la única que no está en
Kotlin: va comentada como espejo, y si divergen, manda el cliente.

### 3 · Las provisionales entran, diciendo lo que son

Decisión del user. Viven 12 minutos, o sea que son **lo que el push desbloquea**: el poll no podía
llegar. Y una alerta no es un pin — es una invitación a mirar —, así que el riesgo no es el de la
doctrina de detección. Pero se dice: copy propio («sin confirmar»), no la misma frase que una
confirmada.

### 4 · El worker se retira entero

Decisión del user. Una sola vía de entrega, cero riesgo de duplicado, cero coste de poll. El precio
asumido: si el push no llega —token caducado, OEM que mató la app, sin red— **no llega nada**.

⛔ Lo que NO se retira es `zone_alerted_spots`: con push también hace falta saber qué se dijo ya. El
cliente sigue decidiendo si enseña la notificación, con la misma tabla y la misma regla.

### 5 · Reglas de seguridad

`zoneAlertSubs`: el usuario escribe **solo la suya** (`uid` del documento == `request.auth.uid`) y
lee **solo la suya**.

⚠️ La primera versión puso `read: if false` y estaba mal: sin poder leer tus propias filas no hay
forma de BORRAR las que sobran, que es exactamente lo que pasa al perder Plus o apagar una zona —
el usuario se quedaba recibiendo avisos de una zona que había apagado, desde un servidor sin forma
de enterarse.

Y la lectura acotada **no** reabre el barrido: Firestore exige que una consulta se pueda demostrar
permitida *antes* de ejecutarla, así que `where('cells','array-contains',…)` —la de la función— es
rechazada de plano para cualquier cliente. Solo pasa `where('uid','==',<el tuyo>)`. Un índice de
dónde vive la gente que se pudiera barrer sería el peor fallo posible de este ticket.

## Privacidad — lo que cambia y lo que no

Contrastado contra `docs/legal/DATA-SAFETY-FORM.md`:

| | ¿Nuevo? |
|---|---|
| Coordenadas de zona en el servidor | ❌ **ya viajaban**: `users/{uid}/zones` las sincroniza desde siempre |
| Token de registro FCM | ⚠️ categoría `Device or other IDs` **ya declarada** — hay que **añadir el token** a su lista |
| Compartido con terceros | ❌ sigue siendo **No**: una función propia es el desarrollador, no un tercero |

O sea: **una línea en el formulario y un párrafo en la política**. Lo que sí es nuevo de verdad es el
ALCANCE —de subcolección privada a colección consultable por el servidor— y eso no se resuelve en un
formulario sino en las reglas de §5.

## Criterio de éxito

| | |
|---|---|
| Perder Plus / apagar el interruptor / quedarse sin zona / sin sesión / sin token → **índice vacío** | ✅ 10 tests |
| Las celdas se piden con el radio de ALERTA, no con el dibujado | ✅ |
| Una fila por (usuario, zona), con su caducidad estampada | ✅ |
| Reglas de `zoneAlertSubs` válidas contra Firebase | ✅ `firebase_validate_security_rules` |
| Suite completa · ambos flavors · iOS · guardarraíles con `--rerun-tasks` | ✅ |
| Función: typecheck estricto | ✅ `tsc --noEmit` |
| Provisional → copy «sin confirmar» en los 9 locales | ✅ |

### ⏳ Lo que SOLO prueba el campo — y esto no está hecho

- **Publicar desde el móvil A dentro de una zona del B, con la app de B cerrada.** Es la única
  prueba que vale de este ticket, y hasta desplegar la función no existe.
- ✅ **La función SÍ tiene tests**: 13 con `node --test`, sin emulador. Salieron de partir el
  fichero en dos — `fanout.ts` decide, `index.ts` hace I/O —, que es el mismo corte que usa la app.
  Cubren plaza propia, fuera del círculo, retractada, caducada, sin geohash, provisional marcada,
  suscripción caducada, suscripción sin dispositivo, dos zonas solapadas, y que solo se borre un
  token realmente muerto.
- ⛔ Lo que sigue sin probarse es el **pegamento**: que el trigger se dispare y que FCM entregue. Eso
  solo lo dice el campo.

### 🔑 Decisiones que quedan escritas

- **El servidor no sabe quién es Plus, y no debe saberlo.** Estar en el índice ES el permiso. Lo
  mantiene el cliente con las mismas tres condiciones de siempre, así que perder Plus vacía tu fila
  sin tocar el `notifyOnSpot` de ninguna zona: pausa, no olvido, en un sitio nuevo.
- **El geohash no se duplica en TypeScript.** El cliente guarda las celdas que cubre su círculo; el
  servidor corta el geohash que la plaza ya trae y pregunta `array-contains`. Una segunda
  implementación de ese algoritmo, en otro lenguaje, funciona hasta el día que dos bordes discrepan
  y un barrio entero deja de recibir avisos en silencio.
- **Mensaje de DATOS, no de notificación.** Decide el cliente, porque el cliente es el único que
  sabe qué dijo ya (`zone_alerted_spots`). Dejar que FCM pinte la tarjeta habría puesto una segunda
  copia de la regla de deduplicación dentro de la bandeja del sistema, donde nada la puede comprobar.
- ⚠️ **`read: if false` era un error** y duró diez minutos: sin leer tus propias filas no se pueden
  BORRAR las que sobran. La lectura acotada al propio uid **no** reabre el barrido — Firestore
  rechaza una consulta que no puede demostrar permitida antes de ejecutarla.
- ⚠️ **`getToken()` y `onNewToken` están deprecados** en firebase-messaging 25.1.3 (migración
  FCM→FID) y **no hay sustituto**: la doc apunta a `onNewToken`, que el mismo SDK también marca. Sin
  fecha de retirada. Suprimido a sabiendas y comentado, no por reflejo.

## Consumidores auditados

| Quién | Estado |
|---|---|
| `ZoneSpotWatchWorker` · `ZoneWatchScheduler` · `RunZoneSpotWatchUseCase` (+ su test) | ✅ **borrados**; `grep` en `shared/` y `app/` no deja ni una mención |
| `ObserveZoneSpotAlertsUseCase` (stream) | ✅ intacto — sigue siendo el carril de app abierta, y comparte la memoria con el push |
| `zone_alerted_spots` | ✅ **no se retira**: con push también hace falta saber qué se dijo. Gana `hasAnnounced` / `recordAnnounced` |
| `AppNotificationManager.showZoneSpotAlert` | ✅ gana `isProvisional` con default, así que los 4 impls siguen compilando |
| `AppPreferences` × 4 impls | ✅ `observeNotifyZoneSpots()` en las cuatro — el índice tiene que reescribirse al silenciar el canal, y una lectura puntual dejaría al servidor empujando |
| `firebase.json` | ✅ bloque `functions` junto al de hosting que ya existía |
| Política de privacidad (EN + ES) y `DATA-SAFETY-FORM.md` | ✅ el token entra en `Device or other IDs`, que ya estaba declarado; `Compartido = No` no cambia |

## Lo que queda antes de que esto exista

1. **Desplegar** (`firebase deploy --only functions,firestore:rules`) — requiere Blaze, ya activo.
2. **Campo**: dos móviles, app cerrada en el receptor.
3. Seguimiento: **webhook de RevenueCat** para sacar del índice a quien caduca y no vuelve a abrir la
   app. Hoy lo acota el `expiresAt` de 30 días.
