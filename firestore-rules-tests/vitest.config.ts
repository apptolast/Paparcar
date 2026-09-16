import { defineConfig } from 'vitest/config';

// ⛔ En serie: los ficheros comparten UN emulador y cada suite hace `clearFirestore()`. En paralelo
// una suite borra la semilla de otra a media prueba, y el resultado no es un fallo de reglas sino
// una carrera — la peor clase de rojo, porque se arregla solo al reintentar.
export default defineConfig({
  test: { fileParallelism: false, testTimeout: 15_000 },
});
