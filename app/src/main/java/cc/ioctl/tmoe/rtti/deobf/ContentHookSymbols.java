package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.Method;

import java.util.Arrays;
import java.util.List;

/** Uses retained TL schema fields and method behavior to identify optimized content hooks. */
public final class ContentHookSymbols {
    private static final String MESSAGE = "Lorg/telegram/tgnet/TLRPC$Message;";
    private static final String CHAT = "Lorg/telegram/tgnet/TLRPC$Chat;";
    private static final String SECRET = "Lorg/telegram/tgnet/TLRPC$TL_message_secret;";
    private static final String TTL = "Lorg/telegram/tgnet/TLRPC$MessageMedia;->ttl_seconds:I";

    private ContentHookSymbols() {}

    public static Method shortNumber(HostDexIndex index) {
        return index.uniqueMethod(m -> AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Ljava/lang/String;")
                && (m.getParameterTypes().equals(Arrays.asList("I", "[I"))
                    || m.getParameterTypes().equals(Arrays.asList("[I", "I")))
                && HostDexIndex.hasString(m, "K")
                && HostDexIndex.hasString(m, "M"));
    }

    public static List<Method> forwarding(HostDexIndex index) {
        Method canForward = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && isBoolean(m) && m.getParameterTypes().isEmpty()
                && references(m, MESSAGE + "->noforwards:Z")
                && references(m, SECRET));
        String owner = canForward.getDefiningClass();
        Method secretStatic = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && isBoolean(m) && m.getParameterTypes().equals(List.of(MESSAGE))
                && references(m, SECRET) && references(m, TTL)
                && references(m, "Lorg/telegram/tgnet/TLRPC$TL_message;")
                && !references(m, MESSAGE + "->ttl:I"));
        Method secretInstance = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && isBoolean(m) && m.getParameterTypes().isEmpty()
                && references(m, SECRET) && references(m, TTL)
                && references(m, MESSAGE + "->ttl:I")
                && !references(m, "Ljava/lang/Math;->max(II)I"));
        Method chatNoForward = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && isBoolean(m) && m.getParameterTypes().equals(List.of(CHAT))
                && references(m, CHAT + "->noforwards:Z")
                && references(m, CHAT + "->migrated_to:Lorg/telegram/tgnet/TLRPC$InputChannel;"));
        return List.of(canForward, secretStatic, secretInstance, chatNoForward);
    }

    private static boolean isBoolean(Method method) {
        return method.getReturnType().equals("Z");
    }

    private static boolean references(Method method, String descriptor) {
        return HostDexIndex.references(method, ref -> ref.toString().equals(descriptor));
    }
}
