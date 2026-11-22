package cc.ioctl.tmoe.hook.func;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cc.ioctl.tmoe.R;
import cc.ioctl.tmoe.hook.base.CommonDynamicHook;
import cc.ioctl.tmoe.lifecycle.Parasitics;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.rtti.deobf.ProfileMenuSymbols;
import cc.ioctl.tmoe.rtti.deobf.ReloadMenuSymbols;
import cc.ioctl.tmoe.ui.LocaleController;
import cc.ioctl.tmoe.util.HookUtils;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.Log;

/** Profile menu actions use the public arguments bundle and retained TL data fields. */
final class OptimizedProfileMenu {
    static boolean needsCompatibility() {
        try {
            Initiator.loadClass("org.telegram.ui.ProfileActivity").getDeclaredMethod("createActionBarMenu", boolean.class);
            return false;
        } catch (ReflectiveOperationException error) {
            return true;
        }
    }

    static void install(CommonDynamicHook hook, boolean showId) throws Exception {
        HostDexIndex index = HostDexIndex.get();
        ProfileMenuSymbols profile = new ProfileMenuSymbols(index);
        ReloadMenuSymbols menu = new ReloadMenuSymbols(index);
        Method create = HostDexIndex.resolve(profile.createMenu);
        Method open = HostDexIndex.resolve(profile.openLinkedChat);
        Method add = HostDexIndex.resolve(menu.addSubItem);
        Method close = HostDexIndex.resolve(menu.closeSubMenu);
        Field currentChat = create.getDeclaringClass().getDeclaredField(profile.chat.getName());
        Field chatInfo = create.getDeclaringClass().getDeclaredField(profile.chatInfo.getName());
        currentChat.setAccessible(true);
        chatInfo.setAccessible(true);
        HookUtils.hookAfterIfEnabled(hook, create, param -> {
            Object fragment = param.thisObject;
            View options = null;
            for (Field field : create.getDeclaringClass().getDeclaredFields()) {
                if (!add.getDeclaringClass().isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                View item = (View) field.get(fragment);
                // ProfileActivity uses menu ID 10 for its options item.
                if (item != null && Integer.valueOf(10).equals(item.getTag())) {
                    if (options != null && options != item) throw new IllegalStateException("Multiple profile menus");
                    options = item;
                }
            }
            if (options == null) return;
            Context context = options.getContext();
            Parasitics.injectModuleResources(context.getResources());
            View header = options;
            String text;
            String value;
            if (showId) {
                Bundle args = (Bundle) fragment.getClass().getMethod("getArguments").invoke(fragment);
                if (args == null) return;
                long user = args.getLong("user_id", 0), chat = args.getLong("chat_id", 0);
                long topic = args.getLong("topic_id", 0);
                if (user == 0 && chat == 0) return;
                value = user != 0 ? Long.toString(user)
                        : Long.toString(chat) + (topic == 0 ? "" : "/" + topic);
                text = LocaleController.getString(user != 0 ? "UserId" : "GroupOrChannelId",
                        user != 0 ? R.string.UserId : R.string.GroupOrChannelId) + ": " + value;
            } else {
                Object chat = currentChat.get(fragment), info = chatInfo.get(fragment);
                if (chat == null || info == null || !chat.getClass().getField("megagroup").getBoolean(chat)
                        || info.getClass().getField("linked_chat_id").getLong(info) == 0) return;
                value = null;
                text = LocaleController.getString("ViewChannel", R.string.ViewChannel);
            }
            View row = (View) add.invoke(header, showId ? 1001002 : 1001003,
                    R.drawable.ic_setting_hex_outline_24, text);
            row.setOnClickListener(view -> {
                if (!hook.isEnabled()) return;
                try {
                    close.invoke(header);
                    if (showId) {
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("Telegram ID", value));
                        Toast.makeText(context, LocaleController.getString("IdCopied", R.string.IdCopied),
                                Toast.LENGTH_SHORT).show();
                    } else open.invoke(fragment);
                } catch (Exception error) {
                    Log.e(error);
                    Toast.makeText(context, LocaleController.getString("UnsupportedOperation",
                            R.string.UnsupportedOperation), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
