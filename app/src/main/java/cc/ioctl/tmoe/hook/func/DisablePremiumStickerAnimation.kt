package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.StickerReactionResolver
import com.github.kyuubiran.ezxhelper.utils.*

@FunctionHookEntry
object DisablePremiumStickerAnimation : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrFalse {
        // 禁用 Premium 贴纸动画
        val named = loadClassOrNull("org.telegram.messenger.MessageObject")
        val method = if (named != null) findMethod(named) {
            name == "isPremiumSticker" && parameterTypes.size == 1
        } else HostDexIndex.resolve(StickerReactionResolver.premiumSticker(HostDexIndex.get()))
        method.hookBefore {
            if (!isEnabled)return@hookBefore

            it.result = false
        }
    }
}
