package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.PrivacyReactionResolver
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.field
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.loadClass
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.utils.method
import com.github.kyuubiran.ezxhelper.utils.staticMethod
import com.github.kyuubiran.ezxhelper.utils.tryOrLogFalse

@FunctionHookEntry
object HideServiceStories : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrLogFalse {
        if (loadClassOrNull("org.telegram.messenger.UserObject") == null) {
            val index = HostDexIndex.get()
            val service = HostDexIndex.resolve(PrivacyReactionResolver.isService(index))
            val getUser = HostDexIndex.resolve(PrivacyReactionResolver.getUser(index))
            val hidden = getUser.returnType.getField("stories_hidden")
            getUser.hookAfter { param ->
                val user = param.result
                if (isEnabled && user != null && service.invoke(null, param.args[0]) == true) {
                    hidden.setBoolean(user, true)
                }
            }
            return@tryOrLogFalse
        }
        // Telegram uses this method to decide if users can hide the stories
        // https://github.com/DrKLO/Telegram/blob/a906f12aaec2768969c77650a7e4b377baa6cf2a/TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java#L4913-L4916
        val mIsService = loadClass("org.telegram.messenger.UserObject").staticMethod(
            "isService",
            Boolean::class.java,
            argTypes(Long::class.java)
        )
        val kUser = loadClass("org.telegram.tgnet.TLRPC\$User")
        val fHidden = kUser.field("stories_hidden", false, Boolean::class.java)
        loadClass("org.telegram.messenger.MessagesController").method(
            "getUser",
            kUser,
            false,
            argTypes(Long::class.javaObjectType)
        ).hookAfter { param ->
            if (isEnabled && param.result != null && mIsService.invoke(null, param.args[0]) == true) {
                fHidden.set(param.result, true)
            }
        }
    }
}
