// GENERADO por docs/branding/master/export_surfaces.py — no editar a mano.
// La marca es FULL BLEED: toca los cuatro bordes de su lienzo, y ese lienzo
// NO es cuadrado. Quien la dibuje tiene que respetar la relacion.
export const MARK_W = 244;
export const MARK_H = 236;
export const MARK_PATHS = [
  // el ojo del pin
  'M160,62.5A21.5,21.5 0 1,0 160,105.5A21.5,21.5 0 1,0 160,62.5Z',
  // la rueda derecha
  'M143.042,176 A5,5 0 0,0 138.046,180.792 A53,53 0 1,0 232.999,150.673 A6,6 0 0,0 224.067,150.026 A92,92 0 0,1 210.074,161.179 A2,2 0 0,0 209.747,164.271 A26.5,26.5 0 1,1 164.629,180.393 A4,4 0 0,0 160.648,176 L143.042,176 Z',
  // el cuerpo: cabeza + pata + rueda izquierda
  'M160,0 A84,84 0 0,0 91.146,35.885 L9.556,152.642 A53,53 0 1,0 105.953,185.239 A18,18 0 0,1 123.937,168 L160,168 A84,84 0 0,0 160,0Z M53,209.5A26.5,26.5 0 1,1 53,156.5A26.5,26.5 0 1,1 53,209.5Z M160,135L78.039,135 A7,7 0 0,1 72.205,124.132 L117.493,55.82 A51,51 0 1,1 160,135Z',
];

// El EMBLEMA: la marca SOBRE su disco. Lo llevan la cabecera, el pie, las dos
// paginas legales y la tarjeta social — no el favicon (glifo de pestana) ni el
// apple-touch-icon (baldosa que el sistema exige opaca).
// El disco sostiene un emblema dentro de una composicion ajena; donde la marca
// llena su caja no lo lleva. Mismo criterio que el banner de Play.
// La PINTURA no es la del banner, y por contraste medido: el fondo del sitio es
// --ink #0d1117, el MISMO color que el disco del banner, que ahi daria 1,00:1.
// Sobre tinta plana el disco que se ve es el verde, con el glifo en tinta:
// 12,97:1 en los dos sentidos. Sin aro: sobre un disco verde no separa nada.
export const EMBLEM_BOX = 108;
export const EMBLEM_DISC_FILL = '#25F48C';
export const EMBLEM_MARK_FILL = '#0D1117';
export const EMBLEM_TRANSFORM = 'translate(28.6314 29.8562) scale(0.18882)';
