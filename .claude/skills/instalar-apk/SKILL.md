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
- **Verificar el sha256 EN el device** (device ↔ APK local, móvil a móvil — **nunca** un móvil contra
  otro). MIUI/Redmi puede contestar `Success` y dejar el APK viejo; sin esa comprobación te pasas
  media hora teorizando sobre un bug ya arreglado. Para comparar DOS móviles entre sí, paso 3-bis.
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
./gradlew :app:assembleProdDebug --console=plain 2>&1 | tail -8
APK=app/build/outputs/apk/prod/debug/app-prod-debug.apk
sha256sum "$APK"
```

> Flavor `mock` (`assembleMockDebug`) es **otro applicationId** (`…paparcar.mock`) y convive sin
> pisar al de producción: se instala igual, pero solo si el user pide el Dev Catalog.

⛔ **Compilar UNA vez y no volver a invocar Gradle hasta que estén instalados todos los móviles.**
`packageProdDebug` **reempaqueta en cada invocación aunque no cambie una línea** — medido el 26-08:
preguntarle a Gradle si estaba al día cambió el sha del APK de `cb2ff4bb…` a `e4488689…` con el dex
byte a byte idéntico. Si compilas entre un móvil y otro, cada uno se lleva bytes distintos.

## 3 · Instalar y verificar

```bash
APK=app/build/outputs/apk/prod/debug/app-prod-debug.apk
for D in $(adb devices | awk '/\tdevice$/{print $1}'); do
  echo "=== $D ==="
  adb -s $D install -r "$APK" 2>&1 | tail -2
  P=$(adb -s $D shell pm path com.rndeveloper.paparcar | head -1 | tr -d '\r' | sed 's/package://')
  adb -s $D shell sha256sum "$P" | tr -d '\r'
done
sha256sum "$APK" | cut -d' ' -f1
```

**Qué prueba este sha y qué NO.** Prueba que **este** móvil recibió **este** fichero **ahora** — que
es exactamente el `Success` mentiroso de MIUI, así que el paso se queda. **No** prueba que dos
móviles corran el mismo código: el APK no es un identificador de build estable (arriba), y el 26-08
dos móviles cuyos sha yo había leído distintos resultaron llevar el mismo dex. Para esa pregunta,
el paso 3-bis.

## 3-bis · ¿Los móviles corren el MISMO código?

El dex sí es estable: sobrevive a los reempaquetados de Gradle y es lo único que decide la conducta.
~2,5 s para los dos móviles (APK de 42 MB a ~38 MB/s).

```bash
export MSYS_NO_PATHCONV=1
DEST="C:/Users/rndev/AppData/Local/Temp/paparcar-dex"   # ⛔ ruta WINDOWS, ver abajo
mkdir -p /c/Users/rndev/AppData/Local/Temp/paparcar-dex
for D in $(adb devices | awk '/\tdevice$/{print $1}'); do
  P=$(adb -s $D shell pm path com.rndeveloper.paparcar | head -1 | tr -d '\r' | sed 's/package://')
  adb -s $D pull "$P" "$DEST/$D.apk" 2>&1 | tail -1
done
```

```bash
python - /c/Users/rndev/AppData/Local/Temp/paparcar-dex/*.apk \
         app/build/outputs/apk/prod/debug/app-prod-debug.apk <<'EOF'
import sys, zipfile, hashlib
for path in sys.argv[1:]:
    z = zipfile.ZipFile(path)
    h = hashlib.sha256()
    for n in sorted(x for x in z.namelist() if x.endswith(".dex")):
        h.update(z.read(n))
    print(f"{h.hexdigest()[:16]}  {path.split('/')[-1]}")
EOF
```

Todos los dex iguales → mismo código, aunque los sha de los APK difieran. Uno distinto → ese móvil
lleva otro build, y ahí sí hay que reinstalar.

⛔ **`adb.exe` es un binario Windows: el destino local NO puede ser una ruta MSYS.** `adb pull … /tmp/x`
falla con `cannot create file/directory`. Usar `C:/…` (las barras normales le valen) y `MSYS_NO_PATHCONV=1`
para que Git Bash no reescriba la ruta REMOTA `/data/app/…`.

> **Comprobación rápida sin descargar nada**, cuando sólo quieres saber si el build lleva un cambio
> concreto: buscar en el dex un literal que el código viejo tenía y el nuevo no. Así se verificó
> DET-PARKDIAG-KEEP-MORE-HISTORY-001 — el literal `parkdiag.log.old` desapareció al pasar a construir
> los nombres en runtime, así que su ausencia en el dex **es** la firma del build nuevo.

**Si `INSTALL_FAILED_UPDATE_INCOMPATIBLE`** (firma distinta — pasó con una beta02 en el Oppo): NO
desinstalar por tu cuenta. Compilar `:app:assembleProdRelease`, que va firmado con el keystore
del repo, e instalar ese con `-r`; conserva los datos. Si tampoco entra, preguntar al user.

## 4 · Arrancar y mirar

Instalar sin arrancar solo prueba que el paquete entró. Arrancar y leer el log es lo que dice si la
app vive:

```bash
for D in $(adb devices | awk '/\tdevice$/{print $1}'); do
  adb -s $D shell am start -n com.rndeveloper.paparcar/.MainActivity
done
```

Y comprobar que no se ha caído ni ha quedado muda:

```bash
adb -s <serial> logcat -d -t 200 | grep -iE "FATAL|AndroidRuntime|PaparcarApp|DepartureWatch|Coordinator"
adb -s <serial> shell dumpsys activity services com.rndeveloper.paparcar | grep -E "ServiceRecord|isForeground|channel"
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

Decir siempre: **qué commit** se ha compilado (`git log --oneline -1`), que el **sha256 device↔local
coincide en cada móvil** (por separado — no afirmar que los dos móviles llevan el mismo sha, que es
falso en cuanto Gradle vuelva a empaquetar), y **qué se ha visto** al arrancar. Un run sin esas tres
cosas no distingue "va bien" de "estoy mirando el APK de ayer".

Si el run tiene que sostener una comparación ENTRE móviles — un field-test de dos coches donde se
comparan dos trazas — añadir el dex del paso 3-bis. Es la única cifra que aguanta esa afirmación.
