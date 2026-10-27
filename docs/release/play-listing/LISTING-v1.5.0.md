# Ficha de Play Store — descripción breve y completa · v1.5.0 (vc 16)

> Escrita el 27-10-2026. **Sustituye a `LISTING-v1.4.0.md`**, que queda como histórico igual que
> `LISTING-v1.3.0.md`, `LISTING.md` y `LISTING-i18n.md`. El nombre de la app y el razonamiento de
> cada decisión siguen viviendo en esos ficheros: aquí está sólo lo que se pega en el Console.
>
> ⛔ **Esta es la ficha VIGENTE, y quién lo dice no es este encabezado**: lo dice
> `published-listing.txt`, que el generador y el guard leen para saber de dónde sale `paste/`.
> Mover la ficha es editar ese fichero; mientras no se edite, la vigente es la que él nombra.
>
> Vocabulario: `CLAUDE.md` ⛔ COPY-SPOT-IS-NOT-A-PARKING-001 — **plaza** es de la comunidad,
> **aparcamiento** es tuyo, y la instalación de un tercero es **car park** en inglés (nunca
> "parking", que ahí ya significa TU sesión).

## Qué cambia respecto a la de 1.4.0: la ficha vendía como Plus algo que ya es gratis

La regla de `docs/release/play-listing/README.md` es ⛔ **la ficha describe el build PUBLICADO, no
master**. **1.5.0 (vc 16) está en producción** — verificado el 27-10 contra la Play Developer API,
no deducido: `production` en `completed` con versionCode 16 y notas en los nueve idiomas.

Y esta vez lo que mueve la ficha **no es una función nueva, es una frase que se ha vuelto falsa**.
El bloque de Plus decía:

> *Gratis, tú preguntas y la app responde. Con Plus, la app vigila mientras tú no miras:*
> • Avisos cuando baja el precio en una gasolinera que sigues.

Desde `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001` (en 1.5.0), **el plan gratuito recibe avisos de
gasolinera**: lo que sube o baja a su alrededor, por notificación y con la app cerrada. Así que ese
encabezado cobra por un trabajo que ya hace gratis, y un usuario free que lea la ficha concluye que
no tiene avisos de combustible — teniéndolos. Eso es peor que no nombrar una función nueva: es
**describir mal el producto que la gente ya tiene instalado**.

Tres reescrituras, las mismas en los nueve idiomas:

| Dónde | Antes | Ahora | Por qué |
|---|---|---|---|
| Bloque de gasolineras | *«Todo gratis: **ver** lo que tienes alrededor nunca está detrás de una suscripción»* | *«Todo gratis, **avisos incluidos**: lo que sube o baja a tu alrededor te llega con la app cerrada»* | la frase se había quedado corta: ya no es sólo *ver* lo que es gratis |
| Encabezado de Plus | *«Gratis, tú preguntas y la app responde. Con Plus, la app vigila mientras tú no miras»* | *«Lo que tienes alrededor es gratis. Plus vigila lo que tú eliges, y lo recuerda»* | es el marco público que ya dice `paparcar.com` desde el 14-10, y describe también el reparto de plazas |
| Bullet de Plus | *«Avisos cuando baja el precio en una gasolinera que sigues»* | *«La gasolinera que sigues, vigilada esté donde esté, y 30 días de histórico»* | lo que Plus vende ahí es el **sujeto** y la **memoria**, no el aviso: la gasolinera que tú elegiste, vigilada sin pasar por delante, con su serie de 30 días |

⛔ **El carril free de avisos de PLAZA no se toca, y la diferencia es deliberada.** El aviso free de
gasolinera es una notificación con la app cerrada; el de plaza sigue siendo in-app y sólo con el mapa
delante. Los dos carriles free **no tienen la misma forma**, está asumido en `CLAUDE.md`, y la ficha
no puede insinuar lo contrario: por eso la frase nueva de «avisos incluidos» vive en el bloque de
**gasolineras**, no en el de plazas, y el bullet de Plus de la plaza en tu zona se queda intacto.

⛔ **Lo que sigue fuera**: el aviso in-app de plaza «cerca de ti» fuera de una zona guardada. Igual
que en 1.4.0, y por la misma razón — anunciarlo sería vender lo que la app todavía no hace.

## ⚠️ El tope de 4000, otra vez — y esta vez salió gratis

Medido **antes** de escribir, que es la regla que dejó 1.4.0 (*nunca medir en en-US*): el francés
entraba con **14** caracteres libres y el alemán con **9**. Añadir una frase era imposible.

La salida no fue recortar ninguna promesa: el encabezado nuevo de Plus es **más corto** que el que
sustituye en los nueve idiomas —decir «lo de alrededor es gratis, lo que eliges es Plus» cuesta
menos que explicar quién trabaja— y eso paga la ampliación de la línea de gratis. Resultado en los
dos idiomas que mandan: **fr-FR queda con 9 libres** (tenía 14) y **de-DE con 18** (tenía 9), o sea
que el alemán respira más que antes.

---

## Nombre de la aplicación (30)

<!-- paste-source: app-name -->

Consolidados aquí el 27-09 desde `LISTING.md` (en-US, es-ES) y `LISTING-i18n.md` (los otros 7):
estaban en dos ficheros y `paste/` se armaba a mano leyendo los dos, que es la vía por la que el
copy se quedó dos generaciones atrás. El razonamiento de cada nombre sigue viviendo allí; lo que se
pega, aquí. [PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001]

| Locale | Texto | Largo |
|---|---|---|
| en-US | `Paparcar: where did you park` | 28/30 |
| es-ES | `Paparcar: dónde aparcaste` | 25/30 |
| it-IT | `Paparcar: dove ho parcheggiato` | 30/30 | ⚠️ **sin margen**. Si el Console lo rechaza: `Paparcar: dov'è la mia auto` (27).
| pt-PT | `Paparcar: onde estacionaste` | 27/30 |
| fr-FR | `Paparcar: où me suis-je garé` | 28/30 |
| de-DE | `Paparcar: wo hast du geparkt` | 28/30 |
| nl-NL | `Paparcar: waar staat je auto` | 28/30 |
| pl-PL | `Paparcar: gdzie zaparkowałeś` | 28/30 |
| ro | `Paparcar: unde ai parcat` | 24/30 |

---

## Descripción breve (80) — RECOMENDADA

<!-- paste-source: short-description -->

> ⛔ **Esta es la variante VIGENTE**, y hasta el 27-09 eso no estaba escrito en ningún sitio:
> el fichero ofrecía dos tablas y `paste/` llevaba la otra. La de abajo se queda como registro
> de lo que se descartó, no como una opción abierta.

| Locale | Texto |
|---|---|
| en-US | `Where did you park? Paparcar remembers — plus fuel prices and car parks.` |
| es-ES | `¿Dónde aparcaste? Paparcar lo recuerda. Con precios de gasolina y parkings.` |
| it-IT | `Dove hai parcheggiato? Paparcar se lo ricorda. Con prezzi e parcheggi.` |
| pt-PT | `Onde estacionaste? O Paparcar lembra-se. Com preços e parques.` |
| fr-FR | `Où vous êtes-vous garé ? Paparcar s'en souvient. Prix et parkings inclus.` |
| de-DE | `Wo hast du geparkt? Paparcar merkt es sich — mit Spritpreisen und Parkhäusern.` |
| nl-NL | `Waar heb je geparkeerd? Paparcar onthoudt het. Met brandstofprijzen.` |
| pl-PL | `Gdzie zaparkowałeś? Paparcar pamięta. Z cenami paliw i parkingami.` |
| ro | `Unde ai parcat? Paparcar ține minte. Cu prețuri și parcări publice.` |

> La variante descartada del 29-08 **ya no se reproduce aquí**: era una segunda tabla en
> esta misma sección y una vez GANÓ, por ser la última que el parser encontraba. Vive en
> `LISTING-v1.3.0.md`, que es su sitio: un histórico.

---

## Descripción completa (4000)

> Las nueve se reescribieron más cortas el 17-09: la primera versión se pasaba del tope en los
> nueve idiomas (el francés por 925 caracteres). Se comprimieron «CÓMO FUNCIONA» a tres líneas y
> las dos vías de detección a un párrafo; no se perdió contenido, se dejó de contar dos veces.

### en-US

```
Where did you park?

Paparcar remembers for you.

Park, get out and get on with your day. Paparcar saves where you left the car automatically: without opening the app, dropping a pin or typing an address. When you come back, you know where it is and how long it has been there.

When you drive off, the spot you leave can be published for the drivers nearby. And you can see the spots others have just left. The more drivers using Paparcar in your city, the more spots appear on the map.

HOW IT WORKS
1. You park. Paparcar detects it and saves the place. With no Bluetooth, you mark the first parking by hand.
2. You drive off. The app detects it.
3. Your spot is published for the drivers nearby, when appropriate.

YOUR PARKING, EFFORTLESS
• Where you left the car, and how long it has been sitting there.
• A home screen widget: where the car is, directions, and a tap to answer mid-trip.
• Your history, and how many spots you have shared.
• Not the right place? Move your parking on the map and save it again.
• Every car with its own size, colour and Bluetooth.

PARKING SPOTS IN REAL TIME
• The spots other drivers have just left, on the map.
• When each one was freed: two minutes ago is not twenty.
• How many drivers are already heading there.
• Whether your car fits: Paparcar compares the space against yours.
• See a free spot as you walk past? Report it in two taps.

FUEL AND CAR PARKS, ON THE SAME MAP
• Fuel stations with the price official registers publish, and the hour it was measured. Spain, France, Germany and Italy.
• The price of the fuel your car actually takes, not a generic average.
• Public car parks with their rate, height limit and spaces, where the source gives them.
• Does your car clear the barrier? Only when we are sure — an unknown height is never a yes.
• All of it free, alerts included: prices moving around you reach you with the app closed.

YOUR NUMBERS
• What you spend on fuel, and how much you burn.
• How long you spend looking for a spot.
• Your usual street, your usual day, and the hour it gets hardest.
• How often detection got it right, in percent, over the trips it measured.

ONE CAR, SEVERAL DRIVERS
• Share a car with up to four people. Whoever drives it marks the parking, and the whole group sees where it is.
• Distance, time and spots freed count for everyone.
• Your routes are shared only if you say so.

AUTOMATIC DETECTION
If your car connects to your phone by Bluetooth, the disconnection helps detect that you have finished parking. If it has no Bluetooth, Paparcar uses motion and location instead.

Paparcar does not want to guess. When it is not sure what happened, it asks you before publishing a spot.

PAPARCAR PLUS
What is around you is free. Plus watches what you choose, and remembers it:
• Alerts when a spot frees up in your area, with the app closed.
• The station you follow, watched wherever it is, with 30 days of history.
• Your whole history. Free keeps the last 7 days.
• More than one car of your own, and more than two private zones.
• Creating a shared car. Joining one is always free.

PRIVACY
• Private zones: mark places like your home and Paparcar will not publish a spot there when you leave.
• Your license plate never leaves your phone.
• Your car's brand and model only appear on the spot you free if you turn that on.
• Your location is never sold or used for advertising.
• You can delete your account and its data from Settings or the web.
• No ads.

LOCATION IN THE BACKGROUND

Paparcar uses location data even when the app is closed or not in use, in order to detect where you park and to free your spot when you drive away.

Automatic detection needs this permission. Without it, Paparcar still works, but you will have to mark and free your parkings manually.

Privacy policy:
https://paparcar.com/privacy-policy
```

### es-ES

```
¿Dónde aparcaste?

Paparcar lo recuerda por ti.

Aparca, bájate del coche y sigue con tu día. Paparcar guarda dónde has dejado el coche automáticamente: sin abrir la app, sin poner un pin y sin escribir una dirección. Cuando vuelvas, sabrás dónde está y cuánto tiempo lleva allí.

Cuando te vas, la plaza que dejas puede publicarse para los conductores que están cerca. Y tú puedes ver las plazas que otros acaban de liberar. Cuantos más conductores usen Paparcar en tu ciudad, más plazas aparecen en el mapa.

CÓMO FUNCIONA
1. Aparcas. Paparcar lo detecta y guarda el sitio. Sin Bluetooth, el primer aparcamiento lo marcas tú.
2. Te vas. La app lo detecta.
3. Tu plaza se publica para los conductores cercanos, cuando corresponde.

TU APARCAMIENTO, SIN ESFUERZO
• Dónde dejaste el coche y cuánto tiempo lleva allí.
• Un widget en la pantalla de inicio: dónde está el coche, cómo ir y contestar en ruta.
• Tu historial y cuántas plazas has cedido.
• ¿El sitio no es exacto? Mueve tu aparcamiento en el mapa y guárdalo de nuevo.
• Cada coche con su tamaño, su color y su Bluetooth.

PLAZAS EN TIEMPO REAL
• Las plazas que otros conductores acaban de liberar, en el mapa.
• Cuándo se liberó cada una: no es lo mismo hace dos minutos que hace veinte.
• Cuántos conductores van ya hacia allí.
• Si tu coche cabe: Paparcar compara el hueco con el tuyo.
• ¿Ves una plaza libre al pasar? Avísala en dos toques.

GASOLINERAS Y PARKINGS, EN EL MISMO MAPA
• Gasolineras con el precio que publican los registros oficiales y la hora a la que se midió. España, Francia, Alemania e Italia.
• El precio del combustible que usa tu coche, no una media genérica.
• Parkings públicos con su tarifa, su gálibo y sus plazas, cuando la fuente los da.
• ¿Cabes por el gálibo? Sólo cuando estamos seguros: un alto desconocido nunca es un «cabes».
• Todo gratis, avisos incluidos: lo que sube o baja a tu alrededor te llega con la app cerrada.

TUS CIFRAS
• Cuánto te gastas en combustible y cuánto consumes.
• Cuánto tiempo pasas buscando plaza.
• Tu calle de siempre, tu día de siempre y la hora en que se pone más difícil.
• Cuánto acierta la detección, en porcentaje, sobre los viajes que midió.

UN COCHE, VARIOS CONDUCTORES
• Comparte un coche hasta con cuatro personas. Quien lo conduce marca el aparcamiento y todo el grupo ve dónde está.
• Distancia, tiempo y plazas liberadas cuentan para todos.
• Tus rutas se comparten sólo si tú lo dices.

DETECCIÓN AUTOMÁTICA
Si tu coche se conecta al móvil por Bluetooth, la desconexión ayuda a detectar que has terminado de aparcar. Si no tiene Bluetooth, Paparcar usa el movimiento y la ubicación.

Paparcar no quiere adivinar. Cuando no está seguro de lo que ha pasado, te pregunta antes de publicar una plaza.

PAPARCAR PLUS
Lo que tienes alrededor es gratis. Plus vigila lo que tú eliges, y lo recuerda:
• Avisos cuando se libera una plaza en tu zona, con la app cerrada.
• La gasolinera que sigues, vigilada esté donde esté, y 30 días de histórico.
• Todo tu historial. El plan gratuito guarda los últimos 7 días.
• Más de un coche propio y más de dos zonas privadas.
• Crear un coche compartido. Unirte a uno es siempre gratis.

PRIVACIDAD
• Zonas privadas: marca sitios como tu casa y Paparcar no publicará una plaza allí cuando te vayas.
• Tu matrícula nunca sale de tu móvil.
• La marca y el modelo de tu coche sólo aparecen en la plaza que liberas si tú lo activas.
• Tu ubicación nunca se vende ni se usa para publicidad.
• Puedes borrar tu cuenta y sus datos desde Ajustes o desde la web.
• Sin anuncios.

UBICACIÓN EN SEGUNDO PLANO

Paparcar usa datos de ubicación incluso cuando la app está cerrada o no se está usando, para detectar dónde aparcas y para liberar tu plaza cuando te vas.

La detección automática necesita este permiso. Sin él, Paparcar sigue funcionando, pero tendrás que marcar y liberar tus aparcamientos a mano.

Política de privacidad:
https://paparcar.com/privacy-policy
```

### it-IT

```
Dove hai parcheggiato?

Paparcar se lo ricorda per te.

Parcheggia, scendi e continua la tua giornata. Paparcar salva in automatico dove hai lasciato l'auto: senza aprire l'app, senza segnaposto e senza scrivere un indirizzo. Quando torni, sai dov'è e da quanto tempo è lì.

Quando riparti, il posto che lasci può essere pubblicato per chi guida lì vicino. E tu vedi i posti che altri hanno appena liberato. Più automobilisti usano Paparcar nella tua città, più posti compaiono sulla mappa.

COME FUNZIONA
1. Parcheggi. Paparcar lo rileva e salva il luogo. Senza Bluetooth, il primo parcheggio lo segni tu.
2. Riparti. L'app lo rileva.
3. Il tuo posto viene pubblicato per chi guida lì vicino, quando è il caso.

IL TUO PARCHEGGIO, SENZA SFORZO
• Dove hai lasciato l'auto e da quanto tempo è lì.
• Un widget sulla schermata home: dov'è l'auto, come arrivarci e come rispondere in viaggio.
• Il tuo storico e quanti posti hai ceduto.
• Il punto non è esatto? Sposta il tuo parcheggio sulla mappa e salvalo di nuovo.
• Ogni auto con la sua taglia, il suo colore e il suo Bluetooth.

POSTI AUTO IN TEMPO REALE
• I posti che altri hanno appena liberato, sulla mappa.
• Quando si è liberato ciascuno: due minuti fa non è venti.
• Quanti automobilisti stanno già andando lì.
• Se la tua auto ci sta: Paparcar confronta lo spazio con la tua.
• Vedi un posto libero mentre passi? Segnalalo in due tocchi.

DISTRIBUTORI E PARCHEGGI PUBBLICI, SULLA STESSA MAPPA
• Distributori con il prezzo pubblicato dai registri ufficiali e l'ora del rilevamento. Spagna, Francia, Germania e Italia.
• Il prezzo del carburante che usa davvero la tua auto, non una media generica.
• Parcheggi pubblici con tariffa, altezza massima e posti, quando la fonte li fornisce.
• Passi sotto la barriera? Solo quando ne siamo certi: un'altezza ignota non è mai un sì.
• Tutto gratis, avvisi compresi: quanto sale o scende intorno a te ti arriva ad app chiusa.

I TUOI NUMERI
• Quanto spendi di carburante e quanto ne consumi.
• Quanto tempo passi a cercare posto.
• La tua via di sempre, il tuo giorno di sempre e l'ora in cui è più difficile.
• Quanto ci prende il rilevamento, in percentuale, sui viaggi misurati.

UN'AUTO, PIÙ CONDUCENTI
• Condividi un'auto fino a quattro persone. Chi la guida segna il parcheggio e tutto il gruppo vede dov'è.
• Distanza, tempo e posti liberati contano per tutti.
• I tuoi percorsi si condividono solo se lo dici tu.

RILEVAMENTO AUTOMATICO
Se la tua auto si collega al telefono via Bluetooth, la disconnessione aiuta a rilevare che hai finito di parcheggiare. Se non ha il Bluetooth, Paparcar usa movimento e posizione.

Paparcar non vuole indovinare. Quando non è sicuro di cosa sia successo, te lo chiede prima di pubblicare un posto.

PAPARCAR PLUS
Quello che hai intorno è gratis. Plus sorveglia quello che scegli tu, e lo ricorda:
• Avvisi quando si libera un posto nella tua zona, con l'app chiusa.
• Il distributore che segui, sorvegliato dove sia, e 30 giorni di storico.
• Tutto il tuo storico. Il piano gratuito tiene gli ultimi 7 giorni.
• Più di un'auto tua e più di due zone private.
• Creare un'auto condivisa. Entrare in una è sempre gratis.

PRIVACY
• Zone private: segna luoghi come casa tua e Paparcar non pubblicherà un posto lì quando riparti.
• La tua targa non esce mai dal tuo telefono.
• Marca e modello della tua auto compaiono sul posto che liberi solo se lo attivi tu.
• La tua posizione non viene mai venduta né usata per pubblicità.
• Puoi eliminare il tuo account e i suoi dati dalle Impostazioni o dal web.
• Nessuna pubblicità.

POSIZIONE IN SECONDO PIANO

Paparcar usa i dati di posizione anche quando l'app è chiusa o non in uso, per rilevare dove parcheggi e per liberare il tuo posto quando riparti.

Il rilevamento automatico ha bisogno di questo permesso. Senza, Paparcar funziona lo stesso, ma dovrai segnare e liberare i tuoi parcheggi a mano.

Informativa sulla privacy:
https://paparcar.com/privacy-policy
```

### pt-PT

```
Onde estacionaste?

O Paparcar lembra-se por ti.

Estaciona, sai do carro e segue com o teu dia. O Paparcar guarda onde deixaste o carro automaticamente: sem abrir a aplicação, sem pino e sem escrever uma morada. Quando voltares, sabes onde está e há quanto tempo ali está.

Quando sais, o lugar que deixas pode ser publicado para quem conduz ali perto. E tu vês os lugares que outros acabaram de libertar. Quantos mais condutores usarem o Paparcar na tua cidade, mais lugares aparecem no mapa.

COMO FUNCIONA
1. Estacionas. O Paparcar deteta e guarda o sítio. Sem Bluetooth, marcas tu o primeiro estacionamento.
2. Sais. A aplicação deteta.
3. O teu lugar é publicado para quem conduz ali perto, quando faz sentido.

O TEU ESTACIONAMENTO, SEM ESFORÇO
• Onde deixaste o carro e há quanto tempo ali está.
• Um widget no ecrã inicial: onde está o carro, como ir e responder a caminho.
• O teu histórico e quantos lugares cedeste.
• O sítio não está certo? Move o teu estacionamento no mapa e guarda outra vez.
• Cada carro com o seu tamanho, a sua cor e o seu Bluetooth.

LUGARES EM TEMPO REAL
• Os lugares que outros condutores acabaram de libertar, no mapa.
• Quando cada um ficou livre: há dois minutos não é há vinte.
• Quantos condutores já vão a caminho.
• Se o teu carro cabe: o Paparcar compara o espaço com o teu.
• Vês um lugar livre ao passar? Avisa em dois toques.

BOMBAS E PARQUES, NO MESMO MAPA
• Bombas de combustível com o preço que os registos oficiais publicam e a hora da medição. Espanha, França, Alemanha e Itália.
• O preço do combustível que o teu carro usa, não uma média genérica.
• Parques de estacionamento com tarifa, altura máxima e lugares, quando a fonte os dá.
• Passas na altura? Só quando temos a certeza: uma altura desconhecida nunca é um sim.
• Tudo grátis, avisos incluídos: o que sobe ou desce à tua volta chega-te com a aplicação fechada.

OS TEUS NÚMEROS
• Quanto gastas em combustível e quanto consomes.
• Quanto tempo passas à procura de lugar.
• A tua rua do costume, o teu dia do costume e a hora em que fica mais difícil.
• Quanto acerta a deteção, em percentagem, nas viagens que mediu.

UM CARRO, VÁRIOS CONDUTORES
• Partilha um carro até com quatro pessoas. Quem o conduz marca o estacionamento e todo o grupo vê onde está.
• Distância, tempo e lugares libertados contam para todos.
• Os teus percursos só são partilhados se tu quiseres.

DETEÇÃO AUTOMÁTICA
Se o teu carro se liga ao telemóvel por Bluetooth, a desconexão ajuda a detetar que acabaste de estacionar. Se não tem Bluetooth, o Paparcar usa o movimento e a localização.

O Paparcar não quer adivinhar. Quando não tem a certeza do que aconteceu, pergunta-te antes de publicar um lugar.

PAPARCAR PLUS
O que tens à tua volta é grátis. O Plus vigia o que tu escolhes, e lembra-se:
• Avisos quando fica livre um lugar na tua zona, com a aplicação fechada.
• A bomba que segues, vigiada onde quer que esteja, e 30 dias de histórico.
• Todo o teu histórico. O plano grátis guarda os últimos 7 dias.
• Mais do que um carro teu e mais do que duas zonas privadas.
• Criar um carro partilhado. Entrar num é sempre grátis.

PRIVACIDADE
• Zonas privadas: marca sítios como a tua casa e o Paparcar não publicará um lugar ali quando saíres.
• A tua matrícula nunca sai do teu telemóvel.
• A marca e o modelo do teu carro só aparecem no lugar que libertas se tu o ativares.
• A tua localização nunca é vendida nem usada para publicidade.
• Podes apagar a tua conta e os seus dados nas Definições ou na web.
• Sem anúncios.

LOCALIZAÇÃO EM SEGUNDO PLANO

O Paparcar usa dados de localização mesmo quando a aplicação está fechada ou não está a ser usada, para detetar onde estacionas e para libertar o teu lugar quando sais.

A deteção automática precisa desta permissão. Sem ela, o Paparcar continua a funcionar, mas terás de marcar e libertar os teus estacionamentos à mão.

Política de privacidade:
https://paparcar.com/privacy-policy
```

### fr-FR

```
Où vous êtes-vous garé ?

Paparcar s'en souvient pour vous.

Garez-vous, sortez et continuez votre journée. Paparcar enregistre automatiquement où vous avez laissé la voiture : sans ouvrir l'application, sans épingle ni adresse. À votre retour, vous savez où elle est et depuis quand.

Quand vous repartez, la place que vous libérez peut être publiée pour les conducteurs à proximité. Et vous voyez celles que d'autres viennent de libérer. Plus il y a de conducteurs dans votre ville, plus de places apparaissent.

COMMENT ÇA MARCHE
1. Vous vous garez. Paparcar le détecte et enregistre l'endroit. Sans Bluetooth, marquez le premier à la main.
2. Vous repartez. L'application le détecte.
3. Votre place est publiée pour les conducteurs à proximité.

VOTRE STATIONNEMENT, SANS EFFORT
• Où vous avez laissé la voiture et depuis combien de temps.
• Un widget sur l'écran d'accueil : où est la voiture, l'itinéraire, et répondre en route.
• Votre historique et les places cédées.
• Pas le bon endroit ? Déplacez-le sur la carte et enregistrez.
• Chaque voiture avec sa taille, sa couleur et son Bluetooth.

DES PLACES EN TEMPS RÉEL
• Les places que d'autres viennent de libérer, sur la carte.
• Quand chacune s'est libérée : deux minutes, ce n'est pas vingt.
• Combien de conducteurs s'y dirigent déjà.
• Si votre voiture rentre : Paparcar compare l'espace à la vôtre.
• Une place libre en passant ? Signalez-la en deux fois.

STATIONS-SERVICE ET PARKINGS, SUR LA MÊME CARTE
• Stations-service avec le prix des registres officiels et l'heure du relevé. Espagne, France, Allemagne, Italie.
• Le prix du carburant de votre voiture, pas une moyenne générique.
• Parkings publics avec tarif, hauteur maximale et nombre de places, quand la source les donne.
• Vous passez sous la barrière ? Seulement si c'est sûr : une hauteur inconnue n'est pas un oui.
• Gratuit, alertes comprises : les prix autour de vous, app fermée.

VOS CHIFFRES
• Ce que vous dépensez en carburant et ce que vous consommez.
• Le temps passé à chercher une place.
• Votre rue habituelle, votre jour habituel et l'heure la plus difficile.
• La part des trajets que la détection a trouvés toute seule, en pourcentage.

UNE VOITURE, PLUSIEURS CONDUCTEURS
• Partagez une voiture jusqu'à quatre : qui la conduit marque, tout le groupe voit où elle est.
• Distance, temps et places libérées comptent pour tous.
• Vos trajets ne sont partagés que si vous le décidez.

DÉTECTION AUTOMATIQUE
Si votre voiture se connecte en Bluetooth, la déconnexion aide à détecter la fin du stationnement. Sans Bluetooth, Paparcar utilise mouvement et localisation.

Paparcar ne veut pas deviner. Quand il n'est pas sûr, il vous demande avant de publier une place.

PAPARCAR PLUS
Ce qui vous entoure est gratuit. Plus surveille ce que vous choisissez, et s'en souvient :
• Alertes quand une place se libère dans votre zone, application fermée.
• La station que vous suivez, où qu'elle soit, et 30 jours d'historique.
• Tout votre historique. Gratuitement, les 7 derniers jours.
• Plus d'une voiture à vous et plus de deux zones privées.
• Créer une voiture partagée. En rejoindre une est toujours gratuit.

CONFIDENTIALITÉ
• Zones privées : marquez votre domicile, Paparcar n'y publie aucune place.
• Votre plaque d'immatriculation ne quitte jamais votre téléphone.
• La marque et le modèle n'apparaissent sur la place libérée que si vous l'activez.
• Votre localisation n'est jamais vendue ni utilisée à des fins publicitaires.
• Compte et données supprimables depuis les Paramètres ou le web.
• Sans publicité.

LOCALISATION EN ARRIÈRE-PLAN

Paparcar utilise des données de localisation même lorsque l'application est fermée ou non utilisée, afin de détecter où vous vous garez et de libérer votre place quand vous repartez.

La détection automatique a besoin de cette autorisation. Sans elle, Paparcar fonctionne, mais vous devrez marquer et libérer vos stationnements à la main.

Politique de confidentialité :
https://paparcar.com/privacy-policy
```

### de-DE

```
Wo hast du geparkt?

Paparcar merkt es sich für dich.

Parke, steig aus und mach mit deinem Tag weiter. Paparcar speichert automatisch, wo du das Auto abgestellt hast: ohne App, ohne Stecknadel und ohne Adresse. Wenn du zurückkommst, weißt du, wo es steht und wie lange schon.

Wenn du losfährst, kann der Platz, den du frei machst, für die Fahrer in der Nähe veröffentlicht werden. Und du siehst die Plätze, die andere gerade frei gemacht haben. Je mehr Fahrer Paparcar in deiner Stadt nutzen, desto mehr Plätze erscheinen.

SO FUNKTIONIERT ES
1. Du parkst. Paparcar erkennt es und speichert den Ort. Ohne Bluetooth markierst du den ersten von Hand.
2. Du fährst los. Die App erkennt es.
3. Dein Platz wird für die Fahrer in der Nähe veröffentlicht, wenn es passt.

DEIN PARKPLATZ, OHNE AUFWAND
• Wo du das Auto abgestellt hast und wie lange es schon dort steht.
• Ein Widget auf dem Homescreen: wo das Auto steht, die Route, und unterwegs antworten.
• Dein Verlauf und wie viele Plätze du weitergegeben hast.
• Der Ort stimmt nicht? Verschiebe ihn auf der Karte und speichere erneut.
• Jedes Auto mit seiner Größe, seiner Farbe und seinem Bluetooth.

FREIE PLÄTZE IN ECHTZEIT
• Die Plätze, die andere gerade frei gemacht haben, auf der Karte.
• Wann jeder frei wurde: vor zwei Minuten ist nicht vor zwanzig.
• Wie viele Fahrer schon unterwegs dorthin sind.
• Ob dein Auto hineinpasst: Paparcar vergleicht die Lücke mit deinem.
• Im Vorbeigehen einen freien Platz gesehen? Melde ihn mit zwei Tipps.

TANKSTELLEN UND PARKHÄUSER, AUF DERSELBEN KARTE
• Tankstellen mit dem Preis aus amtlichen Registern und der Uhrzeit. Spanien, Frankreich, Deutschland, Italien.
• Der Preis des Kraftstoffs, den dein Auto tankt, kein Durchschnitt.
• Parkhäuser mit Tarif, Durchfahrtshöhe und Stellplätzen, wenn die Quelle sie liefert.
• Passt dein Auto unter die Höhe? Nur wenn wir sicher sind — unbekannt ist nie ein Ja.
• Kostenlos, Hinweise inklusive: Preise um dich, App geschlossen.

DEINE ZAHLEN
• Was du für Kraftstoff ausgibst und wie viel du verbrauchst.
• Wie lange du nach einem Platz suchst.
• Deine übliche Straße, dein üblicher Tag und die schwierigste Stunde.
• Wie oft die Erkennung richtig lag, in Prozent, über die gemessenen Fahrten.

EIN AUTO, MEHRERE FAHRER
• Teile ein Auto mit bis zu vier Personen: wer fährt, markiert, die Gruppe sieht, wo es steht.
• Strecke, Zeit und frei gewordene Plätze zählen für alle.
• Deine Routen werden nur geteilt, wenn du es sagst.

AUTOMATISCHE ERKENNUNG
Verbindet sich dein Auto per Bluetooth, hilft das Trennen zu erkennen, dass du fertig eingeparkt hast. Ohne Bluetooth nutzt Paparcar Bewegung und Standort.

Paparcar will nicht raten. Wenn nicht sicher ist, was passiert ist, fragt es dich, bevor ein Platz veröffentlicht wird.

PAPARCAR PLUS
Was dich umgibt, ist kostenlos. Plus bewacht, was du wählst, und merkt es sich:
• Hinweise, wenn in deiner Zone ein Platz frei wird — App geschlossen.
• Deine Tankstelle, wo sie auch steht, und 30 Tage Verlauf.
• Dein ganzer Verlauf. Kostenlos die letzten 7 Tage.
• Mehr als ein eigenes Auto und mehr als zwei private Zonen.
• Ein geteiltes Auto anlegen. Einem beizutreten ist immer kostenlos.

DATENSCHUTZ
• Private Zonen: markiere dein Zuhause, dort wird kein Platz veröffentlicht.
• Dein Kennzeichen verlässt niemals dein Handy.
• Marke und Modell erscheinen nur auf dem Platz, den du frei machst, wenn du das einschaltest.
• Dein Standort wird niemals verkauft oder für Werbung genutzt.
• Konto und Daten löschst du in den Einstellungen oder im Web.
• Keine Werbung.

STANDORT IM HINTERGRUND

Paparcar nutzt Standortdaten auch dann, wenn die App geschlossen ist oder nicht verwendet wird, um zu erkennen, wo du parkst, und um deinen Platz frei zu geben, wenn du losfährst.

Die automatische Erkennung braucht diese Berechtigung. Ohne sie funktioniert Paparcar weiter, aber du musst deine Parkvorgänge von Hand markieren und frei geben.

Datenschutzerklärung:
https://paparcar.com/privacy-policy
```

### nl-NL

```
Waar heb je geparkeerd?

Paparcar onthoudt het voor je.

Parkeer, stap uit en ga verder met je dag. Paparcar slaat automatisch op waar je de auto hebt achtergelaten: zonder de app te openen, zonder speld en zonder adres. Als je terugkomt, weet je waar hij staat en hoe lang al.

Als je wegrijdt, kan de plek die je achterlaat worden gepubliceerd voor de bestuurders in de buurt. En jij ziet de plekken die anderen net hebben vrijgemaakt. Hoe meer bestuurders Paparcar in jouw stad gebruiken, hoe meer plekken verschijnen.

ZO WERKT HET
1. Je parkeert. Paparcar merkt het en slaat de plek op. Zonder Bluetooth markeer je de eerste met de hand.
2. Je rijdt weg. De app merkt het.
3. Jouw plek wordt gepubliceerd voor de bestuurders in de buurt, waar het past.

JOUW PARKEERPLAATS, ZONDER MOEITE
• Waar je de auto hebt achtergelaten en hoe lang hij er al staat.
• Een widget op je startscherm: waar de auto staat, de route, en onderweg antwoorden.
• Je geschiedenis en hoeveel plekken je doorgaf.
• Klopt de plek niet? Verplaats je parkeerplaats op de kaart en sla opnieuw op.
• Elke auto met zijn eigen maat, kleur en Bluetooth.

VRIJE PLEKKEN IN REALTIME
• De plekken die anderen net hebben vrijgemaakt, op de kaart.
• Wanneer elke plek vrijkwam: twee minuten geleden is niet twintig.
• Hoeveel bestuurders er al heen rijden.
• Of jouw auto past: Paparcar vergelijkt de ruimte met die van jou.
• Zie je in het voorbijgaan een vrije plek? Meld hem in twee tikken.

TANKSTATIONS EN PARKEERGARAGES, OP DEZELFDE KAART
• Tankstations met de prijs uit officiële registers en het tijdstip van de meting. Spanje, Frankrijk, Duitsland en Italië.
• De prijs van de brandstof die jouw auto tankt, geen algemeen gemiddelde.
• Parkeergarages met tarief, maximale hoogte en plaatsen, waar de bron die geeft.
• Kom je onder de hoogte door? Alleen als we het zeker weten — onbekend is nooit een ja.
• Allemaal gratis, meldingen ook: prijzen om je heen bereiken je met de app dicht.

JOUW CIJFERS
• Wat je aan brandstof uitgeeft en hoeveel je verbruikt.
• Hoe lang je zoekt naar een plek.
• Je vaste straat, je vaste dag en het lastigste uur.
• Hoe vaak de detectie het goed had, in procent, over de gemeten ritten.

ÉÉN AUTO, MEERDERE BESTUURDERS
• Deel een auto met maximaal vier mensen. Wie erin rijdt, markeert de parkeerplaats, en de hele groep ziet waar hij staat.
• Afstand, tijd en vrijgemaakte plekken tellen voor iedereen.
• Je routes worden alleen gedeeld als jij dat zegt.

AUTOMATISCHE DETECTIE
Maakt je auto via Bluetooth verbinding met je telefoon, dan helpt het verbreken te merken dat je klaar bent met parkeren. Zonder Bluetooth gebruikt Paparcar beweging en locatie.

Paparcar wil niet gokken. Als het niet zeker weet wat er is gebeurd, vraagt het het je voordat er een plek wordt gepubliceerd.

PAPARCAR PLUS
Wat om je heen is, is gratis. Plus waakt over wat jij kiest, en onthoudt het:
• Meldingen als er een plek vrijkomt in jouw zone, met de app dicht.
• Het station dat je volgt, waar het ook staat, en 30 dagen geschiedenis.
• Je hele geschiedenis. Gratis blijven de laatste 7 dagen.
• Meer dan één eigen auto en meer dan twee privézones.
• Een gedeelde auto aanmaken. Meedoen is altijd gratis.

PRIVACY
• Privézones: markeer plekken zoals je huis, dan publiceert Paparcar daar geen plek.
• Je kenteken verlaat nooit je telefoon.
• Merk en model verschijnen alleen op de plek die je vrijmaakt als je dat aanzet.
• Je locatie wordt nooit verkocht of voor advertenties gebruikt.
• Je kunt je account en de gegevens verwijderen via Instellingen of via het web.
• Geen advertenties.

LOCATIE OP DE ACHTERGROND

Paparcar gebruikt locatiegegevens ook wanneer de app gesloten is of niet wordt gebruikt, om te detecteren waar je parkeert en om je plek vrij te geven als je wegrijdt.

Automatische detectie heeft deze toestemming nodig. Zonder werkt Paparcar gewoon, maar moet je je parkeerbeurten met de hand markeren en vrijgeven.

Privacybeleid:
https://paparcar.com/privacy-policy
```

### pl-PL

```
Gdzie zaparkowałeś?

Paparcar pamięta za Ciebie.

Zaparkuj, wysiądź i zajmij się swoimi sprawami. Paparcar automatycznie zapisuje, gdzie zostawiłeś auto: bez otwierania aplikacji, bez pinezki i bez wpisywania adresu. Gdy wrócisz, wiesz, gdzie stoi i jak długo.

Gdy odjeżdżasz, zwolnione miejsce może zostać opublikowane dla kierowców w pobliżu. A Ty widzisz miejsca, które inni właśnie zwolnili. Im więcej kierowców korzysta z Paparcar w Twoim mieście, tym więcej miejsc się pojawia.

JAK TO DZIAŁA
1. Parkujesz. Paparcar to wykrywa i zapisuje miejsce. Bez Bluetootha pierwsze parkowanie oznaczasz ręcznie.
2. Odjeżdżasz. Aplikacja to wykrywa.
3. Twoje miejsce trafia do kierowców w pobliżu, gdy ma to sens.

TWOJE PARKOWANIE, BEZ WYSIŁKU
• Gdzie zostawiłeś auto i jak długo już tam stoi.
• Widżet na ekranie głównym: gdzie stoi auto, jak dojechać i jak odpowiedzieć w drodze.
• Twoja historia i przekazane miejsca.
• Miejsce nie jest dokładne? Przesuń swoje parkowanie na mapie i zapisz ponownie.
• Każde auto z własnym rozmiarem, kolorem i Bluetooth.

WOLNE MIEJSCA W CZASIE RZECZYWISTYM
• Miejsca, które inni właśnie zwolnili, na mapie.
• Kiedy każde się zwolniło: dwie minuty temu to nie dwadzieścia.
• Ilu kierowców już tam jedzie.
• Czy Twoje auto się zmieści: Paparcar porównuje lukę z Twoim.
• Widzisz wolne miejsce, przechodząc obok? Zgłoś je dwoma dotknięciami.

STACJE PALIW I PARKINGI, NA TEJ SAMEJ MAPIE
• Stacje paliw z ceną z oficjalnych rejestrów i godziną pomiaru. Hiszpania, Francja, Niemcy i Włochy.
• Cena paliwa, którego używa Twoje auto, a nie ogólna średnia.
• Parkingi publiczne z opłatą, maksymalną wysokością i liczbą miejsc, gdy źródło je podaje.
• Zmieścisz się pod wysokością? Tylko gdy jesteśmy pewni — nieznana nigdy nie znaczy tak.
• Wszystko za darmo, także powiadomienia: ceny wokół Ciebie docierają przy zamkniętej aplikacji.

TWOJE LICZBY
• Ile wydajesz na paliwo i ile go zużywasz.
• Ile czasu spędzasz na szukaniu miejsca.
• Twoja zwykła ulica, Twój zwykły dzień i najtrudniejsza godzina.
• Ile razy wykrywanie trafiło, w procentach, na zmierzonych przejazdach.

JEDNO AUTO, KILKU KIEROWCÓW
• Udostępnij auto nawet czterem osobom. Kto nim jedzie, oznacza parkowanie, a cała grupa widzi, gdzie stoi.
• Dystans, czas i zwolnione miejsca liczą się dla wszystkich.
• Twoje trasy są udostępniane tylko wtedy, gdy tak zdecydujesz.

AUTOMATYCZNE WYKRYWANIE
Jeśli Twoje auto łączy się z telefonem przez Bluetooth, rozłączenie pomaga wykryć koniec parkowania. Bez Bluetooth Paparcar używa ruchu i lokalizacji.

Paparcar nie chce zgadywać. Gdy nie ma pewności, pyta Cię, zanim opublikuje miejsce.

PAPARCAR PLUS
To, co masz wokół siebie, jest darmowe. Plus pilnuje tego, co wybierzesz, i to pamięta:
• Powiadomienia, gdy w Twojej strefie zwalnia się miejsce, przy zamkniętej aplikacji.
• Stacja, którą obserwujesz, gdziekolwiek jest, i 30 dni historii.
• Cała Twoja historia. Plan darmowy zachowuje ostatnie 7 dni.
• Więcej niż jedno własne auto i więcej niż dwie strefy prywatne.
• Utworzenie współdzielonego auta. Dołączenie jest zawsze darmowe.

PRYWATNOŚĆ
• Strefy prywatne: oznacz miejsca takie jak dom, a Paparcar nie opublikuje tam miejsca.
• Twoja tablica rejestracyjna nigdy nie opuszcza telefonu.
• Marka i model pojawiają się przy zwalnianym miejscu tylko wtedy, gdy to włączysz.
• Twoja lokalizacja nigdy nie jest sprzedawana ani używana do reklam.
• Możesz usunąć konto i jego dane w Ustawieniach lub przez stronę.
• Bez reklam.

LOKALIZACJA W TLE

Paparcar używa danych o lokalizacji także wtedy, gdy aplikacja jest zamknięta lub nieużywana, aby wykryć, gdzie parkujesz, i zwolnić Twoje miejsce, gdy odjeżdżasz.

Automatyczne wykrywanie potrzebuje tego uprawnienia. Bez niego Paparcar nadal działa, ale parkowania trzeba oznaczać i zwalniać ręcznie.

Polityka prywatności:
https://paparcar.com/privacy-policy
```

### ro

```
Unde ai parcat?

Paparcar ține minte pentru tine.

Parchează, coboară și vezi-ți de ziua ta. Paparcar salvează automat unde ai lăsat mașina: fără să deschizi aplicația, fără indicator și fără să scrii o adresă. Când te întorci, știi unde este și de cât timp.

Când pleci, locul pe care îl eliberezi poate fi publicat pentru șoferii din apropiere. Iar tu vezi locurile pe care alții tocmai le-au eliberat. Cu cât mai mulți șoferi folosesc Paparcar în orașul tău, cu atât mai multe locuri apar.

CUM FUNCȚIONEAZĂ
1. Parchezi. Paparcar detectează și salvează locul. Fără Bluetooth, prima parcare o marchezi tu.
2. Pleci. Aplicația detectează.
3. Locul tău este publicat pentru șoferii din apropiere, când este cazul.

PARCAREA TA, FĂRĂ EFORT
• Unde ai lăsat mașina și de cât timp stă acolo.
• Un widget pe ecranul principal: unde e mașina, traseul și un răspuns din mers.
• Istoricul tău și câte locuri ai cedat.
• Locul nu e exact? Mută-ți parcarea pe hartă și salveaz-o din nou.
• Fiecare mașină cu dimensiunea, culoarea și Bluetooth-ul ei.

LOCURI LIBERE ÎN TIMP REAL
• Locurile pe care alții tocmai le-au eliberat, pe hartă.
• Când s-a eliberat fiecare: acum două minute nu e acum douăzeci.
• Câți șoferi se îndreaptă deja într-acolo.
• Dacă mașina ta încape: Paparcar compară spațiul cu a ta.
• Vezi un loc liber când treci pe lângă? Raportează-l din două atingeri.

BENZINĂRII ȘI PARCĂRI PUBLICE, PE ACEEAȘI HARTĂ
• Benzinării cu prețul din registrele oficiale și ora măsurării. Spania, Franța, Germania și Italia.
• Prețul combustibilului pe care îl folosește mașina ta, nu o medie generică.
• Parcări publice cu tarif, înălțime maximă și locuri, când sursa le oferă.
• Încapi la înălțime? Doar când suntem siguri — o înălțime necunoscută nu e niciodată un da.
• Totul gratuit, alertele incluse: prețurile din jurul tău ajung cu aplicația închisă.

CIFRELE TALE
• Cât cheltui pe combustibil și cât consumi.
• Cât timp petreci căutând un loc.
• Strada ta obișnuită, ziua ta obișnuită și ora cea mai grea.
• Cât de des a nimerit detectarea, în procente, pe traseele măsurate.

O MAȘINĂ, MAI MULȚI ȘOFERI
• Împarte o mașină cu până la patru persoane. Cine o conduce marchează parcarea, iar tot grupul vede unde este.
• Distanța, timpul și locurile eliberate contează pentru toți.
• Traseele tale se partajează doar dacă spui tu.

DETECTARE AUTOMATĂ
Dacă mașina ta se conectează la telefon prin Bluetooth, deconectarea ajută la detectarea sfârșitului parcării. Fără Bluetooth, Paparcar folosește mișcarea și locația.

Paparcar nu vrea să ghicească. Când nu este sigur, te întreabă înainte de a publica un loc.

PAPARCAR PLUS
Ce ai în jur e gratuit. Plus veghează ce alegi tu și ține minte:
• Alerte când se eliberează un loc în zona ta, cu aplicația închisă.
• Benzinăria pe care o urmărești, oriunde e, și 30 de zile de istoric.
• Tot istoricul tău. Planul gratuit păstrează ultimele 7 zile.
• Mai mult de o mașină proprie și mai mult de două zone private.
• Crearea unei mașini partajate. Să intri în una este întotdeauna gratuit.

CONFIDENȚIALITATE
• Zone private: marchează locuri precum casa ta, iar Paparcar nu va publica un loc acolo.
• Numărul tău de înmatriculare nu îți părăsește niciodată telefonul.
• Marca și modelul apar pe locul pe care îl eliberezi doar dacă activezi asta.
• Locația ta nu este niciodată vândută sau folosită pentru publicitate.
• Îți poți șterge contul și datele din Setări sau de pe web.
• Fără reclame.

LOCAȚIA ÎN FUNDAL

Paparcar folosește date de locație chiar și atunci când aplicația este închisă sau nu este folosită, pentru a detecta unde parchezi și pentru a elibera locul tău când pleci.

Detectarea automată are nevoie de această permisiune. Fără ea, Paparcar funcționează în continuare, dar va trebui să marchezi și să eliberezi parcările manual.

Politica de confidențialitate:
https://paparcar.com/privacy-policy
```

---

## Margen de cada idioma (medido al escribir esta ficha)

| Locale | 1.3.0 | 1.4.0 | 1.5.0 | Libre de 4000 |
|---|---|---|---|---|
| en-US | 3580 | 3798 | 3820 | **180** |
| es-ES | 3690 | 3904 | 3930 | **70** |
| it-IT | 3712 | 3927 | 3952 | **48** |
| pt-PT | 3722 | 3905 | 3930 | **70** |
| fr-FR | 3989 | 3986 | 3991 | **9** |
| de-DE | 3956 | 3991 | 3982 | **18** |
| nl-NL | 3771 | 3972 | 3982 | **18** |
| pl-PL | 3628 | 3833 | 3851 | **149** |
| ro | 3673 | 3879 | 3861 | **139** |

> Medido con el mismo parser que lee los pegables (`play_locales.paste_sections`), no a mano.
> El techo sigue siendo **fr-FR**, y nl-NL se le acerca: dos idiomas con 9 y 18 libres significan
> que la próxima función que se quiera nombrar **vuelve a exigir condensar antes de escribir**.
