# Prompt para rellenar la ficha de Play de la siguiente versión (Claude web + conector de Play Console)

> Destinatario: **Claude en claude.ai con el conector/plugin de Google Play Console**, que puede LEER
> la ficha actual y RELLENAR los campos. No es para una sesión de Claude Code: ahí no hay repo.
> Por eso el prompt es autocontenido — lleva dentro la doctrina del copy, el vocabulario de los 9
> idiomas y lo que entra en esta versión.
>
> Copiar de la línea de abajo hasta el final y pegarlo tal cual.
>
> ⚠️ Los datos de versión están medidos el **2026-09-10**. Antes de reutilizarlo, actualizar el
> bloque «De dónde partimos» y la lista de novedades.

---

Eres mi copy de tienda para **Paparcar**, una app Android de aparcamiento. Tienes el conector de
**Google Play Console**. Quiero sacar la versión **1.1.0** a producción y necesito que **revises la
ficha entera, campo por campo, y me la dejes rellenada** en los 9 idiomas.

Trabajamos en dos fases y **no empieces la segunda sin enseñarme la primera**:

**Fase 1 — auditoría.** Lee del Console lo que hay HOY (ficha en los 9 idiomas + sección «Contenido
de la app» + monetización) y devuélveme una tabla: **campo · qué dice hoy · cambia o no · por qué ·
¿bloquea la publicación?**. Si un campo depende de una decisión mía, no lo adivines: pregúntamelo.

**Fase 2 — redacción y relleno.** Con mi OK, escribes el copy nuevo y **lo dejas puesto en cada
campo del Console**, idioma por idioma. Al terminar, dame el listado de qué cambiaste y qué queda
pendiente de mí.

## De dónde partimos

- **Publicado**: Paparcar **1.0.0** (versionCode 10), en producción desde el 05-09-2026. La ficha ya
  está completa en 9 idiomas: **en-US** (predeterminada), **es-ES, it-IT, pt-PT, fr-FR, de-DE, nl-NL,
  pl-PL, ro**, con 8 capturas de teléfono y gráfico de funciones localizado por idioma.
- **Lo que sale ahora**: **1.1.0**. El AAB lo compilo y lo subo yo — tú no tocas el binario ni la
  versión. Tu trabajo es la ficha y los formularios de contenido.
- **Título y descripción breve actuales** (no los cambies sin decírmelo; están medidos y encadenan
  con la primera línea de la descripción completa):
  - en-US: `Paparcar: where did you park` (28/30) · breve: `Where did you park? Paparcar remembers — and your spot passes on.` (65/80)
  - es-ES: `Paparcar: dónde aparcaste` (25/30, **con tilde**)
- **Campos de contacto**: web `https://paparcar.com` · política `https://paparcar.com/privacy-policy`
  · borrado de cuenta `https://paparcar.com/delete-account` · categoría *Mapas y navegación*.
  Si en el Console ves alguna URL con `pap-26.web.app`, avísame: está obsoleta.

## Qué entra en 1.1.0 (esto es el material del copy)

1. **Paparcar Plus, suscripción de pago** — con paywall propio y restaurar compras. Es la novedad que
   más papeleo mueve: compras integradas, Seguridad de los datos (información financiera + un
   procesador de pagos externo) y clasificación de contenido a rehacer.
2. **Parkings públicos** en el mapa y en la lista, con su tipo, tarifa y gálibo **cuando se saben**.
3. **Gasolineras con el precio real del surtidor**, media nacional fechada y gasto estimado por
   coche. ⚠️ Precio oficial **solo en España y Francia**; en el resto del mundo no hay precio.
4. **Widget de pantalla de inicio**: dónde está el coche aparcado, el viaje en curso, y la lista de
   coches en el tamaño grande.
5. **Actividad por vehículo**: kilómetros, litros/kWh y gasto, con un panel que explica de dónde sale
   cada cifra.
6. **Zonas**: si aparcas en una zona amplia (un parking grande, una calle larga), se guarda como zona
   y lo que se libera al irte es una plaza aproximada, nunca un pin exacto que no existe.
7. **El plan gratuito queda acotado**: 7 días de historial, 1 coche, 2 zonas.

## Doctrina del copy (esto es lo que no puedes deducir leyendo la ficha)

- **La ficha promete UNA sola cosa: que la app sabe dónde aparcaste.** Eso se cumple con un usuario
  solo, sin comunidad, desde la primera instalación.
- **Encontrar plazas se DESCRIBE, nunca se promete** en título, descripción breve ni gráfico: depende
  de que haya gente usándola en cada ciudad. Prometerlo compra reseñas de 1 estrella por mapa vacío.
  **La misma regla se aplica ahora a parkings y gasolineras**: van descritos, en la sección de
  funciones de la descripción completa, no en el título ni en la breve.
- Lo comunitario se cuenta **desde lo que el usuario DA** («y tu plaza pasa al siguiente»), no desde
  lo que recibe: eso depende solo de él, así que no es promesa de disponibilidad.
- **Free vs Plus**: *gratis = tú preguntas y la app responde · Plus = la app te vigila y recuerda por
  ti*. **VER plazas, parkings y gasolineras es gratis y completo** — no escribas nunca que se paga
  por verlos. Lo de pago es la vigilancia (avisos) y la memoria (historial más allá de 7 días).

## ⛔ Prohibido escribir

- Cualquier **promesa de cobertura**: ni países, ni «plazas libres en el parking», ni ocupación en
  tiempo real. Los datos de parkings vienen de una base abierta mundial pero desigual.
- Cualquier **alerta que no existe**: hoy no hay avisos de parking ni de gasolinera.
- **«Apaga la detección del coche que quieras»** — es FALSO: el ajuste es global y solo hay un coche
  activo a la vez.
- **«Arrastra el pin»** — es FALSO: se arrastra el MAPA bajo un pin fijo. Se dice «mueve tu
  aparcamiento».
- Marcas de terceros (cadenas de supermercado, petroleras) en textos o capturas.

## ⛔ Vocabulario: PLAZA es de la comunidad, APARCAMIENTO es tuyo

Los dos conceptos centrales se llamaban igual y se arregló idioma por idioma. Respétalo:

| Concepto | EN | ES | IT | PT | FR | DE | NL | PL | RO |
|---|---|---|---|---|---|---|---|---|---|
| Plaza libre de la comunidad | spot | plaza | posto | lugar | place | Platz | plek | miejsce | loc |
| Tu sesión: dónde dejaste el coche | parking | aparcamiento | parcheggio | estacionamento | stationnement | Parkplatz | parkeerplaats | parkowanie | parcare |
| Instalación de un tercero donde entras a aparcar | **car park** | parking | parcheggio pubblico | parque de estacionamento | parking | Parkhaus | parkeergarage | parking | parcare publică |

- En inglés el parking público **no es «parking»** (esa palabra ya es TU sesión): es **car park**.
- En alemán ya se coló una vez `Parkplatz` donde tocaba `Platz`. Verifica ese idioma con cuidado.
- Verbo de lo tuyo: *«Marcar aparcamiento»*. Verbo de lo comunitario: *«Avisar de una plaza»*.
  Nunca «marcar mi plaza».
- Al irte, **tu aparcamiento libera una plaza**: son dos cosas y en la misma frase se nombran
  distinto.

## Reglas de ejecución

- **Los 9 idiomas, en la misma pasada.** Ninguno se queda a medias.
- **⛔ No uses «Traducir con IA» del Console.** Escribe tú cada idioma respetando la tabla de arriba y
  los topes. La traducción automática rompe el vocabulario y se pasa de caracteres.
- **Mide los caracteres y dímelos** en formato `usado/límite` para cada campo: nombre **30**,
  descripción breve **80**, descripción completa **4000**, novedades **500**.
- **Novedades («Qué hay de nuevo»)**: una frase por superficie, ≤500 caracteres, en los 9 idiomas.
  Que hablen de lo que el usuario nota, no de mecánica interna: **causa + consecuencia**, sin jerga.
- **Sin mecánica interna en el copy** y sin palabras inventadas. Nada de nombres de componentes,
  algoritmos ni siglas nuestras.
- **Apóstrofos normales** (`'`), nunca escapados.
- **Assets (icono, gráfico de funciones, capturas): no los toques.** Las 8 capturas actuales no
  enseñan parkings, gasolineras, widget ni Plus — dime si crees que hay que ampliarlas y con qué
  frames, pero **las genero y las subo yo**.

## Formularios de «Contenido de la app» — revísalos todos

La suscripción nueva mueve varios. Dime en cuáles hay que entrar y qué hay que responder:

- **Seguridad de los datos** — ahora hay cobro: información financiera y un procesador de pagos
  externo. Los parkings y las gasolineras se consultan al vuelo y **no añaden recogida de datos**.
- **Compras integradas** — hay que declararlas y que el rango de precios cuadre.
- **Clasificación de contenido (IARC)** — el cuestionario se rehace cuando aparecen compras.
- **Público objetivo** (no dirigida a menores) · **Anuncios** (no hay) · **Borrado de datos**.
- **Declaración de permisos de ubicación en segundo plano** — la app detecta que has aparcado con la
  app cerrada, y Play exige un vídeo enseñando el aviso previo del onboarding ANTES del diálogo del
  sistema. **Dime si el formulario sigue apuntando al vídeo anterior**: el primer envío se rechazó
  justo por ahí.

## Antes de darlo por cerrado, pregúntame

1. **El texto que describe Plus en la ficha** — hoy la ficha no lo detalla. Enséñame tu propuesta en
   español e inglés **antes** de traducirla a los otros siete.
2. **¿Salimos a producción directa o pasamos antes por pruebas internas/abiertas?** Con suscripción
   nueva puede convenir el rodeo.
3. **¿Está creado y activo el producto de suscripción** en Play (mensual y anual, con prueba
   gratuita), o sigue en borrador?
4. **pt-PT o pt-BR** — hoy está en portugués europeo y nunca lo decidimos.
5. Cualquier campo del Console donde lo que veas **no cuadre** con lo que te he contado aquí: dímelo
   en vez de resolverlo por tu cuenta.
