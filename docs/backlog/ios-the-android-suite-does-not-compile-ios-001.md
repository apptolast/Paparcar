# IOS-THE-ANDROID-SUITE-DOES-NOT-COMPILE-IOS-001 · la suite no miraba iOS

**Estado:** ✅ Done · rama `fix/IOS-THE-ANDROID-SUITE-DOES-NOT-COMPILE-IOS-001`

## Cómo apareció
Al ponerse por fin con iOS, lo primero fue comprobar qué se puede hacer desde Windows. Resultado
inesperado y bueno: **el klib de iOS SÍ compila en Windows** (`compileKotlinIosSimulatorArm64`).
Lo que no se puede es **enlazar el framework**, que necesita macOS.

Y el primer intento **falló**: `IosAppPreferences` no implementaba `vouchedSpot` / `setVouchedSpot`,
los dos miembros que [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001] añadió a
`AppPreferences` **el mismo día**.

## El agujero, que no es el fichero
⛔ **`testAll` no compilaba iOS.** Once PRs verdes seguidas, **3594 tests**, y ninguna se enteró de
que la app de iOS había dejado de compilar — porque el comando que todo el mundo escribe no lo
miraba. Es exactamente el mismo agujero que
[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001] cerró para el grafo del flavor mock, un peldaño
más arriba.

Y choca de frente con la regla del proyecto: *toda tarea lleva su lado iOS — o `iosMain`, o un
ticket con el hueco escrito*. Una regla que sólo vive en la cabeza de quien la escribió es una
intención; ésta ahora tiene testigo.

## Diseño
- `IosAppPreferences` implementa los dos miembros, con **el MISMO formato de serialización** que
  Android: es el mismo registro, y dos serializaciones para un dato que se lee en un solo sitio son
  dos formas de leerlo mal.
- **`testAll` depende de `:shared:compileKotlinIosSimulatorArm64`.** Cuesta ~30 s y caza firmas que
  faltan y `expect/actual` sin pareja.

⚠️ **Lo que este guard NO cubre, y queda dicho**: el enlazado del framework y todo lo que sólo falla
en un simulador de verdad. Eso sigue necesitando un Mac. Esto caza el 90 % barato — el que se
rompe escribiendo Android.

## Criterio de éxito
**Falsificado**: quitando otra vez los dos miembros de `IosAppPreferences`, `testAll` **falla** —
antes pasaba.
