# Setren的LineageOS补全计划

面向 LineageOS 的 LSPosed 模块，整合最近任务锁定、人脸解锁、限时息屏显示和低亮度唤醒修复。包名为 `com.setren.lineageenhance`，支持直接覆盖升级。

当前适配目标：Xiaomi 13（fuxi）、LineageOS 24 / Android 17、LSPosed API 93 及以上。其他机型和系统版本尚未验证。

## 功能

- **最近任务锁定**：下滑任务卡片锁定或解锁，“清除所有”跳过已锁定任务；支持 Trebuchet 和 Pixel Launcher。
- **人脸解锁**：本地识别，支持识别后上滑解锁或自动退出锁屏，保留系统强认证要求。
- **限时息屏显示**：自定义 AOD 显示秒数，`0` 为不限时。进入 AOD 后计时，下一次亮屏重置；通知和认证显示仍由系统处理。
- **亮屏修复（实验）**：在低亮度唤醒或生物识别解锁后短暂调亮，再交还系统控制，不限制最低亮度。原黑屏故障尚未在对照测试中稳定复现，不能保证适用于所有面板异常。
- **隐藏桌面图标**：立即生效，隐藏后仍可从 LSPosed 的模块设置打开页面并恢复图标。

## 安装与配置

1. 安装 APK，在 LSPosed 中启用模块，作用域选择“系统界面”和正在使用的启动器。
2. 停用原 RecentsLock / UniversalAuth 的对应作用域，避免重复 Hook。
3. 打开应用，按需开启功能。功能开关和 AOD 时长调整后重启生效；隐藏桌面图标无需重启。
4. 人脸功能需要在“人脸录入与解锁设置”中导入识别库、授予权限、启用辅助服务并录入面部。仅点击“测试识别”不会解锁手机。
5. 使用 Thanox 等后台管理工具时，允许本应用后台运行。任务卡片锁定不能阻止后台管理工具强行停止应用。
6. 限时 AOD 需先开启系统息屏显示，并停用外部 AOD 超时规则。

识别库不随 APK 分发。应用从用户选择的 Motorola Face Unlock `01.03.0312` APK 提取四个 ARM64 库，并校验哈希。人脸数据保持在应用私有存储中。

## 构建与测试

要求 JDK 21、Android SDK Platform 37.0 和 Build Tools 37.0.0。设置 `ANDROID_HOME`，或在本机 `local.properties` 中指定 SDK 路径。

本机示例：

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-zulu-fx
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。Debug APK 使用本机调试签名，正式发布应使用自行保管的稳定签名。构建目录、SDK 路径、私有识别库和日志不进入版本控制。

## 项目结构

```text
app/                         应用界面、LSPosed Hook 和有效回归测试
faceunlock-backend/          本地人脸识别后端
faceunlock-framework_stub/   人脸框架兼容接口
libuniversalauth/            认证接口与权限声明
libuniversalauth-xposed/     认证广播和解锁常量
licenses/                   第三方许可证
app/src/main/assets/licenses/ 随 APK 分发的许可证与归属说明
docs/                       来源和使用说明
```

内部保留上游包名和模块边界，以维持已验证的认证及启动器行为。不会更改已安装应用的包名或录入数据。

## 排错与限制

- 识别成功但仍需密码：先确认不是重启首次解锁、锁定模式或系统强认证要求，再确认启用了锁屏辅助服务；详见 [排错说明](docs/TROUBLESHOOTING.md)。
- 最近任务无法锁定：确认只有一份锁定 Hook 启用，并重新加载启动器。
- AOD 短时计时：CPU 未休眠时使用进程计时，休眠时由唤醒闹钟接管；系统调度可能推迟短于 5 秒的超时。
- 系统升级可能改变 SystemUI / 启动器内部接口，需要重新验证兼容性。

## 来源与许可证

代码基于 UniversalAuth（Apache-2.0）与 RecentsLock（MIT）。上游版本和源码归属见 [SOURCES](docs/SOURCES.md)，许可证见 [LICENSE.md](LICENSE.md)、[NOTICE](NOTICE) 和 [licenses](licenses)。版本变化见 [CHANGELOG](CHANGELOG.md)。

头像由项目作者提供，不属于上述上游代码许可证的授权范围。
