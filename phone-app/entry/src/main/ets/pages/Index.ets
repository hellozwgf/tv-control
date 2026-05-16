import { MqttService } from '../service/MqttService';
import { TvState, TvStatus } from '../model/Types';
import router from '@ohos.router';

@Entry
@Component
struct Index {
  @State tvStatus: TvStatus = TvStatus.OFFLINE;
  @State isLocked: boolean = false;
  @State isConnected: boolean = false;
  private mqttService: MqttService | null = null;

  aboutToAppear() {
    const deviceId = AppStorage.get<string>('deviceId') || '';
    if (!deviceId) {
      router.replaceUrl({ url: 'pages/Pairing' });
      return;
    }

    this.mqttService = new MqttService(deviceId);
    this.mqttService.onStatusUpdate = (status) => {
      this.tvStatus = status;
      this.isLocked = (status === TvStatus.LOCKED);
    };
    this.mqttService.onConnectionChange = (connected) => {
      this.isConnected = connected;
    };
    this.mqttService.connect();
  }

  build() {
    Stack() {
      Column() {
        Row() {
          Circle()
            .width(12).height(12)
            .fill(this.isConnected ? Color.Green : Color.Red)
          Text(this.isConnected ? '已连接' : '未连接')
            .fontSize(14).fontColor(Color.Gray).margin({ left: 8 })
        }
        .width('100%').margin({ top: 20, left: 20 })

        Text('电视管控')
          .fontSize(32).fontWeight(FontWeight.Bold).margin({ top: 40 })
        Text(this.tvStatus === TvStatus.UNLOCKED ? '电视可正常观看' : '电视已暂停')
          .fontSize(16).fontColor(Color.Gray).margin({ top: 8 })

        Button(this.isLocked ? '打开电视' : '关闭电视')
          .width(200).height(200)
          .borderRadius(100)
          .backgroundColor(this.isLocked ? Color.Green : Color.Red)
          .fontSize(24).fontColor(Color.White)
          .margin({ top: 60 })
          .onClick(() => {
            if (this.isLocked) {
              this.mqttService?.setState(TvState.OPEN);
            } else {
              this.mqttService?.setState(TvState.CLOSED);
            }
          })

        if (this.isLocked) {
          Button('临时解锁 5 分钟')
            .width(200).height(48)
            .borderRadius(24)
            .backgroundColor(Color.Orange)
            .fontSize(16).fontColor(Color.White)
            .margin({ top: 24 })
            .onClick(() => {
              this.mqttService?.requestUnlock(5);
            })
        }
      }
      .width('100%').height('100%')
      .alignItems(HorizontalAlign.Center)
    }
    .width('100%').height('100%')
    .backgroundColor(Color.Black)
  }

  aboutToDisappear() {
    this.mqttService?.disconnect();
  }
}
