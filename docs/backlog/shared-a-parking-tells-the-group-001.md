# SHARED-A-PARKING-TELLS-THE-GROUP-001 — el coche compartido avisa al grupo

🟢 **Implementado, sin pasar por campo.** Rama `feature/SHARED-A-PARKING-TELLS-THE-GROUP-001`
sobre master `ef49abdc`.

## El problema

Con `VEH-SHARED-001` etapa 1 y `DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001` ya en master, el espejo
del grupo se escribe y la valla compartida sabe seguir al coche. Lo que faltaba es **cuándo se
entera el otro móvil**, y la respuesta era mala en los dos casos que importan:

- **La noticia** — Marta aparca; Antonio no lo sabe hasta que abre la app. La función del coche
  compartido es justo la contraria: enterarte sin mirar.
- **La vigilancia** — la valla del grupo la mueve `SharedCarFenceReconciler`, y al reconciliador lo
  llaman el `GeofenceJanitorWorker` (cada 15 min) y el arranque de la app. O sea que entre que
  Marta deja el coche y Antonio tiene la geocerca donde está el coche podían pasar **15 minutos**.
  Si Antonio sale a por el coche antes, sale sin vigilancia.

## Qué se ha hecho

**Servidor — segunda Cloud Function** (`functions/`, `europe-west1`, el patrón de
`ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001`):

- `onSharedCarParked` — trigger `onDocumentWritten` sobre `sharedCars/{carId}/state/current`.
  ⛔ `Written` y no `Created`: el documento de estado se escribe una vez por aparcamiento pero se
  CREA una vez por coche; todo aparcamiento a partir del segundo es un update.
- `functions/src/sharedCarFanout.ts` — la decisión, pura y testeable: `verdictFor(before, after)` y
  `targetsFor(members, authorUid)`. **8 tests nuevos** (21 en total en `functions/`), y son casi
  todos negativos, porque los tres «no» que tiene son eventos REALES:
  - el estado también se escribe **al salir** el coche → «ha aparcado» sería justo lo contrario;
  - el geocoder contesta tarde → el MISMO aparcamiento se escribe dos veces (coordenadas primero,
    calle después) → un segundo aviso sería el coche aparcado dos veces;
  - sin sesión o sin coordenadas no hay nada que abrir en el mapa.
- **El autor se excluye en el servidor, no en el móvil**: un móvil solo puede callarse DESPUÉS de
  recibir, y para entonces ya ha gastado la entrega y la vibración. Además «alguien ha aparcado tu
  coche» leído por quien acaba de aparcarlo es la peor frase posible de este feature.
- Mensaje **de datos**, no de notificación: los nueve idiomas viven en la app. Prioridad `high`,
  porque el movimiento de valla viaja en este mensaje y una entrega retenida hasta la próxima
  ventana de Doze deja la geocerca exactamente donde el ticket intenta que no se quede.
- Poda de tokens muertos con `arrayRemove`, igual que la de zona.

**Cliente:**

- `SharedCarMemberSync` (androidMain) publica el token de ESTE móvil en `members/{uid}.tokens` de
  cada coche del grupo. **Estar en la lista ES el permiso** — la función no pregunta por Plus, no
  puede (Plus vive en RevenueCat), y salir del grupo borra la ficha y con ella el aviso. Escucha el
  flujo de coches compartidos (no una lectura al arrancar: en instalación limpia Room está vacío
  mientras aterriza la sincronización) y se re-dispara en `onNewToken`.
- `PaparcarMessagingService` reparte el tipo `shared_car_parked`. **Primero encola el janitor con
  `TRIGGER_SHARED_CAR_PUSH` y sin condiciones**, y solo después construye la tarjeta: el
  reconciliador no lee nada de este mensaje —relee el espejo— así que un campo que falte puede
  costar la tarjeta, nunca la vigilancia.
- `AppNotificationManager.showSharedCarParked(...)` + implementación Android
  (`SHARED_CAR_PARKED_NOTIFICATION_ID = 2012`, `RC_SHARED_CAR_PARKED = 21`), mismo intent que la
  alerta de zona: abre el mapa centrado en el punto, porque la pregunta es la misma.
- 4 keys × 9 locales. Sin nombre de perfil la tarjeta dice *«Han aparcado el coche compartido»*, y
  sin calle *«Toca para ver dónde está»* — callarse por no saber el nombre sería perder el aviso
  entero por el campo menos importante que trae.

**Reglas** (`firestore.rules`) — el `create` y el `update` de `members/{uid}` se han **separado**:

```
allow create: … && (esOwner || holdsValidInvite(…));
allow update: … && resource != null;
```

⛔ El update ya no vuelve a pedir el invite. El invite **caduca en 24 h** y el token de FCM **rota
para siempre** (reinstalación, restauración, borrado de datos): con la regla unificada, un miembro
de hace una semana no podía reescribir su propio token y se quedaba sin avisos **en silencio**,
conservando su sitio en el grupo. Que el documento ya exista es la prueba de que la entrada ocurrió.

## Lo que este ticket NO hace

- **Dedupe entre dos móviles del grupo** — sigue en etapa 2. Si Marta y Antonio están juntos en el
  coche, el que no conduce recibe el aviso: es correcto según la regla («no eres el autor»), y es
  ruido. Se resuelve mirando si el receptor estaba en el mismo viaje, no aquí.
- **Aviso de que el coche SE VA** — hoy solo se anuncia el aparcamiento. La salida se ve en la app.
- **Nada de detección** — el carril del coche compartido sigue siendo el de siempre: el espejo lo
  escribe `SharedCarMirrorWriter` desde la sesión personal, que sigue siendo la fuente de verdad.

## Pendiente antes de darlo por cerrado

- [ ] **Desplegar la función** (`npm run deploy` en `functions/`) — ⚠️ la de zona quedó pendiente de
      Eventarc en su ticket; esta comparte proyecto y región, así que probablemente el mismo trámite
      valga para las dos.
- [ ] **Desplegar las reglas** con el `members` partido.
- [ ] **Campo, dos cuentas**: entrar por código en el móvil B, aparcar en el A, y comprobar las dos
      cosas del ticket — que llega la tarjeta y que **la valla ya está movida en B** sin esperar al
      janitor (verificable con `dumpsys` o simplemente viendo que un EXIT en B se atiende).
