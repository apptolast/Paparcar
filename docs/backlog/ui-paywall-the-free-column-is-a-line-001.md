# UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001 · La columna Gratis no era una columna, era una línea

**Estado:** ✅ Done · mergeado en master · verificado en device (Oppo CPH2371, `prodRelease`)
**Abierto:** 2026-09-16 · desde `855cde2c` · rebasado sobre `d61d8d74` y luego sobre `937985c7`

> Va DETRÁS de [PLUS-A-PLAN-MUST-NAME-WHAT-IT-JUST-GAINED-001], y el orden importaba: ver
> «Resuelto: chocaba con…» más abajo.

## Problema

El paywall parte el ancho en dos para escribir **prosa** en celdas de ~150 dp, nueve veces
seguidas. El propio fichero lo admite en su KDoc: *«Width is still the constraint … the values are
terse fragments for that reason — the layout constrains the copy, so the copy is written to fit
rather than left to overflow»*. Es decir: la maqueta manda sobre el mensaje, y el mensaje es lo
único que esa pantalla tiene que hacer.

Medido sobre `PaywallScreen.kt` en master (`855cde2c`):

| Dato | Valor |
|---|---|
| Filas de la comparativa | 9, a dos columnas |
| Ancho de cada celda en un móvil de 360 dp | ~150 dp, texto en `caption` (12 sp) |
| Filas que **no** son un delta | 2 — `nearby` y `support` |
| Filas cuya celda Gratis solo dice que no existe | 2 — `alerts` y `shared_car` («No incluido») |
| Veces que se lee el motivo de entrada con la tienda viva | **0** |

- `nearby` dice Gratis «Parkings y precio del combustible» / Plus «Lo mismo: mirar siempre es
  gratis». En una tabla titulada *Qué cambia con Plus*, gasta media pantalla de ancho para decir
  que no cambia nada. La frase es VERDAD y es doctrina (VER nunca se gatea) — pero no es una fila
  de una tabla de deltas.
- `support` dice Gratis «Gratis para todos» / Plus «Pagas los servidores». Tampoco es un delta de
  producto: es una nota honesta sobre a dónde va el dinero.
- **El motivo de entrada se calcula y se tira.** `reasonLineRes(reason)` solo se pinta en la rama
  `else` de `PaywallFooter` — la de *sin tienda*. Con la tienda funcionando, quien choca con el
  límite de coches, con el de zonas o con la ventana de 7 días entra al paywall sin que nadie le
  mencione por qué está ahí. Las ocho strings existen, traducidas, en los nueve locales, y no las
  lee nadie salvo si la tienda está caída.

Consecuencia de altura (estimación sobre las constantes del fichero, no medida en device):
la columna scrollable pide ~1.150 dp sobre una ventana de ~700 dp, así que **la comparación y el
precio no caben juntos en pantalla**. El argumento se decide con uno de los dos en la memoria.

## Doctrina violada

1. **«No copy al usuario con mecánica interna ni jerga»** no se rompe, pero sí su hermana: la
   maqueta está obligando al copy a ser un fragmento telegráfico. Nueve pares de fragmentos es una
   hoja de especificaciones, y esta es una pantalla de venta.
2. **`Plus = la app te avisa a ti, y recuerda por ti`** (`plus-alerts-and-tiers-001`). El eje del
   producto no aparece por ningún sitio: la tagline enumera dos features, y la tabla enumera nueve
   pares. Nadie dice cuál es la frontera.
3. **Paywall contextual.** `PaywallReason` existe justamente para que el gate que acabas de tocar
   se nombre. Calcularlo y no mostrarlo es tener el invariante y no aplicarlo.

## Señales / datos disponibles

- `PaywallReason` ya llega a `PaywallContent` como parámetro y ya tiene sus 8 strings × 9 locales.
- `PapPremiumSurface` acepta `shape`, así que el metal puede ser un azulejo de 40 dp igual que era
  una chapa de media pantalla — mismo pincel, misma tinta (`premiumInk`), un solo recetario.
- Ninguna de las keys que se retiran (`paywall_free_*`, `paywall_plus_*`, `paywall_tier_*`) se lee
  desde ningún otro sitio: `grep` sobre todo el repo las devuelve solo en `PaywallScreen.kt`.

## Diseño

**El anclaje no lo hace la columna, lo hace tener la referencia al lado.** Una columna es una
forma de anclar; la misma línea es otra, y cuesta la mitad de ancho. Así que la columna Gratis
desaparece **como columna** y sobrevive **como línea**:

```
[▨]  Tu historial completo
     Gratis: los últimos 7 días
```

El delta se sigue leyendo — el título dice lo que ganas, la nota dice contra qué se mide — y el
texto recupera el ancho entero de la pantalla, que es lo que estaba faltando.

De ahí salen las cinco decisiones:

1. **`TierComparison` muere.** En su sitio, `PlusBenefits`: seis filas a ancho completo, título en
   `rowTitle` y nota en `caption`. El par (título, nota) vive en UN objeto `PlusBenefit`, por la
   misma razón por la que `TierRow` juntaba free y plus: dos listas paralelas se desparejan en
   silencio.
2. **El metal deja de ser una chapa y pasa a ser el azulejo de cada fila.** Es un cambio de
   criterio explícito respecto a `UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001`, que argumentó
   que *«el acabado solo vende cuando Plus es un objeto entero que puedes señalar»*. Sigue habiendo
   objetos señalables — la cresta, seis azulejos y el CTA — y ahora el material se enuncia siete
   veces en vez de una, sin gastar media pantalla de ancho. Si en device el metal se queda corto,
   la salida es agrandar el CTA, no devolver la columna.
3. **`nearby` sale de la tabla y se convierte en una frase.** Un check y una línea bajo la lista:
   *«Mirar es gratis y lo seguirá siendo: plazas libres, parkings y precio del combustible,
   completos, sin suscripción.»* Dice lo mismo en una línea y sin una celda que conteste «lo
   mismo».
4. **El motivo de entrada sube arriba y se lee SIEMPRE.** Tira propia bajo la cabecera de marca,
   no solo en la rama sin tienda. `PaywallReason.GENERAL` (la entrada desde Ajustes) sigue sin
   añadir línea, porque ahí no hay motivo que nombrar. La rama sin tienda **deja de pintarla**: si
   se pintara en los dos sitios saldría dos veces.
5. **Nueve filas bajan a cinco.** `widget` se funde con `vehicles` («todos tus coches, también en
   el widget»), `stats` se funde con `history` porque comparten la única ventana que hay (ver
   «Salido de mirarlo en el Oppo»), `nearby` se va a la frase del punto 3 y `support` a la nota de
   facturación, que es donde se habla de dinero. Quedan las cinco cosas por las que se paga.

### Strings

Es un cambio de copy tanto como de maqueta, así que las keys se renombran por su ROL en vez de
reaprovechar las viejas con otro significado:

- **Nuevas (14 × 9 locales):** `paywall_benefit_<x>` + `paywall_benefit_<x>_note` para history,
  stats, alerts, zones, vehicles y shared_car (12), más `paywall_benefits_title` y
  `paywall_free_is_free`.
- **Reescritas (2 × 9):** `paywall_tagline` (pasa a decir el EJE — *la app vigila y recuerda por
  ti* — en vez de enumerar dos features que la lista de debajo ya enumera) y `paywall_billing_note`
  (absorbe la frase de los servidores que traía `paywall_plus_support`, que es donde esta pantalla
  habla de dinero).
- **Retiradas (21 × 9 locales):** `paywall_tier_free`, `paywall_tier_plus`, `paywall_compare_title`,
  los nueve `paywall_free_*` y los nueve `paywall_plus_*`.

⛔ `paywall_compare_title` se retira en vez de reescribirse: una key llamada *compare* para un
título que ya no compara nada es exactamente la deriva que este proyecto persigue. La sustituye
`paywall_benefits_title`, que se llama como lo que encabeza.

⛔ Retirar las 21 no es opcional: `LocaleParityGuardrailTest · every declared string is read by
something` falla con una key declarada y no leída.

## Criterio de éxito

- [x] `./gradlew testAll` en verde, y los **17** guardarraíles ejecutados de verdad (45 tests,
      0 fallos) forzando `--rerun`: Gradle da estos tests por UP-TO-DATE con facilidad porque leen
      ficheros que no declara como entradas, y este proyecto ya se ha quemado con eso.
      Entre ellos, `LocaleParityGuardrailTest` (las 21 keys retiradas ya no están declaradas en
      ningún locale, y las 14 nuevas están en los nueve) y `TypographyGuardrailTest` /
      `ColorGuardrailTest` sobre la pantalla reescrita.
- [x] `:app:compileMockDebugKotlin` y `:app:compileProdDebugKotlin` compilan.
- [x] `GENERAL` (fila de Ajustes) no muestra tira de motivo — `reasonLine` devuelve `null` y
      `ReasonStrip` sale sin emitir nada, ni siquiera su `Spacer`.
- [x] El motivo se dice **exactamente una vez**: la rama sin tienda del pie dejó de pintarlo.
- [x] Galería mock con la variante de motivo CON tienda (la que no existía porque la pantalla no
      la tenía), más una segunda con otro gate para ver que el glifo y la frase cambian con él.
- [x] **En device** (Oppo CPH2371, 1080 × 2400 @ 480 dpi = **360 × 800 dp**, `prodRelease`
      sideload): el metal repartido en seis azulejos **se sostiene**. No lee a papel pintado — los
      azulejos son de 36 dp en una columna estrecha, el resto de la fila es texto normal — y con
      la cresta arriba y el CTA abajo el material enmarca la pantalla en vez de ocupar media.
      No hace falta agrandar el CTA.
- [ ] ❌ **FALSO: «la lista y el primer plan caben sin scroll».** Medido en ese mismo Oppo, en la
      entrada GENERAL (la que **no** lleva tira de motivo, o sea el caso más corto que existe):
      el scroll máximo es de **557 px = 186 dp**. Los planes quedan debajo del pliegue; lo que se
      ve al abrir es la lista completa más la línea de «mirar es gratis», y el primer plan asoma
      cortado.

      Y no se arregla apretando: el inventario de recortes disponibles —`BENEFIT_V_PAD_DP` 7→4
      (36 dp), la fila de coches que envuelve a dos líneas (20 dp), la línea de «mirar es gratis»
      de tres líneas a dos (16 dp), los gaps de sección (12 dp)— suma ~84 dp sobre 186, y con tira
      de motivo el hueco crece otros ~50. **Seis beneficios, dos planes y la nota de facturación
      no caben en 800 dp.** El criterio estaba mal puesto, no el diseño.

      Lo que sí se midió es la mejora: en master la comparación de nueve filas a dos columnas
      dejaba los planes a ~450 dp del pliegue. Ahora están a 186, es decir **un golpe de dedo en
      lugar de medio dedo de barrido**, y el precio sigue permanentemente a la vista en la etiqueta
      del CTA, que es la decisión que esta pantalla ya había tomado y que no se toca.

      → Sustituir este criterio por: *el precio está siempre visible en el CTA, y la lista entera
      se lee sin scroll*. Las dos se cumplen.

      **Re-medido tras fusionar filas y absorber `say-the-wave` (build `81072041…`): 555 px =
      185 dp.** Es decir, prácticamente el mismo número que antes — y eso es el dato interesante:
      la fusión quitó una fila entera, pero el copy nuevo hace que **tres de las cinco filas
      envuelvan a dos líneas**, y eso se lo come exacto. La pantalla ocupa lo mismo y dice bastante
      más (la ola de estadísticas, el resumen del mes, el widget por su nombre). Cambiar de sitio
      el bulto no es reducirlo: **la altura la manda el número de LÍNEAS, no el de filas.**

## Salido de mirarlo en el Oppo

**Aplicado — la lista baja de 6 filas a 5:**

- **Historial y cifras eran una sola cosa y se estaban contando dos veces.** Las dos filas llevaban
  la MISMA nota a una línea de distancia («Gratis: los últimos 7 días»), y eso no era un descuido
  que ordenar: es que **hay UNA ventana** (`FREE_HISTORY_WINDOW_DAYS`) y las cifras se calculan
  sobre lo que esa ventana deja pasar [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]. Dos filas
  enunciando un solo límite leen a fallo de copy y gastaban una fila en ello.
  → Una fila: **«Tu historial completo: gasto, tiempo, previsiones y mejores sitios y horas» /
  «Gratis: los últimos 7 días»**.

  ⚠️ El ORDEN de esa lista no es indiferente y costó tres vueltas. «…mejores sitios y horas, y
  previsiones» mete dos conjunciones y la segunda detrás de coma, así que se tropieza al leer.
  Poniendo «previsiones» en medio, la pareja *sitios y horas* queda al final como **un solo item**:
  la primera «y» separa la lista, la segunda es interna a ese item. De paso desaparece el
  «mejores» repetido. En PL y RO las dos conjunciones serían la MISMA palabra y la pareja dejaría
  de leerse como un item, así que ahí la de lista es la suya propia (`oraz`, `precum și`).

  ⛔ **El título ENUMERA, porque las dos versiones cortas eran folleto.** La celda que sustituye
  decía «Cualquier periodo», que describe un selector de fechas y malvende once tickets. La
  primera reescritura decía «y lo que aprende de él» — y **alguien que lleva dos días en la app no
  sabe qué significa eso**. *Aprende* es de esa clase de palabras que suenan a valor y no nombran
  ninguno: sobreviven a la revisión porque nadie puede demostrar que son falsas. Así que la fila
  gasta su ancho nombrando las salidas reales de la pantalla de cifras. Sigue sin jerga: ni
  «insights», ni «heatmap», ni «racimos».

  ⚠️ **«sitios» aquí son los LUGARES donde aparcas, no plazas libres.** Las traducciones tienen que
  esquivar la palabra que cada idioma reserva para *spot* (`posto`, `plek`, `miejsce`, `loc`) y
  usar la de *lugar*. [COPY-SPOT-IS-NOT-A-PARKING-001]
- **El widget se llama widget.** La fila de coches decía «también en la pantalla de inicio» y
  envolvía a dos líneas. `settings_add_widget` ya dice «Añade el widget a tu pantalla de inicio»,
  así que la palabra YA es del vocabulario del producto: lo que se promete aquí y lo que vas a
  añadir allí tienen que llamarse igual. → **«Todos tus coches, también en el widget»**, una línea.

**Sin decidir, fuera de este ticket:**

- **El plan mensual se pinta antes que el anual**, que es el preseleccionado y el que lleva el
  badge de −41 %. El orden sale de `state.offers`, o sea del repositorio, no de esta pantalla.
  Todo lo que se leyó sobre anclaje de precio dice lo contrario: el anual primero.
- El badge sale **«−41 %»**, no «−41 %%» — [COPY-PERCENT-IS-NOT-ESCAPED-001] aguantando.

## ✅ Resuelto: chocaba con PLUS-A-PLAN-MUST-NAME-WHAT-IT-JUST-GAINED-001

`feature/…-say-the-wave` (worktree `../Paparcar-plus-copy`, **🟢 listo para merge y verificado en
device**, sin commitear) y esta rama salen del mismo `855cde2c` y **editan las mismas keys**:

| Key | say-the-wave | esta rama |
|---|---|---|
| `paywall_tagline` | «Paparcar watches for you, and remembers everything» | «The app watches and remembers for you» — **misma decisión, tomada dos veces por separado** |
| `paywall_plus_stats` | «Your whole history, and what it learns from it» | **borrada** — su contenido vive ahora en `paywall_benefit_history` |
| `paywall_plus_alerts` | «Spots, prices and every month's recap» | **borrada** — su contenido debe pasar a `paywall_benefit_alerts` |
| `paywall_reason_stats` | reescrita | **no tocada** → sobrevive intacta |

⚠️ Y **master se movió**: `855cde2c` → `c0b9c11a` (3 commits). Las dos ramas están desfasadas.

⛔ Si se hubiera mergeado esta rama primero, las tres decisiones de `say-the-wave` desaparecerían
sin dejar rastro: sus keys ya no existen aquí, así que el conflicto **no salta** — se pierde en
silencio. Por eso el orden fue el contrario.

**Cómo se resolvió** (ramas apiladas: CÓDIGO con la rama, DOCS con master):

1. `say-the-wave` mergeado primero a master (`d61d8d74`): estaba listo, verificado en device, y
   lleva además el cresta de `StatsMonthRecapCard`, que no tiene nada que ver con esta maqueta.
2. Esta rama rebasada encima. Los nueve `strings.xml` chocaron; se resolvieron **a favor de la
   rama** (la estructura de keys es de aquí) y encima se trasladaron a mano las dos decisiones que
   se habrían perdido:
   - `paywall_tagline` ← el valor de master **tal cual**, en los 9 locales. Las dos ramas habían
     llegado por separado a la misma frase; la que se queda es la que ya se vio en device.
   - `paywall_plus_alerts` («Plazas, precios y el resumen de cada mes») ← pasa a ser el TÍTULO de
     `paywall_benefit_alerts`… con una enmienda: **«precios» no decía precios DE QUÉ**. Como celda
     de tabla, bajo una columna «Plus» y al lado de una fila de zonas, el contexto lo sostenía;
     como título de fila a ancho completo, no — ¿el parking?, ¿la suscripción? Ahora dice
     **«Plazas, precio de gasolineras y el resumen del mes»**.
   - Y de paso, la NOTA de esa fila cubría dos alcances de tres: decía sólo «en las zonas y
     gasolineras que elijas» y dejaba el resumen del mes sin alcance. Ahora dice «…, y al cerrar
     el mes». Verificado que el resumen es Plus: `RunMonthRecapUseCase` gatea por entitlement, una
     sola puerta, la del historial — no hay un segundo `if (isPlus)`.
   - `paywall_reason_stats` no la toca esta rama, así que su reescritura sobrevive intacta.
   - La decisión de stats ya estaba puesta: `paywall_benefit_history` dice «y lo que aprende de él».

⚠️ **`git checkout --theirs` sobre un conflicto se lleva el fichero ENTERO de la rama**, así que
también habría borrado en silencio cualquier string que master hubiera añadido en los otros tres
commits. Comprobado por script clave a clave en los 9 locales: **0 pérdidas** — lo único que falta
respecto a master son las 21 keys que este ticket retira a propósito.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `PaywallScreen.kt` | reescrito — único consumidor de las 20 keys retiradas |
| `grep paywall_(free_\|plus_\|tier_)` en todo el repo | sin resultados fuera de `PaywallScreen.kt` ✅ |
| `StateGalleryScreen.kt` · grupo «Paparcar Plus» | actualizado (variante nueva de motivo con tienda) |
| `PaywallViewModel` / `PaywallState` / `PaywallIntent` | **sin tocar** — el cambio es de pintura, no de estado |
| `PaywallViewModelTest` | sin tocar, por lo mismo |
| `PaywallReason` y sus 8 `paywall_reason_*` | sin tocar; solo cambia DÓNDE se pintan |
| Otros puntos de contacto de Plus (`PapPremiumSurface` en Ajustes, pie de Historial) | sin tocar — usan el mismo recetario y no dependen de la tabla |
