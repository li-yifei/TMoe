package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Resolves the message menu and its existing sender-search/forward actions after R8 optimization. */
public final class HistoricalMenuSymbols {
    private static final String MESSAGE = "Lorg/telegram/tgnet/TLRPC$Message;";
    private static final String USER = "Lorg/telegram/tgnet/TLRPC$User;";
    private static final String CHAT = "Lorg/telegram/tgnet/TLRPC$Chat;";

    public final Method createMenu, processOption, showSearchList, forward, groupedMessage;
    public final Method sendMessage, textParams, sendSticker, getInputUser;
    public final MethodReference openSearch, searchSender, getUser, getChat, menuCellConstructor;
    public final FieldReference selectedMessage, messageOwner, groupMessages, dialogId, threadMessage;

    public HistoricalMenuSymbols(HostDexIndex index) {
        createMenu = index.uniqueMethod(m -> m.getReturnType().equals("Z")
                && HostDexIndex.hasString(m, "open menu msg_id="));
        String owner = createMenu.getDefiningClass();
        processOption = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of("I"))
                && HostDexIndex.hasString(m, "tel:") && HostDexIndex.hasString(m, "onlySelect"));
        Method canForward = ContentHookSymbols.forwarding(index).get(0);
        String messageType = canForward.getDefiningClass();
        selectedMessage = firstFieldRead(processOption, f -> f.getDefiningClass().equals(owner)
                && f.getType().equals(messageType));
        messageOwner = firstFieldRead(canForward, f -> f.getDefiningClass().equals(messageType)
                && f.getType().equals(MESSAGE));
        Method instantSearch = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && HostDexIndex.hasString(m, "search_from_user_id")
                && HostDexIndex.hasString(m, "search_from_chat_id"));
        openSearch = reference(instantSearch, m -> m.getDefiningClass().equals(owner)
                && m.getReturnType().equals("V")
                && m.getParameterTypes().equals(List.of("Ljava/lang/String;")));
        searchSender = reference(instantSearch, m -> m.getDefiningClass().equals(owner)
                && m.getReturnType().equals("V") && m.getParameterTypes().size() == 2
                && m.getParameterTypes().contains(USER) && m.getParameterTypes().contains(CHAT));
        getUser = reference(instantSearch, m -> m.getReturnType().equals(USER)
                && isIdParameter(m));
        getChat = reference(instantSearch, m -> m.getReturnType().equals(CHAT)
                && isIdParameter(m));
        showSearchList = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of("Z"))
                && HostDexIndex.references(m, r -> r.toString().contains("->getLayoutManager()"))
                && HostDexIndex.references(m, r -> r.toString().equals("Landroid/view/View;->getHeight()I"))
                && HostDexIndex.references(m, r -> r.toString().equals(MESSAGE + "->id:I")));
        forward = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getReturnType().equals("V")
                && m.getParameterTypes().equals(Arrays.asList("Ljava/util/ArrayList;", "Z", "Z", "Z", "I", "J")));
        dialogId = firstFieldRead(forward, f -> f.getDefiningClass().equals(owner) && f.getType().equals("J"));
        threadMessage = firstFieldRead(forward, f -> f.getDefiningClass().equals(owner)
                && f.getType().equals(messageType));
        MethodReference helperGetter = reference(forward, m -> m.getName().equals("getSendMessagesHelper"));
        sendMessage = index.uniqueMethod(m -> m.getDefiningClass().equals(helperGetter.getReturnType())
                && m.getReturnType().equals("V") && m.getParameterTypes().size() == 1
                && HostDexIndex.hasString(m, "send message user_id = "));
        String paramsType = sendMessage.getParameterTypes().get(0).toString();
        List<String> textPrefix = Arrays.asList("Ljava/lang/String;", "J", messageType, messageType,
                "Lorg/telegram/tgnet/TLRPC$WebPage;", "Z", "Ljava/util/ArrayList;",
                "Lorg/telegram/tgnet/TLRPC$ReplyMarkup;", "Ljava/util/HashMap;", "Z", "I", "I");
        textParams = index.uniqueMethod(m -> m.getDefiningClass().equals(paramsType)
                && m.getReturnType().equals(paramsType) && m.getParameterTypes().size() == 14
                && m.getParameterTypes().subList(0, 12).equals(textPrefix)
                && m.getParameterTypes().get(13).equals("Z"));
        List<String> stickerPrefix = Arrays.asList("Lorg/telegram/tgnet/TLRPC$Document;",
                "Ljava/lang/String;", "J", messageType, messageType,
                "Lorg/telegram/tgnet/tl/TL_stories$StoryItem;");
        sendSticker = index.uniqueMethod(m -> m.getDefiningClass().equals(helperGetter.getReturnType())
                && m.getReturnType().equals("V") && m.getParameterTypes().size() == 17
                && m.getParameterTypes().subList(0, 6).equals(stickerPrefix)
                && m.getParameterTypes().subList(8, 13).equals(Arrays.asList("Z", "I", "I", "Z", "Ljava/lang/Object;"))
                && m.getParameterTypes().subList(14, 16).equals(List.of("J", "J")));
        getInputUser = index.uniqueMethod(m -> m.getDefiningClass().equals(getUser.getDefiningClass())
                && m.getReturnType().equals("Lorg/telegram/tgnet/TLRPC$InputUser;")
                && m.getParameterTypes().equals(List.of("J")));
        groupedMessage = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getParameterTypes().equals(List.of(messageType))
                && index.classes.stream().anyMatch(c -> c.getType().equals(m.getReturnType())
                    && hasFieldType(c, "Ljava/util/HashMap;") && hasFieldType(c, "Ljava/util/ArrayList;")));
        groupMessages = firstFieldRead(groupedMessage,
                f -> f.getDefiningClass().equals(groupedMessage.getReturnType())
                        && f.getType().equals("Ljava/util/ArrayList;"));
        menuCellConstructor = reference(createMenu, m -> m.getName().equals("<init>")
                && m.getParameterTypes().size() == 4
                && m.getParameterTypes().contains("Landroid/content/Context;")
                && m.getParameterTypes().stream().filter("Z"::equals).count() == 2
                && index.classes.stream().anyMatch(c -> c.getType().equals(m.getDefiningClass())
                    && hasMethod(c, "setText") && hasMethod(c, "getTextView") && hasMethod(c, "setIcon")));
    }

    private static boolean hasFieldType(ClassDef cls, String type) {
        for (var field : cls.getFields()) if (field.getType().equals(type)) return true;
        return false;
    }

    private static boolean hasMethod(ClassDef cls, String name) {
        for (Method method : cls.getMethods()) if (method.getName().equals(name)) return true;
        return false;
    }

    private static boolean isIdParameter(MethodReference method) {
        return method.getParameterTypes().equals(List.of("J"))
                || method.getParameterTypes().equals(List.of("Ljava/lang/Long;"));
    }

    private static MethodReference reference(Method method, Predicate<MethodReference> predicate) {
        Set<MethodReference> matches = new LinkedHashSet<>();
        if (method.getImplementation() != null) {
            for (Instruction i : method.getImplementation().getInstructions()) {
                if (i instanceof ReferenceInstruction
                        && ((ReferenceInstruction) i).getReference() instanceof MethodReference) {
                    MethodReference ref = (MethodReference) ((ReferenceInstruction) i).getReference();
                    if (predicate.test(ref)) matches.add(ref);
                }
            }
        }
        return HostDexIndex.unique(new ArrayList<>(matches));
    }

    private static FieldReference firstFieldRead(Method method, Predicate<FieldReference> predicate) {
        if (method.getImplementation() != null) {
            for (Instruction i : method.getImplementation().getInstructions()) {
                if ((i.getOpcode() == Opcode.IGET_OBJECT || i.getOpcode() == Opcode.IGET_WIDE)
                        && i instanceof ReferenceInstruction
                        && ((ReferenceInstruction) i).getReference() instanceof FieldReference) {
                    FieldReference ref = (FieldReference) ((ReferenceInstruction) i).getReference();
                    if (predicate.test(ref)) return ref;
                }
            }
        }
        throw new IllegalStateException("Missing message-menu field in " + method);
    }
}
