import db from '../db.js';

// 存储 socket 连接映射: deviceId -> socket
export const deviceSockets = new Map();

export function handleSetState(socket, data) {
  const { state, authToken } = data;
  if (!['open', 'closed'].includes(state)) {
    socket.emit('error', { message: 'Invalid state' });
    return;
  }

  const device = db.prepare('SELECT id FROM devices WHERE auth_token = ?').get(authToken);
  if (!device) {
    socket.emit('error', { message: 'Invalid auth token' });
    return;
  }

  // 更新状态
  db.prepare('UPDATE states SET state = ?, updated_at = datetime(\'now\') WHERE device_id = ?')
    .run(state, device.id);

  // 找到配对的设备并推送
  const paired = db.prepare('SELECT paired_with FROM devices WHERE id = ?').get(device.id);
  if (paired && paired.paired_with) {
    const pairedSocket = deviceSockets.get(paired.paired_with);
    if (pairedSocket) {
      pairedSocket.emit('state_update', { state, from: device.id });
    }
  }

  socket.emit('state_updated', { state });
}

export function handleGetState(socket, data) {
  const { authToken } = data;
  const device = db.prepare('SELECT id, paired_with FROM devices WHERE auth_token = ?').get(authToken);
  if (!device) {
    socket.emit('error', { message: 'Invalid auth token' });
    return;
  }

  const state = db.prepare('SELECT state FROM states WHERE device_id = ?').get(device.id);
  socket.emit('current_state', { state: state ? state.state : 'open' });
}

export function handleStatusReport(socket, data) {
  const { status, authToken } = data;
  const device = db.prepare('SELECT id, paired_with FROM devices WHERE auth_token = ?').get(authToken);
  if (!device) {
    socket.emit('error', { message: 'Invalid auth token' });
    return;
  }

  // 转发状态给配对的手机
  if (device.paired_with) {
    const pairedSocket = deviceSockets.get(device.paired_with);
    if (pairedSocket) {
      pairedSocket.emit('tv_status', { status, deviceId: device.id });
    }
  }
}
