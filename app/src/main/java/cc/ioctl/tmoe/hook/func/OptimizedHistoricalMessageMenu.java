package cc.ioctl.tmoe.hook.func;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.jf.dexlib2.iface.reference.FieldReference;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

import cc.ioctl.tmoe.R;
import cc.ioctl.tmoe.lifecycle.Parasitics;
import cc.ioctl.tmoe.rtti.deobf.HistoricalMenuSymbols;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.ui.LocaleController;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.dex.DexFieldDescriptor;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** The optimized host retains the public menu-cell API and its own search/forward actions. */
final class OptimizedHistoricalMessageMenu {
    private final BooleanSupplier enabled;
    private final Method createMenu, processOption, openSearch, searchSender, showSearchList;
    private final Method getUser, getChat, forward, groupedMessage;
    private final Method sendMessage, textParams, sendSticker, getInputUser;
    private final Field selectedMessage, messageOwner, groupMessages, dialogId, threadMessage;
    private final Constructor<?> menuCell;
    /** setTextAndIcon also shows the icon and pads the label; setText/setIcon alone leave them overlapping. */
    private final Method setTextAndIcon;
    private final ThreadLocal<ArrayDeque<Scope>> menus = ThreadLocal.withInitial(ArrayDeque::new);

    static void install(BooleanSupplier enabled) throws Exception {
        new OptimizedHistoricalMessageMenu(enabled).install();
    }

    private OptimizedHistoricalMessageMenu(BooleanSupplier enabled) throws Exception {
        this.enabled = enabled;
        HistoricalMenuSymbols symbols = new HistoricalMenuSymbols(HostDexIndex.get());
        createMenu = HostDexIndex.resolve(symbols.createMenu);
        processOption = HostDexIndex.resolve(symbols.processOption);
        openSearch = HostDexIndex.resolve(symbols.openSearch);
        searchSender = HostDexIndex.resolve(symbols.searchSender);
        showSearchList = HostDexIndex.resolve(symbols.showSearchList);
        getUser = HostDexIndex.resolve(symbols.getUser);
        getChat = HostDexIndex.resolve(symbols.getChat);
        forward = HostDexIndex.resolve(symbols.forward);
        groupedMessage = HostDexIndex.resolve(symbols.groupedMessage);
        sendMessage = HostDexIndex.resolve(symbols.sendMessage);
        textParams = HostDexIndex.resolve(symbols.textParams);
        sendSticker = HostDexIndex.resolve(symbols.sendSticker);
        getInputUser = HostDexIndex.resolve(symbols.getInputUser);
        selectedMessage = field(symbols.selectedMessage);
        messageOwner = field(symbols.messageOwner);
        groupMessages = field(symbols.groupMessages);
        dialogId = field(symbols.dialogId);
        threadMessage = field(symbols.threadMessage);
        Class<?> cell = Initiator.loadClass(symbols.menuCellConstructor.getDefiningClass());
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : cell.getDeclaredConstructors()) {
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length != symbols.menuCellConstructor.getParameterTypes().size()) continue;
            boolean matches = true;
            for (int i = 0; i < types.length; i++) {
                matches &= DexFieldDescriptor.getTypeSig(types[i])
                        .contentEquals(symbols.menuCellConstructor.getParameterTypes().get(i));
            }
            if (matches) constructor = candidate;
        }
        if (constructor == null) throw new NoSuchMethodException("Message menu cell constructor");
        menuCell = constructor;
        menuCell.setAccessible(true);
        setTextAndIcon = findSetTextAndIcon(cell);
    }

    private static Method findSetTextAndIcon(Class<?> cell) throws NoSuchMethodException {
        try {
            return cell.getMethod("setTextAndIcon", CharSequence.class, int.class, Drawable.class);
        } catch (NoSuchMethodException ignored) {
            // R8 renames it; the (CharSequence, int, Drawable) void signature is unique on the cell.
        }
        Method found = null;
        for (Method m : cell.getDeclaredMethods()) {
            if (m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers())
                    || !Arrays.equals(m.getParameterTypes(),
                    new Class<?>[]{CharSequence.class, int.class, Drawable.class})) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous message menu setTextAndIcon");
            found = m;
        }
        if (found == null) throw new NoSuchMethodException("Message menu setTextAndIcon");
        found.setAccessible(true);
        return found;
    }

    private static Field field(FieldReference ref) throws ReflectiveOperationException {
        Field field = Initiator.loadClass(ref.getDefiningClass()).getDeclaredField(ref.getName());
        field.setAccessible(true);
        return field;
    }

    private void install() throws Exception {
        Class<?> popup = Initiator.loadClass(
                "org.telegram.ui.ActionBar.ActionBarPopupWindow$ActionBarPopupWindowLayout");
        Method addView = popup.getDeclaredMethod("addView", View.class);
        XposedBridge.hookMethod(createMenu, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                menus.get().push(new Scope(param.thisObject));
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                ArrayDeque<Scope> stack = menus.get();
                if (!stack.isEmpty()) stack.pop();
                if (stack.isEmpty()) menus.remove();
            }
        });
        XposedBridge.hookMethod(addView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Scope scope = menus.get().peek();
                if (!enabled.getAsBoolean() || scope == null
                        || !menuCell.getDeclaringClass().isInstance(param.args[0])) return;
                ViewGroup popup = (ViewGroup) param.thisObject;
                if (!scope.popups.add(popup)) return;
                Object selected = selectedMessage.get(scope.chat);
                if (selected == null) return;
                Object owner = messageOwner.get(selected);
                Object provider = scope.chat.getClass().getMethod("getResourceProvider").invoke(scope.chat);
                View history = cell(popup.getContext(), provider, "MenuItem_HistoryMessage",
                        R.string.MenuItem_HistoryMessage);
                View repeat = cell(popup.getContext(), provider, "MenuItem_RepeatMessage",
                        R.string.MenuItem_RepeatMessage);
                XposedBridge.invokeOriginalMethod(addView, popup, new Object[]{history});
                XposedBridge.invokeOriginalMethod(addView, popup, new Object[]{repeat});
                history.setOnClickListener(v -> run(v, () -> search(scope.chat, owner)));
                history.setOnLongClickListener(v -> {
                    run(v, () -> copySender(v.getContext(), scope.chat, owner));
                    return true;
                });
                repeat.setOnClickListener(v -> run(v, () -> repeat(scope.chat, selected, owner)));
            }
        });
    }

    private View cell(Context context, Object provider, String key, int label) throws Exception {
        Parasitics.injectModuleResources(context.getResources());
        Class<?>[] types = menuCell.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = types[i] == Context.class ? context : types[i] == boolean.class ? true : provider;
        }
        View view = (View) menuCell.newInstance(args);
        setTextAndIcon.invoke(view, LocaleController.getString(key, label),
                R.drawable.ic_setting_hex_outline_24, null);
        return view;
    }

    private void search(Object chat, Object owner) throws Exception {
        Object peer = owner.getClass().getField("from_id").get(owner);
        if (peer == null) throw new IllegalStateException("Message has no sender");
        long userId = peer.getClass().getField("user_id").getLong(peer);
        long chatId = peer.getClass().getField("chat_id").getLong(peer);
        long channelId = peer.getClass().getField("channel_id").getLong(peer);
        Object controller = chat.getClass().getMethod("getMessagesController").invoke(chat);
        Object user = userId != 0 ? getUser.invoke(controller, userId) : null;
        Object channel = userId == 0 ? getChat.invoke(controller, channelId != 0 ? channelId : chatId) : null;
        if (user == null && channel == null) throw new IllegalStateException("Sender is unavailable");
        processOption.invoke(chat, 999);
        openSearch.invoke(chat, "");
        Object[] args = new Object[2];
        for (int i = 0; i < args.length; i++) {
            args[i] = searchSender.getParameterTypes()[i].getName().endsWith("$User") ? user : channel;
        }
        searchSender.invoke(chat, args);
        showSearchList.invoke(chat, true);
    }

    private void copySender(Context context, Object chat, Object owner) throws Exception {
        Object peer = owner.getClass().getField("from_id").get(owner);
        if (peer == null) throw new IllegalStateException("Message has no sender");
        long id = peer.getClass().getField("user_id").getLong(peer);
        if (id == 0) id = peer.getClass().getField("channel_id").getLong(peer);
        if (id == 0) id = peer.getClass().getField("chat_id").getLong(peer);
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Telegram sender ID", String.valueOf(id)));
        Toast.makeText(context, "ID: " + id, Toast.LENGTH_SHORT).show();
        processOption.invoke(chat, 999);
    }

    private void repeat(Object chat, Object selected, Object owner) throws Exception {
        long peer = dialogId.getLong(chat);
        Object thread = threadMessage.get(chat);
        Object controller = chat.getClass().getMethod("getMessagesController").invoke(chat);
        Object currentChat = peer < 0 ? getChat.invoke(controller, -peer) : null;
        boolean protectedChat = currentChat != null
                && currentChat.getClass().getField("noforwards").getBoolean(currentChat);
        if (thread != null || protectedChat || owner.getClass().getField("noforwards").getBoolean(owner)
                || AntiAntiCopy.INSTANCE.isNoForw()) {
            resend(chat, owner, controller, peer, thread);
            processOption.invoke(chat, 999);
            return;
        }
        ArrayList<Object> messages = new ArrayList<>();
        Object group = groupedMessage.invoke(chat, selected);
        if (group != null) {
            for (Object item : (ArrayList<?>) groupMessages.get(group)) messages.add(item);
        } else {
            messages.add(selected);
        }
        // A zero initial allowance lets Telegram request its paid-message confirmation when needed.
        forward.invoke(chat, messages, false, false, true, 0, 0L);
        processOption.invoke(chat, 999);
    }

    private void resend(Object chat, Object owner, Object controller, long peer, Object thread) throws Exception {
        Object helper = chat.getClass().getMethod("getSendMessagesHelper").invoke(chat);
        Object media = owner.getClass().getField("media").get(owner);
        Object document = media == null ? null : media.getClass().getField("document").get(media);
        if (document != null) {
            for (Object attribute : (ArrayList<?>) document.getClass().getField("attributes").get(document)) {
                if (attribute.getClass().getName().endsWith("$TL_documentAttributeSticker")) {
                    sendSticker.invoke(helper, document, null, peer, thread, thread, null, null,
                            null, true, 0, 0, false, null, null, 0L, 0L, null);
                    return;
                }
            }
        }
        String text = (String) owner.getClass().getField("message").get(owner);
        if (text == null || text.isEmpty()) throw new IllegalStateException("Message has no repeatable text or sticker");
        ArrayList<Object> entities = new ArrayList<>();
        Object sourceEntities = owner.getClass().getField("entities").get(owner);
        if (sourceEntities != null) for (Object entity : (ArrayList<?>) sourceEntities) {
            if (entity.getClass().getName().endsWith("$TL_messageEntityMentionName")) {
                Object converted = Initiator.loadClass("org.telegram.tgnet.TLRPC$TL_inputMessageEntityMentionName")
                        .getConstructor().newInstance();
                converted.getClass().getField("offset").set(converted, entity.getClass().getField("offset").get(entity));
                converted.getClass().getField("length").set(converted, entity.getClass().getField("length").get(entity));
                long user = entity.getClass().getField("user_id").getLong(entity);
                converted.getClass().getField("user_id").set(converted, getInputUser.invoke(controller, user));
                entities.add(converted);
            } else {
                entities.add(entity);
            }
        }
        Object params = textParams.invoke(null, text, peer, thread, thread, null, false,
                entities.isEmpty() ? null : entities, null, null, true, 0, 0, null, false);
        sendMessage.invoke(helper, params);
    }

    private interface Action { void run() throws Exception; }

    private void run(View view, Action action) {
        if (!enabled.getAsBoolean()) return;
        try {
            action.run();
        } catch (Exception e) {
            XposedBridge.log(e);
            Toast.makeText(view.getContext(), LocaleController.getString(
                    "UnsupportedOperation", R.string.UnsupportedOperation), Toast.LENGTH_SHORT).show();
        }
    }

    private static final class Scope {
        final Object chat;
        final Set<ViewGroup> popups = Collections.newSetFromMap(new IdentityHashMap<>());
        Scope(Object chat) { this.chat = chat; }
    }
}
