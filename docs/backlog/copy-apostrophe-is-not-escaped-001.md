# COPY-APOSTROPHE-IS-NOT-ESCAPED-001 · El usuario lee `Paparcar\'s`, con la barra dentro

**Estado:** ✅ Done (29-08-2026), verificado EN DEVICE · rama
`bugfix/COPY-APOSTROPHE-IS-NOT-ESCAPED-001-apostrophes` · worktree `../Paparcar-apostrophes`
87 sustituciones, 0 restantes en los 9 locales; `.cvr` compilado limpio; APK instalado en Redmi +
emulador (sha256 device↔local `8f861754…`) y el diálogo se lee **«Paparcar's»**.
⚠️ Los dos móviles llevan AHORA este build, que va por delante de master.

## Problema
87 textos de la app muestran una **barra invertida literal delante de cada apóstrofo**:
«A free spot you\'ve seen…», «Paparcar\'s recent technical log», «l\'application». Visto en
device el 29-08 en el emulador, en el diálogo de SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001, y
confirmado como preexistente y de toda la app.

**Causa medida.** El repo escribe los apóstrofos con la convención de Android (`\'`), pero esto es
**Compose Resources**, no `android:strings`, y su compilador **no desescapa `\'`**. Evidencia
directa, decodificando `assets/composeResources/…/values/strings.commonMain.cvr` del APK
`prodDebug` recién instalado:

```
home_report_subtitle              -> "A free spot you\\'ve seen and want to share with others."
home_zone_private_hint            -> "Spot won\\'t be shared when you leave"
permissions_perm_bluetooth_desc   -> "…from your car's Bluetooth"      ← crudo: SALE BIEN
permissions_bg_guide_body         -> "On the next screen:<LF><LF>1. …" ← \n SÍ se desescapa
```

O sea: `\n` funciona, el apóstrofo **crudo** funciona, y `\'` es el único roto. No es un problema
de escapado en general — es esa secuencia concreta.

## Doctrina violada
«No copy al usuario con mecánica interna»: una barra de escapado es mecánica interna filtrada a la
cara del usuario, en 3 idiomas y a días de subir a Play. Y una convención heredada de otra
plataforma que nadie volvió a medir aquí.

## Señales / datos disponibles
Ocurrencias por locale (contadas sobre el fuente, no con grep de bash — su quoting miente en los
dos sentidos con `\'`):

| locale | ocurrencias | claves |
|---|---|---|
| `values` (EN) | 22 | 21 |
| `values-fr` | 44 | 35 |
| `values-it` | 21 | 19 |
| resto (es, pt, de, nl, pl, ro) | 0 | 0 |

EN por las contracciones (`you\'ve`, `won\'t`), FR/IT por las elisiones (`l\'app`, `d\'un`,
`dell\'auto`). Los demás idiomas no usan apóstrofo, por eso nadie lo vio.

## Diseño
Sustituir `\'` por el apóstrofo crudo `'` en los 3 ficheros. No hace falta ningún escape: el XML no
le da significado al apóstrofo dentro de un elemento, y el propio repo ya tenía un caso crudo
(`permissions_perm_bluetooth_desc`) que compila y se lee bien — es la prueba de que el arreglo es
correcto, no una apuesta.

**El invariante, para que no vuelva:** en `composeResources` los apóstrofos van CRUDOS. Queda
escrito en CLAUDE.md junto a la regla de strings, que es donde se mira antes de añadir una key.

## Criterio de éxito
`\'` = 0 ocurrencias en los 9 locales; el `.cvr` compilado devuelve los textos con apóstrofo limpio;
y el diálogo que lo destapó se lee «Paparcar's» en device.

## Consumidores auditados
- Los 9 `strings.xml`: barridos los 3 con ocurrencias, verificados a 0 los 9.
- Otros escapes: `\n` verificado OK (se desescapa), `\"` y `\@` no aparecen en el repo → nada más
  que barrer. El bug es exclusivo de `\'`.
- Tests de guardarraíl (Konsist) miran tipografía/color/divisores, no el contenido de los strings →
  no aplican.
