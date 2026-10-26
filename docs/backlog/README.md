# Backlog — índice de lo que sigue abierto

> **808 ficheros: 721 cerrados, 78 abiertos, 9 que no son tickets.** Este índice lista **solo lo que
> queda**, para que la pregunta *"¿qué falta?"* se conteste leyendo y no listando el directorio.
> Verificado contra master `1339ea263` el **2026-10-07**
> [DOCS-A-CLOSED-TICKET-MUST-NOT-SAY-IT-IS-IN-REVIEW-001]. El barrido anterior
> [DOCS-BACKLOG-TRUTH-002] se midió contra `f1ddd2a5` el **15-09** (544 ficheros, 481 cerrados, 54
> abiertos): cada línea abierta se comprobó con `git log master --grep=<ID>` **y, donde el ticket
> describe código, leyendo el código y anotando `fichero:línea` en su doc** — las líneas citadas por
> tickets de agosto habían corrido con `DET-PACKAGE-CLUSTERS-001` y el split `:app`/`:shared`.
>
> ⛔ **Y entre los dos barridos el bucle de abajo no se ejecutó ni una vez en 22 días**: 23 docs
> seguían diciendo *"🔵 En revisión · rama X"* con X borrada y su commit en master, 4 marcaban
> cerrado con 🟢 —dos de ellos **fuera de cualquier etiqueta que el bucle lea**, uno en prosa y otro
> en el TÍTULO— y 4 llevaban el estado en palabras, sin emoji. **Un comando en un README no es una
> herramienta** — así que desde el **07-10** lo ejecuta `BacklogStatusGuardrailTest` dentro de
> `./gradlew testAll`, y estas cifras son parte de lo que comprueba
> [DOCS-THE-BACKLOG-STATUS-LOOP-MUST-BE-A-GUARD-001].
>
> ⚠️ **master se mueve mientras auditas**: durante este barrido entraron **cinco** commits —
> `9afbc5e5` (Alemania por Tankerkönig), `d7880ac7` (fusión del carril BT), `851b00ff` (la ✕ de
> Browse), `3ffb7eae` (tarjeta de aparcado) y `f1ddd2a5` (la pregunta pendiente no es una negativa)—
> y los dos primeros cerraron tickets que esta lista daba por abiertos. Re-leer el tip **al
> publicar**, no solo al empezar.
>
> **Los cerrados no se borran y no son ruido**: cada uno guarda *por qué existe un guard concreto*, y
> es lo que consultan [`../detection/PARKING-DETECTION.md`](../detection/PARKING-DETECTION.md) y la
> skill `det-change` antes de tocar detección. Para buscar uno: `grep -rl <TICKET-ID> docs/backlog/`.

## La regla: **todo doc lleva su línea de estado** — y desde el 07-10 **la ejecuta un guard**

⛔ **Esto ya no se comprueba a mano.** `BacklogStatusGuardrailTest` corre dentro de
`./gradlew testAll` y pone el build en rojo si: (a) un doc que no esté en su allowlist no tiene
línea de estado, o la tiene sin un marcador del vocabulario; (b) cualquier `.md` lleva un **byte de
control** —uno solo y `grep` trata el fichero ENTERO como binario—; (c) **las cifras de la cabecera
de arriba no son las medidas**. Esa tercera es la que tiene dientes: cerrar un ticket obliga a
recontar, y el mensaje de fallo te da la línea exacta que hay que pegar.

⛔ **El chivato de la rama vive FUERA de la suite**, en
[`tools/backlog/branch_tell.py`](../../tools/backlog/branch_tell.py):

```bash
python tools/backlog/branch_tell.py             # 0 = limpio · 1 = hay docs que mienten
python tools/backlog/branch_tell.py --verbose   # qué rama cita cada doc abierto
```

No está en `testAll` por una razón **medida**, no por comodidad: el CI hace
`actions/checkout@v4` con su `fetch-depth` por defecto, que es **1**. Sobre un clon shallow el
chequeo no ve historial de `master`, así que ningún doc parece tener commit de implementación y
saldría **verde para todos** — medido el 07-10 en un `git clone --depth 1`: *«Revisados 7 docs
abiertos … **0 commits de master** … OK»*, un verde indistinguible del real. Por eso el script
**se niega a correr** en esa situación en vez de mentir, y el guard vigila que siga negándose.

⛔ Y la rama que cuenta es la del **bloque de estado**, no cualquiera que el doc mencione: la
primera versión leía las 20 primeras líneas y acusó en falso a `ios-apple-sign-in-on-baselogin-3-001`,
que está 🟡 con razón y nombra su rama en el párrafo donde explica que **se borró**. Hablar de una
rama en prosa es legítimo; presentarla como si el trabajo siguiera ahí, no.

El bucle de abajo se queda como **documentación del criterio** — es lo que el guard implementa:

```bash
# ¿cuántos siguen abiertos? — decide el PRIMER emoji de la línea de estado
cd docs/backlog
for f in *.md; do
  st=$(grep -m1 -E '^[ \t]*>?[ \t]*\*{0,2}(Estado|Status)' "$f")
  # Respaldo: 5 docs abren con **Fecha:** y uno (ios-f0-001) lleva las dos cosas en la MISMA
  # linea, asi que mirar Fecha PRIMERO clasifica mal a los otros cuatro. Estado manda siempre.
  [ -z "$st" ] && st=$(grep -m1 -E '^[ \t]*>?[ \t]*\*{0,2}Fecha' "$f")
  first=$(echo "$st" | grep -oE '✅|❌|🚫|🔵|🟡|🟠|🔴|⚪|🧊|📋|📚|🟢' | head -1)
  case "$first" in ✅|❌|🚫) ;; *) echo "$first $f";; esac
done
```

⚠️ **Corrección de método, en DOS pasos, y el segundo también hizo falta (15-09):**
1. El comando original (`grep -rLE '✅|❌|🚫'` sobre el **fichero entero**) daba **34** abiertos.
   Un ticket abierto que cita un ✅ en su cuerpo (*"la mitad 1 está en master"*) desaparecía:
   se perdían `arch-health-001`, `det-lone-sample…`, `det-coarse-fix…`, `det-a-bt-connect…` y más.
2. Mirar la **línea** de estado tampoco basta: `fuel-station-brand-marks-001` es 🔵 y su línea dice
   *"sale de `fuel-stations-list-001` ✅"* — el ✅ del ticket PADRE lo daba por cerrado. Lo único
   que no miente es **el primer emoji de la línea**, que es lo que hace el bucle de arriba.
   Cifra real hoy: **63 en bruto → 54 tickets abiertos** descontando este índice, los 3 `*-PROMPT`
   y los 5 docs de análisis (de 544 ficheros).
3. Y para que el bucle no mienta, la convención tuvo que aplicarse a tres rezagados: `🟢 ✅ DONE`
   pasó a `✅ DONE` en `det-fence-reregister-by-cause-001`, `det-handoff-not-manual-001` y
   `det-human-powered-early-close-001` — los tres en master desde el 21-08, los tres contados como
   abiertos hasta hoy por empezar la línea en verde.

⚠️ **Marcar cerrado con ✅, nunca con 🟢** (barrido del 03-09).

⚠️ **El chivato que no depende de la redacción sigue siendo la rama**:
`git show-ref --verify --quiet refs/heads/<rama> || echo "rama borrada → el doc miente"`.
El barrido del 15-09 cazó con él **cuatro tickets** que decían *"🔵 En progreso"* / *"🟡 En revisión ·
PR desde rama…"* llevando días en master (`AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001`,
`FIRESTORE-DEBUG-BUILDS-USE-DEBUG-DATABASE-001`, `ONBOARDING-SELLS-THE-NEW-PRODUCT-001`,
`HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001`) y **uno** que ya lo había resuelto otro ticket
sin enterarse (`UI-MAP-LAYER-TOGGLES-LIVE-IN-TWO-CORNERS-001`).
El del **07-10** cazó con el mismo chivato **23 más**, todos con su commit ya en master: los diez de
la tanda de issues de UI/mapa (#30–#53), los cuatro de gasolineras, los cuatro de Home y
`det-a-walk-is-not-an-egress-001`, `det-a-refused-trip-gives-the-car-back-001`,
`det-a-parked-bt-car-needs-no-resident-watcher-001`,
`notif-one-question-deserves-one-notification-001` y `stats-the-detection-shows-its-hit-rate-001`.
Cada uno cita ya su hash en su línea de estado.

---

## 🗺 Lo que queda de los DOS planes en curso

| Plan | Qué queda |
|---|---|
| [**Ola 2 · Plus**](plus-roadmap-001.md) | **F6b** [`parkings-live-occupancy-001`](parkings-live-occupancy-001.md) (ocupación real; ✔ 15-09: **no existe `functions/` y `firebase.json` no declara functions** — sigue siendo el 1.er backend propio, sin empezar) y **F8** `VEH-SHARED-001` (✔ 15-09: cero commits, sin doc de ticket; el modelo se decidió el 08-09). A1–F5 y F7 ✅ |
| [**Pivote trayectos**](trip-a-long-drive-keeps-its-origin-001.md) (8 fases, 14-09) | **fase 6** vía A pin-a-pin del historial BT viejo ⛔ *gated por el viaje del Kamiq* (verificado en código: `EnrichParkingSessionWorker.kt:217-235` sigue excluyendo `Bt`/`BtTimeout`); y la **proyección "viaje"** [`trip-a-journey-is-a-chain-of-trips-001`](trip-a-journey-is-a-chain-of-trips-001.md) (4 decisiones de UX del user). Fases 1-5, 7 y 8 ✅ · la fusión de servicios [`det-one-service-two-lanes-001`](det-one-service-two-lanes-001.md) ✅ `d7880ac7` |

---

## 🔴 Detección — abiertos con evidencia

| Ticket | Qué falla |
|---|---|
| [`det-blind-after-lost-park-001`](det-blind-after-lost-park-001.md) | perder un aparcamiento deja la app **ciega** para el viaje siguiente. ⚠️ auditado 03-09: el honest close NO lo cubre — la vía 1 pide **decisión de producto**. ✔ 15-09: `CoordinatorDetectionService.kt:1333` (`?: return` sin pin) y `EvaluateArEnterArmUseCase.kt:78` (`NoSession`) intactos |
| [`det-lone-sample-is-not-a-drive-001`](det-lone-sample-is-not-a-drive-001.md) | un solo fix de velocidad abre la sesión entera. ⚠️ medido 03-09: las 2 barras candidatas **pierden un viaje real** (`CASA_GAP_ANCHOR`). ✔ 15-09: la regla vive ahora en `state/FixReduction.kt:105` (el doc citaba el detector viejo) y el `provisionalCrossing` solo la **revoca a posteriori** |
| [`det-a-bt-connect-knows-where-the-car-is-001`](det-a-bt-connect-knows-where-the-car-is-001.md) | 🟡 mitad 1 en master. ⚠️⚠️ la mitad 2 es el agujero gordo: la plaza de **A puede publicarse con el coche ya en B** |
| [`det-a-departure-has-no-provenance-001`](det-a-departure-has-no-provenance-001.md) | 📋 14-09: sabemos quién puso cada pin (`detectionPath`) y **no quién detectó cada marcha**. ✔ 15-09: `UserParking` tiene 28 campos y ninguno firma el cierre; los cuatro cerradores escriben `endedAtMs`/`publishedSpot` sin decir quién fue |
| [`spot-a-promotion-inherits-the-age-of-its-departure-001`](spot-a-promotion-inherits-the-age-of-its-departure-001.md) | 📋 14-09: promover una salida deducida reescribe la plaza con **vida completa de 2 h anclada a la promoción**. ✔ 15-09: `ReportSpotWorker.kt:163` estampa `now()` al encolar y `FinalizeDeducedDepartureUseCase.kt:131-148` no pasa el instante. Ya tapado: forma Zone (TTL corto) y pin desmentido por CONNECT BT |
| [`det-bt-pin-grade-is-not-a-driving-threshold-001`](det-bt-pin-grade-is-not-a-driving-threshold-001.md) · [`det-coarse-fix-drive-proof-001`](det-coarse-fix-drive-proof-001.md) | ⚠️ **son el MISMO tornillo**, y tiran en sentidos opuestos: `minGpsAccuracyForDriving = 50f` (`ParkingDetectionConfig.kt:846`), leído por `FixReduction.kt:88` y por `EvaluateBtParkUseCase.kt:293`. Uno pide bajarlo (un fix de 50 m no debe colocar un pin a 0,95), el otro que no ciegue a un móvil de accuracy mala. **No se tocan por separado** |
| [`det-a-repark-leaves-the-pin-at-the-first-stop-001`](det-a-repark-leaves-the-pin-at-the-first-stop-001.md) | re-aparcar deja el pin en A. 🟡 fuera de 1.0 por decisión |
| [`det-a-manual-release-leaves-the-arrival-enter-in-the-bus-001`](det-a-manual-release-leaves-the-arrival-enter-in-the-bus-001.md) | 🔵 una liberación manual deja vivo el ENTER de llegada. ✔ 15-09: `ReleaseActiveParkingSessionUseCase` sigue sin inyectar `DepartureEventBus`, frente a `ConfirmParkingUseCase:386` y `ProcessConfirmedDepartureUseCase:314` que sí lo resetean |
| [`det-bike-departure-release-001`](det-bike-departure-release-001.md) | un paseo en bici sigue declarando que el coche se ha ido. ✔ 15-09: `isHumanPoweredRide` solo veta el GUARDADO (`ConfidenceScoringStage.kt:126`); `VerifyDepartureEvidenceUseCase` no lo consulta y 10 km/h siguen sellando `verified_speed` |
| [`det-bt-boarding-anchor-001`](det-bt-boarding-anchor-001.md) | distinguir *"aparcó a mi lado"* de *"pasó por mi lado"* |
| [`det-bt-veto-must-not-orphan-a-session-001`](det-bt-veto-must-not-orphan-a-session-001.md) | ¿puede cerrarse una sesión cuya vía dueña no corre? ⛔ premisa a medias, **diseño sin decidir** |
| [`det-a-bt-drive-leaves-no-trace-001`](det-a-bt-drive-leaves-no-trace-001.md) | 🟡 **vía B ✅ en master** (el CONNECT enciende el grabador, `213b4596`); queda la **vía A**: etiquetar pin-a-pin el historial BT viejo (= fase 6 del pivote, gated por el Kamiq). ✔ 15-09: `EnrichParkingSessionWorker.kt:217-235`, `Bt`/`BtTimeout` en la rama NO elegible — abrirla es una línea del `when` |
| [`det-a-walk-reporting-zero-is-still-a-walk-001`](det-a-walk-reporting-zero-is-still-a-walk-001.md) | 🧊 **APARCADO, no es tarea**: hoy el caso ya PREGUNTA, que es lo correcto |

## ⏸ Detección — bloqueados por MEDICIÓN, no por código

No esperan a un viaje: esperan a que vuelva a fallar lo que los originó.

| Ticket | Qué hace falta |
|---|---|
| [`det-broadcast-queue-stall-001`](det-broadcast-queue-stall-001.md) · [`det-heartbeat-lane-repair-001`](det-heartbeat-lane-repair-001.md) | que el **Oppo** vuelva a atascar su cola de broadcasts |
| [`det-pedal-cadence-cannot-convict-a-car-in-traffic-001`](det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md) | otra cadencia real de coche lento en ciudad |
| [`det-bt-autonomous-repairing-android-17-001`](det-bt-autonomous-repairing-android-17-001.md) | un móvil con **Android 17** |
| [`det-resume-reconcile-001-2026-07-02`](det-resume-reconcile-001-2026-07-02.md) | ⚠️ **premisa CADUCADA** — releer antes de implementar nada |

## 🐞 Bugs de UI / app — abiertos y verificados en código el 15-09

| Ticket | Qué falla (y dónde se comprobó) |
|---|---|
| [`map-the-handler-map-must-be-rebuilt-with-the-marker-list-001`](map-the-handler-map-must-be-rebuilt-with-the-marker-list-001.md) | al cambiar la vigilancia del viaje, **salida y llegada pierden su dibujo**: la lista de marcadores keya por `watchKey` y el mapa de handlers no. ✔ sigue vivo: `PaparcarMapView.kt:1202` — `watchKey` NO está entre las claves del `remember`, y `departureContentId(watchKey)`/`arrivalContentId(watchKey)` sí lo llevan |
| [`fuel-the-map-capsule-must-wear-the-follow-001`](fuel-the-map-capsule-must-wear-the-follow-001.md) | la **cápsula del mapa** no dice si sigues esa gasolinera: [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001] le puso la estrella a la fila y al peek, y la tercera superficie —la más vista— quedó fuera. Pide device: `isFollowed` tiene que entrar en la clave de caché del marcador, como el `watchKey` de la fila de arriba |
| [`ui-a-captured-name-needs-its-ceiling-001`](ui-a-captured-name-needs-its-ceiling-001.md) | el nombre de zona no tiene techo y viaja entero a Firestore. ✔ sigue vivo: `AddingZonePeek.kt:276-280`, `OutlinedTextField` con `onValueChange = onNameChange` sin `take(n)` (`singleLine` no es un límite) |
| [`dev-an-edit-variant-must-edit-a-car-001`](dev-an-edit-variant-must-edit-a-car-001.md) | las variantes «Edición» de la galería pintan una **moto**. ✔ sigue vivo: `StateGalleryScreen.kt:2277-2285`, el state se construye sin `vehicleType` |
| [`bug-home-fab-padding-2026-06-05`](bug-home-fab-padding-2026-06-05.md) | padding negativo en `HomeMapFabsLayer` (Nothing Phone A001). 1 evento, sin reproducir: su propio doc dice priorizar **solo si reaparece** |
| [`vehicles-multimarker-2026-05-19`](vehicles-multimarker-2026-05-19.md) | 🟡 sprint PARCIAL — los marcadores existen; el resto quedó diferido |
| [`fuel-a-peek-does-not-mark-your-car-001`](fuel-a-peek-does-not-mark-your-car-001.md) | 📋 el peek de gasolinera lista todos los productos sin distinguir cuál compra el coche activo — para un híbrido, «Gasoline» y «Diesel» se leen igual de relevantes. Pide decisión de producto |

## 🧪 Salud de build / KMP

| Ticket | Qué falla |
|---|---|
| [`db-a-room-expect-object-breaks-the-metadata-compilation-001`](db-a-room-expect-object-breaks-the-metadata-compilation-001.md) | `:shared:compileCommonMainKotlinMetadata` no compila por el `expect object AppDatabaseConstructor`. ✔ sigue vivo: `AppDatabase.kt:58-59` sin cambios. **No rompe APK, framework ni tests** — rompe el análisis de `commonMain` en el IDE |
| [`map-a-marker-bitmap-is-measured-before-it-exists-001`](map-a-marker-bitmap-is-measured-before-it-exists-001.md) | ✅ arreglado en el fork de `kmp-maps` (`0.9.1-puck5`, ya en Maven Central) |
| [`deps-revenuecat-3-10-and-a-full-sweep-001`](deps-revenuecat-3-10-and-a-full-sweep-001.md) | ✅ Play avisó del bug de logout de `purchases-kmp-core` 3.8.0/3.9.0 → subido a 3.14.0 + barrido de 18 claves del catálogo (AGP, Kotlin, Compose Multiplatform, `firebase-bom` 34→35…) |

## 🧱 Arquitectura y limpieza

| Ticket | Estado |
|---|---|
| [`arch-health-001`](arch-health-001.md) | 🔵 plan por fases · **F7 (split `:app`+`:shared`) ✅ ejecutada el 29-08** |
| [`det-verdict-not-predicate-001`](det-verdict-not-predicate-001.md) | 🟡 **re-medido 15-09: la consolidación ya pasó** por `DET-PACKAGE-CLUSTERS-001` — el detector cayó de 2.622 a **1.466 líneas** y de 11 predicados privados a **4**; `domain/detection/` tiene 95 ficheros de funciones puras. Lo que queda es auditar `CoordinatorDetectionService` (**2.713 líneas, 44 privadas**, I/O del intake) por si esconde algún veredicto. ⚠️ la rama que citaba ya no existe |
| `PIPE-004` (arriba) | ✔ 15-09: `EnrichParkingSessionWorker.kt` y `UpdateParkingSessionAddressAndPlaceWorker.kt` siguen siendo dos |
| [`infra-datastore-migration-001`](infra-datastore-migration-001.md) (arriba) | ✔ 15-09: **1 fichero** usa DataStore frente a **13** con `getSharedPreferences` — y esa asimetría es deliberada (prefs de usuario → DataStore · detección → SharedPrefs) |
| [`DET-AUDIT-REMEDIATION-001`](DET-AUDIT-REMEDIATION-001.md) · [`audit-a12-001`](audit-a12-001.md) | 📋 planes de auditoría · ⚠️ **las ramas que citan ya no existen**; su contenido sigue vigente, sus punteros no |
| [`infra-datastore-migration-001`](infra-datastore-migration-001.md) | 📋 propuesto 10-08. No urgente |
| [`ui-the-light-theme-is-lighter-without-losing-its-edges-001`](ui-the-light-theme-is-lighter-without-losing-its-edges-001.md) | 🔵 **a verificar**: su rama ya no existe, pero el único commit que lo cita es un `docs(` — el barrido del 07-10 lo dejó abierto a propósito en vez de adivinar que aterrizó |
| `PIPE-004` | ⏸ diferido — colapsar `EnrichParkingSessionWorker` + `UpdateParkingSessionAddressAndPlaceWorker`. ⚠️ **no tiene doc propio**: solo se le nombra en [`../refactors/PIPE-001-confirm-parking-pipeline.md`](../refactors/PIPE-001-confirm-parking-pipeline.md) |

## 📐 Producto — piden decisión, no un fix

| Ticket | Nota |
|---|---|
| [`trip-a-journey-is-a-chain-of-trips-001`](trip-a-journey-is-a-chain-of-trips-001.md) | 🔵 la proyección "viaje" del pivote: **4 decisiones de UX del user** antes de código |
| [`plus-alerts-and-tiers-001`](plus-alerts-and-tiers-001.md) | 📋 doc de PRODUCTO: *free pregunta / Plus vigila y recuerda*. ⛔ VER parkings y gasolineras NO se gatea |
| [`fuel-a-recorded-series-must-be-readable-001`](fuel-a-recorded-series-must-be-readable-001.md) | 🔵 se graban **30 días** de precios por gasolinera seguida y no se leen en ninguna pantalla (`observeHistoryFor`, cero consumidores en UI). Pide decidir dónde vive y qué dibuja con una serie que tiene huecos |
| [`ui-where-does-the-per-zone-alert-list-live-001`](ui-where-does-the-per-zone-alert-list-live-001.md) | 🔵 dónde vive la lista de alertas por zona |
| [`zone-subscribe-001`](zone-subscribe-001.md) | 🟡 entregado por la opción (a) vía MONEY-001 — queda ABIERTA solo la decisión (b), el TTL |
| [`snap-to-park-001`](snap-to-park-001.md) | 🔵 sacar el ancla de dentro de edificios. Sin código y sin rama |
| [`map-a-remembered-pin-looks-like-a-live-one-001`](map-a-remembered-pin-looks-like-a-live-one-001.md) | 📋 secuela de la cache del mapa. Prioridad baja |
| [`spots-follow-the-camera-001`](spots-follow-the-camera-001.md) | 🔵 las plazas siguen a la cámara como ya hacen gasolineras y parkings. ✔ 15-09: `ObserveNearbySpotsUseCase(location, radiusMeters)` sigue pidiendo un **radio fijo** (2 km por defecto) alrededor de un punto, no los límites de la cámara |
| [`onb-widget-is-the-last-step-001`](onb-widget-is-the-last-step-001.md) · [`widget-map-snapshot-003`](widget-map-snapshot-003.md) | 📋 ideas del user al cerrar `WIDGET-ACTIONS-002` |
| [`fuel-station-brand-marks-001`](fuel-station-brand-marks-001.md) | 🔵 cola de F5: marcas de enseña en el marcador (decidido, sin empezar; ✔ 15-09 sin código). ⚠️ `fuel-germany-001` **ya NO está abierto**: lo entregó [`fuel-germany-the-feed-answers-whole-001`](fuel-germany-the-feed-answers-whole-001.md) (`9afbc5e5`). Siguiente del orden medido: **Italia** |
| [`parking-availability-vendor-001`](parking-availability-vendor-001.md) | 🧊 diferido sin fecha — datos de ocupación de pago |
| [`auth-email-verification-as-an-opt-in-setting-001`](auth-email-verification-as-an-opt-in-setting-001.md) | 🟡 idea del user (03-09), post-1.0 |
| [`ui-en-route-blue-sits-close-to-bt-blue-001`](ui-en-route-blue-sits-close-to-bt-blue-001.md) | 🧊 diferido por el user (08-09) |
| [`ux-park-flow-001`](ux-park-flow-001.md) | placeholder POR DEFINIR · análisis en [`ux-park-flow-001-analysis`](ux-park-flow-001-analysis.md) y [`home-flow-analysis`](home-flow-analysis.md) |
| [`moving-car-native-marker-2026-07-01`](moving-car-native-marker-2026-07-01.md) | spike; el fork de kmp-maps que salió de aquí **ya está en Maven Central** |
| *(sin ticket)* | **estilo de mapa**: el default sigue siendo `MapType.TERRAIN` y el JSON de marca solo rinde sobre `NORMAL`. Documentado en [`home-flow-analysis` §H2](home-flow-analysis.md) |

## 🏪 Ficha de Play — abierto

| Ticket | Nota |
|---|---|
| [`play-the-store-shots-still-live-in-dublin-001`](play-the-store-shots-still-live-in-dublin-001.md) | 🔵 las **72 capturas** (9 × 8) enseñan calles de **Dublín** y el titular con la marca en **mayúscula**. ⛔ **No vale re-renderizar**: `render.sh` pide raws POR IDIOMA y los del repo son planos y en inglés, así que un re-render arregla el titular y deja Dublín dentro. Necesita **sesión de emulador**, no una edición. Los 9 gráficos de funciones SÍ están listos |

## 🍎 iOS — en marcha

⛔ La nota *«🧊 APARCADO por el user (07-09): no proponer trabajo iOS»* que encabezaba esta sección
queda **RETIRADA**: el user desaparcó iOS el **22-09** (lote de 9 issues), y desde entonces iOS tiene
lane de release, Fastlane, un build en TestFlight y sus propias superficies de marca.

| Ticket | Estado |
|---|---|
| `IOS-F0-001` | ✅ **Fase 0 EN MASTER** (`8c2daf096`). ⛔ Esta tabla decía *«en la rama `feature/IOS-F0-001-fase0` (tip `6627e649`), sin mergear»* y esa rama ya no existe — pero su tip **sí es alcanzable**, por el tag local `prerebase/feature/IOS-F0-001-fase0` |
| [`ios-f2-a-wake-must-query-the-past-001`](ios-f2-a-wake-must-query-the-past-001.md) | ✅ **F2 en master** — `IosWakeMonitors.kt`, `IosSafetyNetRecords.kt` y `IosDetectionController.kt` están ahí y han seguido creciendo. ⛔ Decía *«en esa misma rama, no en master (`1e0a70f2`, `610128ab`)»*: no es trabajo pendiente, y los dos hashes viven bajo el mismo tag `prerebase/` |

⛔⛔ **Los tags `prerebase/` son la red de seguridad, y son LOCALES.** `git tag -l 'prerebase/*'`
devuelve tres (`…IOS-F0-001-fase0` → `6627e649f`, `…IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001` →
`b227a9c4f`, `…DET-A-CAR-CONNECTION-ARMS…-aa-signal` → `0a4e3ccdf`) y **ninguno está en `origin`**
(`git ls-remote --tags origin 'refs/tags/prerebase/*'` vuelve vacío). Es decir: el tip de una rama
borrada sigue a un `git checkout <tag>` de distancia **en esta máquina y solo aquí**. Antes de decir
que un hash «quedó colgando», `git tag --contains <hash>`.
| [`ios-death-a-kill-that-signs-its-name-001`](ios-death-a-kill-that-signs-its-name-001.md) · [`ios-devcat-a-gallery-the-simulator-can-open-001`](ios-devcat-a-gallery-the-simulator-can-open-001.md) · [`ios-diag-a-log-that-survives-to-be-shipped-001`](ios-diag-a-log-that-survives-to-be-shipped-001.md) · [`ios-route-a-snap-the-drainer-never-does-001`](ios-route-a-snap-the-drainer-never-does-001.md) | ⚪ pendientes, sin rama |
| [`ios-social-login-001`](ios-social-login-001.md) | 🔵 bloqueado hasta tener un Mac |

---

## 📚 Docs que NO son tickets

No tienen estado global porque no lo tienen que tener: son análisis de campo con su estado POR BUG
dentro, o histórico. Se listan para que nadie los cuente como trabajo pendiente.

[`detection-departure-bugs-2026-06-05`](detection-departure-bugs-2026-06-05.md) ·
[`parking-detection-real-world-2026-05-28`](parking-detection-real-world-2026-05-28.md) ·
[`detection-improvements-2026-05-27`](detection-improvements-2026-05-27.md) ·
[`home-flow-analysis`](home-flow-analysis.md) ·
[`ux-park-flow-001-analysis`](ux-park-flow-001-analysis.md) ·
los tres `*-PROMPT.md` (encargos, no tickets).

## ✅ Cerrados que arrastran un ⏳ residual

No son trabajo pendiente: son confirmaciones que faltan. Se listan para que nadie los reabra por error.

| Ticket | Qué falta |
|---|---|
| [`det-bt-pin-is-sampled-after-the-walk-001`](det-bt-pin-is-sampled-after-the-walk-001.md) · [`det-bt-a-park-is-confirmed-not-assumed-001`](det-bt-a-park-is-confirmed-not-assumed-001.md) · [`trip-the-tracker-rides-the-sentinel-001`](trip-the-tracker-rides-the-sentinel-001.md) · [`det-one-service-two-lanes-001`](det-one-service-two-lanes-001.md) | el **viaje del Kamiq**: valida de una tacada las fases 3 y 4 del pivote, el hosting único del carril BT (`d7880ac7`) y los tickets BT viejos |
| [`fuel-germany-the-feed-answers-whole-001`](fuel-germany-the-feed-answers-whole-001.md) | ⏳ verlo en mano con GPS en Alemania (`9afbc5e5`) |
| [`batt-a-cost-is-measured-not-declared-001`](batt-a-cost-is-measured-not-declared-001.md) | primera pasada del **rig A/B** de batería + la línea de Ajustes cuando haya corpus limpio |
| [`det-reliability-001`](det-reliability-001.md) | F1–F3 en master (`7350f358`); **F4 sigue diferida** a propósito |
| [`det-memory-limiter-is-an-attributable-kill-001`](det-memory-limiter-is-an-attributable-kill-001.md) | implementado (`e4fcac6c`); falta **cosechar** un force-stop y un `am memory-limiter` en Android 17 |
| [`det-fgs-reaper-001`](det-fgs-reaper-001.md) · [`det-fence-reregister-by-cause-001`](det-fence-reregister-by-cause-001.md) · [`det-handoff-not-manual-001`](det-handoff-not-manual-001.md) | código en master; lo que queda es device/field-test o instrumentación (§B.5) |
| [`det-parkdiag-keep-more-history-001`](det-parkdiag-keep-more-history-001.md) | instalado solo en el Redmi — el Oppo se desenchufó a mitad |
| [`geo-cache-answers-nearby-001`](geo-cache-answers-nearby-001.md) · [`data-room-starts-at-version-one-001`](data-room-starts-at-version-one-001.md) | ⏳ confirmación en device |
| [`sync-reconcile-userparking-deferred-2026-07-03`](sync-reconcile-userparking-deferred-2026-07-03.md) | el reconcile cerrado; **Profile** sigue diferido |
| [`settings-remodel-001-followups-2026-07-03`](settings-remodel-001-followups-2026-07-03.md) | validación en plataforma, sin código |
| [`ui-a-page-keeps-one-vertical-rhythm-001`](ui-a-page-keeps-one-vertical-rhythm-001.md) | medido en A/B sobre emulador; ⏳ verlo en un **móvil real**, y una medición suelta de Home (15,2 dp con el binario nuevo) que no se reprodujo — ver «Cabo suelto» en el doc |
| [`det-no-device-mute-in-remote-001`](det-no-device-mute-in-remote-001.md) · [`det-retract-denied-forever-001`](det-retract-denied-forever-001.md) | hechos; el doc existe para que la regla tenga dónde vivir |
| [`mkt-add-facebook-attribution-route-001`](mkt-add-facebook-attribution-route-001.md) | segunda fuente de atribución (`/descargar/fb`), análoga a las seis de Instagram; guard generalizado a `UTM_SOURCE_OF_PATH`. ⏳ desplegar y verlo UNA vez en device |

## 🚧 Ojo: tickets cuyo doc **no está aquí**

Las ramas sin mergear llevan su `docs/backlog/<id>.md` **dentro de la rama**, así que este índice no
las ve. Están listadas en [`../ROADMAP.md § En vuelo`](../ROADMAP.md). Es el agujero que motivó
`IOS-SOCIAL-LOGIN-001`: *una spec que solo vive en una rama es una spec que el backlog no puede leer*.
