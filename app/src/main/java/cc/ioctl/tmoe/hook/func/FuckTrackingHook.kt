package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClass

@FunctionHookEntry
object FuckTrackingHook : CommonDynamicHook() {
    override fun initOnce(): Boolean {
        val requestClass = loadClass("org.telegram.tgnet.TLRPC\$TL_messages_clickSponsoredMessage")
        val requestBase = loadClass("org.telegram.tgnet.TLObject")
        val methods = loadClass("org.telegram.tgnet.ConnectionsManager").declaredMethods.filter {
            it.name == "sendRequest" && it.returnType == Int::class.javaPrimitiveType &&
                it.parameterTypes.size >= 2 && it.parameterTypes[0] == requestBase
        }
        check(methods.isNotEmpty()) { "ConnectionsManager.sendRequest was not found" }
        methods.forEach { method ->
            method.hookBefore {
                if (isEnabled && requestClass.isInstance(it.args[0])) {
                    // Click telemetry has no completion delegate in Telegram; zero means no token.
                    it.result = 0
                }
            }
        }
        return true
    }
}
