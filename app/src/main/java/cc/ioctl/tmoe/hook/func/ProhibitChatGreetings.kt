package cc.ioctl.tmoe.hook.func

import android.view.View
import android.widget.LinearLayout
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.AdditionalUiHookSymbols
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClass
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull
import java.lang.reflect.Field
import java.lang.reflect.Method

//https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/ChatGreetingsView.java
@FunctionHookEntry
object ProhibitChatGreetings : CommonDynamicHook() {

    override fun initOnce(): Boolean {
        val named = loadClassOrNull("org.telegram.ui.Components.ChatGreetingsView")
        val symbols = if (named == null) AdditionalUiHookSymbols.greetings(HostDexIndex.get()) else null
        val cgv = named ?: loadClass(symbols!!.className)
        val fields = (symbols?.hiddenViewFields ?: listOf("stickerToSendView", "descriptionView"))
            .map { cgv.getDeclaredField(it).apply { isAccessible = true } }
        for (method in cgv.declaredMethods) {
            when (method.name) {
                "onMeasure" -> onMeasure(method, fields)
                "setListener",
                "setSticker",
                "fetchSticker" -> {
                    method.hookBefore {
                        if (!isEnabled) return@hookBefore
                        it.result = null
                    }
                }


            }
        }
        return true
    }

    private fun onMeasure(method: Method, fields: List<Field>) {
        method.hookAfter {
            if (!isEnabled) return@hookAfter

            fields.forEach { field -> (field.get(it.thisObject) as View).visibility = View.GONE }

            val chatGreetingsView = it.thisObject as LinearLayout
            chatGreetingsView.background = null

        }
    }
}
