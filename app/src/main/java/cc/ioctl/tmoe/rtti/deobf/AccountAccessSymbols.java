package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;

import java.util.ArrayList;
import java.util.List;

/** Account access identified by preference keys and retained protocol types. */
public final class AccountAccessSymbols {
    private AccountAccessSymbols() {}

    public static final class Config {
        public final Method instance;
        public final FieldReference selectedAccount, clientUserId;

        private Config(Method instance, FieldReference selectedAccount, FieldReference clientUserId) {
            this.instance = instance;
            this.selectedAccount = selectedAccount;
            this.clientUserId = clientUserId;
        }
    }

    public static Config config(HostDexIndex index) {
        Method load = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getParameterTypes().isEmpty() && m.getReturnType().equals("V")
                && HostDexIndex.hasString(m, "selectedAccount")
                && HostDexIndex.hasString(m, "registeredForPush")
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I")));
        String owner = load.getDefiningClass();
        Method setter = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("V")
                && m.getParameterTypes().equals(List.of("Lorg/telegram/tgnet/TLRPC$User;"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$User;->id:J")));
        return new Config(singleton(index, owner),
                writtenField(load, Opcode.SPUT, "I"),
                writtenField(setter, Opcode.IPUT_WIDE, "J"));
    }

    private static FieldReference writtenField(Method method, Opcode opcode, String type) {
        List<FieldReference> matches = new ArrayList<>();
        for (Instruction insn : method.getImplementation().getInstructions()) {
            if (insn.getOpcode() != opcode) continue;
            FieldReference field = (FieldReference) ((ReferenceInstruction) insn).getReference();
            if (field.getDefiningClass().equals(method.getDefiningClass())
                    && field.getType().equals(type) && !matches.contains(field)) matches.add(field);
        }
        return HostDexIndex.unique(matches);
    }

    private static Method singleton(HostDexIndex index, String owner) {
        return index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && AccessFlags.STATIC.isSet(m.getAccessFlags()) && m.getReturnType().equals(owner)
                && m.getParameterTypes().equals(List.of("I")));
    }

    /** getInstance, getChat, getUser, getInputChannel in that order. */
    public static List<Method> messages(HostDexIndex index) {
        String owner = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/messenger/")
                && m.getReturnType().equals("Z")
                && m.getParameterTypes().equals(List.of("Lorg/telegram/tgnet/TLRPC$Chat;"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$Chat;->noforwards:Z"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$Chat;->migrated_to:Lorg/telegram/tgnet/TLRPC$InputChannel;")))
                .getDefiningClass();
        Method chat = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getParameterTypes().equals(List.of("Ljava/lang/Long;"))
                && m.getReturnType().equals("Lorg/telegram/tgnet/TLRPC$Chat;"));
        Method user = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getParameterTypes().equals(List.of("Ljava/lang/Long;"))
                && m.getReturnType().equals("Lorg/telegram/tgnet/TLRPC$User;"));
        Method channel = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getParameterTypes().equals(List.of("J"))
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Lorg/telegram/tgnet/TLRPC$InputChannel;"));
        return List.of(singleton(index, owner), chat, user, channel);
    }

    public static String chatClass(HostDexIndex index) {
        String descriptor = index.uniqueMethod(m -> m.getReturnType().equals("Z")
                && HostDexIndex.hasString(m, "open menu msg_id=")).getDefiningClass();
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
}
