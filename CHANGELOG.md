# Changelog

## 1.8-a16fix12

- Handle Android 16's direct `PrimaryBouncerInteractor` swipe path used by panel expansion.
- Dismiss the keyguard only for `StatusBarKeyguardViewManager#onPanelExpansionChanged` after successful face authentication.
- Stop face authentication unconditionally on screen-off and user-present events so AOD never starts a new camera scan.

## 1.8-a16fix11

- Handle the Android 16 lock-screen swipe bouncer request after successful custom-face authentication.
- Limit bouncer suppression to the exact `BouncerScrimController#show` swipe reason.
- Complete dismissal through KeyguardViewMediator only while temporary face authorization and strong-auth policy are both valid.

## 1.8-a16fix10

- Preserve the Android 16 modern face-auth StateFlow until swipe-to-dismiss completes.
- Prevent native FaceManager failure callbacks from erasing a successful custom-face result.
- Explicitly reset the protected state on screen-off, successful entry, or strong-auth rejection.

## 1.8-a16fix9

- Preserve Android 16 custom-face authentication until swipe-to-dismiss completes.
- Clear the temporary swipe authorization on screen-off or successful device entry.
- Re-check Android strong-auth policy whenever SystemUI asks whether the bouncer can be skipped.

## 1.8-a16fix8

- Respect the Auto-dismiss lock screen preference on Android 16.
- When auto-dismiss is disabled, authenticate the face but keep the keyguard visible until the user swipes.

## 1.8-a16fix7

- Add an Android 16 SystemUI face-authentication and wake-and-unlock path.
- Keep Android strong-auth requirements intact.
- Register the unlock receiver with explicit exported behavior on modern Android.
- Migrate the face-unlock app to Material 3 Expressive with dynamic color.
- Add Simplified Chinese strings for the main screen, settings, status, and enrollment flow.
- Compile against Android API 36 and update the Android build toolchain.
