package cc.ioctl.tmoe.hook.func;

import android.text.TextPaint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry;
import cc.ioctl.tmoe.hook.base.CommonDynamicHook;
import cc.ioctl.tmoe.util.HookUtils;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.rtti.deobf.MessageTimeSymbols;
import org.jf.dexlib2.iface.reference.FieldReference;

@FunctionHookEntry
public class ShowMsgId extends CommonDynamicHook {

    public static final ShowMsgId INSTANCE = new ShowMsgId();

    private ShowMsgId() {
    }

    @Override
    public boolean initOnce() throws Exception {
        if (Initiator.load("org.telegram.ui.Cells.ChatMessageCell") == null) {
            MessageTimeSymbols symbols = new MessageTimeSymbols(HostDexIndex.get());
            install(HostDexIndex.resolve(symbols.measureTime), field(symbols.time),
                    field(symbols.messageOwner), field(symbols.paint), field(symbols.textWidth),
                    field(symbols.width));
            return true;
        }
        Class<?> kChatMessageCell = Initiator.loadClass("org.telegram.ui.Cells.ChatMessageCell");
        Method measureTime = kChatMessageCell.getDeclaredMethod("measureTime",
                Initiator.loadClass("org.telegram.messenger.MessageObject"));
        Field currentTimeString = kChatMessageCell.getDeclaredField("currentTimeString");
        currentTimeString.setAccessible(true);
        Field messageOwner = Initiator.loadClass("org.telegram.messenger.MessageObject").getDeclaredField("messageOwner");
        messageOwner.setAccessible(true);
        Class<?> kTheme = Initiator.loadClass("org.telegram.ui.ActionBar.Theme");
        Field chatTimePaint = kTheme.getDeclaredField("chat_timePaint");
        chatTimePaint.setAccessible(true);
        Field timeTextWidth = kChatMessageCell.getDeclaredField("timeTextWidth");
        timeTextWidth.setAccessible(true);
        Field timeWidth = kChatMessageCell.getDeclaredField("timeWidth");
        timeWidth.setAccessible(true);
        install(measureTime, currentTimeString, messageOwner, chatTimePaint, timeTextWidth, timeWidth);
        return true;
    }

    private static Field field(FieldReference ref) throws ReflectiveOperationException {
        Field field = Initiator.loadClass(ref.getDefiningClass()).getDeclaredField(ref.getName());
        field.setAccessible(true);
        return field;
    }

    private void install(Method measureTime, Field currentTimeString, Field messageOwner,
                         Field chatTimePaint, Field timeTextWidth, Field timeWidth) throws Exception {
        Field msgId = Initiator.loadClass("org.telegram.tgnet.TLRPC$Message").getDeclaredField("id");
        msgId.setAccessible(true);
        HookUtils.hookAfterIfEnabled(this, measureTime, param -> {
            CharSequence time = (CharSequence) currentTimeString.get(param.thisObject);
            if (time == null || param.args[0] == null) return;
            Object messageObject = param.args[0];
            Object owner = messageOwner.get(messageObject);
            int id = msgId.getInt(owner);
            String delta = id + " ";
            time = android.text.TextUtils.concat(delta, time);
            currentTimeString.set(param.thisObject, time);
            TextPaint paint = (TextPaint) chatTimePaint.get(null);
            assert paint != null;
            int deltaWidth = (int) Math.ceil(paint.measureText(delta));
            timeTextWidth.setInt(param.thisObject, deltaWidth + timeTextWidth.getInt(param.thisObject));
            timeWidth.setInt(param.thisObject, deltaWidth + timeWidth.getInt(param.thisObject));
        });
    }
}
