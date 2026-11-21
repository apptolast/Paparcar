# PARKINGS-PUT-LIVE-AVAILABILITY-FIRST-001 · Dar prioridad a las plazas libres en mapa, lista y ficha

**Estado:** ✅ Done

## Problema observado

En la variante prod, la ficha de Recoletos mostró «De pago», «116 plazas libres» y «Capacidad: 391». La disponibilidad tenía el mismo peso que el resto de datos y compartía el icono P con el aforo. La fila y el marcador seguían mostrando solo «De pago».

La ficha que ya muestra ocupación está en el worktree de `PARKINGS-SHOW-FEE-AND-OCCUPANCY-001`. Esta rama parte de `master` e incluye la lista, el mapa y la ficha.

## Datos disponibles

La API de Paparcar entrega si el parking es de pago o gratuito, plazas libres con fecha de medición cuando la fuente las publica, y aforo cuando la base lo incluye. Para Recoletos, `offer` llegó vacío. El importe en euros requiere una fuente de tarifas integrada en Paparcar. La cifra de aforo y las plazas libres pueden cubrir conjuntos distintos. En Recoletos, EMT publicó 398 plazas totales tras la reforma y Paparcar muestra 391. [EMT Madrid](https://www.emtmadrid.es/getattachment/98cc1886-9807-4331-bea7-19ff5ca05d35/El-lunes-reabre-Recoletos-360%2C-transformado-en-un.aspx)

## Diseño a validar

- **Lista.** En cada fila, mantener nombre y distancia fáciles de localizar. Mostrar la disponibilidad a la derecha y debajo «De pago» o «Gratuito», con la misma tipografía y color en las dos líneas. La columna derecha comparte con el nombre y el subtítulo sus alturas de línea y la separación vertical. Si falta ocupación fresca, la tarifa conocida ocupa el espacio principal.
- **Mapa.** Probar una píldora con la P, cifra o estado principal y un calificador de tarifa cuando quepa, siguiendo la jerarquía de las píldoras de gasolineras. La primera línea nombra las plazas en los nueve idiomas («143 plazas libres», «143 spaces left»), con singular al quedar una plaza. Verificar legibilidad con mapa denso y a distintas escalas, seleccionado y sin seleccionar. Reservar el aforo para la ficha si la píldora y la fila pierden legibilidad al incluirlo.
- **Ficha.** Dar a disponibilidad y tarifa una posición destacada cerca del título. Bajar aforo, horario y gálibo a datos secundarios. La P identifica el parking en la cabecera. Probar las métricas sin iconos y, si ayudan a leerlas, usar iconos diferenciados de disponibilidad y pago.
- Compartir el criterio de frescura entre las tres superficies. Mostrar el aforo como dato independiente y omitir porcentajes de ocupación o fórmulas «N de M» sin confirmar que ambos datos cubren las mismas plazas. Mantener los colores del producto y el acceso a los parkings.

## Criterios de aceptación

- Fila, marcador y ficha muestran la misma disponibilidad fresca para un parking, incluido el caso «Completo». Si caduca o falta, desaparece la cifra y se conserva la tarifa conocida.
- La ficha destaca disponibilidad y tarifa y muestra una única P. Los datos secundarios quedan legibles con texto ampliado.
- Validar en el Dev Catalog los casos con ambos datos, completo, caducado y sin datos. Adjuntar capturas de lista, mapa y ficha, y verificar al menos un parking con datos reales en prod. Usar los nueve idiomas del producto.

## Consumidores

`HomePublicParkingRows`, `HomeMapSection`, `PaparcarMapView` y `PublicParkingPeek` están cubiertos. La tarjeta Aquí y ahora conserva su etiqueta de tarifa. El mapa reutiliza la cápsula de `FuelStationMarker` y añade la disponibilidad localizada a la clave del bitmap.

## Validación

- Pixel 8 Pro, variante prod. Recoletos mostró 115 plazas libres en la fila, la cápsula y la ficha. La ficha mostró «Paid» bajo la disponibilidad, el aforo de la base en la sección secundaria y una única P.
- `:app:assembleProdDebug :app:compileMockDebugKotlin` y 22 pruebas dirigidas de frescura, claves de marcadores y backlog pasaron.
- `testAll --console=plain` pasó en 34 s. Ejecutó las pruebas Android de shared y del grafo mock y compiló la klib de iOS Simulator ARM64. La comprobación de iOS se limita a compilación. Tras cambiar la etiqueta de la píldora, pasaron de nuevo `:app:assembleProdDebug :app:compileMockDebugKotlin testAll --no-daemon --console=plain`. En prod, la píldora de Recoletos mostró «148 spaces left» y «Paid» en dos líneas.
- Dev Catalog con ocupación, completo, caducado y sin datos. Previews Android de lista, marcador y ficha, incluida una variante con texto ampliado. La ficha de Recoletos conservó disponibilidad, tarifa, aforo y el botón de ruta visibles al 150 % en el emulador.
