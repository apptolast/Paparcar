# OPS-DIAGNOSTICS-WIPE-IS-HALF-DONE-001 · El borrado de datos del lanzamiento se quedó en 1 de 8 uids

**Estado:** ⏸ Aplazado (03-09-2026) — decisión del user: el corpus ya está a salvo en disco, los
7 uids restantes se limpian cuando convenga. Sin rama: es operación, no código.

## Qué se hizo el 03-09

Borrado de datos previo al lanzamiento, con las cuentas de Auth **intactas** (los testers vuelven
con su mismo uid):

| Colección | Estado |
|---|---|
| `users` | ✅ borrada — 11 docs + 628 anidados (`parkingHistory`, `vehicles`, `zones`) |
| `spots` | ✅ borrada — 13 docs |
| `diagnostics` | 🔴 **1 de 8 uids** (`12ck5eNW…`). Quedan 7 |
| `diagnostics_config` | ⏸ **conservada a propósito** — ver abajo |
| Auth | ✅ intacto, 11 cuentas |

Export completo previo, **fuera del repo** (que es público):
`~/Documents/paparcar-field-corpus-2026-09-03/` — 33 MB, 28.163 docs anidados, con `MANIFEST.json`.

### Por qué `diagnostics_config` NO se borra

No es dato de usuario: es el flag de opt-in **administrado por nosotros**
(`diagnostics_config/{uid}.enabled`, `DiagnosticsFirestoreSchema.COLLECTION_CONFIG`) que el cliente
lee para auto-silenciarse. `FirestoreDetectionEventLogger` y `FirestoreUiLocationLogger` se
desactivan solos si no está a `true`. Borrarlo dejaría la telemetría muda para los 11 testers justo
al lanzar, y además tira las notas curadas por uid. Los uids sobreviven (Auth intacto), así que el
flag sigue apuntando a la persona correcta.

## Por qué se paró — y ⛔ NO fue la escritura

**429 `RESOURCE_EXHAUSTED`** — cuota diaria gratuita del plan **Spark** (billing desactivado en
`pap-26`). No era volumen ni ruta: fallaba igual con un uid de 51 documentos. Se renueva a
medianoche del Pacífico.

El eje importa, porque el reflejo es culpar a la app y aquí la app es inocente. Contado sobre el
export (gratis, ya está en disco):

| Eje | Techo Spark/día | Consumo real |
|---|---|---|
| **Escrituras** | 20.000 | **267 el 02-09, 7 el 03-09** en TODO `diagnostics` — ni de lejos |
| **Lecturas** | 50.000 | **~56k**: el export (28.163 docs) + el primer `firestore:delete diagnostics --recursive`, que escanea la colección entera para saber qué borrar antes de fallar |

Los 25.260 `uiLocation` son acumulados de semanas, no de un día. **La cuota la quemó el rescate del
corpus, no la telemetría.** Si esto se repite: exportar y borrar el mismo día, sobre 28k documentos,
no cabe en Spark — hay que separarlos en días distintos o subir a Blaze.

⚠️ Mientras dura el 429, la app da error contra Firestore **en todos los móviles**.

## Decisión sobre el gate remoto (03-09)

Los diagnósticos remotos **ya están apagados por defecto en producción**, sin tocar código: el gate
es `.get<Boolean?>(FIELD_ENABLED) ?: false` y arranca `null`, así que un usuario nuevo de Play —sin
doc en `diagnostics_config`— no emite nada remoto. El sink local de logcat sigue activo y no cuesta
cuota.

Los 13 docs existentes están **los 13 en `enabled: true`** (barrido del 30-08). Se propuso apagar
todos menos los dos móviles de banco; **el user decidió dejarlos como están**. Consecuencia asumida
y anotada aquí para que no se descubra por sorpresa: en cuanto los 11 testers reabran la app tras el
borrado vuelven a emitir, y `uiLocation` escribe **un doc por fix con throttle de 10 s** ≈ 360
docs/hora por móvil con el mapa abierto (`REMOTE_FIX_MIN_INTERVAL_MS = 10_000L`).

## Lo que esto destapa para el lanzamiento

`diagnostics/{uid}/uiLocation` es, de largo, el mayor volumen del proyecto: **18.438 documentos en
un solo uid**, un doc por muestra de localización de mapa (`UiLocationSampleDto`, sin doc de
sesión que los agrupe). Si 28k documentos agotan un día en Spark con 11 testers, una app pública
toca ese techo enseguida. Dos frentes distintos, y conviene no confundirlos:

1. **Plan** — Blaze antes de abrir el grifo (decisión del user, necesita tarjeta).
2. **Diseño** — un doc por muestra es caro por construcción. Agrupar por sesión, o muestrear más
   fuerte, es una tarea de código propia que este doc NO abre; solo la señala con su medida.

## Cómo terminarlo

Con cuota disponible, por uid (el borrado de la colección entera falló a mitad):

```bash
npx -y firebase-tools firestore:delete "diagnostics/<uid>" --recursive --force --project pap-26
```

uids pendientes: `90lnZzs5…` · `WZB7oft…` · `fiypNbEl…` · `itmGbBxa…` · `nJEqcLEs…` · `nvGQi4pT…`
· `sUGo7EYl…`. Dos de ellos (`WZB7oft…`, `nJEqcLEs…`) son **huérfanos** del reset del 30-08: no
tienen ni cuenta de Auth ni doc en `users`.

## Criterio de éxito

`firestore_list_collections` sobre la raíz devuelve solo `diagnostics_config`, y las cuentas de
Auth siguen siendo 11.
