package cc.ioctl.tmoe.hook.func

import android.text.TextUtils
import android.widget.TextView
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex
import cc.ioctl.tmoe.rtti.deobf.PrivacyReactionResolver
import com.github.kyuubiran.ezxhelper.utils.*

@FunctionHookEntry
object HidePhoneNumber : CommonDynamicHook() {

    //ProfileActivity.java hidePhone = true;          updateListAnimated(false);
    override fun initOnce(): Boolean = tryOrLogFalse {
        if (loadClassOrNull("org.telegram.ui.Cells.DrawerProfileCell") == null) {
            val symbols = PrivacyReactionResolver.phoneHeader(HostDexIndex.get())
            val update = HostDexIndex.resolve(symbols.update)
            val subtitle = update.declaringClass.getDeclaredField(symbols.subtitle.name).apply {
                isAccessible = true
            }
            update.hookAfter { param ->
                if (isEnabled) {
                    val text = subtitle.get(param.thisObject) as? TextView ?: return@hookAfter
                    // The modern settings header replaces the removed navigation drawer.
                    // Keep Telegram's selected public username, including secondary usernames.
                    val value = text.text.toString()
                    when {
                        value.contains(" • @") -> text.text = value.substringAfter(" • ")
                        value.startsWith("+") -> text.text = "@???"
                    }
                }
            }
            return@tryOrLogFalse
        }
        findMethod(loadClass("org.telegram.ui.Cells.DrawerProfileCell"), false) {
            name == "setUser" && parameterTypes.size == 2
        }.hookAfter {

            if (!isEnabled) return@hookAfter

//            Log.d("class name:  "+it.thisObject::class.java.name)
            val vvv = findField(it.thisObject::class.java, true) {
                name == "phoneTextView"
            }.get(it.thisObject)

            var username = findField(it.args[0]::class.java, true) {
                name == "username"
            }.get(it.args[0]) as String?

            username = if (TextUtils.isEmpty(username)) {
                "@???"
            } else {
                "@$username"
            }

            (vvv as TextView).text = username
//                findMethod(vvv::class.java){
//                    name=="setText"&&parameterTypes.size==1&&parameterTypes[0]==CharSequence::class.java
//                }.invoke(vvv,vvv2)


        }
    }
}
