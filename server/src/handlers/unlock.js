import db from '../db.js';
import { deviceSockets } from './state.js';

export function handleUnlock(socket, data) {
  const { durationMinutes, authToken } = data;
  const minutes = durationMinutes || 5;

  const device = db.prepare('SELECT id, paired_with FROM devices WHERE auth_token = ?').get(authToken);
  if (!device) {
    socket.emit('error', { message: 'Invalid auth token' });
    return;
  }

  // 创建解锁记录
  const expiresAt = new Date(Date.now() + minutes * 60000).toISOString();
  db.prepare('INSERT OR REPLACE INTO unlocks (device_id, expires_at) VALUES (?, ?)')
    .run(device.paired_with, expiresAt);

  // 通知电视解锁
  const pairedSocket = deviceSockets.get(device.paired_with);
  if (pairedSocket) {
    pairedSocket.emit('unlock', { durationMinutes: minutes, expiresAt });
  }

  // 到期自动恢复锁定
  setTimeout(async () => {
    db.prepare('DELETE FROM unlocks WHERE device_id = ?').run(device.paired_with);
    db.prepare('UPDATE states SET state = ? WHERE device_id = ?').run('closed', device.paired_with);
    const tvSocket = deviceSockets.get(device.paired_with);
    if (tvSocket) {
      tvSocket.emit('state_update', { state: 'closed' });
    }
    // 通知手机
    if (pairedSocket) {
      socket.emit('tv_status', { status: 'locked', deviceId: device.paired_with });
    }
  }, minutes * 60000);

  socket.emit('unlock_started', { expiresAt });
}
