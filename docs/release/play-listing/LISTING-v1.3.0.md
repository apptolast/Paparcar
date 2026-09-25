# Ficha de Play Store — descripción breve y completa · v1.3.0 (vc 14)

> Escrito el 17-09-2026. **Sustituye** a los bloques de descripción de `LISTING.md` (en-US, es-ES)
> y `LISTING-i18n.md` (los otros 7). El nombre de la app y el razonamiento de cada decisión siguen
> viviendo en esos dos ficheros: aquí está sólo lo que se pega en el Console.
>
> Vocabulario: `CLAUDE.md` ⛔ COPY-SPOT-IS-NOT-A-PARKING-001 — **plaza** es de la comunidad,
> **aparcamiento** es tuyo, y la instalación de un tercero es **car park** en inglés (nunca
> "parking", que ahí ya significa TU sesión).

## Qué cambia respecto a la ficha del 29-08, y por qué

**⛔ Se corrige una promesa que hoy es FALSA.** La ficha vigente dice *«Todos tus coches en un
mismo garaje»* / *«All your cars in one garage»*. Desde `MONEY-001` el plan gratuito se queda en
**un solo coche propio** (`VehicleEntitlementGate`: *"Free keeps ONE own car; Plus has no cap"*).
Prometer el garaje entero en la descripción y luego topar en uno es justo lo que compra reseñas de
una estrella. Ahora la línea describe lo que un coche trae (tamaño, color, Bluetooth) y el número
se dice donde toca: en el bloque de Plus.

**Entra lo que ha salido desde entonces**, todo verificado contra el código:
- **Gasolineras** con el precio que publican los registros oficiales y la hora a la que se midió.
  ⚠️ Cuatro países y ni uno más: `MineturStationsSource` (ES), `FranceFuelStationsSource`,
  `GermanyFuelStationsSource`, `ItalyFuelStationsSource`. **México aún no está en master** — hay
  docs de hoja de ruta, no código: no se nombra.
- **Parkings públicos** con tarifa, gálibo y plazas cuando la fuente los da.
- **Coche compartido**, tope **cuatro personas** (`SharedCarEntitlementGate.MAX_MEMBERS = 4`, y es
  lo que dice `shared_car_not_shared_body` en la app: las dos cifras tienen que coincidir).
- **Tus cifras**: gasto, combustible, tiempo buscando, calle habitual.
- **Plus** dicho con sus límites reales: historial libre **7 días**
  (`HistoryEntitlementGate.FREE_HISTORY_WINDOW_DAYS = 7`), **1** coche propio, **2** zonas privadas.

**Se mantiene la estrategia de no prometer plazas** en la breve: encontrar plaza depende de que haya
comunidad en esa ciudad. Lo que SÍ se promete ahora, además de recordar dónde aparcaste, es
**precio de gasolina y parkings** — porque eso funciona el día 1, sin comunidad, en los cuatro
países con fuente. Y se dice explícitamente que **ver nunca se paga**, que es la doctrina de
`CLAUDE.md` (⛔ *Nunca se gatea VER*).

---

## Descripción breve (80) — RECOMENDADA

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
| ro-RO | `Unde ai parcat? Paparcar ține minte. Cu prețuri și parcări publice.` |

### Alternativa — si prefieres conservar el remate comunitario

Es el texto vigente, intacto. Mantiene *«y tu plaza pasa al siguiente»*, que cuenta la comunidad
desde lo que el usuario **da** (no promete disponibilidad), pero deja fuera gasolina y parkings.

| Locale | Texto |
|---|---|
| en-US | `Where did you park? Paparcar remembers — and your spot passes on.` |
| es-ES | `¿Dónde aparcaste? Paparcar lo recuerda por ti. Y tu plaza pasa al siguiente.` |

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
• Your history, with how many parkings the app detected and how many spots you shared.
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
• All of it free: seeing what is around you is never behind a subscription.

YOUR NUMBERS
• What you spend on fuel, and how much you burn.
• How long you spend looking for a spot.
• Your usual street, your usual day, and the hour it gets hardest.

ONE CAR, SEVERAL DRIVERS
• Share a car with up to four people. Whoever drives it marks the parking, and the whole group sees where it is.
• Distance, time and spots freed count for everyone.
• Your routes are shared only if you say so.

AUTOMATIC DETECTION
If your car connects to your phone by Bluetooth, the disconnection helps detect that you have finished parking. If it has no Bluetooth, Paparcar uses motion and location instead.

Paparcar does not want to guess. When it is not sure what happened, it asks you before publishing a spot.

PAPARCAR PLUS
Free, you ask and the app answers. With Plus, the app watches while you are not looking:
• Alerts when a spot frees up in your area, with the app closed.
• Alerts when the price drops at a station you follow.
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
• Tu historial, con cuántos aparcamientos detectó la app y cuántas plazas has cedido.
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
• Todo gratis: ver lo que tienes alrededor nunca está detrás de una suscripción.

TUS CIFRAS
• Cuánto te gastas en combustible y cuánto consumes.
• Cuánto tiempo pasas buscando plaza.
• Tu calle de siempre, tu día de siempre y la hora en que se pone más difícil.

UN COCHE, VARIOS CONDUCTORES
• Comparte un coche hasta con cuatro personas. Quien lo conduce marca el aparcamiento y todo el grupo ve dónde está.
• Distancia, tiempo y plazas liberadas cuentan para todos.
• Tus rutas se comparten sólo si tú lo dices.

DETECCIÓN AUTOMÁTICA
Si tu coche se conecta al móvil por Bluetooth, la desconexión ayuda a detectar que has terminado de aparcar. Si no tiene Bluetooth, Paparcar usa el movimiento y la ubicación.

Paparcar no quiere adivinar. Cuando no está seguro de lo que ha pasado, te pregunta antes de publicar una plaza.

PAPARCAR PLUS
Gratis, tú preguntas y la app responde. Con Plus, la app vigila mientras tú no miras:
• Avisos cuando se libera una plaza en tu zona, con la app cerrada.
• Avisos cuando baja el precio en una gasolinera que sigues.
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
• Il tuo storico, con quanti parcheggi ha rilevato l'app e quanti posti hai ceduto.
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
• Tutto gratis: vedere cosa hai intorno non è mai dietro un abbonamento.

I TUOI NUMERI
• Quanto spendi di carburante e quanto ne consumi.
• Quanto tempo passi a cercare posto.
• La tua via di sempre, il tuo giorno di sempre e l'ora in cui è più difficile.

UN'AUTO, PIÙ CONDUCENTI
• Condividi un'auto fino a quattro persone. Chi la guida segna il parcheggio e tutto il gruppo vede dov'è.
• Distanza, tempo e posti liberati contano per tutti.
• I tuoi percorsi si condividono solo se lo dici tu.

RILEVAMENTO AUTOMATICO
Se la tua auto si collega al telefono via Bluetooth, la disconnessione aiuta a rilevare che hai finito di parcheggiare. Se non ha il Bluetooth, Paparcar usa movimento e posizione.

Paparcar non vuole indovinare. Quando non è sicuro di cosa sia successo, te lo chiede prima di pubblicare un posto.

PAPARCAR PLUS
Gratis, sei tu a chiedere e l'app risponde. Con Plus, l'app sorveglia mentre tu non guardi:
• Avvisi quando si libera un posto nella tua zona, con l'app chiusa.
• Avvisi quando cala il prezzo in un distributore che segui.
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
• O teu histórico, com quantos estacionamentos a aplicação detetou e quantos lugares cedeste.
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
• Tudo grátis: ver o que tens à volta nunca está atrás de uma subscrição.

OS TEUS NÚMEROS
• Quanto gastas em combustível e quanto consomes.
• Quanto tempo passas à procura de lugar.
• A tua rua do costume, o teu dia do costume e a hora em que fica mais difícil.

UM CARRO, VÁRIOS CONDUTORES
• Partilha um carro até com quatro pessoas. Quem o conduz marca o estacionamento e todo o grupo vê onde está.
• Distância, tempo e lugares libertados contam para todos.
• Os teus percursos só são partilhados se tu quiseres.

DETEÇÃO AUTOMÁTICA
Se o teu carro se liga ao telemóvel por Bluetooth, a desconexão ajuda a detetar que acabaste de estacionar. Se não tem Bluetooth, o Paparcar usa o movimento e a localização.

O Paparcar não quer adivinhar. Quando não tem a certeza do que aconteceu, pergunta-te antes de publicar um lugar.

PAPARCAR PLUS
Grátis, tu perguntas e a aplicação responde. Com Plus, a aplicação vigia enquanto não estás a olhar:
• Avisos quando fica livre um lugar na tua zona, com a aplicação fechada.
• Avisos quando desce o preço numa bomba que segues.
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

Garez-vous, sortez et continuez votre journée. Paparcar enregistre automatiquement où vous avez laissé la voiture : sans ouvrir l'application, sans épingle et sans saisir d'adresse. À votre retour, vous savez où elle est et depuis quand.

Quand vous repartez, la place que vous libérez peut être publiée pour les conducteurs à proximité. Et vous voyez celles que d'autres viennent de libérer. Plus il y a de conducteurs sur Paparcar dans votre ville, plus de places apparaissent.

COMMENT ÇA MARCHE
1. Vous vous garez. Paparcar le détecte et enregistre l'endroit. Sans Bluetooth, marquez le premier à la main.
2. Vous repartez. L'application le détecte.
3. Votre place est publiée pour les conducteurs à proximité.

VOTRE STATIONNEMENT, SANS EFFORT
• Où vous avez laissé la voiture et depuis combien de temps.
• Votre historique, avec le nombre de stationnements détectés et de places cédées.
• L'endroit n'est pas exact ? Déplacez-le sur la carte et enregistrez.
• Chaque voiture avec sa taille, sa couleur et son Bluetooth.

DES PLACES EN TEMPS RÉEL
• Les places que d'autres viennent de libérer, sur la carte.
• Quand chacune s'est libérée : il y a deux minutes n'est pas il y a vingt.
• Combien de conducteurs s'y dirigent déjà.
• Si votre voiture rentre : Paparcar compare l'espace à la vôtre.
• Une place libre en passant ? Signalez-la en deux fois.

STATIONS-SERVICE ET PARKINGS, SUR LA MÊME CARTE
• Stations-service avec le prix publié par les registres officiels et l'heure du relevé. Espagne, France, Allemagne et Italie.
• Le prix du carburant que votre voiture utilise, pas une moyenne générique.
• Parkings publics avec tarif, hauteur maximale et nombre de places, quand la source les donne.
• Le tout gratuitement : voir ce qui vous entoure n'est jamais derrière un abonnement.

VOS CHIFFRES
• Ce que vous dépensez en carburant et ce que vous consommez.
• Le temps passé à chercher une place.
• Votre rue habituelle, votre jour habituel et l'heure la plus difficile.

UNE VOITURE, PLUSIEURS CONDUCTEURS
• Partagez une voiture avec jusqu'à quatre personnes. Celui qui la conduit marque le stationnement, et tout le groupe voit où elle est.
• Distance, temps et places libérées comptent pour tout le monde.
• Vos trajets ne sont partagés que si vous le décidez.

DÉTECTION AUTOMATIQUE
Si votre voiture se connecte au téléphone en Bluetooth, la déconnexion aide à détecter la fin du stationnement. Sans Bluetooth, Paparcar utilise le mouvement et la localisation.

Paparcar ne veut pas deviner. Quand il n'est pas sûr, il vous demande avant de publier une place.

PAPARCAR PLUS
Gratuitement, vous demandez et l'application répond. Avec Plus, elle veille pendant que vous ne regardez pas :
• Alertes quand une place se libère dans votre zone, application fermée.
• Alertes quand le prix baisse dans une station que vous suivez.
• Tout votre historique. L'offre gratuite garde les 7 derniers jours.
• Plus d'une voiture à vous et plus de deux zones privées.
• Créer une voiture partagée. En rejoindre une est toujours gratuit.

CONFIDENTIALITÉ
• Zones privées : marquez des lieux comme votre domicile, Paparcar n'y publiera pas de place.
• Votre plaque d'immatriculation ne quitte jamais votre téléphone.
• La marque et le modèle n'apparaissent sur la place libérée que si vous l'activez.
• Votre localisation n'est jamais vendue ni utilisée à des fins publicitaires.
• Vous pouvez supprimer votre compte et ses données depuis les Paramètres ou le web.
• Sans publicité.

LOCALISATION EN ARRIÈRE-PLAN

Paparcar utilise des données de localisation même lorsque l'application est fermée ou non utilisée, afin de détecter où vous vous garez et de libérer votre place quand vous repartez.

La détection automatique a besoin de cette autorisation. Sans elle, Paparcar fonctionne quand même, mais vous devrez marquer et libérer vos stationnements à la main.

Politique de confidentialité :
https://paparcar.com/privacy-policy
```

### de-DE

```
Wo hast du geparkt?

Paparcar merkt es sich für dich.

Parke, steig aus und mach mit deinem Tag weiter. Paparcar speichert automatisch, wo du das Auto abgestellt hast: ohne die App zu öffnen, ohne Stecknadel und ohne Adresse. Wenn du zurückkommst, weißt du, wo es steht und wie lange schon.

Wenn du losfährst, kann der Platz, den du frei machst, für die Fahrer in der Nähe veröffentlicht werden. Und du siehst die Plätze, die andere gerade frei gemacht haben. Je mehr Fahrer Paparcar in deiner Stadt nutzen, desto mehr Plätze erscheinen.

SO FUNKTIONIERT ES
1. Du parkst. Paparcar erkennt es und speichert den Ort. Ohne Bluetooth markierst du den ersten von Hand.
2. Du fährst los. Die App erkennt es.
3. Dein Platz wird für die Fahrer in der Nähe veröffentlicht, wenn es passt.

DEIN PARKPLATZ, OHNE AUFWAND
• Wo du das Auto abgestellt hast und wie lange es schon dort steht.
• Dein Verlauf, mit wie vielen Parkvorgängen die App erkannt und wie vielen Plätzen du weitergegeben hast.
• Der Ort stimmt nicht? Verschiebe deinen Parkplatz auf der Karte und speichere erneut.
• Jedes Auto mit seiner Größe, seiner Farbe und seinem Bluetooth.

FREIE PLÄTZE IN ECHTZEIT
• Die Plätze, die andere gerade frei gemacht haben, auf der Karte.
• Wann jeder frei wurde: vor zwei Minuten ist nicht vor zwanzig.
• Wie viele Fahrer schon unterwegs dorthin sind.
• Ob dein Auto hineinpasst: Paparcar vergleicht die Lücke mit deinem.
• Im Vorbeigehen einen freien Platz gesehen? Melde ihn mit zwei Tipps.

TANKSTELLEN UND PARKHÄUSER, AUF DERSELBEN KARTE
• Tankstellen mit dem Preis aus amtlichen Registern und der Uhrzeit der Messung. Spanien, Frankreich, Deutschland und Italien.
• Der Preis des Kraftstoffs, den dein Auto tankt, kein allgemeiner Durchschnitt.
• Parkhäuser mit Tarif, Durchfahrtshöhe und Stellplätzen, wenn die Quelle sie liefert.
• Alles kostenlos: zu sehen, was um dich herum ist, steckt nie hinter einem Abo.

DEINE ZAHLEN
• Was du für Kraftstoff ausgibst und wie viel du verbrauchst.
• Wie lange du nach einem Platz suchst.
• Deine übliche Straße, dein üblicher Tag und die schwierigste Stunde.

EIN AUTO, MEHRERE FAHRER
• Teile ein Auto mit bis zu vier Personen. Wer es fährt, markiert den Parkplatz, und die ganze Gruppe sieht, wo es steht.
• Strecke, Zeit und frei gewordene Plätze zählen für alle.
• Deine Routen werden nur geteilt, wenn du es sagst.

AUTOMATISCHE ERKENNUNG
Verbindet sich dein Auto per Bluetooth mit dem Handy, hilft das Trennen zu erkennen, dass du fertig eingeparkt hast. Ohne Bluetooth nutzt Paparcar Bewegung und Standort.

Paparcar will nicht raten. Wenn nicht sicher ist, was passiert ist, fragt es dich, bevor ein Platz veröffentlicht wird.

PAPARCAR PLUS
Kostenlos fragst du, und die App antwortet. Mit Plus wacht sie, während du nicht hinsiehst:
• Hinweise, wenn in deiner Zone ein Platz frei wird, bei geschlossener App.
• Hinweise, wenn der Preis an einer Tankstelle sinkt, der du folgst.
• Dein ganzer Verlauf. Kostenlos bleiben die letzten 7 Tage.
• Mehr als ein eigenes Auto und mehr als zwei private Zonen.
• Ein geteiltes Auto anlegen. Einem beizutreten ist immer kostenlos.

DATENSCHUTZ
• Private Zonen: markiere Orte wie dein Zuhause, dann wird dort kein Platz veröffentlicht.
• Dein Kennzeichen verlässt niemals dein Handy.
• Marke und Modell erscheinen nur auf dem Platz, den du frei machst, wenn du das einschaltest.
• Dein Standort wird niemals verkauft oder für Werbung genutzt.
• Du kannst dein Konto und seine Daten in den Einstellungen oder im Web löschen.
• Keine Werbung.

STANDORT IM HINTERGRUND

Paparcar nutzt Standortdaten auch dann, wenn die App geschlossen ist oder nicht verwendet wird, um zu erkennen, wo du parkst, und um deinen Platz frei zu geben, wenn du losfährst.

Die automatische Erkennung braucht diese Berechtigung. Ohne sie funktioniert Paparcar weiterhin, aber du musst deine Parkvorgänge von Hand markieren und frei geben.

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
• Je geschiedenis, met hoeveel parkeerbeurten de app detecteerde en hoeveel plekken je doorgaf.
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
• Allemaal gratis: zien wat er om je heen is, zit nooit achter een abonnement.

JOUW CIJFERS
• Wat je aan brandstof uitgeeft en hoeveel je verbruikt.
• Hoe lang je zoekt naar een plek.
• Je vaste straat, je vaste dag en het lastigste uur.

ÉÉN AUTO, MEERDERE BESTUURDERS
• Deel een auto met maximaal vier mensen. Wie erin rijdt, markeert de parkeerplaats, en de hele groep ziet waar hij staat.
• Afstand, tijd en vrijgemaakte plekken tellen voor iedereen.
• Je routes worden alleen gedeeld als jij dat zegt.

AUTOMATISCHE DETECTIE
Maakt je auto via Bluetooth verbinding met je telefoon, dan helpt het verbreken te merken dat je klaar bent met parkeren. Zonder Bluetooth gebruikt Paparcar beweging en locatie.

Paparcar wil niet gokken. Als het niet zeker weet wat er is gebeurd, vraagt het het je voordat er een plek wordt gepubliceerd.

PAPARCAR PLUS
Gratis vraag jij, en de app antwoordt. Met Plus waakt de app terwijl jij niet kijkt:
• Meldingen als er een plek vrijkomt in jouw zone, met de app dicht.
• Meldingen als de prijs daalt bij een station dat je volgt.
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
• Twoja historia, z liczbą parkowań wykrytych przez aplikację i przekazanych miejsc.
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
• Wszystko za darmo: to, co widzisz wokół siebie, nigdy nie jest za abonamentem.

TWOJE LICZBY
• Ile wydajesz na paliwo i ile go zużywasz.
• Ile czasu spędzasz na szukaniu miejsca.
• Twoja zwykła ulica, Twój zwykły dzień i najtrudniejsza godzina.

JEDNO AUTO, KILKU KIEROWCÓW
• Udostępnij auto nawet czterem osobom. Kto nim jedzie, oznacza parkowanie, a cała grupa widzi, gdzie stoi.
• Dystans, czas i zwolnione miejsca liczą się dla wszystkich.
• Twoje trasy są udostępniane tylko wtedy, gdy tak zdecydujesz.

AUTOMATYCZNE WYKRYWANIE
Jeśli Twoje auto łączy się z telefonem przez Bluetooth, rozłączenie pomaga wykryć koniec parkowania. Bez Bluetooth Paparcar używa ruchu i lokalizacji.

Paparcar nie chce zgadywać. Gdy nie ma pewności, pyta Cię, zanim opublikuje miejsce.

PAPARCAR PLUS
Za darmo to Ty pytasz, a aplikacja odpowiada. Z Plus aplikacja czuwa, gdy Ty nie patrzysz:
• Powiadomienia, gdy w Twojej strefie zwalnia się miejsce, przy zamkniętej aplikacji.
• Powiadomienia, gdy spada cena na stacji, którą obserwujesz.
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

### ro-RO

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
• Istoricul tău, cu câte parcări a detectat aplicația și câte locuri ai cedat.
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
• Totul gratuit: să vezi ce ai în jur nu stă niciodată în spatele unui abonament.

CIFRELE TALE
• Cât cheltui pe combustibil și cât consumi.
• Cât timp petreci căutând un loc.
• Strada ta obișnuită, ziua ta obișnuită și ora cea mai grea.

O MAȘINĂ, MAI MULȚI ȘOFERI
• Împarte o mașină cu până la patru persoane. Cine o conduce marchează parcarea, iar tot grupul vede unde este.
• Distanța, timpul și locurile eliberate contează pentru toți.
• Traseele tale se partajează doar dacă spui tu.

DETECTARE AUTOMATĂ
Dacă mașina ta se conectează la telefon prin Bluetooth, deconectarea ajută la detectarea sfârșitului parcării. Fără Bluetooth, Paparcar folosește mișcarea și locația.

Paparcar nu vrea să ghicească. Când nu este sigur, te întreabă înainte de a publica un loc.

PAPARCAR PLUS
Gratuit, tu întrebi și aplicația răspunde. Cu Plus, aplicația veghează cât timp tu nu te uiți:
• Alerte când se eliberează un loc în zona ta, cu aplicația închisă.
• Alerte când scade prețul la o benzinărie pe care o urmărești.
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
