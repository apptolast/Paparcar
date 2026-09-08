# WIDGET-MAP-SNAPSHOT-003 · El widget estirado enseña el mapa del pin

**Estado:** 📋 Abierto (2026-09-08) — idea del user al cerrar WIDGET-ACTIONS-002. Sin empezar.

## Problema

El widget dice la calle; a mucha gente le orienta más VER el mapa. Un widget estirado con la foto
del mapa y el pin responde "¿dónde está?" sin abrir nada.

## Restricción técnica (la que define el diseño)

Un widget NO puede alojar un mapa vivo — RemoteViews/Glance no admiten MapView ni Compose Maps.
El estándar del sector: **imagen estática** (Google Static Maps API) descargada y cacheada.

## Diseño (a detallar al abrirlo)

- Recuperar `SizeMode.Responsive` con dos cortes (la infraestructura se escribió y retiró en
  WIDGET-ACTIONS-002 — está en el historial de esa rama): compacto = tarjeta actual; estirado =
  tarjeta + bitmap del mapa debajo (tap en el mapa = abrir la app centrada, como la tarjeta).
- **Una petición por aparcamiento**, no por render: al confirmar (o al primer render sin cache),
  un worker baja el PNG del Static Maps API (centro = pin, marker, zoom fijo, tamaño por corte,
  variante dark si el sistema está en oscuro) y lo cachea en storage privado; el widget pinta
  `ImageProvider(bitmap)`. Sin red en el momento de pintar. Coste ≈ 0 (≪ cuota).
- Requisito de consola: habilitar **Static Maps API** en la key de Maps de `pap-26` (inventario de
  keys en la memoria; ⛔ recordar que Firebase auto-crea keys por SHA-1 — usar la existente).
- Fallback honesto: sin cache y sin red → el corte estirado enseña la tarjeta + un área neutra con
  la calle, nunca un mapa roto.
- La licencia/atribución de Google en la esquina de la imagen viene incluida en el PNG del API
  (obligatoria — no recortarla).

## Cuándo

No bloquea la ola. Buen compañero de ONB-WIDGET-IS-THE-LAST-STEP-001 (mismo músculo). El user
decide el hueco.
