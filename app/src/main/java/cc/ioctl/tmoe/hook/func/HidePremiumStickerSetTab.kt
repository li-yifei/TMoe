package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.StickerReactionResolver
import cc.ioctl.tmoe.util.HookUtils
import cc.ioctl.tmoe.util.Initiator
import java.util.ArrayList

@FunctionHookEntry
object HidePremiumStickerSetTab : CommonDynamicHook() {

    private const val TYPE_PREMIUM_STICKERS: Int = 7

    override fun initOnce(): Boolean {
        val kMediaDataController = Initiator.load("org.telegram.messenger.MediaDataController")
        val getRecentStickers = kMediaDataController?.declaredMethods
            ?.filter { it.name == "getRecentStickers" && it.parameterTypes.isNotEmpty()
                && it.parameterTypes[0] == Int::class.javaPrimitiveType }
            ?.maxByOrNull { it.parameterTypes.size }
            ?: HostDexIndex.resolve(StickerReactionResolver.recentStickers(HostDexIndex.get()))

        HookUtils.hookBeforeIfEnabled(this, getRecentStickers) {
            val args = it.args
            if (args[0] == TYPE_PREMIUM_STICKERS) {
                it.result = ArrayList<Any>()
            }
        }
        return true
    }

}
