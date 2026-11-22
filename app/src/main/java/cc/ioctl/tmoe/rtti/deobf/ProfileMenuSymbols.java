package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProfileMenuSymbols {
    private static final String PROFILE = "Lorg/telegram/ui/ProfileActivity;";
    public final Method createMenu, openLinkedChat;
    public final FieldReference chat, chatInfo;

    public ProfileMenuSymbols(HostDexIndex index) {
        createMenu = index.uniqueMethod(m -> m.getDefiningClass().equals(PROFILE)
                && m.getParameterTypes().equals(List.of("Z")) && m.getReturnType().equals("V")
                && HostDexIndex.hasString(m, "DeleteTopics"));
        openLinkedChat = index.uniqueMethod(m -> m.getDefiningClass().equals(PROFILE)
                && m.getParameterTypes().isEmpty() && m.getReturnType().equals("V")
                && HostDexIndex.hasString(m, "chat_id")
                && HostDexIndex.references(m, r -> r.toString().equals(
                    "Lorg/telegram/tgnet/TLRPC$ChatFull;->linked_chat_id:J")));
        chat = field(createMenu, "Lorg/telegram/tgnet/TLRPC$Chat;");
        chatInfo = field(openLinkedChat, "Lorg/telegram/tgnet/TLRPC$ChatFull;");
    }

    private static FieldReference field(Method method, String type) {
        Set<FieldReference> fields = new LinkedHashSet<>();
        for (var instruction : method.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            var ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference) {
                FieldReference f = (FieldReference) ref;
                if (f.getDefiningClass().equals(PROFILE) && f.getType().equals(type)) fields.add(f);
            }
        }
        return HostDexIndex.unique(new ArrayList<>(fields));
    }
}
