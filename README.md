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
- 人脸识别依赖 Megvii 的闭源专有库，应用会从 Motorola Face Unlock 应用中提取这些库。

### 安装

1. 安装 UniversalAuth Xposed 模块，在 LSPosed 中启用并设置推荐作用域。
2. **重启手机**，让更新后的 Xposed 代码进入 SystemUI。
3. 安装 Face Unlock APK，打开应用并按提示下载识别库。
4. 授予解锁权限，并启用 Face Unlock 辅助功能服务。
5. 点击“开始设置”录入面部；相机权限请选择“仅在使用该应用时允许”。
6. 锁定屏幕并测试。更新 Xposed APK 后必须再次重启；仅更新 Face Unlock APK 通常不需要重启。

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
These libraries can be found in this free app: https://play.google.com/store/apps/details?id=com.motorola.faceunlock so this app will download it and pull the libraries out of it.


#### Installation
1. Install and enable the UniversalAuth Xposed module.
2. Reboot to make sure the Xposed module is enabled.
3. Install the face unlock auth module APK.
4. A new app called "Face unlock" should now appear, open it.
5. Allow the app to download the libraries when requested.
6. Grant the app permission to unlock your phone when asked.
7. Enable the accessibility service when asked.
8. Press the "START SETUP" button to enroll your face. The app will ask you to grant it camera permissions, make sure to select "allow while using the app" if that option is available.
9. Lock your phone and test that you are able to use face unlock!

After updating the Xposed APK, reboot again so SystemUI loads the new module code. Updating only the face-unlock app normally does not require a reboot.

# Credits
Thanks to:

- the PixelExperience devs for writing the core UI and face unlock logic code.
- The LsPosed/EdXposed devs and rovo89 for Xposed.
- topjohnwu for Magisk.
- Google for AOSP.
