import { describe, it } from 'node:test';
import assert from 'node:assert';
import { generatePairingCode, generateAuthToken, generateDeviceId } from '../src/utils/token.js';

describe('Token utilities', () => {
  it('generates pairing code in TV-XXXX format', () => {
    const code = generatePairingCode();
    assert.match(code, /^TV-\d{4}$/);
  });

  it('generates unique pairing codes', () => {
    const codes = new Set(Array.from({ length: 20 }, () => generatePairingCode()));
    assert.equal(codes.size, 20);
  });

  it('generates 64-char hex auth token', () => {
    const token = generateAuthToken();
    assert.equal(token.length, 64);
    assert.match(token, /^[0-9a-f]+$/);
  });

  it('generates device IDs with correct prefix', () => {
    assert.match(generateDeviceId('tv'), /^TV-/);
    assert.match(generateDeviceId('phone'), /^PH-/);
  });
});
