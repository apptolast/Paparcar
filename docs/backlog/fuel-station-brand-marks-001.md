# FUEL-STATION-BRAND-MARKS-001 · Reconocer la marca de un vistazo

**Estado:** 🔵 Decidido, sin empezar — sin rama · sale de [`fuel-stations-list-001.md`](fuel-stations-list-001.md) ✅

**Decisión del user (09-09-2026): camino A — monograma en nuestro tile.** El B (logos reales de las
~12 grandes) queda descartado para la primera versión; si algún día se revisa, se revisa mirándolo
en pantalla, no sobre el papel.

## Problema

En la lista de «dónde repostar» todas las filas llevan el mismo glifo de surtidor. El nombre ya
está escrito, pero una lista que se mira mientras decides dónde parar se **escanea**, no se lee, y
el color y la forma de una marca se reconocen antes que su nombre.

## Lo medido antes de proponer nada (09-09-2026, feed entero del Ministerio)

**11.493 gasolineras · 3.548 rótulos distintos · el 90 % de los rótulos aparece UNA sola vez.**

| Marcas con glifo | Cobertura |
|---|---|
| top 10 | 52,3 % |
| top 12 (match exacto) | 54,5 % |
| top 12 (match por subcadena) | 61,1 % |
| top 30 | 61,3 % |
| **top 100** | **66,9 %** |

Las diez primeras: REPSOL (23,9 %), MOEVE (5,2), CEPSA (5,0), GALP (4,0), BALLENOIL (3,6),
PLENERGY (3,4), SHELL (3,0), PETROPRIX (1,5), PETRONOR (1,4), BP (1,3).

**La curva se aplana enseguida**: pasar de 20 marcas a 100 compra 8 puntos. Entre un tercio y la
mitad de las filas **no tendrá glifo de marca nunca** — son 3.206 independientes con nombre propio
(`REPSOL N ESTACION 97179`, `INLOCOR S.L. "CEPSA"`, `BP SANDOVAL MD`).

⛔ **El match tiene que ser EXACTO, no por subcadena.** Los 6,6 puntos que gana la subcadena los paga
con `"BP"` —dos letras— buscándose dentro de cualquier rótulo. Es la misma trampa que el fallback por
regex de `VehicleCatalog.inferBodyType()`.

## Lo que decide el diseño no son los glifos, es el tercio que no lo tendrá

Una columna donde seis filas llevan marca y el resto un glifo genérico no se lee como variedad: se
lee como **carga a medias**. Así que la pieza que se diseña es el respaldo, y la marca es el extra.
La regularidad la sostiene el **tile**: `PapIconTile` mantiene forma, tamaño e inset idénticos en
todas las filas, y lo único que cambia es su contenido.

## ⛔ Redibujar los logos «a nuestro terreno», en verde marca: NO

Se propuso y se descarta por tres razones, en este orden:

1. **Un logo redibujado sigue siendo la marca ajena.** Reproducir el logotipo real para identificar
   al negocio real es uso nominativo y se defiende; **alterarlo** es justo lo que prohíben las guías
   de marca de Repsol, Shell o BP. La versión "nuestra" cambia un uso defendible por uno que no lo es.
2. **El monocromo mata lo que hace útil a un logo.** El reconocimiento a un vistazo lo lleva primero
   el COLOR: Shell es amarillo y rojo, BP verde y amarillo, Repsol naranja. Todos en el mismo verde
   Paparcar quedan como una columna de manchas verdes del mismo peso — habría que **leerlos**, y si
   hay que leerlos el nombre de la fila ya lo hacía mejor. El rediseño destruye el beneficio que
   justifica el rediseño.
3. **Rompe nuestra doctrina de color.** Aquí el verde significa *marca y acción* (CTA, links, nav) e
   identidad de vehículo vigilado. Pintar de verde marcas ajenas le añade un cuarto significado, y
   todo el sistema se apoya en que un color dice **una** cosa. Ver `docs/design/COLOR-SYSTEM.md`.

⚠️ Además, un logotipo ajeno no encaja en los tres niveles del sistema de iconos: el Nivel 3 pide
**vector propio**. Meterlos abre una cuarta categoría, y eso se decide en la doctrina (CLAUDE.md),
no colando ficheros en `drawable/`.

## Dos caminos, y cuál recomiendo

**A · Monograma en nuestro tile (recomendado para la primera versión).** La inicial del rótulo, en
nuestra tipografía y nuestro tile. Es 100 % nuestro, cubre **todas** las gasolineras incluidas las
3.206 independientes, no tiene pregunta legal, no cuesta ni un asset y **nunca parece carga a
medias**. Reconoce menos que un logotipo real, pero muchísimo más que nada.

**B · Logotipo real de las ~12 grandes, sin tocar, + glifo genérico en la cola.** Da el
reconocimiento de verdad en el 55 % de las filas. Cuesta conseguir los vectores con procedencia
defendible —no existe ninguna fuente de logos con licencia que podamos consumir— y **el trabajo real
son las variantes oscuras**: muchos son texto oscuro sobre blanco y la app es oscura por defecto, así
que cada marca necesita su `_dark`.

Se pueden combinar (logo real donde lo haya, monograma en la cola), pero eso son otra vez dos
lenguajes visuales en la misma columna: decidirlo mirándolo, no sobre el papel.

## Criterio de éxito

- La columna se lee regular con cualquier mezcla de filas con y sin marca.
- Ninguna marca ajena aparece recoloreada ni alterada.
- El mapeo rótulo → glifo es exacto y está testeado con rótulos reales del feed, incluida la cola.
- Cobertura declarada en el propio ticket, no supuesta.
