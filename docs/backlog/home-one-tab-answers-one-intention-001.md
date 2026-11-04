# HOME-ONE-TAB-ANSWERS-ONE-INTENTION-001 · Aparcar / Repostar, no tres fuentes de datos

**Estado:** 🔵 Abierto, sin rama · idea del user el **03-11-2026**, con una contrapropuesta medida
en la respuesta · sale de revisar las modales en
[`HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001`](home-the-first-glance-speaks-for-the-car-001.md)

> *«Estaba pensando en una manera de unificar plazas comunitarias y parkings en la misma sección de
> tab, tener algo como Aparcar/Repostar (o Estación de servicio) […] y dejar lo de plaza comunitaria
> como filtro y elemento siendo la misma lista con parking.»*

## El problema, que el user nombró bien

Los carriles de Home se agrupan hoy por **fuente de datos** —Plazas · Parkings · Gasolineras— cuando
el usuario llega con una **intención**: *dejar el coche* o *repostar*. Que la plaza la libere la
comunidad y el parking lo explote una empresa es una diferencia de **medio**, no de objetivo.

Coste medido en la propia pantalla: para contestar «¿dónde aparco?» hay que mirar **dos carriles** y
comparar a mano. Se ve en las capturas de la 4ª ronda — «PLAZA LIBRE MÁS CERCA · 76 m» y «PARKING MÁS
CERCA · 637 m» son dos tarjetas separadas que responden a la misma pregunta.

## La propuesta: agrupar SÍ, fundir la lista NO

**Dos pestañas por intención** — `Aparcar` / `Repostar` — y dentro de *Aparcar*, **dos bloques** en
el mismo scroll con su propia cabecera y sus propios filtros, ordenados por distancia. No una sola
lista ordenada. Cuatro razones, ninguna de gusto:

1. **No son comparables, y el ORDEN lo destapa.** Una plaza comunitaria **caduca** (TTL, rampa de
   frescura, `enRouteCount` de quien ya va); un parking está ahí seguro, cuesta dinero y tiene
   gálibo. Un «más cercanas» fundido pondría una plaza que puede desaparecer en tres minutos por
   encima de un parking que existe fijo. **Un solo orden exige un solo modelo de confianza, y aquí
   hay dos.**
2. **Los filtros no componen.** Plazas filtran por TAMAÑO (Moto/Pequeño/Mediano/Grande); parkings por
   Gratuito/De pago/Cubiertos/Sin gálibo bajo. Fundidos, o el chip no hace nada sobre media lista
   —que es mentir— o vuelve a hacer falta un selector de modo… que es el carril que se acaba de
   quitar.
3. **El vocabulario los separa a propósito** [COPY-SPOT-IS-NOT-A-PARKING-001]: *una plaza la libera
   la comunidad en la calle, un parking público lo explota alguien y se entra con el coche*. Fundir
   la lista no rompe las palabras, pero invita a la UI a difuminarlas.
4. **La frescura 🟢🟡🔴 existe porque una plaza es una promesa perecedera.** Un parking no tiene eso,
   y una rampa de frescura junto a filas que no caducan se lee como si a esas les faltara el dato.

El filtro `[Todo | Plazas | Parkings]` sigue existiendo para cuando sí sabes cuál quieres — que es
la mitad buena de la idea original.

## Lo que hay que decidir antes de tocar nada

1. **¿`Repostar` o `Estación de servicio`?** El user dejó las dos. `Repostar` es el VERBO, y casa
   con `Aparcar`: dos intenciones, dos verbos. «Estación de servicio» nombra el sitio, no lo que vas
   a hacer. ⛔ Y hay que mirar el cupo en los 9 idiomas: DE y PL alargan los verbos.
2. **¿Qué pasa con el tercer tile de la cápsula?** Hoy el contador del carril activo vive en el lead
   tile de la hoja. Con dos pestañas, `Aparcar` cuenta **dos** cosas (plazas + parkings) y un solo
   dígito no puede decir las dos sin mentir. Opciones: contar sólo lo del bloque de arriba, contar
   el total, o dejar el tile con el glifo de la intención y ninguna cifra.
3. **¿Afecta a la navegación inferior?** No debería: Home sigue siendo Home. Pero conviene
   comprobarlo contra la regla editorial — *si pasa AHORA → Home*.
4. **¿Y el mapa?** Los pines no se agrupan: una plaza y un parking seguirán siendo dos marcadores
   distintos. La pestaña filtra la LISTA y la capa, no el lenguaje del mapa.

## Alcance real (por qué no entra en el ticket de las modales)

Toca la cápsula de carriles, los filtros de los dos bloques, el estado de Home (`HomeNearbyLens`
pasa de tres valores a dos + un filtro interno), la hoja entera, el Dev Catalog y la galería de
estados. **Ticket propio, worktree propio.**

## Preview

Hecha en HTML antes de escribir Kotlin, para decidir mirando: se enseña al user y vive como artifact.
⚠️ Es una maqueta: no sale de la app, así que no prueba que quepa a `fontScale` 1,3 ni en DE.
