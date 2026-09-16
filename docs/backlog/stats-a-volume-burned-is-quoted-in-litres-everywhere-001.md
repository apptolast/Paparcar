# STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001 · La cifra de litros gastados no cambia de unidad nunca

**Estado:** 🔵 Abierto, sin empezar · descubierto al cerrar
[I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]

## Problema

`stats_value_liters` («%1$s L») es la ÚNICA magnitud de volumen que no pasa por ningún borde de
unidad. Con el ajuste en imperial el consumo ya se enseña en **mpg** y con locale de EE. UU. el
precio ya se cotiza **por galón**, pero los litros gastados del periodo siguen diciendo «142 L» en
los nueve locales y en los dos ajustes.

Es el mismo fallo de familia que sus dos hermanos y el último que queda de la serie: una cifra
correcta bajo una unidad que ese usuario no usa.

## Por qué no entró en ninguno de los dos tickets anteriores

- [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001] cerró las magnitudes POR DISTANCIA (l/100 km ⇄
  mpg): los litros gastados no son una magnitud por distancia, son un volumen suelto.
- [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001] cerró el DINERO por volumen. Un volumen a
  secas no es un precio, y ampliar aquel encargo a esto habría sido cambiar el alcance por mi cuenta.

## Lo que ya está hecho y este ticket reutiliza

`pumpPriceToDisplay` / `pumpPriceFromDisplay` (`presentation/util/PumpPriceFormat.kt`) convierten
**dinero** por volumen. Para un volumen a secas hace falta la conversión desnuda —`litros ÷
litros-por-galón`— con las mismas constantes compartidas (`LITRES_PER_US_GALLON`) y el mismo
selector (`LocalPumpGallon`), más una key `stats_value_gallons` en los 9 locales.

## La pregunta que hay que decidir antes de escribir código

⛔ **¿Qué galón?** Y aquí NO vale `localePumpGallon()` sin pensarlo: un británico compra litros —por
eso el precio no se le toca— pero habla de su coche en **galones imperiales**, y la cifra de litros
gastados está más cerca de «lo que gasta mi coche» que de «lo que cuesta en el surtidor». Puede que
la señal correcta sea la del consumo (el ajuste imperial + `localeGallonKind()`) y no la del precio.
Son las dos preguntas que [I18N-A-RADIUS-…-001] ya tuvo que separar, y ésta cae justo en medio.
Decidirlo con el user antes de tocar nada.
