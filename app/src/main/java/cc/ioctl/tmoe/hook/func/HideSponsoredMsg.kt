package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.SponsoredMessageResolver
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.utils.loadAndFindMethods

/**
 * From `https://github.com/shatyuka/Killergram`
 */
@FunctionHookEntry
object HideSponsoredMsg : CommonDynamicHook() {
    override fun initOnce(): Boolean {
        if (loadClassOrNull("org.telegram.messenger.MessagesController") != null
            && loadClassOrNull("org.telegram.ui.ChatActivity") != null) {
            arrayOf(
                "org.telegram.messenger.MessagesController",
                "org.telegram.ui.ChatActivity"
            ).loadAndFindMethods {
                name.contains("SponsoredMessages")
            }.hookBefore { if (isEnabled) it.result = null }
        } else {
            // Returning null also prevents ChatActivity from inserting cached sponsored rows.
            val getter = SponsoredMessageResolver.findGetter(HostDexIndex.get())
            HostDexIndex.resolve(getter).hookBefore {
                if (isEnabled) it.result = null
            }
        }
        return true
    }
}
