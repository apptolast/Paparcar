# VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001 · el gálibo del vehículo

**Estado:** ✅ Done · rama `feature/VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001` · issue #80

## Problema
El filtro «¿cabe mi coche?» no se pudo hacer en #69, y no por falta de dato del parking
(`maxHeightMeters` existe, cobertura 0-7 %): **un `Vehicle` no tenía altura**. `CarbodyType` describe
anchura e identidad visual, no metros.

## Lo que el ticket pedía, y lo que resultó ser posible

### ⛔ «Inferir la altura de marca + modelo» — NO se puede hacer honestamente
`VehicleCatalog` tiene **1.800 líneas y miles de modelos, ninguno con altura**. Rellenarlas sería
escribir a mano miles de cifras sin fuente, y luego dar un veredicto («cabes») que se comprueba
contra una barra de acero.

Lo que sí sabemos es el **cuerpo**, y un cuerpo no da una cifra: da un **rango**. Un utilitario mide
1,40-1,60; una furgoneta comercial, 1,90-2,80. Esa diferencia contesta *¿paso por debajo de 1,80?*
en los casos que importan, y **se calla** cuando la banda cruza la barrera — que es exactamente
donde nadie debería pronunciarse.

Y ahí es donde el campo manual cobra su sentido real: **declarar el alto colapsa la banda a un
punto** y convierte un «no lo sé» en un veredicto. Una baca o un cofre no están en ningún catálogo.

### El veredicto, y su asimetría
```
FITS          banda.alta + 5 cm <= barrera     ← lo ÚNICO que autoriza a prometer
DOES_NOT_FIT  banda.baja > barrera             ← vale para esconder, nunca para prometer
UNKNOWN       falta un dato, o la banda cruza
```
⚠️ **El margen sólo se exige en un sentido**, y no es un descuido: es la doctrina de fallo asimétrico
aplicada a una barra de acero. Equivocarse escondiendo un parking cuesta un rodeo; equivocarse
prometiendo que cabes cuesta el techo del coche. Los 5 cm salen de que un gálibo se mide al punto
más bajo del dintel y un coche no siempre mide lo de su ficha (neumáticos, carga, antena).

### El chip se llama por lo que HACE
⛔ «Cabe mi coche» suena mejor y **sería mentira**: con el 0-7 % de cobertura, enseñar sólo lo que
podemos prometer dejaría la lista casi vacía, justo para la furgoneta que lo necesita. Lo que sí
podemos afirmar es lo contrario. El chip es **«Sin gálibo bajo»**: descarta los que NO caben y deja
pasar lo desconocido.

### Campos opcionales
Había **una** sección marcada —«Apodo (opcional)»— que además contenía la matrícula, así que el
título mentía sobre su propio contenido; las otras cuatro opcionales no decían nada y el formulario
parecía obligatorio entero. Ahora la marca es un `optional: Boolean` de `PapSectionHeader`: **una
palabra, en un sitio**. Con la marca dentro de cada título traducido, nueve idiomas decidían por su
cuenta dónde ponerla y si ponerla.

⚠️ **Verificado lo que el ticket pedía verificar**: obligatorios no son sólo marca, modelo y
combustible — `canSubmit` exige también `sizeCategory` y, para coches, `carbodyType`. **No se han
tocado**: los rellena la inferencia del catálogo, así que el usuario no los ve como una exigencia.
Queda dicho aquí; cambiarlos es una decisión de producto que nadie ha pedido.

## Criterio de éxito
`VehicleClearanceTest`, 11 testigos. **Falsificados** contra el arreglo ingenuo (decidir por el punto
medio de la banda y sin margen): caen los dos que importan —la banda que cruza la barrera, y el que
cabe por menos del margen—.

## Consumidores auditados
| Sitio | Veredicto |
|---|---|
| `Vehicle` · `VehicleEntity` · `VehicleDto` · `VehicleMapper` (×4) · `VehicleReconcile` · `RemoteUserProfileDataSourceImpl` | **cerrado** — `heightMeters` end-to-end, Room **v17** |
| `SharedCarDto` / el espejo del grupo | **exento con razón** — el alto NO viaja al grupo. Un coche vinculado cae en la banda de su carrocería, que ya contesta; añadir un campo a la copia del grupo es una decisión de privacidad que nadie ha pedido |
| `filteredBy(parkingFilter)` ×2 en `HomeState` | **cerrado** — pasan el coche con el que medir |
| `PapSectionHeader` | ampliado con `optional`; los demás call sites no lo pasan y no cambian |

⛔ El coche contra el que se mide es `vehiclesByRecentUse.first()`, **el mismo** «cuál es el de
ahora» que cotiza el carril de gasolineras. Dos superficies preguntando lo mismo y contestándose
distinto es cómo se llega a un carril cotizado para el diésel y un filtro medido para la furgoneta.
