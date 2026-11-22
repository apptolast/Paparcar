# RELEASE-1-7-0-001 · el AAB de 1.7.0 (versionCode 18)

**Estado:** ✅ Done · cortado con `python tools/release/cut.py 1.7.0` · ⏳ del user: subir,
publicar y la página `paparcar.com/blackfriday` (en marcha en paralelo)

## Problema

En producción está 1.6.0 (vc 17), publicada el 10-11. Desde entonces entraron en master, entre
otros: confirmación antes de avisar una plaza o marcar/corregir un aparcamiento, los parkings
distinguiendo ocupación medida en vivo de caducada o sin dato, la primera entrada a Home
reordenada para enseñar una sola cosa cada vez, menos avisos de cierre honesto cuando ya acertó,
mejoras de batería y de precisión al dibujar la ruta, limpieza del catálogo de dependencias, y —el
motivo de este corte— el regalo de Fundadores por Black Friday: 3 meses de Plus gratis para las
2.000 primeras cuentas NUEVAS que lleguen a Home antes del 30-11 23:00 UTC. El servidor
(`grantFoundersPlus`/`releaseFoundersSlot`) y el gate de cuenta-nueva ya están desplegados y
probados en producción (ver `MONEY-FOUNDERS-PLUS-PROMO-001` y
`MONEY-FOUNDERS-PROMO-REQUIRES-A-NEW-ACCOUNT-001`); este build es el primero que lleva el código
de cliente que escribe el reclamo.

## El AAB de esta version

<!-- cut:measured -->

| | |
|---|---|
| sha256 | `d4f0bc407d1c1cecef241143e500f226d038249d774d9db351b23ca6f8912b70` |
| tamano | 24.310.567 B · 1570 entradas · 1.2 % de overhead de zip |
| vs. anterior | +66510 B (+0.27 %) vs paparcar-1.6.0-vc17.aab |
| firma | SHA-1 `45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97` (upload key vigente) |
| leido del bundle | `versionName 1.7.0` presente en `base/manifest/AndroidManifest.xml`; `1.6.0` **ausente** |
| notas | `release-notes-v1.7.0.txt` · nl-NL 475/500 |
| suite | `testAll --rerun-tasks` · 95 tareas ejecutadas |
| web | `hosting/public/` igual byte a byte: no hace falta desplegar |
| exportado en | `C:\Users\rndev\Documents\AndroidProjects\paparcar-1.7.0-vc18.aab` |

<!-- /cut:measured -->

## Notas de version

`docs/release/play-listing/paste/release-notes-v1.7.0.txt` — nueve locales, **a mano**.

## Criterio de exito

- [x] bump en los **dos** sitios · suite verde con `--rerun-tasks` · firma verificada
      con `-printcert` · notas en los 9 locales bajo 500
- [ ] **Del user**: subir el AAB a Play (canal interno, borrador), pegar las notas, y promover a
      producción cuando toque
