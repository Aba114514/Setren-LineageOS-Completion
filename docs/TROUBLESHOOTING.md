# 排错说明

## 人脸识别

“测试识别”只检查识别器，不能替代锁屏辅助服务。请检查相机及解锁权限、辅助服务开关，并确认 LSPosed 已启用 SystemUI 作用域。

使用 Thanox 时，在“后台运行”中允许 `com.setren.lineageenhance`。强行停止应用可能移除辅助服务的启用状态，恢复后台运行权限后需重新开启辅助服务。

重启后的首次解锁、锁定模式以及系统要求强认证时，仍需输入 PIN、图案或密码。不要通过放宽系统认证策略排错。

## 最近任务锁定

仅启用本模块的锁定功能，停用旧 RecentsLock。启用或切换作用域后重新加载启动器或重启设备。卡片锁定只防止“清除所有”，不能代替允许后台运行。

## 限时 AOD

先开启系统 AOD，并关闭 Thanox 等工具中的旧 AOD 超时规则。若旧规则留下了抑制，可使用与该规则相同的 root 身份解除：

```sh
su -c 'cmd power suppress-ambient-display thanox_aod_timeout none'
```

本模块不改写系统 AOD 开关。`0` 表示不限制时长；通知脉冲和认证显示不被强制禁用。

## 低亮度黑屏

归一化亮度 `0.0` 是系统最低值，并不等于物理黑屏。判断是否恢复应结合实际画面，不能只看亮度日志。

若画面无法恢复，可通过已授权的 ADB 临时调亮：

```sh
adb shell settings put system screen_brightness_mode 0
adb shell settings put system screen_brightness 80
adb shell input keyevent KEYCODE_WAKEUP
```

这些命令会关闭自动亮度并设置手动亮度，恢复后请按自己的习惯重新调整。
