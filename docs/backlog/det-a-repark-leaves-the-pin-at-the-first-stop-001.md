# DET-A-REPARK-LEAVES-THE-PIN-AT-THE-FIRST-STOP-001 · rectificar de plaza deja el pin en la primera parada

**Estado:** 🟡 abierto, sin rama ni código · razonado el 03-09-2026 SIN datos (se perdieron) ·
⛔ **decisión del user: NO entra en 1.0** — el compromiso actual se acepta a propósito

> Este documento no propone tocar nada hoy. Existe porque el bug es **invisible en la traza** (no
> deja ni una línea), llevaba meses tolerado de palabra, y la única vez que se ha querido mirar de
> cerca no había dato que mirar.

## El caso

Paras en A (esperando a que salga un coche, dudando, o porque la plaza no convence), y al poco
rectificas y aparcas en B, unos metros más allá. El pin se queda en **A**. Al irte, la **plaza que
se publica a la comunidad hereda el error**.

Palabras del user (03-09-2026): *«realmente lo dejamos pasar porque la distancia entre uno y otro no
suele ser mucha y preferimos tener este bug a arrastrar el pin andando hacia el interior de una casa,
ya nos ha pasado más veces»*. Es decir: **el compromiso es deliberado**, y cualquier arreglo que lo
toque compite contra los FPs que hoy están cerrados (Camelias, Enamorados, Vivero, Góndola, Galeote).

⛔ **Sin datos de campo.** Ocurrió en el Redmi el 03-09; el `parkdiag.log` local se fue al
desinstalar la app y el gate remoto estaba apagado. Todo lo de abajo es lectura de código, no
telemetría. Los flags remotos de Oppo (`NLIA3mSvAzSObXG2RHhJlSxomki1`) y Redmi
(`K6BehTn8jwUOIr3zyQfp8baoYFM2`) quedaron **encendidos ese mismo día** para que la próxima vez sí
haya traza.

## Mecanismo, leído del código

Tres decisiones encadenadas, todas medidas en campo, ninguna un despiste.

### 1. A se convierte en un descanso probado en ~15 segundos

El congelado del ancla se prueba por TIEMPO **o** por EVIDENCIA (`StopTracking.kt:236-249`):

```kotlin
val restProvenByTime  = (now - evidenceSince) >= config.anchorFreezeStopMs        // 60 s
val restProvenByFixes = s.anchorTrust.stopWindowFixes.size >= config.anchorFreezeStableFixes  // 3
```

`anchorFreezeStableFixes = 3` [DET-SHORT-TRIP-FREEZE-001]: tres fixes parado ≈ 10-15 s a cadencia
HIGH_ACCURACY. Esperar a que salga el otro coche ya congela A.

### 2. Congelado, el trayecto A→B no puede moverlo

Con `anchorPinned`, `effectiveDriving` (`EffectiveDriving.kt:102-113`) solo cede ante tres puertas:

| puerta | exige | un repark de ~40 m |
|---|---|---|
| 1b conducción real | ≥ `minimumTripSpeedMps` **5 m/s** (18 km/h), acc ≤ 50, en **2 fixes seguidos** (`pinnedAnchorRealDriveFixes`) | rara vez llega a 18 km/h, y la maniobra entera son 1-3 fixes |
| 2b salida sostenida | **≥ `sustainedDepartureFloorMeters` = 150 m** desde el ancla | imposible por definición |
| 3 salida sin pasos | **4 fixes** (`frozenAnchorSteplessDepartureFixes`) ≥ 2,5 m/s fuera del sobre, con podómetro **vivo y mudo** | ≈ 20 s de creep continuo; y un solo paso fantasma (MIUI cuenta pasos dentro del coche) lo mata |

### 3. B nunca compite, y nadie registra la duda

- `pinnedToOtherStop` → `mayCapture = false` (`StopTracking.kt:182,197`): la parada nueva no puede
  recapturar el ancla.
- `judgeEgressBirth` (`AnchorPredicates.kt:250`) compara d(A,B) ≈ 40 m contra
  `max(allowance, egressBirthFloorMeters = 150 m)` → devuelve **BORN_AT_ANCHOR**. El sistema **no
  registra ninguna duda**: el suelo de 150 m se puso generoso a propósito (un egreso honesto
  infra-cuenta pasos: Calle Gavia, 68 m andados sobre 8 pasos logueados).
- `refinedParkLocation` (`AnchorPredicates.kt:285`) sí podría mover el pin a B, pero su tope es
  `pasos_al_nacer × anchorStrideMeters + accA + accB` ≈ 10-25 m. 40 m no pasa.

Resultado: confirmación silenciosa en A, sin una sola línea en `parkdiag` que diga por qué.

## ⛔ El hallazgo: este bug ya tenía arreglo, y un ticket posterior lo dejó sin ventana

`PARKING-001` (`repositionSpeedMps = 1.7`, `repositionFixCount = 3`, acc ≤ 15 m) está documentado
literalmente para este escenario — *«el usuario para 10-15 m antes de la plaza real esperando a que
salga otro coche, la ventana inicial congela `bestStopLocation` ahí, y la maniobra breve nunca cruza
`clearBestStopSpeedMps`»*. Pero su condición es (`StopTracking.kt:456`):

```kotlin
val isRepositionBurst = newConsecutive >= config.repositionFixCount && !anchorPinned && …
```

**Solo actúa con el ancla SIN congelar.** Cuando `DET-SHORT-TRIP-FREEZE-001` bajó el congelado a 3
fixes parados, la ventana en la que PARKING-001 podía actuar se quedó en ~15 segundos.

No es un olvido: el comentario de esas líneas explica por qué el ancla congelada tiene que vetar el
burst (una caminata brisk con podómetro mudo tiene **la misma firma** que la maniobra lenta, y así es
como el pin se iba a la puerta de casa en Camelias). Pero el efecto compuesto —*el guard escrito para
este bug ya no llega a este bug*— no está escrito en ningún sitio hasta hoy.

📌 La lección general: **la firma de "reaparcar 40 m" y la de "caminar 40 m" son idénticas para todo
lo que medimos salvo el podómetro**, y el podómetro es justo lo que no se puede creer en este banco
(Redmi mudo, MIUI con pasos fantasma dentro del coche).

## ¿Tiene arreglo?

### Descartados — reabren FPs ya cerrados con traza

- Bajar `sustainedDepartureFloorMeters` (150 m): es exactamente la distancia del paseo coche→casa.
  Reabre Enamorados.
- Bajar `minimumTripSpeedMps` o `pinnedAnchorRealDriveFixes`: reabre Calle del Vivero (un fix suelto
  a 6,45 m/s andando deprisa limpió un ancla correcta).
- Reactivar PARKING-001 con ancla congelada: reabre Camelias tal cual.

### El único con forma aceptable — un segundo explicador, no una puerta nueva

No tocar el ancla ni la sesión. Ampliar **solo** el tope de `refinedParkLocation` con un segundo
explicador junto a los pasos: *un salto medido*. El pin puede ir de A a B si, **y solo si**, todo:

1. hay una segunda parada madurada en B;
2. d(A,B) está entre los sobres de precisión y un techo bajo (~100 m);
3. el tramo A→B pasa `isCorroboratedVehicleHop` con ambos fixes ≤ `repositionMaxAccuracyMeters`
   (15 m) — el swing de recuperación de Camelias falla ese test, está calibrado para eso;
4. **cero pasos contados** entre la última parada de A y la primera de B, con el podómetro **probado
   vivo** (`sensorAlive`);
5. el nacimiento del egreso (`egressBirth`) cae en B.

Se conserva la regla física del sistema — *el pin nunca se mueve más de lo que algo MEDIDO explica* —
y solo se le añade un explicador. Con podómetro mudo la regla **no dispara** y queda el comportamiento
de hoy: falla al lado seguro por construcción.

**Riesgo residual real:** un salto fantasma + una entrada andando degradaría un pin **correcto** hasta
100 m. Hoy los pins correctos son la mayoría, así que el cambio arriesga el caso bueno para arreglar
el caso malo.

## Por qué no entra en 1.0

- **No hay dato**, y el corpus de replays del repo **no tiene ni una traza de repark**: el cambio se
  validaría contra un test sintético, que es justo lo que la doctrina no acepta para tocar
  `effectiveDriving` / `AnchorTrust`.
- El fallo actual está **acotado** (decenas de metros, misma calle) y tiene remedio de usuario
  (arrastrar el pin, que ya sella `user_moved` [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT]). El fallo que se
  arriesga no está acotado igual.
- Toca la función más disputada del sistema, con cinco casos de campo colgando de su ORDEN.

## Lo que sí se puede hacer antes, sin cambiar ninguna decisión

1. **Diagnóstico remoto ON** en Oppo y Redmi — hecho el 03-09 (el Redmi necesita antes reinstalar la
   app y volver a entrar con `collejaygusilu@gmail.com`, o el uid del flag ya no vale).
2. **Una línea de traza, edge-triggered**: cuando una parada nueva se establece mientras el ancla
   sigue clavada a otra a menos de X metros →
   `⚓⤾ segunda parada a N m del ancla clavada — ancla mantenida`.
   Requisitos para que sea inerte, verificados en código:
   - `DiagnosticNote(text, claim = null)` — ⛔ el campo `claim` **sí es entrada de decisión** en
     `CoordinatorParkingDetector.kt:549`; una nota con `claim` no es una nota, es una señal.
   - se computa dentro de la reducción y se **dice** en el caller (patrón ya establecido: la
     reducción puede reintentarse por CAS y no puede tener efectos).
   - **edge-triggered**, p. ej. sobre `stopWindowFixes.size == anchorFreezeStableFixes`: dispara una
     vez por parada, sin estado nuevo. Sin eso la condición es cierta en CADA fix de la parada y
     llena `parkdiag` (6 × 5 MB rotando), que es tanto como borrar el historial que se quiere leer.
   - las notas van a `PaparcarLogger` → logcat + `parkdiag.log`; **no** generan documentos en
     Firestore, así que no cuestan cuota.
3. **Protocolo para reproducirlo a propósito** (con el Redmi ya con app): parar en A, esperar 60-90 s,
   avanzar 30-50 m, aparcar en B, alejarse andando. Repetir con el móvil en el bolsillo y en soporte,
   para medir **si el podómetro calla dentro del coche** — que es el único dato que decide si el
   arreglo de arriba es viable o es una ilusión.

## Qué mirar en la traza cuando vuelva a pasar

- `⚓ anchor FROZEN — drive-entered stop matured (stableFixes=3 …)` en la parada A.
- Ausencia de `⟲ reposition-burst` (lo veta `!anchorPinned`).
- `🔒 anchor FROZEN — ignoring walking-range speed …` durante el tramo A→B: **esa línea es la firma
  del bug**, dice el sistema tomando la maniobra por una caminata.
- Distancia real A→B y velocidad máxima del tramo: son los dos números que faltan para saber si la
  puerta 1b (18 km/h × 2 fixes) está lejos o a un pelo.
