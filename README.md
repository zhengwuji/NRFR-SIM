<div align="center">
  <h1>NRFR-SIM</h1>
  <p>🌍 免 Root 的 SIM 卡国家码修改工具，让你的网络更自由</p>

  <p>
    <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android" alt="Platform">
    <img src="https://img.shields.io/badge/Android-8%20~%2016-3DDC84?logo=android" alt="Android Version">
    <img src="https://img.shields.io/badge/CI%20自动编译-每次代码变更-2088FF?logo=githubactions" alt="CI Auto Build">
  </p>

  <div style="display: flex; justify-content: center; align-items: center; gap: 20px; margin: 20px 0;">
    <img src="docs/images/app.png" alt="Android 应用界面" width="220">
  </div>
   <br>
</div>

NRFR-SIM 是一款免 Root 的 SIM 卡国家码修改工具，无需 Root 权限即可修改 SIM 卡国家码。本项目完全基于 Android 系统原生 API 实现，不依赖
Xposed、Magisk 等任何第三方框架，仅通过调用系统级接口实现功能。通过修改国家码，你可以：

- 🌏 解锁运营商限制，使用更多本地功能
- 🔓 突破某些区域限制的应用和服务
- 🛠️ 解决国际漫游时的兼容性问题
- 🌐 帮助使用海外 SIM 卡获得更好的本地化体验
- ⚙️ 解决部分应用识别 SIM 卡地区错误的问题

> 💡 **每次代码变更都会通过 GitHub Actions 自动编译 APK 并发布到
> [Releases](../../releases) 页面**，修改 README 等文档不会触发构建。所有发布版本都会附带中文更新内容，直接下载最新 Release 的
> `nrfr-*.apk` 安装即可。

## 📱 使用案例

### 运营商配置优化

- 手机无法正确识别运营商配置
- 某些运营商特定功能无法使用
- 网络配置与当地运营商不匹配

### 运营商参数适配

- 运营商功能配置不完整
- 网络参数与运营商默认配置不匹配
- 运营商特定服务无法正常启用

### 漫游网络识别

- 漫游时运营商名称显示异常
- 网络配置与漫游地运营商不匹配
- 运营商特定功能无法正常启用

### TikTok 区域限制解除

- TikTok 网络错误
- 无法正常使用 TikTok 的完整功能

### Samsung Health 区域限制解除

- 无法通过 Samsung Health 的首次 SIM 卡检测
- 无法同步健康数据
- 无法正常使用 Samsung Health 的完整功能

解决办法：使用 NRFR-SIM 修改 SIM 卡国家码为支持的地区（如 JP、US 等），然后重新打开对应应用即可。

## 💡 实现原理

NRFR-SIM 通过调用 Android 系统级 API（CarrierConfigLoader）修改系统内的运营商配置参数，而**不是直接修改 SIM 卡**。这种实现方式：

- 完全在系统层面工作，不会对 SIM 卡本身进行任何修改或造成损坏
- 仅改变系统对 SIM 卡信息的读取方式
- 基于 Android 原生 API 实现，不依赖任何第三方框架（如 Xposed、Magisk 等）
- 通过 Shizuku 仅提供必要的权限支持
- 所有修改都是可逆的，随时可以还原

## ✨ 特性

- 🔒 安全可靠
   - 无需 Root 权限
   - 不修改系统文件
   - 不影响系统稳定性
   - 不会对 SIM 卡造成任何影响
- 🔄 功能完善
   - 支持随时还原修改
   - 支持双卡及多卡设备，可分别配置
   - 自动适配最新 Android 系统（支持到 Android 16）
   - 一次修改永久生效，重启后保持
- 🚀 简单易用
   - 智能检测设备和 SIM 卡状态
   - 操作过程有加载指示，写入后自动确认生效
   - 内置常见运营商预设，也支持自定义
   - 界面支持中文 / 英文
   - 轻量且高效，安装包体积小

## 📲 使用方法（手机 + 电脑）

推荐使用「快速启动工具」，它会自动帮你完成安装和授权：

1. **准备手机**
    - 启用开发者选项（连续点击「设置 → 关于手机 → 版本号」7 次）
    - 进入开发者选项，开启 **USB 调试**
    - 如有「USB 调试（安全设置）」也一并开启
    - 开启 **USB 安装**（允许通过 USB 安装应用）

2. **连接手机到电脑**
    - 使用数据线将手机连接到电脑
    - 在手机上弹出的授权框中允许 USB 调试

3. **下载并启动快速启动工具**
    - 从 [Releases](../../releases) 页面下载最新版本的快速启动工具压缩包并解压
    - 运行其中的 Nrfr 快速启动工具，工具会自动检测已连接的设备

4. **安装必要组件**
    - 工具会自动安装 Shizuku 到手机，按照提示启用 Shizuku
    - 等待工具自动安装 NRFR-SIM 应用

5. **修改国家码**
    - 在手机上打开 NRFR-SIM，授予 Shizuku 权限
    - 选择需要修改的 SIM 卡
    - 选择目标国家码（或自定义输入两位字母代码，如 `JP`、`US`）
    - 可同时自定义运营商名称（可选）
    - 点击「保存生效」，提示成功后即完成

修改完成后无需重启设备，设置会立即生效并永久保持。如需还原，点击「还原默认」即可恢复原始状态。

## 📲 使用方法（仅手机，APK 直装）

1. 从 [Releases](../../releases) 下载最新 `nrfr-*.apk` 安装到手机
2. 安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest)：
   - 有 Root：直接在 Shizuku 内启动
   - 无 Root：按 Shizuku 应用内提示，通过「无线调试」配对启动（Android 11+），或连接电脑用 ADB 启动
3. 打开 NRFR-SIM，授予 Shizuku 权限后按上面的步骤选择国家码保存即可

## 🚨 常见问题

- **提示需要 Shizuku / 授权失败**：确认 Shizuku 正在运行且已给本应用授权；Shizuku 重启后需重新授权
- **保存失败（权限不足）**：部分系统限制了 shell 权限，应用会自动尝试提权方式，如仍失败请重启 Shizuku 后重试
- **修改后不生效**：开关一次飞行模式，或等待片刻让系统重新加载运营商配置
- **想恢复原状**：使用应用内的「还原默认」按钮
- **双卡设备**：可在应用内分别选择每张卡单独设置

## ⚠️ 注意事项

- 需要安装并启用 Shizuku
- 修改国家码可能会影响运营商服务，请谨慎操作
- 部分设备可能不支持修改国家码
- 如需还原设置，请使用应用内的还原功能

## 📦 构建

项目包含手机应用（Android）和快速启动工具（桌面端）两部分。**推送任何代码变更后 GitHub Actions
会自动构建**，无需手动操作；以下为本地构建方式。

### 手机应用 (app)

```bash
# 构建 Release 版本（需要 JDK 17+ 和 Android SDK）
./gradlew assembleRelease

# 构建 Debug 版本
./gradlew assembleDebug
```

> 无签名配置时会自动使用 debug 签名，生成的 release APK 仍可直接安装。
> 版本号统一在根目录 `gradle.properties` 的 `APP_VERSION_NAME` / `APP_VERSION_CODE` 中维护。

### 快速启动工具 (nrfr-client)

```bash
cd nrfr-client/frontend && npm install && cd ..
wails build
```

构建完成后，可以在以下位置找到生成的文件：

- 手机应用: `app/build/outputs/apk/`
- 快速启动工具: `nrfr-client/build/bin/`

## 🤖 CI 自动编译说明

- **触发条件**：推送到任意分支的代码变更（修改 `*.md`、`docs/` 不会触发）
- **发布位置**：每次构建自动创建 Release，标签为 `v<版本号>-r<构建号>` 形式
- **更新内容**：Release 说明由最近的提交记录自动生成（中文），并附安装说明
- **构建产物**：`nrfr-<版本号>.apk` + 配套的 `shizuku-<版本>.apk`
- 也可在 Actions 页面手动触发（workflow_dispatch）

## 📝 依赖项

- [Shizuku](https://shizuku.rikka.app/) - 用于提供特权服务
- [ADB](https://developer.android.com/tools/adb) - Android 调试桥接

## 📄 许可证

本项目采用 [Apache-2.0](LICENSE) 许可证。

## ⚠️ 免责声明

本工具仅供学习和研究使用。使用本工具修改系统设置可能会影响设备的正常使用，请自行承担风险。作者不对任何可能的损失负责。

## 🙏 鸣谢

- 原项目 [Nrfr](https://github.com/Ackites/Nrfr) by [@Ackites](https://github.com/Ackites)
- [Shizuku](https://shizuku.rikka.app/) - 感谢 Shizuku 提供的特权服务支持
