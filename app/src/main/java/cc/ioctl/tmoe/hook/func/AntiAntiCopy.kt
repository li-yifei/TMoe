package cc.ioctl.tmoe.hook.func

import android.app.AndroidAppHelper
import android.widget.Toast
import cc.ioctl.tmoe.R
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.ui.LocaleController
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.InteractionHookSymbols
import com.github.kyuubiran.ezxhelper.utils.*

@FunctionHookEntry
object AntiAntiCopy : CommonDynamicHook() {
    private var isOF=true
    var isNoForw=false

    override fun initOnce(): Boolean = tryOrLogFalse {
        val optimized = if (loadClassOrNull("org.telegram.ui.ChatActivity") == null ||
            loadClassOrNull("org.telegram.messenger.MessagesController") == null) {
            InteractionHookSymbols.copyWithoutForwarding(HostDexIndex.get()).map(HostDexIndex::resolve)
        } else null
        val restrictionMethods = optimized?.let { listOf(it[0]) } ?:
            findAllMethods("org.telegram.messenger.MessagesController") { name == "isChatNoForwards" }.toList()
        val processOption = optimized?.get(1) ?:
            findMethod("org.telegram.ui.ChatActivity") { name == "processSelectedOption" }
        val openForward = optimized?.get(2) ?:
            findMethod("org.telegram.ui.ChatActivity") { name == "openForward" }

        if ( AntiAntiForward.isEnabledByUser){
            AntiAntiForward.isEnabledByUser=false
        }


        restrictionMethods.forEach { method -> method.hookAfter {
            if (isEnabled) {
                isNoForw=it.result as Boolean
                if(isOF) it.result = false
            }
        } }

        processOption.hookBefore {
            if (!isEnabled) return@hookBefore

            if (it.args[0] == 2) {
                if (isNoForw) {
                    it.result = null
                    Toast.makeText(
                        AndroidAppHelper.currentApplication().applicationContext,
                        LocaleController.getString("ForwardsRestrictedInfoGroup", R.string.ForwardsRestrictedInfoGroup),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

        openForward.hookMethod {
            before { isOF=false }
            after { isOF=true }
        }

    }
}
