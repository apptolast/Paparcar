# COPY-THE-SERVICE-NOTIFICATION-IS-ONE-LINE-001 · La notificación de servicio dice una cosa, en una línea

**Estado:** ✅ Done — en master por squash. Master avanzó **dos veces** durante la tarea; la rama se
rebasó sin conflictos y la suite se repasó después de cada movimiento, incluida una última pasada ya
sobre master fusionado. ⏳ **Sin ver en device**: falta comprobar
que la fila colapsada es realmente UNA línea en el Oppo y en el Redmi (ver la advertencia del
`BigTextStyle` en *Criterio de éxito*).

## Problema

Las dos notificaciones *ongoing* de la app —la de detección activa y la del sentry— gastan **dos
líneas de la bandeja para decir una sola cosa**, y la segunda repite la primera:

| | `contentTitle` | `contentText` |
|---|---|---|
| detección (ES) | Detección de aparcamiento activa | Atento a tu %1$s · toca para gestionar |
| sentry (ES) | Coche aparcado — atentos a tu salida | Cuando te vayas, liberaremos tu plaza… |

Tres cosas sobran a la vez:

1. **El header ya dice quién habla.** Android pinta "Paparcar" encima de cada notificación. El
   título no tiene que presentarse ("Detección de aparcamiento activa" es el nombre del canal, no
   una noticia): el usuario ya sabe qué app es y qué hace.
2. **`contentText` repite el título.** *"Detección de aparcamiento activa"* + *"Detectaré cuándo
   aparcas"* son la misma frase dos veces, y gastan la línea donde cabría lo único que el usuario
   no sabe: **qué coche** se está vigilando.
3. **"toca para gestionar" es plumbing.** Tocar una notificación abre la app en todas las apps de
   Android; decirlo consume 20 caracteres de una línea de ~35.

En el sentry el desperdicio es peor, porque es la notificación que el usuario ve **durante horas**
mientras el coche está aparcado: es la más silenciosa (canal MIN) y a la vez la más ancha.

## Doctrina violada

- **Copy sin mecánica interna, causa + consecuencia + remedio** — *"toca para gestionar"* no es
  ninguna de las tres: es una instrucción sobre el sistema operativo, no sobre el aparcamiento.
- **`notif_detection_title` no nombra nada real.** La regla editorial del proyecto es que un
  nombre es de una cosa (esta plaza, este coche); "Detección de aparcamiento activa" nombra un
  subsistema. El dato con valor es el **coche**, y hoy vive en la línea secundaria.

No hay ninguna doctrina que exija dos líneas, y —comprobado abajo— tampoco la exige nadie fuera.

## Qué exigen Android y Google Play (comprobado antes de acortar)

Esto se verificó primero porque acortar el copy de un foreground service parece territorio de
política, y no lo es:

- **Android**: una notificación de FGS sólo exige **canal** + **`smallIcon`**. Título y texto son
  libres — no hay longitud mínima, ni frase obligatoria, ni obligación de mencionar la ubicación
  (el sistema ya pinta su propio indicador de ubicación y la fila de uso en segundo plano).
- Desde **Android 13** el usuario puede descartar la notificación de un FGS y el servicio sigue
  vivo; desde **Android 12** existe además el *Foreground Services task manager* con su botón
  **Detener**. O sea: esta notificación **no es el interruptor de apagado** de nadie, y no tiene que
  comportarse como tal. Nuestro botón "Parar detección" se queda porque es un atajo bueno, no
  porque haga falta para cumplir nada.
- **Google Play** no dicta el copy. Lo que sí exige, y **este ticket no toca**, es: el formulario de
  *Permisos de servicios en primer plano* (targetSdk ≥ 34; nosotros 37) con vídeo, el formulario de
  *background location* con su prominent disclosure in-app, y que la notificación **no mienta**
  (Deceptive behavior). Decir la verdad en cuatro palabras la cumple igual que decirla en dos
  líneas.

→ Riesgo de política al acortar: **ninguno**. Queda registrado aquí para no re-litigarlo.

## Diseño

**El invariante: una notificación ongoing dice UNA cosa en UNA línea, y el porqué vive en el
expandido.** La línea colapsada es el `contentTitle` y nada más; `contentText` se queda vacío; el
explicativo largo (qué hace, batería, cómo apagarlo) se mantiene íntegro en el `BigTextStyle`, que
no cuesta ni un píxel mientras la notificación está colapsada.

Dónde vive el invariante: **un único constructor privado**
`buildOngoingNotification(channelId, line, explainer)` en `AppNotificationManagerImpl`, por el que
pasan las dos notificaciones ongoing. No es cosmética: hoy son dos builders gemelos copiados, y una
tercera notificación ongoing volvería a nacer con dos líneas por imitación. Con el helper, el call
site **no tiene dónde poner un `contentText`** — la prohibición la sostiene la firma, no un test que
hay que acordarse de escribir.

Devuelve el `Builder`, no la `Notification`: cada llamante conserva lo que sí es suyo (el botón
"Parar detección" de la detección, el `PRIORITY_MIN` + `setSilent` del sentry). Lo compartido —
canal, icono, `CATEGORY_SERVICE`, intent de apertura, `ongoing`, `showWhen`, color— deja de estar
duplicado de paso.

Por eso este ticket **no añade guardarraíl**: un test de prohibición aquí sería un segundo dueño de
un invariante que la firma ya hace inexpresable, y el proyecto ya pagó el precio de los tests de
prohibición que nunca se ven fallar.

### Copy

La línea nueva contesta la única pregunta que el usuario no puede responder solo: **qué está
vigilando la app ahora mismo**.

| | antes (2 líneas) | ahora (1 línea) |
|---|---|---|
| detección, con coche | Detección de aparcamiento activa / Atento a tu Kamiq · toca para gestionar | **Atento a tu Kamiq** |
| detección, sin coche | Detección de aparcamiento activa / Detectaré cuándo aparcas · toca para gestionar | **Atento a tu coche** |
| sentry | Coche aparcado — atentos a tu salida / Cuando te vayas, liberaremos tu plaza… | **Aparcado · atento a tu salida** |

Mismo verbo en las dos ("atento a…"), distinto objeto: conduciendo se vigila **el coche**, aparcado
se vigila **la salida**. Es la misma app diciendo lo mismo en dos momentos, no dos voces.

### Movimiento de keys (`app/src/main/res`, × 9 locales)

| key | qué le pasa |
|---|---|
| `notif_detection_title` | **reescrita** — pasa de nombrar el subsistema a nombrar el coche |
| `notif_detection_title_vehicle` | **nueva** — la variante con `%1$s`, que antes vivía en el texto |
| `notif_detection_text` | **borrada** — su contenido era el título dicho otra vez |
| `notif_detection_text_vehicle` | **borrada** — su contenido pasa a `notif_detection_title_vehicle` |
| `notif_sentry_title` | **reescrita** — más corta, mismo hecho |
| `notif_detection_explainer` | intacta — sigue siendo el `BigText` de la detección |
| `notif_sentry_text` | intacta — deja de ser `contentText` y se queda sólo como `BigText` |

⚠️ Aquí el apóstrofo **sí** se escapa (`\'`): son recursos Android, no Compose Resources
—[COPY-APOSTROPHE-IS-NOT-ESCAPED-001] aplica sólo a los segundos—. El EN de `notif_sentry_title`
llevaba uno (`we\'re`) y el copy nuevo no lo necesita.

Presupuesto de longitud: **≤ 36 caracteres**, medido en el idioma más largo de cada frase (DE y PL
son los que se pasan, nunca ES). Una fila colapsada corta por ahí en un móvil normal.

## Señales / datos disponibles

`LocaleParityGuardrailTest` cubre **las dos superficies** de strings, incluida `app/src/main/res`, y
tiene el test *«every declared string is read by something»*: borrar `notif_detection_text*` del
código sin borrarlas de los 9 ficheros **pone la suite en rojo**. No hace falta confiar en el grep.

## Verificado

- `:shared:testDebugUnitTest` → **1.999 tests, 0 fallos** (176 clases), y
  `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` verdes.
- **Falsificado el guardarraíl que sostiene el borrado**, para no dar por bueno un verde que sólo
  significa "no miró": con `notif_detection_text` reinsertada en los 9 ficheros (para no disparar de
  rebote los tests de paridad), la suite se pone en rojo por *«every declared string is read by
  something»* — y sólo por ese test, 1 de 5. Es decir: si me hubiera dejado la key muerta en los 9
  locales, el build lo habría dicho. Revertido después.
- `grep -rn "notif_detection_text" app/ shared/` → 0 resultados.
- Codificación UTF-8 de los 9 ficheros releída tras editar (`mașina`, `Obserwuję`, `Garée`,
  `à votre départ` intactos) — [MAP-TYPES-001] ya quemó una vez con un reemplazo masivo que
  corrompió acentos.

## Criterio de éxito

- `:shared:testDebugUnitTest` verde — en particular las 5 pruebas de `LocaleParityGuardrailTest`
  (paridad en las dos direcciones, 9 carpetas, plurales, y **key muerta**).
- `grep -rn "notif_detection_text" app/ shared/` no devuelve nada.
- En device (Oppo + Redmi): la notificación de detección y la del sentry ocupan **una sola línea**
  colapsadas, y al expandir siguen mostrando el explicativo entero.

⚠️ Lo que hay que **medir**, no suponer: con `contentText` vacío y `BigTextStyle` puesto, la vista
colapsada debería mostrar sólo el título (AOSP hace la sustitución al revés: si el `bigText` está
vacío, cae al `text`). Si alguna capa OEM sube la primera línea del `bigText` a la colapsada, la
"una línea" se convierte en dos y hay que quitar el `BigTextStyle` de esa notificación. Se ve en el
primer `/run`.

## Consumidores auditados

`notif_detection_title` · `notif_detection_text` · `notif_detection_text_vehicle` ·
`notif_detection_explainer` · `notif_sentry_title` · `notif_sentry_text` — grep sobre todo el repo:

- `app/.../notification/AppNotificationManagerImpl.kt` → **el único call site de las 6**. Cerrado.
- `app/src/main/res/values*/strings.xml` (9) → declaración. Cerrado.
- `docs/backlog/det-resident-fgs-001.md`, `docs/backlog/copy-notification-layer-still-says-plaza-001.md`
  → sólo prosa. Ver nota abajo.
- Dev Catalog: no aplica — no hay pantalla, estado ni routing nuevo; las notificaciones no tienen
  entrada en la galería de estados.

### Solape con COPY-NOTIFICATION-LAYER-STILL-SAYS-PLAZA-001 (abierto)

Ese ticket lista `notif_detection_explainer` y `notif_sentry_text` como **usos correctos** de
"spot" que se quedan como están, y sus 4 keys (`notif_first_park_nudge_action`,
`notif_mark_parking_action`, `notif_mark_parking_text`, `notif_confirmation_failed_text`) **no las
toca este ticket**. Los dos pueden cerrarse en cualquier orden; el único fichero compartido es
`strings.xml` y no coinciden en ninguna línea.
