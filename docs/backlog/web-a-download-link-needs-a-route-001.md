# WEB-A-DOWNLOAD-LINK-NEEDS-A-ROUTE-001 · `paparcar.com/descargar` redirige a Play Store

**Estado:** ✅ Done · issue GitHub #139

## Problema
No existe ninguna ruta en `paparcar.com` que redirija a la ficha de Play Store. El issue #139 pide
`paparcar.com/descargar` → `https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar`,
pensado para enlazarlo desde fuera (campañas, bio de redes, etc.) sin tener que pegar la URL larga
de Play en todos los sitios.

## Doctrina violada
Ninguna — es una feature nueva, no una corrección de invariante. Aplica
*La web es un consumidor más de la verdad del producto* (CLAUDE.md): el `applicationId` que forma
la URL de Play vive en `web/lib/config.mjs` y no se escribe a mano aparte.

## Señales / datos disponibles
Investigación (agente explorador):
- `firebase.json` → `hosting` ya tiene `public`, `cleanUrls`, `headers`; **no** tenía `redirects`.
  El propio fichero deja escrito [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001]: una regla por
  ruta EXACTA, nunca un `@(a|b)` de alternativas — un `@(…)` deployó limpio y no casó con nada.
- `web/build.mjs` genera 7 páginas (`home`, `support`, `terms` × 2 locales) + sitemap/robots/404.
  `privacy-policy.html` y `delete-account.html` son a mano (`HAND_WRITTEN`), el resto sale del
  generador. No hay precedente de "página de redirect" en `pages/`.
- `web/lib/config.mjs:16` ya declara `PLAY_URL` construida desde `applicationId`
  (`app/build.gradle.kts:68` = `com.rndeveloper.paparcar`, coincide con el issue).
  `WebSiteParityGuardrailTest` ya compara `PLAY_URL` contra ese `applicationId`.
- No existía ninguna ruta `/descargar` ni `/download` en el repo.

## Diseño
**Redirect nativo de Firebase Hosting** (`hosting.redirects` en `firebase.json`), no una página
generada: es infraestructura de hosting, no copy del producto, y el propio `firebase.json` ya
declara convenciones para esto (una regla por ruta exacta, nunca una alternación).

```json
"redirects": [
  {
    "source": "/descargar",
    "destination": "https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar",
    "type": 301
  }
]
```

`firebase.json` es JSON puro — no puede importar `PLAY_URL` de `config.mjs` — así que el destino
queda **duplicado a mano**, como una segunda fuente del mismo hecho. Para no dejarlo sin vigilancia
(doctrina *la web es un consumidor más de la verdad del producto*), se extiende
`WebSiteParityGuardrailTest` (`every fact the site declares still matches the repository`) con un
chequeo nuevo: lee `firebase.json`, busca la regla de `source == "/descargar"` y comprueba que su
`destination` termina en `?id=$applicationId` — el mismo patrón que ya usa para `PLAY_URL`.
Solo hay una ruta (`/descargar`, sin prefijo de locale): es un atajo técnico de marketing, no
contenido — mismo criterio que `notFound: '/404'` en `routes()`.

## Criterio de éxito
- ✅ `firebase.json` declara `hosting.redirects[0]` con `source: "/descargar"`,
  `destination` a la ficha de Play, `type: 301`.
- ✅ `node web/build.mjs --check` pasa (no afecta a páginas generadas).
- ✅ `WebSiteParityGuardrailTest` extendido y en verde — **falsificado**: se rompió el
  `applicationId` del destino a mano, el test cayó con `AssertionError` en la línea del nuevo
  check, se restauró y volvió a verde. Comando:
  `./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks`.
- ✅ `:app:testMockDebugUnitTest` (grafo mock) sigue en verde — UP-TO-DATE, el cambio no toca `:app`.
- ⚠️ `:shared:testDebugUnitTest` completo: 39 fallos, los 39 `UnsatisfiedLinkError` en
  `SQLiteConnectionNatives` — el bloqueo de App Control a la DLL de Kotlin/Native ya documentado
  (activo desde el 08-10, ajeno a este ticket). `WebSiteParityGuardrailTest` no es uno de ellos.
- ⏳ Deploy real (`firebase deploy --only hosting`) pendiente de permiso explícito del user — no
  ejecutado en esta tarea.

## Consumidores auditados
- `web/lib/config.mjs` — no toca nada, `PLAY_URL` sigue siendo la única fuente para el copy de la
  propia web; el redirect no se referencia desde ninguna página.
- `WebSiteParityGuardrailTest` — extendido (sección 1 de la clase), sin tocar las otras 7 pruebas.
- Ningún otro guard (Konsist, `LocaleParityGuardrailTest`, etc.) lee `firebase.json` ni `/descargar`.
- `web/README.md` — no se ha tocado; no hay tabla de "página nueva de nivel raíz" que actualizar,
  y esto no es una feature que la web "nombre" (no aparece en ningún copy).
