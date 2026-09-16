# COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001 · la regla del apóstrofo se volvió a romper porque nada la vigilaba

**Estado:** ✅ Done
**Sale de:** `IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001` (16-09) y de que el user dijera «pensaba que
esto estaba arreglado». Lo estaba: `4ae0cabe` barrió 87 y dejó cero. **Volvió a entrar porque aquel
ticket no dejó guard.**

## ⚠️ Corrección de una medición anterior

Al cerrar el ticket de push reporté **247 cadenas afectadas en las dos plataformas**. Era falso, y la
causa merece quedar escrita porque es reutilizable: **el patrón viajaba dentro de un heredoc y perdía
un nivel de escape**, así que el script acabó buscando un apóstrofo cualquiera en vez de
barra-invertida + apóstrofo, y contó casi todas las cadenas con apóstrofo del repo. Una segunda
medición, con el backslash construido como `chr(92)` —sin escribir ni un escape en el código— da el
número real.

Y una segunda trampa en el mismo sitio: el script leía revisiones con `git show` **sin mirar el
código de salida**. Cuando el path no existía en esa revisión (antes del split `:shared`/`:app`,
ARCH-HEALTH F7, los recursos vivían en `composeApp/`), `git show` fallaba, devolvía vacío, y el
recuento salía **0**. Un cero de «no he podido mirar» es indistinguible de un cero de «está limpio»
si nadie comprueba. [project_test_a_green_suite_must_prove_it_looked_001]

El script correcto queda en el ticket: `tools/` no, porque es de un solo uso; el guard que sale de
aquí es la versión que se queda.

## Problema — medido

**Seis ocurrencias de `\'` en `shared/src/commonMain/composeResources`**, todas en claves `notif_*`:

| locale | ocurrencias | claves |
|---|---|---|
| `values` (EN) | 3 | `notif_action_no_not_parked`, `notif_parking_saved_confirm_text`, `notif_action_ive_left` |
| `values-it` | 2 | `notif_still_parked_text` (dos en la misma línea: `dov\'è l\'auto`) |
| `values-fr` | 1 | `notif_parking_saved_confirm_text` |
| los otros seis | 0 | — |

Compose Resources **no desescapa `\'`**, así que el usuario lee la barra: *«I\'ve left»*, *«ce n\'était
pas votre véhicule»*.

⛔ **Alcance real: la superficie de notificaciones de iOS, y solo ella.** Las `notif_*` de
`composeResources` las lee `IosAppNotificationManagerImpl` y nadie más; Android lee sus notificaciones
de `app/src/main/res`. Mi informe anterior decía «en las dos plataformas, en la app publicada» — no es
cierto, y el número tampoco lo era.

⛔ **`app/src/main/res` tiene 33 `\'` y están BIEN.** Ahí la regla es la contraria: un `'` crudo **no
compila**. Los dos árboles no se barren con el mismo comando, y el guard no puede mirarlos igual.

## Doctrina violada

`COPY-APOSTROPHE-IS-NOT-ESCAPED-001`, otra vez. Y la de segundo orden, que es la que de verdad falló:
**una regla sin guard es una regla que se vuelve a romper.** Entre `4ae0cabe` y hoy la regla estuvo
escrita en `CLAUDE.md` y en un doc de backlog, y aun así reentró.

## Por dónde reentró

Las seis vienen del **espejo de notificaciones** que abrió
`IOS-NOTIF-A-SURFACE-THAT-SPEAKS-ONE-LANGUAGE-001` (`d23417b2`): copió las `notif_*` desde
`app/src/main/res` —donde el escape es obligatorio— a `composeResources` —donde está prohibido— sin
desescapar.

⛔ Y `NotificationCopyParityTest`, que existe justo para vigilar ese espejo, **no puede verlo**:
normaliza `\'` → `'` en los DOS lados antes de comparar, porque para *su* pregunta (¿dice lo mismo?)
esa diferencia es legítima. Correcto para lo suyo, ciego para esto. Dos guards, dos preguntas.

## Diseño

1. **Barrido de las 6** → apóstrofo crudo, solo en `composeResources`.
2. **`ComposeResourcesEscapeGuardrailTest`** (`androidUnitTest/.../i18n/`): cero `\'` en los nueve
   `composeResources/values*/strings.xml`, con su **testigo de población** (que los nueve ficheros
   existan y tengan cadenas) para que un árbol vacío o un path mal escrito no pase por limpio — que
   es exactamente el fallo de medición de arriba, convertido en test.
   - Cubre también `%%`, de `COPY-PERCENT-IS-NOT-ESCAPED-001`: misma familia, misma causa (esto no es
     `android:strings` ni `String.format`) y mismo árbol. Hoy está a cero; el guard es lo que lo
     mantiene ahí.
   - ⛔ **No mira `app/src/main/res`**: ahí la regla es la inversa y el compilador de Android ya la
     impone — un `'` crudo no compila. Duplicarla aquí sería escribir la regla contraria dos veces.
3. El testigo de que el guard MIRA: se falsifica a mano reintroduciendo un `\'` y comprobando que
   pone rojo. ⚠️ Un cambio de recursos sí invalida la tarea de test, así que no hace falta
   `--rerun-tasks` (eso es para los Konsist sobre imports).

## Criterio de éxito — medido

- ✅ **El guard en ROJO antes del barrido**, nombrando las cinco líneas exactas (EN 3, IT 1 línea con
  dos, FR 1). Escrito ANTES de tocar los recursos, para verlo fallar.
- ✅ **En VERDE tras el barrido**: 6 ocurrencias sustituidas, diff de 5 líneas en 3 ficheros.
- ✅ **Falsificado**: reintroducido un `\'` en `notif_action_ive_left` → rojo otra vez; revertido →
  verde. El guard MIRA, no pasa por verde por no encontrar el árbol.
- ✅ `app/src/main/res` sigue con sus **33** `\'` intactos: el barrido no lo tocó, y ahí están bien.
- ✅ `NotificationCopyParityTest` verde: normaliza, así que el barrido no le cambia la respuesta.
- ✅ `./gradlew testAll` verde.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `composeResources/values*/strings.xml` (9) | ✅ barridos, cero `\'` |
| `app/src/main/res` (9) | ⛔ exento por regla contraria, documentado |
| `NotificationCopyParityTest` | ✅ intacto: su normalización es correcta para su pregunta |
| `CLAUDE.md` § apóstrofo | ✅ la regla ya estaba escrita; lo que faltaba era el guard, y ahora se nombra |
| `docs/backlog/copy-247-strings-still-print-a-backslash-001.md` | ✅ **borrado**: su número y su alcance eran falsos |
| Memoria | ✅ corregida la entrada del ticket de push |
