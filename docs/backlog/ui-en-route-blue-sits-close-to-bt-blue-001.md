# UI-EN-ROUTE-BLUE-SITS-CLOSE-TO-BT-BLUE-001 · Nota medida, aparcada

**Estado:** 🧊 Diferido por el user (08-09-2026) — el en-route es una feature de comunidad y **hoy no
hay usuarios**, así que no compite por atención. Esto es la medición, no una tarea.

Al vestir el viaje con la identidad de su coche [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001], la traza de
un coche **BT** queda cerca del azul del pin de plaza en-route, que es fijo:

| Tema | Traza BT (`papCarBlue`) | Pin en-route (`PapLiveMap`) | ΔE00 |
|---|---|---|---|
| oscuro | `#5B9EFF` | `#2F6BFF` | 16,1 — por encima del umbral (~15) |
| claro | `#0057CA` | `#2F6BFF` | **10,0** — por debajo |

No es una contradicción de significados (son dos cosas distintas: tu viaje y el de otro, con formas
distintas — línea gruesa vs pin con "P"), así que no urge. Cuando el en-route tenga usuarios de
verdad, se decide entonces si el pin cambia de tono o si `PapLiveMap` se separa en claro.
