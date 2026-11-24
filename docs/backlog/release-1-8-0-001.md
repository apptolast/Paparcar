# RELEASE-1-8-0-001 · el AAB de 1.8.0 (versionCode 19)

**Estado:** 🔵 En progreso · cortado con `python tools/release/cut.py 1.8.0` · ⏳ del user: subir,
publicar

## Problema

En producción está 1.7.0 (vc 18), confirmado `completed` en `verify`. Desde `v1.7.0` entraron en
master: una parada a repostar deja de confundirse con aparcar (no publica plaza ni cuenta como
sesión), quien ya tiene Plus ve ahora un resumen de sus ventajas en vez de un paywall muerto, el
primer acceso marca dónde se ha dejado el coche antes de ofrecer la detección automática, un fix
visual en la insignia de Plus en tema claro, y cobertura de analíticas (GA4) en gasolineras/
historial/alertas — invisible para el usuario, no entra en las notas. Tres commits más son
sólo-web (Black Friday, atribución de los CTA del sitio): no tocan el binario y `web` ya confirmó
que `hosting/public/` no necesita desplegarse de nuevo.

## El AAB de esta version

<!-- cut:measured -->

| | |
|---|---|
| sha256 | `9e5ce7df846b16fd57818fca69283681ffd25d85a7d48fbaed19c223aeeea52b` |
| tamano | 24.361.692 B · 1570 entradas · 1.2 % de overhead de zip |
| vs. anterior | +51125 B (+0.21 %) vs paparcar-1.7.0-vc18.aab |
| firma | SHA-1 `45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97` (upload key vigente) |
| leido del bundle | `versionName 1.8.0` presente en `base/manifest/AndroidManifest.xml`; `1.7.0` **ausente** |
| notas | `release-notes-v1.8.0.txt` · fr-FR 475/500 |
| suite | `testAll --rerun-tasks` · 95 tareas ejecutadas |
| web | `hosting/public/` igual byte a byte: no hace falta desplegar |
| exportado en | `C:\Users\rndev\Documents\AndroidProjects\paparcar-1.8.0-vc19.aab` |

<!-- /cut:measured -->

## Notas de version

`docs/release/play-listing/paste/release-notes-v1.8.0.txt` — nueve locales, **a mano**.

## Criterio de exito

- [x] bump en los **dos** sitios · suite verde con `--rerun-tasks` · firma verificada
      con `-printcert` · notas en los 9 locales bajo 500
- [ ] **Del user**: subir el AAB a Play, pegar las notas y publicar
