package cc.ioctl.tmoe.hook.func;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import cc.ioctl.tmoe.R;
import cc.ioctl.tmoe.hook.base.CommonDynamicHook;
import cc.ioctl.tmoe.lifecycle.Parasitics;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.rtti.deobf.InfoContainerSymbols;
import cc.ioctl.tmoe.util.HookUtils;
import cc.ioctl.tmoe.util.Initiator;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import org.jf.dexlib2.iface.reference.FieldReference;

/** Keeps Telegram's own information screens and hides unavailable management actions. */
final class OptimizedInfoContainer {
    private final CommonDynamicHook hook;
    private final Method profileMenu, editCreate, editUpdate, usersCreate, bind, enabled;
    private final Field profileChat, editVisible, editChat, adminCell, blockCell, logCell;
    private final Field usersChat, adapterOwner, holderView;
    private final Field settingsSection;
    private final ArrayList<Field> hiddenRows = new ArrayList<>();
    private final Map<View, Integer> collapsedHeights = new WeakHashMap<>();

    static void install(CommonDynamicHook hook) throws Exception {
        new OptimizedInfoContainer(hook).install();
    }

    private OptimizedInfoContainer(CommonDynamicHook hook) throws Exception {
        this.hook = hook;
        InfoContainerSymbols s = new InfoContainerSymbols(HostDexIndex.get());
        profileMenu = HostDexIndex.resolve(s.profileMenu);
        editCreate = HostDexIndex.resolve(s.editCreate);
        editUpdate = HostDexIndex.resolve(s.editUpdate);
        usersCreate = HostDexIndex.resolve(s.usersCreate);
        bind = HostDexIndex.resolve(s.bind);
        enabled = HostDexIndex.resolve(s.enabled);
        profileChat = field(s.profileChat); editVisible = field(s.editVisible);
        editChat = field(s.editChat); adminCell = field(s.adminCell);
        blockCell = field(s.blockCell); logCell = field(s.logCell);
        usersChat = field(s.usersChat); adapterOwner = field(s.adapterOwner);
        holderView = field(s.holderView);
        settingsSection = field(s.settingsSection);
        for (FieldReference row : s.hiddenRows) hiddenRows.add(field(row));
    }

    private static Field field(FieldReference reference) throws Exception {
        Field field = Initiator.loadClass(reference.getDefiningClass()).getDeclaredField(reference.getName());
        field.setAccessible(true);
        return field;
    }

    private static boolean hasAdminRights(Object chat) throws Exception {
        if (chat == null) return false;
        if (chat.getClass().getField("creator").getBoolean(chat)) return true;
        Object rights = chat.getClass().getField("admin_rights").get(chat);
        return rights != null && rights.getClass().getField("flags").getInt(rights) != 0;
    }

    private void install() throws Exception {
        HookUtils.hookAfterIfEnabled(hook, profileMenu, param -> {
            Object chat = profileChat.get(param.thisObject);
            if (chat == null || !(hasAdminRights(chat)
                    || chat.getClass().getField("megagroup").getBoolean(chat))) return;
            editVisible.setBoolean(param.thisObject, true);
            for (View view : views(param.thisObject)) {
                if (!Integer.valueOf(12).equals(view.getTag())) continue;
                Parasitics.injectModuleResources(view.getResources());
                view.setVisibility(View.VISIBLE);
                view.setAlpha(1f);
                view.getClass().getMethod("setIcon", int.class).invoke(view, R.drawable.ic_setting_hex_outline_24);
            }
        });
        HookUtils.hookAfterIfEnabled(hook, editCreate, param -> exposeInformation(param.thisObject));
        HookUtils.hookAfterIfEnabled(hook, editUpdate, param -> exposeInformation(param.thisObject));
        HookUtils.hookAfterIfEnabled(hook, usersCreate, param -> {
            Object chat = usersChat.get(param.thisObject);
            if (chat == null || hasAdminRights(chat)) return;
            Bundle args = (Bundle) param.thisObject.getClass().getMethod("getArguments").invoke(param.thisObject);
            int type = args.getInt("type");
            if (type == 0 || type == 3) {
                for (View view : views(param.thisObject)) {
                    if (Integer.valueOf(0).equals(view.getTag()) || Integer.valueOf(1).equals(view.getTag()))
                        view.setVisibility(View.GONE);
                }
            }
        });
        XposedBridge.hookMethod(bind, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) holderView.get(param.args[0]);
                Integer height = collapsedHeights.remove(view);
                if (height != null) {
                    ViewGroup.LayoutParams layout = view.getLayoutParams();
                    layout.height = height;
                    view.setLayoutParams(layout);
                    view.setVisibility(View.VISIBLE);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!hook.isEnabled()) return;
                Object fragment = adapterOwner.get(param.thisObject);
                Object chat = usersChat.get(fragment);
                if (chat == null || hasAdminRights(chat)) return;
                int position = (int) param.args[1];
                for (Field row : hiddenRows) {
                    if (row.getInt(fragment) != position) continue;
                    View view = (View) holderView.get(param.args[0]);
                    ViewGroup.LayoutParams layout = view.getLayoutParams();
                    collapsedHeights.put(view, layout.height);
                    layout.height = 0;
                    view.setLayoutParams(layout);
                    view.setVisibility(View.GONE);
                    break;
                }
            }
        });
        HookUtils.hookAfterIfEnabled(hook, enabled, param -> {
            if (collapsedHeights.containsKey((View) holderView.get(param.args[0]))) param.setResult(false);
        });
    }

    private void exposeInformation(Object fragment) throws Exception {
        Object chat = editChat.get(fragment);
        if (chat == null) return;
        View admin = (View) adminCell.get(fragment);
        if (admin == null) return;
        if (admin.getParent() instanceof ViewGroup) ((ViewGroup) admin.getParent()).setVisibility(View.VISIBLE);
        View section = (View) settingsSection.get(fragment);
        if (section != null) section.setVisibility(View.VISIBLE);
        View block = (View) blockCell.get(fragment);
        if (block != null) block.setVisibility(View.VISIBLE);
        if (hasAdminRights(chat)) return;
        View log = (View) logCell.get(fragment);
        if (log != null) {
            if (log.getParent() instanceof ViewGroup) ((ViewGroup) log.getParent()).removeView(log);
            logCell.set(fragment, null);
        }
        View value = (View) admin.getClass().getMethod("getValueTextView").invoke(admin);
        value.getClass().getMethod("setText", CharSequence.class).invoke(value, "**");
        value.setVisibility(View.VISIBLE);
    }

    private static ArrayList<View> views(Object fragment) throws IllegalAccessException {
        ArrayList<View> result = new ArrayList<>();
        for (Field field : fragment.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !View.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(fragment);
            if (value != null) result.add((View) value);
        }
        return result;
    }
}
