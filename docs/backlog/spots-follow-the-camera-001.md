# SPOTS-FOLLOW-THE-CAMERA-001 · Los spots del encuadre, cuando arrastras el mapa a tu destino

**Estado:** 🔵 Propuesto — sin rama · hermana de [`fuel-layer-follows-the-camera-001.md`](fuel-layer-follows-the-camera-001.md)

## Problema

Los spots se observan alrededor del GPS del user. Vas conduciendo a Chamberí, arrastras el mapa a
tu destino para ver qué hay libre ALLÍ — y el mapa te sigue enseñando lo de donde estás. Es el
mismo agujero que la capa de gasolineras tapó con el molde de encuadre, con el matiz de que aquí
el caso útil es INTRA-ciudad: una plaza a 500 km muere antes de que arranques.

## Por qué NO es copiar el ticket de gasolineras (medir antes de hacer)

1. **La fuente es una suscripción viva de Firestore**, no un REST con caché barata. Re-anclar el
   listener en cada arrastre = churn de listeners y lecturas facturadas. Diseño candidato:
   suscripción viva SOLO alrededor del user (como hoy) + **consulta one-shot** para el encuadre
   lejano, con el mismo `ViewportRefetch` (umbral + asentamiento) — y medir el coste en lecturas
   antes de decidir umbrales.
2. **La frescura pierde valor con la distancia**: un spot del encuadre lejano puede ser honesto de
   pintar y absurdo de perseguir. Decidir si a partir de cierta distancia se pintan igual (son
   información) o con algún tratamiento — SIN tocar la rampa de frescura, que es exclusiva de la
   caducidad.
3. **Los votos ya se auto-protegen** (`SpotVotePolicy` exige testigo por distancia), y el en-route
   («3 en camino») a un spot lejano es legítimo — es justo el caso «voy hacia allí».
4. **F6 (parkings) hará la misma pregunta**: tercera superficie con encuadre. Hacer esta ficha
   pegada a F6 amortiza el molde dos veces.

## Cuándo

Con F6 o justo después — cuando el molde de encuadre ya esté rodado por la capa de gasolineras y
el coste Firestore se pueda medir en frío.
