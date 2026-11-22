package cc.ioctl.tmoe.hook.func

import android.view.View
import android.widget.Toast
import cc.ioctl.tmoe.R
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.lifecycle.Parasitics
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.StickerReactionResolver
import cc.ioctl.tmoe.util.HostInfo
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.utils.tryOrLogFalse
import java.util.Objects
import java.util.WeakHashMap

@FunctionHookEntry
object ProhibitEnableReactions : CommonDynamicHook() {

    private val enabledReactionsList: List<Objects> = ArrayList()
    override fun initOnce(): Boolean = tryOrLogFalse {
        if (loadClassOrNull("org.telegram.ui.ChatActivity") == null
            || loadClassOrNull("org.telegram.messenger.MediaDataController") == null) {
            val symbols = StickerReactionResolver.reactions(HostDexIndex.get())
            val select = HostDexIndex.resolve(symbols.select)
            val doubleTapIndex = select.parameterTypes.indexOf(Boolean::class.javaPrimitiveType)
            require(doubleTapIndex >= 0) { "Missing selectReaction double-tap argument" }
            val setMessage = HostDexIndex.resolve(symbols.setMessage)
            val attached = HostDexIndex.resolve(symbols.onAttached)
            val notification = HostDexIndex.resolve(symbols.notification)
            val type = setMessage.declaringClass.getDeclaredField(symbols.containerType.name).apply {
                isAccessible = true
            }
            val hiddenPickers = WeakHashMap<View, Int>()
            fun updatePicker(view: View) {
                val pickerType = type.getInt(view)
                if (pickerType != 0 && pickerType != 3) return
                if (isEnabled) {
                    if (view.visibility != View.GONE || !hiddenPickers.containsKey(view)) {
                        hiddenPickers[view] = view.visibility
                    }
                    view.visibility = View.GONE
                } else {
                    hiddenPickers.remove(view)?.let { view.visibility = it }
                }
            }
            // Apply at the chat picker boundary. Notifications can make a waiting picker
            // visible again, so reapply after that callback as well as message binding.
            listOf(setMessage, attached, notification).forEach { method ->
                method.hookAfter { updatePicker(it.thisObject as View) }
            }
            select.hookBefore {
                if (isEnabled) {
                    it.result = null
                    if (it.args[doubleTapIndex] == true) showDoubleTapNotice()
                }
            }
            return@tryOrLogFalse
        }

        // The first boolean is fromDoubleTap in both the older 7/9-argument
        // signatures and the modern 11-argument signature with a leading cell View.
        val select = findMethod("org.telegram.ui.ChatActivity") { name == "selectReaction" }
        val doubleTapIndex = select.parameterTypes.indexOf(Boolean::class.javaPrimitiveType)
        require(doubleTapIndex >= 0) { "Missing selectReaction double-tap argument" }
        val enabledList = findMethod("org.telegram.messenger.MediaDataController") {
            name == "getEnabledReactionsList"
        }
        enabledList.hookBefore { if (isEnabled) it.result = enabledReactionsList }
        select.hookBefore {
            if (isEnabled) {
                it.result = null
                if (it.args[doubleTapIndex] == true) showDoubleTapNotice()
            }
        }
    }

    private fun showDoubleTapNotice() {
        val context = HostInfo.getApplication()
        Parasitics.injectModuleResources(context.resources)
        Toast.makeText(context, R.string.DebugVerbose_DoubleTapDetected, Toast.LENGTH_SHORT).show()
    }
}
