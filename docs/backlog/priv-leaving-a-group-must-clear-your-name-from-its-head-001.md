# PRIV-LEAVING-A-GROUP-MUST-CLEAR-YOUR-NAME-FROM-ITS-HEAD-001 · Salir del grupo borraba tu historial pero dejaba tu nombre en la tarjeta que el grupo mira

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Verificado:** `testAll --rerun-tasks` → **3400 tests, 0 fallos** · testigo falsificado (abajo).
**Origen:** revisión legal de la 1.2.0 (Claude web) sobre `9812642`, punto 1.

## Problema

`PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001` se llevó las sesiones y la ficha de miembro al
salir de un grupo. Quedó fuera **`sharedCars/{carId}/state/current`**, que no es una sesión: es el
documento ÚNICO que lee la tarjeta del coche, y lleva dos nombres —quién lo dejó ahí (`authorName`)
y quién se lo llevó (`movedByName`)—. Resultado: el historial se limpiaba y la portada seguía
diciendo «aparcado por Nacho» de alguien que ya no está en el grupo.

Y §6 de la política ya promete, publicada, que al salirte se va tu parte.

## Diseño

`RemoteSharedCarDataSource.clearMemberFromState(carId, uid)`, llamado desde `leave()` entre el
barrido de sesiones y el borrado de la ficha:

- si `state.authorUid == uid` → `authorName = null`
- si `state.movedByUid == uid` → `movedByUid = null` y `movedByName = null`
- **la posición no se toca**: dónde está el coche es del grupo, y borrarla dejaría al dueño sin
  saber dónde tiene su propio coche. Lo que se va es el nombre, no el sitio.
- **no se sella `updatedAtMs`**: el coche no se ha movido. Firestore empuja el cambio a quien
  escucha igual, así que sellarlo solo mentiría sobre cuándo cambió el estado del coche.
- `update` con campos, nunca `set`: un `set` reescribiría la posición del grupo con lo que este
  móvil tuviera en la mano.

⛔ **Orden**: va ANTES de `deleteMember`, igual que el barrido de sesiones — escribir en `state`
exige `isMember()`. El fake reproduce esa regla a propósito, para que invertir las líneas salga rojo
en la suite y no en producción.

`authorUid` se queda. Es la decisión de la revisión y es la correcta: identifica QUÉ aparcamiento
está mostrando la tarjeta, y vaciarlo dejaría una portada sin sujeto. Lo que era dato personal
legible —el nombre— es lo que se va.

## Testigo

`should_takeTheirNameOffTheGroupsCard_when_aMemberLeaves` — falsificado quitando la llamada de
`leave()`: **rojo**. Y su pareja, `should_leaveTheOtherMembersNameAlone_when_someoneElseLeaves`,
comprueba lo contrario: que irse uno no toque la firma de otro.

## De paso, dos comentarios que afirmaban invariantes FALSOS

No son cosmética: los dos prometen una prohibición que no existe, y el siguiente lector razona
sobre ellas.

| Dónde | Decía | Realidad |
|---|---|---|
| `RemoteUserProfileDataSourceImpl` | «the mirror never carries geometry at all (its type has no field for it)» | `SharedCarSessionDto.routePolyline` existe desde F8. Pasar `session` es seguro por la PUERTA (`shareRoutesInGroup`), no por el tipo |
| `ZoneAlertSubscriptionRepository` | «nobody — not even its owner — may READ this collection» | Las reglas permiten leer acotado al propio uid, y `replaceAll` lee en su primera línea. Lo prohibido es el barrido por `cells array-contains`, no la lectura |

## Documentos que van con el código

- `ACCOUNT-DELETE-RUNBOOK.md`: ⛔ `zoneAlertSubs/<uid>` **no existe** —los ids son `{uid}_{zoneId}`,
  uno por zona— así que ese borrado salía bien sin borrar nada, con los tokens dentro; ahora se
  filtra por campo. Se añade `diagnostics_reports/<uid>`. Y en el paso del coche propio, el **código
  de invitación vivo** se borra ANTES: vive en la colección raíz `sharedCarInvites` y el `-r` del
  coche no lo alcanza.
- `DATA-SAFETY-FORM.md`: misma corrección del id de `zoneAlertSubs` en las dos menciones.
- Política §2.9 (EN+ES): los miembros ven tu nombre **y foto** de perfil, y que fuiste tú quien se
  llevó el coche cuando el móvil puede saberlo por el Bluetooth. §5: al salirte, tus aparcamientos
  **se borran** de la copia compartida — que es lo que ahora hace el código.

## Lado iOS

Todo `commonMain` más `firestore.rules` (que no cambia aquí: escribir en `state` ya exigía ser
miembro). iOS lo hereda sin código propio.
