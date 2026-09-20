# IOS-A-MISSING-IMPORT-BREAKS-THE-WHOLE-TARGET-001 · master no compilaba para iOS

**Estado:** 🔵 En revisión · rama `bugfix/IOS-A-MISSING-IMPORT-BREAKS-THE-WHOLE-TARGET-001`

## Problema

`./gradlew :shared:compileKotlinIosSimulatorArm64` sobre **master** falla:

```
e: IosAppNotificationManagerImpl.kt:307:40 Unresolved reference 'notif_action_ack'.
```

Un `import paparcar.composeapp.generated.resources.notif_action_ack` que falta. Compose Resources
exige importar **cada** accessor uno a uno, y la lista del fichero salta de `notif_action_confirm` a
`notif_action_ive_left`.

## Cómo llegó aquí

Entró con `NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001` (PR #36, `f1296ae8`), que añadió la
acción «Got it» a la tarjeta post-guardado. El lado Android compila porque su fichero tiene sus
propios imports; el de iOS se quedó sin el suyo, y **nadie compiló el target de iOS en ese PR**.

Es el patrón recurrente que ya tiene nombre en la memoria del proyecto: *Android es la plataforma
principal y el target de iOS regresiona sin que nadie lo note*. Aquí ni siquiera fue una API
JVM-only: fue una línea de import.

## Por qué importa AHORA y no dentro de un mes

La rama del login de Apple (PR #29) estaba **detrás de master**, así que compilaba y subió la 0.1.1
a TestFlight sin tocar este fichero. Al rebasarla sobre master esta madrugada se trajo la rotura:
**el siguiente build de iOS habría fallado**, y con una versión en revisión eso se descubre en el
peor momento.

## Fix

El import. Una línea.

## Lo que este ticket NO arregla (y merece su propio)

Nada vigila que el target de iOS compile. Un `:shared:compileKotlinIosSimulatorArm64` en CI —o un
guard que exija que cada `Res.string.X` usado en `iosMain` tenga su import— habría cazado esto en el
PR #36. Se puede montar; dime si lo abro.

## Verificación

`./gradlew :shared:compileKotlinIosSimulatorArm64` → **BUILD SUCCESSFUL**.
