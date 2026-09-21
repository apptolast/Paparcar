# IOS-A-MISSING-IMPORT-BREAKS-THE-WHOLE-TARGET-001 · master no compilaba para iOS

**Estado:** ✅ Done · en master (PR #64)

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

## Consumidores auditados — barrido de TODO `iosMain` (21-09-2026, al mergear)

Un import que falta es la clase de fallo que nunca viene solo, así que antes de mergear se barrió
el módulo entero: para cada `.kt` de `shared/src/iosMain`, todos los `Res.{string,drawable,plurals,
array,font}.X` usados contra los `import paparcar.composeapp.generated.resources.X` declarados.

**Resultado: `notif_action_ack` era el ÚNICO que faltaba en todo el módulo.** El arreglo de una
línea cierra el problema entero, no una de sus caras. Comprobado además que la key existe en los
**nueve** locales, así que el accessor se genera y el import resuelve.

## ⛔ Corrección: el guardarraíl SÍ existe — lo que no existe es que arranque

Este doc decía «nada vigila que el target de iOS compile». **Es falso, y el matiz cambia el
remedio.** `.github/workflows/ci.yml` tiene el job `iOS framework (Kotlin/Native)` desde el
29-08-2026 (`02a29f620`, [CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001]), y su condición
—`github.event_name == 'pull_request'`— lo hace correr en **cada pull request a master**. O sea que
el PR #36 **tenía** su chequeo de iOS, y el chequeo debería haberlo cazado.

Lo que pasó es que **no arrancó**. Lo medido el 21-09-2026 con la API de GitHub, y **solo** lo
medido:

- El último run verde es del **13-09 a las 10:01**. Desde entonces, **100 de 100** runs fallan.
- Falla en **todas** las ramas, master incluida — no es cosa de una rama ni de un cambio concreto.
- Los jobs terminan en **2-7 segundos** con `steps: 0`, **sin logs** y con `output.title` y
  `output.summary` a `null` en el check-run. Es decir: **no llegan a arrancar**, no es que el build
  falle.

⛔ **La causa NO está confirmada.** Una versión anterior de este doc la daba por «facturación de
Actions»; la API no dice eso en ninguna parte y no hay forma de verificarlo desde aquí. Un job que
muere con `steps: 0` es compatible con un límite de gasto, con Actions deshabilitado para la org y
con un problema de runners, y no tenemos con qué distinguirlos. Queda escrito como **síntoma
medido**, no como diagnóstico.

Lo que sí se sigue de esto, sea cual sea la causa: **no se abre ticket de «montar un guard de
iOS»**. Montar un segundo guard sobre uno que ya existe y no arranca no arregla nada — sería la
duplicación que [DET-VERDICT-NOT-PREDICATE-001] evita en su terreno. Lo que hay que devolver a la
vida es el CI que ya está escrito.

## Verificación

- `./gradlew :shared:compileKotlinIosSimulatorArm64` → **BUILD SUCCESSFUL** (autor del PR; el
  compilador de Kotlin/Native para iOS exige macOS y aquí se desarrolla en Windows).
- Barrido estático de imports de `iosMain` → limpio, ni una ausencia más (arriba).
