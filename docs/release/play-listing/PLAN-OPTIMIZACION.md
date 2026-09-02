# Plan de optimización de la ficha de Play — informe de análisis

> **Fase: ANÁLISIS. No se ha ejecutado ningún cambio.** `LISTING.md` sigue con el copy
> anterior, y ni el gráfico de funciones ni ningún asset se han tocado. Este fichero es
> nuevo y no altera nada existente.
>
> Fecha: 2026-08-29 · Auditado contra master `b949efa1`.

---

## 1 · Auditoría de la ficha actual

Verifiqué **una por una** las afirmaciones de la ficha contra el código. Resultado:

| Afirmación de la ficha | Veredicto | Evidencia en el código |
|---|---|---|
| Detección automática | ✅ | `CoordinatorDetectionStrategy`, `BluetoothDetectionStrategy` |
| Bluetooth: desconexión = has aparcado | ✅ | `BluetoothDetectionStrategy`, `Vehicle.bluetoothDeviceId` |
| Detección asistida (movimiento + ubicación) | ✅ | `CoordinatorParkingDetector`, `EvaluateParkingDecisionUseCase` |
| Ubicación en segundo plano | ✅ | FGS + geofences + `ParkingSafetyNetWorker` |
| Publicación de la plaza al irte | ✅ | `ConfirmParkingUseCase` → Firestore |
| Ver plazas en el mapa | ✅ | `Spot`, `HomeSpotRows`, marcadores |
| Frescura: cuándo se liberó | ✅ | `SpotTtlPolicy`, `spot_indicator_ttl_minutes` |
| Cuántos van de camino | ✅ | `Spot.enRouteCount`, `spot_indicator_en_route` |
| Si tu coche cabe en la plaza | ✅ | `SpotFit.kt`, `SpotFitRow.kt`, `VehicleSize`/`CarbodyType` |
| Reportar una plaza al pasar | ✅ | `SpotType.MANUAL_REPORT` |
| Garaje con varios coches | ✅ | `Vehicle`, invariante de un solo activo |
| Historial | ✅ | `history_fact_auto_detected`, `history_fact_active_day` |
| Cuántas plazas has cedido | ✅ | `vehicle_stats_spots_shared` («spots shared») |
| Cuánto lleva el coche ahí | ✅ | `home_peek_parking_duration_*`, `home_browse_parked_ago` |
| Zonas privadas | ✅ | `home_zone_private_hint`: «Spot won't be shared when you leave» |
| Preguntar en vez de suponer | ✅ | `home_det_ask_*`, doctrina de fallo asimétrico |
| Eliminar cuenta desde Ajustes y web | ✅ | Ajustes + `paparcar.com/delete-account` |
| Sin anuncios | ✅ | Sin AdMob ni billing en el catálogo de dependencias |

### 🔴 Dos afirmaciones que NO se sostienen

**1. «Apaga la detección del coche que quieras» / «turn detection off for any car» — FALSO.**

No existe un interruptor de detección por vehículo. Lo que existe:
- `settings_auto_detect` («Auto-detect parking»), que es un ajuste **global**;
- el invariante de **un solo vehículo activo** (`VehicleActiveStatePolicy`): la detección
  corre sobre el activo, y activar otro coche desactiva el anterior;
- `vehicleType`: la detección se suprime para `SCOOTER`/`BIKE`, pero eso es una propiedad
  del tipo de vehículo, no un interruptor que el usuario ponga y quite por coche.

`vehicle_status_active_cd` = «Detection active» refleja `isActive`, no un toggle propio.
La propuesta que me pasaste **repite este error** («Activa o desactiva la detección
automática para cada coche»). Lo he eliminado del copy recomendado.

**2. «Arrastra el pin» — impreciso.**

Mover un aparcamiento guardado sí existe (`home_add_parking_header_label_edit` = «Move your
parking», `home_add_parking_confirm_edit` = «Save new location»), pero el gesto real es
**arrastrar el mapa bajo un pin fijo** (`home_add_parking_helper_primary_edit` = «Drag the
map to the right location»). Quien lea «arrastra el pin» buscará un gesto que no está.
En el copy recomendado digo «muévelo en el mapa».

### 🟢 Dos funciones reales de privacidad que la ficha NO estaba aprovechando

Son puntos de confianza fuertes, verificados en `Vehicle.kt`, y no aparecían:

- **La matrícula nunca sale del móvil** (`licensePlate`: *"on-device only — never sent to
  Firestore or shared on Spot"*).
- **Marca y modelo son privados por defecto** y sólo se publican en la plaza si el usuario
  lo activa (`showBrandModelOnSpot = false`, `vehicle_show_on_spot`).

---

## 2 · Nota de la ficha actual

Juicio editorial, no medición — no tengo datos de conversión de esta ficha.

| Dimensión | Nota | Por qué |
|---|---|---|
| Propuesta de valor | 8/10 | El eslogan es claro y el orden promesa→comunidad está bien resuelto |
| ASO | 4/10 | Prosa literaria con pocas keywords reales; el título ES no contiene la búsqueda que hace la gente |
| Conversión | 5/10 | Muros de texto, poco escaneable, sin jerarquía visual en la completa |
| Claridad | 7/10 | Se entiende, pero exige leer frases largas |
| Diferenciación | 8/10 | «Prefiere preguntar antes que suponer» es un ángulo que nadie más usa |
| Confianza | 6/10 | Buena en privacidad, pero **penalizada por las dos afirmaciones falsas** |

El fallo dominante no es el tono: es que **el texto está escrito para leerse, y una ficha
de Play se escanea**. La propuesta que me pasaste acierta de lleno ahí.

---

## 3 · Problemas detectados

1. 🔴 Afirmación falsa: detección on/off por coche (en la ficha actual **y** en tu propuesta).
2. 🔴 «Arrastra el pin»: el gesto real es arrastrar el mapa.
3. 🟠 Título ES `aparcamiento vivo`: «vivo» no es una palabra que nadie busque, y apunta
   justo a lo comunitario que hemos decidido no prometer todavía.
4. 🟠 Título EN `live parking spots`: promete plazas en el campo de más peso, que es
   exactamente lo que la estrategia acordada dice no hacer. **Contradice la propia estrategia.**
5. 🟠 Densidad: párrafos largos donde Play premia bloques cortos.
6. 🟡 Dos activos de confianza sin usar (matrícula y marca/modelo privados).
7. 🟡 «Suave con la batería» en el bloque Bluetooth: es plausible (un broadcast de
   desconexión no hace polling) pero no está medido. Lo he suavizado a «eficiente».

---

## 4 y 13 · Copy ES recomendado

### Título ES
```
Paparcar: dónde aparqué
```

### Descripción breve ES
```
¿Dónde aparcaste? Paparcar lo recuerda por ti, sin que hagas nada.
```

### Descripción completa ES
```
¿Dónde aparcaste?

Paparcar lo recuerda por ti.

Aparca, bájate del coche y sigue con tu día. Paparcar guarda dónde has dejado el coche automáticamente: sin abrir la app, sin poner un pin y sin escribir una dirección. Cuando vuelvas, sabrás dónde está y cuánto tiempo lleva allí.

Y Paparcar hace algo más. Cuando te vas, la plaza que dejas puede publicarse en el mapa para los conductores que están cerca. Y tú puedes ver las plazas que otros acaban de liberar. Cuantos más conductores usen Paparcar en tu ciudad, más plazas podrán aparecer en el mapa.

CÓMO FUNCIONA

1. APARCA
Paparcar detecta que has terminado de aparcar y guarda el sitio. También puedes marcarlo a mano con un toque.

2. TE VAS
La app detecta que vuelves a conducir.

3. TU PLAZA SE LIBERA
Cuando corresponde, tu plaza se publica para los conductores que están cerca.

TU APARCAMIENTO, SIN ESFUERZO
• Dónde dejaste el coche y cuánto tiempo lleva allí.
• Todos tus coches en un mismo garaje, cada uno con su tamaño, su color y su Bluetooth.
• Tu historial de aparcamientos, con cuántos detectó la app y cuántas plazas has cedido.
• ¿El sitio no es exacto? Mueve tu aparcamiento en el mapa y guárdalo de nuevo.

PLAZAS DE APARCAMIENTO EN TIEMPO REAL
• Mira en el mapa las plazas que otros conductores acaban de liberar.
• Cuándo se liberó cada una: no es lo mismo una de hace dos minutos que una de hace veinte.
• Cuántos conductores van ya hacia esa plaza.
• Si tu coche cabe: Paparcar compara el hueco con el tamaño del tuyo.
• ¿Ves una plaza libre al pasar? Avísala en dos toques para el siguiente conductor.

DETECCIÓN AUTOMÁTICA, POR DOS VÍAS

• BLUETOOTH
Si tu coche se conecta al móvil, la desconexión ayuda a detectar que has terminado de aparcar. Es un método preciso y eficiente.

• DETECCIÓN ASISTIDA
Si tu coche no tiene Bluetooth, Paparcar usa el movimiento y la ubicación para detectar cuándo has empezado a conducir y dónde te has detenido.

Paparcar no quiere adivinar. Cuando no está seguro de lo que ha ocurrido, te pregunta antes de publicar una plaza.

PRIVACIDAD
• Zonas privadas: marca lugares como tu casa y Paparcar no publicará allí una plaza cuando te vayas.
• Tu matrícula no sale nunca de tu móvil.
• La marca y el modelo de tu coche son privados: sólo aparecen en la plaza que liberas si tú lo activas.
• Tu ubicación no se vende ni se utiliza para publicidad.
• Puedes eliminar tu cuenta y sus datos desde Ajustes o desde la web.
• Sin anuncios.

UBICACIÓN EN SEGUNDO PLANO

Paparcar utiliza datos de ubicación incluso cuando la aplicación está cerrada o no la estás utilizando, para detectar dónde aparcas y liberar tu plaza cuando te vas.

La detección automática necesita este permiso. Sin él, Paparcar sigue funcionando, pero tendrás que marcar y liberar los aparcamientos manualmente.

Política de privacidad:
https://paparcar.com/privacy-policy
```

---

## 5 y 14 · Copy EN recomendado

### Título EN
```
Paparcar: where did I park
```

### Descripción breve EN
```
Where did you park? Paparcar remembers for you, hands-free.
```

### Descripción completa EN
```
Where did you park?

Paparcar remembers for you.

Park, get out and get on with your day. Paparcar saves where you left the car automatically: without opening the app, dropping a pin or typing an address. When you come back, you know where it is and how long it has been there.

And Paparcar does one more thing. When you drive off, the spot you leave can be published on the map for the drivers nearby. And you can see the spots other drivers have just left. The more drivers using Paparcar in your city, the more spots can appear on the map.

HOW IT WORKS

1. PARK
Paparcar detects that you have finished parking and saves the place. You can also mark it by hand with one tap.

2. DRIVE OFF
The app detects that you are driving again.

3. YOUR SPOT IS FREED
When appropriate, your spot is published for the drivers nearby.

YOUR PARKING, EFFORTLESS
• Where you left the car, and how long it has been sitting there.
• All your cars in one garage, each with its own size, colour and Bluetooth.
• Your parking history, with how many the app detected and how many spots you have shared.
• Not quite the right place? Move your parking on the map and save it again.

PARKING SPOTS IN REAL TIME
• See on the map the spots other drivers have just left.
• When each one was freed: a spot from two minutes ago is not the same as one from twenty.
• How many drivers are already heading for that spot.
• Whether your car fits: Paparcar compares the space against the size of yours.
• See a free spot as you walk past? Report it in two taps for the next driver.

AUTOMATIC DETECTION, TWO WAYS

• BLUETOOTH
If your car connects to your phone, the disconnection helps detect that you have finished parking. Precise and efficient.

• ASSISTED DETECTION
If your car has no Bluetooth, Paparcar uses motion and location to detect when you started driving and where you stopped.

Paparcar does not want to guess. When it is not sure what happened, it asks you before publishing a spot.

PRIVACY
• Private zones: mark places like your home and Paparcar will not publish a spot there when you leave.
• Your license plate never leaves your phone.
• Your car's brand and model are private: they only appear on the spot you free if you turn that on.
• Your location is never sold or used for advertising.
• You can delete your account and its data from Settings or from the web.
• No ads.

LOCATION IN THE BACKGROUND

Paparcar uses location data even when the app is closed or not in use, in order to detect where you park and to free your spot when you drive away.

Automatic detection needs this permission. Without it, Paparcar still works, but you will have to mark and free your parkings manually.

Privacy policy:
https://paparcar.com/privacy-policy
```

---

## 7 · Keywords trabajadas y dónde

Todas colocadas en frases que se leen solas. Ninguna repetida artificialmente.

| Concepto | Dónde aparece | Veredicto |
|---|---|---|
| dónde aparqué / dónde aparcaste | **título ES**, breve, primera línea de la completa | ✅ el que mejor recoge la intención real |
| aparcamiento | título del bloque de plazas, «TU APARCAMIENTO», background | ✅ |
| aparcar / aparcas | paso 1, apertura, varias veces natural | ✅ |
| plaza de aparcamiento | «PLAZAS DE APARCAMIENTO EN TIEMPO REAL» | ✅ como descripción, no como promesa |
| aparcamiento automático | «DETECCIÓN AUTOMÁTICA», «guarda … automáticamente» | ✅ |
| detectar aparcamiento | bloque de detección | ✅ |
| encontrar mi coche / localizar coche | «sabrás dónde está», «dónde dejaste el coche» | 🟠 cubierto por concepto, no literal |
| mapa de aparcamiento | «Mira en el mapa» | 🟠 parcial |
| parking (EN) | título EN, «PARKING SPOTS IN REAL TIME», «YOUR PARKING» | ✅ |
| where did I park (EN) | **título EN**, breve, primera línea | ✅ |
| find my car (EN) | «you know where it is» | 🟠 concepto |
| parking app / parking map (EN) | no forzadas | 🟠 |

## 8 · Keywords que NO recomiendo usar

- **«parking gratis», «aparcamiento gratis», «free parking»** — la app no dice nada sobre
  si la plaza es de pago o gratuita. En inglés «free parking spots» se lee como *gratis*,
  no como *libre*: es una promesa falsa y Play la sanciona.
- **«encontrar aparcamiento» / «find parking» como promesa de resultado** — es la que la
  estrategia acordada descarta hasta que haya masa crítica.
- **«parking automático» en el título** — riesgo real de confusión con el *aparcamiento
  asistido del coche* (self-parking). Es la razón por la que descarto tu propuesta A.
- **«aparcamiento vivo»** — nadie busca eso.
- **«plazas libres cerca de ti»** en título o breve — promesa de disponibilidad.

⚠️ **No tengo datos de volumen de búsqueda ni de dificultad de ninguna keyword.** Todo lo
anterior es análisis de intención y de riesgo, no datos. Para datos reales harían falta
Google Play Console (rendimiento de la ficha) o una herramienta ASO de pago.

---

## 9-12 · Recomendaciones finales de título y breve

**9 · Título ES: `Paparcar: dónde aparqué`**
Es literalmente la consulta que teclea alguien con este problema. Descarto
`parking automático` por la confusión con el self-parking del coche, y `aparcamiento vivo`
porque no es una búsqueda.
*Coste que asumes*: pierde la palabra «aparcamiento» exacta, que sí está varias veces en
la completa.

**10 · Título EN: `Paparcar: where did I park`**
🔴 **El actual (`live parking spots`) contradice la estrategia**: promete plazas en el campo
de mayor peso. Este lo alinea con la promesa real.

**11 · Breve ES: `¿Dónde aparcaste? Paparcar lo recuerda por ti, sin que hagas nada.`**
La pregunta convierte mejor que una afirmación: el usuario se reconoce en el problema. El
remate «sin que hagas nada» no es redundante — la pregunta habla del problema, el remate
del **cómo**, que es el diferenciador.

**12 · Breve EN: `Where did you park? Paparcar remembers for you, hands-free.`**

⚠️ **Un aviso sobre cambiar el nombre de la app**: el título es un elemento de marca, y
cambiarlo reinicia parte del reconocimiento que ya tengas. Como la app aún no está
publicada, ahora es el momento barato de decidirlo.

---

## 6 · Conteo exacto de caracteres

Ver la tabla de verificación que acompaña a este informe (se recalcula con el script del
final). Todos los campos propuestos entran holgadamente en los límites reales de Play:
**nombre 30 · breve 80 · completa 4.000**.

---

## 15-17 · Plan de capturas (NO EJECUTADO)

Pantallas verificadas contra el inventario real de la galería mock
(`StateGalleryScreen.kt`): Home·detección, Home·búsqueda, Home·peek/sheet,
Home·compatibilidad (SpotFit), Detección·confirmación, Historial, Settings, Vehicles,
Permisos, Registro de vehículo, Bluetooth, Onboarding, Mapa·marcadores, Detalle de
aparcamiento histórico.

**Las 6 que propones existen todas.** Una necesita cambio de copy — la 2.

| # | Título propuesto | Pantalla real | Estado / escenario | Veredicto |
|---|---|---|---|---|
| 1 | ¿Dónde aparqué? / Paparcar lo recuerda automáticamente | **Home · mapa con la sesión aparcada + peek** | `ownParkedSession = true`, `sentryAlive = true` | ✅ Es la mejor apertura: enseña el coche guardado y el peek con «Parked 12 min ago» |
| 2 | No necesitas hacer nada | **Home · estado «Watching your …»** | `ownParkedSession`, `activeVehicleBluetooth = true` para la línea BT | 🟠 **Cambiar copy** (abajo) |
| 3 | Tu plaza puede ayudar al siguiente conductor | **Home · detección, línea de conducción** | `promptOpen = false`, runtime «driving» | ✅ |
| 4 | Encuentra plazas que acaban de quedar libres | **Home · mapa con marcadores de plazas + sheet** | por defecto (el fake siembra plazas) | ✅ pero ver nota de copy |
| 5 | No todas las plazas sirven para todos los coches | **Home · compatibilidad (SpotFit)** — grupo propio en la galería | plaza + vehículo con `sizeCategory` | ✅ Existe tal cual |
| 6 | Tú decides qué comparte Paparcar | **Ajustes** + **zona privada en Home** | zonas privadas creadas | ✅ Sugiero Ajustes; la zona privada es más visual pero menos legible en miniatura |

### Cambios de copy que propongo en las capturas

- **Captura 2** — «No necesitas hacer nada» es demasiado absoluto: la app **sí** te pregunta
  cuando duda (`home_det_ask_*`), y ese es un comportamiento que presumimos en la ficha.
  Propuesta: **«Se guarda solo»** / *«Ni abrir la app. Ni poner un pin. Ni escribir una dirección.»*
  El subtítulo tuyo es exacto y se queda.
- **Captura 4** — «Encuentra plazas que acaban de quedar libres» es una promesa de
  resultado, justo la que la estrategia evita. Propuesta:
  **«Las plazas que otros acaban de dejar»** / *«En el mapa, en tiempo real.»*
  Describe sin prometer que las habrá.
- **Captura 3** — sugiero mover el foco al momento honesto: *«Cuando te vas, tu plaza puede
  quedar libre para otro»*.

### Orden recomendado

`1 → 2 → 4 → 3 → 5 → 6`

Razón: las dos primeras cierran la promesa principal (problema + magia). La **4** debe ir
tercera porque es el gancho visual más fuerte del producto y en el carrusel de Play sólo se
ven 2–3 sin deslizar. La 3 (ceder) va después: pide algo al usuario, y pedir va detrás de dar.

### Datos y estado que habría que preparar

- Escenario base: `session = LoggedInWithVehicles`, `permissionTier = All`, `gpsEnabled`,
  `online`, `aggressiveOem = false`, `sentryAlive = true`.
- Vehículo activo con marca/modelo **genéricos** y **sin matrícula real**.
- Plazas sembradas por el fake a distintas edades, para que se vea la rampa de frescura
  (verde/ámbar) y algún `enRouteCount`.
- Al menos una zona privada creada, nombrada «Casa» (no una dirección real).
- Ubicación del emulador fijada en una zona urbana con calles densas (`adb emu geo fix`).

### Qué habría que ocultar

- 🔴 **Los botones flotantes `DEV` y `☀/🌙`** de `DevRoot`. El APK instalado ahora en el
  emulador ya los lleva invisibles (`alpha(0f)`), pero **ese parche no está en el árbol**:
  si se recompila el mock, vuelven a salir.
- La barra de estado del emulador puede quedarse (da realismo) o sustituirse por una limpia.
- Cualquier dato personal: matrícula real, nombre de usuario, foto de perfil real, y el
  nombre de calle si coincide con tu domicilio.

### Aspecto del conjunto

Frames 1080×1920 (9:16), fondo Ink con el glow verde del gráfico de funciones, titular en
Outfit y subtítulo en Inter, captura con esquinas redondeadas y sombra suave. Mismo encuadre
en las 6 para que el carrusel se lea como una serie. Las 6 en tema **oscuro**, coherentes
con el icono y el gráfico.

---

## 18 · Plan para el gráfico de funciones

**Recomendación: cambiarlo, pero sólo el texto.** La composición (glifo + wordmark + retícula
+ marcadores) funciona y no la tocaría.

Motivo: si el título y la breve pasan a la fórmula «¿Dónde aparcaste?», el gráfico debe
rematar la misma idea. Hoy dice *«Knows where you parked, so you don't have to remember»*,
que es correcto pero **repite literalmente la descripción breve**. En Play, gráfico y breve
se ven juntos: repetir desperdicia el único sitio donde puedes decir algo distinto.

Propuesta: que el gráfico diga **lo que la breve no puede decir** — el «cómo»:

```
Aparca y olvídate. Paparcar lo recuerda.
Park and forget it. Paparcar remembers.
```

Sobre legibilidad: el texto actual son 27 px sobre 500 px de alto; en la miniatura del
listado eso es legible, pero cada palabra que quites lo mejora. Las dos versiones de arriba
son más cortas que la actual.

⚠️ **No lo he modificado.** El asset sigue siendo el que aprobaste.

---

## 19 · Auditoría de coherencia legal

Contrastado con `hosting/public/privacy-policy.html` (secciones 1–10, EN y ES) y
`docs/legal/DATA-SAFETY-FORM.md`.

**No he encontrado ninguna contradicción.** Detalle de lo comprobado:

| Punto | Ficha | Política | Data Safety | ¿Coherente? |
|---|---|---|---|---|
| Ubicación en segundo plano | «incluso con la app cerrada o sin usarla» | §2.2 «Location (precise, including background)» | Precise location, incluye background | ✅ |
| Finalidad de la ubicación | detectar dónde aparcas y liberar la plaza | §3 | App functionality + Analytics | ✅ |
| Publicación de plazas | «puede publicarse para conductores cercanos» | §2.6 «Spots published to the community» | funcionalidad iniciada por el usuario → no «compartido» | ✅ |
| Datos del vehículo | matrícula on-device; marca/modelo opt-in | §2.4 | «Other user-generated content», opcional | ✅ |
| Zonas privadas | no se publica plaza allí | §2.5 | — | ✅ |
| Eliminación de cuenta | Ajustes o web | §6 | URL de borrado declarada | ✅ |
| Publicidad / venta de datos | «no se vende ni se usa para publicidad» | «We do not sell your data» | Compartido = No | ✅ |
| Anuncios | «Sin anuncios» | «The app has no ads» | — | ✅ |

🟡 **Único punto de atención, no contradicción**: la ficha (actual y propuesta) **no
menciona la actividad física**, que la política declara en §2.3 y el Data Safety declara
como *Health & fitness → Physical activity* (obligatorio para el carril Coordinator).
No es una incoherencia — la ficha no tiene que enumerar todo lo que recoges, y el bloque
«DETECCIÓN ASISTIDA» ya dice «usa el movimiento». Lo dejo señalado por si prefieres que
diga «movimiento y pasos» de forma explícita, que sería aún más transparente.

---

## 20 · Ficheros que habría que modificar (cuando lo autorices)

1. `docs/release/play-listing/LISTING.md` — sustituir título, breve y completa en ES y EN.
2. `docs/release/play-listing/README.md` — actualizar cifras, guion de capturas y estado.
3. `docs/release/play-listing/assets/feature.html` — **sólo si** apruebas el cambio de texto.
4. `docs/release/play-listing/assets/play-feature-graphic-1024x500.png` — regenerar tras 3.
5. `docs/release/play-listing/assets/screenshots/` — carpeta nueva, aún inexistente.

## 21 · Ficheros y assets que NO se tocan en esta fase

- ❌ `assets/play-feature-graphic-1024x500.png` (y su `feature.html`)
- ❌ `assets/play-icon-512.png` y las dos alternativas
- ❌ Cualquier captura de pantalla — no existe ninguna todavía
- ❌ Código de la app. **En particular `DevRoot.kt`**: el parche de `alpha(0f)` sólo se
  reaplica si hay que recompilar el mock, y siempre como cambio temporal a revertir.
- ❌ `hosting/public/privacy-policy.html` y `docs/legal/DATA-SAFETY-FORM.md` — no hace falta
  tocarlos: no hay contradicción.

## 22 · Orden recomendado de implementación

1. **Decidir los títulos** (es el cambio de mayor impacto y el que condiciona todo lo demás).
2. Aplicar el copy nuevo a `LISTING.md` — reversible, no toca assets.
3. Decidir si el gráfico cambia de texto; si sí, editar `feature.html` y regenerar el PNG.
4. Preparar el escenario mock y **verificar que el parche del chrome de dev sigue puesto**
   en el APK del emulador.
5. Capturar las 6 pantallas en crudo y **revisarlas contigo antes de montar los frames**
   (es el punto donde se detecta un dato personal colado).
6. Montar los frames 1080×1920 y volver a revisarlos.
7. Actualizar `README.md` con el estado final.
8. Sólo entonces: pegar en el Play Console.
