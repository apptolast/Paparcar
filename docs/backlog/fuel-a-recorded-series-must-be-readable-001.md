# FUEL-A-RECORDED-SERIES-MUST-BE-READABLE-001 · se graban 30 días de precios y no se ven en ninguna pantalla

**Estado:** 🟡 Abierto · **la decisión de producto YA ESTÁ TOMADA** (29-10), falta el código ·
sale de [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001](fuel-a-follow-must-show-itself-001.md).

## ✅ Dónde vive — contestado por el rediseño de la home (29-10)

Este doc pedía *«decisión de producto (¿dónde vive?) antes que código»*. La respuesta salió de
[`UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001`](ux-the-home-must-earn-its-first-glance-001.md), a
raíz de una idea del user (*«últimas actualizaciones cerca»*):

> **No vive en una pantalla nueva: vive en la tarjeta de gasolinera de «Aquí y ahora», que
> prefiere el CAMBIO al NIVEL.**
> Con movimiento: «**Bajó hoy cerca de ti** · −4 cts · Repsol Embajadores · 1,489 €/L».
> Sin movimiento: «**Gasolina 95 más barata** · 1,419 €/L».

Por qué **no** una sección propia: sólo tiene **un sujeto** (una plaza ya es un cambio por
naturaleza — el carril de plazas *es* el feed de novedades; de los parkings no se muestrea ocupación
en el tiempo), rompería el **orden fijo** del primer vistazo, y el mismo hueco lo dice mejor.

**Y las subidas también se cuentan**: contar sólo las bajadas convierte la app en un cupón — una
subida también cambia una decisión (repostar hoy o mañana).

⛔ La negativa que lo hace honesto es la que este repo ya tenía escrita en
`EvaluateDailyPriceMoveUseCase`: *«dos filas que difieren no es un precio que cambia»* — hace falta
que **las dos muestras lleven sello de la fuente y los sellos difieran**, más el umbral de
≥0,5 c/l. Y un movimiento **no se salta** el umbral de frescura de la tarjeta.

**Dónde está especificado:** `design-prototypes/paparcar-home/b-decisions.md` §14 (el razonamiento)
y `interaction-spec.md` §2.3 (la regla) + §9 **tarea 2.4** (la implementación, que es la que cierra
este ticket).

⚠️ **Sigue abierto** porque falta el código. Lo que ya no le falta es la decisión.

## Problema

Seguir una gasolinera arranca una **serie de precios de 30 días** en Room
(`fuel_price_history`, `FuelWatchRepository.RETENTION_DAYS = 30`). Esa serie **no se lee en ninguna
superficie de la app**: medido con
`grep -rn "observeHistoryFor\|historyFor(" shared/src/commonMain/.../presentation shared/.../ui`,
el único consumidor fuera de la capa de datos es `RunFuelPriceWatchUseCase`, que la usa para decidir
si manda una notificación. El repositorio incluso publica un `observeHistoryFor` **reactivo**, pensado
para una pantalla, y nadie lo observa.

O sea: la app mide, guarda y prune-a un dato durante un mes para gastarlo en una sola frase de una
notificación que puede no llegar nunca (el movimiento exige ≥0,5 c/l). Si el usuario quiere saber
«¿está caro hoy o es lo normal aquí?» —que es LA pregunta de la que vive este dato— no hay dónde
mirarlo.

## Doctrina violada

- **Plus vende MEMORIA** (`plus-alerts-and-tiers-001.md`): *«lo que pasó más allá de la ventana
  free»*. Una memoria que no se puede leer no es memoria, es un log. Y en el paywall la serie larga
  de precios figura como eje vendible — vendiendo algo que no tiene superficie.
- **Un dato que se guarda se enseña o no se guarda.** Treinta días de escrituras diarias por
  gasolinera seguida es coste de disco y de batería justificado por una pantalla que no existe.

## Qué pide decidir (y por eso no es un fix)

1. **Dónde vive.** Tres candidatos, y la regla editorial de CLAUDE.md no los separa sola —la serie es
   *mío-permanente* (→ Vehículos) pero su sujeto se toca en el mapa (→ Home):
   - en el **peek** de la gasolinera, bajo los precios (lo ve quien ya está mirando ese surtidor);
   - en **Vehículos**, junto a estadísticas y gasto de combustible (donde ya vive «lo mío»);
   - una **pantalla de seguidas** —la que hoy no existe: no hay ninguna lista de lo que sigues.
2. **Qué dibuja.** La serie es irregular por construcción: hay huecos (días sin muestra) y el feed
   repite lecturas viejas. Un gráfico de línea que une dos puntos a seis días de distancia **miente
   sobre los cuatro de en medio** — ver [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]. Puntos
   con su fecha, mínimo/máximo del periodo, o «hoy vs. la media de 30 días».
3. **Free vs Plus.** Si la ventana free son 7 días y la de Plus 30, esto es una fila más de la tabla
   de planes y necesita su gate soft, no esconder el control.

## Señales / datos disponibles

- `observeHistoryFor(stationKey, fuelType, days)` ya existe y es reactivo.
- `FuelPriceSample` ya trae las dos horas separadas (`reportedAtMs` del feed, `observedAtMs`
  nuestra), `observedDay` como clave de serie, e `isSelf` — así que el gráfico puede negarse a
  comparar dos surtidores distintos, que es la trampa de
  [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001].
- `dataviz`: la skill del harness fija la forma y la paleta de cualquier gráfico.

## Criterio de éxito

- La serie de una gasolinera seguida se puede leer en la app, con los huecos dichos y no
  interpolados, y la ventana que corresponda al plan.
- `dataviz` aplicada (sin colores a mano) y variante en la galería de estados, incluida la serie de
  **un solo punto** (el primer día) y la **vacía**, que son los dos estados que un gráfico suele
  romper.
