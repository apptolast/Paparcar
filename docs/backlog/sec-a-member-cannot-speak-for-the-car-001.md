# SEC-A-MEMBER-CANNOT-SPEAK-FOR-THE-CAR-001 · Cualquier miembro podía firmar la cabecera del coche en nombre de otro

**Estado:** ✅ Done (2026-09-16) — reglas desplegadas y releídas; el hash vive en `MEMORY.md`.
**Origen:** revisión de Claude web — C5 en la 2.ª vuelta (sin ticket propio entonces), A4 en la 3.ª.

## Problema

`sharedCars/{carId}/state/current` tenía `allow read, write: if isMember()`. Escribir ahí es decir
**dónde está el coche, quién lo dejó y quién se lo llevó**, y nada obligaba a que la firma fuese del
que escribe: un miembro podía publicar que *Marta* acaba de aparcar donde él quisiera.

Era una travesura hasta el **16-09 a las 22:07**, cuando `onSharedCarParked` se desplegó por primera
vez: desde ese momento esa escritura **dispara un push a todo el grupo**. La misma laguna pasó de
ensuciar una tarjeta a poner una notificación falsa en el móvil de cuatro personas.

## Arreglo

`create`/`update` exigen que la escritura **se firme a sí misma**: `authorUid` o `movedByUid` han de
ser el uid que escribe. `delete`, solo el dueño.

⛔ Y una excepción que el propio código necesita: al salir del grupo se **borra** el nombre de quien
se va (`clearMemberFromState`), y esa escritura no firma nada. Se permite un update que toque
**solo** `authorName`, `movedByUid` y `movedByName` **y solo si esos campos ya eran suyos**.

## Consumidores auditados

Los cuatro sitios que escriben `state`, leídos uno a uno antes de desplegar:

| Escritura | Pasa por |
|---|---|
| `putState` (aparcar) | firma propia: `authorUid` es quien aparca |
| `setStateTakenBy` (se lo lleva otro) | firma propia vía `movedByUid` |
| `setStateParked` (se cierra la sesión) | solo corre si la cabecera es de MI sesión, así que `authorUid` ya es mío |
| `clearMemberFromState` (salir/expulsar) | la excepción de los tres campos |

⚠️ **Sin tests de reglas**: el repo no tiene infraestructura de emulador para `firestore.rules`, así
que la compatibilidad se verificó **leyendo los cuatro escritores**, no ejecutándolos. Montar esa
infraestructura es su propio ticket, y este es el tercer cambio de reglas del mes que la pide.
