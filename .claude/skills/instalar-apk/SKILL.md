---
name: instalar-apk
description: Compilar el APK prodDebug de Paparcar e instalarlo en TODOS los móviles físicos conectados, con verificación de hash en device y arranque de la app. Es lo que significa "/run" en este proyecto. Usar cuando el user diga "run", "instala el APK", "pásalo a los móviles", "compila y mete en el Oppo/Redmi", o cuando haga falta ver un cambio corriendo en hardware real (no en tests).
---

# Instalar Paparcar en los móviles conectados

`/run` en este proyecto **no** levanta un emulador ni corre tests: compila `prodDebug` desde el
árbol actual y lo mete en los móviles físicos que estén enchufados. Nada más, y todos ellos.

## ⛔ Invariantes

- **Compilar SIEMPRE con la herramienta Bash** (`./gradlew …`). El repo no tiene `gradlew.bat`: en
  PowerShell `.\gradlew` sale **exit 0 sin compilar nada** y te instala el APK de ayer.
- **`install -r`, nunca `uninstall`.** Los móviles llevan sesiones de aparcamiento, login y estado de
  detección reales; borrarlos tira el field-test en curso. Si hace falta desinstalar, **preguntar**.
- **Verificar el sha256 EN el device.** MIUI/Redmi puede contestar `Success` y dejar el APK viejo;
  sin esa comprobación te pasas media hora teorizando sobre un bug ya arreglado.
- **Todos los conectados.** Si hay dos móviles, van los dos: el setup de campo es intencionalmente
  de dos coches/dos cuentas y comparar un móvil contra el otro es medio diagnóstico.
- **⛔ No tocar ajustes del móvil: pantalla, bloqueo ni rotación.** Nada de `input keyevent 26`
  (apagar), swipes contra el keyguard ni `settings put system accelerometer_rotation`. Los dos
  móviles van **vertical fijo sin auto-rotación** y el user los quiere así; cada vez que se cambian
  tiene que rehacerlo a mano. Si un caso necesita la pantalla bloqueada, **pedírselo al user** — el
  Oppo pide credencial y adb no puede desbloquearlo de todas formas.

## 1 · Inventario

```bash
adb devices -l
```

| Serial | Modelo | Quién es en los field-tests |
|---|---|---|
| `LNRCMZ8H6HBITWNJ` | `CPH2371` | **Oppo** — cuenta principal, Škoda Kamiq con **BT real** (estrategia BLUETOOTH) |
| `5f8991cb` | `2201117TY` | **Redmi** — **OTRA cuenta**, C5 Aircross ficticio sin BT (estrategia COORDINATOR) |

Ninguno conectado → decirlo y parar. `unauthorized` → el móvil tiene el diálogo de depuración
esperando; pedir al user que lo acepte.

## 2 · Compilar

```bash
./gradlew :composeApp:assembleProdDebug --console=plain 2>&1 | tail -8
APK=composeApp/build/outputs/apk/prod/debug/composeApp-prod-debug.apk
sha256sum "$APK"
```

> Flavor `mock` (`assembleMockDebug`) es **otro applicationId** (`…paparcar.mock`) y convive sin
> pisar al de producción: se instala igual, pero solo si el user pide el Dev Catalog.

## 3 · Instalar y verificar

```bash
APK=composeApp/build/outputs/apk/prod/debug/composeApp-prod-debug.apk
for D in $(adb devices | awk '/\tdevice$/{print $1}'); do
  echo "=== $D ==="
  adb -s $D install -r "$APK" 2>&1 | tail -2
  P=$(adb -s $D shell pm path io.apptolast.paparcar | head -1 | tr -d '\r' | sed 's/package://')
  adb -s $D shell sha256sum "$P" | tr -d '\r'
done
sha256sum "$APK" | cut -d' ' -f1        # los tres hashes deben coincidir
```

**Si `INSTALL_FAILED_UPDATE_INCOMPATIBLE`** (firma distinta — pasó con una beta02 en el Oppo): NO
desinstalar por tu cuenta. Compilar `:composeApp:assembleProdRelease`, que va firmado con el keystore
del repo, e instalar ese con `-r`; conserva los datos. Si tampoco entra, preguntar al user.

## 4 · Arrancar y mirar

Instalar sin arrancar solo prueba que el paquete entró. Arrancar y leer el log es lo que dice si la
app vive:

```bash
for D in $(adb devices | awk '/\tdevice$/{print $1}'); do
  adb -s $D shell am start -n io.apptolast.paparcar/.MainActivity
done
```

Y comprobar que no se ha caído ni ha quedado muda:

```bash
adb -s <serial> logcat -d -t 200 | grep -iE "FATAL|AndroidRuntime|PaparcarApp|DepartureWatch|Coordinator"
adb -s <serial> shell dumpsys activity services io.apptolast.paparcar | grep -E "ServiceRecord|isForeground|channel"
```

Si el cambio que se está probando toca detección, el criterio de éxito concreto (qué línea de log
debe salir) vive en `docs/backlog/<ticket>.md` — leerlo antes de dar el run por bueno.

### Tocar la UI desde adb

```bash
export MSYS_NO_PATHCONV=1                       # Git Bash reescribe /sdcard/… si no
adb -s $D shell uiautomator dump /sdcard/ui.xml
adb -s $D shell cat /sdcard/ui.xml | tr '<' '\n<' | grep -o 'text="[^"]*"' | grep -v 'text=""'
```

El `bounds="[x1,y1][x2,y2]"` del nodo da el centro para `input tap`. El sheet de Home arranca
plegado: casi todo (incluida la superficie de detección) exige expandirlo con
`input swipe 540 1950 540 600 400`, y conviene volver a plegarlo al terminar.

### Provocar la fila de "vigilancia detenida"

`am stopservice …CoordinatorDetectionService` **no basta**: con la app en primer plano el lane
automático la resucita en ~60 ms y el CTA no llega a pintarse. Truco: pararlo **dos veces
seguidas** — la segunda cae dentro de `AUTOMATIC_RETRY_COOLDOWN_MS` (60 s), el lane contesta
`skipped — automatic retry cooling down` y la fila queda visible para tocar "Reactivar".

## 5 · Reportar

Decir siempre: **qué commit** se ha compilado (`git log --oneline -1`), el **sha256** y que coincide
en ambos móviles, y **qué se ha visto** al arrancar. Un run sin esas tres cosas no distingue "va
bien" de "estoy mirando el APK de ayer".
