# Iconos exportados — los seis conceptos

Salida de `python docs/branding/icons/export_icons.py`. **Nada aquí se dibuja a mano**: los paths se
derivan de su fuente en cada corrida, así que si un icono cambia en la app, se vuelve a correr el
script y la exportación le sigue. Si cambia a mano, miente.

⛔ **El exportador no sólo escribe en esta carpeta: también genera `web/lib/glyphs.generated.mjs`**,
de donde `paparcar.com` saca el dibujo de plaza, parking y gasolinera. Ése es el motivo de que el
script exista y no sea un apaño de una tarde: la web llegó a declarar su propia plaza y su propio
parking a mano y los sirvió durante meses, en dos idiomas, con **glifos que la app había retirado**
[ICON-A-CONCEPT-KEEPS-ONE-GLYPH-ON-EVERY-SURFACE-001].

```bash
python docs/branding/icons/export_icons.py   # iconos + el modulo de la web
node web/build.mjs                           # sin esto, el sitio publicado se queda con lo de antes
```

Lo vigila `WebSiteParityGuardrailTest`, con tres preguntas y las tres falsificadas: que la web no
vuelva a **declarar** un glifo de concepto, que lo generado sea **lo que dibuja la app** (se compara
contra las constantes de `PaparcarIcons.kt`), y que el sitio **publicado** lo lleve.

| Concepto | Token en el código | Nivel | Fichero |
|---|---|---|---|
| Plazas que dejan otros conductores | `PaparcarIcons.CommunitySpot` | 3 · **marca propia** | `spot-community` |
| Parkings públicos | `PaparcarIcons.CarParkMark` | 3 · **marca propia** | `car-park` |
| Gasolineras | `PaparcarIcons.Fuel` | 2 · Material | `fuel-station` |
| Coche compartido | `PaparcarIcons.SharedCar` | 2 · Material | `shared-car` |
| Historial | `PaparcarIcons.History` | 2 · Material | `history` |
| Cifras | `PaparcarIcons.Figures` | 2 · Material | `figures` |

Todos se piden por **token**, nunca por el Material crudo: el glifo de un concepto se decide en
`PaparcarIcons.kt` y en ningún otro sitio. Por eso `Group` ya no vale para «3 en camino» —eso es
`PaparcarIcons.EnRoute`, una flecha—: un dibujo que significa dos cosas no significa ninguna.

## La que hubo que elegir: **cifras**

No había glifo para el concepto, y a la vez había dos: el paywall pintaba `Insights` en la tira de
motivo de `PaywallReason.STATS` mientras la fila que VENDE ese mismo gate llevaba `History`. Se
eligió **`BarChart`**, y la razón es de vocabulario, no de gusto: es el único candidato que no mete
una segunda idea en una rejilla donde esa idea ya está gastada.

- ⛔ **`Insights`** (exportado como `figures-alt-insights`) — su chispa es la de
  `Icons.Rounded.AutoAwesome`, que en esa MISMA rejilla ya significa «detectado automáticamente».
  Dos cosas distintas con la misma chispa a 24 dp.
- ⛔ **`Leaderboard`** (`figures-alt-leaderboard`) — es un podio: dice *ranking*, y aquí no se
  compite contra nadie.
- ✅ **`BarChart`** — barras y nada más. Sin lupa (ésa es `TravelExplore` = tiempo buscando plaza),
  sin chispa, sin podio.

## Formatos

- **`svg/`** — rejilla de 24, un `<path>` por trazo, `fill="currentColor"`. Es el entregable:
  el call site lo tiñe, igual que `tint` en Compose. Entra tal cual en Figma, en la web o en un
  `VectorDrawable`.
- **`png/`** — 512 px, fondo transparente, en las cuatro tintas que estas superficies llevan de
  verdad: `ink` (#0D1117, sobre papel), `white` (sobre el disco de color de un puck de mapa),
  `green` (#009F5E `PapGreenLight`, el relleno en tema claro) y `muted` (#8EA0B4, `onSurfaceVariant`
  en oscuro).
- **`preview.png`** — los ocho juntos, para mirarlos de una vez.

⛔ Los PNG son **derivados de conveniencia**, no la fuente. Para cualquier superficie que escale
(marcador, store, banner) se usa el SVG.

## De dónde sale cada dibujo

- Las dos marcas propias, de las constantes de `PaparcarIcons.kt`. La «P» vive en una rejilla de 48
  y el exportador **rehace las mismas cuentas** que su `addGroup` (mancha de `P_TARGET_HEIGHT`
  centrada en el cuadro de 24) en vez de reescribir sus números: corregir una letra a mano es como
  se pierde una letra.
- Los Material, del *sources jar* de `org.jetbrains.compose.material:material-icons-extended` en la
  versión que declara `gradle/libs.versions.toml` — el mismo artefacto que compila la app. No se
  bajan de un catálogo web, que publica **Material Symbols** Rounded y no el **Material Icons**
  Round que es lo que `Icons.Rounded.*` renderiza de verdad.

⚠️ La `P` se rellena por *winding* no-cero (su ojo es una elipse dibujada al revés). El SVG no
declara `fill-rule`, que es justo lo correcto: `nonzero` es el defecto de SVG. **Con `evenodd` el
asta abre un segundo hueco y la P se parte en dos** — si alguien reprocesa estos ficheros, que no
le ponga un `fill-rule` «por limpieza».
