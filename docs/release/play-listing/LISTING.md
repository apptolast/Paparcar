# Ficha de Play Store — textos

> Actualizado el 2026-08-29 con el copy de `PLAN-OPTIMIZACION.md` (aprobado por el user).
> Fuente de verdad del vocabulario: `CLAUDE.md` (⛔ COPY-SPOT-IS-NOT-A-PARKING-001) y
> `composeResources/values/strings.xml`.
>
> **Todas las promesas están verificadas contra el código** — la tabla de verificación,
> una fila por afirmación, está en `PLAN-OPTIMIZACION.md` § 1. En esa auditoría cayeron
> dos frases del copy anterior que NO eran ciertas:
> - ❌ «apaga la detección del coche que quieras» — no existe interruptor por vehículo:
>   hay un ajuste **global** (`settings_auto_detect`) y el invariante de **un solo coche
>   activo** (`VehicleActiveStatePolicy`). **No volver a escribirlo.**
> - ❌ «arrastra el pin» — el gesto real es arrastrar el MAPA bajo un pin fijo
>   (`home_add_parking_helper_primary_edit`). Se dice «mueve tu aparcamiento».
>
> **Qué se PROMETE y qué se DESCRIBE.** La ficha promete una sola cosa: **que la app sabe
> dónde aparcaste**. Se cumple con un usuario solo, sin comunidad, desde la primera
> instalación. Encontrar plazas — el producto estrella — se **describe** con todas sus
> letras en la completa y en las capturas, pero NO se promete en título, descripción breve
> ni gráfico: depende de que haya masa crítica en esa ciudad, y prometerla el día 1 compra
> reseñas de una estrella por un mapa vacío, que en el ranking de Play pesan más que
> cualquier palabra clave.
>
> ⚠️ **Esto se revisa cuando una ciudad tenga comunidad viva.** Entonces la promesa de
> encontrar plazas sube al título y a la breve.
>
> **Coherencia obligatoria**: la sección "Ubicación en segundo plano" tiene que decir lo
> mismo que `https://paparcar.com/privacy-policy` y que `docs/legal/DATA-SAFETY-FORM.md`.
> Si cambia lo que la app recoge, se actualizan los tres en la MISMA tarea. Auditoría de
> coherencia: `PLAN-OPTIMIZACION.md` § 19 (sin contradicciones a 29-08).

---

## 1 · en-US (ficha predeterminada)

### Nombre de la aplicación (30)
```
Paparcar: where did you park
```
`28/30` — el título es el campo con MÁS peso en la búsqueda de Play y se localiza por
idioma: en la ficha en-US va en inglés. Es el paralelo exacto del ES (`dónde aparcaste`) y
enlaza con la primera línea de la breve y de la completa. Sustituye a
`Paparcar: live parking spots`, que prometía plazas en el campo de mayor peso y
contradecía la estrategia de arriba.

### Descripción breve (80)
```
Where did you park? Paparcar remembers — and your spot passes on.
```
`65/80` — la pregunta convierte mejor que la afirmación: el usuario se reconoce en el
problema. El remate mete la cara comunitaria **desde lo que el usuario DA**, no desde lo
que recibe: eso depende sólo de él, así que no es una promesa de disponibilidad.

### Descripción completa (4000)
```
Where did you park?

Paparcar remembers for you.

Park, get out and get on with your day. Paparcar saves where you left the car automatically: without opening the app, dropping a pin or typing an address. When you come back, you know where it is and how long it has been there.

And Paparcar does one more thing. When you drive off, the spot you leave can be published on the map for the drivers nearby. And you can see the spots other drivers have just left. The more drivers using Paparcar in your city, the more spots can appear on the map.

HOW IT WORKS

1. PARK
Paparcar detects that you have finished parking and saves the place. You can also mark it by hand with one tap.

2. DRIVE OFF
The app detects that you are driving again.

3. YOUR SPOT IS FREED
When appropriate, your spot is published for the drivers nearby.

YOUR PARKING, EFFORTLESS
• Where you left the car, and how long it has been sitting there.
• All your cars in one garage, each with its own size, colour and Bluetooth.
• Your parking history, with how many the app detected and how many spots you have shared.
• Not quite the right place? Move your parking on the map and save it again.

PARKING SPOTS IN REAL TIME
• See on the map the spots other drivers have just left.
• When each one was freed: a spot from two minutes ago is not the same as one from twenty.
• How many drivers are already heading for that spot.
• Whether your car fits: Paparcar compares the space against the size of yours.
• See a free spot as you walk past? Report it in two taps for the next driver.

AUTOMATIC DETECTION, TWO WAYS

• BLUETOOTH
If your car connects to your phone, the disconnection helps detect that you have finished parking. Precise and efficient.

• ASSISTED DETECTION
If your car has no Bluetooth, Paparcar uses motion and location to detect when you started driving and where you stopped.

Paparcar does not want to guess. When it is not sure what happened, it asks you before publishing a spot.

PRIVACY
• Private zones: mark places like your home and Paparcar will not publish a spot there when you leave.
• Your license plate never leaves your phone.
• Your car's brand and model are private: they only appear on the spot you free if you turn that on.
• Your location is never sold or used for advertising.
• You can delete your account and its data from Settings or from the web.
• No ads.

LOCATION IN THE BACKGROUND

Paparcar uses location data even when the app is closed or not in use, in order to detect where you park and to free your spot when you drive away.

Automatic detection needs this permission. Without it, Paparcar still works, but you will have to mark and free your parkings manually.

Privacy policy:
https://paparcar.com/privacy-policy
```

---

## 2 · es-ES

### Nombre de la aplicación (30)
```
Paparcar: dónde aparcaste
```
`25/30` — **con tilde**: `dónde` es interrogativo (pregunta indirecta), y la tilde
diacrítica es obligatoria. Sin tilde sería el relativo átono, que necesita un antecedente
(«el lugar donde aparcaste») y aquí no lo hay: estaría mal escrito.

Es la consulta que teclea quien tiene este problema, y enlaza con la primera línea de la
descripción breve y de la completa. Comparte raíz con `aparcar`/`aparcamiento`.
Descartados:
- `tu aparcamiento` — lleva la keyword completa, pero es enunciativo y no engancha con la
  pregunta que abre las otras dos piezas.
- `parking automático` — **inexacto**: describe un coche que aparca solo (Park Assist), y
  el nuestro no aparca; lo automático es el *registro*. Segunda lectura mala: recinto con
  barrera automática. Y `aparcamiento automático`, que sería la forma correcta, **no cabe**
  (33/30).
- `aparcamiento vivo` — nadie busca eso.
- `dónde aparqué` — es la consulta literal de alta intención, pero pierde la palabra
  «aparcamiento» completa.

### Descripción breve (80)
```
¿Dónde aparcaste? Paparcar lo recuerda por ti. Y tu plaza pasa al siguiente.
```
`76/80` — el remate anterior («sin que hagas nada») era redundante: «lo recuerda por ti»
ya lo dice. Este espacio se aprovecha para la cara comunitaria, contada desde lo que el
usuario DA — no promete que vaya a encontrar plazas.

### Descripción completa (4000)
```
¿Dónde aparcaste?

Paparcar lo recuerda por ti.

Aparca, bájate del coche y sigue con tu día. Paparcar guarda dónde has dejado el coche automáticamente: sin abrir la app, sin poner un pin y sin escribir una dirección. Cuando vuelvas, sabrás dónde está y cuánto tiempo lleva allí.

Y Paparcar hace algo más. Cuando te vas, la plaza que dejas puede publicarse en el mapa para los conductores que están cerca. Y tú puedes ver las plazas que otros acaban de liberar. Cuantos más conductores usen Paparcar en tu ciudad, más plazas podrán aparecer en el mapa.

CÓMO FUNCIONA

1. APARCA
Paparcar detecta que has terminado de aparcar y guarda el sitio. También puedes marcarlo a mano con un toque.

2. TE VAS
La app detecta que vuelves a conducir.

3. TU PLAZA SE LIBERA
Cuando corresponde, tu plaza se publica para los conductores que están cerca.

TU APARCAMIENTO, SIN ESFUERZO
• Dónde dejaste el coche y cuánto tiempo lleva allí.
• Todos tus coches en un mismo garaje, cada uno con su tamaño, su color y su Bluetooth.
• Tu historial de aparcamientos, con cuántos detectó la app y cuántas plazas has cedido.
• ¿El sitio no es exacto? Mueve tu aparcamiento en el mapa y guárdalo de nuevo.

PLAZAS DE APARCAMIENTO EN TIEMPO REAL
• Mira en el mapa las plazas que otros conductores acaban de liberar.
• Cuándo se liberó cada una: no es lo mismo una de hace dos minutos que una de hace veinte.
• Cuántos conductores van ya hacia esa plaza.
• Si tu coche cabe: Paparcar compara el hueco con el tamaño del tuyo.
• ¿Ves una plaza libre al pasar? Avísala en dos toques para el siguiente conductor.

DETECCIÓN AUTOMÁTICA, POR DOS VÍAS

• BLUETOOTH
Si tu coche se conecta al móvil, la desconexión ayuda a detectar que has terminado de aparcar. Es un método preciso y eficiente.

• DETECCIÓN ASISTIDA
Si tu coche no tiene Bluetooth, Paparcar usa el movimiento y la ubicación para detectar cuándo has empezado a conducir y dónde te has detenido.

Paparcar no quiere adivinar. Cuando no está seguro de lo que ha ocurrido, te pregunta antes de publicar una plaza.

PRIVACIDAD
• Zonas privadas: marca lugares como tu casa y Paparcar no publicará allí una plaza cuando te vayas.
• Tu matrícula no sale nunca de tu móvil.
• La marca y el modelo de tu coche son privados: sólo aparecen en la plaza que liberas si tú lo activas.
• Tu ubicación no se vende ni se utiliza para publicidad.
• Puedes eliminar tu cuenta y sus datos desde Ajustes o desde la web.
• Sin anuncios.

UBICACIÓN EN SEGUNDO PLANO

Paparcar utiliza datos de ubicación incluso cuando la aplicación está cerrada o no la estás utilizando, para detectar dónde aparcas y liberar tu plaza cuando te vas.

La detección automática necesita este permiso. Sin él, Paparcar sigue funcionando, pero tendrás que marcar y liberar los aparcamientos manualmente.

Política de privacidad:
https://paparcar.com/privacy-policy
```

---

## 3 · Frase de marca (gráfico de funciones)

✅ **Aplicado el 29-08.** `assets/play-feature-graphic-1024x500.png` dice:

```
Park and forget it. Paparcar remembers.
```

Motivo (`PLAN-OPTIMIZACION.md` § 18): con los títulos nuevos, la frase anterior
(«Knows where you parked, so you don't have to remember») repetía casi literalmente la
descripción breve, y en Play se ven juntas. El gráfico dice ahora lo que la breve no puede
— el «cómo». Al ser más corta, el cuerpo subió de 27 px a 31 px: se lee mejor en la
miniatura del listado.

Sólo cambió el texto. La composición (glifo + wordmark + retícula + marcadores de plaza)
se mantiene.

Versión ES, por si algún día hay gráfico localizado:
```
Aparca y olvídate. Paparcar lo recuerda.
```

🟡 **Alternativa disponible sin decidir**: `assets/alt-feature-graphic-no-name-repeat.png`
dice «Park and forget it. **It remembers for you.**». La versión aplicada repite «Paparcar»
justo debajo del wordmark; la alternativa lo evita a cambio de un remate menos rotundo.

---

## 4 · Otros campos de la ficha

| Campo | Valor |
|---|---|
| Categoría | Mapas y navegación (*Maps & Navigation*) |
| Etiquetas | Aparcamiento, Mapas, Navegación, Comunidad |
| Email de contacto | support@paparcar.com |
| Sitio web | https://paparcar.com |
| Política de privacidad | https://paparcar.com/privacy-policy |
| Borrado de cuenta | https://paparcar.com/delete-account |
| Vídeo (YouTube) | *opcional — dejar vacío* |

⚠️ El vídeo que Play te pedirá para declarar `ACCESS_BACKGROUND_LOCATION` y
`FOREGROUND_SERVICE_LOCATION` **no es este campo**: va en la declaración de permisos, y
tiene que enseñar el aviso destacado del onboarding ANTES del diálogo del sistema.
Ver `docs/legal/DATA-SAFETY-FORM.md` § "Declaraciones adicionales".
