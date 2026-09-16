# LEGAL-DATA-SAFETY-ANSWERS-FOR-1-2-0-001 · El formulario y la política se quedaron en las alertas de zona; desde entonces entraron el grupo, el push y cuatro fuentes más

**Estado:** 🟡 En curso — abierto 2026-09-16 desde master `ac67e5ce` (versionCode 13 / 1.2.0)
**Hecho en esta pasada:** `docs/legal/DATA-SAFETY-FORM.md` y `hosting/public/privacy-policy.html`
actualizados (sin desplegar).
**Bloquea:** publicar la 1.2.0 con la declaración de Play al día.

## Problema

La chuleta del formulario se revisó por última vez el **15-09** con las alertas de zona
(`1b690f45`). Después entraron F8 (coche compartido), el push al grupo, Italia y Alemania, y el
geocodificador. La política publicada (10-09) está mejor que la chuleta —ya tiene §2.8 y la tabla
de terceros— pero tampoco ha visto nada de F8.

## Inventario de lo que faltaba (medido en código, 16-09)

| # | Hueco | Medido en | Dónde faltaba |
|---|---|---|---|
| 1 | El token FCM ya no vive solo en `zoneAlertSubs`: también en `sharedCars/{carId}/members/{uid}.tokens` | `functions/src/index.ts:205` | Formulario (fila *Device or other IDs*) y política §2.5 |
| 2 | Otros usuarios ven **dónde está tu coche AHORA y quién lo dejó** | `sharedCars/{carId}/state/current` | Política §8 decía que las plazas eran «el único dato visible para otros usuarios» — falso |
| 3 | ~~La ruta viaja al grupo aunque el consentimiento personal esté apagado~~ | `SharedCarMirror.kt` | ✅ **Arreglado** (`e8baaf35`): dos puertas, cada una con su permiso. Política §2.9 y la fila de ubicación ya lo dicen así |
| 4 | Borrar la cuenta deja `members/{uid}` y `zoneAlertSubs/{uid}` | `DomainModule.kt:66` | Política §6. Ticket propio: `priv-a-deleted-account-leaves-two-tokens-behind-001.md` |
| 5 | Italia (MIMIT) y Alemania (Tankerkönig) reciben consultas | `carburanti.mise.gov.it`, `creativecommons.tankerkoenig.de` | Tabla §4 de la política |
| 6 | El geocodificador **photon.komoot.io** recibe texto de búsqueda y coordenadas | `AndroidGeocoderDataSourceImpl.kt:125` | Tabla §4 — no estaba en ningún sitio |
| 7 | A Overpass no le mandamos «un recuadro aproximado» sino **el punto exacto y un radio** (`around:$radius,$lat,$lon`) | `OsmFuelStationParser.kt:40`, `OsmPublicParkingParser.kt:53` | Política §2.8 y §4 lo suavizaban |

## Decidido por el user (16-09)

- **Terceros**: el coche compartido es **opcional** — *«compartimos si el usuario quiere»*. La
  transferencia entre miembros de un grupo al que te unes a propósito se declara como funcionalidad
  iniciada por el usuario, no como venta ni cesión comercial.
- **Trader (DSA) y compras**: ya declarados y **aceptados por Google** — la app vende Plus en
  producción (<https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar>). Lo que
  estaba desfasado era la chuleta, que seguía diciendo «la primera release va sin monetizar». Queda
  **verificar en el Console** que la fila de compras existe en el Data Safety, no solo el trader.

## El guard de paridad de la política (16-09)

Pregunta del user: *¿para qué mantenemos la política en dos idiomas, si es una web y el navegador
traduce?* Es razonable — se abre con `ACTION_VIEW`, o sea en el navegador del usuario, donde Chrome
ofrece traducir; y los otros **siete** idiomas de la app ya leen la inglesa hoy.

Lo que decide quedarse con la española no es la traducción, es de quién es la responsabilidad: el
texto vinculante lo publica el responsable del tratamiento, y una traducción automática no la
publica él. Con España como mercado principal, la AEPD como autoridad citada en §7 y un trader
español, un aviso solo en inglés es una arista que no compensa. Pero el coste que señalaba el user
—dos textos que hay que escribir a mano y se desincronizan— **es real y ya había pasado**.

`PrivacyPolicyParityGuardrailTest` compara el ESQUELETO de las dos mitades (índice de epígrafes,
puntos por epígrafe, filas de la tabla de terceros), que es lo que se rompe cuando alguien escribe
en una y no en la otra. No compara texto: son idiomas distintos, y un cambio de redacción tiene que
poder pasar sin ruido.

**Encontró una divergencia en su primera ejecución, anterior a esta tanda**: §5 tenía 7 puntos en
inglés y 6 en español — al lector español no se le decía nunca cuánto se conserva el **estado de la
suscripción** ni que las consultas al mapa no se guardan. Añadido.

⛔ **El guard y el HTML tienen que entrar en el MISMO commit.** Si el test entra solo, master se
pone rojo: el fichero que hay en master todavía no lleva el punto que falta en español.

## Lo que queda

- [ ] Verificación en Play Console (la hace Claude Code web, ver prompt del turno): filas reales del
      Data Safety vs. este doc; trader; URL de borrado; declaración de background location.
- [x] §6 de la política — **cerrado el 16-09**: el barrido aterrizó en master, así que el texto ya
      nombra el grupo, los viajes espejados y los tokens, y dice qué pasa con un coche que era tuyo.
      ⛔ No desplegar el hosting antes que `firestore:rules`: sin las reglas nuevas, el barrido de un
      miembro falla con permiso denegado y la política prometería otra vez lo que el código no hace.
- [ ] Desplegar: `firebase deploy --only hosting`. ⛔ El MCP `firebase_deploy` devuelve `success`
      sin aplicar nada — CLI y releer.
- [ ] Bajar la fecha de cabecera de la política al día del despliegue (hoy dice 10-09-2026).

## Lado iOS

Nada que implementar: es documentación y consola. El token APNs viaja por el mismo campo `tokens`
que el de FCM, así que la fila del formulario ya lo cubre cuando iOS se publique.
