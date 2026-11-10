# MCP y conexiones para Paparcar
**Actualizado el 2026-11-08**, HEAD local `f84ade96a`. Esta guía distingue configuración, autenticación, autorización al recurso y herramienta operativa. No contiene tokens. Revalidar cada sesión.

## Qué existe hoy
| Conexión | Hallazgo | Estado |
|---|---|---|
| Android Studio | Herramientas `studio_*` de búsqueda, buffers, shell, Gradle y dispositivo visibles; lectura/escritura/shell usados | Operativa en esta sesión; no configura otros clientes Codex |
| GitHub | Plugin Codex autenticado en `rndevelo`, instalado solo en la cuenta personal; `get_repo(apptolast/Paparcar)` devuelve 404. `gh repo view apptolast/Paparcar` sí accede al privado | Acceso **por CLI verificado**; MCP del plugin pendiente de instalación/autorización en la organización `apptolast` |
| Firebase de Claude | `.mcp.json` declara `firebase` por `npx -y firebase-tools@latest mcp`; ajustes de Claude lo habilitan | Declarado para Claude, **no heredado por Codex** |
| Firebase de Codex | `.codex/config.toml` arranca el mismo CLI que Claude, limitado a `auth,firestore`. Handshake MCP, listado de 44 herramientas, `firebase_get_project` y `firestore_list_databases` respondieron | **Servidor, autenticación y proyecto verificados**; falta confirmar carga automática en una sesión nueva de Codex |
| OpenAI Docs | Servidor público de solo lectura registrado en `.codex/config.toml` | Configurado; carga automática y búsqueda de prueba pendientes |
| RevenueCat | Servidor oficial `https://mcp.revenuecat.ai/mcp` registrado en `.codex/config.toml` | Configurado sin secreto; OAuth y acceso a Paparcar pendientes |
| Google Play | `tools/release/cut.py --phases verify` autenticó y consultó canales y ficha con la credencial local existente | **Lectura API verificada**; no es un MCP y no autoriza subir/publicar |
| Google Cloud | Google publica MCP por servicio, pero no hay uno configurado para este proyecto | Sin conexión MCP verificada; la credencial Play no prueba permisos GCP |
| Otros | El catálogo de herramientas incluye apps ajenas al proyecto; su presencia no da acceso a cuentas Paparcar | Solo habilitar cuando exista un flujo real |

El ejecutable `codex` no apareció en el PATH de PowerShell del IDE. Node v24.16.0 y Firebase CLI v15.35.0 funcionaron mediante `npx`; la sesión CLI está autenticada y `pap-26` es accesible. El servidor MCP respondió con el proyecto Paparcar y dos bases Firestore, `(default)` y `debug`, ambas **Standard**. Se cerraron los procesos de prueba. No se instaló ningún paquete global.

## Conectar Codex sin tocar Claude
La [documentación oficial de Codex MCP](https://learn.chatgpt.com/docs/extend/mcp?surface=cli) admite procesos STDIO y servidores HTTP. Codex CLI, escritorio y extensión IDE comparten `config.toml` **cuando usan el mismo host**; un proyecto confiable puede tener `.codex/config.toml`. Android Studio puede entregar herramientas `studio_*` por su propia integración: que aparezcan aquí no prueba que un Codex CLI aparte las arranque.

1. Elegir ámbito: configuración de usuario para MCP personales; `.codex/config.toml` para nombres/comandos públicos específicos de Paparcar en un proyecto marcado confiable. **No versionar secretos, tokens ni OAuth client secrets**. No editar `.mcp.json` de Claude.
2. Firebase **ya está configurado** en `.codex/config.toml` con el mismo `npx -y firebase-tools@latest mcp` de Claude y `--only auth,firestore`. Sin `--only`, el servidor anunció solo herramientas core; con esos dos grupos anunció 44, incluidas las consultas que usa Claude. La credencial se toma de la sesión local del Firebase CLI, no del repositorio. El MCP respondió a lectura de proyecto y metadatos de bases; no se leyeron documentos de usuario. `@latest` puede cambiar de versión al iniciar: revisar la versión ante un fallo antes de modificar el comando. Falta abrir una nueva sesión Codex y confirmar que el cliente confiable carga el archivo del proyecto.
3. El plugin oficial Firebase sugerido sigue sin instalarse. Es una alternativa a este MCP local; evitar dos clientes con credenciales distintas hasta necesitarlo.
4. GitHub: el plugin solo muestra instalación de la cuenta personal `rndevelo` (repositorios de esa cuenta); la organización `apptolast` no aparece entre las cuentas instaladas. La misma máquina sí accede al repositorio privado por `gh`, así que Codex puede consultar GitHub con ese CLI como alternativa inmediata. Para habilitar **el MCP del plugin**, un administrador de `apptolast` debe permitir/instalar la aplicación GitHub de esta conexión para el repositorio; revisar [instalaciones de la organización](https://github.com/organizations/apptolast/settings/installations). Repetir `get_repo` para verificar, sin ampliar permisos de escritura por defecto. Si la organización no permite esa app, evaluar el [servidor MCP oficial de GitHub para Codex](https://github.com/github/github-mcp-server/blob/main/docs/installation-guides/install-codex.md) con credencial de mínimo alcance fuera del repo; no se configuró ni se leyó ningún token.
5. OpenAI Docs **ya está registrado** en `.codex/config.toml` con `https://developers.openai.com/mcp`. Es un servidor público de solo lectura. Abrir una sesión Codex nueva y pedir una búsqueda documental para verificar su carga. [Guía oficial](https://developers.openai.com/learn/docs-mcp).
6. Google Cloud ofrece [MCP oficiales por servicio](https://docs.cloud.google.com/mcp/supported-products), entre ellos Resource Manager, Logging y Cloud CLI (Preview). Elegir el servicio que corresponda a una tarea concreta, comprobar API habilitada, OAuth/IAM y permisos mínimos; el Firebase CLI autenticado no prueba acceso MCP a Cloud. Google Play Developer API existe, pero Play no figura en ese catálogo MCP consultado. La consulta Play ya se verificó con el cliente local existente, bajo la autorización posterior del usuario para comprobar ese acceso; ver el procedimiento de abajo. No asumir que Firebase MCP sirve para Play ni que la cuenta de Play tiene permisos GCP.
7. RevenueCat **ya está registrado** como servidor HTTP oficial en `.codex/config.toml`, sin clave. [RevenueCat documenta OAuth para Codex](https://www.revenuecat.com/docs/tools/mcp/setup); falta que el usuario inicie sesión en el cliente Codex y confirme que la cuenta tiene acceso al proyecto Paparcar mediante una lectura. No se encontró su plugin en el directorio integrado consultado, aunque RevenueCat publica un AI Toolkit propio; no se instaló. OAuth puede dar acceso a todos los proyectos permitidos por la cuenta y el MCP incluye operaciones de escritura y cambios de productos en tiendas. No poner claves API en archivos compartidos.
8. Reiniciar el cliente que carga MCP y comprobar **registro → handshake/herramientas → autenticación → autorización al recurso → lectura de humo**. La [referencia CLI](https://learn.chatgpt.com/docs/developer-commands) documenta `codex mcp list --json`, `get` y `login` para servidores HTTP con OAuth. El cliente Android Studio de esta sesión puede mostrar una lista distinta; verificar en él después de cualquier conexión.

## Google Play: consulta existente verificada
El usuario confirmó acceso de la cuenta de servicio a Paparcar. La credencial local `google-application-credentials-play.json` ya existía y el cliente la consumió para autenticarse; no se abrió en el editor, se copió ni se mostró su contenido. No solicitar otra clave para repetir esta consulta.

Desde la raíz del proyecto, **en CMD** (o `studio_run_shell_command` con `shell = "cmd"`):

```bat
set "PATH=C:\Program Files\Git\usr\bin;%PATH%" && python -B tools/release/cut.py --phases verify
```

Este PATH solo dura el proceso. PowerShell no encontraba `openssl`; la invocación directa desde CMD verificó OpenSSL 3.5.9. No se instaló software ni se cambió el PATH persistente. `verify` abre una edición temporal, consulta canales/ficha y la descarta, sin `commit`, subida ni publicación. Revisar el código antes de reutilizar el comando si cambia su implementación. El contrato de publicación sigue en [RELEASE-PROCESS.md](../release/RELEASE-PROCESS.md).

Prueba del 2026-11-08: exit code 0; producción e interna `completed`, ambas `versionCode 16` y notas en nueve idiomas; alpha/beta `draft` sin versión; ficha en nueve idiomas. La comparación implementada por el script no encontró discrepancias con el repositorio. Es una fotografía de consulta, no el estado permanente de la tienda ni una auditoría de toda Play Console.

GitHub también respondió a `gh workflow list --repo apptolast/Paparcar --all` y `gh run list`: solo aparecieron CI y snapshot de parkings. No se encontró un workflow de publicación de la app en ese repositorio; localizar el enlace si la automatización vive en otro lugar antes de atribuirle ese comportamiento.

## Permisos y riesgo
Firestore contiene ubicaciones/diagnósticos y puede modificar reglas o desplegar; GitHub permite escribir, mergear y disparar workflows; Play publica; RevenueCat altera monetización. La conexión verificada no autoriza esas operaciones. La revisión automática rechazó ampliar esta configuración a `functions,crashlytics` por exponer capacidades sensibles y de despliegue sin una autorización específica; esos grupos no están activados. Reducir herramientas expuestas a lectura cuando el servidor permita `enabled_tools` o políticas por herramienta; comprobar permisos efectivos del proyecto y del cliente.

## Acciones pendientes concretas
- Reiniciar o abrir nueva sesión del cliente Codex en el proyecto confiable y comprobar que `firebase`, `revenuecat` y `openaiDeveloperDocs` aparecen. Firebase ya pasó prueba manual de proceso, credenciales y lectura de metadatos; RevenueCat requiere OAuth y lectura del proyecto; Docs requiere búsqueda de prueba.
- Autorizar la aplicación GitHub del plugin en la organización `apptolast` para `Paparcar`; hasta entonces usar `gh` para lectura. El 404 del plugin sigue pendiente.
- Para Google Cloud, elegir un MCP oficial del servicio necesario y comprobar IAM. Para consultar Play, usar la fase `verify` ya verificada; no hace falta instalar un MCP para ese flujo.
- Si se quiere usar Codex CLI en este PowerShell, instalarlo/configurarlo por el procedimiento oficial; hoy no está en PATH. No es requisito para usar Codex mediante Android Studio.
