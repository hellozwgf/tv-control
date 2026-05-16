import webSocket from '@ohos.net.webSocket';
import { TvState, TvStatus, ServerMessage } from '../model/Types';

export class WebSocketService {
  private ws: webSocket.WebSocket | null = null;
  private serverUrl: string = 'ws://YOUR_SERVER_IP:3000';
  private authToken: string = '';
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;

  // 回调
  onStatusUpdate: ((status: TvStatus) => void) | null = null;
  onConnectionChange: ((connected: boolean) => void) | null = null;

  constructor(authToken: string) {
    this.authToken = authToken;
  }

  connect() {
    if (this.ws) return;

    this.ws = webSocket.createWebSocket();
    this.ws.on('open', () => {
      this.onConnectionChange?.(true);
      this.startHeartbeat();
    });
    this.ws.on('message', (data: string) => {
      this.handleMessage(data);
    });
    this.ws.on('close', () => {
      this.onConnectionChange?.(false);
      this.scheduleReconnect();
    });
    this.ws.on('error', () => {
      this.scheduleReconnect();
    });

    this.ws.connect(this.serverUrl, (err) => {
      if (err) this.scheduleReconnect();
    });
  }

  private handleMessage(data: string) {
    const msg: ServerMessage = JSON.parse(data);
    if (msg.type === 'current_state') {
      this.onStatusUpdate?.(
        msg.state === 'closed' ? TvStatus.LOCKED : TvStatus.UNLOCKED
      );
    } else if (msg.type === 'tv_status') {
      this.onStatusUpdate?.(msg.status as TvStatus);
    }
  }

  setState(state: TvState) {
    this.send({
      type: 'set_state',
      state: state,
      authToken: this.authToken
    });
  }

  requestUnlock(durationMinutes: number = 5) {
    this.send({
      type: 'unlock',
      durationMinutes: durationMinutes,
      authToken: this.authToken
    });
  }

  private send(data: object) {
    this.ws?.send(JSON.stringify(data), (err) => {
      if (err) console.error('Send failed', err);
    });
  }

  private startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: 'ping' });
    }, 30000);
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.disconnect();
      this.connect();
    }, 5000);
  }

  disconnect() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
  }
}
