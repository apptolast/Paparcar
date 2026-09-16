# PRIV-A-LAPSED-PLUS-STILL-UPLOADS-ROUTES-001 · Caducar Plus no apaga la subida de rutas, y la política dice que sí

**Estado:** 🔴 Sin empezar — 1.2.1
**Origen:** revisión independiente, 2.ª vuelta — hallazgo C8.

## Problema

`RemoteUserProfileDataSourceImpl` decide subir la geometría mirando **solo la preferencia**
(`appPreferences.syncRouteGeometry`). Esa preferencia solo se puede encender siendo Plus, pero
**nadie la apaga al perderlo**: quien se dio de baja sigue subiendo su recorrido a la nube.

La política publicada dice, en §2.5, que la ruta sube *«solo si tienes Plus y activas la
sincronización»*. Hoy la primera mitad de esa frase no la comprueba nadie.

## Tensión que hay que resolver, no ignorar

⛔ Choca con la doctrina escrita en `VehicleEntitlementGate`: *«losing Plus never deletes or disables
anything, it only gates what is NEW»*. Aquí **sí** hay que apagar algo, y se sostiene porque lo que
se apaga no es una función del usuario: es una **transferencia de datos suyos**, consentida bajo una
condición que ya no se cumple. Dejar de subir no le quita nada; seguir subiendo le quita el
consentimiento.

Lo que NO debe pasar: borrar lo ya subido. Eso es retención, y la política ya la describe.

## Diseño

Combinar el consentimiento con `observeIsPlus` en la única puerta que ya existe, y decidir qué ve el
usuario en Ajustes: el interruptor tiene que **explicar por qué está apagado**, no aparecer apagado
sin más.
