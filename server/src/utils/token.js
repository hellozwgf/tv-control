import { randomBytes } from 'crypto';

export function generatePairingCode() {
  // 生成 4 位数字配对码
  const code = String(Math.floor(1000 + Math.random() * 9000));
  return `TV-${code}`;
}

export function generateAuthToken() {
  return randomBytes(32).toString('hex');
}

export function generateDeviceId(type) {
  const prefix = type === 'tv' ? 'TV' : 'PH';
  const id = randomBytes(4).toString('hex');
  return `${prefix}-${id}`;
}
