package ax.nd.universalauth.xposed

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import ax.nd.universalauth.xposed.common.XposedConstants.EXTRA_BYPASS_KEYGUARD
import ax.nd.xposedutil.XposedHelpersExt
import ax.nd.xposedutil.asAccessible
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TrustHook {
    private const val TAG = "UniversalAuthTrustHook"
    private const val FACE_AUTH_INTERACTOR_CLASS =
        "com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor"
    private const val BIOMETRIC_UNLOCK_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.phone.BiometricUnlockController"
    private const val BIOMETRIC_UNLOCK_SOURCE_CLASS =
        "com.android.systemui.keyguard.shared.model.BiometricUnlockSource"

    @Volatile
    private var modernFaceAuthInteractor: Any? = null

    @Volatile
    private var biometricUnlockController: Any? = null

    private var faceBiometricUnlockSource: Any? = null
    private var startWakeAndUnlock: java.lang.reflect.Method? = null

    private fun hookModernFaceAuthInteractor(kumClazz: Class<*>) {
        try {
            XposedBridge.log("$TAG: probing modern SystemUI face auth interactor")
            val classLoader = kumClazz.classLoader ?: ClassLoader.getSystemClassLoader()
            val clazz = classLoader.loadClass(FACE_AUTH_INTERACTOR_CLASS)
            XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    modernFaceAuthInteractor = param.thisObject
                    XposedBridge.log("$TAG: captured modern SystemUI face auth interactor constructor")
                }
            })
        } catch (throwable: Throwable) {
            XposedBridge.log("$TAG: failed to hook modern SystemUI face auth interactor")
            XposedBridge.log(throwable)
        }
    }

    private fun hookBiometricUnlockController(kumClazz: Class<*>) {
        try {
            XposedBridge.log("$TAG: probing BiometricUnlockController")
            val classLoader = kumClazz.classLoader ?: ClassLoader.getSystemClassLoader()
            val controllerClass = classLoader.loadClass(BIOMETRIC_UNLOCK_CONTROLLER_CLASS)
            val unlockSourceClass = classLoader.loadClass(BIOMETRIC_UNLOCK_SOURCE_CLASS)
            faceBiometricUnlockSource = XposedHelpers.getStaticObjectField(
                unlockSourceClass,
                "FACE_SENSOR"
            )
            startWakeAndUnlock = controllerClass.getDeclaredMethod(
                "startWakeAndUnlock",
                Int::class.javaPrimitiveType!!,
                unlockSourceClass
            ).asAccessible()
            XposedBridge.hookAllConstructors(controllerClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    biometricUnlockController = param.thisObject
                    XposedBridge.log("$TAG: captured BiometricUnlockController constructor")
                }
            })
        } catch (throwable: Throwable) {
            XposedBridge.log("$TAG: failed to hook BiometricUnlockController")
            XposedBridge.log(throwable)
        }
    }

    private fun startModernSystemUiWakeAndUnlock(kum: Any) {
        try {
            val isUnlockAllowed = XposedHelpers.callMethod(
                kum,
                "isUnlockingWithBiometricAllowed",
                true
            ) as Boolean
            if (!isUnlockAllowed) {
                Log.d(TAG, "SystemUI rejected biometric unlock due to strong-auth policy")
                return
            }
            val controller = biometricUnlockController ?: run {
                Log.w(TAG, "BiometricUnlockController is null")
                return
            }
            val source = faceBiometricUnlockSource ?: run {
                Log.w(TAG, "FACE biometric unlock source is null")
                return
            }
            val method = startWakeAndUnlock ?: run {
                Log.w(TAG, "startWakeAndUnlock method is null")
                return
            }
            val isInteractive = XposedHelpers.getBooleanField(kum, "mDeviceInteractive")
            val unlockMode = if (isInteractive) 5 else 1
            method.invoke(controller, unlockMode, source)
            Log.d(TAG, "Started modern SystemUI face wake-and-unlock mode=$unlockMode")
        } catch (throwable: Throwable) {
            Log.w(TAG, "Modern SystemUI wake-and-unlock failed", throwable)
        }
    }

    private fun markFaceAuthenticatedForModernSystemUi(kum: Any) {
        try {
            val faceAuthInteractor = modernFaceAuthInteractor
                ?: XposedHelpers.getObjectField(kum, "mFaceAuthInteractor")
                ?: run {
                    Log.w(TAG, "Modern SystemUI face auth interactor is null")
                    return
                }
            val authenticatedState = XposedHelpers.getObjectField(
                faceAuthInteractor,
                "isAuthenticated"
            ) ?: run {
                Log.w(TAG, "Modern SystemUI authenticated state is null")
                return
            }
            XposedHelpers.callMethod(authenticatedState, "setValue", true)
            Log.d(TAG, "Marked modern SystemUI face authentication state as authenticated")
        } catch (throwable: Throwable) {
            Log.w(TAG, "Modern SystemUI face authentication state was unavailable", throwable)
        }
    }

    fun hookKum(kumClazz: Class<*>) {
        hookModernFaceAuthInteractor(kumClazz)
        hookBiometricUnlockController(kumClazz)

        val contextField = kumClazz.getDeclaredField("mContext").asAccessible()
        val onFaceAuthenticated = kumClazz.getDeclaredMethod(
            "onFaceAuthenticated",
            Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        ).asAccessible()
        XposedHelpersExt.runAfterClassConstructed(kumClazz) { param ->
            val context = contextField.get(param.thisObject) as Context
            UnlockReceiver.setup(context, param.thisObject) { intent ->
                val bypassKeyguard = intent.getBooleanExtra(EXTRA_BYPASS_KEYGUARD, true)
                val requestsTrustPath = !bypassKeyguard
                val hasModernFaceAuthPath = modernFaceAuthInteractor != null
                if (requestsTrustPath || hasModernFaceAuthPath) {
                    val currentUser = Util.getCurrentUser()
                    markFaceAuthenticatedForModernSystemUi(param.thisObject)
                    onFaceAuthenticated.invoke(param.thisObject, currentUser, true)
                    if (hasModernFaceAuthPath && bypassKeyguard) {
                        startModernSystemUiWakeAndUnlock(param.thisObject)
                    } else if (hasModernFaceAuthPath) {
                        Log.d(TAG, "Face authenticated; keeping keyguard for swipe-to-dismiss")
                    }
                }
            }
        }

        /* This method doesn't actually seem to exist:
        XposedHelpers.findAndHookMethod(kumClazz, "destroy", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val context = contextField.get(param.thisObject) as Context
                UnlockReceiver.teardown(context, param.thisObject)
            }
        })*/
    }
}
