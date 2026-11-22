package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.ContentHookSymbols
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import com.github.kyuubiran.ezxhelper.utils.*

@FunctionHookEntry
object ChannelDetailNumbers : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrLogFalse {
        //频道关注者显示详细人数 3k → 3000
        val legacy = loadClassOrNull("org.telegram.messenger.LocaleController")
        val method = if (legacy != null) {
            findMethod(legacy) { name == "formatShortNumber" && parameterTypes.size == 2 }
        } else {
            HostDexIndex.resolve(ContentHookSymbols.shortNumber(HostDexIndex.get()))
        }
        // R8 can reorder the two parameters while retaining both types.
        val numberIndex = method.parameterTypes.indexOf(Int::class.javaPrimitiveType)
        val roundedIndex = method.parameterTypes.indexOf(IntArray::class.java)
        check(numberIndex >= 0 && roundedIndex >= 0)
        method.hookBefore {
            if (!isEnabled) return@hookBefore

            val number = it.args[numberIndex] as Int
            val rounded = it.args[roundedIndex] as IntArray?
            if (rounded != null) {
                rounded[0] = number
            }
            it.result = number.toString()
        }


    }
}
