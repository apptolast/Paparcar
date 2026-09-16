# PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001 · La traza salía por dos puertas con reglas opuestas — cada una pasa a pedir su propio permiso

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Verificado:** `testAll --rerun-tasks` → **3389 tests, 0 fallos** · testigo falsificado (abajo) ·
compila prod e `iosSimulatorArm64`.
**Decisión del USER (16-09):** opción **A** (no se crean sin permiso) con **dos puertas, cada una
con su permiso** — el consentimiento personal NO es techo del grupo.

## Problema

Había **dos interruptores** para el mismo dato —la geometría del recorrido— y hacían **cosas
opuestas**:

| | Interruptor | Default | Qué hacía | Medido en |
|---|---|---|---|---|
| Sesión propia | `appPreferences.syncRouteGeometry` | **false** (y exige Plus) | **No subía** | `RemoteUserProfileDataSourceImpl.kt:102` → `RouteGeometryPrivacy.kt` |
| Coche compartido | `vehicle.shareRoutesInGroup` | **true** | **Subía siempre**; solo ocultaba al leer | `SharedCarMirror.kt` (payload) · `SharedCarHistory.kt` (`takeIf { showRoute }`) |

Consecuencia: **un usuario con el consentimiento personal apagado —que es el default, y además es de
pago— subía su traza entera en cuanto el coche estaba compartido.** `SharedCarMirrorWriter` no
consultaba el interruptor en ningún punto: `sharedCarMirrorPayload()` copiaba `session.routePolyline`
tal cual y `putSession` lo escribía entero.

No era un fallo del espejo. Cada mitad se diseñó bien por separado —
`PRIV-A-TRACE-IS-NOT-A-STAT-001` decidió *no subir sin consentimiento*; `VEH-SHARED-001` decidió
*subir siempre y resolver al leer*, a propósito, para que volver a encender devolviera los viajes
viejos. Lo que nadie comprobó es qué pasa cuando las dos reglas caen sobre el MISMO polyline.

## Doctrina violada

*Sistemas, no parches*: el invariante «la traza sale del móvil solo con permiso» estaba en dos
sitios con dos respuestas. Y la promesa de `PRIV-A-TRACE-IS-NOT-A-STAT-001` dejaba de ser cierta
justo en el caso donde más gente ve el dato.

## La decisión

Se evaluaron dos formas de cumplir el invariante del user (*«o no se crean o no se muestran»*):

- **A · no se crean sin permiso** — una sola regla, declarable en Play en una línea, protege por
  omisión. Coste: volver a encender no resucita lo que nunca se subió.
- **B · se crean siempre y se filtran al leer** — consistencia hacia atrás, pero obliga a declarar
  que la geometría sube SIEMPRE con Ajustes diciendo lo contrario.

El user eligió **A**. Y sobre quién manda, eligió **dos puertas con permiso propio** en vez de hacer
techo el consentimiento personal: hacerlo techo habría convertido *compartir rutas con tu grupo* en
una función de Plus de facto, y hoy un free las comparte. Queda así:

```
syncRouteGeometry (Plus)  →  TU historial en la nube
shareRoutesInGroup        →  la copia del GRUPO
```

Ninguna sube nada apagada. Son independientes: un free sigue compartiendo con su grupo, y un Plus
con rutas apagadas no sube nada a su historial.

## Diseño

1. `sharedCarMirrorPayload()` recibe `shareRoutes` y **sin valor por defecto**: un default es
   justamente cómo la geometría viajó dos semanas sin que nadie decidiera que debía hacerlo. Sigue
   siendo función pura — la decisión no baja al I/O.
2. `SharedCarMirrorWriter.sharesRoutes()` lee la fila del vehículo y **cae a `false` si no puede
   leerla**, no al default `true` del campo: una fila ilegible no es un permiso. Fallo asimétrico
   aplicado a privacidad — el error barato es una ruta que el grupo no ve, no una que su autor
   nunca aceptó mandar.
3. **El filtro de lectura se queda**, y ahora hay que explicar por qué son dos momentos distintos:
   el interruptor decide lo que viaja **de ahora en adelante**, el filtro decide lo que se ve **de
   lo que ya viajó**. Apagado: ni sube ni se enseña. Encendido otra vez: vuelven las que subieron
   mientras estaba encendido.
4. Los KDoc que afirmaban lo contrario se corrigen en vez de quedarse mintiendo:
   `RouteGeometryPrivacy` decía ser *«the one door every session push converges on»* — cierto
   cuando se escribió, falso desde F8.

## Consecuencia asumida

Un viaje grabado con el interruptor apagado llega al espejo **sin polyline**, así que
`routeHidden()` es false y el historial del grupo no dice «ruta no compartida»: calla. Es correcto —
el espejo genuinamente no tiene ruta que ocultar— pero cambia lo que ve el grupo respecto a hoy.

## Consumidores auditados

`grep routePolyline` sobre todo lo que escribe hacia fuera del móvil:

| Puerta | Veredicto |
|---|---|
| `ParkingHistoryDto` (historial propio) | ✅ ya con permiso (`withoutRouteGeometry`) |
| `SharedCarSessionDto` (espejo del grupo) | 🔧 arreglado aquí |
| `TripRoutePointEntity` / `UserParkingEntity` | ✅ Room local, no sale del móvil |
| `SharedCarHistory.asHistoryEntry` | ✅ lectura, se queda (ver Diseño 3) |
| **No hay una tercera puerta** | el sweep no encontró más escrituras remotas de la geometría |
| `SharedCarMirrorWriter` | ⚠️ **sin test propio**: no existen fakes de `VehicleDao`/`UserParkingDao` y no se inventa infraestructura para un glue de I/O. Lo que protege estructuralmente es que el payload **no admite default** |

## Testigo

`should_leaveTheRouteOnThePhone_when_theAuthorDoesNotShareRoutes` — falsificado devolviendo
`routePolyline = session.routePolyline`: **rojo**. Comprueba además que las CIFRAS
(`routeDistanceMeters`, `searchTimeMs`) y la posición siguen viajando: dicen cuánto, no dónde.

## Lado iOS

Todo en `commonMain` (`domain/sharedcar`, `data/sharedcar`, `data/datasource/remote/dto`): iOS lo
hereda sin código propio.

## Cola

- Política §2.9 y `DATA-SAFETY-FORM.md`: el interruptor ya no «solo oculta», **impide subir**.
  Se actualiza en `legal-data-safety-answers-for-1-2-0-001.md`.
