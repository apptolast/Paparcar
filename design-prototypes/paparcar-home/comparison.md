# Comparativa de las tres direcciones y recomendación

> Acompaña a [`docs/design/home-ux-audit.md`](../../docs/design/home-ux-audit.md).
> Prototipos: `vehicle-first.html` · `smart-mobility.html` · `adaptive-home.html`
> Índice con la matriz interactiva: `index.html`

---

## 1 · Lo que las tres comparten (y por qué)

Mismo armazón (`assets/shell.js`), mismos tokens (`assets/base.css`), mismos datos de demostración
(`assets/proto.js`), mismo panel de estados. **Deliberado**: si cada propuesta trajera su propia
paleta, la comparación mediría gusto en vez de arquitectura.

Lo que **ninguna** de las tres toca:
- la navegación de 3 destinos y su regla editorial;
- el mapa como lienzo, con tus coches / tus zonas / tu viaje pintados siempre;
- las pestañas de sujeto (un sujeto a la vez, una capa de mapa);
- el vocabulario de honestidad: no preguntado ≠ vacío ≠ fallo;
- la detección. Ni una línea.

---

## 2 · Las tres, en detalle

### A · Vehicle-first

**Idea de producto.** Lo que Paparcar sabe siempre, y que ninguna otra app sabe, es dónde está tu
coche y si lo está vigilando. Eso merece el primer vistazo entero. Lo de alrededor sigue existiendo,
un nivel por debajo, sin recortar.

**Estructura.**

```
mapa (≈58 %)
┌─ peek ~190 dp  ───────────────────────────────┐
│ glifo 48 · EYEBROW de estado · NOMBRE         │
│ dirección · "hace 2 h 10 min · a 180 m"       │
│ [badge de vigilancia] [badge sin ubicación]   │
│ [acción principal]  [acción quieta]           │
└───────────────────────────────────────────────┘
al desplegar:  checklist (1 fila) → fila de salud del sistema →
               tus vehículos (si >1) → pestañas 1:1:1 → carril
```

**Jerarquía visual.** Un único foco: el nombre del coche en su color de identidad. El estado en
`onSurface`, que es la doctrina. Dos píldoras como máximo, siempre en el mismo sitio.

**Ventajas.**
- Contesta la pregunta nº 1 sin ninguna interacción, en todos los estados.
- Es la de **menor carga cognitiva**: un sujeto, un estado, una acción.
- Coste de implementación mínimo: todo sale de `HomePeekSlice` y `ParkedWatchBadge`, que ya existen.
- Hereda gratis la honestidad de la vigilancia.

**Inconvenientes.**
- **Desperdicia el dato cuando lo hay.** Con precios frescos a 300 m, la home no lo menciona.
- El mapa pierde ~100 px de alto visible en reposo.
- Para un usuario sin coche vigilado (el que más ayuda necesita) el peek dice poco.

**Riesgos de UX.**
- Que se lea como «una ficha del coche» y el usuario deje de esperar nada más de la home.
- Un peek alto y fijo reduce el mapa también cuando el usuario está explorando, no volviendo al
  coche.

**Sin datos.** Se degrada bien: el coche siempre tiene estado. El peor caso es «sin coche
registrado», donde el peek cae a una fila de alta de vehículo.

**Coste / riesgo técnico.** **Bajo.** Composición de datos existentes + reordenar items. No toca
ViewModels, ni slices, ni el disparador de las fetchs.

**A quién beneficia.** Al usuario que ya aparca con la app a diario, y a quien tiene el coche en
Bluetooth: su pregunta es siempre la misma.

---

### B · Smart mobility

**Idea de producto.** El valor recurrente está en el entorno. El coche se resume en una línea y el
primer nivel lo ocupa un carrusel de **oportunidades** —plaza libre, parking, precio de gasolina—
las tres con el mismo trato.

**Estructura.**

⚠️ Actualizado en la 2.ª vuelta (§6). La v1 resumía el coche a **una** línea; la revisión del user
le devolvió las dos, y las tarjetas ganaron su segundo destino:

```
mapa (≈48 %)
┌─ peek ~300 dp  ───────────────────────────────────┐
│ [🚗] Ford Focus · Aparcado hace 2 h   [vigilando] │  ¿qué · cuándo?
│      Calle de Embajadores, 118 · a 180 m          │  ¿dónde · cuán lejos?
│ ─────────────────────────────────────────────────  │
│ AQUÍ Y AHORA            lo que merece la pena cerca│
│ ┌ PLAZA LIBRE ─┐ ┌ PARKING ─────┐ ┌ GASOLINA 95 ┐ │
│ │ 4 min        │ │ 2,10 €/h     │ │ 1,419 €/L   │ │  ← el cuerpo abre
│ │ Plaza libre  │ │ P. Embajador.│ │ Ballenoil   │ │    ESE item
│ │ C/ Olivar·120│ │ público·240 m│ │ Méndez·1,4km│ │
│ ├──────────────┤ ├──────────────┤ ├─────────────┤ │
│ │ Ver las 4  › │ │ Ver las 3  › │ │ Ver las 3 › │ │  ← el pie abre
│ └──────────────┘ └──────────────┘ └─────────────┘ │    SU LISTA
└───────────────────────────────────────────────────┘
al desplegar:  PRIMEROS PASOS → salud del sistema → TU COCHE (+ acciones) →
               pestañas 1,25:1:1 → carril
al bajar al carril: la cabecera encoge el coche a una línea y se queda con las pestañas
```

**Jerarquía visual.** Un foco permanente (el coche, con el nombre en su color de identidad) y hasta
tres focos iguales en el carrusel. Cada tarjeta usa el rol **CIFRA** (`counter`) para su número, que
es exactamente para lo que ese rol existe, con el levante óptico medido de la fuente.

**Ventajas.**
- Es la que **mejor aprovecha los datos** cuando existen.
- Convierte el uso en recurrente: hay motivo para abrirla sin tener nada que aparcar.
- Reduce a **un** toque llegar a gasolineras (hoy son cuatro).
- Es la que mejor comunica que Paparcar es más que plazas.

**Inconvenientes.**
- **Su valor depende de datos que a menudo no existen.** Con los tres sujetos vacíos, el primer
  nivel de la app es una tarjeta que dice que no hay nada. Es la situación real de Paparcar hoy en
  muchas ubicaciones, y el prototipo lo deja ver a propósito.
- **Degrada el coche a una línea** justo cuando el estado del coche es lo único garantizado.
- Tres focos iguales = ningún foco. Es la de **mayor carga cognitiva**.
- Un carrusel horizontal esconde contenido a la derecha sin anunciarlo.

**Riesgos de UX.**
- Las tarjetas se leen como **publicidad de las propias features** (§10 H4 de la auditoría).
- Un precio en el primer nivel invita a interpretarlo como recomendación cuando sólo es un dato.
- Mezclar unidades (metros, €/h, €/L) en tres tarjetas contiguas dificulta la comparación.

**Sin datos.** **Es su punto débil, y es estructural, no de maquetación.** No hay forma honesta de
llenar ese hueco sin inventar.

**Coste / riesgo técnico.** **Alto.** Para que el carrusel esté poblado al abrir hay que pedir
gasolineras y parkings **sin** que el usuario entre en su carril — es decir, cambiar el disparador
documentado de `HomeFuelSlice` / `HomeParkingsSlice`, que existe para no gastar datos del usuario en
preguntas que no ha hecho. Y si el carrusel decide qué se pinta en el mapa, rompe la regla de
**un sujeto = un control** que `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001` dejó cerrada.

**A quién beneficia.** A una Paparcar futura con comunidad activa y cobertura de precios amplia.
Hoy, al usuario de una ciudad grande con buen feed de gasolineras.

---

### C · Adaptive home · **recomendada**

**Idea de producto.** La estructura no se mueve nunca. Lo que adapta es el **contenido** de dos
zonas, decidido por una **precedencia fija y pura** — el mismo patrón que `resolveDetectionStory`
usa ya para la detección.

**Estructura — cuatro zonas, altura reservada.**

```
mapa (≈52 %)
┌─ peek ~268 dp  ───────────────────────────────┐
│ ① SUJETO   glifo · EYEBROW · NOMBRE · línea   │  siempre
│ ② SALUD    [badge de vigilancia]              │  siempre
│ ─────────────────────────────────────────────  │
│ ③ NOTICIA  una línea, o MUDA (altura fija)    │  ← adapta
│ ─────────────────────────────────────────────  │
│ ④ ACCIÓN   [principal] [quieta]               │  ← adapta
└───────────────────────────────────────────────┘
al desplegar:  checklist (1 fila) → salud del sistema (sólo si añade algo) →
               tus vehículos → pestañas 1,3:1:1 → carril → alternativa útil
```

**La precedencia de la zona ③** (orden fijo, urgente → ambiente):

| # | Regla | Dispara cuando | Qué dice |
|---|---|---|---|
| 1 | `PERMISO_CORE` | falta permiso | **muda** — la fila de abajo lo pide |
| 2 | `PREGUNTA_ABIERTA` | hay prompt con plazo | **muda** — nada compite con una pregunta |
| 3 | `VIGILANCIA_MUERTA` | `ServicePresence.Dead` | **muda** — se arregla antes de ofrecer |
| 4 | `CONTRIBUCION` | acabas de liberar y hay plaza | «Tu aparcamiento ha liberado una plaza» |
| ~~5~~ | ~~`EN_RUTA`~~ | ~~viaje vivo~~ | **RETIRADA por el user (27-10)** — conduciendo es cuando MÁS sirve lo de alrededor, repostar incluido |
| 6 | `PLAZA_CERCA` | hay plaza de la comunidad | «Plaza libre a 120 m» |
| 7 | `PRECIO_VALIDO` | precio de TU producto, con hora | «Gasolina 95 a 1,489 €/L, a 300 m» |
| 8 | `PARKING_CERCA` | hay parking público | «3 parkings públicos en esta zona» |
| 9 | `SILENCIO` | nada afirmable | **muda** |

Cuatro de las ocho reglas **callan** (eran cinco de nueve hasta que el user retiró `EN_RUTA`). Eso
no es un hueco de diseño: es la doctrina del proyecto —*mejor falso negativo que falso positivo*—
aplicada a la home.

**Jerarquía visual.** Un foco permanente (el coche) y un segundo foco **opcional** separado por
divisores. Las pestañas se reponderan a 1,3 : 1 : 1 — la jerarquía que su propio KDoc declara y que
tres tercios idénticos no comunican. Nada se oculta: los tres siguen a un toque.

**Ventajas.**
- La única que trata «hay datos» y «no hay datos» como **dos estados de primera clase**.
- La única que resuelve el estado vacío con una **alternativa útil y acotada** (una, nombrada, sólo
  si se puede ofrecer).
- La única que lleva a la home **un hecho de valor propio** sin convertirla en dashboard: la
  contribución a la comunidad, que es noticia del ahora.
- La precedencia es un **caso de uso puro y testeable** — por `DET-VERDICT-NOT-PREDICATE-001`, su
  resultado se cita en pantalla, así que es un veredicto con su test, y se audita leyendo nueve
  líneas en vez de recorriendo un composable.
- **Escala sin rediseño**: cuando haya comunidad, la regla 6 gana más veces y el default puede
  volver a `SPOTS` cambiando una regla, no una pantalla.

**Inconvenientes.**
- El peek más alto de las tres (~268 dp): el mapa queda en ~52 % en reposo.
- Una precedencia mal ordenada se nota en pantalla — es poder, y por tanto responsabilidad.
- La zona ③ muda ocupa altura sin decir nada. Es el precio de que nada salte de sitio.

**Riesgos de UX.**
- Que la zona ③ se perciba como inestable (§10 H9). **Mitigación:** altura reservada + mismo sitio
  + un solo elemento. La estructura no se mueve ni una vez en las 3.456 combinaciones del panel.
- Que el silencio se lea como un fallo. **Mitigación:** no se dibuja una caja vacía, sólo un hueco
  tipográfico.

**Sin datos.** Es la mejor de las tres: la zona ③ calla, la ④ sigue ofreciendo la acción del
momento, y el carril ofrece la alternativa cuando la hay.

**Coste / riesgo técnico.** **Medio.** Se separa limpiamente:

| Pieza | Coste | Riesgo |
|---|---|---|
| Peek de 4 zonas | bajo — todo en `HomePeekSlice` | ninguno |
| Pestañas reponderadas | trivial | ninguno |
| Checklist plegado | bajo | ninguno |
| Alternativa «mira los parkings» | bajo — `publicParkingsShown` ya está | ninguno |
| Reglas 1-6, 8, 9 de la precedencia | medio — caso de uso + test | bajo |
| Regla 7 (`PRECIO_VALIDO`) | **alto** — exige precio sin entrar en el carril | ⚠️ cambia el disparador de `HomeFuelSlice` |

⚠️ **La regla 7 es la única que toca el modelo de datos, y por eso va en la fase 4**, no en la 1.
Hasta entonces la precedencia la salta y la zona ③ pasa a la regla 8. El diseño funciona sin ella.

**A quién beneficia.** A todos, pero sobre todo al usuario de hoy: el que a veces tiene plazas
cerca y casi siempre no.

---

## 3 · La ponderación, justificada

| Criterio | Peso | Por qué ese peso |
|---|---|---|
| Claridad del estado del vehículo | **15** | Es la pregunta nº 1 y la única que la app puede contestar siempre. Si falla, la home falla. |
| Utilidad sin plazas compartidas | **15** | Es el condicionante declarado del encargo y la realidad del producto hoy. |
| Facilidad de encontrar la acción principal | **12** | Una app de coche se usa de pie, con prisa, a menudo con una mano. |
| Toques para las tareas principales | **10** | Medible objetivamente, y hoy el peor dato (4 toques para gasolineras). |
| Carga cognitiva | **10** | El criterio de éxito del encargo lo pone primero: «se entienda antes». |
| Viabilidad y mantenimiento | **10** | App en fase temprana, un solo desarrollador. Una arquitectura que no se puede sostener no es un diseño. |
| Evolución con la comunidad | **10** | Lo que se decida ahora tiene que seguir valiendo con 2.000 usuarios. |
| Valor de gasolineras y precios | **8** | Valor recurrente real, pero **no** es la identidad del producto. |
| Legibilidad y relación con el mapa | **8** | El mapa es el lienzo; recortarlo tiene coste, aunque no sea el contenido. |
| Descubrimiento de funcionalidades | **7** | Importa, pero por debajo de que lo principal se entienda. |
| Coherencia del sistema de diseño | **5** | Ya es muy alta en el código; difícil de empeorar y difícil de mejorar. |
| Accesibilidad | **5** | Peso bajo **sólo** porque es un suelo no negociable, igual en las tres, no porque importe poco. |

Los pesos suman **115**, no 100: se declararon por importancia relativa y el total divide por la
suma real. Normalizarlos a mano habría supuesto retocar juicios para cuadrar una cifra.

---

## 4 · El cálculo

| Criterio | Peso | A | B | C | Hoy |
|---|---|---|---|---|---|
| Estado del vehículo | 15 | 5 | 3 | 5 | 2 |
| Acción principal | 12 | 5 | 3 | 5 | 3 |
| Útil sin plazas | 15 | 3 | 4 | 5 | 2 |
| Gasolineras y precios | 8 | 2 | 5 | 4 | 2 |
| Mapa | 8 | 3 | 2 | 3 | 5 |
| Descubrimiento | 7 | 3 | 5 | 4 | 3 |
| Toques | 10 | 4 | 5 | 5 | 2 |
| Carga cognitiva | 10 | 5 | 2 | 4 | 2 |
| Coherencia | 5 | 5 | 3 | 5 | 4 |
| Accesibilidad | 5 | 5 | 3 | 4 | 4 |
| Viabilidad | 10 | 5 | 2 | 4 | 5 |
| Evolución | 10 | 3 | 4 | 5 | 3 |
| **Suma ponderada** | | **461** | **392** | **519** | **333** |
| **Sobre 5** (÷115) | | **4,01** | **3,41** | **4,51** | **2,90** |

### Los puntos donde C **no** gana, y por qué no se han maquillado

- **Carga cognitiva: A 5, C 4.** A es más simple, y eso es verdad. C paga una zona más.
- **Mapa: hoy 5, C 3.** La home actual deja el mapa casi entero. C se come ~180 px más que hoy.
  Es el coste consciente de la propuesta.
- **Gasolineras: B 5, C 4.** B llega antes al precio. C lo cita sólo cuando es afirmable.
- **Viabilidad: A 5 y hoy 5, C 4.** A y el estado actual no tocan nada; C añade un caso de uso.
- **Accesibilidad: A 5, C 4.** Más zonas = más orden de foco que mantener correcto.

Si el criterio fuera sólo «lo más simple posible», **A ganaría**. Gana C porque dos criterios con
peso 15 —estado del coche **y** utilidad sin comunidad— sólo los resuelve una propuesta a la vez, y
ésa es C.

---

## 5 · Decisión

**C · Adaptive home**, con la estructura de A como su estado base y la ambición de B contenida en
una sola línea con permiso para callar.

Resumen de por qué, en una frase: **es la única que puede ser útil tanto el día que no hay nada
alrededor como el día que hay de todo, sin cambiar de forma entre los dos.**

### Lo que se implementa primero (y lo que NO)

La fase 1 es sólo **presentación**: peek de 4 zonas, pestañas reponderadas, checklist plegado,
cabeceras decorativas fuera. Sin esa parte, nada de lo demás se nota; con ella, el 70 % del
diagnóstico queda resuelto sin un campo nuevo en el estado.

La precedencia completa (fase 2) y la regla del precio (fase 4) van después, en ese orden, porque la
segunda es la única que toca cómo se piden los datos. El plan por fases está en
[`interaction-spec.md`](interaction-spec.md) §9, que desde el 29-10 es la spec de **B** — la dirección elegida. La precedencia de C sigue descrita en §2 de este documento y navegable en `adaptive-home.html`.

### Solución híbrida: sí, pero con un modelo de interacción único

Lo que se toma de cada una no son tres mecanismos pegados: es **un** modelo —*sujeto fijo + noticia
opcional por precedencia*— que da la claridad de A, admite el valor de B cuando existe, y calla
cuando no. Si la noticia no cupiera en una línea, o necesitara su propio carrusel, dejaría de ser
este modelo y pasaría a ser B. Ése es el límite.

---

## 6 · Segunda vuelta (27-10) — el user se inclina por **B**

> *«Por ahora, me gusta más el B.»*

Lo que eso cambia, y lo que no.

### 6.1 Las decisiones de la revisión **no eran de B**: eran del producto

Siete puntos salieron de la revisión y cinco de ellos valen para cualquier alternativa, así que
están aplicados a **las tres** ([`b-decisions.md`](b-decisions.md)):

| Decisión | ¿De B? | Aplicada a |
|---|---|---|
| Gramática de **dos líneas** del coche («Aparcado hace 2 h» / «Calle… · a 180 m») | no | A · B · C |
| Peek de detalle **unificado** para plaza, parking y gasolinera | no | A · B · C |
| Cabecera que pasa al carril **sólo al leerlo** (y lleva las pestañas) | no | A · B · C |
| **Cabeceras de provincia** («Primeros pasos», «Tu coche») | no | A · B · C |
| Pestañas **reponderadas** 1,25 : 1 : 1 | no | A · B · C |
| Tarjeta con **dos destinos** (su item · su lista) | **sí** | B |
| **Umbrales** de lo que es «notable» | sí, en su forma | B los enseña · C los usa para su línea |

**La consecuencia importante: B y C ya no discrepan sobre qué es afirmable.** C elige su línea con
los **mismos** `notables()` que B usa para sus tarjetas. Así que la diferencia entre las dos se ha
reducido a **una sola pregunta de producto**:

> ¿El primer vistazo ofrece **hasta tres** cosas del entorno, o **una** elegida por nosotros?

Y eso no lo decide una matriz de puntuación. Lo decide qué app quieres que sea Paparcar.

### 6.2 Lo que la matriz de §4 ya no mide bien

Tres filas se puntuaron contra la primera vuelta y hoy están desactualizadas, **a favor de B**:

| Fila | Era | Por qué cambia |
|---|---|---|
| Claridad del estado del vehículo | A 5 · B **3** · C 5 | B ya lleva el coche en dos líneas con estado, cuándo, dónde, a cuánto y badge. **Sube a 5.** |
| Toques para las tareas principales | A 4 · B 5 · C 5 | iguales: las tres comparten cabecera por scroll y peek de detalle |
| Coherencia del sistema de diseño | A 5 · B **3** · C 5 | B ya no maqueta nada propio: usa las piezas compartidas. **Sube a 5.** |

Con eso, **B pasa de 3,41 a ≈3,93** y sigue por debajo de C (4,51) **por dos filas y sólo dos**:
**carga cognitiva** (tres focos siguen siendo tres focos) y **viabilidad** (poblar el carrusel al
abrir exige pedir datos que hoy no se piden hasta entrar en el carril).

⛔ **No he vuelto a puntuar la matriz entera.** Repuntuar después de saber qué alternativa prefiere
el user es exactamente cómo se fabrica una justificación a posteriori. Las cifras de §4 se quedan
como quedaron, con esta nota al lado.

### 6.3 Lo que sigue siendo verdad de B, y que hay que aceptar al elegirla

1. **Su valor depende de datos que a menudo no existen.** Se ha hecho honesto —sin notables la
   sección no existe, ni como caja vacía— pero eso significa que **hay estados en los que B es
   exactamente A**, con un peek 100 dp más alto. Pon los tres sujetos a «ninguno» y compáralos.
2. **Tres focos siguen siendo tres focos.** Es la fila donde A gana a las dos.
3. ⚠️ **El riesgo técnico no ha desaparecido, se ha localizado**: para que el carrusel esté poblado
   al abrir hay que pedir parkings y gasolineras **sin** que el usuario entre en su carril. Ese
   disparador existe para no gastar datos del usuario en preguntas que no ha hecho, y cambiarlo es
   una decisión de producto. Mientras no se cambie, B abre con **una** tarjeta (la de plazas, que sí
   es suscripción viva) y las otras dos aparecen cuando el usuario visita sus carriles — que es
   bastante menos de lo que B promete.

**Eso último es lo que de verdad hay que decidir antes de implementar B**, y está escrito como
pregunta abierta en [`b-decisions.md`](b-decisions.md) §8.
