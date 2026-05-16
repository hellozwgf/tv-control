import UIAbility from '@ohos.app.ability.UIAbility';
import window from '@ohos.window';

export default class EntryAbility extends UIAbility {
  onCreate(want, launchParam) {
    // 应用初始化
  }

  onDestroy() {
    // 清理
  }

  onWindowStageCreate(windowStage: window.WindowStage) {
    windowStage.loadContent('pages/Index', (err) => {
      if (err) {
        console.error('Failed to load content', err);
        return;
      }
    });
  }

  onWindowStageDestroy() {
    // 窗口销毁
  }

  onForeground() {
    // 切换到前台
  }

  onBackground() {
    // 切换到后台
  }
}
