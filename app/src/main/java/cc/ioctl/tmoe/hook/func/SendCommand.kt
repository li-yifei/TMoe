package cc.ioctl.tmoe.hook.func

import android.app.AlertDialog
import android.content.DialogInterface
import android.view.View
import cc.ioctl.tmoe.R
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.lifecycle.Parasitics
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.InteractionHookSymbols
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClass
import com.github.kyuubiran.ezxhelper.utils.tryOrLogFalse
import de.robv.android.xposed.XposedBridge

@FunctionHookEntry
object SendCommand : CommonDynamicHook() {

    override fun initOnce(): Boolean = tryOrLogFalse {
        val target = loadClass("org.telegram.ui.Components.ChatActivityEnterView").declaredMethods
            .singleOrNull { it.name == "setCommand" && it.parameterTypes.size == 4 }
            ?: HostDexIndex.resolve(InteractionHookSymbols.command(HostDexIndex.get()))
        target.hookBefore {
            if (!isEnabled)return@hookBefore

            try {

            val command=it.args[1] as String
            val longPress=it.args[2] as Boolean

            if (longPress)return@hookBefore


            val context = (it.thisObject as View).context
            if (command.startsWith("/")){
                val receiver = it.thisObject
                val arguments = it.args.clone()
                Parasitics.injectModuleResources(context.resources)
                AlertDialog.Builder(context)
                    .setMessage(R.string.Dialog_Message_ConfirmClickToSendBotCommand)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                      XposedBridge.invokeOriginalMethod(target, receiver, arguments)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()

                it.result=null
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        }

    }
}
