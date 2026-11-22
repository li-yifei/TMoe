package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.InteractionHookSymbols
import com.github.kyuubiran.ezxhelper.utils.*

@FunctionHookEntry
object ProhibitSpoilers : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrLogFalse {
        if (loadClassOrNull("org.telegram.messenger.MessageObject") == null ||
            loadClassOrNull("org.telegram.ui.Components.spoilers.SpoilerEffect") == null) {
            val targets = InteractionHookSymbols.spoilers(HostDexIndex.get()).map(HostDexIndex::resolve)
            targets[0].hookBefore { if (isEnabled) it.result = null }
            targets[1].hookBefore { if (isEnabled) it.result = false }
            return@tryOrLogFalse
        }

        findAllMethods("org.telegram.ui.Components.spoilers.SpoilerEffect"){ name=="addSpoilers" }.hookBefore {
            if (isEnabled) it.result=null
        }

        //  org.telegram.ui.Cells.ChatMessageCell.drawBlurredPhoto(ChatMessageCell.java)
        findMethod("org.telegram.messenger.MessageObject") { name == "hasMediaSpoilers" }.hookBefore {
            if (isEnabled) it.result = false
        }


    }
}
