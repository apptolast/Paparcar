# STATS-A-PLACE-CAN-GET-WORSE-001 · Un sitio puede empeorar, y la app debería notarlo

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Abierto:** 2026-09-16 · desde master `460b4241` · último fleco de
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

El motor de insights sabía decir qué sitio te cuesta más **hoy**, y la tendencia sabía decir que el
periodo entero va peor. Ninguna de las dos podía decir lo único que un conductor hace algo al oír:
*«en el sitio de siempre has empezado a tardar el triple»*. Obras, un vecino nuevo, un parking que
cerró — cambios que ocurren en UN sitio y que la media del periodo diluye.

Es además la vigilancia que el pitch de Plus promete y la ola aún no tenía.

## Doctrina violada

Ninguna. Las que respeta:

- **Emparejar por GEOMETRÍA, nunca por nombre**: el mismo trozo de acera se llama «Calle de Alcalá»
  un mes y «Calle de O'Donnell» al siguiente — el motivo entero de que un sitio dejara de ser una
  cadena de texto [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]. Dos racimos son el mismo
  sitio cuando sus centroides caen dentro del radio que los construyó.
- **Ninguna frase sin su n** y nada bajo umbral: hacen falta **dos búsquedas medidas a cada lado**
  (una media de una muestra no es una media) y un cambio de al menos la mitad.
- **Un predicado más, no un caso de uso más**: la regla entra en el veredicto que ya existe.

## Diseño

`placeTrend(places, previousPlaces)` — puro, en `domain/stats/insights/`. Para cada sitio con nombre
y con media, busca su gemelo del periodo anterior por distancia, compara medias y se queda con el
mayor efecto. La dirección viaja en los valores: subir y bajar son **dos frases**, no un signo.

## Verificación (16-09-2026)

- `testAll` verde · `DeriveStatsInsightsUseCaseTest` tests="16" failures="0" (+5: empareja por
  suelo aunque el nombre cambie, dice también cuando MEJORA, no compara sitios a 3 km, calla con una
  sola búsqueda a un lado, y calla ante el ruido normal).
- Guardarraíles a cero · 2 keys nuevas ×9 · mock/prod/iOS compilan.
- **En device (Redmi, mock)**: «En Av. de la Castellana 110 tardas 9 min más que el periodo
  anterior · sobre 3 aparcamientos medidos», con su variante de galería que sí alcanza su caso
  (el mismo sitio en dos meses: barato el pasado, caro este).
