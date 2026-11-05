# 源码来源与归属

## UniversalAuth

- 上游项目：[null-dev/UniversalAuth](https://github.com/null-dev/UniversalAuth)。
- 本项目基于原 `Aba114514/UniversalAuth` 的 `android-16-material3-chinese` 分支整合。
- 集成前提交：`c18428516704399ee72f911ba34b5f5cea069c43`，历史保留于本仓库，可查看[集成前快照](https://github.com/Aba114514/Setren-LineageOS-Completion/tree/c18428516704399ee72f911ba34b5f5cea069c43)。
- 许可证：Apache-2.0，原作者与项目归属见根目录 `LICENSE.md` 和 `NOTICE`。

## RecentsLock

- 原项目：`Aba114514/RecentsLock`，现已整合至本项目。
- 来源提交：`1a078729853028be90479fb403d2176585923b55`。
- 锁定实现保留于 [Hook.java](../app/src/main/java/com/setren/recentslock/Hook.java)，未改变原有行为。
- 许可证：MIT，完整声明见 [RecentsLock-MIT.txt](../licenses/RecentsLock-MIT.txt)。

## 集成说明

原 UniversalAuth 和 RecentsLock 仓库由本项目取代。内部保留原有识别和启动器 Hook 包名，安装包使用独立应用 ID 与认证广播名称。

UniversalAuth 的 Hook 位于 `app/src/main/java/ax/nd/universalauth/xposed`，不再构建单独的旧 Xposed APK。许可证同时保留在源码与 APK 的 assets 中。

头像位于 `app/src/main/res/drawable-nodpi/setren_avatar.jpg`，由项目作者提供；不从代码许可证推定该图片的再分发授权。
