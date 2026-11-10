# AGENTS-CODEX-COEXISTS-WITH-CLAUDE-001 · Codex entra en el repo con sus instrucciones versionadas

**Estado:** ✅ Done (10-11-2026)

## Problema
El 07/08-11-2026 Codex se inicializó en el repo y dejó su trabajo **sin commitear en el árbol
principal**: `AGENTS.md`, `docs/agents/` (4 docs), `.codex/config.toml` y revisiones en
`docs/ARCHITECTURE.md`, `docs/ROADMAP.md` y `docs/detection/PARKING-DETECTION.md`. Tres días
apareciendo en cada `git status`, y ningún agente podía cerrarlo: cada uno tiene prohibido tocar
lo del otro sin permiso. El user lo autorizó el 10-11.

## Doctrina violada
«El árbol principal es compartido: nada se queda a medias en él». Y la de los docs vivos: el aviso
del roadmap decía que el build iba por 1.5.0/16 cuando ya iba por 1.6.0/17.

## Diseño
- **Se versiona todo**, por el mismo criterio que ya rige `.claude/skills/`: las instrucciones de
  un agente que trabaja en el repo son parte del repo. Escaneado antes: sin credenciales, claves,
  correos, uids ni coordenadas. `.codex/config.toml` sólo declara servidores MCP (Firebase con
  `auth,firestore`, RevenueCat, docs de OpenAI), sin secretos.
- Los cambios a docs existentes entran por **parche** (`git diff` → `git apply` sobre master), no
  copiando ficheros: master avanzó desde `f84ade96a` y copiar habría revertido lo de después.
- `PARKING-DETECTION.md`: el cambio de Codex (el traspaso de la red de seguridad va como
  `ARRIVAL_HANDOFF` y pasa la puerta de estrategia, no como `MANUAL`) es **cierto** en master:
  `ARRIVAL_HANDOFF` vive en `ArrivalHandoffDetectionImpl`, `CoordinatorDetectionService` y
  `ParkingSafetyNetWorker` [DET-HANDOFF-NOT-MANUAL-001].
- `ROADMAP.md`: el aviso de vigencia pasa a 1.6.0/17 y remite a `cut.py --phases verify` para
  saber qué está publicado.
- `CLAUDE.md` (autorizado por el user como administrador): (a) la convivencia, simétrica a la que
  `AGENTS.md` ya declara; (b) el guard `AD_ID` del binario de [RELEASE-THE-AAB-MUST-CARRY-AD-ID-001],
  que el bloque de Meta no nombraba.

## Criterio de éxito
`git status` del árbol principal limpio tras el merge, con el contenido de Codex intacto en master.
