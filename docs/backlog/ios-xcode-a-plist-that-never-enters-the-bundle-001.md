# IOS-XCODE-A-PLIST-THAT-NEVER-ENTERS-THE-BUNDLE-001 · el proyecto Xcode, listo para construirse

**Estado:** ✅ Done (05-09-2026) · plegado en `feature/IOS-F0-001-fase0` (todo iOS viaja en el
PR #3) · ⏳ declarados: veredicto del paso `xcodebuild` en el job `apple` del PR, y el arranque
real en un iPhone (compañero del Mac: copiar el plist a `iosApp/iosApp/` + `TEAM_ID` para device).

## Problema (y una corrección a la auditoría)

La auditoría de paridad (03-09) señaló tres bloqueantes de arranque: `PBXResourcesBuildPhase`
vacía, `TEAM_ID` vacío y sin `.xcscheme` compartido.

**Corrección medida (05-09), leído el `project.pbxproj` completo**: el proyecto es formato
Xcode 16 (`objectVersion = 77`) con **`PBXFileSystemSynchronizedRootGroup`** — la carpeta
`iosApp/iosApp/` se sincroniza sola con el target, así que un `GoogleService-Info.plist` copiado
ahí **SÍ entra al bundle automáticamente** (la fase de recursos vacía es lo normal con sync
groups; solo `Info.plist` está en la lista de excepciones). El "plist que nunca entra al bundle"
del título era un falso problema. ⏳ Verificación final en un Mac real, pero el mecanismo está en
el formato del proyecto, no en una conjetura.

**Lo que SÍ bloquea**: sin `.xcscheme` compartido (viven en `xcuserdata/`, gitignored), ni el
compañero desde un checkout limpio ni el CI pueden `xcodebuild` — el escalón que
[CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001] dejó explícitamente a la espera de este fichero.
Y `TEAM_ID` vacío bloquea la firma de DEVICE, pero no el build de simulador
(`CODE_SIGNING_ALLOWED=NO`).

## Doctrina violada

Ninguna de detección. La de proceso: el job `apple` compila Kotlin/Native pero el Swift del shell
no lo construye nadie — el mismo agujero "solo falla el build que nunca corremos", una capa más
arriba.

## Diseño

1. **`iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`** — el scheme compartido, escrito a
   mano (XML estándar; `BlueprintIdentifier = 1957BC10FC5AE3EA3F651BB0`, el target `iosApp` del
   pbxproj). Committeado: deja de depender del `xcuserdata` de una máquina concreta.
2. **CI**: paso `xcodebuild` en el job `apple` — build de SIMULADOR sin firma
   (`CODE_SIGNING_ALLOWED=NO`, sin `TEAM_ID`), con caché de paquetes SPM
   (`-clonedSourcePackagesDirPath`). Construye el Swift + la fase "Compile Kotlin Framework"
   (embedAndSign vía Gradle) — el camino completo que un Mac humano recorrería.
   El plist de Firebase NO hace falta para construir (solo en runtime), así que el build de CI no
   necesita secrets nuevos.
3. **`TEAM_ID`**: se queda vacío a propósito — es del Apple Developer account del user/compañero,
   no del repo. Documentado aquí: para device, rellenar `iosApp/Configuration/Config.xcconfig`.

## Criterio de éxito

- El job `apple` del PR #3 construye `Paparcar.app` para simulador con `xcodebuild` en verde.
- Un checkout limpio en un Mac puede `xcodebuild -scheme iosApp` sin abrir Xcode antes.
- Suite JVM intacta (este ticket no toca Kotlin).

## Consumidores auditados

- `ci.yml` job `apple`: gana el paso; el KDoc del paso "Link iOS framework" que decía «the
  xcodebuild step joins this job the day that .xcscheme is committed» se actualiza — ese día es hoy.
- `docs/backlog/ci-ios-compiles-on-a-mac-not-on-a-promise-001.md`: su escalón pendiente queda
  cubierto por este ticket (anotado allí).
- Ticket hermano NO absorbido: `IOS-CRASH-A-BRIDGE-NOBODY-INSTALLS-001` (Crashlytics SPM + bridge)
  sigue siendo tarea propia — un ticket, un commit.
