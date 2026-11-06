# Login y registro · issue #140

## Hallazgos

- BaseLogin controla los formularios y la navegación entre Login, Registro y recuperación de contraseña. Paparcar aporta los slots visuales en `PaparcarAuthSlots.kt`.
- El login bloqueaba el botón de email y todos los proveedores por una casilla local de privacidad. La casilla también condiciona la inicialización de la atribución publicitaria.
- Un primer acceso con Google puede crear el usuario de Firebase Auth. El arranque consulta `users/{uid}`: si existe, carga y reconcilia el perfil; si falta, lo crea en Firestore y Room. Por tanto, Auth y perfil no son el mismo estado.

## Decisión

- Mantener Login y Registro separados. BaseLogin ya proporciona la navegación y la recuperación de contraseña; añadir una bienvenida duplicaría una decisión que estas pantallas expresan.
- Dejar entrar a usuarios existentes por email o proveedor sin casilla. La información de privacidad aparece con enlace funcional antes de las opciones de proveedor, donde un primer acceso puede crear una cuenta.
- Mantener la confirmación explícita en el formulario de registro por email, incluso si este dispositivo la había guardado para otra cuenta. No se modifican el texto ni el destino de la política.
- No introducir un paso posterior a Google: Auth ya ha iniciado la sesión en ese momento, y el perfil se crea durante el arranque. Un paso cancelable entre ambos dejaría una cuenta Auth sin perfil. Sin aceptación explícita, la atribución publicitaria permanece desactivada.
- El imagotipo se muestra a 168 dp y forma un bloque de marca con el lema «Aparca. Recuerda. Comparte.». Tras 24 dp aparece el título de la tarea, con la escala de sección, junto al formulario. Así el logo no compite con «Inicia sesión» ni con «Crea tu cuenta». El fondo recibe un degradado verde tenue: en claro termina en `surfaceContainer`; en oscuro usa un punto intermedio entre `surfaceContainer` y `surfaceContainerLow` para conservar el tono azulado de la app con menos brillo. La nota de privacidad aparece antes de Google en login y no se repite junto a la casilla de registro. Los campos y botones conservan los componentes y estados de BaseLogin y Paparcar.

## Verificación

- `:shared:compileDebugKotlinAndroid`: correcto.
- `:app:assembleMockDebug` y `:app:assembleProdDebug`: correctos.
- Tests de `AppViewModelTest`, `PaparcarLoginConfigTest` y `LocaleParityGuardrailTest`: 39 correctos.
- Batería completa `:shared:testDebugUnitTest`: 3987 correctos y dos fallos en `HomeSlicesTest` sobre capas del mapa, sin relación con autenticación.
- La vista previa de Compose no pudo renderizarse en el IDE (`Missing ComposeViewAdapter`). Se revisaron login y registro en el dispositivo Android conectado. Con el teclado abierto, el formulario permite desplazar hasta el botón de registro; se añadió una protección fija de la barra de estado para el contenido que se desplaza por debajo.
- La casilla de registro se conserva porque forma parte del alta actual y también activa la marca local de atribución publicitaria. Separar la confirmación de información del consentimiento para atribución requiere una decisión de producto y revisión legal.
