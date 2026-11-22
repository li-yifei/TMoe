package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.EmojiChannelResolver
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import com.github.kyuubiran.ezxhelper.utils.*

@FunctionHookEntry
object ProhibitChannelSwitching : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrLogFalse {
        val cpd = loadClassOrNull("org.telegram.ui.ChatPullingDownDrawable")
        if (cpd == null) {
            val index = HostDexIndex.get()
            val nextUnread = EmojiChannelResolver.nextUnreadChannel(index)
            val drawing = EmojiChannelResolver.channelDrawMethods(index, nextUnread)
            // Resolve every target before installing the group of hooks.
            val methods = (listOf(nextUnread) + drawing).map { HostDexIndex.resolve(it) }
            methods.forEach { method ->
                method.hookBefore { if (isEnabled) it.result = null }
            }
            return@tryOrLogFalse
        }
        for (method in cpd.declaredMethods) {
            when (method.name) {
                "getNextUnreadDialog",
                "drawBottomPanel",
                "draw"->{
                    method.hookBefore {
                        if (isEnabled) it.result=null
                    }
                }
                "showBottomPanel"->{
                    method.hookBefore {
                        if (isEnabled) it.args[0]=false
                    }
                }
                "needDrawBottomPanel"->{
                    method.hookBefore {
                        if (isEnabled) it.result=false
                    }
                }
            }
        }



    }
}
