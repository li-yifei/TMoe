package cc.ioctl.tmoe.hook.func

import android.os.Bundle
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.util.HookUtils
import cc.ioctl.tmoe.util.Initiator
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.InteractionHookSymbols

@FunctionHookEntry
object ViewTopicAsMsgByDefault : CommonDynamicHook() {

    override fun initOnce(): Boolean {
        val symbols = if (Initiator.load("org.telegram.ui.DialogsActivity") == null ||
            Initiator.load("org.telegram.ui.TopicsFragment") == null ||
            Initiator.load("org.telegram.ui.ChatActivity") == null) {
            InteractionHookSymbols.topics(HostDexIndex.get())
        } else null
        val kTopicsFragment = Initiator.loadClass(symbols?.topicsClass ?: "org.telegram.ui.TopicsFragment")
        val kChatActivity = Initiator.loadClass(symbols?.chatClass ?: "org.telegram.ui.ChatActivity")
        val getArguments_BaseFragment = kTopicsFragment.getMethod("getArguments")
        val presentFragment_DialogsActivity = symbols?.let { HostDexIndex.resolve(it.present) } ?:
            Initiator.loadClass("org.telegram.ui.DialogsActivity").declaredMethods.single {
                it.name == "presentFragment" && it.parameterTypes.size == 1
            }
        val isTopic_ChatActivity = kChatActivity.getDeclaredField(symbols?.isTopic?.name ?: "isTopic").apply {
            isAccessible = true
        }
        HookUtils.hookBeforeIfEnabled(this, presentFragment_DialogsActivity) { param ->
            val fragment = param.args[0]
            val args = getArguments_BaseFragment.invoke(fragment) as Bundle?
            if (args == null || args.getBoolean("forward_to", false) || args.getBoolean("for_select", false)) {
                // do not intercept forward and select
                return@hookBeforeIfEnabled
            }
            if (fragment.javaClass == kTopicsFragment) {
                val chatId = args.getLong("chat_id", 0)
                if (chatId == 0L) {
                    // do not intercept chat_id == 0
                    return@hookBeforeIfEnabled
                }
                val newBundle = Bundle().apply {
                    putLong("chat_id", chatId)
                }
                val newFragment = kChatActivity.getConstructor(Bundle::class.java).newInstance(newBundle)
                param.args[0] = newFragment
                // control flow continues.
            } else if (fragment.javaClass == kChatActivity) {
                if (isTopic_ChatActivity.getBoolean(fragment)) {
                    // unset forum by replacing with a new fragment
                    val newFragment = kChatActivity.getConstructor(Bundle::class.java).newInstance(args)
                    param.args[0] = newFragment
                }
            }
        }
        return true
    }

}
