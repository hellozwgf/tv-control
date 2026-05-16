export enum TvState {
  OPEN = 'open',
  CLOSED = 'closed'
}

export enum TvStatus {
  ONLINE = 'online',
  OFFLINE = 'offline',
  LOCKED = 'locked',
  UNLOCKED = 'unlocked'
}

export interface DeviceInfo {
  deviceId: string;
}
