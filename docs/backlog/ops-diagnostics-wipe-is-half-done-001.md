# OPS-DIAGNOSTICS-WIPE-IS-HALF-DONE-001 · El borrado de datos del lanzamiento se quedó en 1 de 8 uids

**Estado:** ✅ Cerrado (03-09-2026) — resuelto borrando **la base de datos entera**, no documento a
documento. Sin rama: es operación, no código. Lo que este doc conserva es la trampa que destapó.
Estado final verificado: `listCollectionIds` sobre la raíz de `pap-26` devuelve **`{}`** — ni una
colección, `diagnostics_config` incluida. El proyecto está en **Blaze** desde el 03-09.

## Cómo acabó

`firestore:databases:delete "(default)" --force` + `create --location eur3` + `deploy --only
firestore:rules,firestore:indexes`, y `diagnostics_config` repoblada desde el export. La raíz de
`pap-26` es hoy **solo `diagnostics_config`** (13 docs, `COUNT()` verificado), y Auth sigue intacto:
borrar la base no toca las cuentas.

| Colección | Estado final |
|---|---|
| `users`, `spots` | ✅ borradas (03-09, antes del reset de base) |
| `diagnostics` | ✅ borrada entera con la base — sesiones, `uiLocation` y ~57.000 `events` |
| `diagnostics_config` | ✅ **borrada** — se restauró, se apagó entera y finalmente se barrió: sin usuarios no protegía nada, y las notas viven en el zip |
| Reglas | ✅ intactas — viven a nivel de proyecto, sobrevivieron al borrado de la base |
| Índices | ✅ `indexes: []` en el repo y `[]` en el proyecto: no había ninguno que perder |
| Auth | ✅ intacto |

## ⛔ Borrar la BASE es gratis; borrar DOCUMENTOS cuesta cuota

Es el hallazgo reutilizable, y es contraintuitivo porque el reflejo es `firestore:delete --recursive`:

- **Documento a documento** cuenta contra dos techos Spark distintos: **50.000 lecturas/día**
  (`firestore:delete` LEE todo lo que va a borrar) y **20.000 borrados/día**. Con ~59.000 documentos
  vivos eran **3 días** de ventanas encadenadas, no una tarde.
- **Borrar la base** (`firestore:databases:delete`) es una operación de administración: **0 lecturas,
  0 borrados**, instantánea. Requisitos: `DELETE_PROTECTION_DISABLED` (comprobar con
  `firestore:databases:get`) y volver a crearla en **la misma location** (`eur3` aquí).
- ⚠️ El ID `(default)` **no se libera hasta ~5 min después**: el `create` responde
  *"Database ID '(default)' is not available… retry in N seconds"*. Es cooldown, no un fallo; se
  reintenta y entra.
- Sobrevive todo lo que no es la base: `google-services.json`, la key de Maps, los SHA-1, Auth,
  Crashlytics, Hosting y el AAB ya firmado. Por eso **no** hace falta recrear el proyecto: recrearlo
  sí obligaría a rehacer todo eso.
- Lo que NO sobrevive y hay que redesplegar: los **índices** (viven en la base). Las **reglas** no —
  son del servicio `firebaserules`, a nivel de proyecto.

## ⛔ El export del 03-09 no era completo, y su propia docstring lo negaba

`export_firestore.py` se presenta como *"Full recursive export"*. No lo es. Su `subcollections()`
para un nivel por debajo de cada documento raíz, con este comentario:

> *"Deliberately not recursive past this: the leaves are session documents, and probing each of the
> ~800 per uid for children would cost a request apiece to learn they have none."*

**Las sesiones no son hojas.** Cada `diagnostics/{uid}/sessions/{sid}` tiene una subcolección
`events` — un documento por paso del detector, que es justo lo que la skill `field-test` lee para
reconstruir un viaje. Los 28.163 docs del `MANIFEST.json` son solo `sessions` + `uiLocation`; había
además ~**57.000 eventos** que nunca se exportaron.

Se descubrió tarde y de rebote: el propio `firestore:delete` iba imprimiendo rutas
`…/sessions/<id>/events/<id>` mientras borraba. Confirmado después con `listCollectionIds` sobre una
sesión viva, que devuelve `["events"]`.

**Coste real:** antes de verlo se borraron 3 uids enteros y 2 a medias — **303 sesiones cuyo trazado
evento a evento ya no existe**. De esas 303 sobrevive el rollup (`summary`, `outcome`, `fixCount`,
`maxSpeedKmh`, coords finales, device, flags), que sí estaba en el doc de sesión. Se perdió el
detalle forense, no el inventario de viajes. El resto se fue al borrar la base, ya con la decisión
tomada de empezar de cero.

📌 Reglas que deja: **un export se verifica con `listCollectionIds` sobre una HOJA**, no con la
docstring del script que lo generó; y **el número del MANIFEST no prueba cobertura**, solo cuenta lo
que el script se dignó a mirar. `export_events.py` (recursivo de verdad, reanudable, con techo de
lecturas autoimpuesto) quedó escrito en el scratchpad de la sesión, sin llegar a usarse.

## Por qué se paró la primera vez — y ⛔ NO fue la escritura

**429 `RESOURCE_EXHAUSTED`**, cuota diaria del plan **Spark** (billing desactivado en `pap-26`). No
era volumen ni ruta: fallaba igual con un uid de 51 documentos. La ventana se renueva a medianoche
del Pacífico (**09:00 en España**), y los tres contadores son independientes:

| Eje | Techo Spark/día | Consumo real el 03-09 |
|---|---|---|
| **Escrituras** | 20.000 | **267 el 02-09, 7 el 03-09** en TODO `diagnostics` — la app es inocente |
| **Lecturas** | 50.000 | **~56k**: el export (28.163) + el primer `firestore:delete diagnostics --recursive`, que escanea la colección entera antes de fallar |
| **Borrados** | 20.000 | el techo que habría hecho falta reventar 3 veces para terminar a mano |

⚠️ Mientras dura el 429, la app da error contra Firestore **en todos los móviles**. Pero agotar un
contador no toca los otros, y conviene mirar cuál se ha quemado antes de dar la app por caída.

### ⛔ Un 429 no es "Firestore caído": medido operación a operación (03-09, 13:41)

Tras los borrados masivos, con la base ya recreada, esto es lo que pasaba **a la vez** en el mismo
proyecto — por eso una sola prueba no vale para declarar el estado:

| Operación | Estado |
|---|---|
| `get` de un documento suelto | ✅ pasa |
| Aggregation `COUNT()` | ✅ pasa |
| **Escrituras** (`PATCH`/`update`) | ✅ pasan — el contador de escrituras seguía intacto |
| **`listDocuments` / `runQuery`** | 🔴 429 `RESOURCE_EXHAUSTED` |
| **`delete`** | 🔴 429 tras el primero |

📌 Consecuencia para el producto: **"plazas cercanas" es una QUERY**, así que con el contador de
lecturas de consulta agotado la app no lista spots aunque escriba sesiones sin problema. Un `get`
suelto que responde **no** demuestra que la app funcione. Comprobar la operación que hace la
pantalla, no la que tienes a mano.

📌 Consecuencia para la operación: con `delete` agotado y `update` vivo, apagar un flag escribiendo
`enabled: false` equivale a borrarlo (el gate es `?: false`) y **sí entra**. Así se apagaron los 12
mientras el contador de borrados seguía cerrado.

⚠️ **Subir a Blaze no suelta los contadores a la vez.** Con el plan ya cambiado, las lecturas y las
consultas volvieron de inmediato —una `listDocuments` que daba 429 pasó al minuto— pero los
**borrados siguieron en 429 varios minutos más**, y `firestore:delete --recursive` falló entretanto
con *"Failed to fetch documents to delete >= 3 times"*, que parece un problema de ruta y no lo es.
Reintentar: entran solos cuando el contador se libera.

## Gate remoto: apagado para TODOS, y se abre por uid bajo demanda (decisión del user, 03-09)

Los diagnósticos remotos están apagados por defecto sin tocar código: el gate es
`.get<Boolean?>(FIELD_ENABLED) ?: false` y arranca `null`, así que un usuario nuevo de Play —sin doc
en `diagnostics_config`— no emite nada remoto. El sink local de logcat sigue activo y no cuesta cuota.

El barrido del 30-08 que dejó los 13 en `enabled: true` **se revierte, y la colección se borra
entera**: sin usuarios en producción, 13 flags de una etapa que ya no existe no protegen nada, y sus
notas (qué uid era qué móvil) siguen en el zip del corpus. `diagnostics_config` se repuebla de una en
una, cuando haga falta. Ninguna cuenta emite telemetría remota.

**Cómo se abre a alguien concreto:** cuando un usuario reporta un problema de detección, su **uid
viaja en el propio informe** (`diagnostics_reports/{uid}`, [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]),
que además **trae ya el log local troceado** — o sea, hay diagnóstico útil *sin* encender nada. Si
hace falta seguirle en vivo, se le enciende su flag y se le apaga al cerrar el caso.

`set_diagnostics_flag.py` (scratchpad de la sesión) hace las tres cosas: `on <uid> "motivo"`,
`off <uid>` y `list`. Va por REST con el token OAuth del CLI, que es la vía admin: las reglas dicen
`create, update: if false` **para el cliente**, y el toggle nunca es del cliente.

⚠️ Encender a alguien tiene precio medido: `uiLocation` escribe **un doc por fix con throttle de
10 s** ≈ 360 docs/hora con el mapa abierto (`REMOTE_FIX_MIN_INTERVAL_MS = 10_000L`), más un doc por
paso del detector en `events`. Por eso "encendido a todos por si acaso" fue lo que llenó la base.

## Lo que esto destapa para el lanzamiento

Con los eventos contados, el volumen real era **~85.000 documentos con 11 testers**, no los 28k que
decía el manifest. Dos frentes distintos, y conviene no confundirlos:

1. **Plan** — ✅ **Blaze activado el 03-09**, antes de abrir el grifo. Se acabaron los techos
   diarios; estas operaciones cuestan céntimos. El problema de Spark nunca fue el precio.
2. **Diseño** — un doc por muestra (`uiLocation`) y un doc por paso del detector (`events`) es caro
   por construcción. Agrupar por sesión, o muestrear más fuerte, es una tarea de código propia que
   este doc NO abre; solo la señala con su medida.

## Dónde está el corpus

`~/Documents/paparcar-field-corpus-2026-09-03/` (33 MB, fuera del repo, que es público) y su copia
comprimida en `OneDrive\Documentos\paparcar-field-corpus-2026-09-03.zip` (2,67 MB, SHA256 de
`diagnostics.json` verificado idéntico). Contiene `users`, `spots`, `diagnostics_config` y —con la
salvedad de arriba— `sessions` + `uiLocation` de los 8 uids. **Sin `events`.**
