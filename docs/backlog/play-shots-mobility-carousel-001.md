# PLAY-SHOTS-MOBILITY-CAROUSEL-001 · Capturas de publicación con composición dinámica y UI actual

**Estado:** ✅ Done · 72 capturas y descripciones guardadas en Play (09-11-2026) · rama `chore/PLAY-SHOTS-MOBILITY-CAROUSEL-001` · worktree `.worktrees/store-screenshots`

## Problema

El usuario pide renovar las capturas de la próxima publicación y mejorar su acabado con referencias profesionales del sector de movilidad. Alcance confirmado: ocho imágenes en los nueve idiomas (72 PNG). La tanda documentada del 25-09 todavía muestra Dublín; el mundo mock y la interfaz han cambiado desde entonces.

## Doctrina violada

No cambia negocio ni detección. Las capturas deben representar el producto que acompaña al lanzamiento, conservar la marca y localizar tanto titulares como UI. Una pantalla inventada no acredita una función.

## Señales / datos disponibles

- Base de trabajo: master `8619d087e`, tras rebase solicitado por el usuario; los cambios ajenos del árbol principal y otros worktrees se conservan.
- `:app:assembleMockDebug -PmockAsProd --console=plain` finalizó con código 0 desde el worktree.
- El usuario autorizó sustituir la instalación del Pixel 8; el build mock se instaló y la captura de Home confirmó mapa y escenario de Madrid.
- Home sustituyó badges sueltos por acciones «ver todas». `uiquery.py` resuelve esas acciones desde recursos por idioma; una prueba dirigida abrió plazas, gasolineras, parkings y detalle en español. La captura completa se realizó en los nueve idiomas.
- Se corrigieron dos nombres de lugares de Dublín en el fixture de plazas, y se recompiló e instaló el build. Los titulares 1 y 2 se revisaron para no garantizar detección automática en todos los casos.
- `ShotPreset`, `DevMainActivity` y `DevRoot.shotSession` ya permiten montar escenarios deterministas sin controles DEV ni parches temporales.
- `shoot.sh`: 11 raws por idioma, capturados a 1080×2400, anclas por UIAutomator.
- `screenshot-frame.html` y `render.sh`: ocho composiciones a 1080×1920 por idioma.
- Se consulta el flujo de `.claude/skills/nuevo-ticket/SKILL.md` como referencia; no se ejecuta su cierre.

## Diseño

1. Contrastar referencias públicas actuales de movilidad y fijar una dirección visual propia.
2. Capturar y revisar primero español en Pixel 8, `mockDebug -PmockAsProd`, tema oscuro y barra de estado limpia.
3. Mejorar jerarquía, escala de las pantallas y ritmo del carrusel con composiciones reproducibles en HTML/CSS. Los detalles ampliados proceden del mismo raw.
4. Capturar los otros ocho idiomas, componer las 72 imágenes y entregar una galería local revisable y un paquete por idiomas.
5. Mantener generación, archivos derivados y fuentes separados.
6. Ampliación autorizada por el usuario el 09-11-2026: actualizar capturas y descripción completa en Google Play en los nueve idiomas. Validar y verificar una única edición de ficha; conservar títulos, descripciones breves, binarios y canales.

## Criterio de éxito

- 72 PNG a 1080×1920; idioma del titular y de la UI coincidentes.
- Madrid y datos ficticios coherentes; mapas cargados, sin diálogos de sistema ni herramientas DEV.
- Textos legibles en todos los idiomas; fuentes, imágenes y ampliaciones cargadas.
- Escáner de raws y frames y revisión visual de cada composición.
- Fuentes, comandos, limitaciones y ubicación de la entrega documentados.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `play-listing/tools/shoot.sh`, `uiquery.py` | navegación y anclas adaptadas a la UI actual |
| `play-listing/assets/screenshot-frame.html` | composición y copy localizados |
| `play-listing/tools/render.sh`, `scan.py` | generación y revisión |
| `play-listing/README.md` | actualizar instrucciones de reproducción |
| `mockkit`, app mock | escenarios existentes; cambios sólo si los requiere la captura |
| Ficha publicada | actualización autorizada: 72 capturas y descripción completa en nueve idiomas |
| Web, binarios, canales, reglas Firebase | fuera de alcance: no cambia producto ni se despliega |

## Referencias y validación

- Referencias públicas: fichas de [EasyPark](https://play.google.com/store/apps/details?hl=es&id=net.easypark.android) y [Waze](https://play.google.com/store/apps/details?hl=es&id=com.waze); se conserva la identidad y las capturas reales de Paparcar.
- `:app:assembleMockDebug -PmockAsProd --console=plain`: código 0, con los fixtures corregidos; instalación en `emulator-5554` satisfactoria.
- El Pixel 8 capturó los 11 raws por idioma; `scan.py raws` no detectó problemas en los nueve idiomas. Los 72 montajes se revisaron en hojas de contacto y el recorte de precios se corrigió para conservar las etiquetas largas.
- `scan.py frames docs/release/play-listing/assets`: 0 problemas en 9 idiomas tras el render. La galería local está en `assets/review.html`.
- Una captura en inglés reveló las pestañas en español y los recursos rumanos en polaco; se corrigieron y se compilaron `:app:assembleMockDebug` y `:app:assembleProdDebug`.
- `testAll --rerun-tasks --console=plain`: 4.056 tests, 3 fallos ya documentados en la base en `home-live-address-readable-001.md` (dos de `HomeSlicesTest` y el recurso Android `notif_bt_saved_text` sin consumidor). Los guardarraíles de anclas, escapes e índice de backlog pasaron.
- El timestamp general del fixture de combustible ahora usa la misma edad relativa que los precios; se recompiló mock y se repitieron los nueve raws de combustible. Se revisaron visualmente los montajes de los nueve idiomas y se corrigió la separación tipográfica de los titulares.
- `:mockkit:compileKotlinIosSimulatorArm64 --console=plain`: compilación ejecutada correctamente; no equivale a enlazar ni probar iOS en Mac. El grafo mock Android pasó sus 2 pruebas.
- Entrega local: [galería](../release/play-listing/assets/review.html) y `assets/screenshots/<idioma>/` (ocho PNG por idioma). Los PNG son derivados ignorados por Git; los raws y anclas están en `build/store-raw/`, que puede borrarse al limpiar Gradle. La actualización de la ficha se guardó posteriormente según el registro inferior.
- Git Bash y Chrome funcionan desde el terminal del IDE mediante un proceso Node intermedio; se mantiene Bash como ejecutor de los scripts del proyecto.

## Actualización de Play autorizada · 09-11-2026

El usuario autorizó guardar las capturas y revisar las descripciones. La consulta remota confirmó
producción e interna en vc16, nueve idiomas y ocho capturas previas por idioma. La descripción
completa remota todavía llevaba copy anterior de Plus aunque los nombres y breves coincidían.

Se revisó `LISTING-v1.5.0.md` y se regeneraron los diez pegables desde su generador: detección
condicionada, disponibilidad de plazas y precios, gasto estimado, sincronización de rutas
compartidas y reparto de avisos Free/Plus. Largos de las descripciones completas:

| en-US | es-ES | it-IT | pt-PT | fr-FR | de-DE | nl-NL | pl-PL | ro |
|---|---|---|---|---|---|---|---|---|
| 3744 | 3918 | 3938 | 3898 | 3956 | 3987 | 3982 | 3851 | 3894 |

`tools/release/store_listing.mjs --apply` consiguió preparar las nueve descripciones y las
72 imágenes, y comprobar huellas, orden y ausencia de cambios en canales. Sin embargo,
`edits.validate` devolvió **403 The caller does not have permission**. No se llamó a commit;
la edición se descartó. En ese primer intento no se actualizó la ficha. La comprobación posterior
`--check-permissions` volvió a devolver 403. Se pidió al usuario revisar «Administrar presencia
en Google Play» para la cuenta de servicio y sólo para Paparcar; no se cambió ningún permiso
ni credencial. [Permiso de presencia en tienda](https://developers.google.com/android-publisher/api-ref/rest/v3/grants).

El usuario confirmó después la corrección del permiso. `--check-permissions` ha terminado con
código 0 y `Store listing validation permission confirmed.` Se repitió `--apply` con el mismo
baseline y plan: **código 0**. Google aceptó la validación y el commit de la edición. Una lectura
desde una edición nueva confirmó **72 capturas, su orden y huellas, y las nueve descripciones
completas**, con títulos, breves y canales sin cambios. No se subió ningún binario.
Los recibos están en `build/store-publish/validated.json`, `commit.json` y `after.json`.
Este guardado no acredita que Google haya concluido su revisión o la propagación pública.

El comando incorpora ahora esa comprobación antes de subir imágenes y evita cancelar revisiones
ajenas mediante `changesInReviewBehavior=ERROR_IF_IN_REVIEW`. Al reanudar, comprobar permisos y
repetir `--apply` con el baseline preservado en `build/store-publish/`. Si el estado remoto o las
fuentes locales cambian, se detiene para revisar el nuevo plan.

Validación local de esta ampliación:
- `make_paste.py`: regeneración de 10 ficheros; `PlayListingPasteParityGuardrailTest` ejecutó 4 tests sin fallos con `--rerun-tasks` (61 tareas ejecutadas).
- `node --experimental-vm-modules --test tools/release/store_listing.test.mjs`: 4 tests sin fallos; inspección sin publicación, guardado completo con verificación posterior, rechazo por permisos antes de subir y rechazo por huella incorrecta. Simula API y credencial, sin red ni secretos.
- `before.json`, `plan.json` y `failure.json` en `build/store-publish/`: evidencia local ignorada por Git; no contienen credenciales ni tokens.

