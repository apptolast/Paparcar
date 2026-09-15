# INFRA-AN-ALERT-THAT-TRAVELS-NEEDS-A-BACKEND-001 · Lo que no podemos avisar todavía, y qué hay que montar para poder

**Estado:** 📋 Aparcado (15-09-2026) — doc de INFRAESTRUCTURA. **No se implementa nada de aquí por
iniciativa propia**; existe para que ninguna alerta se planifique sin nombrar lo que le falta debajo.

Nace al abrir las dos primeras alertas de verdad
([`fuel-a-price-remembered-is-a-price-that-can-move-001.md`](fuel-a-price-remembered-is-a-price-that-can-move-001.md)
y [`zone-an-alert-nobody-is-awake-to-receive-001.md`](zone-an-alert-nobody-is-awake-to-receive-001.md)),
cuando quedó claro que esas dos se pueden hacer **enteras en el dispositivo** y las demás no.

## La línea que separa las dos mitades

> **Si el aviso se puede decidir mirando lo que este móvil ya sabe, es local. Si hay que enterarse de
> algo que ha pasado en otro sitio, hace falta que alguien nos lo cuente.**

Y ahí no hay término medio disfrazado: el sustituto local de «que te lo cuenten» es **preguntar cada
rato**, que es lo mismo pagado en batería y en lecturas de Firestore.

## Lo que Paparcar NO tiene hoy

| Pieza | Estado | Qué desbloquea |
|---|---|---|
| **FCM** (push a dispositivo) | ❌ sin montar — no hay token, ni registro, ni `FirebaseMessagingService` | Que un aviso llegue en segundos sin que el móvil pregunte |
| **Cloud Functions** | ❌ ninguna desplegada. Las estrena **F6b** (`PARKINGS-LIVE-OCCUPANCY-001`) | El fan-out: de UN evento a los N usuarios a quienes les importa |
| **Suscripciones en servidor** | ❌ las zonas viven solo en Room + Firestore del propio usuario | Que el servidor sepa a quién avisar sin leerle el móvil |
| **Histórico de precios compartido** | ❌ el de `FUEL-A-PRICE-REMEMBERED…` es **local, 30 días, por dispositivo** | Serie larga, misma serie en dos móviles, medias reales |
| **Ocupación en vivo de parkings** | ❌ no hay fuente | Cualquier aviso de «tu parking tiene hueco» |

⚠️ **Y tres cosas que cuestan dinero o trabajo y conviene decir antes, no después:** el fan-out se
paga por escritura; un push que se equivoca de persona es un problema de privacidad, no una
molestia; y **un token de FCM es un identificador de dispositivo** — entra en la política y en el
Data Safety de Play, que ya está pendiente de un delta.

## Alertas aparcadas aquí, con lo que le falta a cada una

| Alerta | Bloqueada por | Nota |
|---|---|---|
| **Zona por push** (latencia de segundos, sin poll) | FCM + Cloud Function + suscripciones en servidor | Es la **sucesora** del worker de `ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001`, no su alternativa: el worker entrega ya, esto lo hace barato |
| **Parking habitual lleno / con hueco** | F6b (ocupación) + FCM | La pregunta que la gente hace de verdad antes de salir |
| **Seguir una gasolinera con serie larga** | almacenamiento en servidor | La versión local de 30 días sale en el ticket de fuel; la serie histórica y multi-dispositivo, no |
| **Gasolinera barata en ruta** (mientras conduces) | — *(local, pero pide el proyector de viaje)* | Depende de la proyección «viaje» del pivote, que sigue con UX por decidir |
| **Aviso de gálibo** | ⚠️ **NO es backend**: falta la **altura del vehículo** | Ver abajo |
| **Aviso de depósito** («~450 km desde el último repostaje») | ⚠️ **NO es backend**: falta **marcar el repostaje** | Consumo y km ya se calculan |

### ⛔ Dos que el doc de tiers daba por casi hechas y no lo están

1. **El aviso de gálibo NO es «NADA nuevo».** `PublicParking.maxHeightMeters` existe, sí — pero
   `CarbodyType` solo lleva `sizeCategory`: **no hay ni un metro de altura del lado del coche**.
   Hace falta mapear `CarbodyType → altura típica` (o preguntarla en el alta de furgonetas y SUV),
   y eso es un ticket de modelo con su migración, no un `if` en un peek.
2. **La alerta de zona no estaba «sin implementar»**, estaba **implementada y sin entregar**. Es
   peor: es lo único que ya cobramos, y solo llega con el proceso vivo.

## Cuándo se retoma

El orden natural no es de valor, es de dependencias: **F6b** despliega la primera Cloud Function →
con las funciones vivas, FCM es un paso corto → con FCM, la zona por push retira su poll y el resto
de la tabla deja de estar bloqueado. Mientras tanto, lo que sea decidible en el móvil se hace en el
móvil, y lo que no, se queda escrito aquí.
