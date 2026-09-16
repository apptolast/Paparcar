# PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001 · Borrar la cuenta deja vivos tu token de push y tu pertenencia al grupo

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Verificado:** `testAll --rerun-tasks` → **3370 tests, 0 fallos** · `:app:compileProdDebugKotlin` y
`:shared:compileKotlinIosSimulatorArm64` OK · testigos falsificados (abajo)
**Aprobado por el USER (16-09):** el cambio de comportamiento de `leave()` (§Decisiones, 2).
**⏳ Pendiente del USER:** desplegar `firestore:rules` **ANTES** de publicar el APK
(§Orden de despliegue) y cerrar el §6 de la política, que ya puede decir la verdad.

## Problema

`DeleteAccountUseCase` recorre `userScopedRepos` y luego borra la cuenta de Auth
(`DeleteAccountUseCase.kt:28-30`). La lista, cableada en `DomainModule.kt`, eran **cinco**
repositorios. Nadie barría las dos colecciones que nacieron en septiembre y guardan un
**identificador de dispositivo**:

| Qué sobrevivía | Dónde | Qué contiene |
|---|---|---|
| Ficha de miembro del coche compartido | `sharedCars/{carId}/members/{uid}` | uid, nombre visible, `sharesRoutes` y **tokens FCM** (`functions/src/index.ts:205`) |
| Suscripción de alertas de zona | `zoneAlertSubs/{uid}` | centro/radio/nombre de tus zonas y **otra copia de los tokens** |

Y al medirlo apareció **un agujero mayor, que no necesita borrar ninguna cuenta**: `leave()` borraba
la ficha de miembro y **no las sesiones**. Salir de un grupo dejaba dentro tu recorrido —
ubicación, hora y, si el interruptor estaba encendido, la ruta— firmado por alguien que ya no
pertenece al grupo, **visible para los que siguen dentro e inalcanzable para ti**, porque leerlo
exige la pertenencia que acabas de soltar.

Eso además hacía incompleto el arreglo del borrado de cuenta: quien primero sale del grupo y luego
borra su cuenta no tenía ya ningún coche compartido que barrer.

## Doctrina violada

La política publicada (§6) dice que borrar la cuenta borra tu cuenta y los datos asociados. Es la
misma familia que `account-delete-sweeps-diagnostics-001.md`, cerrado en agosto — y la causa es
idéntica: **la erasure es una LISTA, y una lista es lo que nadie se acuerda de ampliar**. El KDoc de
`DeleteAccountUseCase` ya avisaba de que implementar la interfaz es solo la mitad del contrato.

## Diseño — el sistema, no el parche

**1 · El borrado de cuenta no estrena semántica: ejecuta las dos salidas que ya existían.**
`SharedCarRepository.deleteAllData()` recorre tus coches compartidos y, por cada uno, llama a
`stopSharing()` si eres el dueño o a `leave()` si te lo prestaron. Disolver el grupo del dueño **no
es daño colateral**: el espejo es una proyección de SU coche, y nadie pierde su historial, porque la
sesión personal es la fuente de verdad. Lo dice ya el KDoc de `Vehicle.unlinked()` para la dirección
contraria: *compartir presta el coche, no el recuerdo de haberlo usado*.

**2 · `leave()` se lleva las sesiones que firmaste**, vía `RemoteSharedCarDataSource.deleteSessionsOf`.
⛔ **Antes de borrar la ficha de miembro**: leerlas exige `isMember()`, así que invertir esas dos
líneas no borra nada y reporta éxito. El fake reproduce esa regla a propósito.

**3 · El guard que impide la próxima omisión.** `AccountDeletionGuardrailTest` (Konsist) lee todas
las interfaces que extienden `UserScopedRepository` y exige que cada una esté en la lista de
`DomainModule`. La prohibición que faltaba no era «implementa la interfaz» —eso ya lo obliga el
compilador— sino «está cableada en la cadena», que es justo lo que compila igual de bien cuando
falta. Dos poblaciones con testigo (`GuardrailScope.productionFilesMentioning`), porque el fichero
que cablea **no menciona el marcador**: nombra los repositorios concretos.

**4 · El orden de la cadena pasa a estar razonado en el sitio donde se decide** (`DomainModule`):
tokens primero, `SharedCar` antes que `Vehicle` —los coches son el índice por el que se encuentran
los grupos—, y Diagnostics al final.

## Hallazgo que bloqueaba todo: las reglas no dejaban

```
match /sessions/{sessionId} {
  allow delete: if … ownerUid == request.auth.uid;   // SOLO el dueño
}
```

Un miembro **no podía borrar sus propias sesiones**. Sin tocar `firestore.rules`, el barrido habría
fallado en silencio contra producción — exactamente lo que mordió el 16-09 con F8, donde se desplegó
la función y no las reglas. Ahora borra el autor **o** el dueño, y la regla no puede pedir
`isMember()` para el autor porque el barrido corre justo cuando esa pertenencia se deshace.

## Decisiones tomadas

1. **El dueño que borra su cuenta disuelve el grupo para todos.** Decidido por el user (16-09).
   Justificación medida, no estética: nadie pierde su propio historial.
2. ⚠️ **`leave()` cambia de comportamiento** para todo el mundo, no solo para quien borra la cuenta:
   al salir de un grupo, tus sesiones dejan de estar en él. Es lo correcto en privacidad y lo que
   cierra el agujero del §Problema, pero el grupo verá encogerse su historial compartido cuando
   alguien se va. **Esto necesita el visto bueno del user antes de mergear.**
3. `zoneAlertSubs` implementa la erasure como `replaceAll(uid, emptyList())` — su propio verbo con
   una lista vacía, que es lo que la interfaz ya documentaba como el caso interesante.

## Criterio de éxito

- Salir de un grupo no deja ninguna sesión tuya dentro.
- Borrar la cuenta siendo miembro: el grupo sobrevive; tu ficha y tus sesiones no.
- Borrar la cuenta siendo dueño: no queda coche, ni miembros, ni sesiones.
- Añadir un repositorio user-scoped sin cablearlo pone la suite en rojo.

## Testigos (falsificados, no supuestos)

| Testigo | Cómo se falsificó | Resultado |
|---|---|---|
| `should_takeTheTripsTheySigned_when_aMemberLeaves` + `should_walkOutOfEveryGroup_…` | comentando `remote.deleteSessionsOf(...)` en `leave()` | **2 rojos** (la del dueño sigue verde: `deleteCar` ya lo cubría) |
| `AccountDeletionGuardrailTest` | quitando `get<ZoneAlertSubscriptionRepository>()` del cableado | **rojo, nombrando la que falta** |

⛔ Konsist exige `--rerun-tasks`: un cambio que no toca bytecode sale UP-TO-DATE y el test no llega
a ejecutarse. Los 19 s de la primera pasada de `testAll` eran eso, no una suite verde.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `SharedCarRepositoryImpl.stopSharing` | ✅ ya barría todo vía `deleteCar` (sesiones → miembros → estado → invites → doc) |
| `SharedCarRepositoryImpl.leave` | 🔧 arreglado (sesiones antes que la ficha) |
| `RemoteSharedCarDataSourceImpl.deleteCar` | ✅ sin cambios |
| `syncFromRemote` | ✅ el miembro cuyo espejo desaparece se auto-desvincula (`:273-278`); no quedan filas huérfanas |
| `removeMember` (el dueño echa a alguien) | ⚠️ **fuera de alcance**: expulsar no borra las sesiones del expulsado. Es otra decisión de producto — echar a alguien no es lo mismo que irse — y no la toma este ticket |
| `ACCOUNT-DELETE-RUNBOOK.md` | 🔧 actualizado: `zoneAlertSubs` + el paso 2.1, que no se puede scriptear a ciegas porque `sharedCars` no está indexado por uid |
| `hosting/public/privacy-policy.html` §6 | ⏳ se puede cerrar **cuando esto esté en master**, no antes |

## Orden de despliegue

⛔ **Reglas primero, APK después.** Si el APK sale antes, el barrido de sesiones de los miembros
falla con permiso denegado, se traga el error (está en `runCatching` a propósito, para no bloquear
el borrado de la cuenta) y deja el rastro que este ticket existe para quitar.

```bash
firebase deploy --only firestore:rules --project pap-26
```

Y releer el ruleset después: un deploy no se da por bueno con su propio `success`.

## Lado iOS

Todo el cambio vive en `commonMain` (`domain/repository`, `data/repository`,
`data/datasource/remote`) más `firestore.rules`, que es del servidor. iOS lo hereda sin código
propio; verificado con `:shared:compileKotlinIosSimulatorArm64`. El token APNs viaja por el mismo
campo `tokens`, así que queda barrido por la misma vía.
