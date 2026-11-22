package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.AdditionalUiHookSymbols
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull

@FunctionHookEntry
object ForceBlurChatAvailable : CommonDynamicHook() {
    override fun initOnce(): Boolean {
        val method = loadClassOrNull("org.telegram.messenger.SharedConfig")?.declaredMethods
            ?.singleOrNull { it.name == "canBlurChat" && it.parameterTypes.isEmpty() }
            ?: HostDexIndex.resolve(AdditionalUiHookSymbols.canBlurChat(HostDexIndex.get()))
        method.hookBefore {
            if (!isEnabled)return@hookBefore

            it.result = true
        }
        return true
    }
}
