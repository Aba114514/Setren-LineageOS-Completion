# UniversalAuth — Android 16 compatibility fork

[更新说明](#更新说明) | [原仓库说明](#原仓库说明)

This fork keeps UniversalAuth working on modern SystemUI, adds an Android 16 face-unlock path, and refreshes the face-unlock app with Material 3 Expressive and Simplified Chinese support.

> Tested on Xiaomi 13 (fuxi), Android 16, LSPosed. The legacy Android 10–12 path is retained, but has not been re-tested on every ROM.

## 更新说明

UniversalAuth 可以为不同 Android ROM 提供自定义锁屏认证方式。本分支新增了 Android 16 的 SystemUI 解锁适配，并将人脸解锁应用更新为 Material 3 Expressive 界面，支持简体中文和系统动态配色。

### 功能与限制

- 支持使用人脸解锁锁屏；不会替代应用内的生物识别认证。
- Android 16 路径会同步 SystemUI 的人脸认证状态，并触发正常的生物识别唤醒解锁流程。
- 关闭“自动退出锁屏”后，面部匹配只完成认证，仍需上滑退出锁屏；开启后会直接进入主屏幕或上次使用的应用。
- “提前开始识别”跟随 SystemUI 的锁屏可见与唤醒状态启动识别，可能缩短等待时间，但依赖 ROM 内部接口，默认关闭。
- 保留系统强认证规则：重启后首次解锁、锁定模式或系统要求密码时，仍需输入 PIN、图案或密码。
- 需要 Xposed、EdXposed 或 LSPosed；Android 16 已在 Xiaomi 13（fuxi）上验证。
- 人脸识别依赖 Megvii 的闭源专有库；首次启动时需要选择 Motorola Face Unlock `01.03.0312` APK，应用只提取并校验所需的四个 ARM64 库。

### 安装

1. 安装统一版 UniversalAuth APK；它同时包含控制界面、人脸识别服务和 LSPosed 模块。
2. 在 LSPosed 中启用“人脸解锁”，作用域选择“系统界面/SystemUI”。从旧双 APK 版本升级时，请停用并卸载旧的独立 UniversalAuth Xposed 模块。
3. 打开应用，点“打开 APKPure”，在浏览器中手动完成人机验证并下载 Motorola Face Unlock `01.03.0312`；返回应用后选择已下载的 APK 以导入识别库。[APKPure 历史版本页](https://apkpure.com/moto-face-unlock/com.motorola.faceunlock/versions)
4. 授予解锁权限，并启用“人脸解锁”辅助功能服务。
5. 点击“开始设置”录入面部；相机权限请选择“仅在使用该应用时允许”。
6. **重启手机**，让 SystemUI 加载统一 APK 中的模块代码，然后锁屏测试。

## 原仓库说明

This project aims to bring a variety of custom authentication options to various Android ROMs.

**Your phone needs to have Xposed (or EdXposed/Lsposed).**

**This fork has been tested on Android 16.** The original Android 10–12 implementation is retained for older SystemUI versions.

**You currently cannot use this project to authenticate in apps. It can only unlock your lockscreen.** Support for using this project to authenticate in apps is under development/investigation.

![Face unlock demo](https://raw.githubusercontent.com/null-dev/UniversalAuth/master/branding/face-unlock.gif)
## Authentication modules
Currently available authentication modules:

- [Face unlock](#face-unlock)

### Face unlock
Face unlock allows you to unlock your phone with your face.

The face unlock module depends on closed source, proprietary libraries developed by Megvii.
These libraries are available in Moto Face Unlock `01.03.0312`. The unified APK asks the user to select a locally obtained copy, then extracts only the required ARM64 libraries and verifies their hashes.


#### Installation
1. Install the unified UniversalAuth APK. It contains the controller, recognizer, accessibility service, and LSPosed module.
2. Enable Face unlock in LSPosed and scope it to SystemUI. Disable the legacy standalone UniversalAuth Xposed module when upgrading.
3. Open Face unlock, use the APKPure button to download Moto Face Unlock `01.03.0312` manually, then return and select the downloaded APK. [APKPure version history](https://apkpure.com/moto-face-unlock/com.motorola.faceunlock/versions)
4. Grant the unlock permission and enable the accessibility service.
5. Enroll your face and grant camera access while using the app.
6. Reboot so SystemUI loads the module code, then lock the phone and test.

# Credits
Thanks to:

- the PixelExperience devs for writing the core UI and face unlock logic code.
- The LsPosed/EdXposed devs and rovo89 for Xposed.
- topjohnwu for Magisk.
- Google for AOSP.
