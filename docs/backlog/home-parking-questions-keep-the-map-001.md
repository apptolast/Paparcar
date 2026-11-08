# HOME-PARKING-QUESTIONS-KEEP-THE-MAP-001 · Preguntas de aparcamiento con contexto y sin exigir interacción al conducir

**Estado:** ✅ Done — integrado en master mediante squash (2026-11-08).

## Problema
Las capturas aportadas muestran un diálogo central que oculta el aparcamiento sobre el que pregunta, respuestas equivalentes con jerarquía visual arbitraria y un botón «Corregir detección» que duplica el editor de ubicación. Las preguntas ofrecen «Estoy/Sigo conduciendo», aunque el flujo automático debe funcionar sin atender el móvil durante el trayecto.

## Doctrina violada
El contexto del mapa debe seguir visible al confirmar un lugar. El silencio no es una confirmación humana ni una declaración de conducción. Corrección de pin, nuevo aparcamiento y retirada de una detección falsa tienen consecuencias distintas. No publicar una plaza por una declaración de salida cuya frescura se desconoce.

## Señales / datos disponibles
Sesiones activas por vehículo/geofence, pregunta durable StillParkedQuestion, ventana de confirmación, editor AddingParking, corrección CONTINUE_TRIP/WRONG_CAR y contratos de salida existentes.

## Diseño vigente · revisión de la card
Las dos preguntas viven en la card principal del vehículo, con estados distintos:
- «¿Has aparcado aquí?» valida un candidato nuevo. Sí confirma el candidato; No lo rechaza y mantiene el seguimiento conforme al contrato existente.
- «¿El coche sigue aparcado aquí?» revisa un aparcamiento guardado. Sí ratifica la sesión sin mover el pin ni reiniciar el tiempo. «No» abre el editor existente de ESA sesión y ofrece corregir la ubicación o registrar un aparcamiento nuevo.
- Abrir/cancelar el editor no cierra la sesión, no publica plaza, no arma un trayecto ni resuelve la pregunta. Solo guardar correctamente termina esa revisión.
- Se retiran la hoja nueva, «Confirmar aparcamiento» y el aplazamiento específico de aquella hoja. La pregunta durable permanece en la card mientras no se responda; se puede seguir usando el mapa.
- Se conserva el editor y sus recuperaciones excepcionales. Las preguntas se vinculan por geofence/sesión y momento; una acción vieja no puede editar otro aparcamiento.
- App, notificaciones Android/iOS, recursos de los nueve idiomas, catálogo/fakes y previews deben respetar la misma semántica. Un No de permanencia nunca se trata como salida confirmada.

## Criterio de éxito
- La card del coche afectado muestra la pregunta correcta y queda visible también con varios coches.
- Los dos momentos conservan callbacks, identidad y consecuencias independientes.
- Sí de permanencia mantiene la sesión, ubicación y fecha de nacimiento.
- «No» abre el editor del aparcamiento guardado, sin borrar datos ni usar el GPS del teléfono como coche.
- Cancelar conserva el aparcamiento y la duda; corregir mantiene el tiempo; nuevo aparcamiento crea una sesión nueva.
- No aparece una hoja adicional de pregunta ni un botón para entrar en ella.
- Notificaciones, errores, acciones antiguas y aperturas en frío conservan los mismos contratos.
- Validación proporcional de pruebas, prod/mock y catálogo desde el worktree.

## Consumidores auditados
- **Home y cámara:** preguntas en la card del vehículo, con proyección y callbacks diferentes. El sujeto se identifica por sesión/geofence y se eleva mediante la ordenación existente. Tocar la card encuadra el pin sin contestar; no hay una nueva hoja de pregunta.
- **Edición/corrección:** No de permanencia abre AddingParking en el pin guardado. Corregir conserva sesión/tiempo; nuevo crea otra para el mismo coche. Cancelar o fallar conserva datos y pregunta. Las recuperaciones excepcionales conservan el borrador y bloquean Atrás durante la escritura.
- **Dominio:** ConfirmStillParkedUseCase ratifica únicamente la pregunta vigente. Retira una plaza provisional antes de reconocer Sí; propaga cancelación y deja reintentar fallos. ConfirmParking no borra dudas de otro coche.
- **Android:** Sí lleva identidad/época al caso de uso común; «No» abre directamente MainActivity y el editor mediante el bus, también en frío. Las acciones negativas antiguas solo vuelven a mostrar la revisión vigente; no cierran ni publican.
- **iOS:** pregunta durable, sello en payload y las mismas rutas Sí/No. Se retira la antigua interpretación de No como salida atestiguada. La safety net respeta el Sí con la misma función común de Android hasta nueva evidencia de embarque/salida; el prompt toma el pin de la sesión, no el del teléfono. Una publicación asíncrona comprueba que la pregunta siga vigente tras cargar recursos.
- **Copy:** nueve idiomas compartidos y Android. Respuestas equivalentes con fondo primary; negativas sobre el aparcamiento y arranque voluntario «Iniciar trayecto». Eliminadas las claves que quedan sin consumidor.
- **Desarrollo:** previews de llegada/permanencia, claro/oscuro y segundo coche; galería, preset y fake observable permiten responder sin que el escenario vuelva a crear la pregunta.
- **Detector:** sin cambios de scoring, umbrales, nominación BT, política de timeout o provenance. No responder no equivale a confirmación humana; la evidencia medida conserva sus vías automáticas.

## Validación de la revisión de la card
- Pruebas dirigidas: `:shared:testDebugUnitTest --tests *HomeViewModelTest --tests *StillParked* --tests *ConfirmParkingUseCaseTest` → **276 correctas, 0 fallos**.
- Pasada forzada: `bash ./gradlew testAll :app:compileProdDebugKotlin :app:assembleMockDebug --continue --rerun-tasks --console=plain` → **4051 correctas / 5 fallos previos en shared; 4 correctas / 0 fallos en app/mock**. Los XML confirman las mismas tres causas de la comparación con la base: estado de auth-login-redesign-140, notif_bt_saved_text sin uso y dos expectativas de capas en HomeSlicesTest.
- Prod debug y APK mock compilados; `:shared:compileKotlinIosSimulatorArm64` se ejecutó sin fallo en esta pasada. No se afirma enlace/ejecución nativa iOS en un Mac.
- Recompilado `:shared:compileKotlinIosSimulatorArm64` tras conectar el guard de respuesta humana y la publicación asíncrona: correcto. La regla común `stillParkedQuestionMayRepeat` ya está cubierta por DetectionQuestionFlowTest en la pasada agregada.
- Auditoría de consumidores: sin usos de la hoja retirada, enum/respuestas antiguas, intents de aplazamiento ni recursos huérfanos de esta revisión; sin consumidores Swift de la API retirada.
- Última pasada de Home tras reforzar la prueba de GPS lejano: `:shared:testDebugUnitTest --tests *HomeViewModelTest` → **191 correctas, 0 fallos**. La comprobación previa de DetectionQuestionFlowTest y guards de Koin/mock → **8 correctas, 0 fallos**.
- `git diff --check`: correcto tras los últimos cambios.
- La variante mock separada se instaló y se lanzó con Status ok. La inspección del dispositivo mostró keyguard y pantalla bloqueada en dos comprobaciones; se solicitó desbloqueo. **Pendiente revisión visual y evidencia final de las cards**. No se han pulsado respuestas ni alterado aparcamientos reales.
- Release vigente actualizada y verificada en el Oppo; detalle al final de este ticket.

## Validación anterior a la revisión de la card
- Compilación prod y APK mock: correctos mediante `studio_gradle_build -p <worktree> :app:compileProdDebugKotlin :app:assembleMockDebug --console=plain`.
- Primer testAll: 4023 correctos / 7 fallos en shared; 4 correctos en el grafo mock. Detectó dos ajustes de etiquetas de esta tarea y claves huérfanas, corregidos.
- Comparación con checkout limpio de `f84ade96a` en un worktree temporal: reproducidos **cinco fallos previos**. Dos de BacklogStatusGuardrailTest por falta de estado en `auth-login-redesign-140.md`; uno de LocaleParityGuardrailTest por `notif_bt_saved_text` Android sin uso; dos de HomeSlicesTest por expectativas de capas del mapa. Se conservan como incidencias ajenas a esta tarea.
- Pasada forzada con `testAll :app:compileProdDebugKotlin :app:assembleMockDebug --continue --rerun-tasks --console=plain`: 4027 correctos y solo los cinco fallos previos en shared; 4 correctos en app/mock. Tras añadir la regresión de borrador del editor, repetido el mismo agregado con `--continue --console=plain`: **4028 correctos / 5 fallos previos en shared y 4 correctos / 0 fallos en app/mock**. Prod y APK mock compilan; no se afirma que la suite completa esté verde. El test nuevo del borrador está incluido en el XML ejecutado.
- `git diff --check`: correcto. El worktree temporal de comparación se eliminó tras registrar los resultados.
- Preview del nuevo contenido: el MCP del IDE devuelve `Failed to get module` para el archivo del worktree. El APK mock sí compila. La consulta `adb devices -l` tampoco devolvió resultado (timeout del MCP a los 300 s); no se instaló ni lanzó la app en dispositivos. Pendiente evidencia visual final desde un IDE que tenga abierto este worktree o un dispositivo de pruebas.
- Sin commits, pushes, merges ni publicación. El árbol principal y las modificaciones documentales ajenas se conservan.

## Ejecución anterior a la revisión de la card · prod release en Oppo · 2026-11-08
Solicitada expresamente por el usuario, solo en el Oppo (CPH2371).
- Fuente: base `f84ade96a` más los cambios sin commit de este worktree.
- Build: `bash ./gradlew :app:assembleProdRelease -x :app:uploadCrashlyticsMappingFileProdRelease --console=plain`, correcto (1 min 41 s). Se reutilizó la configuración local de firma mediante enlaces ignorados, sin mostrar ni modificar sus contenidos; no se subieron mappings.
- APK: `app/build/outputs/apk/prod/release/app-prod-release.apk`, versión 1.5.0 (16).
- Actualización con `adb install -r`: Success. No se desinstaló ni se borraron datos.
- SHA256 del APK local e instalado: `987ad8a5fbe009ae4930b2d9b7ccdeffdb4885518b4528f537c898754eb9eb44`, coincidencia comprobada antes del arranque.
- `am start -W` devolvió Status ok; MainActivity quedó como topResumedActivity, el proceso siguió activo y AndroidRuntime:E no mostró errores para ese proceso.
- Esta comprobación acredita instalación y arranque; no responde preguntas ni modifica manualmente aparcamientos reales, y no sustituye la revisión visual de cada estado de la hoja.

## Release vigente · revisión de la card · Oppo · 2026-11-08
- Fuente: este worktree sobre `f84ade96a`, sin commits ni integración.
- Build final: `:app:assembleProdRelease -x :app:uploadCrashlyticsMappingFileProdRelease --console=plain`, correcto mediante Gradle del IDE junto con las ocho comprobaciones dirigidas. No se subieron mappings.
- APK: `app/build/outputs/apk/prod/release/app-prod-release.apk`; paquete prod, versión **1.5.0 (16)**.
- SHA256 local e instalado: `0ab54cb1b5009fdb114baa2df3b78151c615efc025970fb33d661c66af1c7f47`, coincidencia comprobada después de `adb install -r` (Success).
- `am start -W`: Status ok para MainActivity; proceso activo y AndroidRuntime:E vacío para ese proceso. El Oppo continúa bloqueado: acredita instalación y arranque, **no** revisión visual de los estados.
- Mock permanece como paquete separado `com.rndeveloper.paparcar.mock`. No se desinstaló ninguna variante, no se borraron datos y no se cambiaron ajustes de bloqueo.
