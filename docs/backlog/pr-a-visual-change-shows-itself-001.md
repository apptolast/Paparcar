# PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001 · Una PR con resultado visual se enseña

**Estado:** ✅ Done · en master · regla viva en `CLAUDE.md` § *Commits y ramas*

## Problema
Petición del user (21-09-2026): toda PR cuyo cambio se aprecie en pantalla debe llevar una captura
del resultado en su descripción, y esa regla tiene que vivir en las instrucciones permanentes del
proyecto, no en el hilo de una conversación.

El dato que lo justifica está en las PRs abiertas hoy. De las 14, **ninguna** llevaba captura en su
cuerpo al abrirse, y **todas las visuales acabaron recibiendo `CHANGES_REQUESTED` con una captura
adjunta por el USER** — #37 (badge que «apenas destaca»), #55 (chip), #58 (separación del marcador),
#59 (la «P» y «De pago» cortado), #62 (la notificación FGS que seguía ahí), #63 (canje que no
funciona). En #37 la captura del user probó además un **bug de diseño** —dos gasolineras empatadas a
1,809 € y el badge de «más barata» caído— que el autor de la PR no había visto.

O sea: la captura se acaba haciendo siempre. La única diferencia es **quién** la hace y **cuántas
vueltas** cuesta. Hoy la pone el revisor después de instalar el APK; la regla la mueve al autor,
antes de pedir revisión.

## Doctrina violada
Ninguna existente — esto **añade** una regla al bloque de *Commits y ramas*. No toca ni contradice
las que ya están; la sección nueva se inserta entre *Commits y ramas* y *Cosas que NO hacer*.

Sí se apoya en dos que ya existen y hoy no tenían testigo en la PR:
- *«⏳ sin ver en mano»* es un estado recurrente en `MEMORY.md`: media docena de tickets cerrados en
  master siguen marcados como no vistos en device. Una captura en la PR es exactamente el testigo
  que le falta a ese estado.
- El **Dev Catalog** (galería de estados) se mantiene en sync justo para poder mirar un estado sin
  montar el escenario real. Era la herramienta sin el hábito.

## Señales / datos disponibles
- `gh pr view <n> --json body` — la descripción es lo que se audita; las capturas del user viven hoy
  en `comments`, no en `body`.
- Dev Catalog por adb (`…paparcar.DevMainActivity`) y `instalar-apk` (`/run`) ya producen el device
  desde el que sale la captura.

## Diseño
Una sección nueva en `CLAUDE.md`, en el bloque de convenciones de PR, con cinco líneas:
1. la captura va en el **cuerpo** de la PR, no en un comentario;
2. tiene que ser del **estado final** del código de esa rama, y se **sustituye** si la PR cambia;
3. lista de disparadores (UI, Compose, pantallas, componentes, navegación, diálogos, estados
   visuales, mapa, marcadores, iconos, animaciones, layout, tema, accesibilidad visual);
4. si no hay resultado visual, **decirlo en una línea** — el hueco se lee como olvido;
5. varios estados → una captura por estado, y antes/después cuando el valor está en la diferencia.

El punto (2) es el que de verdad cuesta: una captura vieja **certifica lo que nadie ha visto**, que
es peor que no tener ninguna.

⛔ **No se automatiza con un guard.** Konsist lee código, no descripciones de PR, y un check de CI
que exija una imagen en el cuerpo no puede juzgar si la captura corresponde al último push — sería
un guard que aprueba lo que no ha mirado, justo lo que
`TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001` prohíbe. Además el CI de la org lleva caído por
facturación desde el 14-09, así que un check nuevo hoy nace muerto.

## Criterio de éxito
La siguiente PR con cambio visible se abre ya con su captura en el cuerpo, y la ronda de revisión
del user no tiene que empezar por instalar el APK para ver de qué se está hablando.

## Consumidores auditados
- `CLAUDE.md` § *Commits y ramas* → sección nueva justo debajo. **No se modifica ni se borra
  ninguna regla existente.**
- `.claude/skills/nuevo-ticket/SKILL.md` → describe abrir/cerrar tarea (worktree, doc, squash), no
  la redacción de la PR. **Exento**: no hay nada que contradiga ni que duplicar.
- `.github/workflows/ci.yml` → **exento** por el ⛔ de arriba: esto no se vigila con un job.
- `docs/` (PARKING-DETECTION.md, COLOR-SYSTEM.md, VEHICLE-CATEGORIZATION.md) → documentan producto y
  arquitectura, no el flujo de PR. **Exentos**.
