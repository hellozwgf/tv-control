# 云服务器部署指南（免费方案）

使用 Zeabur（国产平台）免费部署，支持 WebSocket，无需信用卡。

---

## Zeabur 免费部署

### 1. 打开 Zeabur

访问 [zeabur.com](https://zeabur.com)，用 GitHub 账号登录。

### 2. 创建项目

1. 点击 **创建项目**
2. 输入项目名：`tv-control`
3. 点击 **创建**

### 3. 添加服务

1. 在项目中点击 **创建新服务**
2. 选择 **GitHub**（会跳转授权，选你的 `tv-control` 仓库）
3. 选择仓库后会自动部署

### 4. 配置服务

部署完成后，在服务设置中配置：

| 配置项 | 值 |
|--------|-----|
| 构建命令 | `cd server && npm install` |
| 启动命令 | `cd server && node src/index.js` |
| 端口 | `3000` |

### 5. 验证

服务启动后，Zeabur 会提供一个域名，如 `https://tv-control.zeabur.app`

在浏览器打开 `https://tv-control.zeabur.app/health`

应该看到：`{"status":"ok"}` ✅

---

## 更新客户端中的服务器地址

部署完成后，更新两个客户端代码中的服务器地址。

### 电视 APK（MainActivity.kt）

文件：`tv-app/app/src/main/java/com/tvcontrol/MainActivity.kt`

```kotlin
private val serverUrl = "https://tv-control.zeabur.app"
```

### 手机鸿蒙 App（WebSocketService.ets）

文件：`phone-app/entry/src/main/ets/service/WebSocketService.ets`

```typescript
private serverUrl: string = 'wss://tv-control.zeabur.app';
```
