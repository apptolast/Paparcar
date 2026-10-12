# DOCS-THE-README-MUST-MATCH-THE-TREE-001 · el README describía un árbol de hace 33 commits

**Estado:** ✅ **Done** (30-09-2026) · sin rama ni worktree: cambio de documentación pedido y
commiteado directamente en `master` a petición del user.

## Problema

El README se verificó por última vez contra `ad101968a` (24-09). Master iba por `d5e3e3f71`, 33
commits después, y entre medias habían entrado la marca de 2026, el módulo `:mockkit`, el carril de
release de iOS, el ciclo de vida de la plaza y el corte de 1.4.0. Un README con fecha y commit en la
cabecera promete haber sido verificado: cuando deja de serlo, el que miente no es un número, es la
promesa.

Tres clases de error distintas, y solo la primera es la inofensiva:

1. **Contadores viejos.** 27 guardarraíles (son 33), 374 ficheros de `commonTest` (383), 52 de
   `androidUnitTest` (58), 106 ficheros en `domain/detection/` (107), 717 docs de backlog (758),
   «3.700+ tests» (3.832 `@Test`).
2. **Afirmaciones que habían dejado de ser verdad.** La *compensación óptica* de grosor de rueda
   (6 sobre oscuro / 7 sobre claro) describía un dibujo que ya no existe: la marca de 2026 no tiene
   ni un trazo. Medido — `grep -c stroke docs/assets/paparcar-logo.svg` → **0**. Y «Publicación iOS
   ❌ 0%: sin certificados, sin lane» con `iosApp/fastlane/Fastfile` y sus 6 lanes en master desde
   `6970b4065`.
3. **Estructura que apuntaba a código movido.** El árbol seguía poniendo `fakes/` dentro de
   `shared/src/commonMain/`, de donde salieron el 18-09, y `:mockkit` no aparecía en ninguna parte —
   ni en la tabla de estado, ni en el árbol, ni en la sección del Dev Catalog. O sea: el README no
   sabía del módulo que existe *precisamente* para que los fakes no entren en el binario de iOS.

## Doctrina

*Un doc vivo afirma en presente y declara contra qué commit fue verificado.* El README lo declara en
su cabecera, así que su fecha es una **afirmación comprobable**, no un adorno. Y
[[project_docs_living_docs_must_match_master_001]]: si no coincide con el código, es un bug del doc.

## Lo que se hizo

Todo lo escrito se verificó contra el árbol, no contra la versión anterior del README:

- Cabecera a `2026-09-30` / `d5e3e3f71`; badge a `1.4.0 (vc 15)` — **distinguiendo** lo que está en
  producción (1.3.0, vc 14) de lo que está cortado y firmado sin subir (1.4.0, `8c45ce88a`).
- Los seis contadores, cada uno de un comando: `find`, `grep -c '@Test'`, `ls | wc -l`.
- Sección de logo reescrita: muere la compensación de grosor, entran las **dos** excepciones de disco
  con su pintura medida (banner de Play: disco tinta + aro verde al 28 %; web: disco verde + glifo
  tinta, 12,97:1), el criterio numérico que las separa de la app (`F_MARK = 1.0` frente a
  `F_EMBLEM = 0.52896`) y el lienzo **no cuadrado** `244 × 236` full bleed.
- `:mockkit` en la tabla, en el árbol y en Dev Catalog, con el motivo (161 clases `Fake*` exportadas
  a `ComposeApp.h` = guideline 2.3.1) y su guard.
- Publicación iOS pasa a 🟡 con la advertencia que importa: el carril está entero en master y **nada
  está verificado** — se mergeó desde Windows, sin Xcode.
- CI: el job Apple ya no solo compila el klib, también corre `commonTest` en el simulador y construye
  la app con `xcodebuild`. Y está verde otra vez (los 3 últimos push a master, en éxito), así que el
  ✅ vuelve a ser honesto.
- Entra el **ciclo de vida de la plaza**, que el README no mencionaba: `RELEASING` → `PROVISIONAL` →
  `CONFIRMED`, con `RETRACTED` como **estado** y no como borrado, y el `status` en el modelo `Spot`.
- Releases gana las cuatro trampas medidas: el `versionName` vive en DOS sitios con guard
  (`WebSiteParityGuardrailTest`), el AAB es derivado y no se versiona pero su commit sí, la firma
  solo se comprueba con `-printcert`, y en Play el rumano es `ro`.

## Verificación

- ⛔ **Ningún guard lee el README de la raíz**: los dos que mencionan «README» —
  `PlayListingPasteParityGuardrailTest` y `WebSiteParityGuardrailTest` — lo hacen en comentarios, y
  apuntan a `docs/release/play-listing/README.md` y `web/README.md`. Por eso no se corre `testAll`
  para un cambio que no toca ni un byte de Kotlin: no hay nada que pueda romper.
- `grep` final por los números viejos (`27 guardarra`, `374 fichero`, `717 docs`, `3.700`, `grosor`,
  `fakes/`): sin restos, salvo las dos menciones de 1.3.0 que son deliberadas.

## Queda abierto (fuera del encargo, no tocado)

- ✅ **`docs/ROADMAP.md:42`** y **`docs/IOS_PLAN.md`** — CERRADOS el **09-10** por
  [DOCS-THE-IOS-DOCS-MUST-MATCH-THE-TREE-001](docs-the-ios-docs-must-match-the-tree-001.md). Y había
  más de lo que esta lista veía: el ROADMAP daba Room en **v10** (va por **v20**), F8 «etapa 1 en
  rama» (está en master, `3d1d80a67`) y una tabla de **cuatro ramas en vuelo** sin que exista
  ninguna; el IOS_PLAN pedía `BGTaskScheduler` ya implementado y daba por ausente un
  `GoogleService-Info.plist` que está gitignored a propósito.
- ✅ **El logo de la cabecera del README** — CERRADO el **13-10** por [BRAND-THE-README-MARK-NEEDS-ITS-LIGHT-GREEN-001](brand-the-readme-mark-needs-its-light-green-001.md): la superficie pasa a `themed=True`, el mismo mecanismo del favicon. ⚠️ No hizo falta `<picture>` ni un segundo fichero. Lo que esta línea decía, y sigue siendo el motivo:
  el `#25F48C` horneado, en GitHub con tema claro, da
  **1,42:1**, que es justo el caso que `PapGreenLight` existe para resolver. Arreglarlo es una
  superficie nueva en `export_surfaces.py` (un `<picture>` con variante clara), no una edición del
  README — el logo se genera, no se copia.
