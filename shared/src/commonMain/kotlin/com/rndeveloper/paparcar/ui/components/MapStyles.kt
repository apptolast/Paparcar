package com.rndeveloper.paparcar.ui.components

// ─────────────────────────────────────────────────────────────────────────────
// Google Maps JSON styling.
//
// ⛔ These styles apply to `MapType.NORMAL` and to NOTHING ELSE — Google drops the whole JSON on
// satellite, hybrid and terrain. That is not a footnote: the app defaulted to TERRAIN for months,
// so every rule below was silently discarded and the map shipped in Google's own colours, with
// Google's own POI pills competing against our zone markers and fuel capsules. Whoever changes the
// default map type owns this file too. [MAP-A-STYLE-THAT-NEVER-RAN-001]
//
// What both themes deliberately keep: POI and neighbourhood NAMES. Only the icons go
// (`labels.icon: off`) — a pill with an icon is our marker language, a grey name is orientation.
// ─────────────────────────────────────────────────────────────────────────────

// The light map is a SURFACE the peek sheet sits on, and its neighbour is not white: the sheet and
// the nav bar are `PapAzure` #ECF0F9 (`surfaceContainer`). Measured against that neighbour on
// device, the old ground was the same surface as the sheet — 1.05:1 — and only the elevation
// shadow said where one ended.
//
// ⚠️ Tinting the ground blue to match the light palette does NOT fix that on its own, and this is
// the counter-intuitive part: the old neutral grey contrasted POORLY in luminance but WELL in hue
// (ΔE00 4.5 against the blue sheet). Moving the map into the sheet's own hue family spends that
// difference — #e2e9f3 measured 1.07:1 and ΔE00 2.1, i.e. perceptually CLOSER to the sheet than the
// grey it replaced, despite being darker. Keeping the tint means paying for the separation in
// luminance instead: #cdd8ea → 1.26:1 and ΔE00 6.8, better on both axes at once.
//
// The roads stay pure white on purpose: once the ground darkens, the street network becomes the
// bright figure on it, and highways sit between the two so the network stays one material.
//
// Water and parks are the exception to the muted ground, and deliberately so: they are the only
// two things on this map that are a PLACE rather than a surface, and the first pass drained them
// (water at chroma 16, parks at 5.5 — a green that wasn't green). They now carry real colour,
// #9ac9ee and #b6ddb0, at chroma 24 and 28. No risk of reading as ours: every Paparcar accent is
// dark and saturated (`PapCarBlueLight` #0057CA, `PapLiveMap` #2F6BFF, `PapGreenLight` #009F5E),
// and these are pale fills — the nearest pair still measures ΔE00 23. A marker keeps popping off
// a park because it is a small dark pill on a large pale field, not because the field was grey.
// [MAP-A-STYLE-THAT-NEVER-RAN-001]
internal const val LIGHT_MAP_STYLE = """[
  {"elementType":"geometry","stylers":[{"color":"#cdd8ea"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#3d4861"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#cdd8ea"}]},
  {"featureType":"administrative.land_parcel","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative.land_parcel","elementType":"labels.text.fill","stylers":[{"color":"#8b97ae"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#c5d1e5"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#5e6a85"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#b6ddb0"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#4f5b74"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#ffffff"}]},
  {"featureType":"road.arterial","elementType":"labels.text.fill","stylers":[{"color":"#4f5b74"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#e7eef8"}]},
  {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#3d4861"}]},
  {"featureType":"road.local","elementType":"labels.text.fill","stylers":[{"color":"#6e7b91"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#9ac9ee"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4f5b74"}]}
]"""

// Azul de noche, no gris neutro. Estos valores NO están elegidos a ojo: están muestreados píxel a
// píxel del tema oscuro nativo de Google sobre el Oppo, en la captura previa al arreglo del tipo de
// mapa — o sea del mapa real que la app llevaba meses enseñando, no de una imitación de memoria.
//
// El gris `#212121` que había aquí era lo que este fichero pedía desde siempre, y nadie lo había
// visto nunca porque nunca llegó a ejecutarse. Visto, convierte el plano nocturno en una
// radiografía: un mapa de noche es un plano bajo luz de noche, y el azul es lo que lo hace leer
// como tal. Además mide mejor — contra la hoja del peek `#131A24`, `#1c2a40` da 1,21:1 frente al
// 1,09:1 del gris, el mismo eje que motivó repintar el tema claro.
//
// ⛔ Lo que NO vuelve son los POI: `labels.icon` y `transit` siguen apagados. Se recupera el TONO,
// no las píldoras de Google. [MAP-THE-DARK-MAP-KEEPS-ITS-NIGHT-BLUE-001]
internal const val DARK_MAP_STYLE = """[
  {"elementType":"geometry","stylers":[{"color":"#1c2a40"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#9dabc4"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1c2a40"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#6b7a93"}]},
  {"featureType":"administrative.country","elementType":"labels.text.fill","stylers":[{"color":"#aebbd1"}]},
  {"featureType":"administrative.land_parcel","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#c3cfe2"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#20304a"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#8b9ab3"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#196453"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#8aa89b"}]},
  {"featureType":"poi.park","elementType":"labels.text.stroke","stylers":[{"color":"#14402f"}]},
  {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#31435e"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#a8b6cd"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#354d68"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3e5a77"}]},
  {"featureType":"road.highway.controlled_access","elementType":"geometry","stylers":[{"color":"#496787"}]},
  {"featureType":"road.local","elementType":"labels.text.fill","stylers":[{"color":"#8b9ab3"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#144a57"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#7f9fa8"}]}
]"""
