package cc.ioctl.tmoe.hook.func;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import cc.ioctl.tmoe.R;
import cc.ioctl.tmoe.hook.base.CommonDynamicHook;
import cc.ioctl.tmoe.lifecycle.Parasitics;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.rtti.deobf.ReloadMenuSymbols;
import cc.ioctl.tmoe.ui.LocaleController;
import cc.ioctl.tmoe.util.HookUtils;
import cc.ioctl.tmoe.util.Log;

final class OptimizedReloadMessages {
    private static final int RELOAD_ID = 1001001;

    static void install(CommonDynamicHook hook) throws Exception {
        ReloadMenuSymbols symbols = new ReloadMenuSymbols(HostDexIndex.get());
        Method createView = HostDexIndex.resolve(symbols.createView);
        Method jumpToDate = HostDexIndex.resolve(symbols.jumpToDate);
        Method addSubItem = HostDexIndex.resolve(symbols.addSubItem);
        Method closeSubMenu = HostDexIndex.resolve(symbols.closeSubMenu);
        List<Field> menuFields = new ArrayList<>();
        for (Field field : createView.getDeclaringClass().getDeclaredFields()) {
            if (addSubItem.getDeclaringClass().isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                menuFields.add(field);
            }
        }
        HookUtils.hookAfterIfEnabled(hook, createView, param -> {
            Context context = (Context) param.args[0];
            Object chat = param.thisObject;
            View header = null;
            for (Field field : menuFields) {
                View candidate = (View) field.get(chat);
                // Telegram's chat_menu_options ID is also the menu view's public tag.
                if (candidate != null && Integer.valueOf(-2).equals(candidate.getTag())) {
                    if (header != null && header != candidate)
                        throw new IllegalStateException("Multiple chat options menus");
                    header = candidate;
                }
            }
            if (header == null) return; // Embedded/special chat modes do not expose this menu.
            Parasitics.injectModuleResources(context.getResources());
            View menu = header;
            View cell = (View) addSubItem.invoke(menu, RELOAD_ID, R.drawable.ic_refresh_24,
                    LocaleController.getString("ReloadMessage", R.string.ReloadMessage));
            cell.setOnClickListener(view -> {
                if (!hook.isEnabled()) return;
                try {
                    closeSubMenu.invoke(menu);
                    jumpToDate.invoke(chat, Math.toIntExact(System.currentTimeMillis() / 1000L + 1));
                } catch (Exception error) {
                    Log.e(error);
                    new AlertDialog.Builder(context).setMessage(error.toString())
                            .setPositiveButton(android.R.string.ok, null).show();
                }
            });
            ViewGroup parent = (ViewGroup) cell.getParent();
            if (parent != null && parent.getChildCount() > 1) {
                parent.removeView(cell);
                parent.addView(cell, 1);
            }
        });
    }
}
