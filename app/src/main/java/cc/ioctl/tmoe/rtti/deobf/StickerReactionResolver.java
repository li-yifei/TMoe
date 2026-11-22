package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;

public final class StickerReactionResolver {
    private StickerReactionResolver() {}

    private static boolean references(Method method, String descriptor) {
        return HostDexIndex.references(method, r -> r.toString().equals(descriptor));
    }

    public static Method premiumSticker(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && AccessFlags.STATIC.isSet(m.getAccessFlags()) && m.getReturnType().equals("Z")
                && m.getParameterTypes().equals(List.of("Lorg/telegram/tgnet/TLRPC$Document;"))
                && HostDexIndex.hasString(m, "f")
                && references(m, "Lorg/telegram/tgnet/TLRPC$Document;->video_thumbs:Ljava/util/ArrayList;")
                && references(m, "Lorg/telegram/tgnet/TLRPC$VideoSize;->type:Ljava/lang/String;"));
    }

    public static Method recentStickers(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Ljava/util/ArrayList;")
                && m.getParameterTypes().equals(Arrays.asList("I", "Z"))
                && references(m, "Lorg/telegram/tgnet/TLRPC$TL_documentEmpty;")
                && references(m, "Ljava/util/ArrayList;->subList(II)Ljava/util/List;"));
    }

    public static Method dateOnline(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Ljava/lang/String;")
                && m.getParameterTypes().equals(Arrays.asList("J", "[Z"))
                && HostDexIndex.hasString(m, "YesterdayAtFormatted")
                && HostDexIndex.hasString(m, "LastSeenDateFormatted"));
    }

    public static final class Reactions {
        public final Method select, setMessage, onAttached, notification;
        public final FieldReference containerType;
        Reactions(Method select, Method setMessage, Method onAttached, Method notification,
                  FieldReference containerType) {
            this.select = select;
            this.setMessage = setMessage;
            this.onAttached = onAttached;
            this.notification = notification;
            this.containerType = containerType;
        }
    }

    public static Reactions reactions(HostDexIndex index) {
        Method select = index.uniqueMethod(m -> {
            List<? extends CharSequence> p = m.getParameterTypes();
            return m.getDefiningClass().startsWith("Lorg/telegram/ui/")
                    && !AccessFlags.STATIC.isSet(m.getAccessFlags()) && m.getReturnType().equals("V")
                    && p.size() == 11 && p.get(0).equals("Landroid/view/View;")
                    && p.get(3).equals("Landroid/view/View;") && p.get(4).equals("F") && p.get(5).equals("F")
                    && p.subList(7, 11).equals(Arrays.asList("Z", "Z", "Z", "Z"))
                    && references(m, "Lorg/telegram/tgnet/TLRPC$ChatFull;->paid_reactions_available:Z")
                    && references(m, "Lorg/telegram/tgnet/TLRPC$MessageReactions;->reactions_as_tags:Z");
        });
        String container = select.getParameterTypes().get(2).toString();
        Method setMessage = index.uniqueMethod(m -> m.getDefiningClass().equals(container)
                && !AccessFlags.STATIC.isSet(m.getAccessFlags()) && m.getReturnType().equals("V")
                && m.getParameterTypes().equals(Arrays.asList(select.getParameterTypes().get(1),
                        "Lorg/telegram/tgnet/TLRPC$ChatFull;", "Z"))
                && references(m, "Lorg/telegram/tgnet/TLRPC$TL_chatReactionsSome;"));
        Method attached = index.uniqueMethod(m -> m.getDefiningClass().equals(container)
                && m.getName().equals("onAttachedToWindow") && m.getParameterTypes().isEmpty());
        Method notification = index.uniqueMethod(m -> m.getDefiningClass().equals(container)
                && m.getName().equals("didReceivedNotification")
                && m.getParameterTypes().equals(Arrays.asList("I", "I", "[Ljava/lang/Object;")));
        Method windowType = index.uniqueMethod(m -> m.getDefiningClass().equals(container)
                && m.getName().equals("getWindowType") && m.getParameterTypes().isEmpty()
                && m.getReturnType().equals("I"));
        LinkedHashSet<FieldReference> types = new LinkedHashSet<>();
        for (Instruction instruction : windowType.getImplementation().getInstructions()) {
            if (instruction instanceof ReferenceInstruction) {
                var ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof FieldReference && ((FieldReference) ref).getType().equals("I")
                        && ((FieldReference) ref).getDefiningClass().equals(container)) {
                    types.add((FieldReference) ref);
                }
            }
        }
        return new Reactions(select, setMessage, attached, notification,
                HostDexIndex.unique(new ArrayList<>(types)));
    }
}
