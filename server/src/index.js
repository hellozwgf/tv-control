import { createServer } from 'http';
import { Server } from 'socket.io';
import db, { initDB } from './db.js';
import { handleRegister, handlePair } from './handlers/device.js';
import { handleSetState, handleGetState, handleStatusReport, deviceSockets } from './handlers/state.js';
import { handleUnlock } from './handlers/unlock.js';

initDB();

const httpServer = createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  if (req.url === '/health') {
    res.writeHead(200);
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }
  res.writeHead(404);
  res.end('Not found');
});

const io = new Server(httpServer, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  pingInterval: 30000,
  pingTimeout: 60000
});

io.on('connection', (socket) => {
  console.log(`Client connected: ${socket.id}`);

  // 注册设备
  socket.on('register', (data) => handleRegister(socket, data));

  // 配对
  socket.on('pair', (data) => handlePair(socket, data));

  // 设置状态 (手机端)
  socket.on('set_state', (data) => handleSetState(socket, data));

  // 获取状态 (电视端开机查询)
  socket.on('get_state', (data) => handleGetState(socket, data));

  // 电视状态上报
  socket.on('status_report', (data) => handleStatusReport(socket, data));

  // 临时解锁
  socket.on('unlock', (data) => handleUnlock(socket, data));

  // 断开连接
  socket.on('disconnect', () => {
    console.log(`Client disconnected: ${socket.id}`);
    for (const [deviceId, s] of deviceSockets.entries()) {
      if (s === socket) {
        deviceSockets.delete(deviceId);
        break;
      }
    }
  });
});

const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, () => {
  console.log(`TV Control Server running on port ${PORT}`);
});
