package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.ContentHookSymbols
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import com.github.kyuubiran.ezxhelper.utils.findAllMethods
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.utils.tryOrFalse

@FunctionHookEntry
object AntiAntiForward : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrFalse {
        if (loadClassOrNull("org.telegram.messenger.MessageObject") == null) {
            // Resolve every target before installing any hook, so unsupported builds fail atomically.
            val methods = ContentHookSymbols.forwarding(HostDexIndex.get()).map(HostDexIndex::resolve)
            methods.first().hookBefore { if (isEnabled) it.result = true }
            methods.drop(1).forEach { method ->
                method.hookBefore { if (isEnabled) it.result = false }
            }
            return@tryOrFalse
        }
        findMethod("org.telegram.messenger.MessageObject") { name == "canForwardMessage" }.hookBefore {
            if (isEnabled) it.result = true
        }
        findAllMethods("org.telegram.messenger.MessageObject") { name == "isSecretMedia" }.hookBefore {
            if (isEnabled) it.result = false
        }
        findAllMethods("org.telegram.messenger.MessagesController") { name == "isChatNoForwards" }.hookBefore {
            if (isEnabled) it.result = false
        }
    }
}
