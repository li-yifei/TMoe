package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Locates the surviving emoji and channel-pull operations after R8 optimization. */
public final class EmojiChannelResolver {
    private EmojiChannelResolver() {}

    public static Method systemEmojiReplacement(HostDexIndex index) {
        return index.uniqueMethod(method ->
                method.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                        && AccessFlags.STATIC.isSet(method.getAccessFlags())
                        && method.getReturnType().equals("Ljava/lang/CharSequence;")
                        && parameters(method).equals(Arrays.asList("Ljava/lang/CharSequence;",
                                "Landroid/graphics/Paint$FontMetricsInt;", "Z", "[I", "I", "F", "I"))
                        && HostDexIndex.references(method, ref -> ref instanceof MethodReference
                                && ((MethodReference) ref).getDefiningClass()
                                .equals("Landroid/text/Spannable$Factory;")
                                && ((MethodReference) ref).getName().equals("newSpannable")));
    }

    public static Method nextUnreadChannel(HostDexIndex index) {
        return index.uniqueMethod(method ->
                method.getDefiningClass().startsWith("Lorg/telegram/ui/")
                        && AccessFlags.STATIC.isSet(method.getAccessFlags())
                        && method.getReturnType().equals("Lorg/telegram/tgnet/TLRPC$Dialog;")
                        && parameters(method).equals(Arrays.asList("J", "I", "I", "Z", "[I")));
    }

    public static List<Method> channelDrawMethods(HostDexIndex index, Method nextUnread) {
        List<Method> methods = index.findMethods(method -> {
            if (!method.getDefiningClass().equals(nextUnread.getDefiningClass())
                    || AccessFlags.STATIC.isSet(method.getAccessFlags())
                    || !method.getReturnType().equals("V")) return false;
            List<String> params = parameters(method);
            if (params.isEmpty() || !params.get(0).equals("Landroid/graphics/Canvas;")) return false;
            // R8 specializes View to the sole caller's subclass and removes unused width.
            return params.equals(Arrays.asList("Landroid/graphics/Canvas;", "I", "I"))
                    || params.equals(Arrays.asList("Landroid/graphics/Canvas;", "I", "I", "I"))
                    || params.size() == 4 && params.get(1).startsWith("L")
                    && params.get(2).equals("F") && params.get(3).equals("F");
        });
        if (methods.size() != 2) {
            throw new IllegalStateException("Expected channel pull and bottom panel drawing: " + methods);
        }
        return methods;
    }

    private static List<String> parameters(Method method) {
        return method.getParameterTypes().stream().map(Object::toString).collect(Collectors.toList());
    }
}
