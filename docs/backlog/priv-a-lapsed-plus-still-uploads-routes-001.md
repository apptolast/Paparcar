# PRIV-A-LAPSED-PLUS-STILL-UPLOADS-ROUTES-001 · Caducar Plus no apaga la subida de rutas, y la política dice que sí

**Estado:** ✅ Done · tres respuestas en la única puerta, y el interruptor dice por qué está en pausa · ⏳ sin ver en mano
**Origen:** revisión independiente, 2.ª vuelta — hallazgo C8.

## Problema

`RemoteUserProfileDataSourceImpl` decide subir la geometría mirando **solo la preferencia**
(`appPreferences.syncRouteGeometry`). Esa preferencia solo se puede encender siendo Plus, pero
**nadie la apaga al perderlo**: quien se dio de baja sigue subiendo su recorrido a la nube.

La política publicada dice, en §2.5, que la ruta sube *«solo si tienes Plus y activas la
sincronización»*. Hoy la primera mitad de esa frase no la comprueba nadie.

## Tensión que hay que resolver, no ignorar

⛔ Choca con la doctrina escrita en `VehicleEntitlementGate`: *«losing Plus never deletes or disables
anything, it only gates what is NEW»*. Aquí **sí** hay que apagar algo, y se sostiene porque lo que
se apaga no es una función del usuario: es una **transferencia de datos suyos**, consentida bajo una
condición que ya no se cumple. Dejar de subir no le quita nada; seguir subiendo le quita el
consentimiento.

Lo que NO debe pasar: borrar lo ya subido. Eso es retención, y la política ya la describe.

## Diseño

Combinar el consentimiento con `observeIsPlus` en la única puerta que ya existe, y decidir qué ve el
usuario en Ajustes: el interruptor tiene que **explicar por qué está apagado**, no aparecer apagado
sin más.

## Resolución propuesta (3.ª vuelta): **pausar, no borrar ni olvidar**

`RemoteUserProfileDataSourceImpl` combina el consentimiento con `isPlus` leído de la caché de
entitlement. El consentimiento **se conserva**: al volver a Plus, vuelve a subir sin que nadie tenga
que acordarse de reactivarlo.

Y con eso la tensión con *«losing Plus never deletes or disables anything»* se disuelve en vez de
esquivarse: esa doctrina protege **lo tuyo** — tus coches, tus zonas, tu historial no se borran ni
se bloquean al caducar. Lo que aquí se para no es algo tuyo: es que **nosotros sigamos prestando un
servicio de pago** (guardar tu recorrido en nuestra nube) a quien ya no lo paga. La política §2.5 ya
lo dice así.



---

## Resuelto (13-10-2026) · decisión del user: **A · reanudar**, y no borrar lo viejo

El user preguntó lo que el doc no contestaba: *«pero si activa Plus se suben todas?»*. La respuesta
medida era **no, y lo que hacía el código no era ni una cosa ni la otra**: con un `set()` de
documento completo, subían «las que se volvieran a empujar por cualquier motivo». Eso no es una
política, es un efecto colateral.

### ⛔⛔ Y el arreglo propuesto rompía el requisito del propio ticket

Este doc decía *«Lo que NO debe pasar: borrar lo ya subido»*. Pero combinar el consentimiento con
`isPlus` en esa única puerta **y nada más** habría hecho justo eso: el `set()` completo envía el
documento sin los campos de geometría, y un `set()` sin un campo **no lo deja en paz, lo BORRA** —
el comentario que ya estaba ahí lo llama *«the retention promise keeping itself»*, y para el
consentimiento es correcto. Para un Plus caducado, no: esas rutas se subieron pagando.

Así que la puerta pasa a tener **tres** respuestas, no dos:

| | geometría nueva | geometría ya en la nube |
|---|---|---|
| sin consentimiento | no sube | **se borra** — es tu decisión, y es la promesa de retención |
| consentimiento + Plus | sube | se actualiza |
| consentimiento + **Plus caducado** | **no sube** | **se congela**: ni se borra ni se refresca |

«Congelar» se implementa reenviando la geometría que **la nube** ya tiene (`withRouteGeometryOf`),
no la del móvil: conservar la local sería una subida nueva disfrazada.

### Por qué NO hubo migración, que es lo primero que pareció

Primero concluí que hacía falta un campo por sesión («¿fue elegible su geometría?») y por tanto Room
v21. Falso: con la geometría remota como fuente de la congelación, el estado necesario **ya vive en
la nube**. Y `update()` campo a campo —la otra salida— se descartó a propósito: una lista de campos
escrita a mano es un contrato que nadie comprueba
[SYNC-THE-WORKDATA-FIELD-LIST-IS-A-CONTRACT-NOBODY-CHECKS-001].

### La tensión con `VehicleEntitlementGate`, resuelta y no esquivada

*«Losing Plus never deletes or disables anything»* protege **lo tuyo**: coches, zonas, historial. Lo
que aquí se para no es algo tuyo, es que **nosotros** sigamos prestando un servicio de pago —guardar
tu recorrido en nuestra nube— a quien ya no lo paga. Y lo tuyo sigue intacto: la ruta se queda en el
móvil, visible, y la que ya subiste no se toca.

### Ajustes dice por qué

El interruptor ya se pintaba apagado (`checked = state.isPlus && state.syncRouteGeometry`), pero sin
explicar nada — y un interruptor que se apaga solo se lee como un bug. Con el consentimiento puesto
y Plus caducado, la fila cuenta **las dos mitades**: lo nuevo no sube, lo ya subido se queda.
`settings_sync_routes_paused`, los **9 locales**.

### Criterio de éxito — falsificado

`RouteGeometryPrivacyTest`, 4 tests. El que importa es el testigo de que **la lista de los cuatro
campos está completa**: congelar contra una nube vacía tiene que dar *exactamente* el documento
escurrido, así que un quinto campo de geometría que alguien olvide en una de las dos funciones sale
en rojo sin repetir la lista. Falsificado quitando `routeInferredResolution` de
`withRouteGeometryOf`: **dos tests en rojo**.

### Consumidores auditados

- La **puerta única** (`saveParkingSession`) — ✔ los cuatro caminos de empuje (worker de guardado,
  drenador de pendientes, drenador de iOS, mutaciones en sitio) convergen aquí, que es por lo que la
  regla vive en este sitio y no en cada llamador.
- El **espejo del coche compartido** — ✔ exento: pregunta su propio permiso
  (`shareRoutesInGroup`) y no el de la nube personal, desde
  [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001]. No se toca.
- `DataModule` — ✔ `entitlement = getOrNull()`, como sus dos hermanos. ⛔ Y el **nulo significa NO
  FILTRAR**, no «es FREE»: si el grafo no trajera el repositorio, dejar de subir en silencio a quien
  paga sería una regresión muda, peor que el defecto que se arregla.
- `MOCK` — ✔ `compileMockDebugKotlin` verde; el flavor mock no registra `EntitlementRepository`, y
  por eso el nulo tiene que significar lo que significa.

### ⏳ Lo que queda, nombrado

- **Sin ver en mano**: el copy de la fila en pausa.
- **El residuo del `set()` completo**: una sesión grabada durante el hueco que reciba un empujón
  completo **después** de volver a Plus subiría entonces su geometría. Es estrecho —exige que esa
  sesión se reescriba más tarde (una edición tardía de la dirección)— y cerrarlo del todo sí pediría
  el campo por sesión. Se deja escrito aquí en vez de fingir que no existe.
- `MONEY-A-LAPSED-OWNER-KEEPS-THE-GROUP-001` sigue 🔴 y comparte la mitad del problema (qué deja de
  poder hacer una cuenta caducada), pero está bloqueado por un dato de servidor.
