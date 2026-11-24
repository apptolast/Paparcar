import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createCipheriv, randomBytes } from 'node:crypto';
import { decryptMetaReferrerBlock, parseMetaReferrerBlock, type MetaReferrerBlock } from './installReferrer';

/**
 * [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * ⛔ Este test prueba que `decryptMetaReferrerBlock` hace correctamente lo que DICE hacer — un
 * roundtrip AES-256-GCM contra un payload que ESTE MISMO test cifra. **No prueba que coincida con
 * el formato real de Meta** — eso exige un payload real (vista previa de un anuncio), que es el
 * punto 7 del plan de pruebas en el doc de backlog. Confundir las dos cosas es exactamente el
 * error que ese doc avisa de no cometer.
 */

const KEY_HEX = randomBytes(32).toString('hex');

function encryptLikeMeta(payload: Record<string, unknown>, keyHex: string): MetaReferrerBlock {
  const key = Buffer.from(keyHex, 'hex');
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(payload), 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return {
    data: Buffer.concat([ciphertext, tag]).toString('hex'),
    nonce: iv.toString('hex'),
  };
}

test('parseMetaReferrerBlock reconoce el bloque de Meta', () => {
  const block = parseMetaReferrerBlock('{"app":"123","source":{"data":"ab","nonce":"cd"}}');
  assert.deepEqual(block, { data: 'ab', nonce: 'cd' });
});

test('parseMetaReferrerBlock devuelve null para un utm_content corriente (no JSON)', () => {
  assert.equal(parseMetaReferrerBlock('z-mostoles'), null);
  assert.equal(parseMetaReferrerBlock('cta_hero'), null);
});

test('parseMetaReferrerBlock devuelve null para JSON que no tiene la forma del bloque', () => {
  assert.equal(parseMetaReferrerBlock('{"placement":"cta_hero"}'), null);
  assert.equal(parseMetaReferrerBlock('{"source":"not-an-object"}'), null);
  assert.equal(parseMetaReferrerBlock('[1,2,3]'), null);
});

test('decryptMetaReferrerBlock hace el roundtrip de su propia convención AES-256-GCM', () => {
  const block = encryptLikeMeta(
    {
      campaign_group_id: 'camp-1',
      campaign_id: 'adset-1',
      adgroup_id: 'ad-1',
      is_instagram: true,
    },
    KEY_HEX,
  );

  const result = decryptMetaReferrerBlock(block, KEY_HEX);

  assert.deepEqual(result, { campaignId: 'camp-1', adsetId: 'adset-1', adId: 'ad-1' });
});

test('decryptMetaReferrerBlock falla cerrado con la clave equivocada', () => {
  const block = encryptLikeMeta({ campaign_group_id: 'camp-1' }, KEY_HEX);
  const wrongKey = randomBytes(32).toString('hex');

  assert.equal(decryptMetaReferrerBlock(block, wrongKey), null);
});

test('decryptMetaReferrerBlock falla cerrado con un tag manipulado', () => {
  const block = encryptLikeMeta({ campaign_group_id: 'camp-1' }, KEY_HEX);
  const tampered = Buffer.from(block.data, 'hex');
  tampered[tampered.length - 1] ^= 0xff; // flips the last byte of the GCM tag
  assert.equal(decryptMetaReferrerBlock({ ...block, data: tampered.toString('hex') }, KEY_HEX), null);
});

test('decryptMetaReferrerBlock falla cerrado con una clave que no son 32 bytes', () => {
  const block = encryptLikeMeta({ campaign_group_id: 'camp-1' }, KEY_HEX);
  assert.equal(decryptMetaReferrerBlock(block, 'ab'), null);
});

test('decryptMetaReferrerBlock falla cerrado con hex ilegible', () => {
  assert.equal(decryptMetaReferrerBlock({ data: 'not-hex!!', nonce: 'cd' }, KEY_HEX), null);
});
