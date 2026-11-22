package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PrivacyReactionResolver {
    private static final String USER = "Lorg/telegram/tgnet/TLRPC$User;";
    private static final String TEXT_VIEW = "Landroid/widget/TextView;";
    private PrivacyReactionResolver() {}

    public static Method doubleTapEnabled(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/ui/")
                && m.getReturnType().equals("Z")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && (m.getParameterTypes().equals(List.of("Landroid/view/View;"))
                    || m.getParameterTypes().equals(Arrays.asList("Landroid/view/View;", "I")))
                && HostDexIndex.hasString(m, "animated_")
                && HostDexIndex.references(m,
                        r -> r.toString().equals("Lorg/telegram/tgnet/TLRPC$TL_availableReaction;")));
    }

    public static Method getUser(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals(USER)
                && m.getParameterTypes().equals(List.of("Ljava/lang/Long;"))
                && HostDexIndex.references(m, r -> r instanceof MethodReference
                        && (((MethodReference) r).getDefiningClass().equals("Ljava/util/concurrent/ConcurrentHashMap;")
                            || ((MethodReference) r).getDefiningClass().equals("Lj$/util/concurrent/ConcurrentHashMap;"))
                        && ((MethodReference) r).getName().equals("get")));
    }

    public static Method isService(HostDexIndex index) {
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Z") && m.getParameterTypes().equals(List.of("J"))
                && hasLiteral(m, 333000) && hasLiteral(m, 777000) && hasLiteral(m, 42777));
    }

    private static boolean hasLiteral(Method method, long value) {
        if (method.getImplementation() == null) return false;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction instanceof WideLiteralInstruction
                    && ((WideLiteralInstruction) instruction).getWideLiteral() == value) return true;
        }
        return false;
    }

    public static final class PhoneHeader {
        public final Method update;
        public final FieldReference subtitle;
        PhoneHeader(Method update, FieldReference subtitle) {
            this.update = update;
            this.subtitle = subtitle;
        }
    }

    public static PhoneHeader phoneHeader(HostDexIndex index) {
        Method update = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/ui/")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("V")
                && m.getParameterTypes().equals(List.of(USER))
                && HostDexIndex.hasString(m, " • @")
                && HostDexIndex.references(m, r -> r.toString().equals(USER + "->phone:Ljava/lang/String;")));
        // Follow the first TextView receiver after the phone/username separator into setText.
        // Register writes invalidate origins, so a reused register cannot select an earlier view.
        boolean separatorSeen = false;
        Map<Integer, FieldReference> fields = new HashMap<>();
        for (Instruction instruction : update.getImplementation().getInstructions()) {
            if (instruction instanceof ReferenceInstruction) {
                var reference = ((ReferenceInstruction) instruction).getReference();
                if (reference instanceof StringReference
                        && ((StringReference) reference).getString().equals(" • @")) separatorSeen = true;
                if (separatorSeen && reference instanceof MethodReference
                        && reference.toString().equals(TEXT_VIEW + "->setText(Ljava/lang/CharSequence;)V")
                        && instruction instanceof FiveRegisterInstruction) {
                    int receiver = ((FiveRegisterInstruction) instruction).getRegisterC();
                    FieldReference field = fields.get(receiver);
                    if (field == null) throw new IllegalStateException("Settings subtitle receiver is unresolved");
                    return new PhoneHeader(update, field);
                }
            }
            if (instruction.getOpcode().setsRegister() && instruction instanceof OneRegisterInstruction) {
                int target = ((OneRegisterInstruction) instruction).getRegisterA();
                FieldReference moved = null;
                if (instruction.getOpcode() == Opcode.MOVE_OBJECT
                        || instruction.getOpcode() == Opcode.MOVE_OBJECT_FROM16
                        || instruction.getOpcode() == Opcode.MOVE_OBJECT_16) {
                    moved = fields.get(((TwoRegisterInstruction) instruction).getRegisterB());
                }
                fields.remove(target);
                if (moved != null) fields.put(target, moved);
                if (instruction.getOpcode() == Opcode.IGET_OBJECT) {
                    FieldReference field = (FieldReference) ((ReferenceInstruction) instruction).getReference();
                    if (field.getDefiningClass().equals(update.getDefiningClass())
                            && field.getType().equals(TEXT_VIEW)) fields.put(target, field);
                }
            }
        }
        throw new IllegalStateException("Settings phone subtitle was not found");
    }
}
