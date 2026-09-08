# Paparcar — Roadmap post-Plus (después de MONEY-001)

> **Doc vivo.** Verificado contra master `466a10c9` el **2026-09-07**.
> [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]
>
> 📌 **La ejecución inmediata ya no vive aquí**: el 07-09 el user fijó la **ola 2 de Plus**
> ([`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md) — límites free, combustible,
> estadísticas, gasolineras, parkings, tiempo de búsqueda, coche compartido), que **absorbe las
> features 1a, 2 (+Málaga de la 6) y 7 de este doc** y se ejecuta ANTES que el resto. Este doc
> queda como horizonte: qué viene después de la ola y contra qué choca cada cosa (§3 sigue siendo
> lectura obligada al definir cualquiera).
>
> ⛔ **Nada de lo que queda aquí está definido todavía.** Este doc fija QUÉ hay, en QUÉ orden y
> CONTRA QUÉ choca. El *cómo* de cada feature se define paso a paso con el user antes de escribir
> código — cada una abrirá su ticket con la skill `nuevo-ticket` cuando le toque. Un renglón de
> este doc **no** es permiso para empezar.
>
> Fuente: sesión de producto del 5-6 sep 2026 (fichero `paparcar-roadmap-features.md`), fusionada
> aquí con lo que ya vivía en [`ROADMAP.md § 🔮 Futuro`](./ROADMAP.md) y en el backlog.
> Por qué existe este doc y qué se auditó al escribirlo:
> [`backlog/docs-roadmap-post-plus-001.md`](./backlog/docs-roadmap-post-plus-001.md).

---

## 1. ¿Había ya un plan post-Plus? Sí, pero repartido en tres sitios

| Dónde vivía | Qué decía |
|---|---|
| [`ROADMAP.md § 🔮 Futuro`](./ROADMAP.md) | Widget Android · Android Auto · WearOS/Watch · detección por pairing CarPlay/AA · modo "voy de camino" con ETA · 4º tab Comunidad · modularización · detección server-side/ML con eventos (privacy-first) |
| [`backlog/zone-subscribe-001.md`](./backlog/zone-subscribe-001.md) | entrega **en background** de las alertas de zona (Cloud Function) — la parte que MONEY-001 F4 dejó fuera a propósito: hoy solo se entrega con la app viva |
| `backlog/money-001.md` | fuera de alcance de Plus: promo Fundadores, **AdMob**, paywall con identidad propia |

Ese plan era una **lista de ideas de producto sin orden ni coste**. El fichero nuevo es lo contrario:
trae orden, coste, riesgo y datasets reales. **No se pisan casi en nada** — solo en dos puntos:

- *"Detección server-side / ML con eventos confirmados y denegados"* del ROADMAP viejo es el
  antepasado directo de las features **1 + 3 + 8** del fichero nuevo (instrumentar → agregar →
  estimar). El fichero nuevo lo desarrolla mejor y lo sustituye.
- El *modo "voy de camino"* del ROADMAP viejo resulta ser **prerrequisito escondido** de la
  feature 1 (ver §3.2).

Y hay cuatro cosas del ROADMAP viejo que el fichero nuevo **no menciona y siguen vivas**: Widget,
Android Auto/CarPlay pairing, WearOS y el 4º tab. La primera se cuela alto en la prioridad fusionada
(§4); las otras tres siguen sin fecha.

---

## 2. Lo que aporta el fichero nuevo (resumen)

Ocho features, con la escala del propio fichero (facilidad = días para un dev solo):

| # | Feature | Versión propuesta | Free/Plus | Facilidad | Riesgo |
|---|---|---|---|---|---|
| 1 | Instrumentación de eventos (Firebase Analytics) | v11-v12 | transversal | muy alta (½ día) | casi nulo |
| 2 | Pines de parkings públicos con ocupación real (Madrid) | stretch v11 / v12 | free | alta (1-2 d) | bajo (UX) |
| 3 | Agregación anónima por celda | v12 | infra | alta (1-2 d) | bajo ahora, alto después |
| 4 | **Alertas SER (Madrid)** | v12-v13 | **Plus** | media (3-5 d) | medio-alto (reputacional) |
| 5 | Capa de oferta SER (calles + nº plazas) | v12-v13 | free | media (2-3 d) | bajo si se etiqueta bien |
| 6 | Adaptadores de otras ciudades | con usuarios fuera de Madrid | free | muy alta (½ d/ciudad) | bajo |
| 7 | **Coche compartido (solo Bluetooth)** | post-octubre | **Plus** | baja (2-3 sem) | medio |
| 8 | Mapa de probabilidad por zonas | 2027 | por decidir | media-baja | alto sin datos |

Enum transversal propuesto: `CONFIRMED` (sensor u oficial en tiempo real) · `COMMUNITY` (liberación
de un usuario) · `ESTIMATED` (estadística propia) · `STATIC` (oferta, no disponibilidad).

El detalle de cada una está en §5, íntegro.

---

## 3. Contra qué choca (lo que el fichero no podía saber)

Siete choques concretos con el código y las reglas de hoy. Ninguno invalida nada; todos cambian el
coste o el orden.

### 3.1 ⛔ El enum de confianza colisiona con dos nombres que ya existen

`Spot` ya tiene **`confidence: Float` (0..1)** — la fiabilidad con la que se detectó la salida, la que
tiñe el anillo del marcador — y **`SpotStatus.CONFIRMED`**, que significa *"salida presenciada"*, no
*"sensor en tiempo real"*. Meter un tercer sentido de "confianza" con un valor llamado `CONFIRMED`
haría que la palabra signifique tres cosas distintas en el mismo mapa.

**Al definirlo:** eso no es *confianza*, es **procedencia del dato** (`AvailabilitySource` /
`SpotSource`, y `OFFICIAL` en vez de `CONFIRMED`). El mismo problema que ya resolvimos con
`detectionPath`: la procedencia es un **tipo**, no un adjetivo [DET-DETECTION-PATH-IS-A-TYPE-001].

### 3.2 ⚠️ Tres de los seis eventos de la feature 1 **no tienen hecho que los produzca**

`parking_session_started/ended` y `spot_released` ya ocurren y se pueden nombrar mañana.
`search_started`, `search_ended` y `spot_found` **no existen como hecho en la app**: no hay flujo de
"voy a por esta plaza". `enRouteCount` está en `Spot`, en la entidad Room, en el DTO y se pinta en el
sheet — pero **nadie lo incrementa** (verificado en master: solo lecturas).

Consecuencia: la feature 1 no es media tarde entera. Es **media tarde la mitad emisora**, y la otra
mitad es el *modo "voy de camino"* del ROADMAP viejo. Y como **la métrica del piloto es "% de
búsquedas que terminan en plaza encontrada"** (§6), sin ese flujo el piloto no se puede medir.

### 3.3 ⚠️ La feature 1 sería el **tercer** sistema de telemetría

Ya hay dos: la telemetría de detección a Firestore (`diagnostics/{uid}/sessions`, con gate de
consentimiento) y el `parkdiag.log` local que el usuario envía desde Ajustes
[SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]. Los eventos de detección deben salir del **mismo punto**
donde ya se estampan `detectionPath` / `outcome` / `sessionOutcome`, no de un camino nuevo en
paralelo — o tendremos dos verdades sobre el mismo viaje.

Además: **Firebase Analytics no está en el catálogo** (solo Crashlytics). Entra como dependencia
nueva + `expect/actual`, mismo patrón que `CrashReporter`. Y la celda geográfica en un evento **es
ubicación**: fila nueva en [`legal/DATA-SAFETY-FORM.md`](./legal/DATA-SAFETY-FORM.md) (destino
BigQuery + retención) **en la misma tarea**, no después.

### 3.4 ⚠️ La feature 2 monta el **primer backend propio** del proyecto

`firebase.json` declara hoy `firestore` + `hosting` y nada más: **cero Cloud Functions**. La 2a las
estrena (runtime, deploy, CI, secretos). Es coste real que la ficha de la feature no cuenta —
pero se **amortiza**: lo reutilizan la 3b, la 6, el webhook de la 7g y la entrega en background de
`ZONE-SUBSCRIBE-001`. Ese es un argumento para hacer la 2 pronto, no para no hacerla.

⚠️ El MCP `firebase_deploy` es un **no-op silencioso**: desplegar siempre por CLI.

### 3.5 ⛔ Los pines nuevos chocan con la doctrina de color y con el estilo de mapa sin decidir

La rampa 🟢🟡🔴🔵 es **exclusiva de la caducidad de plazas** [UI-COLOR-DOCTRINE-001]. Un parking
público no puede pedirla prestada. Y un marcador es **Nivel 3**: vector propio multicolor, **sin
tintar**. Encima, el mapa arranca en `MapType.TERRAIN` y el JSON de marca solo rinde sobre `NORMAL`
(`MAP-TYPES-001` sigue bloqueado): vamos a apilar dos capas nuevas sobre un estilo que aún no está
decidido. La 2c y la 5b **no se diseñan sin cerrar antes eso**.

### 3.6 ⚠️ La feature 4 arrastra tres decisiones de stack que su ficha no nombra

- **SQLDelight sería una segunda librería de persistencia** junto a Room KMP. Hay alternativas
  (tabla Room de solo lectura, o un binario propio con índice espacial). Es una decisión de stack, no
  un detalle heredado del proyecto de referencia.
- **Remote Config** (4c) tampoco está en el stack hoy. Dependencia nueva.
- **Las notificaciones de vencimiento** (4e) quieren precisión de minuto: en Android 14+ eso es
  `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, con su declaración en Play. WorkManager no garantiza el
  minuto.

Y el `.db` derivado **no se versiona**: el repo es público y la regla es versionar la FUENTE (script
del pipeline + hash del dataset) y regenerar lo derivado.

Lo que sí encaja perfecto: *"preguntar, nunca afirmar"* es exactamente la doctrina de detección
(*ante la duda se PREGUNTA*), y por eso el diseño de la 4d ya nace correcto.

### 3.7 ⛔ La feature 7 es la más cara del fichero y llega en el peor momento del modelo de datos

- **7a** (los aparcamientos cuelgan del coche, no del usuario) toca Room + Firestore + el outbox
  durable + el reconcile LWW… justo después de estabilizar el sync, y con iOS recién puesto al día.
  Todo cambio de esquema exige **bump + `Migration`** [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001].
  Contrapartida: la base remota **está vacía** desde el wipe del 03-09, así que migrar los datos
  remotos es barato **hoy** y caro dentro de seis meses. Eso pide **decidir 7a pronto aunque se
  implemente tarde**.
- **7d** (atribución solo-BT) es **cambio de detección**: congelada hasta octubre y con la pila de
  ramas de detección aún sin plegar. Skill `det-change` obligatoria.
- **7g** (webhook RC → `plusActive` en Firestore) **aparenta** violar la regla dura de MONEY-001
  (*RevenueCat es la ÚNICA fuente de verdad*). El fichero ya lo ve y pide un ADR: hay que escribirlo
  de verdad, dejando claro que es una **proyección de solo lectura para las reglas de seguridad**,
  nunca una réplica de la lógica de expiración.
- **7e** (coche sin Bluetooth) se resuelve a medias con la detección por **pairing CarPlay / Android
  Auto** del ROADMAP viejo: misma señal de fuerza, para quien no empareja el móvil pero sí conecta el
  multimedia. Si la 7 se hace, esa idea sube de prioridad.

---

## 4. Prioridad fusionada (revisada el 07-09: la ola 2 ocupa el frente)

El 06-09 esta tabla ordenaba P0-P10. El 07-09 el user trajo la **ola 2 de Plus**
([`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md)) y decidió: la ola manda, y absorbe
de esta tabla el P1 (1a → fase A1), el P2 (Widget → fase A2), el P3 (parkings → F6, con Málaga) y
el P8 (coche compartido → F8, octubre). SER queda **aparcada tras la ola** — decisión consciente,
no un olvido. La tabla de fases de la ola vive SOLO en su doc; aquí queda el orden de DESPUÉS.

| P | Qué | De dónde | Por qué aquí |
|---|---|---|---|
| **P0** | dashboard RC (plantilla del paywall con la comparativa nueva: 7 días, 1 coche, 2 zonas, stats avanzadas, coche compartido "próximamente") · AAB a Pruebas internas · `paparcar_plus` en Play · destino de la rama iOS (PR #3) | proyecto | Pendientes del user arrastrados de MONEY-001. El "plegar MONEY-001" del 06-09 ya está: mergeado |
| **OLA 2** | A1 analytics · A2 widget · F1 límites free · F2 combustible · F3-F4 estadísticas · F5 gasolineras · F6 parkings (Madrid+Málaga) · F7 tiempo de búsqueda (oct) · F8 coche compartido (oct) | [`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md) | Orden fijado por el user el 07-09. ✅ Dirección del modelo de F8 decidida el 08-09: PROYECCIÓN (espejo `sharedCars/`, sesión personal = fuente de verdad) — detalle en la F8 del doc de la ola |
| **P1** | **1b · Embudo de búsqueda** + modo "voy de camino" | fichero #1 + ROADMAP viejo | Es la **métrica del piloto**. Sin esto, `spot_found` no se puede emitir con verdad (§3.2). F7 de la ola NO lo sustituye: mide *cuánto tardaste tú*, no *si la comunidad funciona* |
| **P2** | **3 · Agregación por celda** | fichero #3 | Barata, y la elección de celda es irreversible: cuanto antes, más barata |
| **P3** | **4 · Alertas SER (Plus)** — ⏸️ aparcada tras la ola (07-09) | fichero #4 | Sigue siendo el argumento de venta que no depende de tener usuarios. Cuando toque: empezar por 4a + 4b + **4f** (fuera de horario), que es donde el usuario percibe el valor sin hacer nada |
| **P4** | **5 · Capa de oferta SER** | fichero #5 | Reutiliza el pipeline de 4a; solo si el estilo de mapa ya está decidido (§3.5) |
| **P5** | **6 · Otras ciudades** | fichero #6 | Solo cuando haya usuarios fuera de Madrid (Madrid + Málaga ya entran con F6 de la ola) |
| **P6** | **8 · Mapa de probabilidad** | fichero #8 | 2027, cuando la agregación lleve meses acumulando en el barrio piloto |
| — | Android Auto / CarPlay pairing · WearOS · 4º tab Comunidad · modularización | ROADMAP viejo | Sin fecha. El pairing sube si F8 revela coches sin BT emparejable |
| — | **AdMob** · promo Fundadores · paywall propio · `ROUTES-PLUS-001` · `PARKING-AFFILIATE-001` | money-001 / ola 2 | Siguen fuera |

---

## 5. Las ocho features, en detalle

> Contenido del fichero de producto, íntegro. La línea **⚠️ Al definirla** es lo que añade este doc.

### 5.1 · Instrumentación de eventos (Firebase Analytics)

Nombrar y emitir los eventos que ya ocurren, para acumular datos desde el día uno.
**v11 si cabe en una tarde, si no v12** · transversal · muy alta (½ día) · complejidad baja · riesgo
casi nulo · no depende de nada.

Eventos: `parking_session_started`, `parking_session_ended`, `spot_released`, `search_started`,
`search_ended`, `spot_found`. Cada uno con timestamp, celda geográfica (nunca coordenada exacta),
fuente de detección y confianza. Firebase Analytics + export a BigQuery da el dashboard gratis: no
construir uno propio.

⚠️ **Al definirla:** partirla en 1a/1b (§3.2) · un solo punto de emisión con la telemetría que ya
existe (§3.3) · dependencia nueva + `expect/actual` · fila en el Data Safety **en la misma tarea**.

→ **07-09:** la mitad **1a** entra como **fase A1** al arranque de la ola 2
([`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md)); **1b** sigue aquí, tras la ola (P1).

### 5.2 · Pines de parkings públicos con ocupación en tiempo real (Madrid)

Parkings de rotación de Madrid con plazas libres reales. Resuelve el "mapa vacío" con información
verdadera. **Stretch #1 de la v11, por delante de AdMob; si no, v12** · free (es gancho, no premium)
· alta (1-2 d) · riesgo bajo, y el único es de UX: que el usuario confunda "parking de pago con
sitio" con "plaza gratis en la calle".

- **2a · Adaptador Madrid** (Cloud Function programada): consulta el feed abierto cada pocos minutos
  y escribe documentos normalizados en `external_parking/{madrid}_{id}` (nombre, coordenadas,
  capacidad, libres, hora, fuente, procedencia). Sin datos de usuario. Sin coste.
- **2b · Interfaz `ExternalParkingSource`**: el contrato que cumple cualquier ciudad. Se diseña una
  vez; después cada ciudad es solo un adaptador y la app no cambia.
- **2c · Render en el mapa**: pines visualmente distintos a las plazas comunitarias (icono de
  parking, número de libres, "actualizado hace X min"). **Nunca** el mismo icono que una plaza de la
  comunidad.
- **2d · Estado "sin datos cerca"**: mensaje honesto en vez de mapa en blanco.

Datasets: [ocupación en tiempo real](https://datos.madrid.es/dataset/50027-0-aparcamientosocupacionyservicios)
· [histórico](https://datos.madrid.es/dataset/300346-0-aparcamientos-ocupa-historico).

⚠️ **Al definirla:** procedencia, no confianza (§3.1) · primer backend propio (§3.4) · el marcador es
Nivel 3 y la rampa de frescura no se toca (§3.5).

→ **07-09:** ejecutándose como **F6 · PARKINGS-LIVE-001** de la ola 2, ampliada con Málaga, esquema
`PublicParking` concreto y fusión con la base estática de Overpass — los tres avisos de arriba
están copiados en su diseño.

### 5.3 · Agregación anónima por celda

Convertir los eventos en contadores por celda × día de la semana × hora. Base de cualquier
probabilidad futura, privada por construcción. **v12** · infraestructura · alta (1-2 d) · complejidad
media (**el sistema de celdas no se puede cambiar después**) · depende de 5.1.

- **3a · Sistema de celdas**: geohash p7 o H3 r9 (~150 m). Decidir **una vez, para siempre**.
- **3b · Cloud Function de agregación**: incrementa `cells/{cellId}/stats/{dow}_{hour}` (sessions,
  releases, searches, found). Sin uid, sin coordenada.
- **3c · Export a BigQuery** (gratis): el análisis pesado vive ahí, no en Firestore.
- **3d · Resúmenes por celda**: SQL programado → `cell_summary/{cellId}`. La app **lee resúmenes,
  nunca calcula**.

⚠️ **Al definirla:** el umbral de muestra mínima va **en la escritura**, no solo al pintar (§5.8):
con pocos usuarios, celda + día + hora identifica a una persona.

### 5.4 · Alertas SER (Madrid) — **Plus**

Al detectar un aparcamiento, saber si está en zona regulada, de qué color, y avisar del vencimiento.
Es el argumento de venta más fuerte: se traduce en euros (multa 90-200 € vs. Plus 24 €/año) y **no
depende de tener usuarios**. **v12-v13** · Plus · media (3-5 d, casi todo el pipeline del dataset) ·
riesgo medio-alto **reputacional**: si la app se equivoca de zona y alguien se come una multa
fiándose, el daño es serio → se mitiga con diseño (preguntar, nunca afirmar; horarios remotos; aviso
de "consulta la señalización").

- **4a · Pipeline del dataset** (script de build): Shapefile de Zonas SER → reproyectar
  ETRS89/UTM30 → WGS84 (**el error clásico si no se hace**) → simplificar → base con tramos +
  bounding box. Fuera de la app; genera un `.db` empaquetado o servido desde Storage.
- **4b · Resolver de zona** (commonMain): bounding box + distancia a polilínea → tramo más cercano
  dentro de ~25 m → color y barrio. Dos tramos de color distinto dentro del umbral ⇒ **ambiguo**, no
  elige.
- **4c · Reglas de horario y límites** (Remote Config): L-V 9-21, S 9-15, dom/festivos libre; máx.
  4 h azul / 2 h verde no residentes. Varía por distrito ⇒ nunca hardcodeado.
- **4d · UX de confirmación**: al aparcar en SER, notificación con pregunta ("¿Zona verde en
  Justicia?") y acciones rápidas: 1 h / 2 h / hasta las X / soy residente. Las correcciones del
  usuario se guardan como dato de calidad.
- **4e · Notificaciones locales**: 15 min antes y al vencer. En el dispositivo, sin backend.
- **4f · Caso fuera de horario**: *"Puedes dejarlo hasta el lunes a las 9:00"*. Es el momento en que
  el usuario percibe el valor sin haber hecho nada. **Prioritario dentro de la feature.**
- **4g · Preferencia de residente por zona**: si marca "soy residente" en su barrio, no volver a
  preguntar ahí.

Datasets: [Zonas SER](https://datos.madrid.es/dataset/200077-0-zona-ser) (mensual, CC BY 4.0) ·
referencia de procesado: [Zona-SER-Madrid](https://github.com/jjimenezshaw/Zona-SER-Madrid).

⚠️ **Al definirla:** tres decisiones de stack sin nombrar — persistencia del dataset, Remote Config y
alarmas exactas (§3.6) · el `.db` derivado **no se commitea**.

→ **07-09:** **aparcada TRAS la ola 2** — decisión consciente del user, no un olvido. El análisis
4a-4g queda intacto para cuando le toque (P3 de §4).

### 5.5 · Capa de oferta SER: calles y número de plazas

Sombrear dónde hay aparcamiento regulado y cuántas plazas, como capa de **oferta**, no de
disponibilidad. **v12-v13 si sobra tiempo** · free · media (2-3 d) · riesgo bajo si se etiqueta como
oferta ("aquí hay 40 plazas de zona azul"), alto si se lee como "aquí hay sitio" · depende de 4a.

- **5a · Ingesta** del dataset de calles y nº de plazas al mismo almacén.
- **5b · Render de tramos** por tipo de zona, grosor/opacidad por nº de plazas, con leyenda que diga
  explícitamente *"oferta, no disponibilidad"*.

Dataset: [SER: calles y número de plazas](https://datos.madrid.es/dataset/218228-0-ser-calles).

### 5.6 · Adaptadores de otras ciudades

Replicar 5.2 donde publiquen ocupación en abierto. **Cuando haya usuarios fuera de Madrid, no antes**
· free · muy alta (½ d/ciudad) una vez existe 2b · depende de 2b.

Confirmado en abierto: **Málaga** (aparcamientos municipales, CSV) y **Valencia** (parkings en tiempo
real, VLCi). **Barcelona** publica el listado, no la ocupación en tiempo real. **Sevilla**: no
encontrado. Europa: bastantes (p. ej. Tours).

### 5.7 · Coche compartido (solo Bluetooth) — **Plus**

Varios usuarios vinculados a un mismo coche ven dónde está aparcado. Efecto de red que funciona con
2-3 personas, no con 2.000; y motor de crecimiento, porque cada usuario Plus mete gratis a su familia
en la app. **Post-octubre, después de 5.4** · la capacidad de compartir la paga el **dueño**; el
invitado no paga y el coche recibido no cuenta contra su límite de vehículos. Baja (2-3 semanas) ·
complejidad **alta**: modelo de datos, Cloud Functions, deep links, detección, notificaciones y
facturación · riesgo medio: deduplicación entre móviles, falsos positivos que sobrescriben la
ubicación real, y privacidad (compartir el coche es compartir dónde estuvo quien lo condujo).

- **7a · Refactor del modelo**: los aparcamientos cuelgan del coche (`cars/{carId}/events`), no del
  usuario; el historial personal pasa a ser una consulta filtrada. **La pieza de fondo y la más
  delicada.**
- **7b · Invitación por Cloud Function**: código corto/enlace (caduca 24-48 h, un solo uso); la
  función valida y añade el uid a `memberIds`. Reglas de seguridad: acceso si tu uid está en la lista.
- **7c · Deep link + entrada manual**: al instalar desde el enlace, pantalla "Te han invitado" (no el
  formulario de crear vehículo). Y **siempre** una entrada manual con código, porque los deep links
  fallan. Lenguaje: *"unirme"*, nunca *"compartir"*.
- **7d · Regla de atribución solo-BT**: en un coche compartido, solo un evento con match de Bluetooth
  escribe la ubicación oficial. Geocerca y Coordinator quedan degradados a **sugerencia** que no
  sobrescribe nada hasta confirmarse. Evita "un amigo recoge al hijo y el padre pierde el coche".
  Merece guard propio (`[SHARE-DEDUP-*]`).
- **7e · Coche sin Bluetooth**: aviso al compartir + botón manual "he aparcado yo".
- **7f · Notificaciones FCM**: *"Papá ha aparcado en Alcalá 120"*. Es lo que hace que se use a diario.
- **7g · Proyección del entitlement**: webhook RC → Cloud Function → `plusActive` en el usuario y
  `sharingEnabled` en el coche. **Proyección, no réplica.** Documentar como ADR.
- **7h · Downgrade**: si el dueño deja de pagar, los miembros existentes siguen; solo se bloquea
  añadir nuevos. Coherente con *"conservar lo creado, gatear lo nuevo"*.
- **7i · Límites y privacidad**: tope 3-4 miembros; el invitado ve solo el coche compartido con su
  propia ventana de historial (30 días en free); pantalla de aceptación explícita; salir en un toque;
  el dueño revoca. Va a la declaración de Seguridad de los datos de Play.

⚠️ **Al definirla:** §3.7 entero — decidir 7a **mientras la base remota sigue vacía**, 7d es
`det-change`, y 7g necesita su ADR para no leerse como una violación de la regla de MONEY-001.

→ **07-09:** es **F8 · VEH-SHARED-001** de la ola 2 (octubre), con reglas de producto ya cerradas
(solo BT, 4 miembros, dueño paga).
→ **08-09:** dirección del modelo **DECIDIDA en sesión: PROYECCIÓN, no la 7a.** La sesión personal
(`users/{uid}/parkingHistory`) sigue siendo la fuente de verdad; el coche compartido es un espejo
top-level `sharedCars/{carId}` (núcleos de sesión sin ruta + `state` con la posición actual), que
nace al activar la compartición y arranca vacío. Se midió en el código que lo caro de la 7a no era
migrar datos sino re-cablear Room/outbox/reconcile/reglas/iOS — y aun así habría tenido que partir
el doc de sesión para no exponer la ruta. Con la proyección, F2/F3/F7 construyen sin riesgo de
re-keyado y **el argumento "decidir mientras la base está vacía" queda disuelto**: nunca hay
migración de datos de sesión. Detalle completo en la F8 de
[`backlog/plus-roadmap-001.md`](./backlog/plus-roadmap-001.md).

### 5.8 · Mapa de probabilidad por zonas

Zonas con mayor o menor probabilidad de encontrar sitio, a partir de datos propios agregados. Visión
a largo plazo. **2027, cuando 5.3 lleve meses acumulando en el barrio piloto** · free/Plus por decidir
(candidata natural a Plus) · complejidad alta · riesgo alto si se muestra sin confianza suficiente:
**"0 liberaciones ≠ 0 plazas"**. Nunca mezclar con datos oficiales ni comunitarios · depende de 5.1,
5.3 y usuarios reales.

- **8a · Indicadores por celda** (BigQuery): ratio búsquedas→encontrado, liberaciones/hora, con
  tamaño de muestra mínimo para publicar.
- **8b · Laboratorio con Melbourne**: validar el método contra el
  [dataset de sensores de calle de Melbourne](https://data.melbourne.vic.gov.au/explore/dataset/on-street-parking-bay-sensors/)
  (tiempo real + histórico plaza a plaza) antes de fiarse de los datos propios. Es la única verdad
  terreno disponible en abierto.
- **8c · Render de calor con confianza visible** y leyenda que diga "estimación".

---

## 6. Decisiones de lanzamiento (no son features, pero condicionan todo)

- **Vender primero "encuentra tu coche"**, no "encuentra plaza". Lo comunitario, como promesa de
  futuro y **fuera del paywall**. — *Coincide con lo que ya promete la ficha de Play: "sabe dónde
  aparcaste", sin prometer detección infalible.*
- **Segmentos iniciales**: personal de hospital/turnos y polígonos · usuarios de zona SER ·
  comerciales y técnicos que aparcan muchas veces al día · hogares con coche compartido.
- **Barrio piloto**: concentrar usuarios en pocas manzanas para que la cara comunitaria tenga
  oportunidad. Criterios: dolor alto diario, aparcamiento en calle dominante, población recurrente,
  zona SER, un imán (hospital, campus, mercado). Candidatos: Chamberí, Goya-Lista, Arganzuela
  (Delicias/Legazpi), entornos de La Paz o Gregorio Marañón.
- **Métrica del piloto**: **% de búsquedas que terminan en plaza encontrada**, no descargas.
  ⚠️ Hoy no es medible: ver §3.2.

## 7. Descartado por ahora (y por qué)

- **TomTom / INRIX / HERE / Parkopedia**: B2B, sin precio público, contrato. Y estratégicamente
  equivocado: si el mapa es la probabilidad de TomTom, Paparcar no aporta nada frente a Google Maps.
- **Backend REST propio delante de Firestore**: rompe el offline-first y añade hosting y
  mantenimiento para un problema que no existe. La normalización se hace con Cloud Functions.
- **Dashboard de analytics propio**: Firebase Analytics + BigQuery lo dan gratis.
- **Expansión a otras ciudades ahora**: sin usuarios en Madrid no tiene sentido.
- **Coche compartido con geocerca**: falsos positivos que sobrescriben la ubicación real. Solo BT.
- **Probabilidad por zonas en la v11**: sin datos, sería inventada.

---

## 8. Cómo se define cada una

1. El user elige la siguiente de §4.
2. **Sesión de definición paso a paso** — decisiones de producto cerradas ANTES de tocar código,
   incluidas las ⚠️ de §3 que le afecten. Sale de ahí el `docs/backlog/<id>.md`.
3. Ticket con la skill `nuevo-ticket`: worktree aislado + rama + doc. Si toca detección, además
   `det-change`.
4. Fases con un commit cada una, parando a resumir al cerrar cada fase (como MONEY-001).

Mientras tanto, este doc es lo único que hay: **una lista ordenada, no un permiso**.
