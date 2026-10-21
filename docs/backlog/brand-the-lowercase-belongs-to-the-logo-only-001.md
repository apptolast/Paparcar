# BRAND-THE-LOWERCASE-BELONGS-TO-THE-LOGO-ONLY-001 · la minúscula es del dibujo, no de la palabra

**Estado:** ✅ Done

## Qué revierte, y por qué no es un error de nadie

[BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001] llevó la minúscula del imagotipo a **todo** el texto:
839 sitios entre los 9 locales de strings, el espejo de notificaciones, la web, las dos páginas
legales, la ficha de Play y el nombre bajo el icono. Era una decisión del user, tomada con la lista
de superficies delante.

Al verla **renderizada** el user la reabre: *«el imagotipo aparece todo en minúscula, pero no sé si
es conveniente aplicar esto a todo donde ponga Paparcar»*. Y decide: **la minúscula se queda en el
logo, el texto vuelve a «Paparcar»**.

Lo que cambió entre una decisión y otra no es la opinión: es que **ya se podía mirar**.

- **A principio de frase.** La mayoría de las apariciones ABREN oración, y ahí la minúscula no se
  lee como decisión de marca sino como un fallo de mayúsculas. En el onboarding se leían seguidas
  *«Welcome to paparcar»* y *«paparcar looks after your car…»*.
- **En las legales.** La política nombra al servicio como parte; en minúscula parece descuido.
- **Como título de tienda.** «paparcar» en una lista junto a Google Maps o Waze sólo funciona si la
  marca ya es conocida. adidas puede apoyarse en eso; una app sin publicar, no.

⛔ **No se adopta el punto medio** («minúscula salvo a principio de frase»): es una regla que nadie
aplica igual dos veces, y es el mismo argumento que sostuvo implementarla entera.

## Diseño

Barrido inverso, **693 ocurrencias en 33 ficheros**, con una regla que no es un `replace`:

```python
WORD = re.compile(r'(?<![\w@./-])paparcar(?![\w./-])')
```

⛔ **Los lookarounds son el ticket entero.** Hay sitios donde `paparcar` en minúscula es correcto y
sigue siéndolo: **`paparcar.com`** (36), **`support@paparcar.com`**, **`@paparcar.app` / `.mock`**
(5), el paquete **`com.rndeveloper.paparcar`** y los nombres de recurso **`paparcar_imagotipo_*`**
(6). Un `replace` a pelo los rompe todos y la web deja de resolver su propio dominio. Comprobado
después del barrido: `Paparcar.com` → 0, recursos intactos, `PaparcarApp.kt` sin tocar.

### El `aria-label` del logo también sube

Era `aria-label="paparcar"`, igualando lo que el logo DIBUJA. Pero un `aria-label` **es texto**: lo
lee un lector de pantalla, y la app ya decía «Paparcar» ahí porque su `contentDescription` sale de
`brand_name`. Dos ortografías para la misma frase hablada. Sube a «Paparcar» en la web y en el
`alt` del README.

### El guard se BORRA, no se invierte

`BrandNameLowercaseGuardrailTest` vigilaba la decisión que esto revierte. No se le da la vuelta:

- lo que vigilaría —que nadie escriba «paparcar» en un string— **casi nadie lo rompe**: la
  inclinación natural es escribirlo con mayúscula, que es justo lo que ahora queremos;
- y para hacerlo tendría que excluir dominio, correos, paquete y nombres de recurso, o sea
  reimplementar los lookarounds de arriba y cargar con sus falsos positivos para siempre.

Un guard que defiende una decisión retirada es ruido con nombre de test.

## Lo que NO cambia

- **El imagotipo**, que sigue dibujando `paparcar`. Es el dibujo, y es lo que el user quiere.
- **Su maestro, su generador y `BrandSurfaceGuardrailTest`**: intactos.
- **El alineado** [BRAND-THE-IMAGOTIPO-MUST-FILL-ITS-WIDTH-001] y el **tagline**
  [AUTH-THE-TAGLINE-MUST-SPEAK-THE-PRODUCTS-VOCABULARY-001]: ninguno nombra la marca.

## Consecuencia buena, y vale la pena decirla

Esto **quita dos de las tres tareas de tienda** que quedaban:

- El **título en Play Console** ya no hay que cambiarlo: vuelve a coincidir con el repo.
- El titular de las **72 capturas** deja de ser un defecto —las actuales ya dicen «Paparcar»—, así
  que a [PLAY-THE-STORE-SHOTS-STILL-LIVE-IN-DUBLIN-001] le queda **un solo** motivo: que el móvil
  de dentro enseña Dublín.

## Verificación

- 693 sustituciones; `paparcar` en minúscula como palabra en las páginas publicadas: **0**.
- Dominio, correos, paquete y recursos: **intactos y contados**.
- `node web/build.mjs` + `restyle-legal` + tarjeta social + banner de los 9 idiomas, regenerados.
- `./gradlew testAll` + ambos flavors verde.
