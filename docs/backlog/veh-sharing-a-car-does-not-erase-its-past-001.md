# VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001 · el historial del grupo sustituía al tuyo en vez de sumarse

**Estado:** ✅ Done · rama `bugfix/VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001-union` · worktree `../Paparcar-shared-past`

## Problema

El día que el dueño comparte su coche, la página de ese coche se queda **en blanco**: historial
vacío, cifras a cero, gráfico sin barras. Todo su pasado con ese coche desaparece de la vista.

Detectado por el user el 16-09-2026 sobre su Ford Focus recién compartido. La contradicción era
visible en la misma pantalla: la hero card decía «Aparcado · hace 1 día» mientras el historial de
debajo no tenía una sola fila — dos fuentes distintas contándose cosas opuestas.

Nada se borra del disco: las sesiones siguen en Room y en Firestore y vuelven enteras con el chip
«Solo yo». Lo que se pierde es la vista por defecto, que es la que todo el mundo mira.

### El mismo agujero, por su otra punta: el parpadeo al DEJAR de compartir

Reportado por el user el mismo día: al pulsar «dejar de compartir», *«parpadea el empty en la
pantalla como si no tuviera datos, en vez del skeleton»*.

Es este bug visto al revés. Al disolver el grupo, el espejo se vacía un instante **antes** de que
Room propague `sharedCarId = null`, así que durante esa ventana el coche todavía cuenta como
compartido y su feed ya está a cero — y el reemplazo resolvía una lista vacía. La pantalla entonces
anuncia «aún no has aparcado» (un `Resolved` sin filas), **no** el skeleton, que es lo que
significaría «todavía no lo sé» [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001].

No hace falta arreglarlo aparte: con la unión, un feed vacío no puede vaciar nada. La ventana deja
de existir en vez de taparse con un skeleton.

## Doctrina violada

La del propio diseño de la feature, escrita en el artifact *«Coche compartido · cómo quedaría
montado»* (15-09-2026):

> «la pestaña del coche calcula stats e historial sobre **las sesiones de todos**»

«De todos» es una **unión**. Lo implementado es un **reemplazo**.

Y la regla del user que la fija en una frase (16-09-2026): *el dueño pone el coche en compartido y
**sigue con su pantalla de siempre**, con la particularidad nueva de que ahora es compartido y tiene
el filtro «solo yo». Al que se une le aparece la pantalla virgen* — que es correcto, porque él no ha
conducido ese coche todavía.

## Señales / datos disponibles

`VehiclesViewModel.kt:249-252`:

```kotlin
val groupRows = group[vId]
    ?.takeIf { vws.vehicle.isShared && !sharedHistoryMineOnly }
    ?.rows(vId)
timeline = HistoryTimeline.resolve(sessions = groupRows ?: vSessions, …)
```

`groupRows ?: vSessions` — o el espejo, o lo tuyo, nunca los dos. Y el chip arranca en «Todo el
grupo» (`sharedHistoryMineOnly = false` por defecto), así que por defecto se ve **solo el espejo**.

El espejo nace vacío: `SharedCarRepositoryImpl.shareVehicle()` crea el `SharedCar` con
`createdAtMs = now` y **no siembra ninguna sesión pasada** en `sessions/`. Tampoco puede: el pasado
del dueño no es del grupo, y subirlo retroactivamente publicaría a cuatro personas dónde aparcó
antes de invitarlas.

Las cifras caen con la lista porque `HistoryTimeline.resolve` + `ComputePeriodStatsUseCase` se
alimentan de esas mismas filas — un solo agregador [STATS-CORE-001], que es lo correcto y por eso el
agujero se propaga entero.

## Diseño

**El historial de un coche son sus sesiones, vengan de donde vengan.** El espejo AÑADE las de los
demás; no sustituye a las tuyas. El invariante vive en `GroupFeed`, junto a la conversión que ya
hace, para que ningún consumidor tenga que acordarse de deduplicar:

```kotlin
fun rowsWith(mine: List<UserParking>, vehicleId: String): List<UserParking> =
    (mine + rows(vehicleId)).distinctBy { it.id }.sortedByDescending { it.location.timestamp }
```

**Las tuyas ganan el desempate**, y por eso van primero. Una sesión propia posterior a compartir
está en las dos listas con el **mismo id** — `SharedCarMirrorWriter` copia el `UserParking.id` en
`SharedCarSession.sessionId`, comprobado—, y la local es la rica: trae su `armEvidence`, su
`detectionPath` y su ruta sin pasar por la lista blanca del espejo.

Sin punto de inflexión ni fila separadora. Se valoró y sobra: para el dueño no hay dos épocas que
distinguir —es su coche, antes y después—, y para quien se une no hay nada anterior que separar.

### Consecuencia en la nota de ruta

`sharedWithheldRouteIds` marca las sesiones cuyo autor tiene el interruptor de rutas apagado, y la
fila lo dice («ruta no compartida»). Con la unión, **mis propias** filas pasan a venir de la copia
local, que SÍ trae la geometría — así que una fila mía dibujaría su ruta y a la vez se comentaría a
sí misma como no compartida. Contradictorio y redundante: que las mías no viajan ya lo dice el
interruptor de la sección, una vez, en lugar de repetirlo en cada fila. La nota se queda para lo que
existe: lo que OTROS no me enseñan. Se excluye el uid propio al calcularla.

## Criterio de éxito

- El dueño comparte y su página **no cambia**: mismo historial, mismas cifras, mismo gráfico. Solo
  aparecen la sección del grupo y los chips de alcance.
- Cuando otro del grupo aparca, su fila se suma a la lista del dueño, firmada con su nombre.
- «Solo yo» sigue devolviendo exactamente las sesiones propias.
- Un miembro que se une ve la página vacía hasta que el grupo aparque — y ahí es donde muerde
  [UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001], que va en su propia rama.
- Ninguna fila duplicada para una sesión propia posterior a compartir.
- Ninguna fila propia comentada como «ruta no compartida».

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `VehiclesViewModel.kt:249` (`groupRows ?: vSessions`) | **la vía donde mordió** — pasa a `rowsWith(vSessions, vId)`. |
| `GroupFeed.rows()` | se conserva: sigue siendo la conversión del espejo, y ahora la usa `rowsWith`. |
| `GroupFeed.hiddenRouteSessionIds()` | **corregido** — excluye el uid propio (ver arriba). |
| `VehiclesViewModel.setSharedHistoryScope()` (el chip) | **la SEGUNDA copia del mismo reemplazo** — pasa a `rowsWith(mine, vehicleId)`. ⚠️ En la primera pasada lo di por exento «porque ya lee `personalSessions`», y eso solo es cierto en la rama `mineOnly`: en «todo el grupo» hacía `rows()` a secas. Un barrido que mira una rama de un `if` y da el fichero por visto no es un barrido. |
| `VehiclePageContent.kt:318` (`sharedNote`) | **exento** — firma por `session.userId != currentUid`, y las filas locales propias llevan el uid propio, así que siguen sin firmarse. |
| `SharedCarRepositoryImpl.shareVehicle()` | **exento a propósito** — no se siembra el pasado en el espejo. Publicaría retroactivamente a cuatro personas dónde aparcó el dueño antes de invitarlas. La unión es LOCAL, del lado del que mira. |
| `RunMonthRecapUseCase`, panel de cifras | **cubiertos por el mismo agregador** — consumen las filas resueltas, así que heredan la unión sin tocarlos. |
