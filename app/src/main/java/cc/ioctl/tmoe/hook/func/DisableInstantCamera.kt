package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.AdditionalUiHookSymbols
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClass

@FunctionHookEntry
object DisableInstantCamera : CommonDynamicHook() {
    override fun initOnce(): Boolean {
        val enterView = loadClass("org.telegram.ui.Components.ChatActivityEnterView")
        val getters = enterView.declaredMethods.filter {
            it.parameterTypes.isEmpty() && (it.name == "isInVideoMode" || it.name == "hasRecordVideo")
        }
        if (getters.size == 2) {
            getters.forEach { method ->
                method.hookBefore { if (isEnabled) it.result = false }
            }
        } else {
            // R8 inlines the getters. Keep the underlying recording state in voice mode.
            val symbols = AdditionalUiHookSymbols.cameraMode(HostDexIndex.get())
            val capability = enterView.getDeclaredField(symbols.hasRecordVideo.name).apply {
                isAccessible = true
            }
            HostDexIndex.resolve(symbols.setter).hookBefore {
                if (isEnabled) {
                    capability.setBoolean(it.thisObject, false)
                    it.args[0] = false
                }
            }
        }
        return true
    }
}
