# Sources

- UniversalAuth: https://github.com/Aba114514/UniversalAuth
  Branch: android-16-material3-chinese
  Commit: c18428516704399ee72f911ba34b5f5cea069c43
- RecentsLock: https://github.com/Aba114514/RecentsLock
  Commit: 1a078729853028be90479fb403d2176585923b55
  Hook.java is copied without behavioral changes.

Original recognition and launcher hook namespaces are retained internally.
The installed application ID and authentication IPC names are independent.
UniversalAuth hook sources live in app/src/main/java/ax/nd/universalauth/xposed.
The upstream standalone Xposed APK scaffold is not part of this project.

Licenses are preserved in LICENSE.md, NOTICE, licenses/, and the APK assets.
The avatar in app/src/main/res/drawable-nodpi/setren_avatar.jpg was supplied by
the project author; no redistribution license is inferred from the code licenses.
