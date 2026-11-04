// Modified for Setren integration; unused tracing helpers removed.
package ax.nd.xposedutil

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.*

object XposedHelpersExt {
    /**
     * Run hook after class is constructed, doesn't matter how many constructors the class has.
     * It will always run after last constructor.
     *
     * Will cause problems if the object attempts to construct an instance of itself inside it's constructor
     */
    fun runAfterClassConstructed(clazz: Class<*>, hook: (param: XC_MethodHook.MethodHookParam) -> Unit) {
        val uuid = UUID.randomUUID().toString()

        fun incConstructorRan(target: Any) {
            val cur = XposedHelpers.getAdditionalInstanceField(target, uuid) as? Int ?: 0
            XposedHelpers.setAdditionalInstanceField(target, uuid, cur + 1)
        }
        fun decConstructorRan(target: Any): Boolean {
            val cur = XposedHelpers.getAdditionalInstanceField(target, uuid) as? Int
            if(cur == null || cur == 0) {
                Log.w(DEBUG_LOG_TAG, "decConstructorRan instance field is $cur!")
                return false // Never run hook
            }
            return if(cur == 1) {
                XposedHelpers.removeAdditionalInstanceField(target, uuid)
                true
            } else {
                val newValue = cur - 1
                XposedHelpers.setAdditionalInstanceField(target, uuid, newValue)
                false
            }
        }

        XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                incConstructorRan(param.thisObject)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if(decConstructorRan(param.thisObject)) {
                    hook(param)
                }
            }
        })
    }
}