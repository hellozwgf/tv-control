import router from '@ohos.router';

@Entry
@Component
struct Pairing {
  @State pairingCode: string = '';
  @State statusMessage: string = '请输入电视上显示的配对码';

  build() {
    Column() {
      Text('设备配对')
        .fontSize(28).fontWeight(FontWeight.Bold).margin({ top: 60 })

      Text(this.statusMessage)
        .fontSize(16).fontColor(Color.Gray).margin({ top: 20 })

      TextInput({ placeholder: '例如 TV-4821' })
        .width(280).height(48)
        .margin({ top: 30 })
        .fontSize(20)
        .onChange((value) => { this.pairingCode = value; })
        .textAlign(TextAlign.Center)

      Button('确认配对')
        .width(200).height(48)
        .borderRadius(24)
        .backgroundColor(Color.Blue)
        .fontSize(18).fontColor(Color.White)
        .margin({ top: 30 })
        .onClick(() => {
          if (this.pairingCode.length < 6) {
            this.statusMessage = '请输入完整的配对码（如 TV-4821）';
            return;
          }
          // 保存设备ID，跳转到主页
          AppStorage.setOrCreate('deviceId', this.pairingCode);
          router.replaceUrl({ url: 'pages/Index' });
        })
    }
    .width('100%').height('100%')
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(Color.Black)
  }
}
