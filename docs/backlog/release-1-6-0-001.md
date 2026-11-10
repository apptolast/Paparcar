# RELEASE-1-6-0-001 · el AAB de 1.6.0 (versionCode 17)

**Estado:** ✅ Done · cortado con `python tools/release/cut.py 1.6.0` · ⏳ del user: publicar el
borrador del canal interno en el Console

## Problema

En producción está 1.5.0 (vc 16), publicada desde el 14-10. Desde entonces entraron en master,
entre otros: el inicio rediseñado (primero tu coche, luego lo de alrededor), Aparcar con plazas y
parkings juntos, la base de parkings servida por CDN en una sola llamada con la ocupación en vivo
de Madrid, las campanas de precio en gasolineras, marcar o corregir un aparcamiento a mano, menos
avisos de Bluetooth, la pantalla de entrada nueva, el imagotipo, y el SDK de Meta App Events
detrás de su propio consentimiento (`MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001`).

## El AAB de esta version

<!-- cut:measured -->

| | |
|---|---|
| sha256 | `70ec54846b109c5a2ce631796867e2fae7f8ddd1cf9f6b2ba3124025a40e7529` |
| tamano | 24.244.057 B · 1581 entradas · 1.2 % de overhead de zip |
| vs. anterior | +806.640 B (+3,44 %) vs `paparcar-1.5.0-vc16.aab` |
| firma | SHA-1 `45:40:9F:1F:A8:8A:B2:A3:BB:25:15:CB:FB:78:F6:FD:E0:E1:BF:97` (upload key vigente) |
| leido del bundle | `versionName 1.6.0` presente y `1.5.0` ausente en `base/manifest/AndroidManifest.xml` |
| Meta | `FACEBOOK_APP_ID` presente en el manifiesto del AAB |
| notas | `release-notes-v1.6.0.txt` · it-IT 476/500 |
| suite | `testAll --rerun-tasks` · 4.069 tests, 0 fallos |
| web | `hosting/public/` igual byte a byte tras el bump |
| exportado en | `../paparcar-1.6.0-vc17.aab` |

<!-- /cut:measured -->

El corte se hizo en tres tandas, y por eso el bloque se rellenó a mano con lo que imprimió cada
fase: (1) desde PowerShell `cut.py` no encuentra `bash` para la fase `suite`; (2) desde Git Bash
`keytool -printcert` revienta con `MissingFormatArgumentException` en locale español → se corrió
con `JAVA_TOOL_OPTIONS='-Duser.language=en'`; (3) desde Git Bash Python lee las notas con la
codificación equivocada y las cuenta largas → `notes` y `doc` desde PowerShell. Tres fricciones
del script en Windows, ninguna del build.

## Notas de version

`docs/release/play-listing/paste/release-notes-v1.6.0.txt` — nueve locales, **a mano**.

## Ficha de Play

`docs/release/play-listing/LISTING-v1.6.0.md`: la de 1.5.0 con el bloque de privacidad nombrando
a Meta en los nueve idiomas (lo exige la fase `bump`). `published-listing.txt` **sigue apuntando a
1.5.0** hasta que Play publique 1.6.0; entonces se mueve el puntero y se regenera `paste/`.

## Criterio de exito

- [x] bump en los **dos** sitios · suite verde con `--rerun-tasks` · firma verificada
      con `-printcert` · notas en los 9 locales bajo 500
- [x] borrador en el canal interno (`cut.py --phases upload`)
- [ ] **Del user**: publicar el borrador en el Console · Data Safety y declaración de Advertising ID
      según `docs/legal/DATA-SAFETY-FORM.md`
