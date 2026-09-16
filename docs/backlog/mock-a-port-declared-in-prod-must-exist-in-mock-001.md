# MOCK-A-PORT-DECLARED-IN-PROD-MUST-EXIST-IN-MOCK-001 · El tab Vehículos muere en mock, y el verify que existe para esto no lo ve

**Estado:** ✅ Done · rama `bugfix/MOCK-A-PORT-DECLARED-IN-PROD-MUST-EXIST-IN-MOCK-001-verify-the-set` ·
worktree `../Paparcar-mockgraph`
**Abierto:** 2026-09-16 · desde master `c0b9c11a`

## Problema

En el flavor **mock**, entrar por el Dev Catalog con cualquier escenario y tocar **Vehículos** mata
la app. Medido en el Redmi 2201117TY el 16-09 a las 02:13:

```
FATAL EXCEPTION: main
Caused by: org.koin.core.error.InstanceCreationException:
    Could not create instance for '[Factory: 'domain.usecase.fuel.ResolveFuelPriceUseCase']'
Caused by: org.koin.core.error.NoDefinitionFoundException:
    No definition found for type 'domain.repository.AverageFuelPricesRepository' on scope '_root_'
      at DomainModuleKt.domainModule$lambda$0$31(DomainModule.kt:435)
      ← PresentationModuleKt … viewModelOf … (VehiclesViewModel)
```

`dataModule` declara `AverageFuelPricesRepository`; `mockModule` no. Como el grafo mock **no carga
`dataModule`** —lo re-declara a mano, puerto por puerto— el tipo no existe, y muere en la primera
pantalla que pide el `VehiclesViewModel`: Vehículos, el historial y **todo el panel de cifras**.

## Doctrina violada

- **El sistema de pruebas mock se mantiene SIEMPRE en sync** (CLAUDE.md). Un puerto nuevo en
  `dataModule` sin su gemelo en `mockModule` deja el flavor mock resolviendo bien en todas partes
  menos en la pantalla que lo pide.
- Y la de verdad: **un guardarraíl que no puede fallar no es un guardarraíl.**
  `MockKoinModuleVerifyTest` existe exactamente para esto y pasó en verde con la app rota.

## Por qué el verify no lo cazó — el agujero, medido

`Module.verify()` **reflexiona constructores**: para cada definición declarada como clase concreta
(`singleOf`, `factoryOf`, `viewModelOf`) mira los parámetros del constructor y comprueba que cada
tipo esté atado. Es ciego a lo que ocurre DENTRO del cuerpo de una definición escrita a mano:

```kotlin
// DomainModule.kt:435 — el `get<>()` de aquí dentro no lo ve nadie
factory { ResolveFuelPriceUseCase(averages = get<AverageFuelPricesRepository>()::current) }
```

El cuerpo es una lambda porque el use case recibe una **función** (`::current`), no el repositorio.
Es legítimo — y es justo el hueco. `MockModule` ya arrastra cuatro comentarios distintos que dicen
«el stack mock no tiene dataModule, así que este puerto tiene que existir aquí»; uno por cada vez
que alguien lo descubrió tocando la app. Éste es el quinto.

## Diseño — el invariante se comprueba sobre CONJUNTOS, no sobre constructores

La propiedad que importa no es «cada constructor es resoluble», es:

> **Todo puerto de `domain/` que ata el grafo de prod tiene que estar atado también en el de mock.**

Se comprueba comparando los dos conjuntos de claves de Koin, sin reflexión de constructores y sin
arrancar nada:

- **prod** = `dataModule`, el módulo al que `mockModule` SUSTITUYE puerto a puerto. (Los otros tres
  módulos que sólo carga prod quedan fuera a propósito; el porqué, medido, está en «Consumidores
  auditados».)
- **mock** = `loginPresentationModule` + `presentationModule` + `domainModule` + `mockModule`.
- Se filtran las claves a las del paquete `…paparcar.domain.`: los puertos. Un `single` de una clase
  concreta de `data/` o de `:app` no tiene por qué existir en mock — el fake ata la INTERFAZ, que es
  lo único que `domainModule` y `presentationModule` pueden consumir (Clean Architecture es lo que
  hace que este filtro sea exacto y no una heurística).
- Lo que falte y sea deliberado va en una lista blanca **con su motivo escrito**, no en silencio.

Esto caza el fallo sea cual sea el estilo de la definición, porque no mira la definición: mira si el
tipo está o no está.

## Criterio de éxito

- `AverageFuelPricesRepository` atado en `mockModule` → Vehículos y el panel de cifras abren en mock.
- El test de paridad **falla** si se quita ese binding (testigo), y pasa con él.
- `testAll` verde · el tab Vehículos + panel de cifras navegados en el Redmi.

## Consumidores auditados — el primer run del test ES el barrido

`dataModule` ata **tres** puertos de dominio que `mockModule` no tenía. No uno:

| Puerto | Quién lo pide en el grafo mock | Estado |
|---|---|---|
| `AverageFuelPricesRepository` | `ResolveFuelPriceUseCase` ← `VehiclesViewModel` | 🔴 **mataba** Vehículos + panel de cifras |
| `ZoneAlertSubscriptionRepository` | hoy sólo `ZoneAlertSubscriptionSync` (androidMain, módulo que mock no carga) | 🟡 a una pantalla de morder |
| `SharedCarMirror` | `RemoteUserProfileDataSourceImpl`, como parámetro **opcional** (`= null`) | 🟡 a una pantalla de morder |

Los tres se atan con fake. Los dos amarillos podrían haber ido a lista blanca «no lo pide nadie
todavía», que es exactamente la frase que precede a las cuatro veces anteriores: un fake de tres
líneas cierra la clase entera de fallo, una lista blanca hay que releerla.

Con los **otros cuatro módulos** de prod (`androidPlatformModule`, `androidDetectionModule`,
`appModule`) el mismo test listaba once puertos más —sensores, `BatteryMeter`, `TripTrail`, los
records de detección— y **ninguno es un hueco**: el catálogo no arranca servicios, así que nadie los
pide, y allí donde el grafo compartido sí los tocaría sería como parámetro de constructor, que es
justo lo que `MockKoinModuleVerifyTest` ya prueba. Por eso el test compara contra `dataModule` y no
contra todo: once entradas de lista blanca que nadie releería a cambio de cero cobertura nueva.

## Verificación

- **Testigo**: quitando el binding de `AverageFuelPricesRepository`, `MockGraphPortParityTest`
  **falla** y lo nombra; devolviéndolo, pasa. Medido, no supuesto.
- Segundo test del fichero: los dos conjuntos tienen sujetos (≥20 puertos cada uno), para que un
  filtro que dejara de encontrar nada no pase por verde.
- `./gradlew testAll` ✅ · `assembleMockDebug` ✅.
- **Redmi 2201117TY**, mock con sha256 verificado (`a8519d65…`): Dev Catalog → escenario → **Vehículos
  abre** (antes moría) → con «Plus activo» → **«Ver desglose completo»** → panel *Tus cifras*
  completamente interactivo: tocar «distancia» redibuja el gráfico en km (48,2 / 7,8).

## Hallazgo lateral (fuera de alcance, ticket propio)

La puerta del panel se **esconde** en free: `VehicleStatsBlock` sólo compone `OpenPanelRow`
`if (isPlus && onOpenPanel != null)`. La pantalla sabe perfectamente ser free —lleva su `isPlus`,
sus cifras de 7 días y su fila Plus dentro— así que esconder la entrada es un gate **duro** sobre un
botón, y la doctrina de MONEY-001 dice *gating soft siempre: la acción dispara el paywall, el botón
nunca se esconde*. Anotado para su propio ticket.
