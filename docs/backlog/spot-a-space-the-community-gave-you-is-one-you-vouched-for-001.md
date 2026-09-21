# SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001 · el espejo de las plazas cedidas

**Estado:** ✅ Done · rama `feat/SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001` · issue #110

## Problema
#52 dejó el verde de las cifras apuntando a lo que el usuario **aporta** (plazas cedidas). Faltaba
su mitad: **las que la comunidad le dio a él**. Y el ticket avisaba de lo difícil: *¿cuándo damos
por bueno que aparcaste EN esa plaza?*

## La pregunta que sí se puede contestar
Las candidatas obvias no valen, y el ticket ya lo decía:
- tocar una plaza en la lista → **mirar no es ir**;
- avisar de que vas → puede que no llegaras, o que estuviera ocupada (para eso existe el voto «ya
  no está»);
- aparcar cerca de una plaza que desaparece → **dos umbrales inventados, dos formas de fallar**.

La que sí, y **sin inventar ni un número**: *«diste fe de estar EN esa plaza, y aparcaste en ella
antes de que caducara»*.

| | de dónde sale | por qué no es inventado |
|---|---|---|
| **dónde** | `SpotVotePolicy.MAX_VOTE_DISTANCE_METERS` (50 m) | es la definición que la app YA tiene de «estabas ahí» — tan fuerte que con ella **un solo voto retira una plaza para todos** |
| **cuándo** | la caducidad de la **propia plaza** | pasada, la plaza no existe: no hay nada que nadie te haya dado |

La evidencia es **del propio usuario**: el voto «sigue libre», que ya pasó el filtro de proximidad.

## ⛔ Subcuenta siempre, y es deliberado
Sólo cuenta cuando el usuario votó. Habrá plazas que la comunidad le dio y que esto no verá.
Es el error correcto: **atribuirse una plaza que el conductor encontró solo convierte la cifra en
propaganda; el silencio sólo la deja pequeña.** Fallo asimétrico, como el resto del proyecto.

## ⛔ Es un HECHO de la sesión, no una cifra agregada — y ahí está la decisión de producto
No entra en la rejilla de cifras como espejo de `SPOTS_SHARED`, aunque el ticket lo pidiera así.
Un total construido sobre votos voluntarios **subcuenta sistemáticamente**, y un total que
subcuenta puesto al lado de totales MEDIDOS (km, gasto, CO₂) es una mentira por vecindad: el lector
los lee a todos con la misma confianza.

Como línea del detalle del aparcamiento, en cambio, **siempre es verdad cuando se enseña** y nunca
afirma un total. Si algún día se mide que la gente vota mucho, promoverlo a tile es una línea.

## Detalles que costaron
- ⛔ **Se guarda la ÚLTIMA plaza avalada, no una lista.** Dar fe de dos y aparcar en la primera es
  un caso real que esto no ve — y perderlo sólo hace la cifra más pequeña. Una lista traería poda,
  caducidad y tamaño máximo para ganar un caso raro.
- ⚠️ Vive en `AppPreferences` y no en Room: entre el voto y el aparcamiento pueden pasar minutos, el
  servicio de detección va por su cuenta, y es **un** registro, no una tabla. Serializado en UNA
  cadena porque cuatro claves sueltas se pueden leer a medio escribir; y una cadena que no se
  entiende **se lee como null**, que deja la cifra pequeña en vez de reventar un aparcamiento.
- ⛔ El campo se llama **`communitySpotId`** y no `spotId`: esto es justo lo que el difunto `spotId`
  fingía ser [DATA-A-DEAD-FIELD-THAT-LIES-IS-WORSE-THAN-A-DEAD-FIELD-001], y reutilizar el nombre
  habría resucitado la trampa el mismo día de enterrarla.

## Criterio de éxito
`CommunityGivenSpotTest`, 5 testigos. **Falsificados** contra el atajo (quitar la caducidad y poner
un umbral propio de 150 m): caen 3, incluido el que fija que la distancia es **la de la política de
votos y no una nueva**.

Room **v18 → v19** (`ADD COLUMN` simple: añadir columnas sí lo sabe hacer el SQLite de Android 8;
lo que no sabe es soltarlas, y de eso iba la v18). 3594 tests, 0 fallos. Strings en los 9 locales.

## Consumidores auditados
`communitySpotId` end-to-end: dominio, `UserParkingEntity`, `ParkingHistoryDto`,
`ParkingSessionMapper` ×4, `RemoteUserProfileDataSourceImpl` (lectura + constante), `UserParkingReconcile`.
⛔ **No viaja al espejo del coche compartido**: es una cifra TUYA, no del coche — la plaza te la
dieron a ti, no al vehículo.
