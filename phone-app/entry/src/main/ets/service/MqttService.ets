import webSocket from '@ohos.net.webSocket';
import { TvState, TvStatus } from '../model/Types';

// 简单的 MQTT over WebSocket 客户端
// 连接 EMQX 免费公共 Broker: broker-cn.emqx.io
class MqttEncoder {
  // MQTT 剩余长度编码（单字节，适用于 <128 字节的包）
  static encodeUTF8(str: string): ArrayBuffer {
    const encoder = new TextEncoder();
    return encoder.encode(str).buffer;
  }

  // 构建 CONNECT 包
  static buildConnect(clientId: string): ArrayBuffer {
    const cidBytes = new TextEncoder().encode(clientId);
    const protocolName = new Uint8Array([0x00, 0x04, 0x4D, 0x51, 0x54, 0x54]); // "MQTT"
    const fixedHeader = 0x10;
    // 可变头 + 载荷长度：协议名(6) + 协议级别(1) + 标志(1) + keepalive(2) + 客户端ID(2+len)
    const remainingLen = 6 + 1 + 1 + 2 + 2 + cidBytes.length;
    const buf = new Uint8Array(2 + remainingLen); // 固定头(2) + 剩余部分
    buf[0] = fixedHeader;
    buf[1] = remainingLen;
    let offset = 2;
    // 协议名
    buf.set(protocolName, offset); offset += 6;
    // 协议级别 (MQTT 3.1.1)
    buf[offset++] = 0x04;
    // 连接标志 (Clean Session)
    buf[offset++] = 0x02;
    // Keep Alive (60秒)
    buf[offset++] = 0x00;
    buf[offset++] = 0x3C;
    // 客户端 ID (UTF-8 长度前缀)
    buf[offset++] = (cidBytes.length >> 8) & 0xFF;
    buf[offset++] = cidBytes.length & 0xFF;
    buf.set(cidBytes, offset);
    return buf.buffer as ArrayBuffer;
  }

  // 构建 PUBLISH 包 (QoS 0)
  static buildPublish(topic: string, payload: string): ArrayBuffer {
    const topicBytes = new TextEncoder().encode(topic);
    const payloadBytes = new TextEncoder().encode(payload);
    const remainingLen = 2 + topicBytes.length + payloadBytes.length;
    const buf = new Uint8Array(2 + remainingLen);
    buf[0] = 0x30; // PUBLISH QoS 0
    buf[1] = remainingLen;
    let offset = 2;
    buf[offset++] = (topicBytes.length >> 8) & 0xFF;
    buf[offset++] = topicBytes.length & 0xFF;
    buf.set(topicBytes, offset); offset += topicBytes.length;
    buf.set(payloadBytes, offset);
    return buf.buffer as ArrayBuffer;
  }

  // 构建 SUBSCRIBE 包
  static buildSubscribe(topic: string): ArrayBuffer {
    const topicBytes = new TextEncoder().encode(topic);
    const remainingLen = 2 + 2 + topicBytes.length + 1;
    const buf = new Uint8Array(2 + remainingLen);
    buf[0] = 0x82; // SUBSCRIBE
    buf[1] = remainingLen;
    let offset = 2;
    // 包标识符
    buf[offset++] = 0x00;
    buf[offset++] = 0x01;
    // 主题过滤器
    buf[offset++] = (topicBytes.length >> 8) & 0xFF;
    buf[offset++] = topicBytes.length & 0xFF;
    buf.set(topicBytes, offset); offset += topicBytes.length;
    // QoS
    buf[offset] = 0x01;
    return buf.buffer as ArrayBuffer;
  }
}

export class MqttService {
  private ws: webSocket.WebSocket | null = null;
  private brokerUrl: string = 'ws://broker-cn.emqx.io:8083/mqtt';
  private deviceId: string = '';
  private clientId: string = '';
  private reconnectTimer: number | null = null;

  // 回调
  onStatusUpdate: ((status: TvStatus) => void) | null = null;
  onConnectionChange: ((connected: boolean) => void) | null = null;
  onError: ((msg: string) => void) | null = null;

  // 主题
  private get stateTopic(): string { return `tv/${this.deviceId}/state`; }
  private get statusTopic(): string { return `tv/${this.deviceId}/status`; }
  private get unlockTopic(): string { return `tv/${this.deviceId}/unlock`; }

  constructor(deviceId: string) {
    this.deviceId = deviceId;
    this.clientId = 'PH_' + deviceId + '_' + Date.now();
  }

  connect() {
    if (this.ws) return;

    this.ws = webSocket.createWebSocket();

    this.ws.on('open', () => {
      // 发送 MQTT CONNECT 包
      const connectPacket = MqttEncoder.buildConnect(this.clientId);
      this.ws?.send(connectPacket, (err) => {
        if (err) {
          this.onError?.('MQTT连接失败');
          this.scheduleReconnect();
        }
      });
    });

    this.ws.on('message', (data: string | ArrayBuffer) => {
      if (data instanceof ArrayBuffer) {
        this.handleMqttPacket(data);
      }
    });

    this.ws.on('close', () => {
      this.onConnectionChange?.(false);
      this.scheduleReconnect();
    });

    this.ws.on('error', () => {
      this.scheduleReconnect();
    });

    this.ws.connect(this.brokerUrl, (err) => {
      if (err) {
        this.onConnectionChange?.(false);
        this.scheduleReconnect();
      }
    });
  }

  private handleMqttPacket(data: ArrayBuffer) {
    const view = new Uint8Array(data);
    if (view.length < 2) return;

    const packetType = view[0] >> 4;
    // const remainingLen = view[1]; // 单字节剩余长度

    switch (packetType) {
      case 0x02: // CONNACK
        // 连接成功，订阅状态主题
        this.onConnectionChange?.(true);
        const subPacket = MqttEncoder.buildSubscribe(this.statusTopic);
        this.ws?.send(subPacket, () => {});
        break;

      case 0x03: // PUBLISH
        this.handlePublish(view);
        break;

      case 0x0D: // SUBACK
        // 订阅成功，可以查询当前状态（通过发布一条空消息，或不做特殊处理）
        break;
    }
  }

  private handlePublish(view: Uint8Array) {
    // 解析 PUBLISH 包
    let pos = 2; // 跳过固定头
    // 主题长度
    const topicLen = (view[pos] << 8) | view[pos + 1];
    pos += 2;
    // 主题
    // const topic = new TextDecoder().decode(view.slice(pos, pos + topicLen));
    pos += topicLen;
    // 载荷
    const payload = new TextDecoder().decode(view.slice(pos));
    const trimmed = payload.trim();

    if (trimmed === 'locked') {
      this.onStatusUpdate?.(TvStatus.LOCKED);
    } else if (trimmed === 'unlocked' || trimmed === 'online') {
      this.onStatusUpdate?.(TvStatus.UNLOCKED);
    }
  }

  // 设置电视状态（开/关）
  setState(state: TvState) {
    const packet = MqttEncoder.buildPublish(this.stateTopic, state);
    this.ws?.send(packet, (err) => {
      if (err) this.onError?.('发送指令失败');
    });
  }

  // 临时解锁
  requestUnlock(durationMinutes: number = 5) {
    const packet = MqttEncoder.buildPublish(this.unlockTopic, String(durationMinutes));
    this.ws?.send(packet, (err) => {
      if (err) this.onError?.('解锁指令发送失败');
    });
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
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
  }
}
