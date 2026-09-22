# SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001 · el espejo remoto se queda a medias hasta que alguien abre la app

**Estado:** ✅ Done · `testAll` verde · iOS compila · 4 de 5 testigos falsificados

## Problema

Encontrado leyendo Firestore para diagnosticar la issue #125: el aparcamiento de la cuenta
`oglnuRxQ…` está `isActive: false` con **`endedAtMs: null`** y `publishedSpot: false`, aunque **todos**
los caminos de cierre del DAO estampan la hora (`COALESCE(endedAtMs, :endedAtMs)`).

Su `updatedAt` remoto lo explica entero: **`1789844649865`, que es el del alta** (19-09 19:04). Desde
que se creó el documento, la ÚNICA escritura remota fue el parche del flag.

```kotlin
// RemoteUserProfileDataSourceImpl.clearParkingSessionActiveFlag
.update(mapOf(FIELD_IS_ACTIVE to false))   // un campo, y ni siquiera toca updatedAt
```

No hay pérdida de datos **local**: como el parche no refresca `updatedAt`, la copia remota nunca
parece más nueva y la fila local con `pendingSync = 1` gana el reconcile. La hora de cierre está a
salvo en Room. Lo que está roto es la **convergencia del espejo**: el documento remoto de una sesión
cerrada dice «cerrada» sin decir **cuándo**, ni si publicó plaza.

¿Y quién sube la sesión entera? `pushPendingParkingSessions()`. **Tiene un solo llamante**:
`AppViewModel.init` cuando estás online, más la reconexión. O sea que un cierre **en background** —que
es el caso NORMAL, la detección cierra la sesión con el móvil en el bolsillo— no llega entero a
Firestore **hasta que el usuario vuelve a abrir la app**. Si no la abre, nunca.

### El comentario que lo tapaba

`UserParkingRepositoryImpl.clearActiveParkingSession` afirma:

> *«endedAtMs/publishedSpot ride the SAME pendingSync: the fast-path worker only patches isActive
> remotely; **the outbox drainer's full-document push carries the close provenance**»*

Falso. El outbox drainer (`SyncOutboxDrainer`) **no tiene ninguna op que suba una sesión cerrada
entera**: su `ClearActive` llama al mismo parche de un campo. Quien sube el documento completo es
`pushPendingParkingSessions()`, que es otro mecanismo con otro disparador. Dos cosas distintas
llamadas igual dentro de un comentario, y la frase sonaba a que estaba cubierto.

Las otras dos vecinas repiten la misma frase: `restoreParkingSession` («rides pendingSync on the
outbox drainer's full-document push») y `retractParkingSession` («rides `pendingSync` on the outbox
drainer's full-document push»). Los tres caminos que cambian el estado de cierre de una sesión
confían en un push que sólo ocurre si abres la app.

## A quién le duele

- **Otro móvil del mismo usuario**: recibe la sesión cerrada sin hora de cierre, y el reconcile no
  puede inventarla.
- **El espejo del coche compartido**: el grupo lee un cierre sin hora.
- **Cualquier lectura remota nuestra** — que es exactamente cómo apareció esto.

## Doctrina violada

- **Sistemas, no parches**: el invariante «una sesión cerrada llega entera» estaba escrito en tres
  comentarios y en ningún sitio ejecutable.
- **Fallo asimétrico**: el silencio del espejo no es visible desde el móvil que lo causó. Nada falla,
  nada se reintenta, nadie se entera.

## Diseño

**Todo cambio local del estado de cierre de una sesión empuja la sesión ENTERA, sin esperar a que
alguien abra la app.**

Nace `ParkingSyncScheduler.enqueueParkingSessionSnapshot(session)` — «sube el estado ACTUAL de esta
sesión como documento completo, ordenado DESPUÉS de lo que ya esté encolado para ella». Cada
plataforma lo resuelve con el carril que ya tiene:

- **iOS**: `OutboxOp.SaveSession(id, previousSessionId = null)`, que ya relee Room al drenar y sube el
  documento entero con `updatedAt` fresco. Cero código nuevo.
- **Android**: el mismo `SaveNewParkingSessionWorker`, pero **`APPEND_OR_REPLACE`** en la cadena de la
  sesión en vez del `REPLACE` que usa el alta. ⛔ Con `REPLACE` cancelaría el parche del flag y la
  actualización de dirección que ya estuvieran en vuelo — que es justo el orden que
  [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001] peleó para conseguir.

Lo llaman los tres caminos: `clearActiveParkingSession`, `retractParkingSession` y
`restoreParkingSession`.

⛔ **Se AÑADE, no sustituye.** El parche del flag se queda: es el camino rápido, y además es quien
avisa al espejo del coche compartido (`sharedCarMirror.onSessionClosed`). Cambiar la op por otra
tocaría el grupo de rebote. La forma final es la que el resto del código ya usa: **camino rápido +
red durable detrás.**

⛔ **El método NO lleva implementación por defecto.** Un no-op heredado es exactamente cómo se pierde
una plataforma en silencio — la lección de [DET-KOIN-MODULE-VERIFY-001]. Que las dos contesten.

## Testigos

`UserParkingRepositoryImplTest` — **el repositorio no tenía un solo test**, y el motivo era que su
única dependencia interesante es un `@Dao` de Room. Pero es una INTERFAZ, así que nace
`FakeUserParkingDao` (tabla en memoria; lo que no hace falta lanza `NotImplementedError`, porque un
doble que devuelve vacío calladamente es cómo un test pasa sin ejercitar nada) y con él cinco
testigos. Quitados los tres `pushSessionSnapshot`, **caen cuatro**:

- cerrar sube el documento entero, **con `endedAtMs` y `publishedSpot`**;
- lo que sube es el estado de la fila YA escrita, no el objeto que el llamante tenía en la mano;
- reabrir (salida refutada) sube `isActive` de vuelta y sin hora de cierre;
- retractar sube su `retractedAtMs`.

El quinto —una fila que ya no existe no encola nada— pasa en los dos casos: es un guard, no un
cambio de comportamiento.

## Criterio de éxito

- Cerrar una sesión encola el push del documento completo, y lo que se sube lleva `endedAtMs`.
- Retractarla y reabrirla, igual.
- En campo: un pin cerrado con el móvil en el bolsillo aparece en Firestore con su `endedAtMs` sin
  que nadie abra la app.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `clearActiveParkingSession` | «el drainer sube el documento completo» | ✅ ahora lo encola de verdad; comentario corregido |
| `retractParkingSession` | ídem | ✅ |
| `restoreParkingSession` | ídem (y NO puede encolar el flag, lo desharía) | ✅ el snapshot es justo lo que le va |
| `pushPendingParkingSessions()` | red de arranque | ✅ intacta, ahora es la segunda red y no la única |
| `clearParkingSessionActiveFlag` | parche de un campo, sin tocar `updatedAt` | ✅ intacto — y su no-tocar-`updatedAt` es lo que mantiene local autoritativo |
| `sharedCarMirror.onSessionClosed` | cuelga del parche del flag | ✅ intacto, por eso no se sustituye la op |
| `SaveNewParkingSessionWorker` | snapshot en el ENCOLADO | ✅ correcto aquí: la fila ya está cerrada cuando se encola |
| `OutboxOp.SaveSession` | relee Room al drenar | ✅ aún mejor: sube lo que la fila diga en ese momento |
