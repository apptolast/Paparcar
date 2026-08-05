# DET-STOP-BUTTON-001 — Botón de usuario "Parar detección" durante una detección EN CURSO

**Estado: PENDIENTE DE DEFINIR** (creada 2026-08-06 a petición del usuario; sin spec, sin rama).

## Idea (en una frase)

Cuando la detección está **realmente detectando** (servicio ACTIVO: armado tras un trigger,
midiendo conducción, resolviendo un aparcamiento), el usuario debe poder **pararla con un botón**
— sin tocar el toggle global de Ajustes y sin esperar a que la sesión resuelva sola.

## Qué NO es

- NO es el toggle de auto-detección de Ajustes (ese apaga la feature entera, [DET-TOGGLE-001]).
- NO afecta al modo centinela (SENTRY, servicio residente durmiente): el centinela se gobierna
  por el toggle de Ajustes + tier ([DET-RESIDENT-FGS-001] F3). Este botón solo existe mientras
  hay una sesión de detección viva.

## Preguntas abiertas (a definir con el usuario)

1. **Dónde vive el botón**: ¿acción en la notificación del FGS de detección, chip/banner en Home
   (junto al chip "Conduciendo"), o ambos?
2. **Semántica de "parar"**: ¿cancela la sesión en silencio (outcome tipo `aborted_by_user`) o
   ofrece también "ya he aparcado, marcar aquí" (atajo al flujo manual)?
3. **Efecto sobre el epílogo**: tras parar, ¿el servicio degrada a centinela como en cualquier
   teardown deliberado, o muere hasta el siguiente trigger?
4. **Telemetría**: outcome/DetectionEvent propio para poder distinguir en Firestore "el usuario
   paró" de cualquier abort del sistema.
5. **Copy**: causa+consecuencia+remedio, sin mecánica interna, 9 locales.
