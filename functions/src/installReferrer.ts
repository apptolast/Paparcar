import { createDecipheriv } from 'node:crypto';

/**
 * El bloque que Meta mete en `utm_content` cuando el clic de verdad vino de un anuncio.
 * [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 */
export interface MetaReferrerBlock {
  data: string; // hex
  nonce: string; // hex
}

export interface MetaDecryptedIds {
  campaignId: string | null;
  adsetId: string | null;
  adId: string | null;
}

/** AES-256-GCM: el tag de autenticación son 16 bytes. */
const GCM_TAG_LENGTH_BYTES = 16;
/** El key de Meta for Developers es un string hex de 64 caracteres = 32 bytes = AES-256. */
const AES_256_KEY_BYTES = 32;

/**
 * Reconoce el bloque de Meta en un `utm_content` crudo. `null` para cualquier otra cosa — el
 * `utm_content` de nuestros propios enlaces (`z-mostoles`, un placement) nunca es JSON con esta
 * forma, así que no hace falta distinguir "no es JSON" de "es JSON pero no es esto".
 */
export function parseMetaReferrerBlock(rawUtmContent: string): MetaReferrerBlock | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawUtmContent);
  } catch {
    return null;
  }
  if (typeof parsed !== 'object' || parsed === null) return null;
  const source = (parsed as Record<string, unknown>).source;
  if (typeof source !== 'object' || source === null) return null;
  const data = (source as Record<string, unknown>).data;
  const nonce = (source as Record<string, unknown>).nonce;
  if (typeof data !== 'string' || typeof nonce !== 'string') return null;
  return { data, nonce };
}

/**
 * ⚠️ **SIN VERIFICAR contra un payload real de Meta** — ver
 * `docs/backlog/mkt-install-attribution-channel-001.md`. La documentación pública (Airbridge,
 * Singular) confirma AES-256-GCM con `data`/`nonce` en hex y una clave de 64 caracteres hex, pero
 * no el byte exacto donde vive el tag de autenticación. Esta función asume la convención más
 * común: el tag de 16 bytes va AÑADIDO al final de `data`.
 *
 * **Falla CERRADO**: cualquier error de parseo o de criptografía (incluida una convención de tag
 * equivocada — AES-GCM verifica el tag, así que una suposición mala lanza, no decripta mal en
 * silencio) devuelve `null`, nunca lanza hacia el caller. Eso es lo que hace seguro desplegar esto
 * antes de validarlo con un anuncio real: en el peor caso, cae a `META_ORGANIC_*` en el cliente —
 * el lado seguro del fallo asimétrico — nunca a un `META_PAID` falso.
 */
export function decryptMetaReferrerBlock(
  block: MetaReferrerBlock,
  decryptionKeyHex: string,
): MetaDecryptedIds | null {
  try {
    const key = Buffer.from(decryptionKeyHex, 'hex');
    if (key.length !== AES_256_KEY_BYTES) return null;

    const iv = Buffer.from(block.nonce, 'hex');
    const payload = Buffer.from(block.data, 'hex');
    if (payload.length <= GCM_TAG_LENGTH_BYTES) return null;
    const tag = payload.subarray(payload.length - GCM_TAG_LENGTH_BYTES);
    const ciphertext = payload.subarray(0, payload.length - GCM_TAG_LENGTH_BYTES);

    const decipher = createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);
    const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
    const payloadJson = JSON.parse(plaintext) as Record<string, unknown>;

    // ⚠️ Nomenclatura LEGACY de la Marketing API de Meta, confirmada por fuentes de terceros
    // (Airbridge): lo que hoy Ads Manager llama Campaña/Conjunto de anuncios/Anuncio sale del
    // payload como `campaign_group_id`/`campaign_id`/`adgroup_id` — NO `campaign_id`/`adset_id`/
    // `ad_id` como sugeriría la UI actual.
    return {
      campaignId: stringOrNull(payloadJson.campaign_group_id),
      adsetId: stringOrNull(payloadJson.campaign_id),
      adId: stringOrNull(payloadJson.adgroup_id),
    };
  } catch {
    return null;
  }
}

function stringOrNull(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}
