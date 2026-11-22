package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.util.List;

/** Resolve the sponsored-message cache accessor by the protocol request it constructs. */
public final class SponsoredMessageResolver {
    private static final String GET_SPONSORED =
            "Lorg/telegram/tgnet/TLRPC$TL_messages_getSponsoredMessages;";

    private SponsoredMessageResolver() {}

    public static Method findGetter(HostDexIndex index) {
        return index.uniqueMethod(SponsoredMessageResolver::isGetter);
    }

    public static boolean isGetter(Method method) {
        if (!method.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                || AccessFlags.STATIC.isSet(method.getAccessFlags())
                || !method.getParameterTypes().equals(List.of("J"))
                || !method.getReturnType().startsWith("L")
                || method.getImplementation() == null) {
            return false;
        }
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.NEW_INSTANCE
                    && instruction instanceof ReferenceInstruction
                    && ((ReferenceInstruction) instruction).getReference() instanceof TypeReference
                    && GET_SPONSORED.equals(((TypeReference)
                    ((ReferenceInstruction) instruction).getReference()).getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRequestEntry(MethodReference method) {
        return method.getDefiningClass().equals("Lorg/telegram/tgnet/ConnectionsManager;")
                && method.getName().equals("sendRequest")
                && method.getReturnType().equals("I")
                && method.getParameterTypes().size() >= 2
                && method.getParameterTypes().get(0).toString()
                .equals("Lorg/telegram/tgnet/TLObject;");
    }
}
