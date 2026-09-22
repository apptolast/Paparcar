# HOME-A-LIVE-STREAM-MUST-NOT-DIE-IN-SILENCE-001 · un stream que muere se queda muerto, y no lo cuenta

**Estado:** ✅ Done · issue **#127** · `testAll` verde · iOS compila · guards con `--rerun-tasks`
verdes · 4 de 5 testigos falsificados · ⏳ **la confirmación es de campo** (ver abajo)

## Problema

> «He notado varias veces que en Home, cuando empiezo a conducir el estado no muestra la ruta ni el
> vehiculo como mi icono en el mapa, **hasta que reinicio la app** y ya si aparece; este
> comportamiento debería de ser retroactivo y cambiar al momento que sufre el cambio toda la UI.»
> — issue #127

Que reiniciar lo arregle es el dato que lo decide: **el estado SÍ está bien en el proceso** —los
singletons de detección son los mismos— y lo que falla es que el ViewModel ya no lo está mirando.
Un ViewModel nuevo vuelve a ver la verdad de siempre.

### Por dónde se muere

`HomeTripController.updates` es un `channelFlow` con dos colectores sueltos dentro:

```kotlin
launch { vehicleRepository.observeVehicles().collect { vehicles = it } }
launch { userParkingRepository.observeActiveSessions().collect { activeSessions = it } }
```

Sin `catch`, sin retry. Si cualquiera de los dos falla, el hijo cae, se lleva el `channelFlow`, y la
excepción sube a:

```kotlin
val tripRender: StateFlow<TripUpdate> = trip.updates.stateIn(viewModelScope, Eagerly, IDLE)
```

que tampoco tiene protección. A partir de ese instante `tripRender` se queda **congelado para
siempre** en su último valor: ni puck, ni traza, ni origen, haga lo que haga la detección. Hasta
recrear el ViewModel — o sea, hasta reiniciar la app.

### Por qué no se ve NADA de esto

Tres silencios encadenados, y cada uno tapa al siguiente:

1. **La excepción que circula es una cancelación.** Un `CancellationException` que no es la
   cancelación del propio job se traga sin ruido: no tumba la app (el handler por defecto las
   ignora) y `PaparcarLogger` **lo excluye de Crashlytics a propósito**. Silencio perfecto.
2. **`collectSafely` termina el flujo.** Su `.catch` registra y **no reintenta**: la KDoc promete
   «keep serving the last good value», y es literalmente cierto — sirve el último valor bueno el
   resto de la vida del ViewModel. Lo que no dice es que no vuelve.
3. **La línea que lo contaría no se persiste.** `PaparcarLogger.e(TAG, "$label stream error", e)` con
   `TAG = "HomeViewModel"`, y `FileAntilog` sólo guarda lo que lleva el prefijo `PARKDIAG`. **Ni una
   sola etiqueta de `presentation/` lo lleva** (comprobadas las 12), así que esa línea sólo existe en
   Logcat, que es debug-only. En campo no hay ningún sitio donde mirar.

### La prueba del disparador

No hay traza directa del #127 — y eso es consecuencia de los tres silencios, no casualidad. Pero el
DISPARADOR está medido en Crashlytics, en el único issue abierto de las dos últimas semanas:

```
PARKDIAG/SafetyNet: ✗ failed to read active sessions
kotlinx.coroutines.JobCancellationException: Job was cancelled
86 eventos · 14 usuarios · 39 sesiones · 1.0.0-beta02 → 1.1.0
```

O sea: **`observeActiveSessions` / la lectura de sesiones activas se cancela de verdad en campo**, y
con frecuencia. Es la MISMA fuente que alimenta el `channelFlow` del viaje, ahí sin ninguna
protección. La red de seguridad lo cuenta porque su etiqueta sí es `PARKDIAG`; el carril del viaje
no lo cuenta porque la suya no lo es.

⚠️ **Lo que NO está probado**: que ese sea el camino exacto del #127. Encaja con todos los
observables (silencioso, permanente, sin crash, sólo el mapa, se arregla reiniciando) y el
disparador está medido, pero no hay una traza que lo ate. Por eso el arreglo incluye **hacer que la
próxima vez se demuestre sola**.

⚠️ **Y es INTERMITENTE**, dicho por el user el mismo día de cerrar esto: *«puede ser que fuera
casualidad, creo que hoy sí se ha cambiado bien la UI»*. Eso no debilita el diagnóstico — lo
refuerza. La issue decía «**varias veces**», no «siempre»: un defecto de diseño permanente fallaría
en todos los viajes, mientras que un stream que sólo muere cuando su fuente resulta lanzar falla a
ratos, que es justo lo que se observa. Un día sin cancelación es un día con el mapa perfecto.

Y por eso el arreglo vale igual aunque aquel viaje concreto fuera otra cosa: el agujero es
estructural y está leído en el código — **cualquier** error en esos streams era permanente e
invisible. Lo que queda por confirmar en campo no es si el agujero existía, sino si era ÉSTE el que
mordió al user.

## Doctrina violada

- **Fallo asimétrico**: el peor resultado posible es el que tenemos — la app sigue pareciendo sana
  mientras la superficie del viaje está muerta. Ni pregunta, ni avisa, ni se recupera.
- **«La única excusa aceptable es el OS, y debe ser detectable a posteriori.»** Esto no es el OS, y
  además no es detectable.
- **Sistemas, no parches**: hay UN punto por el que pasa toda suscripción del ViewModel
  (`collectSafely`), y es ahí donde vive —o falta— la política.

## Diseño

**Un stream que alimenta la UI viva no puede morir en silencio y quedarse muerto.** Tres piezas:

1. **Nace `Flow<T>.resilient(label, onError)`** (commonMain, pura y testeable): ante un fallo
   **reintenta con backoff** en vez de terminar, y deja de reintentar sólo cuando el scope de verdad
   se está cancelando (`currentCoroutineContext().isActive`) — porque cerrar la pantalla NO es un
   error que haya que reintentar.
2. **`collectSafely` pasa por ahí**, así que las ~15 suscripciones del ViewModel se vuelven
   resistentes de una vez. El `notify` (snackbar) sigue disparando en el primer fallo: al usuario se
   le sigue contando lo que ya se le contaba.
3. **`HomeTripController`** protege sus dos colectores sueltos con la misma función, y `tripRender`
   deja de poder tumbar su propia corrutina.
4. **La línea de la muerte se persiste**: el log del fallo pasa a `PARKDIAG/Stream`, que es el
   prefijo que `FileAntilog` guarda — y con él el `parkdiag` del usuario, que desde
   [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] existe también en iOS.

⛔ **No se reintenta infinitamente sin decirlo**: cada reintento deja su línea con el número de
intento. Un stream que reintenta cien veces es un bug distinto, y tiene que poder verse.

## Testigos

`ResilientFlowTest`, cinco. Sustituida la política nueva por la vieja (`.catch` y punto), **caen
cuatro**: el valor de después del fallo no llega; tampoco llega cuando el fallo es una cancelación
ajena —que es el caso REAL medido en campo—; el aviso al usuario no se dispara una sola vez; y
rendirse no termina el flujo. El quinto —una pantalla cerrada no se resuscribe— pasa en los dos
casos, y está porque es la mitad del contrato que un arreglo descuidado rompería.

⚠️ **Un error propio que cazó su testigo**: la primera versión se rendía con `return@retryWhen false`
y nada más. `retryWhen` **relanza** cuando el predicado dice que no — no termina el flujo —, así que
rendirse habría matado la corrutina de la pantalla: exactamente el defecto que este fichero viene a
quitar, seis intentos más tarde. El `catch` terminal no es decoración.

## Criterio de éxito

- Un stream que falla una vez **sigue entregando** los valores siguientes.
- Cerrar la pantalla no genera reintentos.
- El fallo deja línea en `parkdiag.log`.
- En campo: si vuelve a pasar, el `parkdiag` del viaje lo dice con nombre y número de intento.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `HomeViewModel.collectSafely` ×15 | `.catch` = «sigue sirviendo la caché» | ✅ ahora además vuelve |
| `HomeViewModel.collectInto` | delega en `collectSafely` | ✅ hereda |
| `HomeTripController` (2 `launch` sueltos) | nada podía fallar ahí | ✅ protegidos |
| `tripRender` (`stateIn`, sin catch) | el upstream nunca lanza | ✅ el upstream ya no lanza |
| Los `notify` (sesiones activas, GPS) | snackbar al fallar | ✅ intacto, dispara en el primer fallo |
| Otros ViewModels con su propio `.catch` | fuera del alcance de esta issue | 🟡 anotado abajo |

## Follow-ups que NO entran aquí

- 🟡 **`PARKDIAG/SafetyNet: ✗ failed to read active sessions`** — 86 eventos / 14 usuarios. Dos cosas
  separadas: que la lectura se cancele (¿la red de seguridad está dejando de hacer su trabajo para
  14 usuarios?) y que se reporte a Crashlytics siendo una cancelación, que `PaparcarLogger` dice
  excluir. Merece su propio ticket **con los datos delante**.
- 🟡 Las otras 11 etiquetas de `presentation/` siguen sin llegar al `parkdiag`. Aquí sólo se cambia
  la del stream muerto, que es la de esta issue.
