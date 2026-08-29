# CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001 · el CI compila iOS, para que dejar de ser una promesa

**Estado:** ✅ Done (el job existe y corre) · 🟡 **queda anotar aquí el resultado de su primera
ejecución** — ver «Criterio de éxito». El escalón de `xcodebuild` sigue fuera a propósito, a la
espera del scheme compartido (riesgo 1).

## Problema

**Nada ni nadie compila `iosMain` en este proyecto.** No es una laguna teórica:

- Yo no puedo: el desarrollo va desde Windows y Kotlin/Native para iOS exige macOS.
- El CI tampoco: los tres workflows (`ci.yml`, `distribute-alpha`, `distribute-beta`) corren en
  `ubuntu-latest` y sólo hacen `:app:assembleProdDebug`, `:app:compileMockDebugKotlin` y
  `:shared:testDebugUnitTest`. Ni una tarea de iOS.
- El compañero que valida iOS lo hace a mano, cuando puede, sobre lo que haya en `origin` — que hoy
  es la rama vieja, con el paquete anterior y la estructura `composeApp/`.

Consecuencia medida hoy (29-08): la rama `IOS-F0-001` lleva 20 commits de trabajo iOS y su
recompute sobre el split **editó `IosDetectionModule.kt` a mano** (bus común + 4 bindings de los
ports de IOS-F0-06). Esas ediciones **no las ha compilado nada**. Los 1.778 tests verdes no dicen
absolutamente nada sobre iOS: son JVM. Mergear en ese estado es meter en master código que nadie
puede verificar, y el fallo aparecería semanas después, en la máquina de otra persona.

El agravante estructural: `iosMain` se rompe **en silencio**. Un import mal, un tipo que no existe
o un `expect` sin `actual` no fallan ningún build de los que corremos — sólo el que nunca corremos.

## Doctrina violada

*Fallo asimétrico: mejor falso negativo que falso positivo* — aplicado al proceso, no al algoritmo.
Hoy iOS sólo puede dar falsos positivos: todo parece verde porque nadie mira. Y el propio
`ARCH-HEALTH-001` dejó escrito que iOS «lo valida un compañero», que es una dependencia humana
puesta donde debería haber una máquina.

## Señales / datos disponibles

Verificado hoy, no supuesto:

| Dato | Estado |
|---|---|
| `apptolast/Paparcar` es **PUBLIC** | ✅ → los minutos de `macos-latest` son **gratis** (no hay argumento de coste) |
| `:shared:linkDebugFrameworkIosSimulatorArm64` | ✅ la tarea existe |
| `GoogleService-Info.plist` referenciado en el pbxproj | ✅ **0 referencias** → su ausencia (gitignored) **no** rompe el build |
| Schemes compartidos en `iosApp.xcodeproj/xcshareddata/xcschemes/` | ❌ **NO existen** — ver riesgo 1 |
| BaseLogin ya tiene el job resuelto | ✅ `ci.yml` job `apple` en `macos-latest`: plantilla probada |

## Diseño

Un job `apple` nuevo en `.github/workflows/ci.yml`, en paralelo al de Android, copiando el patrón ya
probado en BaseLogin y adaptado al split `:app`+`:shared`:

```yaml
  apple:
    name: iOS framework + app
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Link iOS framework
        run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
      - name: Build the iOS app
        run: |
          xcodebuild build \
            -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -configuration Debug \
            -destination 'generic/platform=iOS Simulator' \
            ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
            CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
```

**Dos escalones, y son distintos a propósito.** El primero (`linkDebugFramework…`) compila
Kotlin/Native: es el que caza lo que yo puedo romper desde Windows. El segundo (`xcodebuild`)
compila el Swift de `iosApp/`, que nadie ha compilado nunca desde este repo. Sin firma: el runner
no tiene certificados y una build de simulador no los necesita.

### Implementado (29-08): sólo el primer escalón

Se toma deliberadamente la salida (b) del riesgo 1: **el job entra sólo con el link de
Kotlin/Native**, sin `xcodebuild`, porque el scheme compartido depende de una Mac que no tengo. Eso
ya cubre lo único que yo puedo romper a ciegas desde Windows. El paso de `xcodebuild` se añade —
tal cual está escrito arriba — el día que se commitee
`iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`.

Dos detalles del job que no son obvios y conviene no "limpiar" luego:

- **Sí se inyectan los secrets** (`google-services.json` + `local.properties`), aunque la tarea viva
  en `:shared`. Gradle configura `:app` igualmente, y su build script lee `local.properties` y
  aplica el plugin de Google Services: sin esos ficheros la ejecución muere en configuración, antes
  de que Kotlin/Native llegue a arrancar. (El plist de iOS sigue sin hacer falta: 0 referencias en
  el pbxproj.)
- **`~/.konan` va en la caché** junto a las de Gradle, con clave propia: el toolchain de
  Kotlin/Native son cientos de MB que se descargan en la primera ejecución.

**Sin `continue-on-error`.** Si el job sale rojo es porque iOS está roto de verdad, y ese es el dato
que este ticket existe para producir; esconderlo detrás de un verde reproduce justo el problema que
venimos de tener (un CI en rojo durante días al que nadie miraba). El job de Android es
independiente, así que un rojo aquí no oculta ni bloquea la señal de Android.

## Riesgos conocidos, con su plan

1. **No hay scheme compartido.** `xcodebuild -scheme iosApp` fallará en el runner: los schemes no
   compartidos viven en `xcuserdata/`, que está gitignored. Dos salidas, en este orden:
   a) compartir el scheme desde Xcode y commitear
      `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` — **lo tiene que hacer el
      compañero en su Mac**, es un checkbox en *Manage Schemes*; o
   b) si tarda, arrancar el job sólo con el paso de `linkDebugFramework…`, que ya aporta el 80% del
      valor y no necesita scheme. El paso de `xcodebuild` se añade después.
2. **La primera ejecución puede salir roja, y eso es el éxito del ticket, no su fracaso.** Nadie ha
   compilado `iosMain` desde el split ni desde varios refactors de detección. Si sale roja, lo que
   descubre es deuda real que hoy está oculta — arreglarla NO es parte de este ticket: se abre uno
   aparte con el error concreto.
3. **iOS no debe bloquear la entrega de Android.** El job va como job independiente del de Android;
   si hace falta, `continue-on-error: true` durante los primeros días para que el rojo informe sin
   frenar. Retirarlo en cuanto esté verde una vez.

## Criterio de éxito

- `ci.yml` tiene un job en `macos-latest` que corre en cada push a `master` y en cada PR.
- El job enlaza el framework de `:shared` — y, con el scheme compartido, compila también `iosApp`.
- Queda ejecutado al menos una vez sobre `master` y su resultado (verde o rojo) está **anotado
  aquí**, porque el primer resultado es el dato que este ticket existe para producir.
- La decisión de mergear `IOS-F0-001` deja de apoyarse en una promesa.

## Consumidores auditados

- `.github/workflows/ci.yml` → recibe el job nuevo.
- `distribute-alpha.yml` / `distribute-beta.yml` → **exentos**: distribuyen el APK de Android; iOS
  no tiene canal de distribución todavía.
- `docs/backlog/ios-f0-001.md` → su lista `VERIFICAR-MAC-F0` deja de ser la única red; anotar que
  el link de Kotlin/Native pasa a estar cubierto por CI.
- `CLAUDE.md` / skills → sin cambios: no se añade ningún comando que se ejecute desde Windows.
