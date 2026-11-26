# RELEASE-1-8-3-001 · el AAB de 1.8.3 (versionCode ?)

**Estado:** 🔵 En progreso · cortado con `python tools/release/cut.py 1.8.3`

## Problema

Corte de emergencia. El AAB publicado de 1.8.2 (vc21) llevaba `REVENUECAT_API_KEY` con un valor
de relleno en vez de la clave real — confirmado descomprimiendo el AAB firmado y buscando la
cadena en sus `.dex` (ver [MONEY-FOUNDERS-GRANT-A-MISSING-SUBSCRIBER-IS-NOT-A-REJECTION-001](money-founders-grant-a-missing-subscriber-is-not-a-rejection-001.md)).
Efecto: ningún usuario nuevo en ese binario se identifica de verdad en RevenueCat — ni el regalo
de founders ni una compra normal de Plus. El usuario detuvo el rollout de vc21 en Play Console al
confirmarlo (`verify` pasó a `production halted [21]` / `completed [20]`).

Esta versión **solo** corrige esa clave (y, encontrado de paso al preparar este corte, un
`TIKTOK_APP_ID` que faltaba del mismo modo). No entra ningún otro cambio de producto — el resto de
commits desde `v1.8.0` ya forman parte de 1.8.1/1.8.2 y no se repiten aquí.

## El AAB de esta version

<!-- cut:measured -->

| | |
|---|---|
| sha256 | `1d264662bf28ecb175a74e08902d4613b4c702075f4c5d15c4f23eb9f838f606` |
| tamano | 24.569.407 B · 1572 entradas · 1.2 % de overhead de zip |
| vs. anterior | sin AAB anterior en disco para comparar |
| firma | SHA-1 `45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97` (upload key vigente) |
| leido del bundle | `versionName 1.8.3` presente en `base/manifest/AndroidManifest.xml`; `1.8.2` **ausente** |
| notas | `release-notes-v1.8.3.txt` · de-DE 110/500 |
| suite | `testAll --rerun-tasks` · 95 tareas ejecutadas, BUILD SUCCESSFUL |
| web | `hosting/public/` igual byte a byte: no hace falta desplegar |
| exportado en | `C:\Users\rndev\Documents\AndroidProjects\paparcar-1.8.3-vc22.aab` |

<!-- /cut:measured -->

## Notas de version

`docs/release/play-listing/paste/release-notes-v1.8.3.txt` — nueve locales, **a mano**.

## Criterio de exito

- [x] bump en los **dos** sitios · suite verde con `--rerun-tasks` · firma verificada
      con `-printcert` · notas en los 9 locales bajo 500
- [ ] **Del user**: subir el AAB a Play, pegar las notas y publicar
