# MQTT 剪贴板同步（Android）

把 Android 设备的系统剪贴板通过 MQTT 与自建 Broker 双向同步：本机复制即发布到指定主题，收到订阅消息即写回本机剪贴板。常驻前台服务保持长连接，通知栏只有一条状态通知。

## 功能特性

- **双向同步**：剪贴板变更自动发布；订阅消息自动写入剪贴板，两个方向可单独开关。
- **常驻后台**：前台服务（`dataSync` 类型）持有 MQTT 连接，支持开机自启动、任务移除后按 `START_STICKY` 重建。
- **自动订阅**：连接成功（含断线重连）后自动订阅配置主题，页面无需「订阅」按钮；修改订阅主题即时重新订阅。
- **自动重连**：断线后按指数退避重连（5s 起，最长 120s），状态与日志实时可见。
- **TLS 支持**：`ssl://` 走系统信任链并显式做主机名校验与 SNI；自签名 Broker 可开启「信任自签名证书」。
- **单条通知**：只保留一条常驻状态通知（已连接绿色 / 其它灰色），仅在首次变为已连接或异常时提示一次，不会重复打扰。
- **合并按钮**：状态卡右上角一个按钮完成连接/断开，图标与文案随状态切换（电源 / 电源断开）。
- **去重防回环**：远程写入的内容 30 秒内不会被当作本地新变更再次发布。

## 工作原理

```
本机复制 ──> ClipboardMonitor ──> publish  主题：clipboard/out（QoS/retain 可配）
                                          │
                                       MQTT Broker
                                          │
订阅消息 <── MqttEngine 回调 <── subscribe 主题：clipboard/in（可多个）
   │
   └──> ClipboardMonitor.writeText() 写入系统剪贴板
```

- 消息负载即**剪贴板纯文本**（UTF-8），不做任何封装，方便与桌面端 / 其他客户端互通。
- 剪贴板检测为 `OnPrimaryClipChangedListener` + 1s 轮询，双保险。
- 消息在 Paho 回调线程到达，统一切回主线程再写剪贴板与刷新 UI。

## 快速开始

1. 安装并打开应用，在 **Broker 配置** 中填写主机（`host` 或 `ssl://host`）、端口（SSL 通常 8883，明文 1883）。
2. 打开 **SSL/TLS 加密连接** 开关（自签名证书时同时打开「信任自签名证书」）。
3. 按需填写 Client ID（留空自动生成）、用户名、密码、KeepAlive、Clean Session。
4. 点击右上角 **连接** 按钮；状态点变绿即表示已连接，随后自动订阅。
5. 需要长期后台运行时，在 **开机启动** 中开启开关，并按页面指引授予通知权限、加入电池优化白名单、在厂商自启动管理中允许本应用。

默认主题：发布 `clipboard/out`、订阅 `clipboard/in`（均可在界面修改，订阅支持换行或逗号分隔多个主题）。

## 配置项说明

| 分组 | 配置项 | 默认值 |
| --- | --- | --- |
| Broker | 主机 / 端口 / SSL / 信任自签名证书 | 空 / 1883 / 关 / 关 |
| Broker | Client ID / 用户名 / 密码 | 空（Client ID 自动生成） |
| Broker | KeepAlive / Clean Session | 60s / 开 |
| 订阅频道 | 订阅主题 / 订阅 QoS | `clipboard/in` / 1 |
| 剪贴板同步 | 发送变更 / 接收写入 / 发布主题 / 发布 QoS / Retain | 开 / 开 / `clipboard/out` / 1 / 关 |
| 开机启动 | 开机自动连接 / 厂商自启动确认 | 关 / 未确认 |

配置保存在 `SharedPreferences`（`SettingsRepository`），修改即时生效：连接参数变化会自动重连，纯开关类改动只调整监听与行为。

## Android 10+ 剪贴板读取限制

Android 10 起，后台应用无法读取剪贴板，因此：

- 应用在前台（或刚复制、通知栏可见）时同步正常；
- 需要在后台**持续读取**剪贴板，请把内置的「剪贴板同步键盘」设为默认输入法以获得系统豁免：**剪贴板同步** 卡片 → **设置** → 系统输入法设置中启用并设为默认。

未启用输入法时，写入方向（收到消息写剪贴板）不受限制，仅后台读取会被系统拒绝，日志区会提示。

## 常驻与权限

| 权限 / 设置 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 显示常驻连接状态通知（Android 13+ 需授权） |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | 后台常驻同步 |
| `RECEIVE_BOOT_COMPLETED` | 开机后自动连接 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 避免系统限制后台服务与自启动 |
| 厂商自启动管理 | 无标准查询接口，需手动允许后在应用内勾选确认 |

「开机启动」卡片会逐项检测并给出跳转入口，全部满足时提示「开机启动所需权限已满足」。

## 构建

环境要求：

- JDK 25（Gradle Daemon JVM，`gradle/gradle-daemon-jvm.properties`）、Gradle 9.6（wrapper 自带）
- Android SDK：`compileSdk 37` / `targetSdk 37` / `minSdk 28`（Android 9）
- 通过 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 指定 SDK 路径

常用命令：

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包
./gradlew installDebug       # 安装到设备
```

技术栈：Kotlin 2.2.10、Jetpack Compose Material3、Android Gradle Plugin 9.4、Eclipse Paho MQTT 1.2.5、Kotlin Coroutines 1.9。

### 发布签名

发布签名通过根目录 `keystore.properties` 配置（**该文件与密钥库均已 gitignore，不会入库**）：

```properties
KEYSTORE_FILE=keystore/mqtt-clipboard.jks
KEYSTORE_PASSWORD=********
KEY_ALIAS=mqtt-clipboard
KEY_PASSWORD=********
```

文件存在时 `assembleRelease` 使用其签名；不存在时自动回退到 debug 签名，便于本地也能打出 release 包。release 构建当前关闭了代码优化（`optimization.enable = false`），以规避 Paho 反射相关的运行期问题。

## 项目结构

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt               # 权限申请、启动常驻服务
├── ClipboardApp.kt               # Application：通知渠道与全局依赖
├── data/
│   ├── BrokerConfig.kt           # 连接与同步配置模型、默认值
│   └── SettingsRepository.kt     # 配置持久化与配置流
├── mqtt/
│   ├── MqttEngine.kt             # 连接、自动订阅、指数退避重连、发布
│   └── SslSupport.kt             # TLS SocketFactory、主机名校验、SNI
├── clipboard/
│   ├── ClipboardMonitor.kt       # 剪贴板监听、写入、回环抑制
│   └── ClipboardImeService.kt    # 可选输入法通道（后台读取豁免）
├── service/MqttForegroundService.kt  # 常驻前台服务：连接 + 剪贴板 + 通知
├── receiver/BootReceiver.kt      # 开机 / 包替换后启动服务
├── notify/Notifications.kt       # 单条常驻状态通知
├── runtime/AppStatus.kt          # 连接状态与日志总线
├── util/                         # 权限检测与系统设置跳转
└── ui/                           # Compose 界面（状态卡、Broker、订阅、剪贴板、开机启动、日志）
```

## 已知限制

- 仅同步**文本**剪贴板内容，图片等二进制内容会忽略。
- 后台读取受 Android 10+ 限制，需启用内置输入法通道（见上文）。
- 多设备同时复制同一内容时，以各自写入顺序为准，无冲突合并策略。
- 消息不做加密或压缩，敏感内容请在 Broker 侧启用 TLS 并妥善设置访问凭据。
