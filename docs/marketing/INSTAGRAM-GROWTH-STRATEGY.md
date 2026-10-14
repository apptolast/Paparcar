# Paparcar en Instagram — diagnóstico, estrategia y plan de 4 semanas

> **Doc vivo.** Escrito el **2026-10-13**, **revisado el 2026-10-14** — §7.3 nueva (de dónde sale
> cada cifra, y el reparto PROD/mock de las capturas) · **§4.1 y §4.2 rehechas: el mercado es España
> y la ciudad es el ESTUDIO**, con la aritmética de densidad que lo decide · **§4.2.2-4.2.3 nuevas:
> la segmentación por zona existe pagando, y la zona la decide un campo que `Spot` no tiene** ·
> **§7.1.b nueva: no meter el SDK de Meta** · P8 y P11 corregidas · el eje de Plus al día con
> `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`.
> **Segunda revisión del 14-10, con la decisión del user**: 🎯 el objetivo es **aumentar los usuarios
> activos** (§4.1 rehecha: métrica rectora = activos ganados y su CPA) · la zona queda **DECIDIDA en
> Móstoles** y el SER baja a nota de producto (§4.2.3) · **§9 nueva: el proceso completo de
> publicidad en Meta**, de las cuentas al calendario de 14 días.
> Verificado contra master **`3ad13cc72`** (app **1.4.0**, vc 15, publicada).
> Cabecera con fecha + commit por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001].
>
> **De dónde sale cada afirmación de producto de este doc** — no se inventa ninguna:
> `docs/release/play-listing/LISTING-v1.4.0.md` (lo que la ficha PUBLICADA promete) ·
> `docs/release/play-listing/PLAN-OPTIMIZACION.md` §1 (la auditoría promesa-por-promesa contra el
> código) · `docs/analytics-data-contract.md` (qué significa exactamente cada cifra que la app
> enseña) · `docs/backlog/plus-alerts-and-tiers-001.md` (free vs Plus) · `web/content/{es,en}.json`
> (lo que la web dice) · `docs/branding/README.md` (la marca) · `docs/design/COLOR-SYSTEM.md`.
>
> **Vocabulario obligatorio también aquí** [COPY-SPOT-IS-NOT-A-PARKING-001]: **plaza** es de la
> comunidad, **aparcamiento** es tuyo, **parking** es la instalación de un tercero (en inglés
> *car park*). Un copy de Instagram que diga «marca tu plaza» es el mismo defecto que la app ya
> arregló dentro.
>
> **Pieza hermana:** [`INSTAGRAM-PRODUCTION-PROMPT.md`](INSTAGRAM-PRODUCTION-PROMPT.md) — el prompt
> maestro, autónomo, para producir las 12 piezas en Claude Web. Este doc decide; ése ejecuta.

---

## 0 · Resumen en una página

1. ⛔ **Corrección del 14-10: la cuenta SÍ existe — [`@paparcar_oficial`](https://www.instagram.com/paparcar_oficial)**,
   y la bio ya enlaza `paparcar.com/descargar`. La primera versión de este doc decía «no hay cuenta»
   porque el repo no la menciona en ningún sitio (grepeado) — **lo que demuestra es que el repo no
   sabe de ella, no que no exista**. Consecuencia: esto **no** es un cero a uno en identidad; sí lo
   es en **medición** (§7.1) y en **sistema creativo** (§5). ⚠️ No he podido auditar su contenido:
   Instagram sirve muro de inicio de sesión a cualquier lectura sin cuenta. Lo que hace falta para
   cerrar el diagnóstico está en §6.0-2.
2. **El activo de marketing real que existe no es creativo, es una FÁBRICA**: el pipeline de
   capturas (`docs/release/play-listing/tools/`) arranca la app de verdad en un estado concreto,
   mide la UI y compone PNG de 1080×1920 con marco de dispositivo, en 9 idiomas. Eso vale más que
   cualquier maqueta, y hoy sólo se usa para la ficha de Play. **Instagram debe comer de ahí — pero
   sólo hasta donde llega**: el pipeline es del flavor mock (mundo en Dublín), así que sirve para
   enseñar un gesto y **no** para enseñar un dato. Reparto PROD/mock pieza a pieza en §7.3.
3. **La oportunidad de posicionamiento no es «encuentra plaza»** (indeliverable: no hay comunidad) ni
   «recuerda dónde aparcaste» (verdad, pero la gente cree que ya lo tiene en Google Maps). Es
   **«Paparcar es lo único que MIDE lo que te cuesta aparcar»** — minutos dando vueltas, litros,
   euros, y la hora exacta en que tu calle es imposible. Única, cierta hoy, con un conductor solo, y
   sin depender de masa crítica.
4. **El mercado es España; la ciudad es el ESTUDIO** (§4.2). Se graba donde vive el autor porque es
   el único sitio donde puede grabar y medir, pero se escribe para cualquier conductor urbano
   español: **el titular habla del fenómeno, el lugar es la evidencia.** La densidad comunitaria
   sigue siendo el objetivo de fondo, pero la aritmética dice que es cosa de 6-12 meses, no de
   cuatro semanas — así que **no es el KPI del mes 1**.
5. ✅ **La atribución, que era el bloqueo de todo, está HECHA Y DESPLEGADA** (`ddf1309f4`, 14-10):
   `/descargar` y cinco rutas más mandan ya su `referrer` a Play, verificadas en la URL viva, y un
   guard nuevo impide escribirlo mal. ⏳ Queda verlo **una vez** en un móvil (§7.1).
6. **Cadencia honesta: 3 piezas/semana, 12 en el mes**, de las cuales 4 Reels. Con 12 piezas se
   detecta una dirección, **no un veredicto** — está dicho en §8.4 y no se va a disimular después.
7. 🎯 **El objetivo es AUMENTAR LOS USUARIOS ACTIVOS** (§4.1, decidido por el user el 14-10). Y
   «activo» aquí tiene una definición dura: **un usuario al que la app le mide un aparcamiento**
   (evento `parking_session_started`). Métrica rectora: **usuarios activos ganados** y, en el carril
   pagado, su **CPA = gasto ÷ activos**. Ni alcance, ni seguidores, ni instalaciones a secas.
8. **La zona está decidida: Móstoles** (§4.2.3), y después Leganés, Alcorcón, Fuenlabrada. No por
   dolor máximo sino por lo que de verdad produce activos: **gente que aparca en la calle, en un
   sitio distinto cada noche**, 210.000 habitantes en un núcleo compacto y CPM barato. ⛔ Un barrio
   céntrico de Madrid es lo contrario: más plaza privada, menos coche en movimiento, CPM caro.
9. **Segmentar por zona existe, pero sólo pagando** (§4.2.2): el orgánico no geolocaliza; Meta Ads
   sí, con pin y radio. **El proceso completo —cuentas, estructura, cada campo del conjunto, las
   métricas, las reglas de corte y el calendario de 14 días— está en §9.** Arranque: tráfico a
   `/descargar/z-mostoles`, 7 €/día × 7 días, Instagram solo.
10. **Y hay un coste de marca que recomiendo NO pagar** (§7.1.b): optimizar por instalación exigiría
    meter el SDK de Meta en una app que promete «sin anuncios, tu ubicación no se vende». Se corre
    campaña de **tráfico** con la atribución del install referrer —que ya funciona— y se acepta un
    CPC peor.

---

## 1 · Diagnóstico honesto de lo que hay

### 1.1 El inventario real

| Activo | Estado | Juicio para Instagram |
|---|---|---|
| **Ficha de Play**, 9 idiomas, auditada promesa a promesa | ✅ Excelente | Es **captura de demanda**: sirve a quien ya teclea «dónde aparqué». No crea demanda. No es contenido. |
| **paparcar.com** (ES/EN, generada, con guard de paridad) | ✅ Sólida | Buen destino intermedio, **mal destino de un Reel**: añade un clic entre la atención y la tienda. |
| **Marca «Compás B+»**, maestro paramétrico, 11 superficies | ✅ Fuerte y única | Ver §5.1: **no sirve como firma visual del feed** y hay que decirlo. |
| **72 capturas enmarcadas** (8 × 9 idiomas, mundo en Dublín) | ✅ Hechas | Material de tienda, no de feed: llevan titular en 9 idiomas y marco de ficha. **El pipeline sí sirve; los PNG, no.** |
| **Vídeo del Shipaton** 2:12, 31 escenas, 1920×1080 | 🟡 A medias | En Instagram es un activo **muerto tal cual** (§6.1). Sus escenas, en cambio, son oro. |
| **Metraje de campo**: GoPro, móvil, 2 clips de conducción, pantallas en CFR | ✅ Existe | El más subestimado del inventario. Es exactamente lo que falta en el feed de cualquier app de aparcamiento. |
| **Instagram** `@paparcar_oficial` | ✅ **Existe**, con `/descargar` en la bio | 🟡 Sin auditar (muro de login). Lo que publique hoy decide si el sistema de §5 **sustituye** o **continúa** |
| **Atribución de instalaciones** | ❌ No existe | §7.1 |

### 1.2 Las tres debilidades que importan

**D1 · El producto estrella no se puede vender todavía, y eso ya está decidido.**
La ficha lo dice con todas sus letras: *«La ficha promete una sola cosa: que la app sabe dónde
aparcaste… Encontrar plazas se DESCRIBE, pero NO se promete»*, porque prometerlo con el mapa vacío
compra reseñas de una estrella. **Esa doctrina es correcta y hay que respetarla en Instagram con
más disciplina todavía**, porque un Reel llega a gente que no ha leído nada y que juzga la app en
los 40 segundos siguientes a instalarla. Un Reel que enseñe un mapa lleno de plazas verdes en una
ciudad sin usuarios es publicidad engañosa con mejor producción.

**D2 · El mensaje vigente es verdadero y poco contagioso.**
«¿Dónde aparcaste? Paparcar lo recuerda» convierte bien **a quien ya busca** la solución, y es la
frase correcta en la tienda. Pero como gancho social es débil por una razón concreta: nadie reenvía
a un amigo una función que cree que ya tiene (guardar la ubicación del coche es un gesto manual que
Google Maps ya hace, y la diferencia —que Paparcar lo hace **sin que tú hagas nada**— es un matiz
que hay que demostrar, no afirmar). Una promesa que hay que demostrar no puede ser el gancho: tiene
que ser lo que el gancho te lleva a creer.

**D3 · Todo el aparato está construido para nueve idiomas, y una cuenta de Instagram es monolingüe.**
La máquina de i18n es una virtud del producto y una **trampa de marketing**: invita a hacer
contenido «para todos» que no es de nadie. La salida no es encerrarse en un pueblo —eso es la otra
trampa, y caí en ella en la primera versión de este doc— sino **un idioma y un mercado** (castellano,
España) con piezas **concretas**: una calle, una hora, un precio. Decisiones en §4.2 y §4.4.

### 1.3 Las tres oportunidades que nadie está usando

**O1 · Paparcar mide algo que ningún conductor ha visto nunca medido: su propio tiempo dando vueltas.**
Y no lo estima: `docs/analytics-data-contract.md` define *buscando plaza* como tiempo de **conducción**
dentro de 300 m del pin final, y **sólo cuando la geometría demuestra vueltas** (trazado > línea
recta × 1.8, exceso > 250 m, ≥ 90 s). Tres valores: `null` = no medible, `0` = llegada directa
medida, `>0` = búsqueda medida. Es el dato más vendible del producto y **no aparece en el título ni
en la descripción breve de la ficha**. Es la oportunidad central de este plan.

**O2 · Paparcar es honesta en un sector que no lo es, y eso es contenido.**
La doctrina *«mejor falso negativo que falso positivo: ante la duda se PREGUNTA»*, el «aprox.» de
cada estimación, el «N de M viajes» de cobertura, el «un gálibo desconocido nunca es un sí». En un
feed lleno de apps que prometen plaza garantizada, **enseñar lo que tu app se niega a afirmar** es
un gancho de confianza que los competidores no pueden copiar sin reescribir su producto.

**O3 · El gálibo y el coche compartido son nichos con dolor intenso y cero competencia de contenido.**
«1,90 m. Tu furgoneta mide 2,05» es un problema que sale caro y que tiene comunidades enteras
(furgos, campers, oficios) que reenvían cosas por WhatsApp todo el día. Y el coche compartido es la
**única función del producto con referral nativo**: no se puede usar solo.

### 1.4 Lo que el posicionamiento actual hace bien y no hay que tocar

- La **jerarquía promesa → comunidad** (primero lo que funciona con un usuario, después lo que
  mejora con vecinos). Es correcta y este plan la hereda.
- El **vocabulario** (plaza / aparcamiento / car park). Es un activo de claridad.
- La **privacidad como hecho concreto** («tu matrícula nunca sale del móvil»), no como declaración.

---

## 2 · La pregunta que decide todo: ¿qué vendemos en un feed?

Tres posicionamientos posibles. Los pongo uno contra otro porque la decisión es la mitad del trabajo.

| | **A · «Recuerda dónde aparcaste»** | **B · «El Waze de las plazas»** | **C · «Lo que te cuesta aparcar, medido»** |
|---|---|---|---|
| ¿Es cierto hoy? | ✅ con un usuario solo, desde la 1ª instalación | ❌ necesita masa crítica | ✅ con un usuario solo |
| ¿Es único? | 🟠 se confunde con guardar un pin en Maps | 🟠 lo ha intentado medio sector | ✅ no he encontrado competidor que lo publique *(🔵 verificación pendiente, §3.4)* |
| Carga emocional | baja | alta | **alta y propia** (es un número sobre TU vida) |
| ¿Se reenvía? | poco | mucho | **mucho, y a una persona concreta** («mira lo que cuesta nuestra calle») |
| ¿Produce contenido infinito? | no (es una función) | sí, pero falso | **sí**: cada calle, hora, barrio y carrocería es una pieza |
| Riesgo | ninguno | 🔴 reseñas de 1 ⭐ y la doctrina de la ficha en la basura | 🟠 exige datos reales y declarar la muestra |
| ¿Vende Plus? | no | no | **sí**: Plus = vigilancia + memoria, y C crea apetito de memoria |

**Decisión: C es el GANCHO, A es la PROMESA, B es el HORIZONTE.**

- **C (gancho)** es lo que para el scroll y lo que se comparte. No es una función: es una queja con
  un número encima. Gana a A porque A es un *feature* y C es un *reconocimiento*; gana a B porque
  B es mentira hoy.
- **A (promesa)** es lo que dice la bio, lo que dice la tienda y lo que el usuario recibe el día 1.
  **No se cambia**: cambiarla rompería la coincidencia de mensaje con la ficha, que es por donde se
  pierde la conversión.
- **B (horizonte)** sólo aparece como consecuencia, nunca como oferta: *«y cuando te vas, la plaza
  que dejas queda para el de detrás»*. Descriptivo, en presente, sin prometer que haya plazas.

**La línea puente** —la frase que sostiene C y A a la vez, y que debe aparecer en la bio y en el
cierre de cada pieza larga:

> **Paparcar recuerda dónde aparcaste. Y de paso, te dice lo que te cuesta encontrar sitio.**

### 2.1 Cómo se comprueba que C era la elección correcta

No por likes. Por tres cosas, en este orden (§8):
1. **envíos/alcance** de las piezas de territorio C vs. las de territorio A → si C no se reenvía más,
   la hipótesis central cae y se revisa en la semana 3, no en el mes 3.
2. **toques en el enlace / alcance** → si C genera atención pero no curiosidad por instalar, el
   problema es el CTA, no el posicionamiento.
3. **D1 «primer aparcamiento medido»** entre los instalados vía Instagram → si llegan y no llegan a
   tener su número, el gancho promete algo que el onboarding no entrega. La app ya mide esto
   (`stats_tile_auto_detected`, el % de acierto de detección), así que es comprobable.

---

## 3 · Referencias externas: qué está comprobado y qué es mi hipótesis

### 3.1 Comprobado — distribución en Instagram 2026

- Las tres señales que Meta ha confirmado que más pesan son **tiempo de visualización, envíos por
  alcance (sends/reach) y likes por alcance**; los **envíos son la señal más potente para llegar a
  quien no te sigue**, con un peso ~3-5× el de un like.
  → Consecuencia directa: **el objetivo creativo de cada pieza no es gustar, es ser reenviada a UNA
  persona concreta.** Eso se diseña: hace falta un sujeto identificable (una calle, una hora, un
  tipo de coche) para que exista alguien a quien mandárselo.
- **Las cuentas de menos de 10.000 seguidores tienen un techo de alcance ligeramente mayor** para un
  Reel que rompe, porque el algoritmo compensa la falta de datos probando más su contenido.
  → Consecuencia: **menos piezas y mejores** gana a más piezas y peores. Sostiene la cadencia de 3/semana.
- **~55 % de las vistas de Reels vienen de no seguidores**; el **hook rate** (impresiones que pasan
  de 3 s) es la métrica dominante, con **≥50 %** como referencia de un Reel orgánico sano.
  → Consecuencia: el primer segundo es el 80 % del trabajo. Por eso cada pieza de §6 tiene su gancho
  escrito aparte del cuerpo, y por eso el experimento de la semana 4 es **re-cortar el mismo cuerpo
  con otro gancho**.
- Lo hiperlocal funciona: lo que supera al marketing genérico es el contenido que enseña **el barrio,
  la calle y los sitios que el espectador ve todos los días**; entre 8 y 12 hashtags, de los cuales
  **3-5 fijos**.
  → ⚠️ **Y aquí hay que leer el hallazgo con cuidado, porque es de otro negocio.** Esa literatura
  habla de un comercio local: una panadería **necesita** que el espectador esté a diez minutos
  andando. El cliente de Paparcar puede estar en cualquier sitio; lo que tiene que ser local es la
  **prueba**, no el cliente. Así que lo que nos llevamos no es *«geografía gana a nacional»* sino
  **«lo concreto gana a lo genérico»**: una calle con nombre, una hora exacta, un precio con su
  minuto. Eso es lo que hace que parezca real — y eso **viaja**. Por eso los hashtags fijos son
  nacionales y la calle vive en la línea de evidencia (§4.2).
- ⚠️ **Y el aviso honesto**: la literatura de crecimiento coincide en que el vídeo corto se comporta
  como «billetes de lotería baratos» — la mayoría de clips no hacen nada. **No es un canal fiable,
  es un canal de alta varianza.** Un plan que prometa lo contrario está mintiendo.

### 3.2 Comprobado — y por qué NO lo vamos a usar en una pieza

Hay datos públicos sobre el coste de aparcar en España, y los dos que más circulan están **viejos**:

| Dato | Fuente | Antigüedad | Veredicto |
|---|---|---|---|
| ~16 min/día aparcando; 25,5 €/mes en parkings y zona azul; Zaragoza 18,6 min, Bilbao 12,9 | prensa citando encuesta | **~2006** | ⚠️ no usable como titular |
| 9 % de conductores pierden ~96 h/año (>15 min/día), 19 ciudades europeas | estudio Xerox vía prensa | **2016** | ⚠️ no usable como titular |
| 57 % tiene problemas para estacionar 2+ veces al día | misma encuesta antigua | ~2006 | ⚠️ contexto, no creativo |

**Decisión: ninguna pieza lleva una cifra prestada como titular.** Dos razones, y la segunda es la
importante: (1) son viejas y se pueden desmontar en comentarios; (2) **una cifra ajena no diferencia
— la nuestra sí.** El activo de Paparcar es que el número es *tuyo y medido*, no que exista un
estudio. Poner un titular de Xerox es regalar el único terreno donde no tenemos competencia.
Las cifras externas sólo valen como **segunda línea de contexto** en una pieza, y citadas con su año.

### 3.3 Comprobado — competencia y hueco

- **Parclick** (250+ ciudades, 1.400+ parkings) y la familia **Telpark / ElParking / Parkimeter**
  venden **inventario y pago**: reservas de parking y zona azul. No miden tu tiempo.
- **Parking Madrid** (Ayuntamiento) y equivalentes municipales venden **ocupación de parkings
  públicos en tiempo real**. Dato de infraestructura, no personal.
- **AparcaMadrid** estima probabilidad de encontrar sitio en una calle y muestra plazas reportadas
  por otros conductores → es el competidor conceptualmente más cercano **en el eje B**, y está
  encerrado en Madrid.
- **SpotAngels** («el Waze del parking», EE. UU.) sigue vivo y es gratis, con reseñas recientes
  quejándose de **información desactualizada**. Es la prueba de campo de D1: una app comunitaria
  cuyo dato envejece pierde al usuario. *(Paparcar ya tiene la respuesta a eso dentro —TTL, frescura
  por edad, votos de retirada— y eso **es** una pieza de contenido: «por qué una plaza caduca».)*
- **Hueco verificado**: ninguno de ellos comunica *«cuánto tiempo pierdes TÚ buscando sitio»*.
  Compiten por la plaza; nadie compite por el **espejo**.

### 3.4 Mis hipótesis — marcadas como tales

- 🔵 **H1** · El gancho «esto es lo que te cuesta tu calle» supera en envíos/alcance al gancho
  «recuerda dónde aparcaste» por un factor ≥ 2. *Se prueba en semanas 1-3 (§8.3).*
- 🔵 **H2** · El contenido hiperlocal con nombre de calle real convierte mejor (toques en enlace /
  alcance) que el mismo contenido sin lugar. *Se prueba con la pareja P2/P5.*
- 🔵 **H3** · El nicho de gálibo (furgoneta/camper/SUV alto) tiene la mayor tasa de envíos de todo
  el plan, aunque menos alcance. *Se prueba con P4.*
- 🔵 **H4** · La honestidad explícita («lo que Paparcar NO hace») genera más comentarios por alcance
  que cualquier demostración de función. *Se prueba con P12.*
- 🔵 **H5** · Ningún competidor publica la medición del tiempo personal de búsqueda. *Verificación
  pendiente: revisar fichas de Play/App Store de Parclick, Telpark, ElParking, AparcaMadrid,
  SpotAngels y Waze buscando la palabra «tiempo».* Si H5 cae, el gancho C sigue siendo válido pero
  deja de ser único y hay que reforzarlo con el eje de honestidad (O2).

---

## 4 · Estrategia

### 4.1 El objetivo real, escrito como se mide

> ⛔ **Corrección del 14-10, y es la más importante de este doc.** La primera versión decía: *«el
> objetivo no son instalaciones: es DENSIDAD en una zona»*, con las plazas publicadas por semana en
> un polígono como métrica rectora. **Está mal para el mes 1**, y lo demuestra una aritmética que no
> hice. Lo que sigue la sustituye.

**La aritmética de la densidad.** Una plaza caduca a las **2 h**. Un conductor activo publica ~1
plaza al día (su salida). Para que un barrio se sienta vivo —digamos **5 plazas frescas** en
cualquier momento— hacen falta ~**60 publicaciones al día en ese barrio**, o sea ~60 conductores que
aparquen ahí **cada día**. Con ratios normales de activos sobre instalados y la dispersión
geográfica, eso son del orden de **200-400 instalaciones concentradas en UN barrio** 🔵.

En una ciudad de 90.000 habitantes, 300 instalaciones son ~0,3 % de la población **y tendrían que
caer todas en el mismo barrio**, no repartidas por el pueblo. Conclusión: **la ciudad pequeña no hace
la densidad barata, sólo la hace menos imposible.** Es un objetivo de 6-12 meses.

**Y el producto no la necesita para entregar valor hoy.** Mirando qué funciona sin comunidad:

| Función | ¿Necesita densidad? |
|---|---|
| Recuerda dónde aparcaste · widget · tus cifras | **no** |
| Gasolineras (ES, FR, DE, IT) · parkings públicos · gálibo | **no** |
| Coche compartido | no: necesita **dos personas**, no una ciudad |
| Plazas de la comunidad | **sí** |

Y lo vendible —Plus— es **todo de un jugador**. Así que el producto monetizable es **nacional**, y lo
único que pide densidad es justo la pieza que ya habíamos decidido no prometer (§2). Cuarto
argumento, y sale del propio repo: **1.000 instalaciones repartidas por España son un corpus de
detección muy superior** a 300 en un pueblo — más OEMs, más coches, más calles.

**El objetivo, dicho por el user el 14-10 y es el que manda:**

> ### 🎯 **Aumentar los USUARIOS ACTIVOS.**

Y «activo» no es una opinión: en esta app un usuario activo es **uno al que la app le mide un
aparcamiento**. Si no mide, no hay plaza, no hay cifras, no hay nada que recordar — y la instalación
no vale.

**Métrica rectora: coste y número de USUARIOS ACTIVOS GANADOS.**

```
usuario activo ganado = instalación atribuida que produce ≥1 aparcamiento medido en sus primeros 7 días
coste por usuario activo (CPA) = gasto ÷ usuarios activos ganados
```

Se parte en dos carriles que **no se mezclan nunca** (§7.1: `utm_medium`):
- **orgánico** (`social`): gasto 0 → se juzga por el número, no por el coste;
- **pagado** (`paid`): se juzga por el **CPA**, que es lo único que dice si se puede escalar.

⛔ **Lo que ya NO es métrica rectora**, y queda escrito para no volver a discutirlo: ni «plazas
publicadas en un polígono» (la aritmética de arriba la pone a 6-12 meses), ni instalaciones a secas
(una instalación que no mide un aparcamiento es un número de vanidad), ni alcance, ni seguidores.
*(Nota: subir el % de activos entre los usuarios que YA están instalados es otra palanca — es
producto, no publicidad — y la vigila la regla de §8.3 que manda parar y mirar el onboarding.)*

### 4.2 El ESTUDIO no es el MERCADO

**Lo que me equivoqué fue meter dos cosas en la misma decisión: dónde se graba y a quién se le
habla.** No tienen por qué ser el mismo sitio.

- **MERCADO = España.** Cualquier conductor urbano. La promesa funciona para uno solo, en cualquier
  ciudad.
- **ESTUDIO = la ciudad donde conduces tú** — por los datos del repo, **El Puerto de Santa María**,
  con Cádiz y Jerez como anillo de rodaje. Es el único sitio donde puedes grabar una calle esta
  tarde y donde tienes cifras medidas de verdad. *(🔵 Si vives en otra parte, se sustituye el nombre
  y no cambia nada más.)*

**La regla que sale de ahí, y gobierna todo el copy de lugar:**

> **El titular habla del FENÓMENO. El lugar es la EVIDENCIA, nunca el sujeto.**

- ✅ *«Cuatro vueltas a la misma manzana»* + línea de evidencia *«medido en 14 viajes · El Puerto,
  centro · oct 2026»*. Un vecino de Málaga se reconoce; que el cronómetro se grabara en Cádiz es la
  prueba de que el número es real.
- ❌ *«Lo que cuesta aparcar en El Puerto»* como titular: deja fuera al 99 % de quien lo ve.

Consecuencias concretas, todas pequeñas: **los 5 hashtags fijos son nacionales** y ninguno es de
localidad (§7 del prompt) · el PARTE titula la cifra y pone la zona en la evidencia · nada de
geotags de pueblo por defecto.

### 4.2.1 Si luego hacemos la apuesta de densidad: primero mirar, luego elegir

⛔ **«Aquí a lo mejor hay más usuarios» es un dato, y lo di por hecho.** Hay dos sitios donde está
la respuesta, los dos gratis y con meses acumulados:

- **Play Console → adquisición por región**: dónde se instaló de verdad desde el 05-09.
- **Firestore**: `users/{uid}/parkingHistory` lleva `address.city` en cada sesión y `spots` lleva
  coordenadas. Contar por ciudad.

**Si ya existe un grupo de usuarios en algún sitio, ése es el candidato — no tu pueblo por defecto.**
Un rescoldo encendido vale más que un sitio donde es cómodo grabar.

Y el criterio **no es «ciudad grande»**: es **centro compacto** (para que 300 personas se noten) +
aparcamiento brutal + cultura local fuerte en Instagram + que puedas pisarla. Con eso, **Madrid y
Barcelona son las peores candidatas**: dolor máximo y área tan grande que la densidad es
inalcanzable. Candidata propia, por geografía más que por gusto: **Cádiz capital** 🔵 — casco
encajonado por el mar, sin sitio donde crecer, y a media hora del estudio. Después, **por barrio y no
por ciudad**: Sevilla (Triana, centro), Málaga, Granada. Hipótesis, no veredictos.

**Regla de apertura**: se abre una ciudad como objetivo cuando publique plazas que no sean tuyas,
dos semanas seguidas.

### 4.2.2 · Sí, se puede segmentar por zona — pero sólo pagando

**La pregunta, contestada**: en Instagram **el alcance orgánico no se puede geolocalizar**. Lo único
que tiene el feed son hashtags, la etiqueta de ubicación del post y colaboraciones con cuentas
locales — ninguna de las tres es segmentación: son pistas. **La segmentación por zona existe y vive
en Meta Ads Manager**, que sirve los anuncios a Instagram.

| Qué permite | Detalle |
|---|---|
| **Pin + radio** | se suelta un pin en el mapa y se mueve el radio. Mínimo documentado **1 milla ≈ 1,6 km**; máximo 80 km |
| Código postal · ciudad · región | también sirven, y para un barrio suelen ser más precisos que un radio |
| **«Personas que viven o han estado hace poco aquí»** | es la opción que hay que elegir: la alternativa incluye a quien está de paso, y un turista no aparca todos los días |

⛔⛔ **Y una trampa de 2026 que, sin tocarla, se come justo lo que estás comprando**: cuando Meta no
puede llenar las impresiones dentro de tu límite, **expande la entrega fuera de él en silencio** — a
menos que marques **«no expandir la audiencia»** en el módulo de ubicación. Para una apuesta de
densidad eso es fatal: pagarías por impresiones repartidas, que es exactamente lo contrario del
objetivo. ⚠️ Se dice también que la entrega **penaliza radios por debajo de ~8 km en zonas de baja
densidad**; un barrio denso de Madrid o de la corona no es baja densidad, así que 1,6-3 km debería
llenar — pero es lo primero que hay que comprobar en la primera campaña.

**Reparto de trabajo que sale de esto, y resuelve la aparente contradicción con §4.2:**

> **Lo orgánico es NACIONAL** (el contenido viaja y es gratis) **· lo PAGADO es por zona** (es la
> única herramienta que puede comprar densidad en un polígono).

**Y lo que cambia el cálculo de §4.1**: la densidad que era inalcanzable orgánicamente **es barata
pagando**. 🔵 Con un CPI de instalación de entre 0,50 € y 2 € para una app de utilidad gratis en
España, las **200-400 instalaciones concentradas** que pide un barrio salen por **~150-600 €**. Eso
convierte «6-12 meses» en «un mes y un presupuesto pequeño» — *si* el CPI real cae en esa banda, que
es lo primero que mide la campaña. ⛔ **Pero la disciplina de §8.3 no se toca**: no se paga por
distribuir un gancho que no ha ganado ≥50 % de hook rate orgánico. Pagar por un gancho flojo es
comprar un gancho flojo.

### 4.2.3 · La zona elegida: **Móstoles**

**Decidido, no propuesto.** Primera zona de compra: **Móstoles** (pin en el centro, radio 3 km).
Después, en este orden y con la misma plantilla de campaña: **Leganés** (Zarzaquemada / El Carrascal),
**Alcorcón**, **Fuenlabrada**.

Por qué Móstoles y no un barrio de Madrid capital:

| Criterio (el objetivo es **usuarios activos**, §4.1) | Móstoles |
|---|---|
| ¿Se aparca en la calle a diario? | **sí** — bloques de los 70-80, sin garaje por vivienda |
| ¿Se aparca en un sitio **distinto cada noche**? | **sí**, y eso es literalmente las dos mitades del producto: *¿dónde aparqué?* y *¿cuánto tardé?* |
| ¿Cuánta gente cabe en un radio de 3 km? | **mucha**: ~210.000 habitantes con núcleo compacto — el mejor ratio personas/euro de la corona |
| Parque de coches | alto, y Android pesa más que en el centro de Madrid |
| Competencia publicitaria | baja → CPM barato |
| ¿Depende de la ordenanza de Madrid? | **no** |

⛔ **Y por eso NO un barrio céntrico de Madrid**: ahí mucha gente tiene plaza o paga parking, el
coche se mueve menos, y el CPM es el más caro de España. Lo que buscamos no es dolor máximo: es
**gente que aparca en la calle todos los días**.

⚠️ **Una nota de producto que se queda, aunque el SER quede fuera de la decisión** (aparcado el
14-10, y con razón: se estaba decidiendo la geografía por un dato que cambia cada trimestre):
**`Spot` no tiene campo para saber si una plaza es de pago, azul, verde o de residentes**, y no hay
modelo de ZBE (`energyLabel` se guarda y se muestra, no decide acceso). Verificado en el modelo.
Consecuencia única y permanente: ⛔ **ninguna pieza dice «aparca gratis» ni insinúa que la app te
diga dónde puedes aparcar legalmente** (ya está en las prohibiciones del prompt). No condiciona nada
más.

### 4.3 Públicos, por prioridad, con su mensaje

| # | Público | Quién es | Mensaje | Por qué está aquí |
|---|---|---|---|---|
| **1** | **El condenado a su calle** | 28-50, coche propio, vive o trabaja en una zona densa o regulada de **cualquier** ciudad española | *«Tu calle tiene un precio en minutos. Nadie te lo había dicho.»* | Es el público mayoritario del problema, y «tu calle» es la **suya**, no la del autor. Todo el territorio EL PARTE es para él. |
| **2** | **El que no se acuerda** | cualquiera; dolor episódico pero agudo (centro comercial, aeropuerto, ciudad ajena, noche) | *«No vuelvas a buscar tu coche.»* | **Mayor conversión, menor contagio.** Vive en Stories, en respuestas y en el perfil — no en el motor del feed. |
| **3** | **El alto**: furgoneta, camper, SUV | oficios, campers, familias con monovolumen | *«Ese parking tiene 1,90. Tu coche mide 2,05.»* | Dolor caro, grupos de WhatsApp activos, **cero competencia de contenido**. Nicho, pero el de mayor envío esperado (H3). |
| **4** | **El coche de dos** | parejas, familias, pisos compartidos | *«Lo deja quien lo conduce. Lo ve todo el grupo.»* | **La única función con referral nativo**: no se puede usar en solitario. Una instalación trae otra. |
| **5** | **El que paga la gasolina** | cualquiera con coche de combustión en ES/FR/DE/IT | *«El precio de tu carburante, no una media.»* | Dinero real y recurrente. Free y completo, así que es el ángulo más fácil de demostrar — y desde el 14-10 el free **además recibe aviso** cuando una gasolinera cerca de él se mueve de precio, lo que lo convierte en el único público al que la app ya trabaja gratis. |

⛔ **Público que DESCARTO a propósito: la comunidad de desarrolladores / build-in-public.** Es el más
fácil de conseguir desde esta silla y el que menos sirve: produce seguidores que aplauden el código y
nunca van a instalar la app, infla las métricas de vanidad y contamina la señal del
experimento. El relato del creador entra, pero **como territorio de confianza (≈1 de cada 8 piezas)
y en castellano para conductores**, no como contenido para programadores.

### 4.4 Un idioma, y luego se verá

**Instagram en castellano de España, una sola cuenta.** No 9. No ES+EN.
Razones: la máquina de i18n sirve a la tienda, no a un feed; una cuenta bilingüe divide el alcance de
cada pieza y confunde la señal del experimento; y el metraje está grabado aquí, con coches, calles y
señalización de aquí.
**Regla de apertura**: el segundo idioma se abre cuando el primero tenga su métrica rectora viva, y
será **el del siguiente mercado**, no «inglés por si acaso».

### 4.5 Cómo se demuestra el valor sin convertir el feed en un catálogo

Tres reglas duras:

1. **Una pieza = un trabajo.** Cada pieza declara **uno** de estos cinco antes de producirse:
   `ATENCIÓN` · `IDENTIFICACIÓN` · `DEMOSTRACIÓN` · `CONVERSACIÓN` · `CONVERSIÓN`. Si no se puede
   declarar, la pieza no se produce. (Es la misma disciplina que
   [DET-VERDICT-NOT-PREDICATE-001] aplica al código: *si su resultado no se puede citar, no existe*.)
2. **El catálogo de funciones vive en UN sitio y no es el feed**: los **destacados de Stories**. Ahí
   sí: una función por destacado, con su captura real. El feed nunca enumera.
3. **La demostración siempre es metraje real.** Ni una UI dibujada, ni un mapa inventado, ni una
   cifra de ejemplo. Lo dice la doctrina del repo para la ficha —*«un callout dibujado a mano es una
   promesa que el binario no firma»*— y vale igual aquí. Cuando una pieza necesite una pantalla,
   §6 nombra **qué preset y qué estado** hay que grabar.

### 4.6 De la atención a la instalación — la cadena entera

```
Reel (gancho C: el fenómeno, con su evidencia local)
  → perfil (bio con promesa A + línea puente)          ← aquí se pierde la gente: la bio tiene que
  → paparcar.com/descargar/<territorio>                   confirmar lo que el Reel insinuó
  → Play (ficha que promete A: coincide)
  → instalación CON referrer=instagram                 ← §7.1, sin esto no hay plan
  → primer aparcamiento medido (D1)                    ← LA MÉTRICA RECTORA: la única que prueba a la
                                                          vez que el gancho atrajo y que la app entregó
  → (a 6-12 meses) plaza publicada en un barrio denso  ← hipótesis en observación, no KPI del mes 1
```

Cuatro mecanismos concretos, no consejos:

- **M1 · El CTA nunca es «descarga la app».** Es **«mide el tuyo»** / **«mira lo que cuesta tu
  calle»**. La instalación es el medio para un número propio, no un favor al desarrollador.
- **M2 · Bucle comentario → DM.** Las piezas de EL PARTE piden **una calle o un barrio** en
  comentarios. Se responde por DM con el dato de esa zona (o con «de esa calle todavía no tengo
  medidas, serás tú el primero») y el enlace. El DM es la superficie de mayor intención **y** alimenta
  la señal de envíos.
- **M3 · Un enlace por territorio**, para que el informe de adquisición diga qué idea trajo gente y
  no sólo «Instagram» (§7.1).
- **M4 · Coincidencia de mensaje en la primera pantalla de la tienda.** La ficha abre con «¿Dónde
  aparcaste? Paparcar lo recuerda» y la captura 1 dice lo mismo. Si un Reel engancha con «lo que te
  cuesta tu calle» y la tienda no lo menciona, hay fuga. **Arreglo de coste cero**: la captura **6**
  del set ya dice *«Tu conducción, en cifras — gasto, combustible, CO₂ y cuánto tardas en aparcar»*.
  🔵 **Propuesta a validar con un experimento de ficha, no por gusto**: subir esa captura al puesto
  2-3 mientras la campaña esté viva. No toca copy ni traducciones: sólo el orden.

### 4.7 Lo que descarto aunque quede bonito

| Idea descartada | Por qué |
|---|---|
| **Montar el vídeo del Shipaton en Reels** | Inglés, 2:12, narrado por el creador, pensado para un jurado. En un feed de conductores españoles no supera los 3 s. Sus **escenas** se reutilizan (§6.1); el **montaje**, no. |
| **Prometer o insinuar plazas libres disponibles** | Rompe la doctrina de la ficha y compra reseñas de 1 ⭐. Innegociable. |
| **Renders preciosos del mapa, sin número y sin persona** | Es lo primero que produce cualquiera y lo que menos se reenvía: no hay sujeto, así que no hay nadie a quien mandárselo. |
| **Carrusel «6 cosas que hace Paparcar»** | Catálogo. Va a destacados. |
| **Cifra prestada como titular** (los 16 min/día) | §3.2: vieja y, peor, ajena. |
| **Cuenta bilingüe o en 9 idiomas** | §4.4. |
| **Audios de tendencia sin relación con el producto** | Alcance prestado, cero recuerdo de marca, y rompe la firma sonora. |
| **Influencers, sorteos, intercambio de seguidores** | Sin presupuesto; y el seguidor de un sorteo no aparca en tu barrio. |
| **Pagar por promocionar antes de la semana 4** | Promocionar una pieza con hook rate bajo es comprar alcance para un gancho que no funciona. Primero se gana orgánicamente ≥50 %. |
| **Enseñar el Dev Catalog, el código o los tests** | Público descartado (§4.3). |
| **Capturas del mundo de Dublín** | La app se usa aquí. Un rótulo en inglés en una calle irlandesa rompe lo concreto, que es lo que hace creíble la cifra. Reparto PROD/mock en §7.3. |
| **Encerrar la cuenta en una ciudad** (pin de localidad en la bio, hashtags de pueblo, titulares con el nombre del sitio) | §4.1-4.2: la densidad está a 6-12 meses, el producto entrega valor a un usuario solo en cualquier parte, y un titular con el nombre del pueblo deja fuera al 99 % de quien lo ve. Lo local se **gana con datos** (§8.3), no se asume. |

---

## 5 · El sistema creativo

### 5.1 Crítica a la identidad actual como identidad de contenido

La marca es buena y está bien construida. Como **firma de un feed** tiene dos problemas medidos en
el propio repo, y hay que decirlos:

1. ⛔ **El Compás B+ se desmiga por debajo de 32 px.** Está escrito y medido en
   `docs/branding/README.md`: *«a 24 px el dibujo se desmiga en tres trozos… y a 20 px ya ha perdido
   una rueda»*. La miniatura del perfil y la del grid están justo ahí. **Conclusión: la marca es
   correcta como foto de perfil y como cierre de una pieza, pero NO puede ser el elemento que hace
   reconocible el feed.**
2. ⛔ **`PapGreen #25F48C` sobre blanco da 1,42:1.** Es decir: **ninguna pieza de Instagram se diseña
   sobre fondo blanco.** El sistema es **oscuro primero**, sobre la tinta `#0D1117` — que además es
   donde vive Instagram y donde el verde de marca se vuelve el elemento más brillante del encuadre.

**La firma visual del feed es otra cosa, y sale del propio producto: la CIFRA.**
La app ya tiene un rol tipográfico para una cifra que protagoniza su bloque (`statNumber` + `statLabel`
+ la línea de evidencia debajo). **Ésa es la unidad visual de marca de Instagram**: una cifra enorme
en Plus Jakarta Sans sobre tinta, su unidad pequeña, y debajo **la línea de evidencia** que dice de
dónde sale. Es reconocible a 150 px, no depende de que el logo se lea, y **comunica exactamente el
posicionamiento C**. La marca va de firma, abajo, pequeña, al final.

La identidad **no se cambia**: se le añade un nivel que la app ya tenía y las superficies de
marketing no estaban usando. (Y hereda su honestidad: una cifra sin línea de evidencia no se publica.)

### 5.2 Los cinco territorios

Cada uno responde a una pregunta distinta y tiene una forma repetible. Conviven en una proporción
fija para que el feed no se vuelva ni un panel de datos ni un diario personal.

| Territorio | La pregunta que responde | Trabajo | Forma | Formato | Peso |
|---|---|---|---|---|---|
| **1 · EL PARTE** | *¿Cuánto cuesta aparcar aquí?* | IDENTIFICACIÓN + CONVERSACIÓN | el parte del aparcamiento: zona, periodo, 3-5 cifras, muestra declarada | carrusel 4:5 · estático | **3/12** |
| **2 · LA VUELTA** | *¿Me reconozco en esto?* | ATENCIÓN | metraje real de conducción, cronómetro en pantalla, humillación compartida | Reel 9:16, 12-25 s | **3/12** |
| **3 · LA PRUEBA** | *¿De verdad funciona sola?* | DEMOSTRACIÓN | captura real + plano del mundo real, sin voz, texto en pantalla | Reel 9:16, 10-20 s | **3/12** |
| **4 · EL DATO QUE NO TE ESPERAS** | *¿Esto se podía saber?* | CONVERSIÓN | un dato duro y accionable (gálibo, precio del TU carburante, tu hora imposible) | carrusel 4:5 · Reel corto | **2/12** |
| **5 · QUIEN LA HACE** | *¿Por qué debería creerte?* | CONVERSACIÓN | el relato de 2019, los field-tests, lo que la app se NIEGA a afirmar | Reel 9:16 20-35 s · carrusel | **1/12** |

**Cómo conviven, y por qué en este orden.** LA VUELTA trae desconocidos (alcance). EL PARTE los
convierte en vecinos (recurrencia y comentarios). LA PRUEBA les hace creer (es la que precede a la
instalación). EL DATO cierra (es el que lleva el CTA más directo). QUIEN LA HACE es lo que hace que
perdonen un fallo. **Una pieza no hace dos de estos trabajos**: la que lo intenta no hace ninguno.

### 5.3 Dirección de arte — reglas, no gustos

- **Suelo**: tinta `#0D1117`. Un solo acento: `PapGreen #25F48C`. ⛔ Nunca verde de marca sobre blanco.
- **Tipografía**: **Plus Jakarta Sans** (el variable ya está en el repo:
  `docs/release/play-listing/assets/fonts/plus_jakarta_sans_variable.ttf`). Tres niveles y nada más:
  **cifra** (enorme, 700-800) · **titular** (semibold) · **evidencia** (regular, 65 % de opacidad).
  ⛔ Ningún otro peso, ninguna otra familia, cero cursivas, cero sombras de texto.
- **La cifra manda la composición.** Si una pieza no tiene cifra, tiene **una frase** del mismo
  tamaño, y entonces no lleva cifra en ningún otro sitio.
- **Línea de evidencia obligatoria** bajo cualquier número: *«medido en 14 viajes · El Puerto,
  centro · oct 2026»*. Hereda el contrato de la app: **ausencia de evidencia = ausencia de dato**, y
  una cifra sin cobertura se marca o no se publica. Esto es diferenciación, no letra pequeña.
- **La marca**: firma inferior, altura ~44 px en 1080, **sin disco** (la excepción del disco es sólo
  para el banner de Play y la web, nunca aquí), verde `#25F48C` sobre tinta. Lienzo no cuadrado
  (244×236) y **full bleed**: se encuadra por su envolvente, nunca metida a pelo en una caja cuadrada.
- **Metraje real, color real.** Sin filtros de color sobre las capturas: la UI de la app ya tiene su
  sistema de color y alterarlo es enseñar una app que no existe.
- **Sonido**: Reels **legibles en silencio** (texto en pantalla, siempre). Audio = ambiente real del
  coche/calle + música libre de derechos sin voz. ⛔ Nada de audios de tendencia ajenos al producto.
- **Lo que NO se dibuja nunca**: pantallas de la app (salen del pipeline), mapas inventados, marcas de
  coche reconocibles en primer plano, matrículas legibles, caras de terceros sin permiso.

### 5.4 Lo que una IA puede generar y lo que no

| Se genera con IA | Sale de la realidad |
|---|---|
| fondos, texturas, degradados, ambientes de calle sin texto | **toda** pantalla de la app (pipeline `shoot.sh` + presets) |
| ilustración conceptual (la manzana vista desde arriba, el hueco, la barrera) | metraje de conducción, de aparcar y de bajarse del coche |
| composiciones abstractas de apoyo | cualquier cifra (medida, no inventada) |
| | nombres de calle, horas y zonas reales |

⛔ **Y una regla de producción que ahorra el 90 % del dolor**: los generadores de imagen **no
escriben texto fiable en castellano**. Así que **la imagen generada va SIN texto** y todo el texto se
compone encima en **HTML + fuente real, renderizado con Chrome headless a PNG** — exactamente el
mecanismo que el repo ya usa para las 72 capturas de Play (`assets/screenshot-frame.html` +
`tools/render.sh`). El prompt maestro lo exige así.

---

## 6 · Plan de ejecución — 4 semanas, 12 piezas

**Cadencia: 3 piezas/semana** (lun · mié · vie, 19:00-21:00 hora local 🔵 a validar con Insights),
**4 Reels** en el mes, **+ 3-4 Stories/semana** por reglas, no por calendario.

Por qué 3 y no 7: la investigación dice que las cuentas pequeñas reciben más oportunidad por pieza, y
una pieza de este sistema necesita metraje real o un dato medido. **7/semana obligaría a inventar, y
lo inventado es justo lo que este plan prohíbe.**

### 6.0 Antes de la pieza 1 (semana 0, media tarde de trabajo)

1. **Atribución** (§7.1) — sin esto el mes no se puede leer.
2. **Auditar la cuenta que ya existe** — `@paparcar_oficial` — y anotar la **línea base**, que es lo
   único que convierte el mes 1 en una comparación en vez de una impresión. Seis preguntas:
   - ¿es **cuenta profesional**? Si no, cambiarla: sin eso no hay Insights y no hay hook rate.
   - seguidores y nº de publicaciones **hoy**, con fecha. Es el punto de partida.
   - ¿qué hay publicado, y **choca** con el sistema de §5? Lo que ya exista no se borra: lo que no
     encaje **se archiva** (Instagram permite archivar sin perderlo).
   - ¿la bio dice la promesa de §2 y lleva `paparcar.com/descargar`? Lo segundo sí; lo primero,
     revisarlo contra el texto de abajo.
   - ¿hay Insights con histórico? Si lleva meses publicando, **ya hay datos** de qué funcionó: eso
     se mira ANTES de producir P1.
   - ¿llega algún DM o comentario sin contestar? Contestarlos es lo más rentable del primer día.
3. **Bio** (ES, ≤150 car.):
   ```
   Paparcar · recuerda dónde aparcaste
   Y te dice lo que te cuesta encontrar sitio.
   Gratis, sin anuncios · Android
   ```
   ⛔ **Sin pin de localidad.** La bio es lo primero que lee alguien de Valencia que acaba de ver el
   Reel: un «📍 El Puerto» le dice que la app no es para él. El sitio donde se grabó vive en la línea
   de evidencia de las piezas (§4.2), no en el perfil.
   Enlace: `paparcar.com/descargar`. Foto: la marca sobre tinta, **centrada por tinta** (no por caja).
4. **Tres destacados vacíos creados**: `CÓMO VA` · `MIS CIFRAS` · `PREGUNTAS`. Son el único catálogo.
5. **Grabar el banco de metraje de una sola vez** (una tarde): 4-6 planos de conducción buscando
   sitio (GoPro/salpicadero), 3 de aparcar-y-bajarse, 2 de caminar hacia el coche, 1 de barrera de
   parking con su cartel de gálibo. Con esto se montan 6 de las 12 piezas.
6. **Leer el dato del primer PARTE en el móvil de producción**, periodo **«últimos 7 días»** —
   tiempo buscando plaza con su cobertura «N de M», hora punta, día habitual, sitio habitual,
   acierto de la detección. La misma pantalla **es** la captura de P2 y P5: una acción, dos salidas.
   Detalle de procedencia y umbrales: §7.3.

### 6.1 Reutilización del material existente — qué sí y qué no

| Material | Veredicto |
|---|---|
| Escena **12-como-funciona** (4,5 s: sale un coche, entra otro) | ✅ **Reutilizar entera.** Es el mejor explicador del repo y no necesita voz. Base de P3 y del cierre de varios Reels. |
| Recorrido de pantallas (escenas 17-24, 61,9 s, con sus cinco zooms justificados) | ✅ **Cantera, no pieza.** Cada zoom (precios del litro, las seis cifras, la ruta sobre la calle, el heatmap, las ventajas de Plus) es un plano de 2-3 s listo para un Reel. |
| Cartones 13/14/15 (aparcar, nace una plaza, otro la coge) — **sin grabar** | 🟡 Se graban en el banco de §6.0-5 y sirven para el vídeo Y para P3/P7. Un rodaje, dos destinos. |
| Relato en texto 01-11 (2019, la calle, los 40 metros) | ✅ **Traducir al castellano y comprimir a 25-30 s** para P9. El texto es bueno; la duración y el idioma, no. |
| Montaje completo `animatico.mp4` | ❌ No publicar en Instagram. |
| Cierre `24c-la-marca` (3 s, marca quieta, sin zoom) | ✅ Cierre estándar de todos los Reels. Coherencia gratis. |
| Las 72 capturas de Play (Dublín, 9 idiomas, marco de ficha) | ❌ Como imagen. 🟡 Como **pipeline, a medias**: es del flavor mock, así que sirve para enseñar un gesto y **no** para enseñar un dato. Ver §7.3. |

### 6.2 Las 12 piezas, por prioridad de producción

> Formato de ficha: **objetivo · enfoque · gancho (lo que se ve en el primer segundo) · formato ·
> CTA · hipótesis · métrica principal**. El contenido final (titulares, textos en imagen, copies,
> guiones y prompts de imagen) lo produce el prompt maestro; aquí están las **decisiones**.

---

**P1 · «La cuarta vuelta» — LA VUELTA** 🥇 *la primera que se produce*
- **Objetivo**: ATENCIÓN. Probar el gancho de identificación con el público 1.
- **Enfoque**: metraje real desde el coche dando la misma vuelta a la misma manzana cuatro veces, con
  un cronómetro sobreimpreso que no para. Corte final: la cifra.
- **Gancho (0-1 s)**: el cronómetro ya corriendo y el rótulo **«vuelta 4»** sobre la misma esquina.
- **Formato**: Reel 9:16, 15-20 s, sin voz, texto en pantalla, ambiente real.
- **CTA**: *«¿Y tú cuánto tardas? Paparcar lo mide.»* → enlace en bio.
- **Hipótesis (H1)**: hook rate ≥ 50 % y envíos/alcance ≥ 1 %, porque todo conductor urbano ha dado
  esa cuarta vuelta.
- **Métrica**: **hook rate**. Secundaria: envíos/alcance.

**P2 · «El parte de aparcamiento» — EL PARTE** 🥇
- **Objetivo**: IDENTIFICACIÓN + CONVERSACIÓN. Estrenar la franquicia recurrente.
- **Enfoque**: 4 láminas, una cifra cada una, con su línea de evidencia y su N real. Última lámina:
  la pregunta a comentarios.
- **Gancho (lámina 1)**: la cifra sola, enorme —**los minutos medidos**— con una frase que hable del
  **fenómeno** («esto es lo que cuesta aparcar en tu calle»). ⛔ La zona y la muestra van en la línea
  de evidencia, pequeñas: **el titular no nombra la localidad** (§4.2).
- **Formato**: carrusel 4:5 (1080×1350), 4-5 láminas. El gancho debe leerse dentro del cuadrado
  central (el grid del perfil recorta a 1:1).
- **CTA**: *«Dime tu calle en comentarios y te digo lo que sé de ella.»* — y funciona en toda España:
  la respuesta por DM es el dato de esa zona, o *«de esa calle no tengo medidas: serás el primero»*.
- **Hipótesis (H2)**: comentarios/alcance ≥ 1,5 %, muy por encima de cualquier pieza sin una cifra
  concreta.
- **Métrica**: **comentarios/alcance** + guardados/alcance.

**P3 · «Aparco, me bajo, me voy» — LA PRUEBA** 🥇
- **Objetivo**: DEMOSTRACIÓN. Matar la objeción «esto ya lo hace Maps».
- **Enfoque**: tres planos sin voz — aparcar y bajarse / el móvil **en el bolsillo** / pantalla real
  con el aparcamiento ya guardado y el minuto. Énfasis en lo que **no** se hace: no se abre la app,
  no se pone un pin, no se escribe una dirección.
- **Gancho (0-1 s)**: un dedo que **no** toca el móvil + rótulo **«sin tocar nada»**.
- **Formato**: Reel 9:16, 12-18 s. Captura real: preset `parked` + el widget.
- **CTA**: *«Gratis, sin anuncios. Enlace en la bio.»*
- **Hipótesis**: menor hook rate que P1 pero **mayor toques-en-enlace/alcance**: es la pieza que
  precede a la instalación.
- **Métrica**: **toques en el enlace / alcance**.

---

**P4 · «1,90» — EL DATO QUE NO TE ESPERAS (gálibo)**
- **Objetivo**: CONVERSIÓN en el público 3.
- **Enfoque**: el cartel real de gálibo de un parking, y al lado la altura del coche. Cierra con la
  regla honesta de la app: **un alto desconocido nunca es un «cabes»**.
- **Gancho**: el cartel «1,90 m» a pantalla completa, con el techo de la furgoneta entrando en cuadro.
- **Formato**: Reel 9:16 10-15 s **o** carrusel 3 láminas. Captura real: ficha de parking con gálibo.
- **CTA**: *«Mete el alto de tu coche y que te lo diga antes de entrar.»*
- **Hipótesis (H3)**: la mayor tasa de envíos/alcance del mes, con alcance bajo.
- **Métrica**: **envíos/alcance**.

**P5 · «Tu hora imposible» — EL PARTE #2**
- **Objetivo**: IDENTIFICACIÓN. Probar H2 con otra forma del mismo dato.
- **Enfoque**: el mapa de calor de *cuándo* aparcas y cuándo te cuesta más, con una conclusión en
  una frase (*«el martes a las 20:00 tardas X; el domingo a las 11, Y»*).
- **Gancho**: la frase del día y la hora peores, sola, sobre tinta.
- **Formato**: carrusel 4:5, 4 láminas. Captura real: preset `numbers`, panel «When you park».
- **CTA**: *«¿Cuál es tu hora imposible?»*
- **Métrica**: guardados/alcance (es información que se guarda, no que se comparte).

**P6 · «Sin abrir la app» — LA PRUEBA (widget)**
- **Objetivo**: DEMOSTRACIÓN de la función más tangible de la 1.4.0.
- **Enfoque**: pantalla de inicio del móvil, el widget dice dónde está el coche; un toque contesta el
  viaje **sin abrir la app**.
- **Gancho**: la pantalla de inicio y el widget con una dirección real.
- **Formato**: Reel 9:16 10-14 s **o** carrusel 2 láminas.
- **CTA**: *«Está en la 1.4.0. Enlace en la bio.»*
- **Métrica**: toques en el enlace / alcance.

---

**P7 · «¿Dónde lo has dejado?» — COCHE COMPARTIDO**
- **Objetivo**: CONVERSIÓN con referral nativo (público 4).
- **Enfoque**: dos móviles, dos personas, un coche. Uno aparca; el otro lo ve. La escena termina con
  la pregunta que **no hace falta hacer**.
- **Gancho**: el mensaje de WhatsApp «¿dónde has dejado el coche?» empezando a escribirse… y borrándose.
- **Formato**: Reel 9:16 15-20 s. Captura real: preset `sharedcar`, dos dispositivos.
- **CTA**: *«Compártelo con quien conduce tu coche.»* (CTA de **envío**, no de instalación: aquí el
  envío ES la instalación.)
- **Hipótesis**: la pieza con mayor **envíos/alcance** fuera del nicho de gálibo, porque el reenvío
  tiene un destinatario obvio.
- **Métrica**: envíos/alcance.

**P8 · «Tres gasolineras, la misma calle» — EL DATO**
- **Objetivo**: CONVERSIÓN con el público 5.
- **Enfoque**: el precio del carburante que gasta **tu** coche, con la hora de la medición, en una
  sola calle. Que esto sea gratis y completo es parte del mensaje.
- **Gancho**: la **diferencia en céntimos**, a tamaño de cifra, y debajo *«tres gasolineras. La misma
  calle.»* Ni marcas ni mapa en la primera lámina.
- 🟢 **Dato ya medido (14-10, registro oficial)**: en la Calle Albert Einstein de El Puerto hay tres
  gasolineras abiertas 24 h — nº17 a **1,789 €/L** de diésel, nº12 a 1,799 y nº16 a **1,925**.
  **0,136 €/L de diferencia en la misma calle**, 6,80 € por depósito de 50 L. En gasolina 95 la
  diferencia sube a 0,166 (8,30 €). Este gancho es mejor que el que yo había escrito —«dos precios
  del mismo barrio»— porque el absurdo ya no hay que explicarlo: está en la dirección postal.
- **Formato**: carrusel 4:5, 4 láminas. Captura: **PROD**, mapa con la capa de gasolineras en esa
  calle (§7.3).
- ⛔ **No se nombra a nadie como «el caro»**: se enseñan los tres precios y el lector concluye.
- ⚠️ **Los precios caducan a diario**: se vuelven a leer el día de publicar, y la hora de medición va
  en la línea de evidencia.
- **CTA**: *«Gratis, sin suscripción. Enlace en la bio.»*
- **Métrica**: toques en el enlace / alcance.

**P9 · «2019» — QUIEN LA HACE**
- **Objetivo**: CONVERSACIÓN y confianza.
- **Enfoque**: el relato que ya existe, en castellano y comprimido: un conductor dando vueltas, una
  plaza libre a 40 metros, nadie podía saberlo. Termina en el producto, no en el creador.
- **Gancho**: *«En 2019 vi a un conductor dar cinco vueltas a mi calle.»*
- **Formato**: Reel 9:16 25-35 s, texto en pantalla + voz propia (opcional).
- **CTA**: *«Por eso existe. Enlace en la bio.»*
- **Métrica**: retención (% que llega al final) + seguidores/alcance.

---

**P10 · Re-corte del mejor gancho — EXPERIMENTO DE GANCHO** 🧪
- **Objetivo**: ATENCIÓN. **Mismo cuerpo, gancho distinto**, sobre la pieza con mejor hook rate de
  las semanas 1-3. Es el único experimento con una sola variable del mes.
- **Métrica**: hook rate, comparado con el del original.

**P11 · «El parte del mes» — EL PARTE #3**
- ⛔ **Fecha mínima: 1 de noviembre.** No es una preferencia editorial: la medición válida del tiempo
  de búsqueda **empieza el 22-09-2026** (§7.3), así que hasta que octubre cierre no existe un mes
  completo de datos. Antes de esa fecha la pieza sería un mes a medias presentado como un mes.
- **Objetivo**: IDENTIFICACIÓN + recurrencia (la franquicia ya es reconocible a la tercera).
- **Enfoque**: el mes cerrado, con las cifras acumuladas **y** —si existe el dato— cuántas plazas ha
  liberado la comunidad en la zona. Si no existe, **se dice**: *«todavía ninguna que no sea mía»*.
  Esa frase es más creíble que cualquier cifra, y es la invitación real.
- **CTA**: *«Dime tu calle y el próximo parte puede llevarla.»*
- **Métrica**: comentarios/alcance. Se anota también **plazas publicadas por la comunidad**, pero
  como hipótesis en observación (§8.1), no como juicio de la pieza.

**P12 · «Lo que Paparcar NO hace» — QUIEN LA HACE / honestidad**
- **Objetivo**: CONVERSACIÓN.
- **Enfoque**: tres cosas que la app se niega a afirmar — no planta una plaza si no está segura
  (**pregunta**); no dice que cabes si no sabe tu alto; no cuenta minutos de búsqueda que no haya
  medido. En un sector que promete plaza garantizada, esto es el gancho.
- **Gancho**: *«Mi app podría mentirte en tres sitios. En los tres prefiere preguntar.»*
- **Formato**: Reel 9:16 20-30 s o carrusel 4 láminas.
- **CTA**: *«Pregúntame lo que quieras en comentarios.»*
- **Hipótesis (H4)**: mayor comentarios/alcance del mes.
- **Métrica**: comentarios/alcance.

### 6.3 Stories — reglas, no calendario

3-4/semana, y **sólo** de estos cuatro tipos: (1) la pieza del día con un «mándaselo a quien
corresponda»; (2) una **encuesta** de una pregunta (*«¿cuántos minutos tardas en aparcar en tu
calle?»* con 4 rangos) — es la forma más barata de conseguir el dato del PARTE siguiente **y** una
interacción; (3) respuesta pública a un comentario o a un DM; (4) un trozo de field-test real (el
pin que salió bien, o el que salió mal y se arregló).
⛔ **Nada de cuentas atrás, repost de citas ni «buenos días».**

---

## 7 · Instrumentación: sin esto no hay plan

### 7.1 ✅ Prerrequisito nº 1 — HECHO Y DESPLEGADO (14-10-2026)

> ✅ **`ddf1309f4` en master · desplegado en `paparcar.com` · las seis rutas verificadas leyendo la
> URL viva** (301 + `Location` correcto, y un `/descargar/noexiste` devuelve 404 — o sea que no hay
> glob accidental). Ticket:
> [`mkt-an-install-must-say-where-it-came-from-001`](../backlog/mkt-an-install-must-say-where-it-came-from-001.md).
>
> ⏳ **Lo único que queda, y ningún test lo puede hacer**: pinchar el enlace en un móvil, instalar,
> abrir, y ver en GA4 el `first_open` con `First user source/medium = instagram / social`. Hasta
> verlo **una vez**, la atribución está escrita y desplegada, pero no demostrada.

**Cuál era el problema** (se deja escrito porque es la razón de ser de todo lo demás): `/descargar`
ya funcionaba y ya estaba en la bio, pero sin `referrer` **Play apuntaba esas instalaciones como
orgánicas** y GA4 como *(direct)* — no se podía saber si la cuenta había traído una instalación o
trescientas. Y no había salida desde fuera del repo: los UTM de un enlace normal **se pierden** en el
salto a la tienda, y un 301 a URL fija **descarta** también la query entrante. Lo único que sobrevive
es el **install referrer** de Play, que Firebase lee en el primer `first_open`.

**Las seis rutas vivas**, cada una con su `referrer` percent-encoded:

```
/descargar            → …&referrer=utm_source%3Dinstagram%26utm_medium%3Dsocial%26utm_campaign%3Dbio
/descargar/parte      → …utm_campaign%3Dparte
/descargar/vuelta     → …utm_campaign%3Dvuelta
/descargar/galibo     → …utm_campaign%3Dgalibo
/descargar/compartido → …utm_campaign%3Dcompartido
/descargar/z-mostoles → …utm_medium%3Dpaid%26utm_campaign%3Dz-mostoles
```

🟢 **El arreglo fue sólo de servidor**: la bio no se toca y el enlace es el mismo, así que desde el
despliegue **todos los clics que ya se producían** se atribuyen. ⚠️ No es retroactivo: lo instalado
antes queda sin atribuir y no se recupera.

🛡 **Y lo vigila un guard nuevo en `WebSiteParityGuardrailTest`**, falsificado cinco veces: sin
`referrer`, `utm_campaign` que nombra otro territorio, valor **sin codificar** (un `&` literal lo
parte en parámetros de Play y la atribución se pierde sin un solo error), zona pagada declarada como
`social`, y el testigo de población vacía. **Una zona nueva se añade con su ruta o el build se pone
rojo.**

⛔ **`utm_medium` separa lo orgánico (`social`) de lo pagado (`paid`), y eso no es cosmético**: sin
esa separación el libro de cuentas de §8.2 mezcla instalaciones compradas con instalaciones ganadas
y el CPI deja de poder calcularse. **Una ruta por zona comprada** (`/descargar/z-leganes`,
`/descargar/z-moratalaz`…), creada el día que se lanza su campaña.

⛔ **Una entrada por ruta exacta, nunca una alternancia `@(a|b|c)`**: el repo ya pagó esa lección en
las reglas de cabeceras — *«desplegó limpio y no coincidió con NADA»*
[WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001]. Y se verifica leyendo la URL viva, no el deploy.

Ticket: ✅ **`MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001`** — `ddf1309f4`, desplegado.

✅ **Buena noticia verificada hoy: la mitad difícil ya está hecha.** El manifest fusionado de los
tres flavors declara `BIND_GET_INSTALL_REFERRER_SERVICE` (entra transitivamente con Firebase /
Play Services), así que **la app ya lee el install referrer de Play** y Firebase lo convierte en
*First user source/medium* en el primer `first_open`. No hay que añadir ninguna dependencia: falta
sólo que el redirect **envíe** el parámetro.

### 7.1.b 🔴 Prerrequisito nº 2 — y aquí hay que elegir, porque tiene coste de marca

Para que Meta pueda **optimizar por instalación** (campaña de objetivo «instalaciones de la app»)
hace falta su SDK en la app, o un MMP. Verificado en `gradle/libs.versions.toml`: **no hay ni SDK de
Meta ni AppsFlyer, Adjust, Branch, Singular ni Kochava.** Nada.

Las dos salidas, y no son equivalentes:

| | **A · Meter el SDK de Meta** | **B · Campaña de TRÁFICO a `/descargar/<zona>`** ✅ recomendada |
|---|---|---|
| Meta puede optimizar por instalación | ✅ | ❌ sólo por clic |
| CPI esperado | mejor | peor 🔵 |
| Atribución | la de Meta | **la del install referrer, que ya funciona** (§7.1) |
| Coste | un SDK de publicidad dentro de la app → **rehacer Data Safety y la política de privacidad** | ninguno |
| Coherencia con la marca | ⛔ **la rompe** | intacta |

⛔ **Recomiendo B, y la razón no es técnica.** La ficha y la web prometen, con estas palabras, *«tu
ubicación no se vende ni se usa para publicidad»* y *«sin anuncios»*. Meter el SDK de una red
publicitaria en esa app es contradecir el argumento de confianza que §1.3-O2 identifica como una de
sus tres ventajas — y encima obliga a tocar la declaración legal en los nueve idiomas. **Se paga el
peor CPI y se queda la promesa intacta.** Si algún día el CPI de B resulta prohibitivo, esa decisión
se vuelve a abrir **con el coste escrito**, no por comodidad.

⚠️ Consecuencia práctica de B: el CPI **lo calculas tú** (gasto de Meta ÷ instalaciones con
`referrer=instagram` en GA4), no te lo da el panel de anuncios. Es una división, pero hay que
acordarse de hacerla.

### 7.2 Dónde se ve, cuánto tarda, y cómo se distingue «no funciona» de «no procesado»

⛔ **Antes de mirar nada: un CLIC no genera el dato.** El install referrer sólo lo entrega Play en una
**instalación nueva**. Si la app ya está en ese móvil, pulsar el enlace sólo abre la ficha: no hay
`first_open`, no hay referrer, y GA4 no enseñará nada — **no porque esté roto, sino porque no había
nada que atribuir**. Ésta es la confusión que hace dar por fallido un montaje que funciona.

**La escalera de diagnóstico.** Cada peldaño responde a una pregunta distinta y tarda lo suyo; se
suben en orden y **no se concluye nada saltándose uno**:

| # | Dónde se mira | Cuánto tarda | Si está | Si falta |
|---|---|---|---|---|
| 1 | El `Location` del redirect, leído de la URL viva | inmediato | el clic sale etiquetado | el servidor está mal → es de este repo (§7.1) |
| 2 | **DebugView** de Firebase (o `logcat -s FA FA-SVC`): el evento `first_open` | **segundos** | la app arrancó por primera vez de verdad | no hubo instalación nueva, o falta el flag de debug |
| 3 | **Los parámetros de campaña de ese `first_open`** | **segundos** | ✅ **ésta es LA prueba: el referrer llegó** | Play no lo entregó → comprobar que el clic fue el del enlace etiquetado |
| 4 | **GA4 → Adquisición de usuarios**, dimensión *First user source / medium* = `instagram / social` | **hasta 24-48 h** | consolidado en informes | **sigue procesando: no concluir** |
| 5 | **Play Console → Adquisición de usuarios**, desglose por UTM | **~2 días** | visitas a la ficha e instalaciones por campaña | idem |

**La prueba controlada, que es lo único que cierra la duda** (10 minutos, en el móvil de pruebas —
⛔ **no en el de producción**, que pierdes el historial de detección):

```bash
# 1 · desinstalar, para que la siguiente instalación sea realmente la primera
adb uninstall com.rndeveloper.paparcar
# 2 · encender DebugView y los logs de Analytics
adb shell setprop debug.firebase.analytics.app com.rndeveloper.paparcar
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE
# 3 · pulsar paparcar.com/descargar EN EL MÓVIL, instalar desde Play y abrir
# 4 · leer el first_open y sus parámetros de campaña
adb logcat -v time -s FA FA-SVC
```

Un reinstalar crea un **app instance id nuevo**, así que las dimensiones de «primer usuario» se
vuelven a calcular — por eso la prueba vale aunque sea el mismo móvil y la misma cuenta.

⚠️ **Y lo que NO se puede hacer desde esta conversación**: leer GA4 o Play Console. El MCP de Firebase
trae Firestore, Auth, Crashlytics, Remote Config, Messaging, Functions y Hosting — **Analytics no
está**, y Play Console no tiene acceso. Lo de los peldaños 1-3 sí lo puedo verificar yo (la URL viva
y el `logcat` con el móvil conectado); los peldaños 4-5 los miras tú.
🔵 **Arreglo sistémico, si queremos que deje de depender de eso**: activar el **export de GA4 a
BigQuery** (tiene nivel gratuito). Con eso la pregunta «¿cuántos activos ha traído Instagram?» pasa a
ser una consulta en vez de una captura de pantalla, y es contestable desde aquí.

**Lo demás que ya existe y nadie ha mirado:**

- **Play Console → Adquisición de usuarios**: lleva meses acumulando, y es gratis.
- **Firestore**: `users/{uid}/parkingHistory` lleva `address.city` por sesión y `spots` lleva
  coordenadas → **dónde están ya los usuarios** (§4.2.1) y cuántas plazas publica la comunidad.
  *(⚠️ El MCP de Firebase falla con `read_time cannot be in the future` — desfase de reloj del
  equipo, ya anotado en memoria. Hay que leerlo desde la consola.)*

### 7.3 · De dónde sale cada cifra que publicamos

> La tabla de procedencia campo a campo, con su caducidad, vive en
> [`INSTAGRAM-PRODUCTION-PROMPT.md`](INSTAGRAM-PRODUCTION-PROMPT.md) § *Notas de uso*, que es donde
> se refresca. Aquí van sólo las tres decisiones que afectan al plan.

**1 · La autoridad es la PANTALLA de la app, no la base de datos.** Las filas crudas están en
Firestore (`users/{uid}/parkingHistory` sincroniza `searchTimeMs` por sesión), pero los agregados los
calcula el móvil con reglas propias — moda para la hora punta y el día habitual, aglomerativo a 120 m
para el sitio, ventanas medio-abiertas en la zona horaria del dispositivo. Sumar Firestore a mano da
un número que **ningún usuario ve en su pantalla**, y entonces la pieza cita una cifra que la app no
sabe producir. Firestore es la **auditoría**; la pantalla es la **fuente**.

**2 · El periodo es el de 7 días del plan gratuito.** Una cifra de todo el histórico sólo la ve quien
paga, así que el recién instalado **no podría reproducirla**: sería prometer en el anuncio lo que la
instalación no entrega — el mismo defecto que la doctrina de la ficha evita, una capa más arriba.
⚠️ Y hay umbrales que pueden dejar un hueco vacío, y eso no es un fallo: hora punta y día habitual
piden **≥5** sesiones terminadas; el sitio habitual, un clúster de 120 m con **≥3**; la duración
media, **≥3**; una celda del mapa de calor, **≥2**. Si no llegan, **la lámina se cae** — nunca se
ensancha el periodo en silencio para que salga un número.

**3 · 🔴 Y el hallazgo que corrige lo que escribí en §4.5**: el pipeline de capturas es **del flavor
mock y sólo de él** (`ShotPresets` vive en `:mockkit`; el mundo de demostración es Dublín). O sea que
**no puede producir una pantalla con tus cifras reales ni con una calle del estudio**. Por
tanto el reparto es fijo:

| Pieza | Origen de la captura | Por qué |
|---|---|---|
| P2 · P5 (tus cifras) · P4 (gálibo real) · P8 (precios) | **PROD, tu móvil** | el **contenido** de la pantalla es el mensaje |
| P3 (aparcado) · P6 (widget) · P7 (compartido) | mock aceptable | el mensaje es el **mecanismo** |

⛔ Una pieza con titular real y pantalla fabricada **se contradice a sí misma**, y encima en una
campaña cuya premisa entera es ser del barrio del espectador. El precio de capturar en prod es una
lista de limpieza antes de disparar: **zonas privadas fuera de plano, sin dirección de casa, sin
matrícula, sin nombres, sin el uid.** Es la misma lista que la escena del mapa del vídeo del Shipaton
esquivó usando mock — ahí se pudo; aquí no, porque aquí el dato *es* el mensaje.

**La fecha que condiciona el calendario**: la migración a Room v20 (`82e55f5ae`, **22-09-2026**)
anuló todos los `searchTimeMs` medidos con la fórmula retirada y empujó la anulación a Firestore. Por
un lado, limpio: cualquier valor no nulo de hoy está medido con la fórmula vigente y no hay nada que
filtrar. Por otro, **sólo hay medición desde el 22-09**: N va a ser pequeño —se declara— y el parte
mensual no puede salir antes del 1 de noviembre.

---

## 8 · Sistema de experimentación y medición

### 8.1 Dos familias de métricas que no se mezclan nunca

| | **ATENCIÓN** (gratis, Insights de IG) | **INTENCIÓN** (lo que importa) |
|---|---|---|
| Qué mide | si la pieza para el scroll y se mueve | si alguien quiere la app |
| Métricas | **hook rate** (3 s / impresiones) · **envíos/alcance** · guardados/alcance · comentarios/alcance · retención · seguidores/alcance | **toques en el enlace / alcance** · **visitas a la ficha de Play con `utm_source=instagram`** · **instalaciones con `referrer=instagram`** · **D1 «primer aparcamiento medido»** · **D7** |
| Referencia | hook rate ≥ 50 % · envíos/alcance ≥ 1 % 🔵 | 🔵 sin base: **el mes 1 ES la base** |
| Trampa | 30.000 vistas de gente sin coche, o de fuera de España | — |

**Y por encima de las dos, la métrica rectora** (§4.1): **usuarios activos ganados** —
instalaciones atribuidas que producen ≥1 aparcamiento medido en sus primeros 7 días— y, en el carril
pagado, su **CPA = gasto ÷ usuarios activos ganados**. Un mes con 40.000 vistas, 300 instalaciones y
**cero aparcamientos medidos** es un mes fallido, y hay que poder decirlo con esa frase.

**Plazas publicadas por la comunidad**: se sigue contando y se anota, **pero como hipótesis en
observación, no como KPI**. La aritmética de §4.1 pone su umbral a 6-12 meses; juzgar cuatro semanas
con ella sería declarar un fracaso garantizado de antemano.

⛔ **Nunca se reporta «vistas» sin su denominador ni «me gusta» como resultado.** Un like no es una
señal de distribución relevante en 2026 y no es una señal comercial en ningún año.

### 8.2 El libro de cuentas — una tabla, se añaden filas

| # | Pieza | Territorio | Trabajo | Enlace | Hook rate | Envíos/alcance | Coment./alcance | Toques enlace/alcance | Instal. (referrer) | Veredicto |
|---|---|---|---|---|---|---|---|---|---|---|
| P1 | La cuarta vuelta | LA VUELTA | ATENCIÓN | bio | | | | | | |
| P2 | El parte · centro | EL PARTE | IDENTIF. | /parte | | | | | | |
| P3 | Aparco y me voy | LA PRUEBA | DEMOSTR. | bio | | | | | | |
| P4 | 1,90 | EL DATO | CONVERS. | /galibo | | | | | | |
| P5 | Tu hora imposible | EL PARTE | IDENTIF. | /parte | | | | | | |
| P6 | Sin abrir la app | LA PRUEBA | DEMOSTR. | bio | | | | | | |
| P7 | ¿Dónde lo has dejado? | COMPARTIDO | CONVERS. | /compartido | | | | | | |
| P8 | Tu carburante | EL DATO | CONVERS. | bio | | | | | | |
| P9 | 2019 | QUIEN LA HACE | CONVERSAC. | bio | | | | | | |
| P10 | Re-corte 🧪 | (el mejor) | ATENCIÓN | = original | | | | | | |
| P11 | El parte de octubre | EL PARTE | IDENTIF. | /parte | | | | | | |
| P12 | Lo que NO hace | QUIEN LA HACE | CONVERSAC. | bio | | | | | | |

**Se rellena a las 72 h de publicar y otra vez a los 7 días.** Las dos lecturas, porque un Reel puede
despertar a la semana y una decisión tomada a las 24 h es ruido.

### 8.3 Reglas de decisión — escritas ANTES de ver los datos

Para que no se racionalicen los resultados después:

- **Retirar un territorio**: 3 piezas publicadas y mediana de hook rate < 35 % **y** envíos/alcance
  < 0,5 %. Se retira — no se «mejora».
- **Escalar un gancho**: hook rate ≥ 50 % → se re-corta en 3 variaciones (P10 es la primera) y pasa
  a ser la plantilla del territorio.
- **Matar el posicionamiento C**: si tras 6 piezas la mediana de envíos/alcance de los territorios
  1+2 **no** supera a la de 3+4, H1 ha caído. Entonces el gancho pasa a ser **la demostración**
  (territorio LA PRUEBA) y C se queda sólo en EL PARTE.
- **Parar de publicar y arreglar el producto**: si llegan ≥ 50 instalaciones con `referrer=instagram`
  y **D1 «primer aparcamiento medido» < 40 %**, el problema no es el marketing. Se para y se mira el
  onboarding y los permisos. *(La app sabe decirlo: mide el % de acierto de su propia detección.)*
- **Declarar una ciudad como objetivo de densidad**: cuando publique plazas que no sean tuyas, dos
  semanas seguidas. Hasta entonces no hay ciudad objetivo, sólo estudio (§4.2).
- **Volverse local**: si tras 12 piezas nacionales la mediana de toques-en-enlace/alcance está por
  debajo del 0,5 % 🔵 y en cambio una pieza con nombre de calle lo dobla, H2 gana y entonces **sí**
  se estrecha a una ciudad. Es decir: lo local se **gana con datos**, no se asume.
- **Empezar a pagar**: nunca antes de tener una pieza con hook rate ≥ 50 % **y** atribución viva.
  Cuando se cumpla, la primera campaña es **una sola zona**, objetivo tráfico a `/descargar/z-<zona>`
  (§7.1.b), **5-10 €/día durante 7 días**, radio de 1,6-3 km, «viven o han estado aquí», y ⛔ **«no
  expandir la audiencia» marcado** (§4.2.2). Lo que se mide no es el alcance: es **CPI = gasto ÷
  instalaciones con `utm_medium=paid`**, y si el CPI real sale por encima de ~3 € 🔵 la apuesta de
  densidad por pago deja de ser barata y hay que decirlo antes de meter más dinero.

### 8.4 ⚠️ Lo que este mes NO puede demostrar

Con **12 piezas** sólo se detectan **diferencias grandes**. No habrá significación estadística de
nada, y cualquiera de los resultados puede ser una pieza afortunada. Por tanto:

- La semana 4 produce **una dirección**, no un veredicto. El veredicto llega a las ~30 piezas.
- Un único Reel que rompe **no** valida un territorio: valida un gancho.
- Y el mes puede terminar con 0 instalaciones atribuidas y **sin que eso signifique que la estrategia
  es mala** — significaría que el cuello de botella está antes (gancho) o después (ficha), y §8.1
  dice en qué fila mirar.

**Lo que sí se habrá ganado con seguridad**: atribución funcionando, un banco de metraje real, una
franquicia de contenido con tres entregas, y un libro de cuentas con doce filas. Eso es la
infraestructura de los meses 2-6, y no existe hoy.

---

## 9 · Publicidad en Meta — el proceso completo

> Objetivo de esta sección: que se pueda lanzar la primera campaña **sin volver a decidir nada**.
> Las cifras marcadas 🔵 son hipótesis de arranque; la primera semana las sustituye por las reales.
> Mandan las reglas de §9.6.

### 9.0 · La configuración de arranque, de un vistazo

| Qué | Valor | Por qué |
|---|---|---|
| Objetivo de campaña | **Tráfico** | ⛔ No «Instalaciones de la app»: exige el SDK de Meta y lo hemos descartado por coste de marca (§7.1.b) |
| Destino | `paparcar.com/descargar/z-mostoles` | 301 a Play **con `referrer`**, que es lo único que sobrevive el salto a la tienda (§7.1) |
| Zona | **Móstoles**, pin en el centro, radio **3 km** | §4.2.3 |
| Plataforma | **Instagram** solo | la creatividad es 9:16 y la cuenta es de IG |
| Presupuesto de prueba | **7 €/día × 7 días = 49 €** | suficiente para medir CPC, barato para equivocarse |
| Qué se prueba | **el CPA**, no el alcance | §4.1 |

### 9.1 · Antes de gastar un euro — seis pasos, en este orden

1. **Cuenta profesional de Instagram** (§6.0) + **portafolio empresarial** en Meta Business Suite,
   con la cuenta de IG vinculada.
2. **Cuenta publicitaria** dentro del portafolio + método de pago.
3. 🔴 **El redirect, desplegado y PROBADO en un móvil de verdad.** `/descargar/z-mostoles` →
   `…&referrer=utm_source%3Dinstagram%26utm_medium%3Dpaid%26utm_campaign%3Dz-mostoles` (§7.1).
   La prueba es: pinchar el enlace, instalar, abrir, y **ver el `first_open` en GA4 con
   `First user source/medium = instagram / paid`**. ⛔ Sin esa comprobación hecha una vez, el mes se
   gasta a ciegas y no se recupera después.
4. **Montar el denominador del CPA en GA4**, con los eventos que la app ya emite —verificados en
   `FirebaseAnalyticsTracker`—:
   - evento de actividad: **`parking_session_started`** (lleva `detection_path` y `arm_evidence`, así
     que se puede separar lo detectado de lo marcado a mano);
   - audiencia: usuarios con `First user source/medium = instagram / paid`;
   - **usuario activo ganado** = usuario de esa audiencia que dispara `parking_session_started`
     **al menos una vez en sus primeros 7 días**.
   *(Otros eventos del embudo, por si hacen falta: `spot_released`, `spot_reported`, `paywall_shown`.)*
5. **Tres creatividades**, y ⛔ **ninguna nueva**: las tres mejores por hook rate del carril orgánico
   (§8.3). Pagar por distribuir un gancho sin probar es comprar un gancho sin probar.
6. **Coherencia de ficha y política** — ya lo está; sólo hay que no romperla con el copy del anuncio.

### 9.2 · Estructura y nomenclatura

Tres niveles, y el nombre es lo que después hace legible el libro de cuentas:

```
Campaña          PAP | TRAFICO | 2026-10
└─ Conjunto      ZONA-MOSTOLES | r3km | 25-55 | IG
   ├─ Anuncio    A1-vuelta        (gancho: la cuarta vuelta)
   ├─ Anuncio    A2-prueba        (gancho: sin tocar nada)
   └─ Anuncio    A3-cifra         (gancho: los minutos medidos)
```

- **Un conjunto por zona.** El conjunto es donde viven la geografía y el presupuesto; el anuncio es
  donde vive el gancho. Así cada cosa se prueba por separado.
- ⛔ **No fragmentar el presupuesto**: cuatro conjuntos a 3 € aprenden cuatro veces peor que uno a
  7 €. Una zona a la vez hasta tener su CPA.

### 9.3 · El conjunto de anuncios, campo por campo

| Campo | Valor | Nota |
|---|---|---|
| Ubicación | **Pin** en el centro de Móstoles, **radio 3 km** | el mínimo que admite Meta es ~1,6 km; 3 km cubre el núcleo y entrega mejor |
| Tipo de ubicación | **«Personas que viven o han estado hace poco en este lugar»** | la otra opción incluye a quien está de paso, y un visitante no aparca a diario |
| **Expansión de audiencia** | ⛔⛔ **«No expandir» MARCADO** | desde feb-2026 Meta sirve fuera de tu límite en silencio si no puede llenarlo — comprarías justo lo contrario de una zona |
| Edad | 25-55 | tiene coche y lo aparca en la calle |
| Género | todos | — |
| Idioma | español | — |
| Intereses detallados | **ninguno (abierto)** | con 7 €/día un público estrecho no sale del aprendizaje; y «coches» trae aficionados, no gente que aparca |
| Plataformas | **manual → Instagram: Reels, Stories, Explorar, Feed** | ⛔ sin Facebook ni Audience Network: la creatividad es vertical y el resto diluye el aprendizaje |
| Optimización | **clics en el enlace** | ⛔ «visitas a la página de destino» exige píxel, y el píxel está descartado (§9.8) |
| Presupuesto | **diario, 7 €** | diario y no total: se puede parar cualquier día |
| Calendario | 7 días, **todo el día** la primera semana | restringir horas antes de tener datos es inventarse cuándo aparca la gente |

⛔ **Y una regla operativa que cuesta dinero romper: no se toca el conjunto en las primeras 72 h.**
Cualquier cambio de presupuesto, público o puja **reinicia la fase de aprendizaje** y tiras lo gastado.

### 9.4 · Los anuncios

- **Formato**: 1080×1920 (9:16), el mismo archivo del Reel orgánico, **legible sin sonido**.
- **Texto principal**: lo importante en los primeros ~125 caracteres (después se corta con «más»).
- **Titular**: ≤40 caracteres.
- **Botón**: «Instalar ahora» o «Más información» — se prueban los dos, uno por semana.
- **Una sola variable entre los tres anuncios: el GANCHO.** Mismo cuerpo, mismo destino, mismo botón.
  Si cambias dos cosas no sabes cuál ganó.
- ⛔ **El copy del anuncio obedece la misma lista de hechos verificados que el orgánico** (§2 del
  prompt). Meta revisa los anuncios y rechaza las afirmaciones exageradas; y una promesa que la app
  no cumple se paga después en reseñas de una estrella, que sale más caro que el anuncio.
- ⚠️ **Límite asumido**: la atribución de instalación es **por zona, no por anuncio** (un solo
  redirect por zona). Qué anuncio funciona lo dice su **CTR en Meta**; cuánto cuesta un activo lo dice
  el conjunto. Un redirect por anuncio multiplicaría rutas y guardarraíles por muy poco.

### 9.5 · Qué se mira, dónde, y en qué orden

| Métrica | Dónde | Para qué | Referencia 🔵 |
|---|---|---|---|
| CPM | Meta | si la zona es cara | 2-6 € |
| **CTR (enlace)** | Meta | **si la creatividad funciona** | ≥ 0,8 % |
| CPC (enlace) | Meta | el coste de traer a alguien a la ficha | 0,10-0,40 € |
| Visitas a la ficha | Play Console → Adquisición | cuánto se pierde entre el clic y la ficha | — |
| Instalaciones `utm_medium=paid` | GA4 / Play Console | el numerador de todo | — |
| **Usuarios activos ganados** | GA4 (audiencia + `parking_session_started`) | **el único resultado** | — |
| **CPA = gasto ÷ activos** | a mano | **la decisión** | ≤ 3 € |

⚠️ **Las ventanas no coinciden**: Meta apunta el clic de hoy, la instalación puede ser de mañana y el
primer aparcamiento medido, de pasado. **Se compara por semanas completas, nunca día contra día.**

### 9.6 · Reglas de decisión, con números y escritas antes

- **Día 3 — se mira sólo el CTR.** Si CTR(enlace) < 0,8 % 🔵 el problema es **la creatividad**: se
  cambian los anuncios y **no se toca el público**. (Si el CTR es bueno y no llegan instalaciones, el
  problema está en la ficha, no en el anuncio.)
- **Día 7 — se calcula el CPA** y manda esta tabla:

  | CPA | Qué se hace |
  |---|---|
  | **≤ 3 €** | 🟢 **escalar** (§9.7) |
  | 3-6 € | 🟡 **una** iteración de creatividad, otros 7 días, mismo presupuesto |
  | **> 6 €** | 🔴 **parar el pagado** y volver al orgánico. Decirlo en voz alta, no «probar un mes más» |

- **Si llegan instalaciones pero activos ÷ instalaciones < 40 %**: ⛔ el problema **no es el
  marketing**. Se para y se mira el onboarding y los permisos — es la regla que ya estaba en §8.3, y
  la app sabe contestarla porque mide el acierto de su propia detección.
- **Subida de presupuesto: nunca más de +20 % al día.** Más que eso reinicia el aprendizaje.
- ⛔ **No pagar nada** antes de tener una creatividad con **hook rate ≥ 50 %** orgánico.

### 9.7 · Escalar: la escalera de zonas

Cuando una zona dé CPA ≤ 3 €:

1. **Duplicar el conjunto** a **Leganés**, luego **Alcorcón**, luego **Fuenlabrada** — uno por
   conjunto, **mismo presupuesto y la MISMA creatividad congelada**. La variable es la zona.
2. Comparar **CPA por zona** a los 7 días. Las zonas no son intercambiables: una puede costar el
   doble que la de al lado.
3. Sólo entonces subir presupuesto, y en la ganadora, +20 %/día como máximo.
4. **Y aquí reaparece la densidad**: cuatro zonas de la misma corona con CPA conocido es, por primera
   vez, un camino calculable hacia las 200-400 instalaciones concentradas de §4.1 — ya no una
   esperanza.

### 9.8 · Lo que NO se hace

| ⛔ | Por qué |
|---|---|
| El botón **«Promocionar»** de Instagram | no deja elegir radio, ubicaciones ni optimización: es pagar sin control |
| Objetivo **«Instalaciones de la app»** | sin SDK de Meta no puede optimizar, y el SDK está descartado (§7.1.b) |
| **Píxel de Meta** en `paparcar.com` | habilitaría «visitas a la página» y retargeting, pero obliga a consentimiento de cookies y mete un rastreador en la web de una app que promete que no vende tu ubicación. Si el CPC lo exigiera, **se reabre con el coste escrito**, no por comodidad |
| Intereses detallados con 7 €/día | público estrecho + presupuesto pequeño = no sale del aprendizaje |
| Cuatro conjuntos a 3 € | §9.2 |
| Tocar el conjunto antes de 72 h | reinicia el aprendizaje |
| Facebook y Audience Network | §9.3 |
| Pagar antes del hook rate ≥ 50 % | §9.6 |

### 9.9 · Calendario de las dos primeras semanas

| Día | Qué |
|---|---|
| **−3** | Redirect `/descargar/z-mostoles` desplegado y **probado en device** (§9.1-3) |
| **−2** | Portafolio, cuenta publicitaria, pago. Audiencia y evento montados en GA4 (§9.1-4) |
| **−1** | Subir los 3 anuncios. Repasar la lista de §9.8 una por una |
| **1** | Lanzar. **No mirar nada** |
| **2** | No mirar nada (fase de aprendizaje) |
| **3** | Sólo CTR. Decisión de creatividad si procede (§9.6) |
| **4-6** | Dejar correr |
| **7** | Cerrar semana: CPC, instalaciones, **activos, CPA**. Decisión de la tabla |
| **8** | Según la decisión: escalar a Leganés · iterar creatividad · parar |
| **8-14** | Segunda semana con **una sola** variable cambiada, y la fila del libro de cuentas (§8.2) rellenada a los 7 días |

---

## 10 · Siguiente paso — dos carriles, y uno habilita al otro

**Carril 1 · ORGÁNICO (empieza ya).** Producción con
[`INSTAGRAM-PRODUCTION-PROMPT.md`](INSTAGRAM-PRODUCTION-PROMPT.md) — autónomo, sin necesidad de este
repo ni de esta conversación. Orden: **P1 · P2 · P3** primero (una semana de contenido), después
P4-P6, y P10 **sólo cuando haya datos de las anteriores**.
Antes de producir: ejecutar §6.0 (cuenta, bio, destacados, banco de metraje, el dato del primer
parte).

**Carril 2 · PAGADO (arranca en cuanto haya un gancho ganador).** El proceso entero está en **§9**.
Se puede ir montando la fontanería en paralelo, porque no depende del creativo:

1. ✅ **El redirect con `referrer`** — hecho y desplegado, `/descargar/z-mostoles` incluida (§7.1).
   ⏳ Falta **verlo en un móvil** una vez: instalar desde el enlace y ver el `first_open` en GA4.
2. **Portafolio empresarial, cuenta publicitaria y pago** en Meta.
3. **La audiencia y el evento en GA4** (`First user source/medium` + `parking_session_started`), que
   es el denominador del CPA.

⛔ **La puerta entre los dos carriles es una sola condición** (§9.6): no se paga un euro hasta tener
una creatividad con **hook rate ≥ 50 %** orgánico. Lo de arriba se puede tener listo antes; el gasto,
no.

### Fuentes externas consultadas (2026-10-13)

- [How the Instagram Algorithm Works in 2026 — eclincher](https://www.eclincher.com/articles/how-the-instagram-algorithm-works-in-2026) · [Instagram Reels Algorithm 2026: What Mosseri Confirmed](https://www.highstyle.ai/insights/instagram-reels-algorithm-2026) · [Instagram Algorithm 2026: Ranking Signals Meta Confirmed](https://www.reeldrop.io/learn/instagram-algorithm)
- [The Instagram Reels 2026 Playbook: Hooks, Metrics, Algorithm](https://creatorhouse.app/blog/instagram-reels-2026-playbook) · [Instagram Reels Reach 2026 — TrueFuture Media](https://www.truefuturemedia.com/articles/instagram-reels-reach-2026-business-growth-guide)
- [Indie iOS App Marketing Strategy 2026: An Honest Playbook — ScreenFast](https://screenfast.app/blog/indie-ios-app-marketing-strategy-2026)
- [Hyperlocal Marketing Strategies for Local Business Growth in 2026 — SocialPilot](https://www.socialpilot.co/blog/hyperlocal-marketing) · [Get Local With It: Building Your Hyperlocal Brand on Instagram — RISMedia](https://www.rismedia.com/2026/08/25/get-local-with-it-building-your-hyperlocal-brand-on-instagram/)
- [Deep Linking for Social Media — Linkrunner](https://linkrunner.io/blog/deep-linking-for-social-media-how-to-route-organic-social-traffic-into-your-app) · [How to Add UTM Parameters to Your Instagram Bio Link (2026)](https://unil.ink/blog/how-to-add-utm-instagram-bio)
- Segmentación por zona (§4.2.2): [Geographic Targeting for Meta Ads: Guide 2026 — Bigeye](https://www.bigeyeagency.com/insights/geographic-targeting-for-meta-ads-guide-2026) · [Meta Ads Location Targeting: Minimum Radius, Geo Hacks](https://thread-transfer.com/blog/2026-06-17-meta-ads-location-targeting-radius-minimum/) · [Radius Targeting Instagram Ads — Coinis](https://coinis.com/how-to/radius-targeting-instagram-ads) · [Radius Targeting on Google and Meta, Compared — AdFlint](https://www.adflint.com/blog/local-radius-targeting-google-and-meta)
- SER de Madrid 2026 (§4.2.3, 🔵 prensa — confirmar en el plano oficial): [Madrid ampliará las zonas SER a partir de 2026 — elEconomista](https://www.eleconomista.es/motor/noticias/13705749/12/25/madrid-ampliara-las-zonas-ser-a-partir-de-2026-nuevos-barrios-afectados-cambios-en-parquimetros-y-horarios.html) · [Zona SER Madrid 2026: horarios y expansión — diario.madrid.es](https://diario.madrid.es/blog/2025/12/30/zona-ser-en-madrid-2026-cambio-de-horarios-expansion-a-mas-barrios-y-nueva-funcion-para-desaparcar/) · [Plano oficial de zonas SER](https://www.esmadrid.com/plano-zonas-ser-madrid) · [El parquímetro llega a 22 nuevos barrios — Libertad Digital](https://www.libertaddigital.com/madrid/2026-03-26/adios-al-aparcar-gratis-en-madrid-el-parquimetro-llega-a-estos-22-nuevos-barrios-7380567/) · [Aparcar gratis en Madrid 2026: zonas sin SER](https://lamaletainquieta.com/aparcar-gratis-madrid/)
- [Los españoles dedican una media de 16 minutos al día a aparcar — elEconomista](https://www.eleconomista.es/mercados-cotizaciones/noticias/21392/05/06/Los-espanoles-dedican-una-media-de-16-minutos-al-dia-a-aparcar.html) · [¿Cuánto tiempo se derrocha buscando aparcamiento? — elEconomista](https://www.eleconomista.es/ecomotor/motor/noticias/7672112/06/16/Cuanto-tiempo-se-derrocha-buscando-aparcamiento.html)
- [App Parking Madrid — Ayuntamiento de Madrid](https://www.madrid.es/portales/munimadrid/es/Inicio/Movilidad-y-transportes/App-Parking-Madrid-ocupacion-de-aparcamientos-en-tiempo-real/) · [AparcaMadrid](https://aparcamadrid.com/) · [Parquímetro y Parking Parclick — Google Play](https://play.google.com/store/apps/details?id=com.parclick) · [SpotAngels — App Store reviews](https://apps.apple.com/us/app/spotangels-parking-gas/id897809583?see-all=reviews)
