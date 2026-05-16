# 云服务器部署指南（免费方案）

使用 Render.com 免费套餐部署，支持 WebSocket，零成本。

---

## Render.com 免费部署

### 1. 准备工作

- 注册一个 [GitHub](https://github.com) 账号（如果还没有）
- 注册一个 [Render.com](https://render.com) 账号（用 GitHub 登录即可）

### 2. 将代码推送到 GitHub

```bash
# 在项目目录下初始化 git
cd d:\电视控制APP
git init
git add .
git commit -m "init: TV control project"
```

然后去 [github.com](https://github.com) 新建一个仓库（取名叫 `tv-control`），执行：

```bash
git remote add origin https://github.com/你的用户名/tv-control.git
git push -u origin main
```

### 3. 在 Render 上部署

1. 登录 [Render Dashboard](https://dashboard.render.com)
2. 点击 **New +** → **Web Service**
3. 选择你刚推的 GitHub 仓库 `tv-control`
4. 填写配置：

| 字段 | 值 |
|------|-----|
| Name | `tv-control` |
| Root Directory | `server` |
| Runtime | `Node` |
| Build Command | `npm install` |
| Start Command | `node src/index.js` |
| Instance Type | **Free** |

5. 点击 **Create Web Service**
6. 等待 2-3 分钟部署完成

部署完成后你会得到一个地址：`https://tv-control.onrender.com`

### 4. 验证服务是否正常

在浏览器打开 `https://tv-control.onrender.com/health`

应该看到：`{"status":"ok"}` ✅

### 5. 关于免费套餐的注意事项

Render 免费套餐有以下特性：
- **服务空闲 15 分钟后自动休眠** — 但电视 App 会保持 WebSocket 持续连接，所以不会休眠
- **有持久化存储** — SQLite 数据文件会保留
- **自动提供 SSL/HTTPS** — WebSocket 地址自动为 `wss://`
- **每月 750 小时** — 24 小时运行，足够用

---

## 更新客户端中的服务器地址

部署完成后，需要更新两个客户端代码中的服务器地址。

### 电视 APK（MainActivity.kt）

文件：`tv-app/app/src/main/java/com/tvcontrol/MainActivity.kt`

找到第 19 行：
```kotlin
private val serverUrl = "http://YOUR_SERVER_IP:3000"
```
改为：
```kotlin
private val serverUrl = "https://tv-control.onrender.com"
```

### 手机鸿蒙 App（WebSocketService.ets）

文件：`phone-app/entry/src/main/ets/service/WebSocketService.ets`

找到第 9 行：
```typescript
private serverUrl: string = 'ws://YOUR_SERVER_IP:3000';
```
改为：
```typescript
private serverUrl: string = 'wss://tv-control.onrender.com';
```

注意：Render 自动提供 SSL，所以 WebSocket 地址用 `wss://` 而非 `ws://`

---

## 常见问题

**Q: 服务休眠了怎么办？**
A: 首次访问需要 5-10 秒启动时间。之后 TV App 的长连接会保持服务活跃。你也可以用 [UptimeRobot](https://uptimerobot.com) 免费服务每 10 分钟 ping 一次 `/health` 来防止休眠。

**Q: 数据会丢失吗？**
A: 不会。SQLite 数据库文件存在 Render 的持久化磁盘上，部署重启后数据保留。

**Q: 怎么看日志？**
A: 在 Render Dashboard → 你的服务 → Logs 选项卡。
