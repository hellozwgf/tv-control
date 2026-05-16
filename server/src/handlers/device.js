import db from '../db.js';
import { generatePairingCode, generateAuthToken, generateDeviceId } from '../utils/token.js';
import { deviceSockets } from './state.js';

// 临时存储配对码 -> device_id 的映射
const pendingPairings = new Map();

export function handleRegister(socket, data) {
  const { type } = data; // 'tv' | 'phone'
  if (!['tv', 'phone'].includes(type)) {
    socket.emit('error', { message: 'Invalid device type' });
    return;
  }

  const deviceId = generateDeviceId(type);
  const authToken = generateAuthToken();

  const stmt = db.prepare('INSERT INTO devices (id, type, auth_token) VALUES (?, ?, ?)');
  stmt.run(deviceId, type, authToken);

  // 初始化状态为 open
  const stateStmt = db.prepare('INSERT OR IGNORE INTO states (device_id, state) VALUES (?, ?)');
  stateStmt.run(deviceId, 'open');

  // 将 socket 关联到 deviceSockets，后续 set_state 才能找到设备
  deviceSockets.set(deviceId, socket);

  if (type === 'tv') {
    const pairingCode = generatePairingCode();
    pendingPairings.set(pairingCode, deviceId);
    // 5 分钟后清除未使用的配对码
    setTimeout(() => pendingPairings.delete(pairingCode), 300000);

    socket.emit('registered', { deviceId, authToken, pairingCode });
  } else {
    socket.emit('registered', { deviceId, authToken });
  }
}

export function handlePair(socket, data) {
  const { pairingCode, phoneToken } = data;
  const tvDeviceId = pendingPairings.get(pairingCode);

  if (!tvDeviceId) {
    socket.emit('error', { message: 'Invalid or expired pairing code' });
    return;
  }

  // 验证手机端的 token
  const phoneStmt = db.prepare('SELECT id FROM devices WHERE auth_token = ? AND type = ?');
  const phone = phoneStmt.get(phoneToken, 'phone');
  if (!phone) {
    socket.emit('error', { message: 'Phone device not registered' });
    return;
  }

  // 绑定
  const pairStmt = db.prepare('UPDATE devices SET paired_with = ? WHERE id = ?');
  pairStmt.run(phone.id, tvDeviceId);
  pairStmt.run(tvDeviceId, phone.id);

  pendingPairings.delete(pairingCode);
  socket.emit('paired', { tvDeviceId });
}
