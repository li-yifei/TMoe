package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;

import java.util.List;

/** Interaction selectors based on retained TL fields, Bundle keys, and Android descriptors. */
public final class InteractionHookSymbols {
    private InteractionHookSymbols() {}

    public static Method command(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass()
                .equals("Lorg/telegram/ui/Components/ChatActivityEnterView;")
                && m.getReturnType().equals("V") && m.getParameterTypes().size() == 4
                && m.getParameterTypes().subList(1, 4).equals(
                List.of("Ljava/lang/String;", "Z", "Z"))
                && HostDexIndex.hasString(m, "^/[a-zA-Z@\\d_]{1,255}(\\s|$)"));
    }

    public static List<Method> spoilers(HostDexIndex index) {
        Method text = index.uniqueMethod(m -> AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of(
                "Landroid/view/View;", "Landroid/text/Layout;", "I", "I", "Landroid/text/Spanned;",
                "Ljava/util/Stack;", "Ljava/util/List;", "Ljava/util/ArrayList;"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Landroid/text/Spanned;->getSpans(IILjava/lang/Class;)[Ljava/lang/Object;")));
        Method media = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && m.getReturnType().equals("Z") && m.getParameterTypes().isEmpty()
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$MessageMedia;->spoiler:Z")));
        return List.of(text, media);
    }

    private static String chatClass(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getReturnType().equals("Z")
                && HostDexIndex.hasString(m, "open menu msg_id=")).getDefiningClass();
    }

    public static List<Method> copyWithoutForwarding(HostDexIndex index) {
        String chat = chatClass(index);
        Method noForwards = ContentHookSymbols.forwarding(index).get(3);
        Method selectedOption = index.uniqueMethod(m -> m.getDefiningClass().equals(chat)
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of("I"))
                && HostDexIndex.hasString(m, "tel:") && HostDexIndex.hasString(m, "onlySelect"));
        Method openForward = index.uniqueMethod(m -> m.getDefiningClass().equals(chat)
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of("Z"))
                && HostDexIndex.hasString(m, "onlySelect") && HostDexIndex.hasString(m, "hasPoll")
                && HostDexIndex.hasString(m, "hasInvoice") && HostDexIndex.hasString(m, "canSelectTopics"));
        return List.of(noForwards, selectedOption, openForward);
    }

    public static final class Topics {
        public final String topicsClass, chatClass;
        public final Method present;
        public final FieldReference isTopic;

        private Topics(String topicsClass, String chatClass, Method present, FieldReference isTopic) {
            this.topicsClass = className(topicsClass);
            this.chatClass = className(chatClass);
            this.present = present;
            this.isTopic = isTopic;
        }
    }

    public static Topics topics(HostDexIndex index) {
        String chat = chatClass(index);
        String topics = index.uniqueMethod(m -> m.getName().equals("<init>")
                && m.getParameterTypes().equals(List.of("Landroid/os/Bundle;"))
                && HostDexIndex.hasString(m, "for_select")
                && HostDexIndex.hasString(m, "forward_to")
                && HostDexIndex.hasString(m, "topics_end_reached_")).getDefiningClass();
        String dialogs = index.uniqueMethod(m -> m.getName().equals("onFragmentCreate")
                && m.getReturnType().equals("Z") && m.getParameterTypes().isEmpty()
                && HostDexIndex.hasString(m, "dialogsType") && HostDexIndex.hasString(m, "folderId")
                && HostDexIndex.hasString(m, "onlySelect")).getDefiningClass();
        Method present = index.uniqueMethod(m -> m.getDefiningClass().equals(dialogs)
                && m.getName().equals("presentFragment") && m.getReturnType().equals("Z")
                && m.getParameterTypes().size() == 1);
        Method setThread = index.uniqueMethod(m -> m.getDefiningClass().equals(chat)
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of(
                "Ljava/util/ArrayList;", "Lorg/telegram/tgnet/TLRPC$Chat;", "I", "I", "I",
                "Lorg/telegram/tgnet/TLRPC$TL_forumTopic;")));
        for (Instruction instruction : setThread.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.IPUT_BOOLEAN) {
                FieldReference field = (FieldReference) ((ReferenceInstruction) instruction).getReference();
                if (field.getDefiningClass().equals(chat)) {
                    return new Topics(topics, chat, present, field);
                }
            }
        }
        throw new IllegalStateException("Unable to resolve the topic flag in " + setThread);
    }

    private static String className(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
}
