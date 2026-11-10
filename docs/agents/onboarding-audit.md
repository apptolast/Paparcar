# Auditoría de incorporación Claude ↔ Codex
**Fecha:** 2026-11-07. **Base:** HEAD local `f84ade96a`, master, un commit por delante de origin/master al inicio; árbol inicialmente limpio. No hubo fetch: la referencia origin/master puede estar desactualizada.

## Resultado y alcance
**Seguimiento 2026-11-07:** se corrigieron secciones desfasadas de `docs/ARCHITECTURE.md`, el párrafo del handoff en `docs/detection/PARKING-DETECTION.md` y se advirtió la vigencia limitada de `docs/ROADMAP.md`. Se añadió [guía MCP](mcp-setup.md). El resto de esta auditoría conserva lo observado en la incorporación inicial; las filas de discrepancias son historial de hallazgos, no afirmación de que sigan sin corregir.

**Seguimiento 2026-11-08:** la [guía MCP](mcp-setup.md) contiene el estado vigente de conexiones. Se creó `.codex/config.toml` para Firebase con `auth,firestore`, RevenueCat y OpenAI Docs. El servidor Firebase, la autenticación y una lectura de metadatos de `pap-26` respondieron. RevenueCat necesita OAuth; la carga automática de estos servidores por un cliente Codex nuevo sigue pendiente de comprobar. El plugin GitHub de Codex solo tiene instalación de cuenta personal y devuelve 404 para `apptolast/Paparcar`; `gh` sí lee ese repositorio privado. Las afirmaciones inferiores sobre ausencia de configuración y falta de pruebas MCP describen exclusivamente el inventario inicial.

Se añade [AGENTS.md](../../AGENTS.md) y navegación progresiva mediante [mapa](project-map.md) y [contratos](critical-contracts.md).
En la incorporación inicial no se creó `.codex/config.toml`; se añadió en el seguimiento del 2026-11-08 para Firebase. En la incorporación inicial tampoco se modificaron fuentes canónicas, código ni archivos de Claude. El contexto documental **no acredita equivalencia completa con la memoria privada de Claude ni validación funcional integral**.

La exploración fue sistemática por módulos, flujos y fuentes, con lectura profunda de puntos críticos y búsqueda dirigida en el resto. No se leyó cada línea del repositorio ni los 10.018 renglones del histórico de detección. Los inventarios funcionales distinguen lo inspeccionado de lo parcialmente recuperado. No se ejecutaron compilaciones, tests, compras, despliegues ni pruebas en dispositivos por tratarse exclusivamente de documentación.

## Fuentes recuperadas y clasificación
| Fuente | Inspección y uso | Vigencia / audiencia |
|---|---|---|
| CLAUDE.md raíz | Leído por tramos: arquitectura, detección, diseño, producto y flujo operativo | Entrada Claude; conservar. Algunas afirmaciones históricas necesitan contraste |
| .claude/skills | Localizadas cinco: nuevo-ticket, det-change, field-test, instalar-apk, rebase-rama. Reglas principales leídas; det-change íntegra | Específicas de Claude, conocimiento operativo reutilizable por referencia; no se invocaron instalaciones/rebases |
| .claude/settings.local.json | Inspeccionada estructura, categorías de permisos y activación MCP sin copiar valores | Privada del entorno Claude; sin hooks declarados en este archivo |
| .mcp.json | Servidor firebase vía npx/firebase-tools@latest/mcp | Declaración Claude; no prueba conexión Codex |
| AGENTS/.codex | No había AGENTS de proyecto ni config .codex en el inventario inicial | Se crea únicamente AGENTS; no tocar configuración global |
| docs/ARCHITECTURE | Capas y persistencia contrastadas con builds y repositorios | Conceptos útiles, versiones y módulos desfasados |
| docs/detection/PARKING-DETECTION | Sección inicial contrastada con resolver/evaluador/servicio; histórico localizado | Canónica por decisión, con discrepancia concreta registrada abajo |
| docs/design, branding, architecture | Inventario y referencias desde CLAUDE; Color.kt leído | Reutilizar; esta auditoría no sustituye cada especificación |
| ROADMAP y RELEASE-PROCESS | Tramos de estado, operación y riesgos leídos | Fotografía histórica, contrastar antes de afirmar pendiente/publicado |
| CI y Gradle | Settings, catálogo, builds, testAll y jobs inspeccionados | Fuente ejecutable para módulos/dependencias/tareas; no ejecución |
| Código Kotlin | Resolver, BaseViewModel, RevenueCat repo, persistencia de sesión, ConfirmParking, evaluador y service; búsquedas dirigidas en resto | Evidencia estática a HEAD |
| Backend | firebase.json, reglas, exports Functions y package scripts; esquemas por búsqueda | Configuración local; no estado desplegado |
| Tests | HomeViewModelTest, pruebas de nominación/retractación y source sets localizados; herramientas desde Gradle/npm/CI | Cobertura existente, resultados no ejecutados |
| tools/release, tools/parkings, web | Inventario y contratos referenciados por configuración/docs | No ejecutar scripts operativos por leerlos |
| Memoria | No apareció MEMORY.md de proyecto en búsqueda acotada; la skill refiere memoria externa | Conversaciones y memorias globales no recuperadas; no inferir su contenido |

Las búsquedas generales devolvieron algunas dependencias/node_modules; se descartaron como contexto del proyecto. No se exploraron masivamente binarios, cachés ni documentos de dependencias. No se reproducen cuentas, uids ni seriales de dispositivos que aparecen en skills de campo.

## Discrepancias comprobadas y resolución propuesta
| Hallazgo | Evidencia | Tratamiento |
|---|---|---|
| “Dos módulos” y fakes en shared | ARCHITECTURE frente a settings con app/shared/mockkit; CLAUDE también conserva encabezado antiguo | Leer settings y mockkit como realidad. Proponer revisión focalizada de ARCHITECTURE en otra tarea |
| Versiones/BaseLogin JitPack antiguas | ARCHITECTURE frente a catálogo y repositorios de settings; CLAUDE documenta Maven Central | Catálogo manda; no actualizar dependencias por este informe |
| iOS descrito como futuro | Introducción/estructura de CLAUDE frente a iosMain y builds con controller, sensores, sync y bootstrap | No dar por vacío iOS. Verificación runtime sigue pendiente |
| MANUAL incluye arrival handoff en referencia | PARKING-DETECTION §1.1 frente a coordinatorMayArm y ARRIVAL_HANDOFF | Aplicar gate real: handoff automático no goza de excepción manual. Corregir doc canónico por tarea acotada |
| Orden de resolución en KDoc | ParkingStrategyResolver tabla enumera primario NONE primero; cuerpo comprueba BT conectado primero | Seguir cuerpo y tests; no cambiar comportamiento para casar con comentario |
| Paleta de referencia antigua | Valores iniciales frente a Color.kt | Mapa recoge valores actuales; ningún rediseño |
| Roadmap hacia 1.1.0 y ocupación pendiente | ROADMAP frente a versionName 1.5.0 y ingestOccupancy/mergeCell existentes | No usar roadmap como prueba de ausencia ni versión publicada |
| Strings: falta de locale “crashea” | nuevo-ticket frente a explicación posterior de fallback en CLAUDE | Mantener los nueve locales, distinguir fallback EN de falta de recurso base |
| BaseViewModel KDoc llama cold a StateFlow | Implementación MutableStateFlow y SharedFlow sin replay | No copiar esa descripción ni prometer entrega durable de efectos |
| “Guardado y enqueue atómicos” en KDoc del repo | Transacción Room seguida de enqueue con fallo capturado | Atomicidad local del reemplazo, no transacción distribuida con scheduler |

No se corrige ninguna decisión ni archivo protegido en esta misión. Las propuestas son documentales; un cambio de comportamiento necesita investigación y autorización de alcance.

## Herramientas y MCP: observado, no supuesto
| Integración | Estado en esta sesión | Autenticación/permisos y verificación pendiente |
|---|---|---|
| Android Studio MCP | **Verificado:** find_files, grep, read_file, write_file y run_shell_command respondieron | Contexto IDE; editar con APIs de buffers. Build, preview y dispositivo disponibles en catálogo pero no ejercitados |
| GitHub plugin | Herramientas expuestas; get_repo(apptolast/Paparcar) devolvió **404** | No prueba ausencia del repo: puede ser falta de acceso. Pendiente conexión/cuenta/instalación adecuada y repetir lectura |
| Firebase MCP de Claude | Declarado en .mcp.json; no hay herramientas Firebase en el catálogo Codex usado | No se inició npx ni se autenticó. Permisos de Claude no se heredan |
| Google Cloud / Google Play / RevenueCat | Sin herramientas específicas encontradas en el catálogo examinado | Scripts locales/configuración no equivalen a servicio conectado; consolas no consultadas |
| Documentación/web | Búsqueda y apertura oficial OpenAI verificadas; herramientas Android docs anunciadas | No son acceso a backend Paparcar |
| Diseño/testing externo | Hay herramientas IDE de preview/tests; plugins adicionales del entorno no se comprobaron para Paparcar | No atribuir compatibilidad o acceso por aparecer instalados |

**Estado MCP vigente:** consultar [mcp-setup.md](mcp-setup.md). En el inventario inicial no se ejecutó `npx`; en el seguimiento del 2026-11-08 sí se probó Firebase MCP por lectura de metadatos sin documentos personales. El uso de `@latest` puede cambiar la versión en el futuro. No copiar ajustes de Claude ni tokens.
GitHub aporta lectura de PR/checks/protecciones; primero resolver el 404, sin crear PR ni modificar remotos. Google Play solo si una tarea de release lo necesita; la presencia de credencial local no autoriza su uso.
No se proponen agentes/automatizaciones nuevas: el mapa y los guardarraíles existentes cubren esta incorporación.

Referencias oficiales consultadas: [AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md) y [MCP](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).
AGENTS es el punto de instrucciones de Codex; no es una importación de memoria Claude. La documentación oficial permite configuración MCP propia; la integración concreta de Android Studio debe verificarse en su sesión. No se promete que reiniciar una conversación cargue herramientas de Claude.

## Matriz de cobertura
“Compartido” significa accesible por archivos, no memoria común. “Parcial” conserva una limitación explícita.

| Área | Fuente Claude | Contexto Codex/compartido | Estado | Pendiente |
|---|---|---|---|---|
| Producto | CLAUDE, roadmap, tickets | project-map + mismos documentos | Compartido parcial | Revalidar roadmap completo |
| Arquitectura | CLAUDE, ARCHITECTURE | AGENTS, builds, mapa | Contrastado estático | Corregir tablas históricas |
| Módulos | CLAUDE | settings, mapa | Verificado estático | Ninguno para orientación |
| Diseño | CLAUDE, COLOR-SYSTEM, branding | tokens + mapa | Parcial | Preview/device, accesibilidad/adaptación |
| Detección | det-change, field-test, histórico | contratos + código + mismos docs | Profundizado en rutas críticas | Campo, historial completo por caso |
| Firebase | MCP, reglas, repositorios | contratos + reglas locales | Parcial | Conexión y estado desplegado |
| Google Cloud | Configuración y docs release | límites de consola documentados | Parcial | IAM, cuotas, claves y APIs habilitadas |
| Google Play | release y scripts | mismo proceso | Parcial | Tracks, SHA/OAuth, declaraciones actuales |
| GitHub | remoto y workflows | tools + CI local | Acceso remoto no verificado | 404, branch protection y checks |
| MCP | .mcp.json/settings | inventario separado | Diferente por entorno | Firebase Codex y auth GitHub |
| RevenueCat | catálogo/tiers | repo + contratos | Estático | Ofertas/productos/compra/restauración real |
| APIs externas | investigación y código | fuentes y cachés | Parcial | Salud, cuotas y cobertura efectiva |
| Testing | skills, Gradle, CI | comandos y límites | Configuración verificada | Ejecutar al cambiar comportamiento |
| Seguridad | reglas, contratos, release | AGENTS + contratos | Parcial | Auditoría integral y consola |
| CI/CD | .github/workflows | mismo código | Estático | Últimas ejecuciones y límites de cuenta |
| Publicación | RELEASE-PROCESS, cut.py | referencias sin ejecución | Documentado | Estado real en tiendas |
| Roadmap | ROADMAP/backlog/memoria | backlog accesible | Parcial | Memoria privada y reconciliación completa |

## Mantenimiento y relevo entre agentes
- Fuente ejecutable para comportamiento; ticket fechado para intención; documentación canónica para contrato. Si difieren, registrar contradicción antes de reinterpretar.
- Antes de una tarea de código: worktree propio, alcance, ticket, consumidores y pruebas. Había cuatro worktrees adicionales (glance, homeux, ladder, overpass); no se inspeccionó ni alteró su trabajo.
- En el relevo dejar objetivo, archivos tocados, decisiones, comandos/resultados y pendiente en el ticket; nunca confiar en que el otro agente vio el chat.
- Actualizar solo el documento dueño al cambiar arquitectura, política, integración, flujo crítico o publicación. Este mapa se actualiza si cambian rutas/fuentes; no copiar el backlog aquí.
- Tickets ya contienen problema, doctrina, diseño y evidencia. Añadir ADR solo para decisiones transversales duraderas que no tengan fuente equivalente, enlazando el ticket.
- Propuesta opcional para CLAUDE, **no aplicada**: añadir una referencia breve a docs/agents/project-map.md y onboarding-audit.md como índice y advertencias. No es requisito para que Codex use la documentación existente.

## Información que falta y acciones con autorización
No se accedió a conversaciones anteriores, memoria global/privada Claude, instrucciones globales externas, consola Firebase/GCP/Play/RevenueCat ni protecciones GitHub.
Para cerrar esos huecos basta exportar **decisiones y pendientes sanitizados** de memoria/conversaciones; no archivos de credenciales. Para consolas: informes sin secretos de restricciones/API/IAM, tracks/publicación y catálogo RevenueCat. No hace falta exportarlo todo para empezar a trabajar en código local.

La autorización posterior del usuario permitió configurar localmente el MCP Firebase de Codex y verificar el acceso de lectura. Sigue haciendo falta autorización específica para tocar CLAUDE/.claude/.mcp, ampliar capacidades sensibles, instalar herramientas globales, cambiar credenciales, modificar reglas/producción o ejecutar operaciones Git de publicación/historia y despliegues.

## Verificación de convivencia
Git inicial limpio; comprobación posterior sin diferencias en archivos rastreados, incluidos CLAUDE.md/.claude/.mcp.json; solo nuevos AGENTS.md y docs/agents.
Se verificó que tres rutas sensibles locales no están rastreadas y están ignoradas; su contenido no fue leído.
Los cuatro entregables se releyeron y coincidieron con el contenido escrito; sus destinos Markdown locales se comprobaron con el índice del IDE (CLAUDE.md también se había leído directamente). La búsqueda de patrones de clave privada/API/token en los entregables no dio coincidencias; no es un escáner integral de secretos del repositorio.
La comparación directa de .mcp.json y .claude/settings.local.json con las lecturas iniciales dio igualdad. La comprobación Git completada mostró solo documentación nueva y diff --check sin errores en rastreados. Una repetición combinada de comprobación de enlaces/Git por shell no devolvió resultado y se interrumpió la espera; no se cuenta como validación ejecutada. Los archivos nuevos se validaron por lectura IDE, no por git diff --check (aún no están rastreados).
La comprobación Git no demuestra comportamiento runtime idéntico de Claude ni cubre configuraciones globales no inspeccionadas: acredita que esta intervención documental no las modificó mediante sus herramientas.
