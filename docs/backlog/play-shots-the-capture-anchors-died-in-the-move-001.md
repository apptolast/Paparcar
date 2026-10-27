# PLAY-SHOTS-THE-CAPTURE-ANCHORS-DIED-IN-THE-MOVE-001 · la sesión de capturas buscaba sitios de Dublín

**Estado:** ✅ Done

## Problema

`shoot.sh` no adivina coordenadas: busca sus objetivos **por el texto que sale en pantalla**, porque
una `y` fija es una moneda al aire que falla sin decirlo (medido el 16-09: la hoja de Home se posa a
alturas distintas en repeticiones idénticas). Eso convierte esos literales en una **dependencia del
mundo mock** — y nada la vigilaba.

Cuando [MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001] mudó el mundo a Madrid, **dos de las tres anclas se
quedaron apuntando a Dublín**:

| Ancla | Estado | Qué pasó |
|---|---|---|
| `"Camden Street, 21"` | ❌ muerta | la dirección del coche aparcado pasó a `"Calle de Fuencarral, 21"` |
| `"Northgate Fuel"` | ❌ muerta | las gasolineras se mudaron a Madrid |
| `"Drury Street Car Park"` | ⚠️ viva, pero **de Dublín** | la mudanza le cambió el `operator` a **EMT** y le dejó el nombre |

⛔ **Y el fallo es MUDO**: `anchor` escribe `WARN: … missing` y la sesión continúa, así que el frame
se publica con la ventana ampliada recortada del sitio equivocado. El coste se paga al final, en la
tienda. Se encontró **un mes después**, leyendo el script para planificar una re-captura.

## Diseño

### 1 · Las tres anclas vuelven a Madrid

- `sheet` → **`"Calle de Fuencarral, 21"`**, la dirección del coche aparcado en el fixture.
- `carpeek` → **`"Parking Plaza Mayor"`**.
- `fuel` → **`"Carburantes"`, por PREFIJO** (`--starts-with`), y no por brevedad: **todas** las
  gasolineras de Madrid llevan tilde (*Estación*, *Tetuán*, *Área*, *Chamartín*) y `uiquery.py`
  compara por **igualdad exacta**. Un carácter mal codificado entre bash, el `argv` de Windows y el
  dump XML pierde el ancla, y perderla sólo imprime un `WARN`. `"Carburantes"` es el único prefijo
  **sin acentos y único**.

### 2 · Los dos parkings dejan de ser de Dublín

`"Drury Street Car Park"` → `"Parking Plaza Mayor"` y `"Connolly Station Car Park"` →
`"Parking Las Ventas"`. Esto no es cosmética para el ancla: son los nombres que **salen en la
captura 5**, y un parking dublinés dentro de un Madrid es justo lo que la ficha no debe enseñar.

### 3 · El guard, que es el arreglo de verdad

`CaptureAnchorGuardrailTest` lee los literales que `shoot.sh` busca y exige que **cada uno exista en
los fixtures de `:mockkit`**. Es la mitad que es mecánicamente comprobable; que la fila esté en
pantalla, o que tenga otra debajo para el recorte de dos filas, sólo lo dice correr la sesión.

⛔ **Lleva además un chequeo de que el parser NO se ha quedado ciego** (≥ 3 anclas), y no es
decorativo: el primer borrador exigía el comando a principio de línea, el ancla del parking va
escrita `select_lane "4" && tap_text "…"` **a mitad de línea**, y encontró 2 de 3. Sin ese contador
el guard habría pasado en verde vigilando dos tercios de lo que cree vigilar.

## Verificación

- **Falsificado en las dos direcciones**: (a) renombrar un fixture con el ancla viva → rojo;
  (b) renombrar el comando en el script para cegar al parser → rojo. Restaurado, verde.
- `bash -n shoot.sh` limpio.
- `./gradlew testAll` verde.

## Consumidores auditados

| Consumidor | Veredicto |
|---|---|
| `tools/shoot.sh` | **cerrado** — las 3 anclas y los comentarios que las explican |
| `FakePublicParkingsSource.kt` | **cerrado** — los 2 nombres dublineses |
| `tools/uiquery.py` · `mockkit/tools/fetch_demo_routes.py` | **exentos** — los citan en ejemplos de uso, no los buscan |
| `docs/backlog/*` · `play-listing/README.md` | **exentos** — son el acta de lo que se hizo entonces |

## Lo que sigue pendiente

Esto **desbloquea** [PLAY-THE-STORE-SHOTS-STILL-LIVE-IN-DUBLIN-001] pero no lo cierra: las 72
capturas siguen siendo del **25-09**. Para rehacerlas falta la sesión de emulador, con el parche
temporal de los chips `DEV` (`if (false)`; ⛔ `alpha(0f)` no vale, el emoji se salta el alpha) y
`assembleMockDebug -PmockAsProd`.

⚠️ Y queda un riesgo que sólo se ve corriendo: el ancla de gasolinera tiene que caer en una fila
**con otra debajo**, porque el frame 4 amplía *esa fila y la siguiente*. `"Carburantes"` es la 2ª de
5 en el fixture, pero el orden en pantalla lo decide la distancia.
