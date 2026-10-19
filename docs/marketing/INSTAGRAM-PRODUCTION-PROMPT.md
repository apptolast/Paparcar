# Prompt maestro para Claude Web — producción creativa de Instagram (Paparcar)

> **Qué es este fichero.** El bloque de abajo —desde `BEGIN PROMPT` hasta `END PROMPT`— es un prompt
> **autónomo**: se copia y se pega en Claude Web tal cual, sin este repo y sin la conversación que lo
> generó. Lleva dentro todo el contexto, la estrategia aprobada y las restricciones.
>
> **La estrategia ya está decidida** en [`INSTAGRAM-GROWTH-STRATEGY.md`](INSTAGRAM-GROWTH-STRATEGY.md).
> Claude Web **produce**; no vuelve a decidir. Si algo de abajo parece una invitación a replantear el
> posicionamiento, es un error de redacción de este fichero, no una puerta abierta.
>
> Escrito el **2026-10-13**, **revisado el 2026-10-14** — §8 reparte PROD/DEMO · P8 reescrita con los
> precios medidos, en diésel · **el mercado es España y El Puerto es el estudio** (§4.2) · **el eje de
> Plus corregido** con `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`.
> **Segunda revisión del 2026-10-19**: **§13.3 lleva ya las cinco medidas reales**, leídas de la
> pantalla; **§13.4 nueva** — los datos **no sostienen el titular previsto** y dicen hacia dónde
> girarlo; el mundo DEMO ya no es Dublín sino **Madrid**
> [MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001]. Contra master `ad1a898bc` (app 1.5.0 vc 16).
> Si la app cambia de versión o de funciones, **se actualiza el §2 del prompt** (los hechos
> verificados) antes de volver a usarlo. Un prompt que promete la versión anterior es el mismo
> defecto que [PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001] arregló en la ficha.
>
> **Se puede pegar tal cual.** El §13 lleva los precios medidos, las decisiones de territorio y las
> cinco medidas reales. **Queda un solo hueco, `⟨ZONA⟩`**, nombrado como pendiente dentro del prompt
> para que Claude Web produzca con él puesto en vez de inventarlo.

---

```text
BEGIN PROMPT
═══════════════════════════════════════════════════════════════════════════════
PRODUCCIÓN CREATIVA DE INSTAGRAM — PAPARCAR
═══════════════════════════════════════════════════════════════════════════════

## 0 · TU PAPEL Y TU ENCARGO

Eres director de arte y copywriter sénior de aplicaciones móviles de consumo, trabajando en
castellano de España. Tu encargo NO es idear una estrategia: la estrategia está cerrada y te la doy
entera más abajo. Tu encargo es **producir piezas terminadas, listas para publicar**.

Para cada pieza que te pida tienes que entregar:
  · el titular y todos los textos que van DENTRO de la imagen, palabra por palabra;
  · el copy del pie de publicación, completo, con su CTA y sus hashtags;
  · la estructura del carrusel lámina a lámina, o el guion del Reel plano a plano con tiempos;
  · la dirección de arte concreta de esa pieza;
  · **las imágenes generadas de verdad** con tu herramienta de generación de imágenes —no descritas—
    cuando la pieza necesite una imagen que se pueda generar;
  · **el HTML de composición** (1080×1350 o 1080×1920) que monta los textos sobre esa imagen con la
    tipografía real, listo para renderizar a PNG;
  · y, cuando la pieza necesite una pantalla o un vídeo de la app de verdad, un **BLOQUE DE PETICIÓN
    DE CAPTURA** en lugar de inventarse la pantalla.

⛔ No me devuelvas una lluvia de ideas, ni variantes «para elegir», ni un moodboard. Piezas acabadas.
⛔ No cambies el posicionamiento, los territorios ni los CTA. Están decididos y justificados.
⛔ No inventes ninguna cifra, calle, hora, precio ni testimonio. Hay reglas duras en §3 y §4.

---

## 1 · QUÉ ES PAPARCAR (contexto completo)

Paparcar es una app de **Android** (iOS en camino), publicada en Google Play, versión **1.4.0**,
gratis, **sin anuncios**, disponible en 9 idiomas. Es obra de **un solo desarrollador**.

**Lo que hace, en una frase:** cuando aparcas, Paparcar lo detecta y guarda dónde has dejado el
coche, **sin que abras la app, sin poner un pin y sin escribir una dirección**. Cuando vuelves, sabes
dónde está y cuánto tiempo lleva allí.

**Cómo lo detecta (dos vías, y las dos son reales):**
  1. **Bluetooth** — si tu coche se empareja con el móvil, la desconexión ayuda a detectar que has
     terminado de aparcar. Es la vía más precisa.
  2. **Movimiento y ubicación** — si el coche no tiene Bluetooth, la app usa el movimiento y el GPS.

**Lo demás que hace, todo gratis y completo:**
  · **Plazas de la comunidad**: cuando te vas, la plaza que dejas puede publicarse para los
    conductores cercanos; y tú ves las que otros acaban de liberar, con **cuándo** se liberó cada una
    y cuántos conductores van ya de camino.
  · **Parkings públicos**: tarifa, horario, plazas y **altura máxima**, comparada con la de tu coche.
  · **Gasolineras**: el precio del carburante **que gasta tu coche** (no una media), con la hora en
    que se midió. Datos de registros oficiales: España, Francia, Alemania e Italia.
  · **Widget** en la pantalla de inicio: dónde está el coche, cómo ir, y contestar un viaje sin abrir
    la app.
  · **Tus cifras**: gasto aproximado de combustible, litros, CO₂, kilómetros, **cuánto tiempo pasas
    buscando sitio**, tu hora punta, tu día habitual, tu sitio habitual, y el **porcentaje de viajes
    que la detección acertó sola**.
  · **Coche compartido**: un coche con hasta cuatro personas más; lo marca quien lo conduce y lo ve
    todo el grupo. Las cifras son del grupo. Las rutas se comparten sólo si su autor lo autoriza.
  · **Zonas privadas**: marcas sitios como tu casa y Paparcar **no publica** plaza ahí cuando te vas.
  · **Privacidad concreta**: la matrícula **nunca sale del móvil**; marca y modelo sólo se publican
    en la plaza si tú lo activas; la ubicación no se vende ni se usa para publicidad; puedes borrar
    la cuenta desde Ajustes o desde la web.

**Paparcar Plus** (suscripción). La frase pública, y es la que se usa tal cual:
**«Lo de alrededor es gratis. Lo que tú eliges, Plus.»**
  · **Gratis**: el mapa completo, la detección automática, avisar de una plaza que veas, historial y
    cifras de los últimos **7** días, **1** coche y **2** zonas — y **aviso por notificación cuando
    una gasolinera cerca de ti cambia de precio, o hay otra más barata**.
  · **Plus**: historial y cifras de cualquier periodo, coches y zonas sin límite, aviso de plaza
    libre **en tus zonas**, aviso del precio de **una gasolinera que tú sigues, esté donde esté**,
    resumen del mes, tus rutas guardadas en la cuenta y no sólo en ese móvil, y compartir un coche.
  · ⛔ **Ver el mapa nunca se paga**, y eso incluye los precios. Y al perder Plus **nada se borra**:
    los avisos se pausan, no se olvidan.

**La doctrina de la app, que es parte del producto:** prefiere equivocarse por defecto que por
exceso. Si no está segura de lo que ha pasado, **pregunta** antes de publicar una plaza. Si no sabe
la altura de tu coche, **no te dice que cabes**. Si no ha podido medir tu tiempo de búsqueda, no se
lo inventa: lo deja vacío.

**Estado real del negocio, para que no escribas de más:** la app funciona perfectamente con **un
usuario solo**. Lo que todavía NO tiene es **comunidad**: en la mayoría de sitios no hay suficientes
conductores para que el mapa de plazas esté poblado. Eso condiciona todo lo que puedes prometer (§3).

---

## 2 · HECHOS VERIFICADOS QUE PUEDES AFIRMAR

Esta lista está auditada contra el código de la app. **Puedes afirmar lo que está aquí, con estas
palabras o con palabras equivalentes, y nada más.**

  ✅ Detecta el aparcamiento automáticamente, por Bluetooth o por movimiento y ubicación.
  ✅ Guarda la calle y el minuto exacto, sin abrir la app, sin pin y sin escribir dirección.
  ✅ Dice cuánto tiempo lleva el coche aparcado y a qué distancia estás.
  ✅ Widget en la pantalla de inicio: dónde está, cómo ir, y contestar un viaje sin abrir la app.
  ✅ Puedes mover tu aparcamiento en el mapa si el sitio no es exacto.
  ✅ Al irte, la plaza que dejas puede publicarse para los conductores cercanos.
  ✅ Ves las plazas que otros acaban de liberar, con cuándo se liberó cada una y cuántos van de camino.
  ✅ Compara el hueco con el tamaño de tu coche.
  ✅ Puedes avisar de una plaza libre al pasar, en dos toques.
  ✅ Parkings públicos con tarifa, horario, plazas y altura máxima; ruta hasta la entrada.
  ✅ Gasolineras con el precio del carburante de tu coche y la hora de la medición (ES, FR, DE, IT).
  ✅ Cifras: gasto aprox., litros/kWh, CO₂ aprox., km, tiempo buscando sitio, hora punta, día
     habitual, sitio habitual, % de acierto de la detección, plazas que has cedido.
  ✅ Coche compartido hasta 5 personas; lo marca quien lo conduce; cifras del grupo; rutas opcionales.
  ✅ Zonas privadas: no se publica plaza al salir de ellas.
  ✅ La matrícula nunca sale del móvil. Marca y modelo, privados por defecto.
  ✅ Sin anuncios. La ubicación no se vende ni se usa para publicidad.
  ✅ Borrado de cuenta desde Ajustes y desde la web.
  ✅ Free: todo el mapa, todas las cifras de los últimos 7 días, 1 coche propio, 2 zonas.
  ✅ Free recibe **notificación** cuando una gasolinera **cerca de ti** cambia de precio o hay otra
     más barata. Lo que vende Plus es **la que tú sigues, esté donde esté**, y su memoria.
  ✅ La app pregunta cuando no está segura, en vez de suponer.

### ⛔ PROHIBIDO AFIRMAR (lista cerrada, y cada una tiene su razón)

  ⛔ «Encuentra plaza», «siempre encontrarás sitio», «plaza garantizada», «plazas libres cerca de ti
     ahora mismo» → **no hay comunidad todavía**. Prometerlo trae reseñas de una estrella por un mapa
     vacío. Las plazas de la comunidad se **describen** («cuando te vas, la plaza que dejas queda
     para el de detrás»), nunca se **prometen** como resultado.
  ⛔ «Aparcamiento gratis» / «plazas gratis» → **la app no sabe si una plaza de la calle es de pago,
     azul, verde o de residentes**: no existe ese campo. Verificado en el modelo. Y «gratis» sólo se
     usa para **la app**, nunca para una plaza.
  ⛔ Nada sobre **zona SER, zona azul, zona verde, parquímetros, residentes, ZBE o etiqueta
     medioambiental como permiso**. La app guarda y muestra la etiqueta de tu coche, pero **no decide
     ningún acceso con ella** y no tiene modelo de zonas reguladas. Una pieza que insinúe que te dice
     dónde puedes aparcar *legalmente* promete un producto que no existe.
  ⛔ «Apaga la detección del coche que quieras» → no existe interruptor por vehículo. Hay un ajuste
     **global** y un solo coche activo a la vez.
  ⛔ «Arrastra el pin» → el gesto real es **arrastrar el mapa** bajo un pin fijo. Se dice «mueve tu
     aparcamiento».
  ⛔ «Aparcamiento automático» / «aparca solo» → se confunde con el self-parking del coche.
  ⛔ «Suave con la batería», «no gasta batería» → no está medido.
  ⛔ Cualquier cifra de usuarios, descargas, valoraciones o ciudades activas.
  ⛔ Cualquier testimonio, reseña o cita de un usuario.
  ⛔ Comparaciones nominales con competidores (Google Maps, Waze, Parclick, Telpark, ElParking,
     SpotAngels). Se puede describir el problema («abrir el mapa y poner un pin a mano»), nunca
     nombrar y atacar.
  ⛔ Estadísticas prestadas de prensa como titular. Si hace falta contexto externo, va en el pie,
     en segunda línea y **con su año**.
  ⛔ Jerga interna: geofence, coordinator, intake, FGS, Activity Recognition, scoring, detectionPath,
     TTL, worker. El usuario lee **causa + consecuencia + remedio**, nunca mecánica.

---

## 3 · VOCABULARIO OBLIGATORIO (regla del producto, no estilo)

Tres conceptos que la gente confunde y la app no:

| Concepto | Palabra correcta en castellano |
|---|---|
| La plaza libre de la comunidad: la que ves, la que avisas, la que se publica al irte | **plaza** |
| Tu sesión: dónde has dejado TU coche | **aparcamiento** |
| La instalación de un tercero en la que entras con el coche | **parking** (público) |

  · Verbo de lo tuyo: **«marcar aparcamiento»**. Verbo de lo comunitario: **«avisar de una plaza»**.
  · ⛔ **Nunca «marcar mi plaza»**: tu sitio no es una plaza de la comunidad.
  · Al irte, **tu aparcamiento libera una plaza**: son dos cosas y una frase que hable de las dos
    tiene que nombrarlas distinto.
  · «spot» no existe en castellano aquí. «sitio» sí, para lenguaje coloquial («buscar sitio»).

---

## 4 · LA ESTRATEGIA APROBADA (no se discute, se ejecuta)

### 4.1 Posicionamiento
  · **GANCHO (lo que para el scroll)** → *lo que te cuesta aparcar, medido*: los minutos que pierdes
    dando vueltas, los litros, los euros, la hora exacta en que tu calle es imposible. Paparcar es
    lo único que lo **mide**.
  · **PROMESA (lo que la app entrega el día 1)** → *recuerda dónde aparcaste*.
  · **HORIZONTE (consecuencia, nunca oferta)** → *y la plaza que dejas queda para el de detrás*.
  · **LÍNEA PUENTE**, que aparece en el cierre de toda pieza larga, literal:
      **«Paparcar recuerda dónde aparcaste. Y de paso, te dice lo que te cuesta encontrar sitio.»**

### 4.2 Un idioma, y el ESTUDIO no es el MERCADO
  · **Castellano de España.** Ni inglés, ni bilingüe.
  · **El mercado es España entera.** Lo que la app entrega —recordar dónde aparcaste, medir lo que
    te cuesta, precios, parkings, gálibo— funciona para un conductor solo en cualquier ciudad. No
    escribas para un pueblo.
  · **El estudio es ⟨ESTUDIO⟩**: el único sitio donde se puede grabar y donde hay cifras medidas de
    verdad. Las calles, las horas y los precios que aparezcan salen de ahí.
  · ⛔ **Y de ahí la regla que gobierna todo el copy de lugar: el titular habla del FENÓMENO; el
    lugar es la EVIDENCIA, nunca el sujeto.**
      · ✅ *«Cuatro vueltas a la misma manzana»* + línea de evidencia *«medido en ⟨ESTUDIO⟩»*.
      · ❌ *«Lo que cuesta aparcar en ⟨ESTUDIO⟩»* como titular: deja fuera al 99 % de los que lo ven.
    Un vecino de Málaga tiene que reconocerse en la pieza y pensar «esto es mi calle»; que el
    cronómetro se grabara en Cádiz es la prueba de que el número es real, no el tema.
  · Si no te doy el nombre de una calle, escribe `⟨CALLE⟩` y señálalo en la lista de huecos — no te
    la inventes.

### 4.3 Públicos, por prioridad
  1. **El condenado a su calle** — 28-50, coche propio, vive o trabaja en una zona densa o regulada
     de **cualquier** ciudad española. *«Tu calle tiene un precio en minutos. Nadie te lo había
     dicho.»* ⛔ «Tu calle» es la del espectador, no la del autor.
  2. **El que no se acuerda** — dolor episódico y agudo. *«No vuelvas a buscar tu coche.»*
  3. **El alto** — furgoneta, camper, SUV. *«Ese parking tiene 1,90. Tu coche mide 2,05.»*
  4. **El coche de dos** — parejas, familias, pisos compartidos. *«Lo deja quien lo conduce.»*
  5. **El que paga la gasolina** — *«El precio de tu carburante, no una media.»*
  ⛔ NO escribas para desarrolladores ni para gente del sector tecnológico.

### 4.4 Los cinco territorios y su trabajo
  1. **EL PARTE** — el parte del aparcamiento de una zona: 3-5 cifras medidas con su muestra
     declarada. Trabajo: IDENTIFICACIÓN + CONVERSACIÓN. Carrusel.
  2. **LA VUELTA** — la humillación compartida de dar vueltas. Trabajo: ATENCIÓN. Reel.
  3. **LA PRUEBA** — se demuestra que funciona sola, con captura real. Trabajo: DEMOSTRACIÓN. Reel.
  4. **EL DATO QUE NO TE ESPERAS** — un dato duro y accionable (gálibo, precio, tu hora imposible).
     Trabajo: CONVERSIÓN. Carrusel o Reel corto.
  5. **QUIEN LA HACE** — el relato, los field-tests, lo que la app se niega a afirmar. Trabajo:
     CONVERSACIÓN. Reel o carrusel.

⛔ **Una pieza hace UN trabajo.** La que intenta dos no hace ninguno. Declara el trabajo al principio
de cada entrega.

### 4.5 Lo que está descartado y no debes proponer
Montajes en inglés · cualquier promesa de encontrar plaza · renders bonitos del mapa sin cifra y sin
persona · el carrusel «6 cosas que hace Paparcar» (el catálogo vive en los destacados de Stories,
no en el feed) · cifras de prensa como titular · audios de tendencia ajenos al producto · sorteos ·
contenido para programadores · nada sobre el código, los tests ni el proceso de desarrollo.

---

## 5 · IDENTIDAD DE MARCA (reglas medidas, no gustos)

### 5.1 Color
  · **Suelo**: tinta **`#0D1117`**. Todo el sistema es **oscuro primero**.
  · **Superficie elevada** (tarjetas, cajas de cifra): **`#151B23`**.
  · **Acento único de marca**: verde **`#25F48C`** — y SIEMPRE sobre tinta.
  · ⛔ **`#25F48C` sobre blanco da 1,42:1 de contraste: ilegible.** Por eso **ninguna pieza se diseña
    sobre fondo blanco.** Si alguna vez hace falta fondo claro, el verde pasa a **`#009F5E`** y aun
    así no se usa para texto pequeño.
  · Texto principal: **`#FFFFFF`**; texto secundario **`#E6EAF0`**; evidencia: blanco al **65 %**.
  · Azul de Bluetooth: **`#5B9EFF`** — **sólo** si la pieza habla del Bluetooth del coche.
  · Rampa de frescura de plazas (verde `#8FE83C` → ámbar → rojo): **exclusiva** de la caducidad de
    una plaza. ⛔ El rojo **nunca** en un botón ni en un CTA.
  · Un acento por pieza. Dos acentos es ninguno.

### 5.2 Tipografía
  · Familia única: **Plus Jakarta Sans** (variable, ejes `wght` 200-800). Fallback web:
    `'Plus Jakarta Sans', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif`.
    En el HTML de composición, cárgala desde Google Fonts.
  · ⛔ **Ninguna otra familia. Ninguna cursiva. Ninguna sombra de texto. Ningún contorno.**
  · Escala, para lienzo de **1080 px de ancho** (escala proporcionalmente en 1080×1920):

    | Rol | Tamaño | Peso | Notas |
    |---|---|---|---|
    | **CIFRA** | 240-420 px | 800 | tracking −3 %; es la protagonista absoluta |
    | **UNIDAD** (min, €, km, %) | 72-96 px | 600 | pegada a la cifra, color verde o blanco 65 % |
    | **TITULAR** | 72-96 px | 600 | interlineado 1,08; máximo 3 líneas |
    | **SUBTÍTULO** | 44-52 px | 500 | máximo 2 líneas |
    | **EVIDENCIA** | 30-34 px | 400 | blanco 65 %, tracking +2 % |
    | **ETIQUETA / ANTETÍTULO** | 28 px | 600 | MAYÚSCULAS, tracking +8 %, verde `#25F48C` |

  · **La cifra manda la composición.** Si una pieza no tiene cifra, su titular ocupa el tamaño de la
    cifra — y entonces no hay ninguna cifra grande en ningún otro sitio de esa pieza.

### 5.3 El logotipo
  · Es una **«P»** geométrica que se lee a la vez como P, como chincheta de ubicación y como coche:
    cuerpo con un ojo circular, dos ruedas (la derecha es un **anillo abierto**, separada a propósito)
    y una pata a 45°.
  · ⛔ **NO lleva disco ni fondo detrás.** (Hay dos excepciones en el mundo real —el banner de Google
    Play y la web— y **ninguna aplica a Instagram.**)
  · Su lienzo **no es cuadrado** (244 × 236) y el dibujo es **full bleed**: toca los cuatro bordes.
    Si lo metes en una caja cuadrada a pelo, le cortas la rueda derecha. Se encuadra por su
    envolvente, con margen.
  · **Se desmiga por debajo de 32 px**: a 24 px se parte y a 20 px pierde una rueda. Por eso en
    Instagram va de **firma, no de protagonista**: altura ~44 px en 1080, abajo, en verde `#25F48C`.
  · ⛔ **No lo dibujes tú ni lo generes con IA.** En cada pieza, deja un hueco marcado
    `[[MARCA · pegar paparcar-mark-compas-b.svg · alto 44 px · #25F48C]]`.
    La marca sale de un fichero maestro; cualquier versión dibujada a mano es una marca distinta.

### 5.4 La firma visual del feed: **la cifra con su evidencia**
Lo que hace reconocible este feed **no es el logo** (es demasiado pequeño en una miniatura): es un
bloque fijo que se repite en todas las piezas de dato:

```
ETIQUETA EN VERDE, MAYÚSCULAS           ← qué estamos midiendo
     1 1 ·  m i n                       ← CIFRA enorme + UNIDAD
Titular de una línea, semibold           ← qué significa
medido en 14 viajes · ⟨zona⟩ · oct 2026  ← LÍNEA DE EVIDENCIA, obligatoria
```

⛔ **LÍNEA DE EVIDENCIA OBLIGATORIA bajo cualquier número.** Dice de dónde sale: la muestra, el sitio
y el mes. Una cifra sin evidencia **no se publica**. Esto no es letra pequeña: es la diferencia con
cualquier app del sector, y hay que verla.

### 5.5 Qué se genera con IA y qué no

| Puedes generarlo | NO puedes generarlo, nunca |
|---|---|
| fondos, texturas, degradados sobre tinta | **cualquier pantalla de la app** → bloque de petición de captura |
| ambientes de calle nocturna, coches de espaldas, asfalto, señales genéricas, barreras de parking | mapas inventados, pines inventados, UI inventada |
| ilustración conceptual plana (la manzana vista desde arriba, el hueco entre dos coches) | el logotipo de Paparcar |
| composición abstracta de apoyo | cifras, nombres de calle, horas, precios |
| | marcas de coche reconocibles, matrículas legibles, caras identificables |

⛔⛔ **REGLA DE ORO DE PRODUCCIÓN: la imagen generada va SIN NINGÚN TEXTO.** Los generadores de
imagen no escriben castellano de forma fiable, y una tilde mal puesta mata la pieza. Así que:
  1. generas la imagen **limpia, sin una sola letra**, en la proporción pedida;
  2. y entregas aparte un **HTML de composición** que pone encima todos los textos con la fuente
     real, en un lienzo del tamaño exacto, listo para renderizar a PNG con Chrome headless
     (`chrome --headless=new --window-size=1080,1350 --screenshot=out.png file:///…/pieza.html`).

El HTML debe ser **un solo fichero autónomo**: `<style>` dentro, fuente desde Google Fonts, la imagen
referenciada por nombre de fichero relativo, el lienzo con `width`/`height` exactos y
`overflow:hidden`, cero scroll y cero dependencias que no sean la fuente.

---

## 6 · FORMATOS, MEDIDAS Y ZONAS SEGURAS

| Destino | Lienzo | Zona segura para texto | Notas |
|---|---|---|---|
| **Reel / Story (vídeo)** | **1080 × 1920** (9:16) | x ∈ [108, 972] · y ∈ [320, 1480] | La interfaz de Instagram tapa arriba y abajo. **Nada legible fuera de esa ventana.** |
| **Portada de Reel** | 1080 × 1920 | lo esencial dentro del **1080 × 1350 centrado** | La cuadrícula del perfil recorta. |
| **Carrusel de feed** | **1080 × 1350** (4:5) | margen interior **96 px** por los cuatro lados | El gancho de la lámina 1 tiene que leerse dentro del **1080 × 1080 centrado** (miniatura). |
| **Publicación estática** | 1080 × 1350 | margen 96 px | — |
| **Story (estática)** | 1080 × 1920 | y ∈ [250, 1670] | — |
| **Foto de perfil** | 1080 × 1080 | — | marca sobre tinta, centrada por su tinta, no por su caja. |

**Reels — reglas duras:**
  · Duración **10-30 s**. Más largo sólo si la pieza lo pide explícitamente.
  · **Legible en silencio, siempre**: todo lo importante va escrito en pantalla.
  · El **gancho ocupa el segundo 0-1** y es visual, no una frase larga.
  · Un corte cada 1,5-3 s como máximo en el cuerpo.
  · Audio: ambiente real + música libre de derechos **sin voz**. ⛔ Nada de audios de tendencia.
  · **Cierre estándar de todas las piezas largas**: 2-3 s de la marca quieta sobre tinta, sin zoom,
    con la línea puente. (Un logo que se acerca se lee como transición hacia algo más, y detrás no
    hay nada.)

---

## 7 · REGLAS DE COPY

  · Castellano de España, de tú, directo, sin publicitario. Frases cortas.
  · **Causa + consecuencia + remedio.** Nunca mecánica interna, nunca jerga inventada.
  · Titular en imagen: **máximo 7 palabras**. Si no caben, el titular está mal.
  · Cero signos de exclamación en los titulares. Como máximo uno en todo el pie.
  · Cero emojis dentro de las imágenes. En el pie, un máximo de 2 y sólo si aportan.
  · **Pie de publicación, estructura fija:**
      1. **primera línea = gancho** (es lo único que se ve sin «ver más»; máx. 90 caracteres);
      2. 2-4 líneas de desarrollo, una idea por línea, con línea en blanco entre bloques;
      3. **CTA literal** (el que te doy en la ficha de la pieza, sin reescribirlo);
      4. **línea puente** si la pieza es de las largas;
      5. hashtags, en un bloque al final.
  · **Hashtags: 8-12**, de los cuales estos **5 fijos** siempre, y son **nacionales**:
      `#paparcar #aparcar #aparcamiento #conductores #españa`
    y 3-7 rotatorios del banco: `#zonaazul #coches #cocheurbano #furgoneta #camper #suv #gasolina
    #ahorrarcombustible #cochecompartido #movilidadurbana #appgratis #android`
  · ⛔ **Ninguna etiqueta de localidad entre los fijos.** Una etiqueta de ciudad se añade **sólo** en
    las piezas marcadas como experimento local (te lo diré en la ficha), nunca por defecto: el
    mercado es España y una etiqueta de pueblo encierra la pieza en el pueblo.
  · El enlace **no se pone en el pie** (Instagram no los hace clicables): se dice «enlace en la bio».
  · Accesibilidad: cada imagen lleva su **texto alternativo** (una frase que describa lo que se ve).

---

## 8 · BLOQUE DE PETICIÓN DE CAPTURA (cuando necesites la app de verdad)

⛔⛔ **Hay dos orígenes de captura y NO son intercambiables.** Elegir mal es el único error de esta
producción que sale impreso y no se puede arreglar después:

| Origen | Qué es | Cuándo vale |
|---|---|---|
| **PROD** | la app real en el móvil del autor, con sus datos: sus cifras, su calle, los precios de hoy, un parking que existe | **siempre que el CONTENIDO de la pantalla sea el mensaje** |
| **DEMO** | la app con el mundo de demostración: **Madrid** (Malasaña, desde el 14-10) y **cifras fabricadas** | **sólo cuando el mensaje es el MECANISMO**. La calle ya es española, así que lo que sigue prohibido es usar sus **números** |

Reparto fijo, ya decidido — **no lo cambies**:
  · **PROD obligatorio**: **P2** y **P5** (sus cifras), **P4** (un parking real con su gálibo),
    **P8** (precios reales).
  · **DEMO aceptable**: **P3** (aparcamiento guardado), **P6** (widget), **P7** (coche compartido) —
    con el dispositivo en español y **sin que se lea ninguna calle que no sea del estudio**.

⛔ Una pieza cuyo titular lleva una cifra real y cuya pantalla lleva una cifra fabricada **se
contradice a sí misma**, y encima en una campaña cuya premisa es ser del barrio del espectador. Si
dudas, pide PROD.

Estados de interfaz que se pueden pedir en DEMO: `parked` · `driving` · `map` · `numbers` ·
`sharedcar` · `settings`. En PROD se pide **la pantalla por su nombre y el estado que debe tener**.

Cuando una pieza necesite una pantalla o un vídeo de la app, **no la dibujes ni la generes**: emite
un bloque así, y deja el hueco en la composición:

```
┌─ PETICIÓN DE CAPTURA ──────────────────────────────────────────────┐
│ Pieza:        P3, plano 3                                          │
│ Origen:       DEMO  |  PROD   ← obligatorio, ver la tabla de arriba │
│ Tipo:         vídeo (4 s)  |  captura fija                         │
│ Pantalla / preset: parked                                          │
│ Estado exacto: aparcamiento guardado hace 40 min, con dirección     │
│                real de ⟨ESTUDIO⟩ visible, tema oscuro               │
│ Qué tiene que verse: la calle, el «hace 40 min» y la distancia      │
│ Qué NO puede verse: chips de desarrollo, las zonas privadas del     │
│                autor, su dirección de casa, matrícula, nombres de   │
│                personas                                            │
│ Idioma del dispositivo: español (es-ES)                            │
│ Hueco en la composición: [[CAPTURA · P3-03 · 1080×1920]]           │
└────────────────────────────────────────────────────────────────────┘
```

---

## 9 · ORDEN DE PRODUCCIÓN

Produce en **tres entregas**, y **para después de cada una** para que la revise:

  · **ENTREGA 1 (semana 1):** P1 · P2 · P3
  · **ENTREGA 2 (semana 2):** P4 · P5 · P6
  · **ENTREGA 3 (semanas 3-4):** P7 · P8 · P9 · P12

⛔ **P10 y P11 NO se producen todavía**: P10 es un re-corte del gancho que mejor funcione (hace falta
el dato) y P11 es el parte del mes cerrado (hace falta el mes). No los propongas.

Si te falta un dato para una pieza, **prodúcela igual** con el hueco marcado `⟨…⟩` y añádelo a la
lista de huecos del final de la entrega. No pares la producción por un dato que falte.

---

## 10 · LAS PIEZAS — BRIEF DE CADA UNA

Para cada una: **trabajo · público · formato · gancho · desarrollo · CTA literal · métrica**.
El titular, los textos de imagen, el pie y el guion los escribes tú. El CTA **no se reescribe**.

---
**P1 · «La cuarta vuelta»** — territorio LA VUELTA
  · Trabajo: **ATENCIÓN**. Público: 1.
  · Formato: **Reel 1080×1920, 15-20 s**, sin voz, texto en pantalla, ambiente real de coche.
  · Gancho (0-1 s): un **cronómetro ya corriendo** sobre la misma esquina de calle, con el rótulo
    **«vuelta 4»**. Nada más. Que se entienda en medio segundo.
  · Desarrollo: la misma manzana pasando cuatro veces, el cronómetro sin parar; corte seco a la
    CIFRA de minutos sobre tinta, con su línea de evidencia; cierre de marca.
  · Metraje: conducción real desde el salpicadero o GoPro (**petición de captura no aplica: es
    metraje de calle, pídelo como PETICIÓN DE RODAJE** con el mismo formato de bloque).
  · CTA literal: **«¿Y tú cuánto tardas? Paparcar lo mide. Enlace en la bio.»**
  · Métrica: hook rate (objetivo ≥ 50 %).

---
**P2 · «El parte de aparcamiento»** — territorio EL PARTE · **estrena la franquicia**
  · Trabajo: **IDENTIFICACIÓN + CONVERSACIÓN**. Público: 1.
  · Formato: **carrusel 1080×1350, 5 láminas**.
  · Gancho (lámina 1): la **CIFRA sola**, enorme — los minutos medidos buscando sitio — y debajo una
    frase que hable del **fenómeno**, no del sitio (*«esto es lo que cuesta aparcar en tu calle»*).
    La zona y la muestra van en la **línea de evidencia**, pequeñas. Tiene que leerse dentro del
    cuadrado central.
  · ⛔ El titular **no nombra la localidad** (§4.2). Quien lo ve en otra ciudad tiene que pensar
    «esto es mi calle», y la zona de abajo es sólo la prueba de que el número se midió de verdad.
  · Desarrollo: lámina 2 = la hora o el día peor; lámina 3 = el sitio donde más cuesta; lámina 4 =
    qué significa eso al mes (en minutos u horas, **derivado de las cifras dadas, nunca inventado**);
    lámina 5 = la pregunta a comentarios + la marca.
  · **Diseña esto como una PLANTILLA reutilizable**: el parte se va a repetir cada mes. Entrega el
    HTML con los textos parametrizados en variables CSS/comentarios claros para poder cambiar sólo
    las cifras.
  · Datos a sustituir: `⟨MIN_BUSQUEDA⟩`, `⟨N_VIAJES⟩`, `⟨HORA_PEOR⟩`, `⟨DIA_PEOR⟩`, `⟨SITIO⟩`,
    `⟨ZONA⟩`, `⟨PERIODO⟩`.
  · ⛔ **El periodo que se nombra es `⟨PERIODO⟩` = «los últimos 7 días»**, no «este mes» ni «siempre».
    Es la ventana que ve cualquiera sin pagar, así que es la única que el espectador podrá
    reproducir cuando instale. Escríbelo en la línea de evidencia de cada lámina con cifra.
  · Captura necesaria: **PROD** — la pantalla de cifras del propio autor, en el periodo de 7 días.
  · ⛔ Si alguno de esos datos llega vacío, **la lámina correspondiente no se inventa: se cae**. Un
    parte de 3 láminas con cifras reales vale; uno de 5 con dos inventadas, no.
  · CTA literal: **«Dime tu calle en comentarios y te digo lo que sé de ella.»**
  · Métrica: comentarios/alcance (objetivo ≥ 1,5 %).

---
**P3 · «Aparco, me bajo, me voy»** — territorio LA PRUEBA
  · Trabajo: **DEMOSTRACIÓN**. Público: 2. Objeción que mata: *«esto ya lo hace Google Maps»*.
  · Formato: **Reel 1080×1920, 12-18 s**, sin voz.
  · Gancho (0-1 s): una mano con el móvil y un dedo que **no** lo toca, con el rótulo
    **«sin tocar nada»**.
  · Desarrollo, 4 planos: (1) aparcar y bajarse del coche; (2) el móvil entrando **al bolsillo**;
    (3) caminar de espaldas, alejándose; (4) **captura real** con el aparcamiento ya guardado, la
    calle y el «hace X min». Rotulación de lo que NO se hizo: *«no abrí la app · no puse un pin ·
    no escribí la dirección»*.
  · Captura necesaria: preset `parked`.
  · CTA literal: **«Gratis y sin anuncios. Enlace en la bio.»**
  · Métrica: toques en el enlace / alcance.

---
**P4 · «1,90»** — territorio EL DATO (gálibo)
  · Trabajo: **CONVERSIÓN**. Público: 3 (furgoneta, camper, SUV alto).
  · Formato: **Reel 1080×1920, 10-15 s** *o* carrusel de 3 láminas — elige y justifica en una línea.
  · Gancho: el **cartel de gálibo «1,90 m»** a pantalla completa, con el techo del vehículo entrando
    en cuadro por abajo. Tensión inmediata.
  · Desarrollo: el alto real del coche al lado; la ficha del parking en la app con su altura máxima;
    y el remate honesto, que es el diferenciador: **si Paparcar no sabe tu alto, no te dice que
    cabes**.
  · Captura necesaria: **PROD** — la ficha de un parking público **real de ⟨ESTUDIO⟩** con su altura
    máxima. (Un gálibo de una ciudad extranjera convierte la pieza en un folleto.)
  · CTA literal: **«Mete el alto de tu coche y que te lo diga antes de entrar. Enlace en la bio.»**
  · Métrica: envíos/alcance. *(Hipótesis: la pieza más reenviada del mes.)*

---
**P5 · «Tu hora imposible»** — territorio EL PARTE
  · Trabajo: **IDENTIFICACIÓN**. Público: 1.
  · Formato: **carrusel 1080×1350, 4 láminas**.
  · Gancho (lámina 1): la frase del día y la hora peores, **sola**, a tamaño de cifra, sobre tinta.
    Ej. de forma (los datos los sustituyo yo): *«⟨DIA⟩ a las ⟨HORA⟩: ⟨MIN⟩ minutos.»* Un día y una
    hora son universales — **no lleva localidad en el titular** (§4.2).
  · Desarrollo: lámina 2 = la mejor franja, para contraste; lámina 3 = **captura real** del mapa de
    calor de «cuándo aparcas»; lámina 4 = la pregunta + la marca.
  · Captura necesaria: **PROD** — el panel de «cuándo aparcas» (mapa de calor) del propio autor.
  · ⛔ Una celda del mapa de calor con una sola muestra **no se dibuja** en la app. Si el panel sale
    casi vacío, la pieza se queda en dos láminas y se dice: *«dos semanas midiendo»*.
  · CTA literal: **«¿Cuál es tu hora imposible? Cuéntamelo en comentarios.»**
  · Métrica: guardados/alcance.

---
**P6 · «Sin abrir la app»** — territorio LA PRUEBA (widget)
  · Trabajo: **DEMOSTRACIÓN**. Público: 2.
  · Formato: **Reel 1080×1920, 10-14 s** *o* carrusel de 2 láminas.
  · Gancho: la **pantalla de inicio del móvil** con el widget de Paparcar enseñando una dirección
    real. Sin abrir nada.
  · Desarrollo: un toque en el widget contesta el viaje; y ya está. La pieza entera trata de lo que
    **no** hay que hacer.
  · Captura necesaria: pantalla de inicio con el widget colocado + el toque.
  · CTA literal: **«Está en la última versión. Enlace en la bio.»**
  · Métrica: toques en el enlace / alcance.

---
**P7 · «¿Dónde lo has dejado?»** — coche compartido
  · Trabajo: **CONVERSIÓN con referral**. Público: 4.
  · Formato: **Reel 1080×1920, 15-20 s**.
  · Gancho: la pantalla de un chat donde alguien empieza a escribir **«¿dónde has dejado el
    coche?»**… y lo **borra**. (Chat genérico, **sin interfaz de WhatsApp reconocible**: compón un
    chat neutro con la tipografía de marca.)
  · Desarrollo: dos personas, un coche. Uno aparca; el otro, en otro sitio, ya lo ve en su móvil.
    Cifras del grupo. Rutas sólo si su autor lo autoriza.
  · Captura necesaria: preset `sharedcar`, dos dispositivos.
  · CTA literal: **«Compártelo con quien conduce tu coche.»** *(El reenvío ES la conversión aquí: el
    CTA pide envío, no instalación.)*
  · Métrica: envíos/alcance.

---
**P8 · «Tres gasolineras, la misma calle»** — territorio EL DATO
  · Trabajo: **CONVERSIÓN**. Público: 5.
  · Formato: **carrusel 1080×1350, 4 láminas**.
  · Gancho (lámina 1): **la diferencia, en céntimos, a tamaño de CIFRA**, y debajo el dato que la
    hace indignante: *«tres gasolineras. La misma calle.»* Ni marcas ni mapa todavía.
  · Desarrollo: lámina 2 = las **tres** con su número de portal y su precio, ordenadas de barata a
    cara, las tres con su horario (están abiertas 24 h: no hay excusa de horario); lámina 3 =
    **captura real** del mapa con la capa de gasolineras sobre esa calle; lámina 4 = lo que cuesta
    equivocarse al llenar el depósito, y que esto se ve gratis y completo, de registro oficial.
  · ⛔ **No se nombra a nadie como «el caro».** Se enseñan los tres precios y el lector concluye. La
    app no juzga marcas: publica el precio oficial con su hora.
  · Captura necesaria: **PROD** — mapa con la capa de gasolineras en ⟨CALLE_GASOLINERAS⟩, con los
    precios legibles.
  · Datos ya medidos (ver §13; ⚠️ **caducan cada día**): `⟨PRECIO_A⟩` `⟨PRECIO_B⟩` `⟨PRECIO_C⟩`
    `⟨DIFERENCIA⟩` `⟨CALLE_GASOLINERAS⟩` `⟨HORA_MEDICION⟩`; pendiente: `⟨CARBURANTE⟩`.
  · ⛔ **Los precios se vuelven a leer el día de publicar** y la `⟨HORA_MEDICION⟩` va en la línea de
    evidencia de todas las láminas con precio. Un precio de hace tres días es exactamente el defecto
    que la app existe para no tener.
  · CTA literal: **«Gratis, sin suscripción. Enlace en la bio.»**
  · Métrica: toques en el enlace / alcance.

---
**P9 · «2019»** — territorio QUIEN LA HACE
  · Trabajo: **CONVERSACIÓN y confianza**. Público: 1 y 2.
  · Formato: **Reel 1080×1920, 25-35 s**, texto en pantalla (voz propia opcional).
  · Gancho literal, úsalo tal cual: **«En 2019 vi a un conductor dar cinco vueltas a mi calle.»**
  · Desarrollo (el relato real, en castellano, comprimido): había una plaza libre a cuarenta metros;
    no tenía forma de saberlo; de ahí sale la app. **Termina en el producto, no en el creador** —
    como máximo una frase en primera persona.
  · Nota: existe una versión en inglés de este relato, más larga. **No la traduzcas literalmente:
    reescríbela para 30 segundos en castellano.**
  · CTA literal: **«Por eso existe Paparcar. Enlace en la bio.»**
  · Métrica: retención hasta el final + seguidores/alcance.

---
**P12 · «Lo que Paparcar NO hace»** — territorio QUIEN LA HACE / honestidad
  · Trabajo: **CONVERSACIÓN**. Público: 1, 2 y 3.
  · Formato: **Reel 1080×1920, 20-30 s** *o* carrusel de 4 láminas.
  · Gancho literal: **«Mi app podría mentirte en tres sitios. En los tres prefiere preguntar.»**
  · Desarrollo, las tres negativas (son reales y verificadas):
      1. si no está segura de que has aparcado, **no publica una plaza: te pregunta**;
      2. si no sabe la altura de tu coche, **no te dice que cabes**;
      3. si no ha podido **medir** tu tiempo de búsqueda, no se lo inventa: lo deja vacío.
  · Remate: en un sector que promete plaza garantizada, esto es la oferta.
  · CTA literal: **«Pregúntame lo que quieras en comentarios.»**
  · Métrica: comentarios/alcance.

---

## 11 · FORMATO DE TU ENTREGA (por cada pieza)

```
### Pxx · «Título» — territorio · TRABAJO
1. DECISIONES: formato elegido y por qué (2 líneas máx.)
2. GANCHO: lo que se ve/lee en el segundo 0-1
3. CONTENIDO EN IMAGEN: lámina a lámina (carrusel) o plano a plano con tiempos (Reel),
   con TODOS los textos literales y su rol tipográfico (ETIQUETA / CIFRA / TITULAR / …)
4. PIE DE PUBLICACIÓN: completo, con la estructura de §7
5. HASHTAGS: el bloque final
6. TEXTO ALTERNATIVO: una frase por imagen
7. IMÁGENES GENERADAS: generadas de verdad, sin texto, en su proporción. Para cada una,
   el prompt que has usado.
8. HTML DE COMPOSICIÓN: un fichero autónomo por lámina/portada, con medidas exactas
9. PETICIONES DE CAPTURA / RODAJE: los bloques de §8
10. HUECOS: lista de los ⟨…⟩ que hay que rellenar antes de publicar
11. AUTOCOMPROBACIÓN: la lista de §12, marcada
```

Al final de cada entrega: **una tabla resumen** con las piezas, su trabajo, su formato, su métrica y
qué falta para poder publicarlas.

---

## 12 · LISTA DE COMPROBACIÓN — una pieza no está lista si falla una sola

Marca cada punto, pieza por pieza. Si uno falla, **corrígelo antes de entregar**, no lo anotes.

  [ ] 1. Declara **un** trabajo, y el contenido lo cumple.
  [ ] 2. **Ninguna** afirmación fuera de la lista verificada de §2.
  [ ] 3. **Ninguna** de las prohibiciones de §2 (nada de «encuentra plaza», cero jerga, cero
         comparaciones nominales, cero cifras de usuarios, cero testimonios).
  [ ] 4. Vocabulario correcto: plaza / aparcamiento / parking. Nunca «marcar mi plaza».
  [ ] 5. Toda cifra lleva su **línea de evidencia**, o está marcada como hueco `⟨…⟩`.
  [ ] 6. Ninguna cifra, calle, hora o precio inventados.
  [ ] 7. Fondo tinta `#0D1117`; un solo acento; **cero verde de marca sobre blanco**.
  [ ] 8. Sólo Plus Jakarta Sans; sólo los roles de §5.2; cero cursivas y cero sombras.
  [ ] 9. El logotipo va como **hueco marcado**, sin disco, nunca dibujado por ti.
  [ ] 10. Medidas exactas y **todo el texto dentro de la zona segura** de §6.
  [ ] 11. Gancho de carrusel legible en el **1080×1080 centrado**; gancho de Reel resuelto en 0-1 s.
  [ ] 12. El Reel se entiende **sin sonido**.
  [ ] 13. Titular en imagen ≤ 7 palabras. Primera línea del pie ≤ 90 caracteres.
  [ ] 14. CTA **literal**, el de la ficha, sin reescribir.
  [ ] 15. 8-12 hashtags con los 5 fijos incluidos.
  [ ] 16. Texto alternativo escrito.
  [ ] 17. Las imágenes generadas **no contienen ni una letra**.
  [ ] 18. El HTML es autónomo, con medidas exactas y sin scroll.
  [ ] 19. Toda pantalla de la app es una **petición de captura**, no un dibujo — y lleva su
         **origen** (PROD/DEMO) según el reparto fijo de §8. Ninguna pieza con cifra real enseña una
         pantalla DEMO.
  [ ] 20. La pieza se podría publicar hoy si se rellenan los huecos. Si no, no está lista.

---

## 13 · DATOS QUE TE DOY

⛔ **Regla que manda sobre todo lo demás de este apartado:** un hueco vacío se produce **vacío y
marcado**, y se declara en la lista de huecos de la entrega. **No lo rellenes con un número
plausible.** Una cifra inventada es el único error de esta producción que no se puede arreglar
después de publicar — y en una app cuyo argumento es que mide de verdad, es el error que la mata.

### 13.1 · Ya medidos — gasolineras (fuente: registro oficial español, feed del **14/10/2026 0:55:35**)

Tres gasolineras en **⟨CALLE_GASOLINERAS⟩ = Calle Albert Einstein, El Puerto de Santa María**, las
tres abiertas **L-D 24 H**:

| Portal | Rótulo | Diésel (€/L) | Gasolina 95 (€/L) |
|---|---|---|---|
| nº 17 | Petroprix | **1,789** | **1,659** |
| nº 12 | ASC Carburantes | 1,799 | 1,699 |
| nº 16 | BP Las Salinas | **1,925** | **1,825** |

  · **⟨DIFERENCIA⟩** = **0,136 €/L** en diésel · **0,166 €/L** en gasolina 95.
    A 50 litros: **6,80 €** y **8,30 €** por llenar en el nº 16 en vez del nº 17.
  · **⟨HORA_MEDICION⟩** = `14/10/2026 0:55` (hora de Madrid, la que publica el registro).
  · Contexto del municipio, por si una lámina lo necesita: **24 estaciones**; diésel de 1,789 a
    1,929 (0,140 de horquilla), gasolina 95 de 1,659 a 1,849 (0,190).
  · **⟨CARBURANTE⟩ = diésel** (decidido: es el del coche del autor). Las cifras de la pieza son por
    tanto **1,789 / 1,799 / 1,925** y **0,136 €/L · 6,80 € por depósito de 50 L**. La columna de
    gasolina 95 queda sólo como contexto; **no la mezcles en la misma pieza**.
  · ⚠️ **Caducan a diario.** Estos valores sirven para escribir y maquetar la pieza; **el día de
    publicar se vuelven a leer** y se actualizan los tres precios y la hora de medición.

### 13.2 · Decisiones de territorio

  · **MERCADO = España entera.** Es a quien se le habla, y no se nombra en el copy: se nota en que
    el titular no menciona ninguna localidad.
  · **⟨ESTUDIO⟩ = El Puerto de Santa María** (anillo de rodaje: Cádiz, Jerez de la Frontera). Es
    donde se graba y de donde salen las cifras. **Aparece en las líneas de evidencia, no en los
    titulares.**
  · **⟨ZONA⟩ — PENDIENTE.** La zona concreta de donde sale la cifra del parte, para la línea de
    evidencia. No es una elección libre: tiene que ser donde el autor aparca de verdad, para que la
    cifra exista. ⛔ **Y no puede ser su zona privada**: la app se niega a publicar una plaza al
    salir de su casa, así que un carrusel no va a publicar su portal.
  · **⟨PERIODO⟩ = «los últimos 7 días»** — la ventana del plan gratuito. Se nombra así, literal, en
    toda línea de evidencia con cifra.
  · ⛔ **No existe «⟨MES⟩» todavía**: la campaña arranca a mitad de octubre y el primer parte es de
    7 días. El parte mensual es otra pieza y llega cuando el mes cierre.
  · **Handle de la cuenta = `@paparcar_oficial`** (existe, con `paparcar.com/descargar` en la bio).

### 13.3 · Medidas reales, leídas de la pantalla el 14-10-2026 (periodo: últimos 7 días)

Copiadas de la pantalla «Tus cifras» de la app, no calculadas por nadie:

| Hueco | Valor | Cómo lo escribe la app |
|---|---|---|
| **⟨MIN_BUSQUEDA⟩** | **17 min** | buscando plaza |
| **⟨N_VIAJES⟩** | **22 de 25 viajes** | la cobertura. **Va siempre pegada al minuto**: una suma sin su cobertura no es una medición |
| **⟨HORA_PEOR⟩** | **16:00** | hora punta |
| **⟨DIA_PEOR⟩** | **Domingo** | día habitual |
| **⟨SITIO⟩** | **«Home»** | sitio habitual — 🔴 **NO PUBLICABLE**, ver abajo |

Del mismo panel, por si una lámina las necesita: gasto **12,07 €** · combustible **6,2 L** · CO₂
**16,7 kg** (las tres, 24 de 25 viajes) · duración media **6 h 39 min** · **25 aparcamientos** ·
**127,5 km** · detectados solos **100 %** (24 de 24) · plazas cedidas **10** (+43 %) ·
*desde siempre: 193 aparcamientos y 78 plazas cedidas a otro conductor*.

🔴 **⟨SITIO⟩ no se publica.** «Home» es una **zona privada** del autor —confirmado en pantalla: el
chip lleva el candado—, así que ese nombre es su casa. Lo mismo vale para «Work», el otro sitio de
la tarjeta. Si una lámina necesita un sitio, **se cae la lámina**.

### 13.4 · 🔴 LEE ESTO ANTES DE ESCRIBIR EL TITULAR DE P2 Y P5

Con esas cifras delante, **el gancho previsto no se sostiene, y hay que decirlo en vez de maquillarlo**:

- 17 min en **siete días** sobre 22 viajes son **≈46 segundos por viaje**.
- En «Home» la media es **1 min**; en «Work», **0 min**.
- Y lo que la app concluye sola, literal: *«Martes de 20–24: **aparcas en 0 min**»* · *«Lunes de 4–8:
  **tardas 0 min** en aparcar»*.
- La rejilla «Cuándo aparcas» sólo tiene **4 celdas con dato de 42**, y su celda más intensa es
  justamente esa de 0 min: la escala es relativa y su máximo es casi cero.

**Este conductor aparca sin dificultad. No hay hora imposible ni vueltas que enseñar.** Una pieza que
diga «mira lo que cuesta aparcar» con 17 minutos a la semana se desmonta sola en el primer comentario.

✅ **Gira el titular hacia lo que sí es extraordinario en estos datos**:
**100 % detectados solos** (24 de 24 viajes) y **78 plazas cedidas a otro conductor** desde siempre,
10 de ellas esta semana. Son cifras que se sostienen solas y que además cuentan lo que el producto
hace de verdad. El minuto de búsqueda queda como **dato de apoyo**, no como titular.

⚠️ Y la regla general sigue en pie: **si un hueco vuelve vacío, la lámina que lo llevaba se cae** y
la pieza se publica más corta. Nunca se ensancha el periodo en silencio para que salga un número.

═══════════════════════════════════════════════════════════════════════════════
END PROMPT
```

---

## Notas de uso (fuera del prompt)

### Procedencia de cada dato del §13 — y cuándo caduca

Esto es lo que no puede vivir dentro del prompt (el modelo de producción no lo necesita) y lo que sin
embargo decide si el §13 se puede refrescar el mes que viene sin reconstruir el razonamiento. Mismo
criterio que `tools/fuelaverages/fuelaverages.py` ya dejó escrito: *refrescar tiene que ser un
comando, no la memoria de alguien.*

| Hueco | Fuente autorizada | Cómo se lee | Caduca |
|---|---|---|---|
| `MIN_BUSQUEDA` · `N_VIAJES` · `HORA_PEOR` · `DIA_PEOR` · `SITIO` | **la pantalla de cifras de la app, en el móvil de producción**, periodo «últimos 7 días» | a ojo sobre la pantalla, y la misma pantalla es la captura de P2/P5 | **semanal** (es una ventana móvil de 7 días) |
| los mismos, para auditar | `users/{uid}/parkingHistory` en Firestore (lleva `searchTimeMs` por sesión) | Consola de Firebase; filtrar `searchTimeMs != null` | — |
| `PRECIO_*` · `HORA_MEDICION` · `CALLE_GASOLINERAS` | **el registro oficial español**, el mismo que consume la app (`MineturStationsSource`) | `…/PreciosCarburantes/EstacionesTerrestres/FiltroProvincia/11` (Cádiz); el campo `Fecha` es la hora de medición | **diaria** |
| `CARBURANTE` | el vehículo del autor | — | cuando cambie de coche |
| `CIUDAD` · `ZONA` · `PERIODO` · handle | decisión, no dato | — | — |

⛔ **La fuente de las cinco primeras es la PANTALLA, no la base de datos, y la diferencia importa.**
Los agregados los calcula el móvil con reglas propias (moda para la hora punta y el día habitual,
aglomerativo a 120 m para el sitio, ventanas medio-abiertas en la zona horaria del dispositivo).
Sumar Firestore a mano da un número que **ningún usuario ve en su pantalla**, y entonces la pieza
estaría citando una cifra que la app no sabe producir. Firestore es la auditoría; la pantalla es la
autoridad.

⛔ **Y el periodo es el de 7 días del plan gratuito, no «todo el histórico».** Una cifra de todo el
histórico sólo la ve quien paga, así que el recién instalado no podría reproducirla: sería prometer
en el anuncio algo que la instalación no entrega — el mismo defecto que la doctrina de la ficha de
Play existe para evitar, una capa más arriba.

⚠️ **Umbrales que deciden si un hueco se puede rellenar.** Si el periodo no los alcanza, la app **no
compone** el dato y el hueco vuelve vacío — eso es la señal de recortar la pieza, no de ensanchar la
ventana: hora punta y día habitual necesitan **≥5** sesiones terminadas; el sitio habitual, un
clúster de **120 m con ≥3** aparcamientos; la duración media, **≥3**; una celda del mapa de calor,
**≥2** muestras.

🔑 **Y una fecha que condiciona el calendario**: la migración a Room v20 (`82e55f5ae`, **22-09-2026**)
**anuló todos los `searchTimeMs` medidos con la fórmula retirada** y empujó la anulación a Firestore.
Dos consecuencias: (a) cualquier valor no nulo de hoy está medido con la fórmula vigente, así que no
hay nada que filtrar; (b) **sólo hay medición desde el 22-09**, de modo que el parte **mensual** no
tiene todavía un mes completo de datos válidos — espera a que octubre cierre o declara la ventana.

### El origen de las capturas, que no es lo que parecía

⛔⛔ **El pipeline de capturas del repo (`docs/release/play-listing/tools/shoot.sh`, presets
`--es shot <preset>`) es del flavor MOCK y sólo de él**: `ShotPresets` vive en `:mockkit`. Es decir:
**no puede producir una pantalla con las cifras reales del autor.** Por eso el §8 del prompt reparte
PROD/DEMO pieza a pieza — una pieza con titular real y pantalla fabricada se contradice a sí misma.

✅ **La mitad geográfica de ese problema se cerró el 14-10**: el mundo de demostración ya es **Madrid**
(Malasaña), con el pin anclado a su propia calle
[MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001]. Lo que sigue en pie es lo otro, y es lo importante:
**las cifras del mock son inventadas**, así que una pieza cuyo mensaje sea un NÚMERO sigue
necesitando PROD. Lo que ya no hace falta es evitar el mock por la calle.
⚠️ Y `-PmockAsProd` **también cambia el applicationId a prod**: el APK se instala como
`com.rndeveloper.paparcar`, no como `…mock`. Nunca sobre el móvil de producción.

Para las capturas **PROD** (P2, P4, P5, P8) hay que hacerlo a mano en el móvil de producción, con
lista de limpieza antes de disparar: **zonas privadas fuera de plano, sin la dirección de casa, sin
matrícula, sin nombres de personas, sin el uid.** Es la misma lista que la escena del mapa del vídeo
del Shipaton esquivó usando mock — ahí se pudo; aquí no, porque aquí el dato *es* el mensaje.

Para las **DEMO** (P3, P6, P7) siguen valiendo las dos trampas ya pagadas y escritas en el pipeline:
`--es shot` **no funciona con R8** (en release los presets se ignoran y la app abre donde estaba), y
el mapa **sólo pinta** si la firma está en la restricción de la clave de Maps — `-PmockAsProd`
resuelve las dos a la vez.

### Lo demás
- **Se puede pegar ya.** El §13 lleva los precios medidos y las decisiones de territorio; lo que
  falta (`ZONA`, handle y las cinco medidas del móvil) está nombrado **como pendiente
  dentro del propio prompt**, así que Claude Web produce con el hueco puesto en vez de «ayudar»
  inventando. Lo único que de verdad bloquea una publicación es rellenar esos huecos antes de subir.
- **El HTML que devuelva Claude Web se renderiza igual que la ficha de Play**: Chrome headless,
  `--force-device-scale-factor=1`, `--window-size=<ancho>,<alto>`, `--screenshot`. Es el mismo
  mecanismo de `tools/render.sh`, así que si una pieza sale mal el fallo se diagnostica igual.
- **Después de la entrega 1, la revisión manda sobre el prompt.** Si P1 sale con hook rate bajo, no
  se pide «otra versión más bonita»: se cambia el gancho, que es la variable del experimento
  (§8.3 de la estrategia).
