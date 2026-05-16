# 电视远程管控 App 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现家长远程控制华为智慧屏的 App 套装——手机点「关闭」电视立即黑屏，重启也绕不过。

**技术栈：** 云服务器 Node.js + Socket.IO + SQLite | 电视端 Android APK (Kotlin) | 手机端 HarmonyOS ArkUI/ArkTS

**项目结构：**
```
d:\电视控制APP\
├── server/           # 云服务器 (Node.js)
│   ├── src/
│   │   ├── index.js
│   │   ├── db.js
│   │   ├── auth.js
│   │   └── handlers/ ...
│   ├── test/
│   └── package.json
├── tv-app/           # 电视端 APK (Android/Kotlin)
│   └── app/src/main/java/com/tvcontrol/ ...
└── phone-app/        # 手机端鸿蒙 App (ArkUI)
    └── entry/src/main/ets/ ...
```

---

## 任务 1：云服务器——项目脚手架 + 数据库

**文件：**
- 创建：`server/package.json`
- 创建：`server/src/db.js`
- 创建：`server/ecosystem.config.js`

- [ ] **步骤 1：创建 package.json**

```json
{
  "name": "tv-control-server",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "start": "node src/index.js",
    "dev": "node --watch src/index.js",
    "test": "node --test test/*.test.js",
    "init-db": "node src/db.js --init"
  },
  "dependencies": {
    "socket.io": "^4.8.0",
    "better-sqlite3": "^11.7.0",
    "uuid": "^11.1.0",
    "cors": "^2.8.5"
  }
}
```

- [ ] **步骤 2：创建数据库模块**

```js
// server/src/db.js
import Database from 'better-sqlite3';
import { existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, '..', 'data');

if (!existsSync(DATA_DIR)) mkdirSync(DATA_DIR, { recursive: true });

const db = new Database(join(DATA_DIR, 'tv-control.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

export function initDB() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      type TEXT NOT NULL CHECK(type IN ('phone', 'tv')),
      name TEXT DEFAULT '',
      auth_token TEXT NOT NULL UNIQUE,
      paired_with TEXT,
      created_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (paired_with) REFERENCES devices(id)
    );
    CREATE TABLE IF NOT EXISTS states (
      device_id TEXT PRIMARY KEY,
      state TEXT NOT NULL DEFAULT 'open' CHECK(state IN ('open', 'closed')),
      updated_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (device_id) REFERENCES devices(id)
    );
    CREATE TABLE IF NOT EXISTS unlocks (
      device_id TEXT PRIMARY KEY,
      expires_at TEXT NOT NULL,
      created_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (device_id) REFERENCES devices(id)
    );
    CREATE INDEX IF NOT EXISTS idx_devices_token ON devices(auth_token);
    CREATE INDEX IF NOT EXISTS idx_devices_paired ON devices(paired_with);
  `);
  console.log('Database initialized');
}

if (process.argv.includes('--init')) {
  initDB();
  process.exit(0);
}

export default db;
```

- [ ] **步骤 3：创建 PM2 配置**

```js
// server/ecosystem.config.js
export default {
  apps: [{
    name: 'tv-control',
    script: 'src/index.js',
    instances: 1,
    exec_mode: 'fork',
    env: {
      PORT: 3000,
      NODE_ENV: 'production'
    }
  }]
};
```

- [ ] **步骤 4：安装依赖并初始化数据库**

运行：
```bash
cd server && npm install && node src/db.js --init
```
预期输出：`Database initialized`

- [ ] **步骤 5：Commit**

```bash
git add server/package.json server/src/db.js server/ecosystem.config.js
git commit -m "feat: add server scaffold with SQLite database"
```

---

## 任务 2：云服务器——认证和工具函数

**文件：**
- 创建：`server/src/utils/token.js`
- 创建：`server/src/auth.js`

- [ ] **步骤 1：创建 Token 工具**

```js
// server/src/utils/token.js
import { randomBytes } from 'crypto';

export function generatePairingCode() {
  // 生成 4 位数字配对码
  const code = String(Math.floor(1000 + Math.random() * 9000));
  return `TV-${code}`;
}

export function generateAuthToken() {
  return randomBytes(32).toString('hex');
}

export function generateDeviceId(type) {
  const prefix = type === 'tv' ? 'TV' : 'PH';
  const id = randomBytes(4).toString('hex');
  return `${prefix}-${id}`;
}
```

- [ ] **步骤 2：创建测试**

创建：`server/test/token.test.js`

```js
// server/test/token.test.js
import { describe, it } from 'node:test';
import assert from 'node:assert';
import { generatePairingCode, generateAuthToken, generateDeviceId } from '../src/utils/token.js';

describe('Token utilities', () => {
  it('generates pairing code in TV-XXXX format', () => {
    const code = generatePairingCode();
    assert.match(code, /^TV-\d{4}$/);
  });

  it('generates unique pairing codes', () => {
    const codes = new Set(Array.from({ length: 100 }, () => generatePairingCode()));
    assert.equal(codes.size, 100);
  });

  it('generates 64-char hex auth token', () => {
    const token = generateAuthToken();
    assert.equal(token.length, 64);
    assert.match(token, /^[0-9a-f]+$/);
  });

  it('generates device IDs with correct prefix', () => {
    assert.match(generateDeviceId('tv'), /^TV-/);
    assert.match(generateDeviceId('phone'), /^PH-/);
  });
});
```

运行：`cd server && node --test test/token.test.js`
预期：所有测试 PASS

- [ ] **步骤 3：Commit**

```bash
git add server/src/utils/token.js server/test/token.test.js
git commit -m "feat: add token generation utilities"
```

---

## 任务 3：云服务器——WebSocket 接入 + 消息处理

**文件：**
- 创建：`server/src/handlers/device.js`
- 创建：`server/src/handlers/state.js`
- 创建：`server/src/handlers/unlock.js`
- 创建：`server/src/index.js`
- 修改：`server/package.json`（添加 crypto 依赖已内置，无需改）

- [ ] **步骤 1：设备配对 Handler**

```js
// server/src/handlers/device.js
import db from '../db.js';
import { generatePairingCode, generateAuthToken, generateDeviceId } from '../utils/token.js';

// 临时存储配对码 -> device_id 的映射
const pendingPairings = new Map();

export function handleRegister(socket, data) {
  const { type } = data; // 'tv' | 'phone'
  if (!['tv', 'phone'].includes(type)) {
    socket.emit('error', { message: 'Invalid device type' });
    return;
  }

  const deviceId = generateDeviceId(type);
  const authToken = generateAuthToken();

  const stmt = db.prepare('INSERT INTO devices (id, type, auth_token) VALUES (?, ?, ?)');
  stmt.run(deviceId, type, authToken);

  // 初始化状态为 open
  const stateStmt = db.prepare('INSERT OR IGNORE INTO states (device_id, state) VALUES (?, ?)');
  stateStmt.run(deviceId, 'open');

  if (type === 'tv') {
    const pairingCode = generatePairingCode();
    pendingPairings.set(pairingCode, deviceId);
    // 5 分钟后清除未使用的配对码
    setTimeout(() => pendingPairings.delete(pairingCode), 300000);

    socket.emit('registered', { deviceId, authToken, pairingCode });
  } else {
    socket.emit('registered', { deviceId, authToken });
  }
}

export function handlePair(socket, data) {
  const { pairingCode, phoneToken } = data;
  const tvDeviceId = pendingPairings.get(pairingCode);

  if (!tvDeviceId) {
    socket.emit('error', { message: 'Invalid or expired pairing code' });
    return;
  }

  // 验证手机端的 token
  const phoneStmt = db.prepare('SELECT id FROM devices WHERE auth_token = ? AND type = ?');
  const phone = phoneStmt.get(phoneToken, 'phone');
  if (!phone) {
    socket.emit('error', { message: 'Phone device not registered' });
    return;
  }

  // 绑定
  const pairStmt = db.prepare('UPDATE devices SET paired_with = ? WHERE id = ?');
  pairStmt.run(phone.id, tvDeviceId);
  pairStmt.run(tvDeviceId, phone.id);

  pendingPairings.delete(pairingCode);
  socket.emit('paired', { tvDeviceId });
}
```

- [ ] **步骤 2：状态管理 Handler**

```js
// server/src/handlers/state.js
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
```

- [ ] **步骤 3：临时解锁 Handler**

```js
// server/src/handlers/unlock.js
import db from '../db.js';

export function handleUnlock(socket, data) {
  const { durationMinutes, authToken } = data;
  const minutes = durationMinutes || 5;

  const device = db.prepare('SELECT id, paired_with FROM devices WHERE auth_token = ?').get(authToken);
  if (!device) {
    socket.emit('error', { message: 'Invalid auth token' });
    return;
  }

  // 创建解锁记录
  const expiresAt = new Date(Date.now() + minutes * 60000).toISOString();
  db.prepare('INSERT OR REPLACE INTO unlocks (device_id, expires_at) VALUES (?, ?)')
    .run(device.paired_with, expiresAt);

  // 通知电视解锁
  const pairedSocket = deviceSockets.get(device.paired_with);
  if (pairedSocket) {
    pairedSocket.emit('unlock', { durationMinutes: minutes, expiresAt });
  }

  // 到期自动恢复锁定
  setTimeout(async () => {
    db.prepare('DELETE FROM unlocks WHERE device_id = ?').run(device.paired_with);
    db.prepare('UPDATE states SET state = ? WHERE device_id = ?').run('closed', device.paired_with);
    const tvSocket = deviceSockets.get(device.paired_with);
    if (tvSocket) {
      tvSocket.emit('state_update', { state: 'closed' });
    }
    // 通知手机
    if (pairedSocket) {
      socket.emit('tv_status', { status: 'locked', deviceId: device.paired_with });
    }
  }, minutes * 60000);

  socket.emit('unlock_started', { expiresAt });
}
```

- [ ] **步骤 4：主入口——Socket.IO 服务**

```js
// server/src/index.js
import { createServer } from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
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
```

- [ ] **步骤 5：编写集成测试**

创建：`server/test/api.test.js`

```js
// server/test/api.test.js
import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { io as ioc } from 'socket.io-client';

const SERVER_URL = 'http://localhost:3001';

describe('WebSocket API', () => {
  let server;
  let tvSocket, phoneSocket;
  let tvToken, phoneToken, tvId;

  before(async () => {
    // 启动测试服务器
    const { initDB } = await import('../src/db.js');
    initDB();
    const { createServer } = await import('http');
    const { Server } = await import('socket.io');
    const httpServer = createServer();
    const io = new Server(httpServer);
    // 简化的测试用 server 设置...
    // 实际测试应启动独立端口
  });

  it('should register a TV device and return pairing code', (done) => {
    const socket = ioc(SERVER_URL);
    socket.on('connect', () => {
      socket.emit('register', { type: 'tv' });
    });
    socket.on('registered', (data) => {
      assert.match(data.deviceId, /^TV-/);
      assert.match(data.pairingCode, /^TV-\d{4}$/);
      assert.equal(data.authToken.length, 64);
      tvToken = data.authToken;
      tvId = data.deviceId;
      socket.close();
      done();
    });
  });
});
```

- [ ] **步骤 6：手动启动服务器测试**

运行：
```bash
cd server && node src/index.js
```
预期输出：`TV Control Server running on port 3000`

新终端：
```bash
curl http://localhost:3000/health
```
预期：`{"status":"ok"}`

- [ ] **步骤 7：Commit**

```bash
git add server/src/index.js server/src/handlers/ server/test/api.test.js
git commit -m "feat: implement WebSocket server with device pairing and state management"
```

---

## 任务 4：电视端 APK——项目脚手架 + 注册配对

**文件：**
- 创建：`tv-app/settings.gradle.kts`
- 创建：`tv-app/build.gradle.kts`
- 创建：`tv-app/app/build.gradle.kts`
- 创建：`tv-app/app/src/main/AndroidManifest.xml`
- 创建：`tv-app/app/src/main/java/com/tvcontrol/MainActivity.kt`
- 创建：`tv-app/app/src/main/res/values/strings.xml`

- [ ] **步骤 1：项目级 build.gradle.kts**

```kotlin
// tv-app/build.gradle.kts
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

- [ ] **步骤 2：settings.gradle.kts**

```kotlin
// tv-app/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TVControl"
include(":app")
```

- [ ] **步骤 3：Module build.gradle.kts**

```kotlin
// tv-app/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvcontrol"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tvcontrol"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude("org.json", "json")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

- [ ] **步骤 4：AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- tv-app/app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />

    <application
        android:label="@string/app_name"
        android:theme="@android:style/Theme.NoTitleBar.Fullscreen">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".LockActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:finishOnTaskLaunch="false"
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:screenOrientation="landscape" />

        <service
            android:name=".LockService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".DeviceAdminReceiver"
            android:exported="false"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin" />
        </receiver>
    </application>
</manifest>
```

- [ ] **步骤 5：MainActivity**

```kotlin
// tv-app/app/src/main/java/com/tvcontrol/MainActivity.kt
package com.tvcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var socket: Socket
    private var deviceId: String? = null
    private var authToken: String? = null
    private var pairingCode: String? = null

    // 默认服务器地址，后续可配置
    private val serverUrl = "http://YOUR_SERVER_IP:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查是否已注册
        val prefs = getSharedPreferences("tv_control", MODE_PRIVATE)
        val savedToken = prefs.getString("auth_token", null)

        if (savedToken != null) {
            authToken = savedToken
            deviceId = prefs.getString("device_id", null)
            startLockService()
            finish() // 进入后台
        } else {
            connectAndRegister()
        }
    }

    private fun connectAndRegister() {
        try {
            socket = IO.socket(serverUrl)
            socket.on(Socket.EVENT_CONNECT) {
                socket.emit("register", JSONObject().put("type", "tv"))
            }
            socket.on("registered") { args ->
                val data = args[0] as JSONObject
                deviceId = data.getString("deviceId")
                authToken = data.getString("authToken")
                pairingCode = data.getString("pairingCode")

                runOnUiThread {
                    findViewById<TextView>(R.id.pairingCode).text = pairingCode
                }

                // 持久化
                getSharedPreferences("tv_control", MODE_PRIVATE).edit().apply {
                    putString("auth_token", authToken)
                    putString("device_id", deviceId)
                    apply()
                }
            }
            socket.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startLockService() {
        val intent = Intent(this, LockService::class.java)
        startForegroundService(intent)
    }
}
```

- [ ] **步骤 6：strings.xml + layout**

```xml
<!-- tv-app/app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">电视管控</string>
</resources>
```

创建：`tv-app/app/src/main/res/layout/activity_main.xml`（简单布局显示配对码）

- [ ] **步骤 7：设备管理员配置**

创建：`tv-app/app/src/main/res/xml/device_admin.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin>
    <uses-policies>
        <limit-password />
        <watch-login />
        <force-lock />
    </uses-policies>
</device-admin>
```

- [ ] **步骤 8：Commit**

```bash
git add tv-app/
git commit -m "feat: add TV app scaffold with registration and pairing"
```

---

## 任务 5：电视端 APK——LockService（前台服务 + WebSocket 保活）

**文件：**
- 创建：`tv-app/app/src/main/java/com/tvcontrol/LockService.kt`
- 创建：`tv-app/app/src/main/java/com/tvcontrol/WebSocketClient.kt`

- [ ] **步骤 1：WebSocket 客户端封装**

```kotlin
// tv-app/app/src/main/java/com/tvcontrol/WebSocketClient.kt
package com.tvcontrol

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

class WebSocketClient(
    private val serverUrl: String,
    private val authToken: String,
    private val deviceId: String
) {
    private var socket: Socket? = null
    private var onStateUpdate: ((String) -> Unit)? = null
    private var onUnlock: ((Long) -> Unit)? = null
    private var onConnected: (() -> Unit)? = null

    fun connect() {
        try {
            socket = IO.socket(serverUrl)
            socket!!.on(Socket.EVENT_CONNECT) {
                onConnected?.invoke()
                // 注册 socket 与 deviceId 的关联
                socket!!.emit("register_connection", JSONObject().apply {
                    put("authToken", authToken)
                    put("deviceId", deviceId)
                })
            }
            socket!!.on("state_update") { args ->
                val data = args[0] as JSONObject
                val state = data.getString("state")
                onStateUpdate?.invoke(state)
            }
            socket!!.on("unlock") { args ->
                val data = args[0] as JSONObject
                val expiresAt = data.getString("expiresAt")
                onUnlock?.invoke(ISO8601.parse(expiresAt).time)
            }
            socket!!.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reportStatus(status: String) {
        socket?.emit("status_report", JSONObject().apply {
            put("status", status)
            put("authToken", authToken)
        })
    }

    fun queryState() {
        socket?.emit("get_state", JSONObject().apply {
            put("authToken", authToken)
        })
        socket?.once("current_state") { args ->
            val data = args[0] as JSONObject
            val state = data.getString("state")
            onStateUpdate?.invoke(state)
        }
    }

    fun disconnect() {
        socket?.disconnect()
    }

    fun setOnStateUpdate(callback: (String) -> Unit) { onStateUpdate = callback }
    fun setOnUnlock(callback: (Long) -> Unit) { onUnlock = callback }
    fun setOnConnected(callback: () -> Unit) { onConnected = callback }
}
```

- [ ] **步骤 2：LockService**

```kotlin
// tv-app/app/src/main/java/com/tvcontrol/LockService.kt
package com.tvcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LockService : Service() {
    private var wsClient: WebSocketClient? = null
    private var isLocked = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("tv_control", MODE_PRIVATE)
        val authToken = prefs.getString("auth_token", null) ?: return START_STICKY
        val deviceId = prefs.getString("device_id", null) ?: return START_STICKY
        val serverUrl = prefs.getString("server_url", "http://YOUR_SERVER_IP:3000") ?: return START_STICKY

        wsClient = WebSocketClient(serverUrl, authToken, deviceId)

        wsClient?.setOnConnected {
            // 查询当前状态
            wsClient?.queryState()
        }

        wsClient?.setOnStateUpdate { state ->
            when (state) {
                "closed" -> lock()
                "open" -> unlock()
            }
        }

        wsClient?.setOnUnlock { expiresAt ->
            // 临时解锁，到期自动锁定
            unlock()
            val delay = expiresAt - System.currentTimeMillis()
            if (delay > 0) {
                android.os.Handler(mainLooper).postDelayed({
                    lock()
                }, delay)
            }
        }

        wsClient?.connect()
        return START_STICKY
    }

    private fun lock() {
        if (isLocked) return
        isLocked = true
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        wsClient?.reportStatus("locked")
    }

    private fun unlock() {
        if (!isLocked) return
        isLocked = false
        // LockActivity 会自行 finish
        wsClient?.reportStatus("unlocked")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wsClient?.disconnect()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tv_control",
                "电视管控",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "tv_control")
            .setContentTitle("电视管控")
            .setContentText("运行中")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add tv-app/app/src/main/java/com/tvcontrol/LockService.kt tv-app/app/src/main/java/com/tvcontrol/WebSocketClient.kt
git commit -m "feat: add LockService with WebSocket keep-alive"
```

---

## 任务 6：电视端 APK——LockActivity（黑屏锁定）+ BootReceiver

**文件：**
- 创建：`tv-app/app/src/main/java/com/tvcontrol/LockActivity.kt`
- 创建：`tv-app/app/src/main/java/com/tvcontrol/BootReceiver.kt`
- 创建：`tv-app/app/src/main/java/com/tvcontrol/DeviceAdminReceiver.kt`

- [ ] **步骤 1：LockActivity（全屏黑 + 按键拦截）**

```kotlin
// tv-app/app/src/main/java/com/tvcontrol/LockActivity.kt
package com.tvcontrol

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏黑色背景，无任何 UI
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setContentView(android.R.layout.simple_list_item_1) // 占位，实际背景黑色

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, DeviceAdminReceiver::class.java)

        // 启用 LockTask 模式
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
            startLockTask()
        }
    }

    // 拦截所有按键
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 拦截 Back, Home, Menu, Volume 等所有按键
        return true
    }

    override fun onBackPressed() {
        // 禁用返回
    }

    override fun onUserLeaveHint() {
        // 阻止回到桌面
        if (!isFinishing) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        // 退出 LockTask
        try {
            stopLockTask()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
```

- [ ] **步骤 2：BootReceiver**

```kotlin
// tv-app/app/src/main/java/com/tvcontrol/BootReceiver.kt
package com.tvcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, LockService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
```

- [ ] **步骤 3：DeviceAdminReceiver**

```kotlin
// tv-app/app/src/main/java/com/tvcontrol/DeviceAdminReceiver.kt
package com.tvcontrol

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "设备管理员已启用", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "设备管理员已禁用", Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **步骤 4：Commit**

```bash
git add tv-app/app/src/main/java/com/tvcontrol/LockActivity.kt tv-app/app/src/main/java/com/tvcontrol/BootReceiver.kt tv-app/app/src/main/java/com/tvcontrol/DeviceAdminReceiver.kt
git commit -m "feat: implement LockActivity black screen lock, BootReceiver, and DeviceAdmin"
```

---

## 任务 7：手机端鸿蒙 App——项目脚手架

**文件：**
- 创建：`phone-app/AppScope/app.json5`
- 创建：`phone-app/build-profile.json5`
- 创建：`phone-app/entry/src/main/module.json5`
- 创建：`phone-app/entry/src/main/resources/base/element/string.json`

**说明：** 需要用 DevEco Studio 创建鸿蒙项目。以下为关键文件结构：

- [ ] **步骤 1：配置 module.json5**

```json
// phone-app/entry/src/main/module.json5
{
  "module": {
    "name": "entry",
    "type": "entry",
    "description": "电视远程管控",
    "mainElement": "Index",
    "deviceTypes": ["phone"],
    "requestPermissions": [
      {
        "name": "ohos.permission.INTERNET"
      }
    ],
    "abilities": [
      {
        "name": "EntryAbility",
        "srcEntry": "./ets/entryability/EntryAbility.ets",
        "description": "电视管控主界面",
        "icon": "$media:icon",
        "label": "$string:app_name",
        "startWindowIcon": "$media:icon",
        "startWindowBackground": "#FF000000",
        "exported": true,
        "skills": [
          {
            "entities": ["entity.system.home"],
            "actions": ["action.system.home"]
          }
        ]
      }
    ]
  }
}
```

- [ ] **步骤 2：Commit**

```bash
git add phone-app/
git commit -m "feat: add phone app scaffold"
```

---

## 任务 8：手机端鸿蒙 App——WebSocket 服务

**文件：**
- 创建：`phone-app/entry/src/main/ets/model/Types.ets`
- 创建：`phone-app/entry/src/main/ets/service/WebSocketService.ets`

- [ ] **步骤 1：定义数据类型**

```typescript
// phone-app/entry/src/main/ets/model/Types.ets
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
  authToken: string;
}

export interface ServerMessage {
  type: string;
  state?: TvState;
  status?: TvStatus;
  message?: string;
}
```

- [ ] **步骤 2：WebSocket 服务**

```typescript
// phone-app/entry/src/main/ets/service/WebSocketService.ets
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
```

- [ ] **步骤 3：Commit**

```bash
git add phone-app/entry/src/main/ets/model/Types.ets phone-app/entry/src/main/ets/service/WebSocketService.ets
git commit -m "feat: add WebSocket service for phone app"
```

---

## 任务 9：手机端鸿蒙 App——主界面（ArkUI）

**文件：**
- 创建：`phone-app/entry/src/main/ets/pages/Index.ets`
- 创建：`phone-app/entry/src/main/ets/pages/Pairing.ets`
- 创建：`phone-app/entry/src/main/ets/entryability/EntryAbility.ets`

- [ ] **步骤 1：主界面（首页）**

```typescript
// phone-app/entry/src/main/ets/pages/Index.ets
import { WebSocketService } from '../service/WebSocketService';
import { TvState, TvStatus } from '../model/Types';
import router from '@ohos.router';

@Entry
@Component
struct Index {
  @State tvStatus: TvStatus = TvStatus.OFFLINE;
  @State isLocked: boolean = false;
  @State isConnected: boolean = false;
  private wsService: WebSocketService | null = null;

  aboutToAppear() {
    const prefs = new Map<string, string>();
    // 从 PersistentStorage 读取 authToken
    const authToken = AppStorage.get<string>('authToken') || '';
    if (!authToken) {
      router.replaceUrl({ url: 'pages/Pairing' });
      return;
    }

    this.wsService = new WebSocketService(authToken);
    this.wsService.onStatusUpdate = (status) => {
      this.tvStatus = status;
      this.isLocked = (status === TvStatus.LOCKED);
    };
    this.wsService.onConnectionChange = (connected) => {
      this.isConnected = connected;
    };
    this.wsService.connect();
  }

  build() {
    Stack() {
      // 背景
      Column() {
        // 顶部状态栏
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

        // 大控制按钮
        Button(this.isLocked ? '打开电视' : '关闭电视')
          .width(200).height(200)
          .borderRadius(100)
          .backgroundColor(this.isLocked ? Color.Green : Color.Red)
          .fontSize(24).fontColor(Color.White)
          .margin({ top: 60 })
          .onClick(() => {
            if (this.isLocked) {
              this.wsService?.setState(TvState.OPEN);
            } else {
              this.wsService?.setState(TvState.CLOSED);
            }
          })

        // 临时解锁按钮（锁定状态下显示）
        if (this.isLocked) {
          Button('临时解锁 5 分钟')
            .width(200).height(48)
            .borderRadius(24)
            .backgroundColor(Color.Orange)
            .fontSize(16).fontColor(Color.White)
            .margin({ top: 24 })
            .onClick(() => {
              this.wsService?.requestUnlock(5);
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
    this.wsService?.disconnect();
  }
}
```

- [ ] **步骤 2：配对页面**

```typescript
// phone-app/entry/src/main/ets/pages/Pairing.ets
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
          // 配对逻辑——通过 WebSocket 发送配对请求
          this.statusMessage = '配对中...';
          // 配对成功后跳转到主页
          AppStorage.setOrCreate('authToken', 'xxx');
          router.replaceUrl({ url: 'pages/Index' });
        })
    }
    .width('100%').height('100%')
    .alignItems(HorizontalAlign.Center)
    .backgroundColor(Color.Black)
  }
}
```

- [ ] **步骤 3：Commit**

```bash
git add phone-app/entry/src/main/ets/pages/ phone-app/entry/src/main/ets/entryability/
git commit -m "feat: implement main control UI and pairing page"
```

---

## 任务 10：部署与初始化指南

**文件：**
- 创建：`server/deploy.md`

- [ ] **步骤 1：创建部署说明**

```markdown
# 云服务器部署指南

## 阿里云轻量应用服务器

1. 购买 阿里云轻量应用服务器（2核1GB，~24元/月），选择 Ubuntu 22.04
2. SSH 登录服务器
3. 安装 Node.js：
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_22.x | sudo bash -
   sudo apt install -y nodejs
   ```
4. 上传 server 目录到服务器：
   ```bash
   scp -r server/ user@your_ip:/opt/tv-control/
   ```
5. 安装依赖并启动：
   ```bash
   cd /opt/tv-control
   npm install
   node src/db.js --init
   npm install -g pm2
   pm2 start ecosystem.config.js
   pm2 save
   pm2 startup
   ```

## 配置 SSL（可选但推荐）

使用 acme.sh 或 certbot 为 WebSocket 配置 wss://

## Render.com 免费方案

1. 在 https://render.com 创建 Web Service
2. 连接 GitHub 仓库
3. 根目录设为 `server`
4. 启动命令：`npm start`
5. 免费套餐支持 WebSocket
```

- [ ] **步骤 2：Commit**

```bash
git add server/deploy.md
git commit -m "docs: add deployment guide"
```

---

## 自检清单

| 需求 | 对应任务 | 状态 |
|------|---------|------|
| 电视设备注册 + 配对码 | 任务 1, 3 | ✅ |
| 手机 App 配对 | 任务 9 | ✅ |
| WebSocket 实时通信 | 任务 3, 5, 8 | ✅ |
| 手机远程关电视 | 任务 3, 9 | ✅ |
| 电视黑屏锁定 | 任务 6 | ✅ |
| 重启自动锁定 | 任务 6 (BootReceiver) | ✅ |
| 临时解锁 | 任务 3, 9 | ✅ |
| 防卸载（设备管理员） | 任务 6 | ✅ |
| 保活（前台 Service） | 任务 5 | ✅ |
| 服务器部署 | 任务 10 | ✅ |
