package ax.nd.universalauth.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private const val STATUS_BAR_KEYGUARD_VIEW_MANAGER_CLASS =
        "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager"
    private const val PRIMARY_BOUNCER_INTERACTOR_CLASS =
        "com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor"
    private const val SWIPE_BOUNCER_REASON = "BouncerScrimController#show"
    private const val PANEL_SWIPE_BOUNCER_REASON =
        "StatusBarKeyguardViewManager#onPanelExpansionChanged"
    private const val USER_NULL = -10000

    @Volatile
    private var modernFaceAuthInteractor: Any? = null

    @Volatile
    private var modernAuthenticatedState: Any? = null

    @Volatile
    private var authenticatedStateHookInstalled = false

    @Volatile
    private var biometricUnlockController: Any? = null

    private var faceBiometricUnlockSource: Any? = null
    private var startWakeAndUnlock: java.lang.reflect.Method? = null

    @Volatile
    private var swipeAuthenticatedUser = USER_NULL

    @Volatile
    private var swipeStateResetReceiver: BroadcastReceiver? = null

    @Volatile
    private var currentKeyguardUpdateMonitor: Any? = null

    @Volatile
    private var currentStatusBarKeyguardViewManager: Any? = null

    private fun isModernBiometricUnlockAllowed(kum: Any): Boolean =
        XposedHelpers.callMethod(kum, "isUnlockingWithBiometricAllowed", true) as Boolean

    private fun clearSwipeToDismissState(reason: String) {
        swipeAuthenticatedUser = USER_NULL
        modernAuthenticatedState?.let { state ->
            try {
                XposedHelpers.callMethod(state, "setValue", false)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to reset modern face authentication state", throwable)
            }
        }
        Log.d(TAG, "Cleared swipe-to-dismiss face authentication: $reason")
    }

    private fun hookAuthenticatedStateWrites(state: Any) {
        if (authenticatedStateHookInstalled) return
        synchronized(this) {
            if (authenticatedStateHookInstalled) return
            XposedBridge.hookAllMethods(state.javaClass, "updateState", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.thisObject !== modernAuthenticatedState) return
                    if (swipeAuthenticatedUser == USER_NULL) return
                    if (param.args.lastOrNull() == false) {
                        param.result = false
                        Log.d(TAG, "Preserved modern face authentication for swipe-to-dismiss")
                    }
                }
            })
            authenticatedStateHookInstalled = true
            XposedBridge.log("$TAG: hooked modern face authenticated StateFlow writes")
        }
    }

    private fun hookSwipeToDismissState(kumClazz: Class<*>) {
        XposedBridge.hookAllMethods(kumClazz, "getUserCanSkipBouncer", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val userId = param.args.firstOrNull() as? Int ?: return
                if (userId != swipeAuthenticatedUser) return
                try {
                    if (isModernBiometricUnlockAllowed(param.thisObject)) {
                        param.result = true
                    } else {
                        clearSwipeToDismissState("strong-auth policy changed")
                    }
                } catch (throwable: Throwable) {
                    clearSwipeToDismissState("policy validation failed")
                    Log.w(TAG, "Unable to validate swipe-to-dismiss authentication", throwable)
                }
            }
        })
    }

    private fun hookSwipeBouncerDismissal(kumClazz: Class<*>) {
        try {
            val classLoader = kumClazz.classLoader ?: ClassLoader.getSystemClassLoader()
            val clazz = classLoader.loadClass(STATUS_BAR_KEYGUARD_VIEW_MANAGER_CLASS)
            XposedHelpersExt.runAfterClassConstructed(clazz) { param ->
                currentStatusBarKeyguardViewManager = param.thisObject
            }
            XposedBridge.hookAllMethods(clazz, "showPrimaryBouncer", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (swipeAuthenticatedUser == USER_NULL) return
                    val reason = param.args.firstOrNull() as? String
                    Log.d(TAG, "Bouncer requested while face-authenticated: reason=$reason")
                    if (reason != SWIPE_BOUNCER_REASON) return
                    try {
                        if (completeFaceAuthenticatedSwipe(param.thisObject)) param.result = null
                    } catch (throwable: Throwable) {
                        Log.w(TAG, "Unable to complete face-authenticated swipe; showing bouncer", throwable)
                    }
                }
            })
            val primaryBouncerInteractor = classLoader.loadClass(PRIMARY_BOUNCER_INTERACTOR_CLASS)
            XposedBridge.hookAllMethods(primaryBouncerInteractor, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (swipeAuthenticatedUser == USER_NULL) return
                    val reason = param.args.firstOrNull() as? String
                    Log.d(TAG, "Primary bouncer requested while face-authenticated: reason=$reason")
                    if (reason != PANEL_SWIPE_BOUNCER_REASON) return
                    try {
                        val manager = currentStatusBarKeyguardViewManager ?: return
                        if (completeFaceAuthenticatedSwipe(manager)) param.result = false
                    } catch (throwable: Throwable) {
                        Log.w(TAG, "Unable to complete panel-swipe authentication; showing bouncer", throwable)
                    }
                }
            })
            XposedBridge.log("$TAG: hooked swipe bouncer dismissal")
        } catch (throwable: Throwable) {
            XposedBridge.log("$TAG: failed to hook swipe bouncer dismissal")
            XposedBridge.log(throwable)
        }
    }

    private fun completeFaceAuthenticatedSwipe(statusBarKeyguardViewManager: Any): Boolean {
        val kum = currentKeyguardUpdateMonitor ?: return false
        if (!isModernBiometricUnlockAllowed(kum)) {
            clearSwipeToDismissState("strong-auth policy rejected swipe")
            return false
        }
        val mediatorCallback = XposedHelpers.getObjectField(
            statusBarKeyguardViewManager,
            "mViewMediatorCallback"
        )
        val mediator = XposedHelpers.getObjectField(mediatorCallback, "this$0")
        XposedHelpers.callMethod(mediator, "handleKeyguardDone")
        Log.d(TAG, "Completed face-authenticated swipe without showing bouncer")
        return true
    }

    private fun setupSwipeStateResetReceiver(context: Context) {
        if (swipeStateResetReceiver != null) return
        synchronized(this) {
            if (swipeStateResetReceiver != null) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    clearSwipeToDismissState(intent?.action ?: "unknown")
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            swipeStateResetReceiver = receiver
        }
    }

    private fun hookModernFaceAuthInteractor(kumClazz: Class<*>) {
        try {
            XposedBridge.log("$TAG: probing modern SystemUI face auth interactor")
            val classLoader = kumClazz.classLoader ?: ClassLoader.getSystemClassLoader()
            val clazz = classLoader.loadClass(FACE_AUTH_INTERACTOR_CLASS)
            XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    modernFaceAuthInteractor = param.thisObject
                    modernAuthenticatedState = XposedHelpers.getObjectField(
                        param.thisObject,
                        "isAuthenticated"
                    )
                    modernAuthenticatedState?.let(::hookAuthenticatedStateWrites)
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
            val isUnlockAllowed = isModernBiometricUnlockAllowed(kum)
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
            val authenticatedState = modernAuthenticatedState
                ?: XposedHelpers.getObjectField(faceAuthInteractor, "isAuthenticated")
                ?: run {
                Log.w(TAG, "Modern SystemUI authenticated state is null")
                return
            }
            modernAuthenticatedState = authenticatedState
            hookAuthenticatedStateWrites(authenticatedState)
            XposedHelpers.callMethod(authenticatedState, "setValue", true)
            Log.d(TAG, "Marked modern SystemUI face authentication state as authenticated")
        } catch (throwable: Throwable) {
            Log.w(TAG, "Modern SystemUI face authentication state was unavailable", throwable)
        }
    }

    fun hookKum(kumClazz: Class<*>) {
        hookModernFaceAuthInteractor(kumClazz)
        hookBiometricUnlockController(kumClazz)
        hookSwipeToDismissState(kumClazz)
        hookSwipeBouncerDismissal(kumClazz)

        val contextField = kumClazz.getDeclaredField("mContext").asAccessible()
        val onFaceAuthenticated = kumClazz.getDeclaredMethod(
            "onFaceAuthenticated",
            Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        ).asAccessible()
        XposedHelpersExt.runAfterClassConstructed(kumClazz) { param ->
            currentKeyguardUpdateMonitor = param.thisObject
            val context = contextField.get(param.thisObject) as Context
            setupSwipeStateResetReceiver(context)
            UnlockReceiver.setup(context, param.thisObject) { intent ->
                val bypassKeyguard = intent.getBooleanExtra(EXTRA_BYPASS_KEYGUARD, true)
                val requestsTrustPath = !bypassKeyguard
                val hasModernFaceAuthPath = modernFaceAuthInteractor != null
                if (requestsTrustPath || hasModernFaceAuthPath) {
                    val currentUser = Util.getCurrentUser()
                    if (hasModernFaceAuthPath && !bypassKeyguard) {
                        if (isModernBiometricUnlockAllowed(param.thisObject)) {
                            swipeAuthenticatedUser = currentUser
                            Log.d(TAG, "Armed swipe-to-dismiss face authentication for user=$currentUser")
                        } else {
                            clearSwipeToDismissState("strong-auth policy rejected authentication")
                        }
                    }
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
