# Paparcar — instrucciones para Codex

Paparcar es un asistente de movilidad KMP/Compose Multiplatform: **conducir → aparcar → recordar → abandonar → compartir**. Los trayectos, el garaje y la detección automática son centrales; no reducir el producto a un pin manual.

## Límites y convivencia
- Claude Code continúa operativo. No modificar `CLAUDE.md`, `.claude/`, `.mcp.json`, sus permisos, hooks, skills, agentes ni memoria sin autorización explícita.
- No hay memoria compartida. Leer decisiones relevantes en el repositorio; no inferir conversaciones del otro agente.
- Antes de editar: `git status --short --branch`, `git worktree list`, diff de archivos afectados. Conservar cambios ajenos; ante solapamiento real, consultar.
- Para tareas de código, usar un worktree aislado y ticket en `docs/backlog/`, conforme a [.claude/skills/nuevo-ticket/SKILL.md](.claude/skills/nuevo-ticket/SKILL.md), leído como referencia. Adaptar herramientas al entorno; no ejecutar sus pasos de cierre automáticamente.
- Commits, pushes, merges, rebases, cherry-picks, force pushes y operaciones remotas requieren autorización explícita. No desplegar, publicar, instalar herramientas globales ni cambiar credenciales por iniciativa propia.
- La inicialización de Codex solo autoriza documentación local: no código, reglas Firebase ni configuración sensible.
- No copiar tokens, claves, identificadores personales ni trazas de ubicación a documentación. No abrir archivos de credenciales para entender la arquitectura.

## Arquitectura real
- `:shared`: producto KMP. `commonMain/kotlin/com/rndeveloper/paparcar/` contiene `domain`, `data`, `presentation`, `ui`, `di`, `core`.
- `androidMain`: sensores, Bluetooth, ubicación, FGS, receivers, workers y adaptadores Android. `iosMain`: implementaciones iOS reales; no tratarlo como directorio futuro vacío.
- `:app`: shell Android, arranque, manifest, firma y flavors `prod/mock`; BuildConfig se comunica a shared mediante contratos de build.
- `:mockkit`: fakes y grafo mock. Depende de shared; producción no importa mockkit. Mantener su grafo al cambiar DI.
- Versiones: [catálogo](gradle/libs.versions.toml); módulos: [settings](settings.gradle.kts). Las tablas históricas no ganan al código.

## Convenciones
- Domain puro Kotlin, interfaces de repositorios en domain, implementaciones y mappers en data, bindings Koin en di. Plataformas tras interfaces/expect-actual.
- MVI: State inmutable + Intent + Effect; seguir `BaseViewModel`, StateFlow y SharedFlow existentes. Un efecto efímero no sustituye estado durable.
- Use cases: Flow observable, kotlin.Result para operaciones; evaluadores puros pueden devolver veredictos. No crear clases inyectadas para cada predicado.
- Room conserva sesiones locales; sync remoto se agenda. No generalizar esto a todos los repositorios: el espejo compartido y RevenueCat tienen autoridad distinta.
- Respetar cancelación, lifecycle y errores tipados `PaparcarError`; no catches silenciosos ni logs de secretos. Usar logger del proyecto, imports explícitos y constantes con dueño claro.
- Fakes sobre mocks; nueva lógica de negocio con pruebas. Naming `should_expectedBehavior_when_condition`.
- UI: componentes Pap*, `PaparcarType.current` por rol, tokens de tema; no hex, tamaños/pesos tipográficos ni strings visibles inline. No redibujar la marca: maestro en `docs/branding/master/`.
- EN base y los otros ocho idiomas en la misma tarea. Compose Resources usa apóstrofo crudo y porcentaje literal; no copiar escapes de recursos Android.
- Pantalla/estado/routing nuevo: previews, Dev Catalog, escenarios y fakes en sync. Cambio visual: evidencia del estado final en PR.
- Home = ahora; Vehículos = lo propio e historial; Ajustes = configuración. No añadir navegación paralela sin decisión de producto.

## Detección y privacidad: reglas que no se revierten
- Evento nomina; movimiento medido confirma. Preferir falso negativo/pregunta frente a plaza fantasma.
- BT y Coordinator tienen decisiones separadas en un único FGS/intake; nunca señales BT en scoring.
- BT conectado manda, no solo emparejado. No prometer ese carril en iOS.
- Decisiones puras en commonMain; service para I/O. Conservar provenance, autoridad del pin, frescura de salida y exclusión de zonas privadas.
- Antes de cambios de detección leer [contratos críticos](docs/agents/critical-contracts.md), el tramo relevante de [referencia canónica](docs/detection/PARKING-DETECTION.md) y [det-change](.claude/skills/det-change/SKILL.md). Barrer todos los consumidores y actualizar el log canónico.
- Nunca gatear ver plazas, parkings o gasolineras. El reparto Free/Plus tiene excepciones deliberadas para avisos de combustible: consultar decisión vigente, no homogeneizarla.
- La geometría compartida puede viajar aunque su visualización esté apagada: no prometer que el interruptor impide subirla.

## Validar y cerrar
- Revisar diff y ejecutar validación proporcional. Suite agregada declarada: `./gradlew testAll --console=plain`; incluye shared, grafo mock de app y compilación klib iOS. No equivale a enlazar/probar iOS en Mac.
- Usar herramientas Gradle del IDE o Bash disponible; no `.\gradlew` en PowerShell (no hay gradlew.bat). No instalar Bash sin permiso.
- Cambios DI/UI: comprobar prod y mock. Guards que leen fuentes/recursos pueden requerir `--rerun-tasks`.
- Informar comandos, resultados reales y límites; nunca declarar tests verdes por existir o por UP-TO-DATE.
- Actualizar fuente canónica si cambia arquitectura, negocio, integración, detección, privacidad o release. Tickets ya registran decisiones: no duplicar un ADR sin necesidad.
- Web y ficha son consumidores del producto; la ficha describe el build publicado. Seguir sus documentos, sin desplegar implícitamente.
- En Android Studio usar `studio_*` para leer/editar buffers, búsquedas y shell. Descubrir MCP en cada sesión: configuración presente no prueba autenticación. Para conexión, permisos y verificación consultar [MCP de Paparcar](docs/agents/mcp-setup.md).

## Lectura progresiva
1. Este archivo.
2. [Mapa por tarea y funcionalidades](docs/agents/project-map.md).
3. [Contratos críticos](docs/agents/critical-contracts.md) y fuentes canónicas enlazadas.
4. Código y tests concretos; herramientas externas solo si aportan evidencia.

[Auditoría de incorporación](docs/agents/onboarding-audit.md): fuentes, discrepancias, cobertura Claude/Codex, herramientas y límites. Es una fotografía fechada, no un roadmap alternativo.
