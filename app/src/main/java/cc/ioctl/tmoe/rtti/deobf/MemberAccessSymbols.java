package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import java.util.ArrayList;
import java.util.List;

public final class MemberAccessSymbols {
    private static final String PROFILE = "Lorg/telegram/ui/ProfileActivity;";
    private MemberAccessSymbols() {}

    public static Method putChats(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && m.getReturnType().equals("V")
                && (m.getParameterTypes().equals(List.of("Ljava/util/List;"))
                || m.getParameterTypes().equals(List.of("Ljava/util/ArrayList;")))
                && HostDexIndex.hasString(m, "REPLACE INTO chats VALUES(?, ?, ?)"));
    }

    public static FieldReference profileChatId(HostDexIndex index) {
        Method create = index.uniqueMethod(m -> m.getDefiningClass().equals(PROFILE)
                && m.getName().equals("onFragmentCreate") && HostDexIndex.hasString(m, "chat_id"));
        boolean afterKey = false;
        List<FieldReference> matches = new ArrayList<>();
        for (Instruction instruction : create.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (reference instanceof StringReference) {
                afterKey = ((StringReference) reference).getString().equals("chat_id");
            } else if (afterKey && instruction.getOpcode() == Opcode.IPUT_WIDE
                    && reference instanceof FieldReference) {
                FieldReference field = (FieldReference) reference;
                if (field.getDefiningClass().equals(PROFILE) && field.getType().equals("J")) {
                    matches.add(field);
                    afterKey = false;
                }
            }
        }
        return HostDexIndex.unique(matches);
    }

    public static FieldReference onlineText(HostDexIndex index) {
        List<String> types = new ArrayList<>();
        for (ClassDef cls : index.classes) {
            if (!cls.getType().startsWith("Lorg/telegram/ui/")) continue;
            boolean constructor = false;
            int overrides = 0;
            for (Method method : cls.getMethods()) {
                if (method.getName().equals("<init>") && method.getParameterTypes().equals(
                        List.of(PROFILE, "Landroid/content/Context;"))) constructor = true;
                if ((method.getName().equals("setAlpha") || method.getName().equals("setTranslationX")
                        || method.getName().equals("setTranslationY"))
                        && method.getParameterTypes().equals(List.of("F"))) overrides++;
                if (method.getName().equals("setTextColor")
                        && method.getParameterTypes().equals(List.of("I"))) overrides++;
            }
            if (constructor && overrides == 4) types.add(cls.getType());
        }
        String type = HostDexIndex.unique(types);
        List<FieldReference> matches = new ArrayList<>();
        for (ClassDef cls : index.classes) {
            if (cls.getType().equals(PROFILE)) {
                for (var field : cls.getFields()) if (field.getType().equals(type)) matches.add(field);
            }
        }
        return HostDexIndex.unique(matches);
    }
}
