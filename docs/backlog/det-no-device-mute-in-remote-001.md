# DET-NO-DEVICE-MUTE-IN-REMOTE-001 · ningún móvil puede nacer mudo en remoto

**Estado:** 🟢 Hecho el 27-08 (los 7 uids activados) · **el ticket existe para que la regla tenga
casa**, porque el trabajo estaba hecho y la regla no estaba escrita en ninguna parte

## El modo de fallo, que ya mordió dos veces

`FirestoreDetectionEventLogger` se auto-desactiva salvo que exista
`diagnostics_config/{userId}.enabled == true`. El default —documento ausente, campo ausente o
ilegible— es **`false`**, y eso es **correcto y deliberado**: la telemetría es opt-in, y así lo dice
su propio KDoc. No hay nada que arreglar en el código.

Lo que falla es **operativo**: una cuenta nueva nace sin ese documento, o sea **ciega en remoto**, y
nadie se entera hasta que hace falta el trace. Ha pasado dos veces:

| Cuándo | Qué costó |
|---|---|
| **24-08** | El FP del semáforo del hospital (Xiaomi, uid `12ck5…`) hubo que diagnosticarlo sacando el `parkdiag` del móvil **a mano**: a Firestore no llegó absolutamente nada |
| **26-08** | El reset de Room a v1 crea una cuenta nueva (`gonrendur`). `DATA-ROOM-STARTS-AT-VERSION-ONE-001` avisó de que nacería ciega — y habría nacido |

## Estado a 27-08: los 7 uids, activados

| uid | Quién | `enabled` |
|---|---|---|
| `fiypNbElGlfFexLMpU9sNaMjRMD3` | rndeveloper11501 — **Oppo** (Kamiq, BT) | ✅ |
| `WZB7oftWLDY1toGJrDwoRHnnYHx2` | collejaygusilu — **Redmi** (otra cuenta) | ✅ |
| `12ck5eNWl2ONpMLN05e8jUrXER33` | luciernaga.peculo04 — **Xiaomi**, el 4º móvil | ✅ 27-08 |
| `nJEqcLEsgoRZIJY3I7jxbXrFhKO2` | gonrendur — **cuenta nueva del reset de Room** | ✅ 27-08 |
| `T4I9HT8Z2rT0MZ9CgwYJyIF0xFL2` | maria.peculo04 — activa a diario, sin config desde siempre | ✅ 27-08 |
| `sUGo7EYl16XDtosI8Ei7LFeAo2E2` | cardomfer97 (Carlos) | ✅ 12-07 |
| `nvGQi4pTnkdCIlUa6iadEFRZxr33` | unanimeani — sin actividad desde el 01-08, activado por si vuelve | ✅ 27-08 |

## La regla, que es lo que este ticket viene a fijar

> **Cuenta nueva que vaya a conducir → su `diagnostics_config` ANTES del primer viaje.**
> Sin él, el viaje sólo deja rastro local, y el `parkdiag` local rota.

Se comprueba y se crea con las herramientas MCP de Firestore, sin desplegar nada:

```
firestore_list_documents  parent=projects/pap-26/databases/(default)/documents
                          collectionId=diagnostics_config
firestore_add_document    → { enabled: true, note: "<quién es y por qué>" }
```

⛔ **El campo `note` no es decoración.** Es lo único que ata un uid opaco a un móvil concreto, y ya
evitó un error: la nota vieja del Redmi decía «(Oppo)» y hubo que corregirla el 12-07. Un uid sin
nota es un uid que alguien confundirá.

## Lo que NO se hace, y por qué

- **No se cambia el default a `true`.** El opt-in es la decisión, y afecta a usuarios que no somos
  nosotros.
- **No se automatiza en el registro.** Escribir telemetría-on desde el cliente al crear cuenta
  convierte una decisión de privacidad en un efecto secundario del alta. Es un paso manual **a
  propósito**, y por eso necesita estar escrito.

## Relacionado

- `DATA-ROOM-STARTS-AT-VERSION-ONE-001` — el reset que crea la cuenta nueva.
- `DET-PARKDIAG-KEEP-MORE-HISTORY-001` — la otra mitad: cuando el remoto falla, el local tiene que
  aguantar. Ahora guarda 5 rotaciones.
- `DET-LOG-02` — la puerta y por qué es un flag de Firestore y no Remote Config.
