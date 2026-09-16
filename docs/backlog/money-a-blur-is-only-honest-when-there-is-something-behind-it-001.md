# MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001 · La inteligencia del panel se ve bloqueada, y solo cuando existe

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Abierto el 2026-09-16 desde master `2cfc408b`, rebasado antes de mergear.
⏳ **Sin ver en mano**: el mock quedó instalado en el Oppo (`5026e7b2…`), pero la galería no llegó a
abrirse en el panel de cifras. El borrón sigue sin mirarse en device.
**Origen:** pregunta del user — *«¿dónde le mostramos al usuario que tenemos más productos, como el
fin de mes y todo lo que metimos con estadísticas avanzadas? Pensaba que ver todas tus cifras era
Plus y he podido entrar por "ver desglose completo"»*.

## Problema

Dos mitades del mismo silencio, las dos medidas en master `2cfc408b`.

### 1 · El panel regala la inteligencia y, a la vez, no la enseña

`e196358b` abrió el panel a free —y bien: esconder la puerta era un gate duro sobre un botón—. Pero
lo que free encuentra dentro es **la inteligencia calculada sobre siete días**, que es justo la
ventana en la que casi nada de eso existe:

| Card | Qué le pasa hoy a un free |
|---|---|
| `StatsMonthRecapCard` | **nunca se compone**: `closedMonthIsFullyVisible` es false con 7 días |
| `StatsPaceCard` | `paceRows` sale vacío: `Last7Days` no tiene cierre que proyectar |
| `StatsInsightsCard` | conclusiones sin cobertura suficiente casi siempre |
| `StatsPlacesCard` · `StatsWhenCard` | salen, recortados a siete días |

O sea: lo que el plan vende —*que la app concluya por ti*— para un free **no existe en ninguna
superficie del panel**, ni como dato ni como oferta. Y lo poco que sí existe se regala.

### 2 · «Ver desglose completo» promete de más

`VehicleStatsBlock.kt:455` → `stats_open_panel`. La fila era solo-Plus; al abrirla a free (`e196358b`)
la palabra **«completo»** se quedó, y lo que un free va a ver son sus siete días.

## Decisión del user (16-09)

Se le ofrecieron tres caminos: nombrar el hueco con una card pulsable, **difuminar las cards de
inteligencia**, o cerrar el panel entero con Plus. **Eligió el borrón**, con dos condiciones suyas:

1. ⛔ **«Hasta que no existan esas estadísticas no deberíamos mostrarlas con el blur.»** Nada de
   borrones sobre el vacío.
2. ⛔ **«Las stats principales sí son las que se muestran, las que el usuario puede ver con Últimos
   7 días.»** Las cifras y la gráfica siguen siendo free. Y *«lo de Cuando aparcas también»* va con
   blur: la inteligencia bloqueada son las **cinco** cards.

### Tensión asumida, escrita aquí para que no se lea como un descuido

Este ticket **revierte en parte dos decisiones de esta misma semana**, y lo hace a sabiendas:

- [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] (13-09) borró el velo (`PapStatTile.veiled`,
  `RedactedValue`) con el argumento *«tapar cifras que la app ya calculó no era memoria, era
  esconder»*. **Sigue vigente donde nació**: las CIFRAS no se tapan, y esta tarea no las toca. Lo
  que se tapa es la capa de conclusiones que F0–F6 añadió DESPUÉS de aquella decisión.
- [MONEY-001] *gating soft*: el botón nunca se esconde. **No se viola**: la card bloqueada es
  visible y **pulsable**, y lleva al paywall. Un borrón pulsable es un gate blando; una card que no
  se compone —lo de hoy— era el gate duro.

## Doctrina

- **Free = tú preguntas y la app responde · Plus = la app vigila y recuerda por ti.** Las cifras de
  los últimos 7 días son la respuesta a lo que preguntas; las conclusiones sobre meses son la app
  hablando sola. El corte del borrón cae exactamente en ese eje.
- ⛔ **Nunca se gatea VER lo que ya es tuyo**: plazas, parkings, gasolineras y tus propias cifras de
  7 días siguen completas y sin tocar.
- ⛔ **Un borrón es una FORMA, nunca un dato.** Ni cifras reales traídas por encima de la puerta de
  entitlement, ni cifras inventadas bajo el cristal. Lo que se difumina es la silueta de la card.
- ⛔ **Un borrón sin nada detrás es publicidad, no un gate.** Si con Plus esa card tampoco tendría
  nada que decir, no se compone — igual que hoy.

## Diseño

### A · El invariante: existencia por encima de la puerta, contenido nunca

`domain/stats/LockedIntelligence.kt` — función pura que responde **cinco booleanos**: para este
coche, ¿tendría cada card algo que decir si el plan no recortara la historia?

```kotlin
data class LockedIntelligence(
    val recap: Boolean, val insights: Boolean,
    val places: Boolean, val heatmap: Boolean, val pace: Boolean,
)
```

⛔ **Devuelve booleanos, nunca valores.** La puerta de entitlement (`gateHistoryToEntitlement`)
sigue siendo la única que decide qué se MUESTRA; para saber si hay algo que vender hay que mirar por
encima de ella, y lo que cruza de vuelta es un `Boolean`, que no es una cifra de nadie.

Se calcula en `StatsViewModel` solo cuando `!isPlus`, desde una segunda resolución
`HistoryTimeline.resolve(isPlus = true)` cuyos valores se descartan en el sitio.

### B · La card bloqueada

`ui/components/PapLockedCard.kt` — **un solo esqueleto** para las cinco (sistemas, no parches):
título real de la card + silueta difuminada + candado + «Con Plus», toda la superficie pulsable.

- Silueta: bloques de `onSurfaceVariant` a baja opacidad con `Modifier.blur`. ⚠️ En Android
  `blur` es no-op por debajo de API 31 y el `minSdk` del proyecto es **26**: por eso la silueta ya
  es ilegible sin el desenfoque (son bloques, no texto), y el blur solo la suaviza donde existe.
- Dos siluetas, no cinco: `Lines` (resumen, conclusiones, sitios, ritmo) y `Grid` (cuándo aparcas).
- Accesibilidad: la card expone su título + el estado bloqueado; los bloques son decorativos.

### C · Motivos del paywall — dos deseos, no uno

- Resumen del mes → `PaywallReason.MONTH_RECAP`. *Querer que te cuenten el mes* y *querer elegir un
  periodo* son deseos distintos: la misma asimetría que ya defiende `SettingsViewModel` en su fila.
- Las otras cuatro → `PaywallReason.STATS`.

`StatsIntent.OpenPaywall` pasa de `data object` a `data class OpenPaywall(val reason: PaywallReason)`.

### D · La fila que promete de más

`stats_open_panel`: **«Ver el desglose»** para todos (×9 locales, sin key nueva). El panel no es «lo
completo» —para nadie—: es el desglose. El periodo ya lo nombra el título de la card justo encima y
la fila Plus de debajo. Decisión del user: una sola cadena, no dos copys que mantener.

### E · Qué significa «existe», medido al escribir el test

La primera versión del test afirmaba lo que sonaba bien —*si toda tu historia cabe en los 7 días,
nada está escondido, luego nada se difumina*— y **falló**. Tenía razón el test en fallar: bajo la
decisión tomada, la capa de conclusiones es de Plus **siempre**, no solo más allá de la ventana. Lo
que el borrón pregunta no es *¿te estoy ocultando historia?* sino **¿tiene esta card algo que
decir?**. Un coche con cuatro paradas en el mismo sitio esta semana TIENE sitio habitual, y por eso
lo ve bajo cristal.

El umbral de cada card es el suyo de siempre, no uno inventado para vender: un sitio son
[MIN_PARKINGS_FOR_PLACE] paradas repetidas, una previsión necesita una magnitud medida, la rejilla
necesita un aparcamiento. Un coche que aún no ha concluido nada ve el panel **exactamente como
antes de este ticket**, sin un solo borrón.

### F · Lo que free PIERDE, dicho sin adornos

`StatsPlacesCard` y `StatsWhenCard` hoy se componen para free sobre sus 7 días. Con este cambio
pasan a verse bajo cristal. Es consecuencia directa de la decisión —*la inteligencia es lo que se
paga*— y se anota aquí para que no se lea como un efecto colateral que nadie vio.

### G · iOS

Nada que portar: todo vive en `commonMain` (`domain/stats`, `ui/components`, `presentation/stats`) y
`Modifier.blur` es API de Compose común. La única salvedad de plataforma es al revés —Android por
debajo de API 31— y está escrita en el KDoc del componente.

## Criterio de éxito

- Free en el panel: cifras y gráfica de 7 días intactas; las cinco cards de inteligencia salen
  **bloqueadas y borrosas solo si tendrían contenido**, y cada una abre el paywall con su motivo.
- Coche sin nada que concluir: el panel se ve **exactamente como hoy** — ni un borrón.
- Plus: sin un solo cambio.
- La fila de la ficha no dice «completo» en ningún plan.
- Galería: «Free · cifras de 7 días, inteligencia bajo cristal», «Free · sin nada que bloquear» y
  «Free · el mes cerrado, bajo cristal».

## Verificación

| Qué | Resultado |
|---|---|
| `./gradlew testAll` | ✅ 3.452 tests, 0 fallos |
| Guards de Konsist con `--rerun-tasks` | ✅ (tipografía, color, divisores, imports) |
| `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` | ✅ |
| `LockedIntelligenceTest` (5) + 4 tests nuevos en `StatsViewModelTest` | ✅ |
| En device | ⏳ pendiente — el repo no tiene test de Compose UI, así que el borrón hay que VERLO |

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `StatsScreen` (recap, insights, places, when, pace) | cambiado |
| `StatsViewModel` / `StatsState` | cambiado (booleanos de bloqueo) |
| `VehicleStatsBlock` · `OpenPanelRow` | copy de la fila |
| `HistoryContent` (ficha) | sin cambio — allí no vive ninguna card de inteligencia |
| `PlusStatsRow` | sin cambio — sigue vendiendo el PERIODO bajo las cifras, que es otra pregunta |
| `StatsIntent.OpenPaywall` | ahora lleva su motivo; único consumidor de test actualizado |
| `StatsPaceCard` · `StatsPlacesCard` · `StatsWhenCard` · `StatsInsightsCard` | sin tocar por dentro — solo quién las compone |
| `StateGalleryScreen` | variantes nuevas |
