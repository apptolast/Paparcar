# OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001 · Preguntar a tres espejos no puede costar tres veces la paciencia de uno

**Estado:** ✅ Done · rama `bugfix/OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001-parkings-hang` ·
worktree `../Paparcar-overpass-deadline`

## Problema

El carril de parkings se queda cerca de un minuto en su esqueleto de carga y acaba sin traer nada.
Reportado desde device el 10-09-2026.

El carril de parkings tiene UNA sola fuente — OSM vía Overpass (`OsmPublicParkingsSource`) — mientras
que el de combustible en España va al Ministerio primero. Por eso el usuario nota la diferencia entre
un carril y el otro: solo uno paga el failover.

**Medido el 10-09-2026, 19:0x, misma query que manda la app** (`amenity=parking`, radio 1.500 m,
centro Sol, `[timeout:20]`):

| espejo | conexión TCP | handshake TLS | primer byte |
|---|---|---|---|
| `overpass-api.de` | 0,09 s | ok | **200 en 2,0 s**, 29,5 kB |
| `overpass.private.coffee` | 0,07 s | 0,12 s | **nada en 60 s** |
| `overpass.kumi.systems` | 0,07 s | ok | ok (sano) |

⚠️ El cuelgue de `kumi.systems` que vi primero era **artefacto de mi máquina** (interceptación TLS de
Norton, ya documentada en memoria): desde una red independiente responde bien. Lo que sí es real es
`private.coffee`, que **no está caída sino saturada** — su propio `/api/status` declara 15 consultas
en curso, la más antigua de 3 minutos, y límites de hasta 600 s. Un servidor así acepta la conexión,
completa el TLS, recibe la petición y **calla**. Es el peor caso posible para un failover ciego.

Y `private.coffee` es el **primer fallback** de `OverpassEndpoints.ALL`. O sea: cada vez que el
primario falla o va lento, el usuario paga el silencio del segundo antes de llegar al tercero.

## Doctrina violada

*Deadline invariant* — el mismo que `FuelHttpClient` enuncia para una petición suelta:

> «una petición sin deadline es una petición que no puede fallar nunca».

`OverpassClient.fetch` lo cumple **por espejo** (25 s de `requestTimeoutMillis`) y lo rompe **para la
vuelta entera**: recorre `endpoints` sin presupuesto ninguno, así que el techo real es
`nº de espejos × 25 s = 75 s`. El invariante que faltaba, y que este ticket coloca:

> **Preguntar a N servidores no puede costar N veces la paciencia de uno.**

⛔ Y no se arregla con un `withTimeout` alrededor de la vuelta: eso ya se pagó en
`OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001` — un muro exterior **anula el failover sin que nada
falle**, cortando la vuelta a mitad y devolviendo silencio en vez de la respuesta del tercero. El
presupuesto se **gasta**, no se amuralla.

## Señales / datos disponibles

- `OverpassClient` ya loguea qué espejo pasó turno y por qué (`overpass <host> passed: <motivo>`),
  así que el reparto del tiempo es diagnosticable desde `parkdiag.log` sin instrumentar nada nuevo.
- Cada query DECLARA su propio presupuesto de servidor en el prólogo `[out:json][timeout:N];`
  (8 s en places, 20 s en fuel / parkings / road network). Ese número ya existe: es la paciencia
  que el servidor mismo tiene concedida, y por tanto la única cifra honesta para la del cliente.
- Al fallar, `DiskBackedPublicParkingsSource` responde desde Room con su sello `rememberedAtMs`. Es
  decir: **fallar rápido no deja al usuario sin nada** — le devuelve lo recordado, fechado. El minuto
  de espera actual no compra información, solo la retrasa.

## Diseño

Todo vive en `OverpassClient`, que es **el único sitio** por el que hablan con Overpass los cinco
consumidores (ver barrido abajo). Nada cambia en las fuentes ni en los parsers.

Dos reglas, y las dos salen de una misma idea — *la paciencia solo vale la pena cuando no queda nadie
más a quien preguntar*:

1. **Un espejo que no ha dicho ni un byte pasa turno.** El guardián es el *time-to-first-byte*
   (`socketTimeoutMillis`, inactividad entre bytes), corto y por petición — no el global del
   `HttpClient`, que lo comparten Minetur y Francia con descargas grandes que sí necesitan paciencia.
   Un servidor que calcula manda cabecera; uno saturado o colgado, no.
2. **El último espejo hereda toda la paciencia que quede.** Si los anteriores pasaron turno, ya no hay
   a quién fallar por encima: ahí el presupuesto restante se le da entero, acotado por el techo de la
   vuelta.

El presupuesto por intento sale del número que la query ya declara (`[timeout:N]`), leído por quien lo
va a hacer cumplir, para que no existan dos cifras que puedan divergir.

Techo de la vuelta: 75 s → **~30 s**, y el caso común de fallo (primario 504 rápido, segundo mudo,
tercero contesta) baja a ~11 s.

## Criterio de éxito

- ✅ **10 tests nuevos, verdes** (`OverpassWalkPatienceTest` 6 + `OverpassClientDeadlineTest` 4), y la
  suite entera sigue verde: **2.680 tests, 0 fallos** en 242 clases.
- ✅ **Falsificados**: subiendo `WALK_CEILING_MS` a 3.000 s (o sea, quitando el techo), se ponen rojos
  exactamente los cuatro que hablan de él — `should_stopAsking_when_theBudgetCannotFundARealTry`,
  `should_giveTheLastMirrorEveryThingLeft…`, `should_neverExceedTheWalkCeiling…` y
  `should_handAQuietMirrorEightSeconds…`. No son verdes por casualidad.
- ✅ **El failover sigue entero**: `should_keepTheFailoverWhole_when_anEarlyMirrorRefuses` cubre
  justo la regresión que este ticket podría causar, y los 5 tests de
  `OsmFuelStationsFailoverTest` siguen pasando sin tocarlos.
- ⏳ **En device** (pendiente): entrar en el carril de parkings con el primario degradado tiene que responder en
  decenas de segundos como mucho, y si no puede, enseñar los parkings recordados con su fecha en vez
  del esqueleto.

⛔ El tiempo en los tests es un **reloj falso inyectado**, no `delay`: que un socket enmudezca es un
evento de motor que `MockEngine` no sabe representar, así que escenificarlo con sleeps sería testear
el test. Lo que sí se demuestra —y es lo que se rompió en device— es **cuánta paciencia recibe cada
espejo** (leída de la capability de Ktor que viaja en la petición) y **cuándo la vuelta deja de
repartirla**.

## Consumidores auditados

`grep -rn "OverpassClient(" --include=*.kt shared/src` → cinco, **todos** por el mismo cliente, así
que el deadline los cubre a los cinco sin tocarlos:

| consumidor | query | presupuesto declarado | estado |
|---|---|---|---|
| `OsmPublicParkingsSource` | car parks | 20 s | cubierto (es el que mordió) |
| `OsmFuelStationsSource` | gasolineras sin feed nacional | 20 s | cubierto |
| `OsmStationNames` (Francia) | nombres por ref oficial | 20 s | cubierto |
| `OverpassPlacesDataSource` | POI del aparcamiento | 8 s | cubierto |
| `OverpassRoadNetworkDataSource` | red viaria | 20 s | cubierto |

⛔ No hay ninguna vía que hable con Overpass por su cuenta: eso ya se barrió y se cerró en
`OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001` (`04f4747c`).

## Fuera de alcance (apuntado, no hecho)

- **Caja de castigo para el espejo mudo.** Sin memoria entre peticiones, cada ask paga otra vez el
  silencio del segundo espejo mientras dure su saturación. Un "no preguntes a este durante 10 min"
  ahorraría ese coste repetido, pero es estado compartido entre las cinco instancias de
  `OverpassClient` y merece medirse antes: con el deadline puesto, el coste repetido es de segundos,
  no de un minuto.
- **Reordenar `OverpassEndpoints.ALL`.** Hoy no toca: `private.coffee` no está caída, está
  saturada, y su KDoc documenta una medición del 09-09 que sigue siendo cierta. Degradarla por una
  tarde mala sería cambiar una lista por ruido.
