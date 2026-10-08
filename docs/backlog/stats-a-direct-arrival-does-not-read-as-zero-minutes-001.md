# STATS-A-DIRECT-ARRIVAL-DOES-NOT-READ-AS-ZERO-MINUTES-001 · «aparcas en 0 min» era el número correcto con las palabras equivocadas

**Estado:** ✅ Done · la frase del hueco más fácil ya no imprime una cifra de cero, y el corpus de la demo llega a hoy con la rejilla llena · ⏳ sin ver en mano

## Problema

En la cuenta de demo (y en la del user) el panel de estadísticas decía
**«viernes de 12-16: aparcas en 0 min»**. Se lee como un dato que falta.

**El número era correcto.** `ParkingSearchTime` tiene un veredicto de **tres valores** y su KDoc lo
dice literalmente — *«zero is a measurement»*:

- `null` — no medible (no se presenció la llegada, sin reloj, fuera de rango).
- `0` — **llegada DIRECTA medida**: la línea conducida dentro del radio es esencialmente la recta de
  entrada. El suelo de «~30–60 s que paga una llegada directa» está **retirado a propósito** porque
  *«booked the final approach as searching on every single trip»*
  [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001].
- `> 0` — búsqueda medida.

⛔ **Mi primer diagnóstico fue que el 0 era un dato ausente y había que guardarlo como `null`.** Era
falso, y hacerlo habría resucitado el suelo que ese ticket retiró. Lo que estaba roto son **las
palabras**.

## Doctrina violada

`UI-SPOT-CLOCKS-NEVER-READ-ZERO-001`, la misma familia: un reloj a cero no se escribe con un cero.
Y la regla de copy del proyecto — causa + consecuencia, sin mecánica interna: «0 min» obliga al
lector a deducir qué significa un cero.

## Señales / datos disponibles

- `durationText(ms) = spanText(ms / MS_PER_MINUTE)`, **división entera**: toda media por debajo de
  **60 000 ms** se escribe «0 min». Ése es el umbral, medido, no elegido.
- Medido el 08-10 sobre las 282 sesiones de la cuenta de demo: 33 búsquedas medidas > 0, **32
  medidas en 0** y 148 a null. Dos celdas de la rejilla día × banda daban media 0,0.
- `ParkingHeatmap` ya excluye los null y exige ≥2 muestras: el `null` nunca pinta cero. El 0 sí
  promedia, **y debe** — es una medición.

## Diseño

Un solo sitio, el que el propio fichero declara como el único donde vive el copy:
`StatsInsight.sentence()` en `StatsInsightsCard.kt` — *«All the copy lives here and none of it in
domain: the engine decides WHAT is true, the surface decides how it is said»*.

Cuando el valor del insight **más fácil** cae por debajo de un minuto —el umbral del propio
formateador— la frase cambia de plantilla en vez de imprimir la cifra:
`stats_insight_easiest_time_direct`, en los **9 locales**. EN: *«you arrive and park without
hunting»*; ES: *«llegas y aparcas sin dar vueltas»*.

⛔ El dominio **no se toca**: el motor sigue diciendo 0, que es verdad. Y el insight *peor* no
necesita rama: por construcción está por encima de la media × `WORST_RATIO`, así que nunca es cero.

`MS_PER_MINUTE` pasa de `private` a `internal` en `TimeStringComposables.kt` con su razón escrita:
el umbral **es** la resolución del formateador, y re-escribir `60_000` en la tarjeta sería el mismo
hecho en dos sitios.

## Criterio de éxito

La frase del hueco más fácil nunca contiene una cifra de cero minutos; con búsqueda medida ≥ 1 min
sigue diciendo el número. `LocaleParityGuardrailTest` cubre que la key exista en los nueve.

## Consumidores auditados

- `stats_insight_worst_time` — ✔ exento: su umbral impide el cero.
- `stats_insight_costliest_place`, los dos de tendencia — ✔ exentos: no son tiempos de búsqueda por
  celda, y la tendencia ya es «más/menos» en frases distintas.
- `durationText` y sus otros llamadores — ✔ sin cambio; solo se expone su constante.

---

# Y la segunda mitad: el corpus de la demo no llegaba a hoy

El mismo informe destapó dos cosas de la cuenta de review, **independientes de la frase**:

1. **Los periodos recientes estaban vacíos.** El seeder clona desplazando desde las fechas del
   corpus de ORIGEN (`w0` desplaza 0 días), y se sembró el 30-09: «7 días», «esta semana» y «este
   mes» no tenían nada el 08-10. Se arregla **re-ejecutándolo**, porque el corpus de origen ya llega
   a hoy — 129 sesiones del 03-09 al 08-10. Hecho: 282 sesiones en 146 días.
2. **23 de las 42 celdas de la rejilla estaban vacías**, porque 148 de las 282 sesiones no traían
   tiempo de búsqueda. `demoSearchTimeMs()` lo **inventa solo donde el corpus real no lo tiene**, de
   forma reproducible y **con forma**: coste alto de 16 a 20 h, bajo de madrugada, y una parte de
   llegadas directas que se conserva porque es un valor legítimo. Vacías: 23 → **10**.

⛔ Las 10 que quedan **no se rellenan**: son bandas donde no hay ninguna sesión, y llenarlas
exigiría inventar *aparcamientos* de madrugada, no duraciones de aparcamientos que ocurrieron. Un
hueco honesto se dibuja como pista vacía, que es lo que `ParkingHeatmap` hace por diseño.

⚠️ El seeder toma la hora en la zona **local del proceso**, la misma en la que la app agrupa la
rejilla. Sembrar desde otra zona desplazaría las bandas.
