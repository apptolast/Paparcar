# DET-A-COMMUTER-TRAIN-IS-NOT-A-CAR-001 · el hueco que la velocidad no puede cerrar

**Estado:** 📋 Abierto · sin rama · hueco declarado por DET-A-TRAIN-IS-NOT-A-CAR-001

## Problema

`DET-A-TRAIN-IS-NOT-A-CAR-001` caza la alta velocidad (AVE/Alvia/Iryo/Ouigo, ≥200 km/h sostenidos) y
duda a partir de 160. **Por debajo no hay nada que medir**: Cercanías (90–120 km/h), Media Distancia
(140–160), metro y autobús urbano viven en la misma banda que un coche y la cinemática no los
separa. Tampoco lo hace Activity Recognition, que etiqueta todo eso `IN_VEHICLE`.

## Por qué no es urgente (y por qué tampoco es cero)

En la traza de campo del ticket padre los FP posteriores —incluido el del autobús a 55 km/h— eran
**hijos** del primero: todo armado va atado a una valla de vehículo (`GEOFENCE_EXIT`,
`AR_VEHICLE_ENTER … dep=enter_at_car`, centinela sobre la valla del coche), así que con el pin
correctamente en el origen ninguna sesión puede armar a 600 km de allí. Cerrado el padre, este hueco
solo muerde en un caso: **aparcar junto a la estación y coger un Cercanías**. Ahí el EXIT de la valla
propia arma legítimamente y el trayecto es indistinguible de conducir.

## La pista del user (16-09): el tiempo puede delatar lo que la velocidad no

> *«aunque no se pueda verificar la distancia, entre Cádiz y Jerez por ejemplo no se puede llegar en
> coche lo que se llegaría en tren — pero bueno, es bastante variable según la zona»*

El fondo es correcto y es el **mismo mecanismo que el corroborador del ticket padre**, bajado de
escala: el ritmo geodésico sostenido es una cota INFERIOR de la velocidad que el viaje habría
exigido por carretera, así que basta con que un par origen-destino sea más rápido en tren que en
coche para que el tiempo delate al tren sin necesidad de saber la distancia por carretera. En un
corredor donde la vía va recta y la carretera da un rodeo —o donde la carretera se colapsa a diario—
el margen existe de verdad.

Y el matiz del user es exactamente el motivo de que esto **no** entre como una barra fija: el margen
es de MINUTOS, no del factor 2 que separa un AVE de un coche, y su signo depende del corredor. Entre
Cádiz y Jerez el tren gana; en la mayoría de trayectos de Cercanías periurbanos el coche empata o
gana, y ahí el mismo umbral convertiría conducciones reales en dudas. Un número calibrado sobre el
corredor del usuario que abrió el ticket es precisamente el parche que este proyecto no hace.

Así que la forma utilizable no es un umbral: es **medir primero**. Con los diagnósticos remotos ya
encendidos, el corpus necesario es barato — pares origen-destino reales con su duración, en coche y
en tren, sobre los corredores donde la gente aparca junto a la estación. Hasta tener esa medida, la
regla no se puede escribir sin inventarse el número, y un veto inventado cuesta parkings de verdad.

## Señales que habría que explorar

- **Geometría, no velocidad**: la traza de un tren sigue un corredor ferroviario. `TrailMapMatcher`
  ya existe y hace map-matching contra OSM — pero necesita red, y las vías corren pegadas a las
  autopistas en buena parte del trayecto, así que el matching puede «acertar» sobre una carretera
  paralela. Habría que medir antes de creerlo.
- **Perfil de paradas**: un tren de cercanías para en estaciones, con una cadencia y una duración
  muy regulares; un coche urbano para en semáforos, con otra. Es un discriminador estadístico, no
  determinista.
- **La pregunta barata**: si la sesión arma desde una valla que está a pocos metros de una estación
  conocida, el nivel de exigencia para confirmar puede subir sin cambiar nada más.

## Criterio de éxito

Aparcar junto a una estación, hacer un trayecto en Cercanías y volver: la app no mueve el coche.
