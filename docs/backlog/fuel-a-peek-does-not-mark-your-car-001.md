# FUEL-A-PEEK-DOES-NOT-MARK-YOUR-CAR-001 · el peek lista los surtidores, ninguno lleva tu nombre

**Estado:** 📋 Producto — pide decisión, no un fix · sin rama de código

## Problema

Follow-up explícitamente fuera de alcance de
[`fuel-a-hybrid-fills-up-with-petrol-001`](fuel-a-hybrid-fills-up-with-petrol-001.md), detectado al
verificar ese ticket en device (Pixel 8 Pro, Berlín falseada, Aiways U6 HYBRID).

La fila y el marcador ya cotizan lo que este coche compra (`FuelType.pumpProduct`: un híbrido
reposta gasolina). El peek (`FuelStationPeek`) hace la pregunta contraria a propósito — lista **todos
los surtidores que la fuente declaró**, «Gasoline 2.349» y «Diesel 2.399» uno debajo del otro, sin
distinguir cuál es el que llena este depósito. Para un coche de un solo combustible da igual: uno de
los dos ya viene con precio y el otro con «not sold». Para un híbrido, un plug-in híbrido o cualquier
coche futuro con más de un producto en la lista, **las dos líneas parecen igual de relevantes**.

No es un bug — `FuelStationPeek` documenta ese diseño a propósito: «*the whole station, not one
price*». Es una pregunta de producto sin decidir: ¿destaca el peek el surtidor que compra ESTE
coche, o se queda neutral porque también sirve para mirar una estación sin coche activo?

## Doctrina relevante

`MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001` ya fija que la fila y el marcador citan la cifra del
coche activo. El peek es la única superficie de precio que no lo hace, y puede que sea correcto que
no lo haga — es el único sitio donde ver TODOS los productos de una estación es la pregunta.

## Qué haría falta decidir

- Si el peek debe marcar visualmente la fila del producto que compra el coche activo (negrita, un
  icono, un orden que la ponga primero) — y qué pasa cuando no hay coche activo o el coche no tiene
  `fuelType`.
- Si aplica solo a coches con `pumpProduct` compartido (HYBRID, PLUG_IN_HYBRID) o a todos, por
  consistencia con la fila/marcador.

## Consumidores

`FuelStationPeek.kt:140-150` (`spokenFuels.forEach { fuel -> FuelPriceRow(...) }`) — itera productos,
no pregunta por el coche. Sin cambios hasta que haya decisión.
